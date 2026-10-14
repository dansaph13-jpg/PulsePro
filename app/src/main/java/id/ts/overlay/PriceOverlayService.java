package id.ts.overlay;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.net.*;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;

public class PriceOverlayService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorHolder worker = new ExecutorHolder();
    private WindowManager wm;
    private View panel;
    private TextView display;
    private TextView buyPrice;
    private TextView buyDelta;
    private TextView sellPrice;
    private TextView sellDelta;
    private TextView spreadValue;

    private TextView newsDisplay;
    private TextView clock;
    private boolean running = false;
    private OkHttpClient pusherClient;
    private WebSocket pusherSocket;
    private final Runnable reconnectTask = this::connectPusher;
    private double buy = 0, sell = 0, previousBuy = 0;
    private static class PricePoint {
        final long time;
        final double price;
        PricePoint(long t, double p) { time=t; price=p; }
    }
    private final ArrayDeque<PricePoint> priceHistory = new ArrayDeque<>();
    private final ArrayDeque<PricePoint> sellPriceHistory = new ArrayDeque<>();
    private String updated = "-", status = "Menunggu jadwal...";
    private static final String CHANNEL = "ts_overlay_channel";

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (clock != null) {
                clock.setText(new java.text.SimpleDateFormat(
                    "HH:mm:ss", java.util.Locale.getDefault()
                ).format(new java.util.Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };

    static class ExecutorHolder {
        final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    @Override public void onCreate() {
        super.onCreate();
        createNotification();
        startForeground(7, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        running = true;
        addOverlay();
        connectPusher();
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                CHANNEL, "TS Gold Overlay", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(c);
        }
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void addOverlay() {
        int d = (int)(getResources().getDisplayMetrics().density + 0.5f);
        int navy = Color.rgb(5, 35, 29);
        int emerald = Color.rgb(0, 230, 150);
        int muted = Color.rgb(150, 171, 184);
        int white = Color.rgb(239, 247, 247);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(14*d, 12*d, 14*d, 13*d);

        android.graphics.drawable.GradientDrawable bg =
            new android.graphics.drawable.GradientDrawable();
        bg.setColor(navy);
        bg.setCornerRadius(16*d);
        bg.setStroke(Math.max(1, d), Color.rgb(0, 180, 115));
        root.setBackground(bg);

        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("TS");
        title.setTextColor(emerald);
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new android.widget.LinearLayout.LayoutParams(0, -2, 1));

        clock = new TextView(this);
        clock.setTextColor(emerald);
        clock.setTextSize(20);
        clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.addView(clock);

        TextView close = new TextView(this);
        close.setText("  ×");
        close.setTextColor(muted);
        close.setTextSize(22);
        close.setOnClickListener(v -> hideOverlay());
        header.addView(close);
        root.addView(header);

        android.widget.LinearLayout priceRow =
            new android.widget.LinearLayout(this);
        priceRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.TOP);

        android.widget.LinearLayout buyCard =
            new android.widget.LinearLayout(this);
        buyCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        buyCard.setPadding(7*d, 9*d, 3*d, 8*d);
        android.graphics.drawable.GradientDrawable buyBg =
            new android.graphics.drawable.GradientDrawable();
        buyBg.setColor(Color.rgb(5, 43, 33));
        buyBg.setCornerRadius(9*d);
        buyBg.setStroke(Math.max(1, d), Color.rgb(0, 180, 115));
        buyCard.setBackground(buyBg);

        buyPrice = new TextView(this);
        buyPrice.setText("Menunggu...");
        buyPrice.setTextColor(white);
        buyPrice.setTextSize(19);
        buyPrice.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        buyPrice.setSingleLine(true);
        buyCard.addView(buyPrice);

        buyDelta = new TextView(this);
        buyDelta.setText("Menunggu data");
        buyDelta.setTextColor(muted);
        buyDelta.setTextSize(10);
        buyCard.addView(buyDelta);

        android.widget.LinearLayout.LayoutParams buyLp =
            new android.widget.LinearLayout.LayoutParams(0, -2, 2f);
        buyLp.rightMargin = 3*d;
        priceRow.addView(buyCard, buyLp);

        android.widget.LinearLayout sellCard =
            new android.widget.LinearLayout(this);
        sellCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        sellCard.setGravity(Gravity.CENTER_VERTICAL);
        sellCard.setPadding(3*d, 8*d, 2*d, 8*d);
        android.graphics.drawable.GradientDrawable sellBg =
            new android.graphics.drawable.GradientDrawable();
        sellBg.setColor(Color.rgb(10, 59, 45));
        sellBg.setCornerRadius(8*d);
        sellCard.setBackground(sellBg);

        sellPrice = new TextView(this);
        sellPrice.setText("—");
        sellPrice.setTextColor(white);
        sellPrice.setTextSize(12);
        sellPrice.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        sellPrice.setSingleLine(true);
        sellCard.addView(sellPrice);

        sellDelta = new TextView(this);
        sellDelta.setText("Menunggu");
        sellDelta.setTextColor(muted);
        sellDelta.setTextSize(8);
        sellCard.addView(sellDelta);

        android.widget.LinearLayout.LayoutParams sellLp =
            new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        sellLp.rightMargin = 3*d;
        priceRow.addView(sellCard, sellLp);

        android.widget.LinearLayout spreadCard =
            new android.widget.LinearLayout(this);
        spreadCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        spreadCard.setGravity(Gravity.CENTER_VERTICAL);
        spreadCard.setPadding(3*d, 8*d, 1*d, 8*d);
        android.graphics.drawable.GradientDrawable spreadBg =
            new android.graphics.drawable.GradientDrawable();
        spreadBg.setColor(Color.rgb(10, 59, 45));
        spreadBg.setCornerRadius(8*d);
        spreadCard.setBackground(spreadBg);

        spreadValue = new TextView(this);
        spreadValue.setText("—");
        spreadValue.setTextColor(emerald);
        spreadValue.setTextSize(13);
        spreadValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        spreadValue.setSingleLine(true);
        spreadCard.addView(spreadValue);

        priceRow.addView(spreadCard,
            new android.widget.LinearLayout.LayoutParams(0, -2, 1f));

        android.widget.LinearLayout.LayoutParams priceRowLp =
            new android.widget.LinearLayout.LayoutParams(-1, -2);
        priceRowLp.topMargin = 9*d;
        root.addView(priceRow, priceRowLp);

        newsDisplay = new TextView(this);
        newsDisplay.setTextColor(white);
        newsDisplay.setTextSize(11);
        newsDisplay.setLineSpacing(2*d, 1f);
        android.widget.LinearLayout.LayoutParams newsLp =
            new android.widget.LinearLayout.LayoutParams(-1, -2);
        newsLp.topMargin = 12*d;
        root.addView(newsDisplay, newsLp);

        panel = root;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            300*d, WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 10*d;
        params.y = 100*d;

        final int[] origin = new int[2];
        final float[] touch = new float[2];
        panel.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                origin[0] = params.x;
                origin[1] = params.y;
                touch[0] = e.getRawX();
                touch[1] = e.getRawY();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                params.x = origin[0] + (int)(e.getRawX() - touch[0]);
                params.y = origin[1] + (int)(e.getRawY() - touch[1]);
                try { wm.updateViewLayout(panel, params); }
                catch (Exception ignored) {}
                return true;
            }
            return e.getAction() == MotionEvent.ACTION_UP;
        });

        wm.addView(panel, params);
        render();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        handler.removeCallbacks(newsRefreshTask);
        handler.post(newsRefreshTask);
    }


    private static String rupiah(double n) {
        return NumberFormat.getIntegerInstance(new Locale("id", "ID"))
            .format(Math.round(n));
    }


    private void recordPrice(double price) {
        long now = System.currentTimeMillis();
        priceHistory.addLast(new PricePoint(now, price));
        while (!priceHistory.isEmpty()
                && now - priceHistory.peekFirst().time > 180000L)
            priceHistory.removeFirst();
    }

    private void recordSellPrice(double price) {
        long now = System.currentTimeMillis();
        sellPriceHistory.addLast(new PricePoint(now, price));
        while (!sellPriceHistory.isEmpty()
                && now - sellPriceHistory.peekFirst().time > 180000L) {
            sellPriceHistory.removeFirst();
        }
    }

    private double sellMinuteReference() {
        long target = System.currentTimeMillis() - 60000L;
        PricePoint best = null;
        for (PricePoint p : sellPriceHistory) {
            if (p.time <= target) best = p;
            else break;
        }
        return best != null && target - best.time <= 65000L
                ? best.price : 0;
    }

    private double minuteReference() {
        long target = System.currentTimeMillis() - 60000L;
        PricePoint best = null;
        for (PricePoint p : priceHistory) {
            if (p.time <= target) best = p;
            else break;
        }
        return best != null && target - best.time <= 120000L
            ? best.price : 0;
    }

    private void render() {
        if (buyPrice == null) return;
        if (buy <= 0 || sell <= 0) {
            buyPrice.setText("Menunggu harga...");
            buyPrice.setTextColor(Color.LTGRAY);
            buyDelta.setText("Mengumpulkan data harga");
            buyDelta.setTextColor(Color.GRAY);
            sellPrice.setText("—");
            sellDelta.setText("Menunggu data 1 menit");
            sellDelta.setTextColor(Color.GRAY);
            spreadValue.setText("—");
            return;
        }

        buyPrice.setText("Rp" + rupiah(buy));
        sellPrice.setText("Rp" + rupiah(sell));

        double sellRef = sellMinuteReference();
        int sellColor;
        if (sellRef <= 0) {
            sellColor = Color.rgb(245,200,90);
            sellDelta.setText("Mengumpulkan data 1 menit");
        } else {
            double sd = sell - sellRef;
            sellColor = sd > 0 ? Color.rgb(0,255,160)
                    : sd < 0 ? Color.rgb(255,90,95)
                    : Color.rgb(245,200,90);
            String sellArrow = sd > 0 ? "▲ +" : sd < 0 ? "▼ −" : "● ";
            sellDelta.setText(sellArrow + "Rp" + rupiah(Math.abs(sd))
                    + " / 1 menit");
        }
        sellPrice.setTextColor(sellColor);
        sellDelta.setTextColor(sellColor);

        spreadValue.setText(String.format(Locale.US, "%.2f%%",
            Math.abs(buy-sell)/buy*100.0));

        double ref = minuteReference();
        int color;
        if (ref <= 0) {
            color = Color.rgb(245,200,90);
            buyDelta.setText("Mengumpulkan data 1 menit");
        } else {
            double d = buy-ref;
            color = d > 0 ? Color.rgb(0,255,160)
                : d < 0 ? Color.rgb(255,90,95)
                : Color.rgb(245,200,90);
            String arrow = d > 0 ? "▲ +" : d < 0 ? "▼ −" : "● ";
            buyDelta.setText(arrow + "Rp" + rupiah(Math.abs(d))
                + " / 1 menit");
        }

        buyPrice.setTextColor(color);
        buyDelta.setTextColor(color);
    }

    private final Runnable newsRefreshTask = new Runnable() {
        @Override public void run() {
            if (!running) return;
            loadOverlayNews();
            handler.postDelayed(this, 15 * 60 * 1000L);
        }
    };

    private String toWib(String value) {
        try {
            java.time.Instant instant = java.time.Instant.parse(value);
            java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern(
                    "dd/MM HH:mm", java.util.Locale.getDefault())
                .withZone(java.time.ZoneId.of("Asia/Jakarta"));
            return fmt.format(instant) + " WIB";
        } catch (Exception ignored) {
            return value;
        }
    }

    private void loadOverlayNews() {
        worker.executor.execute(() -> {
            String result = "US NEWS • HARI INI\nData berita belum tersedia.";
            HttpURLConnection c = null;
            try {
                java.time.LocalDate today =
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"));
                java.time.LocalDate end = today;
                URL url = new URL(
                    "https://www.financecalendar.com/wp-json/fc/v1/calendar"
                    + "?from=" + today + "&to=" + end + "&limit=200");
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/json");

                String raw;
                try (InputStream in = c.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    raw = out.toString("UTF-8");
                }

                org.json.JSONObject root = new org.json.JSONObject(raw);
                org.json.JSONArray events = root.optJSONArray("events");
                if (events == null) events = root.optJSONArray("data");
                if (events == null && root.optJSONObject("data") != null)
                    events = root.optJSONObject("data").optJSONArray("events");

                if (events != null) {
                    StringBuilder out = new StringBuilder("🇺🇸 US NEWS • HARI INI\n");
                    int count = 0;
                    for (int i = 0; i < events.length() && count < 12; i++) {
                        org.json.JSONObject e = events.optJSONObject(i);
                        if (e == null) continue;

                        String title = e.optString("title",
                            e.optString("name", "Berita ekonomi AS"));
                        String check = (title + " " + e.optString("country", "")
                            + " " + e.optString("category", "")).toLowerCase(Locale.US);

                        if (check.contains("china") || check.contains("australia")
                            || check.contains("united kingdom") || check.contains("uk gdp")
                            || check.contains("eurozone") || check.contains("japan")
                            || check.contains("canada")) continue;

                        if (!(check.startsWith("us ") || check.contains("united states")
                            || check.contains("u.s.")
                            || check.contains("cpi") || check.contains("inflation")
                            || check.contains("payroll") || check.contains("employment")
                            || check.contains("unemployment") || check.contains("fomc")
                            || check.contains("federal reserve") || check.contains("pce")
                            || check.contains("ppi") || check.contains("gdp")
                            || check.contains("retail sales") || check.contains("ism")
                            || check.contains("pmi") || check.contains("jolts")
                            || check.contains("consumer confidence")
                            || check.contains("jobless"))) continue;

                        String time = e.optString("time_utc",
                            e.optString("datetime", e.optString("date", "--")));
                        String prev = e.optString("prior", e.optString("previous", "--"));
                        String pred = e.optString("consensus", e.optString("forecast", "--"));
                        String act = e.optString("actual", "--");

                        out.append("\n").append(title)
                            .append("\nWaktu sumber: ").append(time)
                            .append("\nPREV ").append(prev).append("  PRED ").append(pred)
                            .append("  ACT ").append(act).append("\n");
                        count++;
                    }
                    if (count == 0)
                        out.append("Belum ada berita AS yang cocok atau format sumber berubah.");
                    result = out.toString();
                } else {
                    result = "🇺🇸 US NEWS • HARI INI\nFormat sumber berita belum dikenali.";
                }
            } catch (Exception ex) {
                result = "🇺🇸 US NEWS • HARI INI\nData kalender belum dapat dimuat.";
            } finally {
                if (c != null) c.disconnect();
            }

            final String news = result;
            handler.post(() -> {
                if (running && newsDisplay != null) newsDisplay.setText(news);
            });
        });
    }

    private double parsePrice(Object value) {
        if (value == null) return 0;
        String digits = value.toString().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Double.parseDouble(digits); }
        catch (Exception e) { return 0; }
    }

    private void connectPusher() {
        if (!running) return;
        handler.removeCallbacks(reconnectTask);
        status = "Menghubungkan Treasury...";
        render();

        if (pusherClient == null) {
            pusherClient = new OkHttpClient.Builder()
                .pingInterval(25, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        }

        Request request = new Request.Builder()
            .url("wss://ws-ap1.pusher.com/app/52e99bd2c3c42e577e13?protocol=7&client=js&version=8.4.0&flash=false")
            .build();

        pusherSocket = pusherClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                handler.post(() -> {
                    if (!running) return;
                    status = "Tersambung; menunggu harga...";
                    render();
                });
            }

            @Override public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    String event = msg.optString("event", "");

                    if ("pusher:connection_established".equals(event)) {
                        JSONObject sub = new JSONObject();
                        sub.put("event", "pusher:subscribe");
                        JSONObject data = new JSONObject();
                        data.put("channel", "gold-rate");
                        sub.put("data", data);
                        ws.send(sub.toString());
                    } else if ("pusher:ping".equals(event)) {
                        JSONObject pong = new JSONObject();
                        pong.put("event", "pusher:pong");
                        pong.put("data", new JSONObject());
                        ws.send(pong.toString());
                    } else if ("gold-rate-event".equals(event)) {
                        Object raw = msg.opt("data");
                        JSONObject data = raw instanceof JSONObject
                            ? (JSONObject) raw
                            : new JSONObject(String.valueOf(raw));
                        double b = parsePrice(data.opt("buying_rate"));
                        double s = parsePrice(data.opt("selling_rate"));
                        String u = data.optString("created_at", "-");
                        if (b > 0 && s > 0) handler.post(() -> {
                            if (!running) return;
                            previousBuy = buy;
                            buy = b;
                            recordPrice(b);
                            sell = s;
                            recordSellPrice(s);
                            updated = u;
                            status = "Terhubung • LIVE";
                            render();
                            updateNotification();
                        });
                    } else if ("pusher_internal:subscription_succeeded".equals(event)) {
                        handler.post(() -> {
                            if (!running) return;
                            status = "Channel aktif; menunggu harga...";
                            render();
                        });
                    } else if ("pusher:subscription_error".equals(event)
                            || "pusher:error".equals(event)) {
                        handler.post(() -> {
                            if (!running) return;
                            status = "Koneksi ditolak";
                            render();
                        });
                    }
                } catch (Exception ignored) { }
            }

            @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                handler.post(() -> {
                    if (!running) return;
                    status = "Koneksi terputus; mencoba ulang...";
                    render();
                    scheduleReconnect();
                });
            }

            @Override public void onClosed(WebSocket ws, int code, String reason) {
                handler.post(() -> {
                    if (!running) return;
                    status = "Koneksi ditutup; mencoba ulang...";
                    render();
                    scheduleReconnect();
                });
            }
        });
    }

    private void scheduleReconnect() {
        if (running) {
            handler.removeCallbacks(reconnectTask);
            handler.postDelayed(reconnectTask, 5000);
        }
    }

    private void hideOverlay() {
        if (panel != null && wm != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
        }
        panel = null;
        buyPrice = null;
        buyDelta = null;
        sellPrice = null;
        spreadValue = null;
        newsDisplay = null;
        clock = null;
    }

    private Notification buildNotification() {
        String title = buy > 0
            ? "TS • Rp" + rupiah(buy)
            : "TS";
        String content = buy > 0
            ? "SELL Rp" + rupiah(sell) + " • Update " + updated
            : "Menunggu data harga Treasury";

        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);

        return b.setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(PendingIntent.getActivity(this, 0, open, flags))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build();
    }

    private void updateNotification() {
        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(7, buildNotification());
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (running && panel == null && Settings.canDrawOverlays(this)) {
            addOverlay();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        worker.executor.shutdownNow();
        handler.removeCallbacks(reconnectTask);
        if (pusherSocket != null) {
            pusherSocket.close(1000, "Overlay dimatikan");
            pusherSocket = null;
        }
        if (pusherClient != null) {
            pusherClient.dispatcher().cancelAll();
            pusherClient.connectionPool().evictAll();
            pusherClient.dispatcher().executorService().shutdown();
            pusherClient = null;
        }
        if (panel != null && wm != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
