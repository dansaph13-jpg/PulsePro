package id.ts.overlay;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Locale;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;
import java.util.Calendar;
import java.util.ArrayList;
import java.net.URL;
import java.net.HttpURLConnection;
import org.json.JSONObject;
import org.json.JSONArray;
import android.os.Looper;
import android.os.Handler;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;
import android.app.AlertDialog;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class USNewsActivity extends Activity {

    private final int BG = Color.rgb(17, 24, 39);
    private final int PANEL = Color.rgb(31, 41, 55);
    private final int GOLD = Color.rgb(212, 175, 55);
    private final int EMERALD = Color.rgb(16, 120, 80);

    private LinearLayout root;
    private LinearLayout content;
    private WebView calendarView;
    private Button calendarTab;
    private Button analysisTab;
    private String currentTab = "calendar";

    private String selectedPeriod = "Hari Ini";
    private LinearLayout analysisPanel;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService apiExecutor = Executors.newSingleThreadExecutor();
    private static final String PREFS = "pulsepro_news_settings";
    private static final String PULSEPRO_API_KEY = "earnings_api_key";


    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("NEWS UPDATE");
        title.setTextColor(GOLD);
        title.setTextSize(23);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(5), 0, dp(14));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        calendarTab = new Button(this);
        calendarTab.setText("KALENDER");
        calendarTab.setAllCaps(false);

        analysisTab = new Button(this);
        analysisTab.setText("ANALISIS USD/XAU");
        analysisTab.setAllCaps(false);

        tabs.addView(analysisTab,
                new LinearLayout.LayoutParams(0, dp(48), 1));
        tabs.addView(calendarTab,
                new LinearLayout.LayoutParams(0, dp(48), 1));

        root.addView(tabs);

content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content,
                new LinearLayout.LayoutParams(-1, 0, 1));

        calendarView = new WebView(this);
        WebSettings settings = calendarView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        calendarView.setWebViewClient(new WebViewClient());
        calendarView.setWebChromeClient(new WebChromeClient());

        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width," +
            "initial-scale=1,maximum-scale=3,user-scalable=yes'>" +
            "<meta charset='UTF-8'>" +
            "<style>" +
            "html,body{margin:0;padding:0;background:#111827;" +
            "font-family:Arial,sans-serif;color:#fff;}" +
            "iframe{display:block;width:100%;height:900px;border:0;}" +
            ".credit{font-size:11px;padding:10px 4px;color:#ddd;}" +
            "a{color:#65a9ff;}" +
            "</style></head><body>" +
            "<iframe title='Kalender Ekonomi Investing.com' " +
            "src='https://sslecal2.investing.com?" +
            "columns=exc_flags,exc_currency,exc_importance," +
            "exc_actual,exc_forecast,exc_previous" +
            "&amp;category=_employment,_economicActivity,_inflation," +
            "_credit,_centralBanks,_confidenceIndex,_balance,_Bonds" +
            "&amp;importance=2,3" +
            "&amp;features=datepicker" +
            "&amp;countries=5,48" +
            "&amp;calType=week" +
            "&amp;timeZone=27" +
            "&amp;lang=54' " +
            "allowtransparency='true' loading='eager'></iframe>" +
            "<div class='credit'>Kalender ekonomi disediakan oleh " +
            "<a href='https://id.investing.com' " +
            "target='_blank'>Investing.com Indonesia</a></div>" +
            "</body></html>";

        calendarView.loadDataWithBaseURL(
                "https://id.investing.com/",
                html, "text/html", "UTF-8", null);

        calendarTab.setOnClickListener(v -> showCalendar());
        analysisTab.setOnClickListener(v -> showAnalysis());

setContentView(root);
        showAnalysis();
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                selected ? EMERALD : PANEL));
    }

    private void showCalendar() {
        currentTab = "calendar";
        styleTab(calendarTab, true);
        styleTab(analysisTab, false);

        content.removeAllViews();
        if (calendarView.getParent() != null) {
            ((android.view.ViewGroup) calendarView.getParent())
                    .removeView(calendarView);
        }
        content.addView(calendarView,
                new LinearLayout.LayoutParams(-1, -1));
    }


    private void showAnalysis() {
        currentTab = "analysis";
        styleTab(analysisTab, true);
        styleTab(calendarTab, false);

        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        analysisPanel = new LinearLayout(this);
        analysisPanel.setOrientation(LinearLayout.VERTICAL);
        analysisPanel.setPadding(dp(8), dp(10), dp(8), dp(18));
        scroll.addView(analysisPanel);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = makeText("ANALISIS USD/XAU", 18, GOLD, true);
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button refresh = new Button(this);
        refresh.setText("↻");
        refresh.setTextSize(23);
        refresh.setAllCaps(false);
        refresh.setContentDescription("Muat ulang data");
        refresh.setTextColor(Color.WHITE);
        refresh.setBackgroundTintList(android.content.res.ColorStateList.valueOf(EMERALD));
        headerRow.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
        refresh.setOnClickListener(v -> showAnalysis());
        analysisPanel.addView(headerRow);

        LinearLayout periods = new LinearLayout(this);
        periods.setOrientation(LinearLayout.HORIZONTAL);
        periods.setGravity(Gravity.CENTER);
        String[] labels = {"Hari Ini", "Besok", "Lusa", "Pekan Ini", "Pekan Depan"};
        for (String label : labels) {
            Button b = new Button(this);
            b.setText(label);
            b.setAllCaps(false);
            b.setTextSize(10);
            b.setPadding(dp(2), 0, dp(2), 0);
            b.setTextColor(Color.WHITE);
            b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    label.equals(selectedPeriod) ? EMERALD : PANEL));
            LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, dp(44), 1);
            lp.setMargins(dp(1), 0, dp(1), dp(8));
            periods.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedPeriod = label;
                showAnalysis();
            });
        }
        analysisPanel.addView(periods);

        TextView source = makeText(
            "SUMBER: Earnings API | P = Previous, F = Forecast, A = Actual",
            11, Color.LTGRAY, false);
        source.setPadding(dp(4), 0, dp(4), dp(8));
        analysisPanel.addView(source);

        Button changeKey = new Button(this);
        changeKey.setText("GANTI API KEY");
        changeKey.setAllCaps(false);
        changeKey.setTextSize(12);
        changeKey.setOnClickListener(v -> showApiKeyDialog());
        analysisPanel.addView(changeKey,
            new LinearLayout.LayoutParams(-1, dp(42)));

        String key = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PULSEPRO_API_KEY, "");
        if (key.trim().isEmpty()) {
            addNotice(analysisPanel, "KUNCI API BELUM DIATUR",
                "Tekan GANTI API KEY untuk memasukkan kunci Earnings API.");
            return;
        }

        addNotice(analysisPanel, "MEMUAT DATA BERITA...",
                "Mengambil jadwal sesuai periode yang dipilih.");
        loadEconomicEvents(key, selectedPeriod);
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Masukkan API key Earnings");
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
            .setTitle("API KEY EARNINGS")
            .setMessage("Key disimpan di penyimpanan privat aplikasi. "
                + "Jangan bagikan APK yang berisi kredensial pribadi.")
            .setView(input)
            .setNegativeButton("BATAL", (d, w) -> {})
            .setPositiveButton("SIMPAN", (d, w) -> {
                String key = input.getText().toString().trim();
                if (key.isEmpty()) {
                    Toast.makeText(this, "API key masih kosong",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(PULSEPRO_API_KEY, key).apply();
                showAnalysis();
            })
            .show();
    }

    private List<String> datesForPeriod(String period) {
        List<String> dates = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if ("Hari Ini".equals(period)) {
            dates.add(formatDate(c));
        } else if ("Besok".equals(period)) {
            c.add(Calendar.DAY_OF_YEAR, 1);
            dates.add(formatDate(c));
        } else if ("Lusa".equals(period)) {
            c.add(Calendar.DAY_OF_YEAR, 2);
            dates.add(formatDate(c));
        } else if ("Pekan Ini".equals(period)) {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            int daysToSunday = (Calendar.SUNDAY - dow + 7) % 7;
            for (int i = 0; i <= daysToSunday; i++) {
                dates.add(formatDate(c));
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            int daysToNextMonday = (Calendar.MONDAY - dow + 7) % 7;
            if (daysToNextMonday == 0) daysToNextMonday = 7;
            c.add(Calendar.DAY_OF_YEAR, daysToNextMonday);
            for (int i = 0; i < 7; i++) {
                dates.add(formatDate(c));
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return dates;
    }

    private String formatDate(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH));
    }

    private void loadEconomicEvents(String key, String period) {
        final List<String> dates = datesForPeriod(period);
        apiExecutor.execute(() -> {
            List<JSONObject> all = new ArrayList<>();
            String error = null;
            for (String date : dates) {
                HttpURLConnection conn = null;
                try {
                    String urlText =
                        "https://api.earningsapi.com/v1/calendar/economic"
                        + "?date=" + date
                        + "&apikey=" + java.net.URLEncoder.encode(key, "UTF-8");
                    conn = (HttpURLConnection) new URL(urlText).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("User-Agent", "PulsePro/1.0");

                    int status = conn.getResponseCode();
                    java.io.InputStream stream = status >= 200 && status < 300
                        ? conn.getInputStream() : conn.getErrorStream();
                    if (status < 200 || status >= 300) {
                        if (stream != null) stream.close();
                        error = "API mengembalikan HTTP " + status
                            + ". Periksa key, kuota, atau akses API.";
                        continue;
                    }

                    java.io.ByteArrayOutputStream out =
                        new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = stream.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                    stream.close();

                    String body = out.toString("UTF-8");
                    JSONArray events;
                    if (body.trim().startsWith("[")) {
                        events = new JSONArray(body);
                    } else {
                        JSONObject root = new JSONObject(body);
                        events = root.optJSONArray("data");
                        if (events == null) {
                            events = root.optJSONArray("events");
                        }
                        if (events == null) {
                            error = "Format respons API tidak dikenali: "
                                + "array data/events tidak ditemukan.";
                            continue;
                        }
                    }

                    for (int i = 0; i < events.length(); i++) {
                        JSONObject e = events.optJSONObject(i);
                        if (e == null) continue;
                        JSONObject item = new JSONObject();
                        item.put("date", e.optString("date", date));
                        item.put("time", e.optString("time", ""));
                        item.put("event", e.optString("eventName",
                            e.optString("event", e.optString("name", "Berita"))));
                        item.put("country", e.optString("country", ""));
                        item.put("previous", e.optString("previous", ""));
                        item.put("forecast", e.optString("consensus",
                            e.optString("forecast", "")));
                        item.put("actual", e.optString("actual", ""));
                        all.add(item);
                    }
                } catch (Exception ex) {
                    error = "Gagal mengambil data (" +
                        ex.getClass().getSimpleName() + ").";
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            Collections.sort(all, (a, b) -> {
                int d = a.optString("date").compareTo(b.optString("date"));
                return d != 0 ? d :
                    a.optString("time").compareTo(b.optString("time"));
            });

            final String finalError = error;
            mainHandler.post(() -> {
                if (isFinishing() || !"analysis".equals(currentTab)
                        || !period.equals(selectedPeriod)
                        || analysisPanel == null) return;
                renderEconomicEvents(all, finalError, dates);
            });
        });
    }

    private void renderEconomicEvents(List<JSONObject> events,
                                      String error, List<String> dates) {
        if (analysisPanel == null) return;
        analysisPanel.removeAllViews();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = makeText("ANALISIS USD/XAU", 18, GOLD, true);
        headerRow.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button refresh = new Button(this);
        refresh.setText("↻");
        refresh.setTextSize(23);
        refresh.setAllCaps(false);
        refresh.setContentDescription("Muat ulang data");
        refresh.setTextColor(Color.WHITE);
        refresh.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(EMERALD));
        headerRow.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
        refresh.setOnClickListener(v -> showAnalysis());
        analysisPanel.addView(headerRow);

        LinearLayout periods = new LinearLayout(this);
        periods.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Hari Ini", "Besok", "Lusa", "Pekan Ini", "Pekan Depan"};
        for (String label : labels) {
            Button b = new Button(this);
            b.setText(label);
            b.setAllCaps(false);
            b.setTextSize(10);
            b.setTextColor(Color.WHITE);
            b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    label.equals(selectedPeriod) ? EMERALD : PANEL));
            periods.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
            b.setOnClickListener(v -> {
                selectedPeriod = label;
                showAnalysis();
            });
        }
        analysisPanel.addView(periods);

        TextView info = makeText(
            "Earnings API • P: Previous • F: Forecast • A: Actual",
            11, Color.LTGRAY, false);
        info.setPadding(dp(4), dp(6), dp(4), dp(8));
        analysisPanel.addView(info);

        Button changeKey = new Button(this);
        changeKey.setText("GANTI API KEY");
        changeKey.setAllCaps(false);
        changeKey.setTextSize(12);
        changeKey.setOnClickListener(v -> showApiKeyDialog());
        analysisPanel.addView(changeKey,
            new LinearLayout.LayoutParams(-1, dp(42)));

        if (error != null) {
            addNotice(analysisPanel, "PERINGATAN DATA BERITA", error);
        }
        List<JSONObject> filteredEvents = new ArrayList<>();
        for (JSONObject event : events) {
            String country = event.optString("country", "").trim();
            String name = event.optString("event", "").toLowerCase(Locale.ROOT);

            boolean isUnitedStates = "United States".equalsIgnoreCase(country)
                || "USA".equalsIgnoreCase(country)
                || "US".equalsIgnoreCase(country);

            boolean relevant = name.contains("cpi")
                || name.contains("inflation rate")
                || name.contains("inflation expectations")
                || name.contains("pce price")
                || name.contains("personal consumption expenditures")
                || name.contains("payroll")
                || name.contains("nonfarm")
                || name.contains("employment change")
                || name.contains("average hourly earnings")
                || name.contains("average earnings")
                || name.contains("unemployment")
                || name.contains("initial jobless claims")
                || name.contains("continuing jobless claims")
                || name.contains("interest rate")
                || name.contains("federal funds")
                || name.contains("fomc")
                || name.contains("fed chair")
                || (name.contains("s&p global")
                    && name.contains("pmi"))
                || (name.contains("gdp")
                    && !name.contains("gdpnow")
                    && !name.contains("atlanta fed"));

            if (isUnitedStates && relevant) filteredEvents.add(event);
        }

        if (filteredEvents.isEmpty()) {
            addNotice(analysisPanel, "BELUM ADA BERITA UTAMA AS",
                "Tidak ada event AS yang sesuai filter untuk periode ini. "
                + "Coba pilih periode lain.");
            return;
        }

        TextView count = makeText(
            "BERITA UTAMA AS: " + filteredEvents.size(), 13, GOLD, true);
        count.setPadding(dp(4), dp(4), dp(4), dp(10));
        analysisPanel.addView(count);

        for (JSONObject e : filteredEvents) {
            String name = e.optString("event", "Berita ekonomi");
            String country = e.optString("country", "");
            String date = e.optString("date", "");
            String time = e.optString("time", "");
            String previous = displayValue(e.optString("previous", ""));
            String forecast = displayValue(e.optString("forecast", ""));
            String actual = displayValue(e.optString("actual", ""));

            String bias = calculateBias(name,
                e.optString("forecast", ""), e.optString("previous", ""),
                e.optString("actual", ""));

            StringBuilder body = new StringBuilder();
            if (!date.isEmpty()) body.append(date);
            if (!time.isEmpty() && !"null".equalsIgnoreCase(time)) {
                if (body.length() > 0) body.append(" • ");
                body.append(time);
            }
            body.append("\nP ").append(previous)
                .append(" | F ").append(forecast)
                .append(" | A ").append(actual)
                .append("\nUSD: ").append(bias.split("\\|")[0])
                .append("\nGOLD: ").append(bias.split("\\|")[1]);

            addNotice(analysisPanel, terjemahkanEvent(name), body.toString());
        }
    }

    private String terjemahkanEvent(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("initial jobless claims")) return "Klaim Pengangguran Awal AS";
        if (n.contains("continuing jobless claims")) return "Klaim Pengangguran Lanjutan AS";
        if (n.contains("manufacturing pmi")) return "PMI Manufaktur AS";
        if (n.contains("services pmi")) return "PMI Jasa AS";
        if (n.contains("composite pmi")) return "PMI Gabungan AS";
        if (n.contains("inflation expectations")) return "Ekspektasi Inflasi AS";
        if (n.contains("employment change")) return "Perubahan Ketenagakerjaan AS";
        if (n.contains("federal funds") || n.contains("interest rate")) return "Suku Bunga AS";
        if (n.contains("nonfarm") || n.contains("payroll")) return "Data Penggajian AS";
        if (n.contains("retail sales")) return "Penjualan Ritel AS";
        if (n.contains("cpi")) return "Indeks Harga Konsumen (CPI) AS";
        if (n.contains("gdp") && !n.contains("gdpnow")) return "Produk Domestik Bruto (GDP) AS";
        return name;
    }

    private String displayValue(String value) {
        if (value == null || value.trim().isEmpty()
                || "null".equalsIgnoreCase(value.trim())) return "—";
        return value.trim();
    }

    private double parseNumber(String value) {
        if (value == null) return Double.NaN;
        String s = value.trim().replace(",", "");
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)")
            .matcher(s);
        if (!m.find()) return Double.NaN;
        try { return Double.parseDouble(m.group()); }
        catch (Exception ex) { return Double.NaN; }
    }

    private String calculateBias(String name, String forecast,
                                 String previous, String actual) {
        boolean hasActual = actual != null
            && !actual.trim().isEmpty()
            && !"null".equalsIgnoreCase(actual.trim());

        if (!hasActual) {
            return "MENUNGGU DATA AKTUAL|MENUNGGU DATA AKTUAL";
        }

        double f = parseNumber(forecast);
        double a = parseNumber(actual);

        if (Double.isNaN(a) || Double.isNaN(f)) {
            return "NETRAL / BELUM CUKUP DATA|NETRAL / BELUM CUKUP DATA";
        }

        double compare = a - f;
        String direction = "pasca-rilis";

        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);

        boolean inverse = n.contains("unemployment")
            || n.contains("jobless")
            || n.contains("claimant")
            || n.contains("continuing claims");

        boolean relevant = inverse
            || n.contains("cpi")
            || n.contains("inflation")
            || n.contains("pce price")
            || n.contains("payroll")
            || n.contains("nonfarm")
            || n.contains("employment change")
            || n.contains("average earnings")
            || n.contains("retail sales")
            || n.contains("gdp")
            || n.contains("manufacturing pmi")
            || n.contains("services pmi")
            || n.contains("interest rate")
            || n.contains("federal funds");

        if (!relevant || compare == 0.0) {
            return "NETRAL / PERLU KONTEKS|NETRAL / PERLU KONTEKS";
        }

        boolean usdPositive = inverse ? compare < 0 : compare > 0;

        String usd = (usdPositive
            ? "CENDERUNG MENDUKUNG USD"
            : "CENDERUNG MENEKAN USD") + " (" + direction + ")";

        String xau = (usdPositive
            ? "CENDERUNG MENEKAN XAU"
            : "CENDERUNG MENDUKUNG XAU") + " (" + direction + ")";

        return usd + "|" + xau;
    }

    private void addNotice(LinearLayout parent,
                           String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(PANEL);
        shape.setCornerRadius(dp(10));
        shape.setStroke(dp(1), GOLD);
        card.setBackground(shape);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(card, params);

        TextView title = makeText(heading, 14, GOLD, true);
        title.setPadding(0, 0, 0, dp(8));
        card.addView(title);

        TextView detail = makeText(body, 13, Color.WHITE, false);
        detail.setLineSpacing(dp(3), 1f);
        card.addView(detail);
    }

    private TextView makeText(String text, int size,
                              int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    @Override
    protected void onDestroy() {
        apiExecutor.shutdownNow();
        if (calendarView != null) {
            calendarView.stopLoading();
            calendarView.setWebChromeClient(null);
            calendarView.setWebViewClient(null);
            calendarView.destroy();
            calendarView = null;
        }

        super.onDestroy();
    }
}
