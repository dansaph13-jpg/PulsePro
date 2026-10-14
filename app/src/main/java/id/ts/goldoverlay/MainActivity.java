package id.ts.goldoverlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showScreen();
    }

    private void showScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setBackgroundColor(Color.rgb(17, 24, 39));

        TextView title = new TextView(this);
        title.setText("TS GOLD OVERLAY");
        title.setTextColor(Color.rgb(212, 175, 55));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("\nPanel harga Treasury mengambang di atas aplikasi lain.\n\nAktifkan izin overlay, lalu jalankan panel. Layanan berjalan dengan notifikasi.");
        info.setTextColor(Color.WHITE);
        info.setTextSize(15);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        Button permission = new Button(this);
        permission.setText("1. AKTIFKAN IZIN OVERLAY");
        permission.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        addButton(permission);

        Button start = new Button(this);
        start.setText("2. JALANKAN PANEL");
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                showScreen();
                return;
            }
            Intent i = new Intent(this, OverlayService.class);
            startForegroundService(i);
        });
        addButton(start);

        Button stop = new Button(this);
        stop.setText("HENTIKAN PANEL");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        addButton(stop);

        setContentView(root);
    }

    private void addButton(Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        root.addView(button, lp);
    }
}
