package id.ts.overlay;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.net.Uri;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(4, 31, 26);
    private final int CARD = Color.rgb(8, 53, 42);
    private final int GOLD = Color.rgb(222, 181, 91);
    private final int WHITE = Color.rgb(245, 245, 239);
    private final int MUTED = Color.rgb(166, 194, 181);
    private LinearLayout root;

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(14));
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // Header premium CH
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(16));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
        header.addView(brand, bp);

        TextView title = new TextView(this);
        title.setText("CUAN HUNTER");
        title.setTextColor(GOLD);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        brand.addView(title);

        TextView tagline = new TextView(this);
        tagline.setText("GOLD  •  INTELLIGENCE  •  PRECISION");
        tagline.setTextColor(MUTED);
        tagline.setTextSize(9);
        tagline.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(3);
        brand.addView(tagline, tp);

        ShieldLogo logo = new ShieldLogo(this);
        LinearLayout.LayoutParams lpLogo =
            new LinearLayout.LayoutParams(dp(54), dp(58));
        lpLogo.leftMargin = dp(12);
        header.addView(logo, lpLogo);
        logo.setContentDescription("Aktifkan floating overlay");
        logo.setOnClickListener(v -> startOverlay());
        content.addView(header);

        // Garis emas geometris
        View goldLine = new View(this);
        goldLine.setBackgroundColor(GOLD);
        LinearLayout.LayoutParams glp =
            new LinearLayout.LayoutParams(-1, dp(2));
        glp.bottomMargin = dp(22);
        content.addView(goldLine, glp);

        // COMMAND CENTER disembunyikan sesuai desain baru.

        // Empat menu utama
        LinearLayout row1 = makeRow();
        row1.addView(menuCard("01", "MASTER DATA",
            "Data utama & impor", () -> openMasterData()),
            new LinearLayout.LayoutParams(0, dp(145), 1));
        addGap(row1);
        row1.addView(menuCard("02", "Gold Monitor",
            "Harga Treasury", () -> startOverlay()),
            new LinearLayout.LayoutParams(0, dp(145), 1));
        content.addView(row1);

        LinearLayout row2 = makeRow();
        LinearLayout.LayoutParams row2p =
            new LinearLayout.LayoutParams(-1, -2);
        row2p.topMargin = dp(12);
        row2.addView(menuCard("03", "NEWS",
            "Berita & dampak pasar", () -> openNews()),
            new LinearLayout.LayoutParams(0, dp(145), 1));
        addGap(row2);
        row2.addView(menuCard("04", "NOTES",
            "Catatan pribadi", () -> openNotes()),
            new LinearLayout.LayoutParams(0, dp(145), 1));
        content.addView(row2, row2p);

        // Footer garis ganda khas armor
        View line2 = new View(this);
        line2.setBackgroundColor(Color.rgb(111, 84, 39));
        LinearLayout.LayoutParams l2p =
            new LinearLayout.LayoutParams(-1, dp(1));
        l2p.topMargin = dp(24);
        content.addView(line2, l2p);

        // footer CH EDITION disembunyikan sesuai desain baru.

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }


    private class ShieldLogo extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ShieldLogo(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            Path path = new Path();
            path.moveTo(w * .50f, h * .06f);
            path.lineTo(w * .91f, h * .20f);
            path.lineTo(w * .86f, h * .58f);
            path.quadTo(w * .80f, h * .82f, w * .50f, h * .96f);
            path.quadTo(w * .20f, h * .82f, w * .14f, h * .58f);
            path.lineTo(w * .09f, h * .20f);
            path.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(7, 81, 61));
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(GOLD);
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GOLD);
            paint.setTypeface(Typeface.create(
                "sans-serif-condensed", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(20));
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("CH", w / 2f, baseline, paint);
        }
    }

private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void addGap(LinearLayout row) {
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
    }

    private View menuCard(String number, String label, String desc,
                          Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(10), dp(12));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1), GOLD);
        card.setBackground(shape);
        card.setElevation(dp(2));

        TextView num = new TextView(this);
        num.setText("◆");
        num.setTextColor(GOLD);
        num.setTextSize(10);
        num.setTypeface(null, Typeface.BOLD);
        card.addView(num);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(WHITE);
        name.setTextSize(14);
        name.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        name.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(12);
        card.addView(name, np);

        TextView description = new TextView(this);
        description.setText(desc);
        description.setTextColor(MUTED);
        description.setTextSize(10);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(-1, -2);
        dp2.topMargin = dp(5);
        card.addView(description, dp2);

        card.setOnClickListener(v -> action.run());
        return card;
    }

    private void openMasterData() {
        // Coba Activity Master Data yang mungkin sudah ada.
        String[] candidates = {
            "id.ts.overlay.MasterDataActivity",
            "id.ts.overlay.MasterdataActivity",
            "id.ts.goldoverlay.MasterDataActivity"
        };
        for (String name : candidates) {
            try {
                Class<?> c = Class.forName(name);
                startActivity(new Intent(this, c));
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                Toast.makeText(this, "Master Data belum dapat dibuka",
                    Toast.LENGTH_SHORT).show();
                return;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("MASTER DATA")
            .setMessage("Menu Master Data siap. Layar impor CSV perlu dihubungkan ke modul Master Data proyek.")
            .setPositiveButton("OK", null)
            .show();
    }

    private void openNews() {
        try {
            Intent i = new Intent(this, USNewsActivity.class);
            i.putExtra("period", "today");
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "News belum dapat dibuka",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotes() {
        EditText input = new EditText(this);
        input.setText(getSharedPreferences("cuan_hunter", MODE_PRIVATE)
            .getString("notes", ""));
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("Tulis catatan...");
        input.setMinLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), GOLD);
        input.setBackground(bg);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
            .setTitle("NOTES")
            .setView(wrap)
            .setNegativeButton("TUTUP", null)
            .setPositiveButton("SIMPAN", (d, w) ->
                getSharedPreferences("cuan_hunter", MODE_PRIVATE)
                    .edit().putString("notes", input.getText().toString()).apply())
            .show();
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan overlay untuk mengaktifkan tombol C dan TS",
                Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }
        Intent i = new Intent(this, PriceOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "Floating C dan TS diaktifkan",
            Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
    }
}
