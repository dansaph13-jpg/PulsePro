package id.ts.goldoverlay;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

public class OverlayService extends Service {
    private static final String API =
        "https://api.treasury.id/api/v1/antigrvty/gold/rate";
    private static final String CHANNEL = "ts_gold_overlay";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View panel;
    private TextView buyText, buyChangeText, sellText, sellChangeText;
    private TextView spreadText, timeText, statusText, secondsText, batonText;
    private SharedPreferences prefs;
    private boolean busy = false;

    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            if (secondsText == null) return;
            secondsText.setText(new java.text.SimpleDateFormat(
                "ss", Locale.getDefault()
            ).format(new java.util.Date()));
            long delay = 1000 - (System.currentTimeMillis() % 1000);
            handler.postDelayed(this, delay);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("ts_cache", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(1, makeNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (panel == null) createPanel();
        renderCache("Menunggu jadwal pengambilan harga");
        handler.removeCallbacks(clockTicker);
        handler.post(clockTicker);
        scheduleNextMinute();
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                CHANNEL, "TS Gold Overlay", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(c);

            NotificationChannel alerts = new NotificationChannel(
                "ts_price_alerts", "Perubahan Harga TS",
                NotificationManager.IMPORTANCE_DEFAULT);
            alerts.setDescription("Notifikasi kenaikan dan penurunan harga Treasury");
            nm.createNotificationChannel(alerts);
        }
    }

    private Notification makeNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("TS Gold Overlay aktif")
            .setContentText("Panel harga berjalan")
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(18);
        d.setStroke(1, Color.rgb(212, 175, 55));
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(4, 5, 4, 5);
        return t;
    }

    private void createPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(14, 10, 14, 12);
        box.setBackground(rounded(Color.rgb(17, 24, 39)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("TS GOLD • PULSE PRO", 17, Color.rgb(212, 175, 55));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        secondsText = text("00", 36, Color.rgb(57, 255, 136));
        secondsText.setTypeface(null, Typeface.BOLD);
        secondsText.setGravity(Gravity.CENTER);
        secondsText.setMinWidth(dp(48));
        header.addView(secondsText, new LinearLayout.LayoutParams(-2, -2));


        Button close = new Button(this);
        close.setText("×");
        close.setMinWidth(42);
        close.setOnClickListener(v -> stopSelf());
        header.addView(close);
        box.addView(header);

        buyText = text("BELI : —", 32, Color.WHITE);
        buyText.setTypeface(null, Typeface.BOLD);
        buyChangeText = text("— Belum ada perubahan", 13, Color.LTGRAY);

        sellText = text("JUAL : —", 18, Color.WHITE);
        sellChangeText = text("— Belum ada perubahan", 13, Color.LTGRAY);

        spreadText = text("SPREAD  —", 24, Color.rgb(255, 215, 90));
        spreadText.setTypeface(null, Typeface.BOLD);
        spreadText.setGravity(Gravity.CENTER);
        spreadText.setPadding(8, 12, 8, 12);
        spreadText.setBackground(rounded(Color.rgb(38, 31, 12)));


        timeText = text("Data API: —", 12, Color.LTGRAY);
        statusText = text("Menyiapkan layanan…", 12, Color.LTGRAY);

        box.addView(buyText);
        box.addView(buyChangeText);
        box.addView(sellText);
        box.addView(sellChangeText);
        box.addView(spreadText);
        batonText = text("BATON Rp" + rupiah(
            prefs.getLong("baton", 2477000)), 11, Color.LTGRAY);
        box.addView(batonText);

        box.addView(timeText);
        box.addView(statusText);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            dp(320), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = dp(10);
        p.y = dp(100);

        panel = box;
        wm.addView(panel, p);

        header.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int ox, oy;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    sx = e.getRawX(); sy = e.getRawY();
                    ox = p.x; oy = p.y;
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    p.x = ox + (int)(sx - e.getRawX());
                    p.y = oy + (int)(e.getRawY() - sy);
                    wm.updateViewLayout(panel, p);
                    return true;
                }
                return e.getAction() == MotionEvent.ACTION_UP;
            }
        });
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String rupiah(double n) {
        return NumberFormat.getIntegerInstance(new Locale("id", "ID"))
            .format((long)n);
    }

    private String changeLabel(long diff) {
        if (diff > 0) return "▲ +Rp" + rupiah(diff);
        if (diff < 0) return "▼ −Rp" + rupiah(Math.abs(diff));
        return "— Tidak berubah";
    }

    private int changeColor(long diff) {
        if (diff > 0) return Color.rgb(74, 222, 128);
        if (diff < 0) return Color.rgb(248, 113, 113);
        return Color.WHITE;
    }

    private void renderCache(String status) {
        if (panel == null) return;

        double buy = Double.longBitsToDouble(
            prefs.getLong("buy", Double.doubleToLongBits(0)));
        double sell = Double.longBitsToDouble(
            prefs.getLong("sell", Double.doubleToLongBits(0)));
        long buyDiff = prefs.getLong("buyDiff", 0);
        long sellDiff = prefs.getLong("sellDiff", 0);
        String updated = prefs.getString("updated", "Belum ada data");

        if (buy > 0 && sell > 0) {
            buyText.setText("BELI : Rp" + rupiah(buy));
            buyText.setTextColor(changeColor(buyDiff));
            buyChangeText.setText(changeLabel(buyDiff));
            buyChangeText.setTextColor(changeColor(buyDiff));

            sellText.setText("JUAL : Rp" + rupiah(sell));
            sellText.setTextColor(changeColor(sellDiff));
            sellChangeText.setText(changeLabel(sellDiff));
            sellChangeText.setTextColor(changeColor(sellDiff));

            spreadText.setText(String.format(Locale.US,
                "SPREAD  %.2f%%", (buy - sell) / buy * 100));
            timeText.setText("Data API: " + updated);
        }
        if (batonText != null) {
            batonText.setText("BATON Rp" + rupiah(prefs.getLong("baton", 2477000)));
        }
        statusText.setText(status);
    }

    private void sendPriceAlert(String label, long diff, double price) {
        if (diff == 0) return;

        String direction = diff > 0 ? "NAIK" : "TURUN";
        String amount = (diff > 0 ? "+" : "−") + "Rp" + rupiah(Math.abs(diff));
        String detail = label + " " + direction + " " + amount
            + " | Harga sekarang Rp" + rupiah(price);

        int id = (int)(System.currentTimeMillis() & 0x7fffffff);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, "ts_price_alerts")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("TS Gold • " + label + " " + direction)
            .setContentText(amount)
            .setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();

        try {
            getSystemService(NotificationManager.class).notify(id, notification);
        } catch (SecurityException ignored) {
            // Android mungkin memblokir notifikasi jika izinnya belum diberikan.
        }
    }

    private void scheduleNextMinute() {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.SECOND, 3);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.MINUTE, 1);
        handler.postDelayed(() -> fetchPrice(true),
            Math.max(100, next.getTimeInMillis() - now.getTimeInMillis()));
    }

    private void fetchPrice(boolean retryAllowed) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            JSONObject result = null;
            String error = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
                c.setRequestProperty("Origin",
                    "https://cuanmania.broriz-dev.workers.dev");
                c.setRequestProperty("Referer",
                    "https://cuanmania.broriz-dev.workers.dev/");
                c.getOutputStream().close();

                InputStream in = c.getResponseCode() < 400
                    ? c.getInputStream() : c.getErrorStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                in.close();

                JSONObject root = new JSONObject(out.toString("UTF-8"));
                JSONObject data = root.getJSONObject("data");
                double buy = data.getDouble("buying_rate");
                double sell = data.getDouble("selling_rate");
                String updated = data.optString("updated_at", "—");
                if (buy <= 0 || sell <= 0) throw new Exception("Harga tidak valid");
                result = new JSONObject();
                result.put("buy", buy);
                result.put("sell", sell);
                result.put("updated", updated);
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                if (c != null) c.disconnect();
            }

            final JSONObject r = result;
            final String err = error;
            handler.post(() -> {
                busy = false;
                if (r != null) {
                    double newBuy = r.optDouble("buy");
                    double newSell = r.optDouble("sell");

                    double oldBuy = Double.longBitsToDouble(
                        prefs.getLong("buy", Double.doubleToLongBits(0)));
                    double oldSell = Double.longBitsToDouble(
                        prefs.getLong("sell", Double.doubleToLongBits(0)));

                    long buyDiff = oldBuy > 0 ? Math.round(newBuy - oldBuy) : 0;
                    long sellDiff = oldSell > 0 ? Math.round(newSell - oldSell) : 0;

                    prefs.edit()
                        .putLong("buy", Double.doubleToLongBits(newBuy))
                        .putLong("sell", Double.doubleToLongBits(newSell))
                        .putLong("buyDiff", buyDiff)
                        .putLong("sellDiff", sellDiff)
                        .putString("updated", r.optString("updated"))
                        .apply();

                    if (oldBuy > 0 && buyDiff != 0)
                        sendPriceAlert("BELI", buyDiff, newBuy);
                    if (oldSell > 0 && sellDiff != 0)
                        sendPriceAlert("JUAL", sellDiff, newSell);

                    renderCache("● ONLINE");
                    scheduleNextMinute();
                } else {
                    renderCache("● Gagal mengambil harga");
                    if (retryAllowed) {
                        handler.postDelayed(() -> fetchPrice(false), 2000);
                    } else {
                        scheduleNextMinute();
                    }
                }
            });
        }).start();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
            panel = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
