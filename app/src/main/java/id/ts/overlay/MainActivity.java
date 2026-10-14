package id.ts.overlay;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.text.style.*;
import org.json.*;
import okhttp3.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    LinearLayout root;
    TextView clock, display, statusView, updateView;
TextView buyPriceView, buyDeltaView, sellPriceView, sellDeltaView, spreadView, newsSummary;
    Handler handler = new Handler(Looper.getMainLooper());
    OkHttpClient client;
    WebSocket socket;
    boolean active = false;
    double buy = 0, sell = 0, previousBuy = 0;
    static class PricePoint {
        final long time;
        final double price;
        PricePoint(long t, double p) { time=t; price=p; }
    }
    final ArrayDeque<PricePoint> priceHistory = new ArrayDeque<>();
    final ArrayDeque<PricePoint> sellPriceHistory = new ArrayDeque<>();
    String updated = "-", status = "Menghubungkan Treasury...";
    Runnable reconnect;

    Runnable clockTick = new Runnable() {
        public void run() {
            if (!active) return;
            if (clock != null) {
                clock.setText(new SimpleDateFormat("HH:mm:ss",
                    Locale.getDefault()).format(new Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
        }
        active = true;
        getWindow().setStatusBarColor(Color.rgb(5,35,29));
        getWindow().setNavigationBarColor(Color.rgb(5,35,29));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(12));
        root.setBackgroundColor(Color.rgb(5,35,29));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("CUAN MONITOR");
        title.setTextColor(Color.rgb(0,230,150));
        title.setTextSize(22);
        title.setTypeface(null,1);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));

        clock = new TextView(this);
        clock.setText("--:--:--");
        clock.setTextColor(Color.rgb(0,255,160));
        clock.setTextSize(20);
        clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.addView(clock);

        TextView menu = new TextView(this);
        menu.setText("⋮");
        menu.setTextSize(30);
        menu.setTextColor(Color.WHITE);
        menu.setPadding(dp(12),0,dp(4),0);
        header.addView(menu);
        root.addView(header);

        menu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this,menu);
            popup.getMenu().add("US NEWS MINGGUAN").setOnMenuItemClickListener(item -> {
                openNews("weekly"); return true;
            });
            popup.getMenu().add("AKTIFKAN OVERLAY").setOnMenuItemClickListener(item -> {
                startOverlay(); return true;
            });
            popup.getMenu().add("HENTIKAN OVERLAY").setOnMenuItemClickListener(item -> {
                stopService(new Intent(this,PriceOverlayService.class));
                Toast.makeText(this,"Overlay dihentikan",Toast.LENGTH_SHORT).show();
                return true;
            });
            popup.getMenu().add("KELUAR APLIKASI").setOnMenuItemClickListener(item -> {
                active = false;
                if (socket != null) socket.close(1000,"App closed");
                stopService(new Intent(this,PriceOverlayService.class));
                finishAndRemoveTask();
                return true;
            });
            popup.show();
        });

        TextView subtitle = new TextView(this);
        subtitle.setText("PREMIUM  •  SIMPLE  •  HEMAT DAYA");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(11);
        subtitle.setPadding(0,dp(4),0,dp(16));
        subtitle.setVisibility(View.GONE);
        root.addView(subtitle);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(12),dp(14),dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(10,59,45));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1),Color.rgb(0,180,115));
        panel.setBackground(bg);
        root.addView(panel,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView timeLabel = new TextView(this);
        timeLabel.setText("WAKTU WIB");
        timeLabel.setTextColor(Color.LTGRAY);
        timeLabel.setTextSize(12);
        timeRow.addView(timeLabel,new LinearLayout.LayoutParams(0,-2,1));

        timeRow.setVisibility(View.GONE);
        panel.addView(timeRow);

        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.TOP);

        LinearLayout buyCard = new LinearLayout(this);
        buyCard.setOrientation(LinearLayout.VERTICAL);
        buyCard.setPadding(dp(10), dp(12), dp(6), dp(12));
        GradientDrawable buyBg = new GradientDrawable();
        buyBg.setColor(Color.rgb(5, 43, 33));
        buyBg.setCornerRadius(dp(10));
        buyBg.setStroke(dp(1), Color.rgb(0, 180, 115));
        buyCard.setBackground(buyBg);

        TextView buyLabel = new TextView(this);
        buyLabel.setText("BUY");
        buyLabel.setTextColor(Color.LTGRAY);
        buyLabel.setTextSize(12);
        buyCard.addView(buyLabel);

        buyPriceView = new TextView(this);
        buyPriceView.setText("Menunggu...");
        buyPriceView.setTextColor(Color.WHITE);
        buyPriceView.setTextSize(24);
        buyPriceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        buyPriceView.setSingleLine(true);
        buyPriceView.setTextSize(20);
        buyCard.addView(buyPriceView);

        buyDeltaView = new TextView(this);
        buyDeltaView.setText("Menunggu data");
        buyDeltaView.setTextColor(Color.LTGRAY);
        buyDeltaView.setTextSize(10);
        buyCard.addView(buyDeltaView);

        LinearLayout.LayoutParams buyLp =
            new LinearLayout.LayoutParams(0, -2, 1.15f);
        buyLp.rightMargin = dp(5);
        priceRow.addView(buyCard, buyLp);

        LinearLayout rightColumn = new LinearLayout(this);
        rightColumn.setOrientation(LinearLayout.VERTICAL);

        LinearLayout sellCard = new LinearLayout(this);
        sellCard.setOrientation(LinearLayout.VERTICAL);
        sellCard.setPadding(dp(10), dp(8), dp(5), dp(8));
        GradientDrawable sellBg = new GradientDrawable();
        sellBg.setColor(Color.rgb(10, 59, 45));
        sellBg.setCornerRadius(dp(9));
        sellCard.setBackground(sellBg);

        TextView sellLabel = new TextView(this);
        sellLabel.setText("SELL");
        sellLabel.setTextColor(Color.LTGRAY);
        sellLabel.setTextSize(11);
        sellCard.addView(sellLabel);

        sellPriceView = new TextView(this);
        sellPriceView.setText("—");
        sellPriceView.setTextColor(Color.WHITE);
        sellPriceView.setTextSize(16);
        sellPriceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        sellPriceView.setSingleLine(true);
        sellCard.addView(sellPriceView);

        sellDeltaView = new TextView(this);
        sellDeltaView.setText("Menunggu data");
        sellDeltaView.setTextColor(Color.LTGRAY);
        sellDeltaView.setTextSize(10);
        sellCard.addView(sellDeltaView);

        rightColumn.addView(sellCard);

        LinearLayout spreadCard = new LinearLayout(this);
        spreadCard.setOrientation(LinearLayout.VERTICAL);
        spreadCard.setPadding(dp(10), dp(8), dp(5), dp(8));
        GradientDrawable spreadBg = new GradientDrawable();
        spreadBg.setColor(Color.rgb(10, 59, 45));
        spreadBg.setCornerRadius(dp(9));
        spreadCard.setBackground(spreadBg);

        TextView spreadLabel = new TextView(this);
        spreadLabel.setText("SPREAD");
        spreadLabel.setTextColor(Color.LTGRAY);
        spreadLabel.setTextSize(11);
        spreadCard.addView(spreadLabel);

        spreadView = new TextView(this);
        spreadView.setText("—");
        spreadView.setTextColor(Color.rgb(0, 230, 150));
        spreadView.setTextSize(16);
        spreadView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        spreadCard.addView(spreadView);

        LinearLayout.LayoutParams spreadLp =
            new LinearLayout.LayoutParams(-1, -2);
        spreadLp.topMargin = dp(5);
        rightColumn.addView(spreadCard, spreadLp);

        priceRow.addView(rightColumn,
            new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(priceRow);

        newsSummary = new TextView(this);
        newsSummary.setText("US NEWS • Mengambil jadwal ekonomi AS...");
        newsSummary.setTextColor(Color.LTGRAY);
        newsSummary.setTextSize(12);
        newsSummary.setPadding(dp(2), dp(12), dp(2), dp(2));
        newsSummary.setOnClickListener(v -> openNews("weekly"));
        panel.addView(newsSummary);
        updateView = new TextView(this);
        updateView.setTextColor(Color.LTGRAY);
        updateView.setTextSize(12);
        updateView.setPadding(0,dp(12),0,dp(3));
        updateView.setVisibility(View.GONE);
        panel.addView(updateView);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(0,230,150));
        statusView.setTextSize(12);
        statusView.setVisibility(View.GONE);
        panel.addView(statusView);

        TextView note = new TextView(this);
        note.setText("US NEWS hari ini tampil di floating overlay.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(11);
        note.setPadding(0,dp(14),0,0);
        note.setVisibility(View.GONE);
        root.addView(note);

        TextView footer = new TextView(this);
        footer.setText("TS GOLD PULSE PRO  •  LIVE PRICE");
        footer.setTextColor(Color.rgb(110,170,145));
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1,-2);
        fp.topMargin = dp(18);
        footer.setVisibility(View.GONE);
        root.addView(footer,fp);

        setContentView(root);
        render();
        handler.post(clockTick);
        connectPusher();
        handler.post(mainNewsTick);
    }

    void openNews(String period) {
        Intent i = new Intent(this,USNewsActivity.class);
        i.putExtra("period",period);
        startActivity(i);
    }

    void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,"Izinkan overlay di pengaturan",Toast.LENGTH_SHORT).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:"+getPackageName()));
            startActivity(i);
            return;
        }
        Intent i = new Intent(this,PriceOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this,"Overlay diaktifkan",Toast.LENGTH_SHORT).show();
    }

    String rupiah(double n) {
        return NumberFormat.getIntegerInstance(new Locale("id","ID"))
            .format(Math.round(n));
    }


    void recordPrice(double price) {
        long now = System.currentTimeMillis();
        priceHistory.addLast(new PricePoint(now, price));
        while (!priceHistory.isEmpty()
                && now - priceHistory.peekFirst().time > 180000L)
            priceHistory.removeFirst();
    }

    double minuteReference() {
        long target = System.currentTimeMillis() - 60000L;
        PricePoint best = null;
        for (PricePoint p : priceHistory) {
            if (p.time <= target) best = p;
            else break;
        }
        return best != null && target - best.time <= 120000L
            ? best.price : 0;
    }

    final Runnable mainNewsTick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            loadMainNews();
            handler.postDelayed(this, 60000L);
        }
    };

    void loadMainNews() {
        new Thread(() -> {
            String result = "US NEWS • Data kalender belum tersedia.";
            java.net.HttpURLConnection c = null;
            try {
                java.time.LocalDate today =
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"));
                java.net.URL url = new java.net.URL(
                    "https://www.financecalendar.com/wp-json/fc/v1/calendar"
                    + "?from=" + today + "&to=" + today.plusDays(7) + "&limit=200");
                c = (java.net.HttpURLConnection) url.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/json");

                String raw;
                try (java.io.InputStream in = c.getInputStream();
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    raw = out.toString("UTF-8");
                }

                org.json.JSONObject rootJson = new org.json.JSONObject(raw);
                org.json.JSONArray events = rootJson.optJSONArray("events");
                if (events == null) events = rootJson.optJSONArray("data");

                StringBuilder b = new StringBuilder("🇺🇸 US NEWS • 7 HARI");
                int count = 0;
                if (events != null) {
                    for (int i = 0; i < events.length() && count < 3; i++) {
                        org.json.JSONObject e = events.optJSONObject(i);
                        if (e == null) continue;
                        String name = newsValue(e, "title");
                        if (name.equals("Belum rilis")) name = newsValue(e, "name");
                        if (name.equals("Belum rilis")) name = "US economic event";
                        String key = (name + " " + e.optString("category", ""))
                            .toLowerCase(java.util.Locale.US);
                        if (!(key.contains("us ") || key.contains("u.s.")
                            || key.contains("united states") || key.contains("cpi")
                            || key.contains("inflation") || key.contains("payroll")
                            || key.contains("employment") || key.contains("jobless")
                            || key.contains("fomc") || key.contains("federal reserve")
                            || key.contains("pce") || key.contains("ppi")
                            || key.contains("gdp") || key.contains("retail sales")
                            || key.contains("ism") || key.contains("pmi")
                            || key.contains("jolts") || key.contains("consumer confidence")
                            || key.contains("durable goods") || key.contains("housing starts")))
                            continue;

                        String time = newsValue(e, "time_utc");
                        if (time.equals("Belum rilis")) time = newsValue(e, "date");
                        try {
                            time = java.time.format.DateTimeFormatter
                                .ofPattern("dd MMM HH:mm 'WIB'", new java.util.Locale("id", "ID"))
                                .withZone(java.time.ZoneId.of("Asia/Jakarta"))
                                .format(java.time.Instant.parse(time));
                        } catch (Exception ignored) {}

                        String impact = newsValue(e, "impact");
                        String prior = newsValue(e, "prior");
                        String forecast = newsValue(e, "consensus");
                        String actual = newsValue(e, "actual");
                        String bias = newsBias(name, actual, forecast);
                        b.append("\n\n").append(name)
                            .append("\n").append(time).append(" • ").append(impact)
                            .append("\nPREV ").append(prior)
                            .append(" | PRED ").append(forecast)
                            .append(" | ACT ").append(actual)
                            .append("\n").append(bias);
                        count++;
                    }
                }
                if (count == 0) b.append("\nBelum ada jadwal AS yang cocok.");
                b.append("\nKetuk untuk analisis mingguan USD / XAUUSD.");
                result = b.toString();
            } catch (Exception ignored) {
                result = "🇺🇸 US NEWS • Gagal memuat data. Ketuk untuk membuka berita mingguan.";
            } finally {
                if (c != null) c.disconnect();
            }

            final String displayNews = result;
            handler.post(() -> {
                if (active && newsSummary != null) newsSummary.setText(displayNews);
            });
        }).start();
    }

    String newsValue(org.json.JSONObject e, String key) {
        Object value = e.opt(key);
        if (value == null || value == org.json.JSONObject.NULL)
            return "Belum rilis";
        String result = String.valueOf(value).trim();
        if (result.isEmpty() || result.equalsIgnoreCase("null"))
            return "Belum rilis";
        return result;
    }

    String newsBias(String name, String actual, String forecast) {
        if (actual.equals("Belum rilis") || forecast.equals("Belum rilis"))
            return "USD / XAUUSD: menunggu ACT dan PRED.";

        try {
            double a = Double.parseDouble(
                actual.replace(",", "").replace("%", "")
                    .replaceAll("[^0-9.\\-]", ""));
            double f = Double.parseDouble(
                forecast.replace(",", "").replace("%", "")
                    .replaceAll("[^0-9.\\-]", ""));

            if (a == f)
                return "USD: netral awal • XAUUSD: netral awal";

            String n = name.toLowerCase(java.util.Locale.US);
            boolean inverse = n.contains("jobless claims")
                || n.contains("unemployment claims")
                || n.contains("unemployment rate");

            boolean stronger = a > f;
            if (inverse) stronger = !stronger;

            return stronger
                ? "Bias awal: USD 🟢 bullish • XAUUSD 🔴 bearish"
                : "Bias awal: USD 🔴 bearish • XAUUSD 🟢 bullish";
        } catch (Exception ex) {
            return "USD / XAUUSD: data perlu dibaca manual.";
        }
    }

    void recordSellPrice(double price) {
        long now = System.currentTimeMillis();
        sellPriceHistory.addLast(new PricePoint(now, price));
        while (!sellPriceHistory.isEmpty()
                && now - sellPriceHistory.peekFirst().time > 180000L)
            sellPriceHistory.removeFirst();
    }

    double sellMinuteReference() {
        long target = System.currentTimeMillis() - 60000L;
        PricePoint best = null;
        for (PricePoint p : sellPriceHistory) {
            if (p.time <= target) best = p;
            else break;
        }
        return best != null && target - best.time <= 65000L ? best.price : 0;
    }

    void render() {
        if (buyPriceView == null) return;
        if (buy <= 0 || sell <= 0) {
            buyPriceView.setText("Menunggu...");
            buyDeltaView.setText("Menunggu data");
            sellPriceView.setText("—");
            spreadView.setText("—");
            return;
        }

        buyPriceView.setText("Rp" + rupiah(buy));
        sellPriceView.setText("Rp" + rupiah(sell));
        spreadView.setText(String.format(Locale.US, "%.2f%%",
            Math.abs(buy-sell)/buy*100.0));

        double ref = minuteReference();
        int color;
        if (ref <= 0) {
            color = Color.rgb(245,200,90);
            buyDeltaView.setText("Mengumpulkan data 1 menit");
        } else {
            double d = buy-ref;
            color = d > 0 ? Color.rgb(0,255,160)
                : d < 0 ? Color.rgb(255,90,95)
                : Color.rgb(245,200,90);
            String arrow = d > 0 ? "▲ +" : d < 0 ? "▼ −" : "● ";
            buyDeltaView.setText(arrow + "Rp" + rupiah(Math.abs(d))
                + " / 1 menit");
        }

        buyPriceView.setTextColor(color);
        buyDeltaView.setTextColor(color);

        double sellRef = sellMinuteReference();
        if (sellRef <= 0) {
            sellDeltaView.setText("Mengumpulkan data 1 menit");
            sellDeltaView.setTextColor(Color.rgb(245,200,90));
        } else {
            double sd = sell - sellRef;
            int sc = sd > 0 ? Color.rgb(0,255,160)
                : sd < 0 ? Color.rgb(255,90,95)
                : Color.rgb(245,200,90);
            String sa = sd > 0 ? "▲ +" : sd < 0 ? "▼ −" : "● ";
            sellDeltaView.setText(sa + "Rp" + rupiah(Math.abs(sd)) + " / 1 menit");
            sellDeltaView.setTextColor(sc);
        }
        if (updateView != null) updateView.setText("Update: " + updated);
        if (statusView != null) statusView.setText("Status: " + status);
    }

    double parsePrice(Object value) {
        if (value == null) return 0;
        String digits = value.toString().replaceAll("[^0-9]","");
        if (digits.isEmpty()) return 0;
        try { return Double.parseDouble(digits); }
        catch (Exception e) { return 0; }
    }

    void connectPusher() {
        if (!active) return;
        if (client == null) {
            client = new OkHttpClient.Builder()
                .pingInterval(25,TimeUnit.SECONDS).build();
        }

        Request req = new Request.Builder()
            .url("wss://ws-ap1.pusher.com/app/52e99bd2c3c42e577e13?protocol=7&client=js&version=8.4.0&flash=false")
            .build();

        socket = client.newWebSocket(req,new WebSocketListener() {
            @Override public void onOpen(WebSocket ws,Response response) {
                handler.post(() -> {
                    if (!active) return;
                    status = "Terhubung; menunggu harga...";
                    render();
                });
            }

            @Override public void onMessage(WebSocket ws,String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    String event = msg.optString("event","");
                    if ("pusher:connection_established".equals(event)) {
                        JSONObject data = new JSONObject();
                        data.put("channel","gold-rate");
                        JSONObject sub = new JSONObject();
                        sub.put("event","pusher:subscribe");
                        sub.put("data",data);
                        ws.send(sub.toString());
                    } else if ("pusher:ping".equals(event)) {
                        JSONObject pong = new JSONObject();
                        pong.put("event","pusher:pong");
                        pong.put("data",new JSONObject());
                        ws.send(pong.toString());
                    } else if ("gold-rate-event".equals(event)) {
                        Object raw = msg.opt("data");
                        JSONObject d = raw instanceof JSONObject
                            ? (JSONObject)raw : new JSONObject(String.valueOf(raw));
                        double nb = parsePrice(d.opt("buying_rate"));
                        double ns = parsePrice(d.opt("selling_rate"));
                        String u = d.optString("created_at","-");
                        if (nb > 0 && ns > 0) handler.post(() -> {
                            if (!active) return;
                            previousBuy = buy; buy = nb; recordPrice(nb); sell = ns; recordSellPrice(ns); updated = u;
                            status = "Terhubung • LIVE";
                            render();
                        });
                    } else if ("pusher_internal:subscription_succeeded".equals(event)) {
                        handler.post(() -> {
                            if (!active) return;
                            status = "Channel aktif; menunggu harga...";
                            render();
                        });
                    }
                } catch (Exception ignored) {}
            }

            @Override public void onFailure(WebSocket ws,Throwable t,Response response) {
                handler.post(() -> {
                    if (!active) return;
                    status = "Koneksi terputus; mencoba ulang...";
                    render();
                    scheduleReconnect();
                });
            }

            @Override public void onClosed(WebSocket ws,int code,String reason) {
                handler.post(() -> {
                    if (!active) return;
                    status = "Koneksi ditutup; mencoba ulang...";
                    render();
                    scheduleReconnect();
                });
            }
        });
    }

    void scheduleReconnect() {
        if (!active) return;
        if (reconnect != null) handler.removeCallbacks(reconnect);
        reconnect = this::connectPusher;
        handler.postDelayed(reconnect,5000);
    }

    @Override protected void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (socket != null) socket.close(1000,"Activity closed");
        if (client != null) client.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}
