package id.ts.overlay;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;

public class USNewsActivity extends Activity {
    LinearLayout list;
    TextView status;
    Handler handler = new Handler(Looper.getMainLooper());
    ExecutorService worker = Executors.newSingleThreadExecutor();
    boolean active = false;

    int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(17,24,39));
        getWindow().setNavigationBarColor(Color.rgb(17,24,39));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(10));
        root.setBackgroundColor(Color.rgb(17,24,39));

        TextView title = new TextView(this);
        title.setText("today".equals(getIntent().getStringExtra("period"))
            ? "🇺🇸 US NEWS HARI INI" : "🇺🇸 US NEWS MINGGUAN");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(212,175,55));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("KALENDER EKONOMI AS • WAKTU WIB");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(12);
        root.addView(subtitle);

        Button refresh = new Button(this);
        refresh.setText("PERBARUI DATA");
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadData());

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0,dp(8),0,dp(8));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        TextView credit = new TextView(this);
        credit.setText("Data: FinanceCalendar.com");
        credit.setTextColor(Color.LTGRAY);
        credit.setTextSize(12);
        credit.setPadding(0,dp(10),0,0);
        root.addView(credit);

        setContentView(root);
        loadData();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        handler.postDelayed(tick,60000);
    }

    @Override protected void onPause() {
        active = false;
        handler.removeCallbacks(tick);
        super.onPause();
    }

    Runnable tick = new Runnable() {
        @Override public void run() {
            if (active) {
                loadData();
                handler.postDelayed(this,60000);
            }
        }
    };

    void loadData() {
        status.setText("Mengambil jadwal dan data aktual...");
        worker.execute(() -> {
            String result = "";
            try {
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Jakarta"));
                String from = today.toString();
                String to = today.toString();
                URL url = new URL(
                    "https://www.financecalendar.com/wp-json/fc/v1/calendar"
                    + "?from=" + from + "&to=" + to
                    + "&limit=200");

                HttpURLConnection c =
                    (HttpURLConnection)url.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept","application/json");

                InputStream in = c.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf,0,n);
                result = out.toString("UTF-8");
                in.close();
                c.disconnect();
            } catch (Exception e) {
                result = "";
            }

            if (!result.isEmpty()) {
                try {
                    LocalDate today = LocalDate.now(ZoneId.of("Asia/Jakarta"));
                    String html = fetchText(
                        "https://forex.tradingcharts.com/economic_calendar/"
                        + today.toString() + ".html?code=USD");

                    if (!html.isEmpty()) {
                        ArrayList<String[]> rows = tradingRows(html);
                        JSONObject root = new JSONObject(result);
                        JSONArray events = root.optJSONArray("events");
                        if (events == null) events = root.optJSONArray("data");
                        if (events == null && root.optJSONObject("data") != null)
                            events = root.optJSONObject("data").optJSONArray("events");

                        if (events != null) {
                            for (int i = 0; i < events.length(); i++) {
                                JSONObject e = events.optJSONObject(i);
                                if (e == null) continue;
                                String name = (e.optString("name", "") + " "
                                    + e.optString("title", "")).toLowerCase(Locale.US);
                                String target = null;

                                if (name.contains("cpi")) {
                                    String actual = tradingValue(rows, "CPI (Y-o-Y)", 1);
                                    String forecast = tradingValue(rows, "CPI (Y-o-Y)", 2);
                                    String previous = tradingValue(rows, "CPI (Y-o-Y)", 3);
                                    if (!actual.equals("Belum rilis")) e.put("actual", actual);
                                    if (!forecast.equals("Belum rilis")) e.put("consensus", forecast);
                                    if (!previous.equals("Belum rilis")) e.put("prior", previous);
                                } else if (name.contains("jobless claims")) {
                                    target = "Initial Jobless Claims";
                                } else if (name.contains("nonfarm") || name.contains("payroll")) {
                                    target = "Nonfarm Payrolls";
                                } else if (name.contains("unemployment")) {
                                    target = "Unemployment Rate";
                                } else if (name.contains("retail sales")) {
                                    target = "Retail Sales (M-o-M)";
                                } else if (name.contains("ppi")) {
                                    target = "PPI (Y-o-Y)";
                                } else if (name.contains("gdp")) {
                                    target = "GDP (Q-o-Q)";
                                }

                                if (target != null) {
                                    String actual = tradingValue(rows, target, 1);
                                    String forecast = tradingValue(rows, target, 2);
                                    String previous = tradingValue(rows, target, 3);
                                    if (!actual.equals("Belum rilis")) e.put("actual", actual);
                                    if (!forecast.equals("Belum rilis")) e.put("consensus", forecast);
                                    if (!previous.equals("Belum rilis")) e.put("prior", previous);
                                }
                            }
                            result = root.toString();
                        }
                    }
                } catch (Exception ignored) {}
            }

            final String data = result;
            runOnUiThread(() -> render(data));
        });
    }


    String fetchText(String address) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception ex) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    ArrayList<String[]> tradingRows(String html) {
        ArrayList<String[]> result = new ArrayList<>();
        java.util.regex.Matcher tr = java.util.regex.Pattern.compile(
            "<tr\\b[^>]*>(.*?)</tr>",
            java.util.regex.Pattern.CASE_INSENSITIVE |
            java.util.regex.Pattern.DOTALL).matcher(html);

        while (tr.find()) {
            ArrayList<String> cells = new ArrayList<>();
            java.util.regex.Matcher td = java.util.regex.Pattern.compile(
                "<td\\b[^>]*>(.*?)</td>",
                java.util.regex.Pattern.CASE_INSENSITIVE |
                java.util.regex.Pattern.DOTALL).matcher(tr.group(1));

            while (td.find()) {
                String v = td.group(1).replaceAll("(?is)<[^>]*>", " ")
                    .replace("&nbsp;", " ").replace("&#160;", " ")
                    .replace("&amp;", "&").trim().replaceAll("\\s+", " ");
                cells.add(v);
            }

            if (cells.size() >= 8 &&
                "USD".equalsIgnoreCase(cells.get(1))) {
                result.add(new String[] {
                    cells.get(3), cells.get(5), cells.get(6), cells.get(7)
                });
            }
        }
        return result;
    }

    String tradingValue(ArrayList<String[]> rows, String event, int col) {
        for (String[] row : rows) {
            if (row[0].equalsIgnoreCase(event) &&
                col >= 0 && col < 4 &&
                !row[col].isEmpty() && !row[col].equals("-") &&
                !row[col].equals("--")) return row[col];
        }
        return "Belum rilis";
    }

    boolean isUSNews(JSONObject e) {
        String name = (e.optString("name","") + " "
            + e.optString("title","") + " "
            + e.optString("category","")).toLowerCase(Locale.US);

        if (name.contains("china") || name.contains("australia")
            || name.contains("united kingdom") || name.contains("uk gdp")
            || name.contains("eurozone") || name.contains("japan")
            || name.contains("canada")) return false;

        if (name.startsWith("us ") || name.contains("united states")
            || name.startsWith("u.s.")) return true;

        String[] keys = {
            "united states","u.s.","us cpi","cpi","inflation",
            "nonfarm","payroll","employment","unemployment",
            "jobless claims","fomc","federal reserve","fed rate",
            "pce","ppi","gdp","retail sales","ism","pmi",
            "jolts","adp employment","consumer confidence",
            "durable goods","housing starts","industrial production"
        };

        for (String k : keys) if (name.contains(k)) return true;
        return false;
    }

    String val(JSONObject e, String... keys) {
        for (String k : keys) {
            Object v = e.opt(k);
            if (v != null && v != JSONObject.NULL) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !s.equalsIgnoreCase("null"))
                    return s;
            }
        }
        return "Belum rilis";
    }

    void render(String raw) {
        list.removeAllViews();

        if (raw.isEmpty()) {
            status.setText("Data gagal diambil. Periksa internet lalu tekan PERBARUI DATA.");
            return;
        }

        try {
            JSONObject root = new JSONObject(raw);
            JSONArray events = root.optJSONArray("events");
            if (events == null) events = root.optJSONArray("data");
            if (events == null && root.optJSONObject("data") != null)
                events = root.optJSONObject("data").optJSONArray("events");

            if (events == null) {
                status.setText("Format data kalender belum dikenali.");
                return;
            }

            int count = 0;
            for (int i=0; i<events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null || !isUSNews(e)) continue;

                String name = val(e,"title","name","event");
                String time = val(e,"time_utc","datetime","date");
                String prior = val(e,"prior","previous");
                String forecast = val(e,"consensus","forecast");
                String actual = val(e,"actual");
                String impact = val(e,"impact");

                String wib = time;
                try {
                    Instant instant = Instant.parse(time);
                    wib = DateTimeFormatter.ofPattern(
                        "dd MMM HH:mm 'WIB'", new Locale("id","ID"))
                        .withZone(ZoneId.of("Asia/Jakarta"))
                        .format(instant);
                } catch (Exception ignored) {}

                String analysis = analyze(name,actual,forecast);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(13),dp(12),dp(13),dp(12));
                card.setBackgroundColor(Color.rgb(31,41,55));

                LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-1,-2);
                cp.bottomMargin = dp(10);
                list.addView(card,cp);

                addText(card,name,17,Color.WHITE);
                addText(card,wib + "  •  Impact: " + impact,12,Color.LTGRAY);
                addText(card,"PREV   " + prior,14,Color.WHITE);
                addText(card,"FORECAST   " + forecast,14,Color.WHITE);
                addText(card,"ACT      " + actual,15,
                    actual.equals("--") ? Color.LTGRAY : Color.rgb(90,220,140));
                addText(card,"ANALISA DAMPAK NEWS",13,Color.rgb(212,175,55));
                addText(card,analysis,14,Color.WHITE);

                count++;
            }

            status.setText(count == 0
                ? ("today".equals(getIntent().getStringExtra("period"))
                ? "Belum ada berita berdampak tinggi untuk hari ini. Tekan PERBARUI DATA untuk mencoba lagi."
                : "Belum ada berita berdampak tinggi yang cocok dalam 7 hari. Tekan PERBARUI DATA untuk mencoba lagi.")
                : count + " berita cocok • pembaruan otomatis tiap 1 menit saat halaman terbuka.");

        } catch (Exception e) {
            status.setText("Gagal membaca data kalender.");
        }
    }

    void addText(LinearLayout parent,String text,int size,int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0,dp(3),0,dp(3));
        parent.addView(t);
    }

    String analyze(String name,String actual,String forecast) {
        if (number(actual) == null || number(forecast) == null)
            return "Menunggu ACT/FORECAST lengkap. Arah XAUUSD dan USD belum dikonfirmasi.";

        Double a = number(actual);
        Double f = number(forecast);
        if (a == null || f == null || a.equals(f))
            return "Hasil belum menunjukkan kejutan angka yang jelas. Pantau reaksi harga.";

        String n = name.toLowerCase(Locale.US);
        boolean inverse = n.contains("jobless claims")
            || n.contains("unemployment") || n.contains("jobless");

        boolean higher = a > f;
        if (inverse) higher = !higher;

        if (higher)
            return "ACT lebih kuat dari FORECAST. Bias awal USD cenderung bullish, XAUUSD cenderung bearish.";
        return "ACT lebih lemah dari FORECAST. Bias awal USD cenderung bearish, XAUUSD cenderung bullish.";
    }

    Double number(String s) {
        try {
            String x = s.replace(",","").replace("%","")
                .replace("+","").replace("K","").trim();
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?").matcher(x);
            if (m.find()) return Double.parseDouble(m.group());
        } catch (Exception ignored) {}
        return null;
    }
}
