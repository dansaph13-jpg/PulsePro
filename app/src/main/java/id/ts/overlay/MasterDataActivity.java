package id.ts.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.AutoCompleteTextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

/* JADX INFO: loaded from: classes2.dex */
public class MasterDataActivity extends Activity {
    TextView count;
    DB db;
    LinearLayout list;
    LinearLayout root;
    EditText search;
    boolean sortName = false;
    int sortMode = 0;
    boolean saldoOnly = false;
    boolean emptyOnly = false;
    EditText statusFilter;
    TextView saldoButton;
    TextView emptyButton;
    TextView filterButton;
    final int gold = Color.rgb(230, 184, 74);
    final int dark = Color.rgb(0, 65, 48);
    final int panel = Color.rgb(0, 88, 64);
    final int lightText = Color.rgb(255, 220, 55);

    final int green = Color.rgb(16, 110, 79);
    final int ink = Color.rgb(35, 45, 43);
    final int muted = Color.rgb(105, 117, 112);

    int dp(int n) {
        return (int) ((n * getResources().getDisplayMetrics().density) + 0.5f);
    }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    TextView txt(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    TextView btn(String s, final Runnable r) {
        TextView t = txt(s, 13, -1);
        t.setGravity(17);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        t.setBackgroundColor(this.green);
        t.setOnClickListener(new View.OnClickListener() { // from class: id.dibyo.minidatamanager.MasterDataActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                r.run();
            }
        });
        return t;
    }

    @Override // android.app.Activity
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(4, 31, 26));
        getWindow().setNavigationBarColor(Color.rgb(4, 31, 26));
        getWindow().getDecorView().setSystemUiVisibility(0);
        this.db = new DB(this);
        build();
        refresh();
    }

    void build() {
        root = col();
        root.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.setBackgroundColor(dark);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView menuButton = outlineButton("☰", 48, 42);
        menuButton.setBackground(outlineBackground(android.graphics.Color.rgb(4, 70, 54)));
        header.addView(menuButton, lp(48, 42));
        menuButton.setOnClickListener(v -> showMenu(menuButton));

        TextView title = txt("DATABASE CUAN", 20, gold);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));

        TextView closeButton = outlineButton("×", 48, 42);
        header.addView(closeButton, lp(48, 42));
        closeButton.setOnClickListener(v -> finish());

        root.addView(header, lp(-1, 48));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(lightText);
        search.setHintTextColor(muted);
        search.setHint("Cari nama atau nomor HP");
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(outlineBackground(panel));
        LinearLayout.LayoutParams searchLp = lp(-1, 44);
        searchLp.topMargin = dp(8);
        root.addView(search, searchLp);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, dp(8), 0, dp(8));

        saldoButton = outlineButton("◉", 50, 42);
        emptyButton = outlineButton("⊖", 50, 42);
        filterButton = outlineButton("☷", 50, 42);

        filters.addView(saldoButton, lp(50, 42));
        filters.addView(new Space(this), lp(8, 1));
        filters.addView(emptyButton, lp(50, 42));
        filters.addView(new Space(this), lp(8, 1));
        filters.addView(filterButton, lp(50, 42));
        filters.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));

        saldoButton.setOnClickListener(v -> {
            saldoOnly = !saldoOnly;
            if (saldoOnly) emptyOnly = false;
            updateFilterButtons();
            refresh();
        });
        emptyButton.setOnClickListener(v -> {
            emptyOnly = !emptyOnly;
            if (emptyOnly) saldoOnly = false;
            updateFilterButtons();
            refresh();
        });
        filterButton.setOnClickListener(v -> {
            if (statusFilter.getVisibility() == View.VISIBLE) {
                statusFilter.setVisibility(View.GONE);
                statusFilter.setText("");
            } else {
                statusFilter.setVisibility(View.VISIBLE);
                statusFilter.requestFocus();
            }
            updateFilterButtons();
            refresh();
        });
        root.addView(filters, lp(-1, 58));

        statusFilter = new EditText(this);
        statusFilter.setSingleLine(true);
        statusFilter.setTextSize(13);
        statusFilter.setTextColor(lightText);
        statusFilter.setHintTextColor(muted);
        statusFilter.setHint("Status harus sama persis");
        statusFilter.setPadding(dp(10), 0, dp(10), 0);
        statusFilter.setBackground(outlineBackground(panel));
        statusFilter.setVisibility(View.GONE);
        LinearLayout.LayoutParams filterLp = lp(-1, 40);
        filterLp.bottomMargin = dp(8);
        root.addView(statusFilter, filterLp);
        statusFilter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                refresh();
            }
            public void afterTextChanged(Editable e) {}
        });

        LinearLayout tableHead = new LinearLayout(this);
        tableHead.setGravity(Gravity.CENTER_VERTICAL);
        tableHead.setPadding(dp(5), 0, dp(5), 0);
        tableHead.setBackgroundColor(panel);
        TextView hNo = headerCell("No", 12, 32, 0);
        hNo.setOnClickListener(v -> { sortMode = 1; refresh(); });
        tableHead.addView(hNo);

        TextView hNama = headerCell("Nama", 12, 0, 1);
        hNama.setOnClickListener(v -> { sortMode = 2; refresh(); });
        tableHead.addView(hNama);

        TextView hPhone = headerCell("Nomor HP", 12, 142, 0);
        tableHead.addView(hPhone);

        TextView hStatus = headerCell("St", 12, 42, 0);
        hStatus.setOnClickListener(v -> { sortMode = 3; refresh(); });
        tableHead.addView(hStatus);
        root.addView(tableHead, lp(-1, 38));

        ScrollView sc = new ScrollView(this);
        list = col();
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(4, 31, 26));
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        android.widget.FrameLayout.LayoutParams panelLp =
                new android.widget.FrameLayout.LayoutParams(
                        Math.round(screenW * 0.96f),
                        Math.round(screenH * 0.88f),
                        Gravity.CENTER);
        frame.addView(root, panelLp);
        setContentView(frame);

        updateFilterButtons();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                refresh();
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    GradientDrawable outlineBackground(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(8));
        d.setStroke(dp(1), gold);
        return d;
    }

    TextView outlineButton(String label, int width, int height) {
        TextView b = txt(label, 19, gold);
        b.setGravity(Gravity.CENTER);
        b.setBackground(outlineBackground(dark));
        return b;
    }

    TextView headerCell(String label, int size, int fixedWidth, int weight) {
        TextView t = txt(label, size, gold);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (fixedWidth > 0) {
            t.setLayoutParams(new LinearLayout.LayoutParams(dp(fixedWidth), -1));
        } else {
            t.setLayoutParams(new LinearLayout.LayoutParams(0, -1, weight));
        }
        return t;
    }

    void updateFilterButtons() {
        if (saldoButton != null) {
            saldoButton.setAlpha(saldoOnly ? 1f : 0.72f);
            saldoButton.setBackground(outlineBackground(saldoOnly ? Color.rgb(67, 53, 25) : dark));
        }
        if (emptyButton != null) {
            emptyButton.setAlpha(emptyOnly ? 1f : 0.72f);
            emptyButton.setBackground(outlineBackground(emptyOnly ? Color.rgb(67, 53, 25) : dark));
        }
        if (filterButton != null) {
            boolean active = statusFilter != null &&
                statusFilter.getVisibility() == View.VISIBLE;
            filterButton.setAlpha(active ? 1f : 0.72f);
            filterButton.setBackground(outlineBackground(active ? Color.rgb(67, 53, 25) : dark));
        }
    }

    void showMenu(View anchor) {
        final int emerald = android.graphics.Color.rgb(4, 70, 54);
        final String[] labels = {
            "Tambah Akun", "Impor CSV", "Ekspor CSV", "Reset Status"
        };

        LinearLayout panelMenu = col();
        panelMenu.setPadding(dp(5), dp(5), dp(5), dp(5));
        panelMenu.setBackground(outlineBackground(emerald));

        final android.widget.PopupWindow popup =
            new android.widget.PopupWindow(
                panelMenu, dp(190), android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true
            );
        popup.setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(emerald)
        );
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));

        for (int i = 0; i < labels.length; i++) {
            final int action = i + 1;
            TextView item = txt(labels[i], 15, gold);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(8), dp(10), dp(8));
            item.setBackground(outlineBackground(emerald));
            panelMenu.addView(item, lp(-1, 44));
            item.setOnClickListener(v -> {
                popup.dismiss();
                switch (action) {
                    case 1: editor(null); break;
                    case 2: pick(101); break;
                    case 3: pick(102); break;
                    case 4: confirmResetStatuses(); break;
                }
            });
        }
        popup.showAsDropDown(anchor);
    }

    void confirmResetStatuses() {
        new AlertDialog.Builder(this)
            .setTitle("Reset semua status?")
            .setMessage("Semua status akan dikosongkan. Nama, nomor HP, email, dan nomor urut tetap aman.")
            .setNegativeButton("BATAL", null)
            .setPositiveButton("RESET", (dialog, which) -> {
                db.resetStatuses();
                refresh();
                toast("Semua status dikosongkan");
            })
            .show();
    }


    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$1() {
        editor(null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$2() {
        this.sortName = !this.sortName;
        refresh();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$3() {
        pick(101);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$4() {
        pick(102);
    }


    String formatPhone(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        int n = digits.length();
        if (n < 10 || n > 13) return value;

        if (n == 10) {
            return digits.substring(0, 4) + "." +
                   digits.substring(4, 7) + "." + digits.substring(7);
        }
        if (n == 11) {
            return digits.substring(0, 4) + "." +
                   digits.substring(4, 8) + "." + digits.substring(8);
        }
        if (n == 12) {
            return digits.substring(0, 4) + "." +
                   digits.substring(4, 8) + "." + digits.substring(8);
        }
        return digits.substring(0, 4) + "." +
               digits.substring(4, 8) + "." + digits.substring(8);
    }

    int compareNo(String a, String b) {
        String x = a == null ? "" : a.replaceAll("[^0-9]", "");
        String y = b == null ? "" : b.replaceAll("[^0-9]", "");
        if (x.isEmpty() || y.isEmpty()) {
            if (x.isEmpty() && y.isEmpty()) return 0;
            return x.isEmpty() ? 1 : -1;
        }
        try {
            return Long.compare(Long.parseLong(x), Long.parseLong(y));
        } catch (NumberFormatException e) {
            return x.compareTo(y);
        }
    }

    int statusRank(String value) {
        String v = value == null ? "" : value.trim();
        if (v.equalsIgnoreCase("saldo")) return 0;
        if (v.isEmpty()) return 1;
        if (v.matches("[0-9]+")) return 2;
        return 3;
    }

    int compareStatus(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        int rx = statusRank(x), ry = statusRank(y);
        if (rx != ry) return Integer.compare(rx, ry);
        if (rx == 2) {
            try {
                return Long.compare(Long.parseLong(x), Long.parseLong(y));
            } catch (NumberFormatException ignored) {}
        }
        return x.compareToIgnoreCase(y);
    }

    void refresh() {
        if (list == null || search == null) return;
        list.removeAllViews();

        String q = search.getText().toString().trim();
        ArrayList<R> rows = db.all(q, false);
        java.util.Collections.sort(rows, (a, b) -> {
            if (sortMode == 1) return compareNo(a.no, b.no);
            if (sortMode == 2) {
                String an = a.name == null ? "" : a.name.trim();
                String bn = b.name == null ? "" : b.name.trim();
                return an.compareToIgnoreCase(bn);
            }
            if (sortMode == 3) return compareStatus(a.ket, b.ket);

            boolean aSaldo = a.ket != null &&
                a.ket.trim().equalsIgnoreCase("saldo");
            boolean bSaldo = b.ket != null &&
                b.ket.trim().equalsIgnoreCase("saldo");
            if (aSaldo != bSaldo) return aSaldo ? -1 : 1;
            String an = a.name == null ? "" : a.name.trim();
            String bn = b.name == null ? "" : b.name.trim();
            return an.compareToIgnoreCase(bn);
        });
        String exactStatus = statusFilter == null ? "" :
            statusFilter.getText().toString().trim();
        int shown = 0;

        for (final R r : rows) {
            String status = r.ket == null ? "" : r.ket.trim();
            if (saldoOnly && !status.equalsIgnoreCase("saldo")) continue;
            if (emptyOnly && !status.isEmpty()) continue;
            if (!exactStatus.isEmpty() && !status.equals(exactStatus)) continue;

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(5), dp(4), dp(5), dp(4));
            row.setBackgroundColor(shown % 2 == 0 ? dark : panel);

            TextView no = txt(r.no == null ? "" : r.no, 11, gold);
            no.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(no, new LinearLayout.LayoutParams(dp(32), dp(44)));

            TextView name = txt(maskName(r.name), 15, lightText);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1));

            TextView phone = txt(formatPhone(r.phone), 14, lightText);
            phone.setSingleLine(true);
            phone.setEllipsize(TextUtils.TruncateAt.END);
            phone.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(phone, new LinearLayout.LayoutParams(dp(142), dp(48)));
            phone.setOnClickListener(v -> copy(r.phone));
            phone.setOnLongClickListener(v -> {
                editor(r);
                return true;
            });

            TextView st = txt(status, 13, gold);
            android.graphics.drawable.GradientDrawable statusBg =
                    new android.graphics.drawable.GradientDrawable();
            statusBg.setColor(green);
            statusBg.setCornerRadius(dp(5));
            statusBg.setStroke(dp(1), gold);
            st.setBackground(statusBg);
            st.setPadding(dp(4), 0, dp(4), 0);
            st.setSingleLine(true);
            st.setEllipsize(TextUtils.TruncateAt.END);
            st.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(st, new LinearLayout.LayoutParams(dp(42), dp(48)));
            st.setOnClickListener(v -> editStatus(r));

            list.addView(row, lp(-1, 48));
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(48, 53, 60));
            list.addView(divider, lp(-1, 1));
            shown++;
        }

        if (shown == 0) {
            TextView empty = txt("Tidak ada data", 13, muted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            list.addView(empty, lp(-1, -2));
        }
        updateFilterButtons();
    }

    boolean statusBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    String maskName(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String[] words = value.trim().split("\\s+");
        StringBuilder out = new StringBuilder(words[0]);
        if (words.length > 1) out.append('\n');
        for (int i = 1; i < words.length; i++) {
            if (i > 1) out.append(' ');
            String w = words[i];
            if (w.length() <= 2) out.append(w);
            else out.append(w.charAt(0)).append('*')
                    .append(w.charAt(w.length() - 1));
        }
        return out.toString();
    }

    void editStatus(final R r) {
        final int emerald = android.graphics.Color.rgb(4, 70, 54);

        final AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(gold);
        input.setHintTextColor(gold);
        input.setHint("Status");
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setThreshold(1);

        android.graphics.drawable.GradientDrawable statusBg =
            new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(emerald);
        statusBg.setCornerRadius(dp(6));
        statusBg.setStroke(dp(1), gold);
        input.setBackground(statusBg);

        input.setAdapter(new android.widget.ArrayAdapter<String>(
            this, android.R.layout.simple_dropdown_item_1line,
            new String[]{"saldo"}
        ) {
            private TextView styleRow(View view) {
                TextView row = (TextView) view;
                row.setTextColor(gold);
                row.setBackgroundColor(emerald);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                return row;
            }
            @Override
            public View getView(int position, View convertView,
                                android.view.ViewGroup parent) {
                return styleRow(super.getView(position, convertView, parent));
            }
            @Override
            public View getDropDownView(int position, View convertView,
                                        android.view.ViewGroup parent) {
                return styleRow(super.getDropDownView(position, convertView, parent));
            }
        });

        input.setText(r.ket == null ? "" : r.ket);
        input.setSelection(input.length());
        input.setOnFocusChangeListener((v, focused) -> {
            if (!focused && input.getText().toString().trim().equalsIgnoreCase("s")) {
                input.setText("saldo");
                input.setSelection(input.length());
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Status — " + r.name)
            .setView(input)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("SIMPAN", (d, which) -> {
                String value = input.getText().toString().trim();
                if (value.equalsIgnoreCase("s")) value = "saldo";
                db.save(r.f0id, r.no, r.phone, r.name, value, r.email);
                refresh();
            })
            .create();

        dialog.setOnShowListener(dlg -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(emerald)
                );
            }
            TextView title = dialog.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
            if (title != null) {
                title.setTextColor(gold);
                title.setBackgroundColor(emerald);
            }
            android.widget.Button positive =
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            android.widget.Button negative =
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            if (positive != null) {
                positive.setTextColor(gold);
                positive.setBackground(outlineBackground(emerald));
            }
            if (negative != null) {
                negative.setTextColor(gold);
                negative.setBackground(outlineBackground(emerald));
            }
        });
        dialog.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refresh$5(R r, View v) {
        copy(r.email);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void handleRefreshRowLongClick(R r, View v) {
        actions(r);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void handleRefreshRowClick(R r, View v) {
        editor(r);
    }

    void actions(final R r) {
        String[] opts = {"Salin nomor HP", "Salin email", "Edit", "Hapus"};
        new AlertDialog.Builder(this).setTitle(r.name).setItems(opts, new DialogInterface.OnClickListener() { // from class: id.dibyo.minidatamanager.MasterDataActivity$$ExternalSyntheticLambda9
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MasterDataActivity.this.lambda$actions$9(r, dialogInterface, i);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$actions$9(final R r, DialogInterface d, int w) {
        if (w == 0) {
            copy(r.phone);
        }
        if (w == 1) {
            copy(r.email);
        }
        if (w == 2) {
            editor(r);
        }
        if (w == 3) {
            new AlertDialog.Builder(this).setTitle("Hapus data?").setMessage(r.name + " akan dihapus dari HP ini.").setNegativeButton("BATAL", (DialogInterface.OnClickListener) null).setPositiveButton("HAPUS", new DialogInterface.OnClickListener() { // from class: id.dibyo.minidatamanager.MasterDataActivity$$ExternalSyntheticLambda6
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MasterDataActivity.this.lambda$actions$8(r, dialogInterface, i);
                }
            }).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$actions$8(R r, DialogInterface x, int y) {
        this.db.del(r.f0id);
        refresh();
    }

    void copy(String s) {
        if (s == null || s.trim().isEmpty()) {
            toast("Data masih kosong");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService("clipboard");
        cm.setPrimaryClip(ClipData.newPlainText("data", s));
        toast("Berhasil disalin");
    }


    private String normalizeEmail(String value) {
        String e = value == null ? "" : value.trim();
        if (!e.isEmpty() && !e.contains("@")) {
            e += "@gmail.com";
        }
        return e;
    }

EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14.0f);
        e.setPadding(dp(8), dp(4), dp(8), dp(4));
        e.setTextColor(android.graphics.Color.rgb(230, 184, 74));
        e.setHintTextColor(android.graphics.Color.argb(155, 230, 184, 74));
        e.setBackground(new android.graphics.drawable.GradientDrawable() {
            {
                setColor(android.graphics.Color.rgb(4, 70, 54));
                setCornerRadius(dp(8));
                setStroke(dp(1), android.graphics.Color.rgb(230, 184, 74));
            }
        });
        return e;
    }

    void editor(final R old) {
        LinearLayout f = col();
        f.setPadding(dp(4), dp(4), dp(4), dp(4));
        f.setBackgroundColor(android.graphics.Color.rgb(4, 70, 54));
        final EditText no = field("No / nomor urut");
        final EditText phone = field("Nomer HP");
        final EditText name = field("Nama");
        final EditText ket = field("Status");
        final EditText email = field("Email (boleh kosong)");
        email.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String value = normalizeEmail(email.getText().toString());
                if (!value.equals(email.getText().toString().trim())) {
                    email.setText(value);
                    email.setSelection(value.length());
                }
            }
        });
        f.addView(no, lp(-1, 48));
        f.addView(phone, lp(-1, 48));
        f.addView(name, lp(-1, 48));
        f.addView(ket, lp(-1, 48));
        f.addView(email, lp(-1, 48));
        if (old != null) {
            no.setText(old.no);
            phone.setText(old.phone);
            name.setText(old.name);
            ket.setText(old.ket);
            email.setText(old.email);
        }
        final AlertDialog d = new AlertDialog.Builder(this).setTitle(old == null ? "Tambah data" : "Edit data").setView(f).setNegativeButton("BATAL", (DialogInterface.OnClickListener) null).setPositiveButton("SIMPAN", (DialogInterface.OnClickListener) null).create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                if (d.getWindow() != null) {
                    d.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.rgb(4, 70, 54)
                        )
                    );
                }

                MasterDataActivity.this.lambda$editor$12(
                    d, no, phone, name, ket, email, old, dialogInterface
                );

                TextView dialogTitle = d.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
                if (dialogTitle != null) {
                    dialogTitle.setTextColor(android.graphics.Color.BLACK);
                    dialogTitle.setBackgroundColor(android.graphics.Color.rgb(4, 70, 54));
                }

                android.widget.Button positive =
                    d.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                android.widget.Button negative =
                    d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);

                if (positive != null) {
                    positive.setTextColor(android.graphics.Color.rgb(230, 184, 74));
                    positive.setBackground(outlineBackground(
                        android.graphics.Color.rgb(4, 70, 54)
                    ));
                }
                if (negative != null) {
                    negative.setTextColor(android.graphics.Color.rgb(230, 184, 74));
                    negative.setBackground(outlineBackground(
                        android.graphics.Color.rgb(4, 70, 54)
                    ));
                }
            }
        });
        d.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$editor$12(final AlertDialog d, final EditText no, final EditText phone, final EditText name, final EditText ket, final EditText email, final R old, DialogInterface v) {
        d.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: id.dibyo.minidatamanager.MasterDataActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MasterDataActivity.this.lambda$editor$11(no, phone, name, ket, email, old, d, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$editor$11(EditText no, EditText phone, EditText name, EditText ket, EditText email, final R old, final AlertDialog d, View b) {
        final String a = no.getText().toString().trim();
        final String p = phone.getText().toString().trim();
        final String n = name.getText().toString().trim();
        final String k = ket.getText().toString().trim();
        final String e = normalizeEmail(email.getText().toString());
        if (a.isEmpty() && p.isEmpty() && n.isEmpty()) {
            toast("Isi No, nomor HP, atau nama");
            return;
        }
        if (!e.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            toast("Format email belum benar");
            return;
        }
        String dup = this.db.duplicate(p, n, e, old == null ? -1L : old.f0id);
        if (dup == null) {
            this.db.save(old == null ? -1L : old.f0id, a, p, n, k, e);
            refresh();
            d.dismiss();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Kemungkinan duplikat").setMessage("Nomor HP, nama, atau email sudah ada (" + dup + "). Tetap simpan?").setNegativeButton("BATAL", (DialogInterface.OnClickListener) null).setPositiveButton("TETAP SIMPAN", new DialogInterface.OnClickListener() { // from class: id.dibyo.minidatamanager.MasterDataActivity$$ExternalSyntheticLambda2
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MasterDataActivity.this.lambda$editor$10(old, a, p, n, k, e, d, dialogInterface, i);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$editor$10(R old, String a, String p, String n, String k, String e, AlertDialog d, DialogInterface x, int y) {
        this.db.save(old == null ? -1L : old.f0id, a, p, n, k, e);
        refresh();
        d.dismiss();
    }

    void pick(int req) {
        Intent i = new Intent(req == 101 ? "android.intent.action.OPEN_DOCUMENT" : "android.intent.action.CREATE_DOCUMENT");
        i.addCategory("android.intent.category.OPENABLE");
        i.setType(req == 101 ? "text/*" : "text/csv");
        if (req == 101) {
            i.setType("*/*");
        } else {
            i.putExtra("android.intent.extra.TITLE", "MiniDataManager_backup.csv");
        }
        startActivityForResult(i, req);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != -1 || data == null || data.getData() == null) {
            return;
        }
        if (req == 101) {
            importFile(data.getData());
        }
        if (req == 102) {
            exportFile(data.getData());
        }
    }

    String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }

    ArrayList<String> parse(String line) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean quote = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (quote && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    b.append('\"');
                    i++;
                } else {
                    quote = quote ? false : true;
                }
            } else if ((c == ',' || c == ';' || c == '\t') && !quote) {
                out.add(b.toString());
                b.setLength(0);
            } else {
                b.append(c);
            }
            i++;
        }
        out.add(b.toString());
        return out;
    }

    int index(ArrayList<String> headers, String... names) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i) == null ? "" : headers.get(i);
            header = header.replace("\ufeff", "").trim()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "");
            for (String name : names) {
                String normalized = name == null ? "" : name.trim()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
                if (!normalized.isEmpty() && header.equals(normalized)) return i;
            }
        }
        return -1;
    }

    String at(ArrayList<String> a, int i) {
        return (i < 0 || i >= a.size()) ? "" : a.get(i).trim();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r3v3 */
    /* JADX WARN: Type inference failed for: r5v2, types: [android.content.ContentResolver] */
    /* JADX WARN: Type inference failed for: r6v0, types: [android.net.Uri] */
    /* JADX WARN: Type inference failed for: r6v1 */
    /* JADX WARN: Type inference failed for: r6v17 */
    /* JADX WARN: Type inference failed for: r6v19 */
    /* JADX WARN: Type inference failed for: r6v2 */
    /* JADX WARN: Type inference failed for: r6v24 */
    /* JADX WARN: Type inference failed for: r6v29 */
    /* JADX WARN: Type inference failed for: r6v3 */
    /* JADX WARN: Type inference failed for: r6v32 */
    /* JADX WARN: Type inference failed for: r6v33 */
    /* JADX WARN: Type inference failed for: r6v34 */
    /* JADX WARN: Type inference failed for: r6v4 */
    /* JADX WARN: Type inference failed for: r6v5 */
    /* JADX WARN: Type inference failed for: r6v6 */
    /* JADX WARN: Type inference failed for: r6v8 */
    /* JADX WARN: Type inference failed for: r6v9 */
    void importFile(Uri uri) {
        int imported = 0;
        int skipped = 0;
        boolean headerChecked = false;
        int ixNo = 0, ixPhone = 1, ixName = 2, ixStatus = 3, ixEmail = 4;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    getContentResolver().openInputStream(uri),
                    StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                ArrayList<String> row = parse(line);
                if (row.isEmpty()) continue;

                if (!headerChecked) {
                    headerChecked = true;
                    ArrayList<String> headers = new ArrayList<>();
                    for (String h : row) {
                        headers.add(h.replace("\ufeff", "").trim());
                    }

                    int n = index(headers, "No", "Nomor Urut");
                    int p = index(headers, "Nomer", "Nomor", "Nomor HP",
                        "No HP", "Telepon", "Phone");
                    int nm = index(headers, "Nama", "Name");
                    int st = index(headers, "Status", "Ket", "Keterangan",
                        "Catatan", "Notes");
                    int em = index(headers, "Email", "E-mail");

                    if (n >= 0 || p >= 0 || nm >= 0 || st >= 0 || em >= 0) {
                        ixNo = n;
                        ixPhone = p;
                        ixName = nm;
                        ixStatus = st;
                        ixEmail = em;
                        continue;
                    }
                }

                String no = ixNo >= 0 && ixNo < row.size()
                    ? row.get(ixNo).trim() : "";
                String phone = ixPhone >= 0 && ixPhone < row.size()
                    ? row.get(ixPhone).trim() : "";
                String name = ixName >= 0 && ixName < row.size()
                    ? row.get(ixName).trim() : "";
                String status = ixStatus >= 0 && ixStatus < row.size()
                    ? row.get(ixStatus).trim() : "";
                String email = ixEmail >= 0 && ixEmail < row.size()
                    ? row.get(ixEmail).trim() : "";

                if (status.contains("@") && email.isEmpty()) {
                    email = status;
                    status = "";
                }

                if (no.isEmpty() && phone.isEmpty() && name.isEmpty()
                        && status.isEmpty() && email.isEmpty()) {
                    skipped++;
                    continue;
                }

                String duplicate = db.duplicate(phone, name, email, -1);
                if (duplicate != null) {
                    skipped++;
                    continue;
                }

                db.save(-1, no, phone, name, status, email);
                imported++;
            }

            refresh();
            toast("Impor selesai: " + imported + " masuk, " +
                skipped + " dilewati.");

        } catch (Exception e) {
            toast("Impor CSV gagal: " +
                (e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage()));
        }
    }

    String csv(String s) {
        String value = s == null ? "" : s;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    void exportFile(Uri uri) {
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
            try {
                OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                try {
                    w.write("\"No\",\"Nomer\",\"Nama\",\"Status\",\"Email\"\r\n");
                    for (R r : this.db.all("", false)) {
                        w.write(csv(r.no) + "," + csv(r.phone) + "," + csv(r.name) + "," + csv(r.ket) + "," + csv(r.email) + "\r\n");
                    }
                    w.flush();
                    toast("Ekspor selesai");
                    w.close();
                    if (out != null) {
                        out.close();
                    }
                } catch (Throwable th) {
                    try {
                        w.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable th4) {
                        th3.addSuppressed(th4);
                    }
                }
                throw th3;
            }
        } catch (Exception ex) {
            toast("Ekspor gagal: " + ex.getMessage());
        }
    }

    void toast(String s) {
        Toast.makeText(this, s, 1).show();
    }

    static class R {
        String email;

        /* JADX INFO: renamed from: id, reason: collision with root package name */
        long f0id;
        String ket;
        String name;
        String no;
        String phone;

        R(long i, String a, String p, String n, String k, String e) {
            this.f0id = i;
            this.no = a;
            this.phone = p;
            this.name = n;
            this.ket = k;
            this.email = e;
        }
    }

    static class DB extends SQLiteOpenHelper {
        DB(Context c) {
            super(c, "mini_data.db", (SQLiteDatabase.CursorFactory) null, 2);
        }

        @Override // android.database.sqlite.SQLiteOpenHelper
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT,serial TEXT,phone TEXT DEFAULT '',name TEXT,email TEXT,notes TEXT)");
        }

        @Override // android.database.sqlite.SQLiteOpenHelper
        public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
            if (oldV < 2) {
                d.execSQL("ALTER TABLE records ADD COLUMN phone TEXT DEFAULT ''");
            }
        }

        void save(long id2, String a, String p, String n, String k, String e) {
            ContentValues v = new ContentValues();
            v.put("serial", a);
            v.put("phone", p);
            v.put("name", n);
            v.put("notes", k);
            v.put("email", e);
            if (id2 >= 0) {
                getWritableDatabase().update("records", v, "id=?", new String[]{String.valueOf(id2)});
            } else {
                getWritableDatabase().insert("records", null, v);
            }
        }

        void del(long id2) {
            getWritableDatabase().delete("records", "id=?", new String[]{String.valueOf(id2)});
        }

        ArrayList<R> all(String q, boolean byName) {
            ArrayList<R> a = new ArrayList<>();
            String sql = "SELECT id,serial,phone,name,notes,email FROM records";
            ArrayList<String> args = new ArrayList<>();
            if (q != null && !q.isEmpty()) {
                sql += " WHERE phone LIKE ? OR name LIKE ?";
                String pattern = "%" + q + "%";
                args.add(pattern);
                args.add(pattern);
            }
            Cursor c = getReadableDatabase().rawQuery(
                sql + (byName ? " ORDER BY name COLLATE NOCASE" :
                " ORDER BY CAST(serial AS INTEGER),id"),
                args.toArray(new String[0]));
            try {
                while (c.moveToNext()) {
                    a.add(new R(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getString(5)));
                }
            } finally {
                c.close();
            }
            return a;
        }


        void resetStatuses() {
            ContentValues values = new ContentValues();
            values.put("notes", "");
            getWritableDatabase().update("records", values, null, null);
        }

        String duplicate(String p, String n, String e, long except) {
            String[][] checks = {new String[]{"phone", p, "nomor HP"}, new String[]{"name", n, "nama"}, new String[]{"email", e, "email"}};
            for (String[] x : checks) {
                if (x[1] != null && !x[1].trim().isEmpty()) {
                    String sql = "SELECT id FROM records WHERE LOWER(TRIM(" + x[0] + "))=LOWER(TRIM(?))";
                    ArrayList<String> args = new ArrayList<>();
                    args.add(x[1].trim());
                    if (except >= 0) {
                        sql = sql + " AND id<>?";
                        args.add("" + except);
                    }
                    Cursor c = getReadableDatabase().rawQuery(sql + " LIMIT 1", (String[]) args.toArray(new String[0]));
                    try {
                        if (c.moveToFirst()) {
                            String str = x[2];
                            if (c != null) {
                                c.close();
                            }
                            return str;
                        }
                        if (c != null) {
                            c.close();
                        }
                    } catch (Throwable th) {
                        if (c == null) {
                            throw th;
                        }
                        try {
                            c.close();
                            throw th;
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                            throw th;
                        }
                    }
                }
            }
            return null;
        }
    }
}
