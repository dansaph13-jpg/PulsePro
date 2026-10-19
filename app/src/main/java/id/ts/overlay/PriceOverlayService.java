package id.ts.overlay;

import id.tsgold.pulsepro.R;

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
    private View floatingButton;
    private View floatingButtonTS;
    private long lastFloatingClickAt = 0L;
    private View edgeTrigger;
    private AlertDialog menuDialog;
    private final Runnable autoHideFloatingTask = this::hideFloatingButton;
    private TextView display;
    private TextView buyPrice;
    private TextView buyDelta;
    private TextView sellPrice;
    private TextView sellDelta;
    private TextView spreadValue;
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
                    "mm:ss", java.util.Locale.getDefault()
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
        addFloatingButton();
        addEdgeTrigger();
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




    private WindowManager.LayoutParams floatingParams(int y) {
        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            dp(48), dp(48), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.RIGHT;
        p.x = dp(10);
        p.y = y;
        return p;
    }

    private View createFloatButton(String label, int fill, Runnable action) {
        TextView v = new TextView(this);
        v.setBackgroundResource(R.drawable.bg_ft_shield);

        v.setText(label);
        v.setTextColor(0xFFFFD875);
        v.setTextSize(label.equals("TS") ? 14 : 18);
        v.setTypeface(android.graphics.Typeface.create(
            "sans-serif-condensed", android.graphics.Typeface.BOLD));
        v.setGravity(Gravity.CENTER);

        android.graphics.Path outerPath = new android.graphics.Path();
        outerPath.moveTo(50, 3);
        outerPath.lineTo(94, 18);
        outerPath.lineTo(88, 59);
        outerPath.quadTo(82, 82, 50, 97);
        outerPath.quadTo(18, 82, 12, 59);
        outerPath.lineTo(6, 18);
        outerPath.close();

        android.graphics.Path innerPath = new android.graphics.Path();
        innerPath.moveTo(50, 10);
        innerPath.lineTo(86, 22);
        innerPath.lineTo(81, 57);
        innerPath.quadTo(76, 77, 50, 89);
        innerPath.quadTo(24, 77, 19, 57);
        innerPath.lineTo(14, 22);
        innerPath.close();

        android.graphics.drawable.ShapeDrawable outer =
            new android.graphics.drawable.ShapeDrawable(
                new android.graphics.drawable.shapes.PathShape(
                    outerPath, 100, 100));
        outer.getPaint().setColor(0xFFD8B15A);
        outer.getPaint().setAntiAlias(true);

        android.graphics.drawable.ShapeDrawable inner =
            new android.graphics.drawable.ShapeDrawable(
                new android.graphics.drawable.shapes.PathShape(
                    innerPath, 100, 100));
        inner.getPaint().setColor(fill);
        inner.getPaint().setAntiAlias(true);

        v.setBackground(new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[]{outer, inner}));
        v.setElevation(dp(8));

        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final int[] startX = new int[1];
        final int[] startY = new int[1];
        final long[] downTime = new long[1];
        final boolean[] moved = new boolean[1];

        v.setOnClickListener(view -> {
            handler.removeCallbacks(autoHideFloatingTask);
            handler.postDelayed(autoHideFloatingTask, 600000L);
            action.run();
        });

        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    downTime[0] = android.os.SystemClock.elapsedRealtime();
                    moved[0] = false;

                    android.view.ViewGroup.LayoutParams raw =
                        view.getLayoutParams();
                    if (raw instanceof WindowManager.LayoutParams) {
                        WindowManager.LayoutParams p =
                            (WindowManager.LayoutParams) raw;
                        startX[0] = p.x;
                        startY[0] = p.y;
                    }
                    return true;
                }

                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX[0];
                    float dy = event.getRawY() - downY[0];

                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                        moved[0] = true;
                    }

                    if (moved[0] && wm != null) {
                        android.view.ViewGroup.LayoutParams raw =
                            view.getLayoutParams();
                        if (raw instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams p =
                                (WindowManager.LayoutParams) raw;
                            p.x = Math.max(0, startX[0] - (int) dx);
                            p.y = Math.max(0, startY[0] + (int) dy);
                            try {
                                wm.updateViewLayout(view, p);
                            } catch (Exception ignored) {}
                        }
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP: {
                    long held = android.os.SystemClock.elapsedRealtime()
                        - downTime[0];

                    if (!moved[0] && held >= 600L) {
                        handler.removeCallbacks(autoHideFloatingTask);
                        hideFloatingButton();
                    } else if (!moved[0]) {
                        view.performClick();
                    }
                    return true;
                }

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return true;
        });

        v.setBackgroundResource(R.drawable.bg_ft_shield);
        return v;
    }

    private void addFloatingButton() {
        if (wm == null) wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        if (floatingButton == null) {
            floatingButton = createFloatButton("C", 0xFF0A3B30, () -> openMiniDb());
            WindowManager.LayoutParams p = floatingParams(dp(220));
            try { wm.addView(floatingButton, p); }
            catch (Exception e) { floatingButton = null; }
        }
        if (floatingButtonTS == null) {
            floatingButtonTS = createFloatButton("TS", 0xFF153A32, () -> { if (panel == null) addOverlay(); else hideOverlay(); });
            WindowManager.LayoutParams p = floatingParams(dp(276));
            try { wm.addView(floatingButtonTS, p); }
            catch (Exception e) { floatingButtonTS = null; }
        }

        handler.removeCallbacks(autoHideFloatingTask);
        handler.postDelayed(autoHideFloatingTask, 600000L);
    }

    private void showFloatingButton() {
        if (floatingButton == null || floatingButtonTS == null) addFloatingButton();
        if (floatingButton != null) floatingButton.setVisibility(View.VISIBLE);
        if (floatingButtonTS != null) floatingButtonTS.setVisibility(View.VISIBLE);
        handler.removeCallbacks(autoHideFloatingTask);
        handler.postDelayed(autoHideFloatingTask, 600000L);
    }

    private void hideFloatingButton() {
        if (floatingButton != null) floatingButton.setVisibility(View.GONE);
        if (floatingButtonTS != null) floatingButtonTS.setVisibility(View.GONE);
    }

    private void addEdgeTrigger() {
        if (edgeTrigger != null || wm == null) return;
        View edge = new View(this);
        edge.setBackgroundColor(Color.TRANSPARENT);
        final float[] downX = new float[1];
        final float[] downY = new float[1];

        edge.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX[0] = e.getRawX();
                downY[0] = e.getRawY();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getRawX() - downX[0];
                float dy = e.getRawY() - downY[0];
                if (dx >= dp(48) && Math.abs(dx) > Math.abs(dy)) {
                    showFloatingButton();
                }
                return true;
            }
            return true;
        });

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            dp(12), WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = 0;
        p.y = 0;
        edgeTrigger = edge;
        try { wm.addView(edgeTrigger, p); }
        catch (Exception e) { edgeTrigger = null; }
    }

    private void openMiniDb() {
        Intent i = new Intent(this, MasterDataActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            android.util.Log.e("PulsePro", "Gagal membuka Mini DB", e);
        }
    }

    private void showMenu() {
        if (wm == null) wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        if (menuDialog != null && menuDialog.isShowing()) {
            menuDialog.dismiss();
            menuDialog = null;
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("CUAN HUNTER");
        b.setItems(new String[]{"MASTER DATA"}, (dialog, which) -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("open_master_data", true);
            try {
                startActivity(i);
            } catch (Exception ignored) {}
        });

        menuDialog = b.create();
        try {
            if (Build.VERSION.SDK_INT >= 26
                    && menuDialog.getWindow() != null) {
                menuDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            }
        } catch (Exception ignored) {}

        try {
            menuDialog.show();
        } catch (Exception e) {
            menuDialog = null;
        }
    }

    private void addOverlay() {
        if (panel != null || wm == null) return;
        final int d = (int)(getResources().getDisplayMetrics().density + 0.5f);
        final int navy = Color.rgb(5, 35, 29);
        final int emerald = Color.rgb(0, 230, 150);
        final int muted = Color.rgb(150, 171, 184);
        final int white = Color.rgb(239, 247, 247);
        final int gold = Color.rgb(212, 175, 55);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(10*d, 8*d, 10*d, 10*d);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(navy);
        bg.setCornerRadius(14*d);
        bg.setStroke(Math.max(1, d), gold);
        root.setBackground(bg);


        android.widget.LinearLayout priceRow = new android.widget.LinearLayout(this);
        priceRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);

        // KOLOM BELI
        android.widget.LinearLayout buyCard = new android.widget.LinearLayout(this);
        buyCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        buyCard.setGravity(Gravity.CENTER_VERTICAL);
        buyCard.setPadding(2*d, 5*d, 2*d, 5*d);

        GradientDrawable buyBg = new GradientDrawable();
        buyBg.setColor(Color.rgb(5, 43, 33));
        buyBg.setCornerRadius(7*d);
        buyCard.setBackground(buyBg);

        buyPrice = new TextView(this);
        buyPrice.setText("Menunggu...");
        buyPrice.setTextColor(white);
        buyPrice.setTextSize(20);
        buyPrice.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        buyPrice.setSingleLine(true);
        buyPrice.setIncludeFontPadding(false);
        buyPrice.setHorizontallyScrolling(true);
        buyCard.addView(buyPrice,
            new android.widget.LinearLayout.LayoutParams(-1, -2));

        buyDelta = new TextView(this);
        buyDelta.setText("Menunggu data");
        buyDelta.setTextColor(muted);
        buyDelta.setTextSize(12);
        buyDelta.setSingleLine(true);
        buyDelta.setIncludeFontPadding(false);
        buyCard.addView(buyDelta,
            new android.widget.LinearLayout.LayoutParams(-1, -2));

        android.widget.LinearLayout.LayoutParams buyLp =
            new android.widget.LinearLayout.LayoutParams(0, 84*d, 1.20f);
        buyLp.rightMargin = 4*d;
        priceRow.addView(buyCard, buyLp);

        // PEMISAH GOLD 1: BELI | JUAL
        View separator1 = new View(this);
        separator1.setBackgroundColor(gold);
        priceRow.addView(separator1,
            new android.widget.LinearLayout.LayoutParams(Math.max(1, d), 64*d));

        // KOLOM JUAL
        android.widget.LinearLayout sellCard = new android.widget.LinearLayout(this);
        sellCard.setOrientation(android.widget.LinearLayout.VERTICAL);
        sellCard.setGravity(Gravity.CENTER_VERTICAL);
        sellCard.setPadding(4*d, 5*d, 2*d, 5*d);

        GradientDrawable sellBg = new GradientDrawable();
        sellBg.setColor(Color.rgb(10, 59, 45));
        sellBg.setCornerRadius(7*d);
        sellCard.setBackground(sellBg);

        sellPrice = new TextView(this);
        sellPrice.setText("—");
        sellPrice.setTextColor(white);
        sellPrice.setTextSize(16);
        sellPrice.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        sellPrice.setSingleLine(true);
        sellPrice.setIncludeFontPadding(false);
        sellPrice.setHorizontallyScrolling(true);
        sellCard.addView(sellPrice,
            new android.widget.LinearLayout.LayoutParams(-1, -2));

        sellDelta = new TextView(this);
        sellDelta.setText("Menunggu");
        sellDelta.setTextColor(muted);
        sellDelta.setTextSize(12);
        sellDelta.setSingleLine(true);
        sellDelta.setIncludeFontPadding(false);
        sellCard.addView(sellDelta,
            new android.widget.LinearLayout.LayoutParams(-1, -2));

        android.widget.LinearLayout.LayoutParams sellLp =
            new android.widget.LinearLayout.LayoutParams(0, 84*d, 1.0f);
        sellLp.leftMargin = 4*d;
        sellLp.rightMargin = 4*d;
        priceRow.addView(sellCard, sellLp);

        // PEMISAH GOLD 2: JUAL | JAM + SPREAD
        View separator2 = new View(this);
        separator2.setBackgroundColor(gold);
        priceRow.addView(separator2,
            new android.widget.LinearLayout.LayoutParams(Math.max(1, d), 64*d));

        // KOLOM KANAN: JAM DI ATAS, SPREAD DI BAWAH
        android.widget.LinearLayout rightColumn =
            new android.widget.LinearLayout(this);
        rightColumn.setOrientation(android.widget.LinearLayout.VERTICAL);
        rightColumn.setGravity(Gravity.CENTER);
        rightColumn.setPadding(2*d, 0, 0, 0);

        clock = new TextView(this);
        clock.setText("00:00");
        clock.setTextColor(emerald);
        clock.setTextSize(12);
        clock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clock.setSingleLine(true);
        clock.setGravity(Gravity.CENTER);
        clock.setIncludeFontPadding(false);
        rightColumn.addView(clock,
            new android.widget.LinearLayout.LayoutParams(-1, 24*d));

        spreadValue = new TextView(this);
        spreadValue.setText("—");
        spreadValue.setTextColor(emerald);
        spreadValue.setTextSize(12);
        spreadValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        spreadValue.setSingleLine(true);
        spreadValue.setGravity(Gravity.CENTER);
        spreadValue.setIncludeFontPadding(false);
        rightColumn.addView(spreadValue,
            new android.widget.LinearLayout.LayoutParams(-1, 24*d));

        priceRow.addView(rightColumn,
            new android.widget.LinearLayout.LayoutParams(0, 64*d, 0.75f));

        android.widget.LinearLayout.LayoutParams priceRowLp =
            new android.widget.LinearLayout.LayoutParams(-1, 90*d);
        priceRowLp.topMargin = 4*d;
        root.addView(priceRow, priceRowLp);

        panel = root;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            320*d, WindowManager.LayoutParams.WRAP_CONTENT,
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
            buyPrice.setTextColor(Color.rgb(239, 247, 247));
            buyDelta.setText("Mengumpulkan data harga");
            buyDelta.setTextColor(Color.GRAY);
            sellPrice.setText("—");
            sellDelta.setText("Menunggu data 1 menit");
            sellDelta.setTextColor(Color.GRAY);
            spreadValue.setText("—");
            return;
        }

        buyPrice.setText(rupiah(buy));
        sellPrice.setText(rupiah(sell));

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
            String sellArrow = sd > 0 ? "▲ " : sd < 0 ? "▼ " : "● ";
            sellDelta.setText(sellArrow + rupiah(Math.abs(sd))
                   );
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
            String arrow = d > 0 ? "▲ " : d < 0 ? "▼ " : "● ";
            buyDelta.setText(arrow + rupiah(Math.abs(d))
               );
        }

        buyPrice.setTextColor(color);
        buyDelta.setTextColor(color);
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
        sellDelta = null;
        spreadValue = null;
        clock = null;
    }

    private Notification buildNotification() {
        String line;

        if (buy > 0 && sell > 0) {
            double br = minuteReference();
            double sr = sellMinuteReference();

            String ba = br <= 0 ? "⚪" : buy > br ? "🟢" : buy < br ? "🔴" : "⚪";
            String sa = sr <= 0 ? "⚪" : sell > sr ? "🟢" : sell < sr ? "🔴" : "⚪";

            String bd = br <= 0 ? "—" : rupiah(Math.abs(buy - br));
            String sd = sr <= 0 ? "—" : rupiah(Math.abs(sell - sr));

            String sp = String.format(Locale.US, "%.2f%%",
                Math.abs(buy - sell) / buy * 100.0);

            line = rupiah(buy) + " " + ba + " " + bd
                + " | " + rupiah(sell) + " " + sa + " " + sd
                + " | " + sp;
        } else {
            line = "Menunggu data harga Treasury";
        }

        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);

        return b.setContentTitle(line)
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
        if (running && Settings.canDrawOverlays(this)) {
            showFloatingButton();
            if (edgeTrigger == null) addEdgeTrigger();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (floatingButtonTS != null && wm != null) {
            try { wm.removeView(floatingButtonTS); } catch (Exception ignored) {}
            floatingButtonTS = null;
        }

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
        panel = null;
        if (menuDialog != null) {
            try { menuDialog.dismiss(); } catch (Exception ignored) {}
            menuDialog = null;
        }
        if (floatingButton != null && wm != null) {
            try { wm.removeView(floatingButton); } catch (Exception ignored) {}
        }
        floatingButton = null;
        if (edgeTrigger != null && wm != null) {
            try { wm.removeView(edgeTrigger); } catch (Exception ignored) {}
        }
        edgeTrigger = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
