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
                String to = today.plusDays(7).toString();
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

            final String data = result;
            runOnUiThread(() -> render(data));
        });
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
                addText(card,"PRED   " + forecast,14,Color.WHITE);
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
        if (actual.equals("--") || forecast.equals("--"))
            return "Menunggu ACT/PRED lengkap. Arah XAUUSD dan USD belum dikonfirmasi.";

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
            return "ACT lebih kuat dari PRED. Bias awal USD cenderung bullish, XAUUSD cenderung bearish.";
        return "ACT lebih lemah dari PRED. Bias awal USD cenderung bearish, XAUUSD cenderung bullish.";
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
