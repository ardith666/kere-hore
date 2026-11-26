package com.klikdash.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class MaintenanceActivity extends Activity {
    private MaintenanceServer server;
    private LinearLayout listLayout;
    private TextView kmSummaryView;
    private TextView urlView;
    private ImageView qrView;
    private Button serverBtn;
    private LinearLayout qrPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        server = new MaintenanceServer(this, () -> runOnUiThread(this::refreshList));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F19"));
        root.setPadding(32, 24, 32, 24);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Moving Part & Servis");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, tp);

        Button btnKm = makeBtn("Update KM", "#0284C7");
        btnKm.setOnClickListener(v -> showKmDialog());
        header.addView(btnKm);

        Button btnAdd = makeBtn("+ Part", "#059669");
        btnAdd.setOnClickListener(v -> showPartDialog(-1));
        header.addView(btnAdd);

        Button btnLabel = makeBtn("Inisial", "#475569");
        btnLabel.setOnClickListener(v -> showLabelDialog());
        header.addView(btnLabel);

        Button btnSwot = makeBtn("SWOT", "#D97706");
        btnSwot.setOnClickListener(v -> showSwotDialog());
        header.addView(btnSwot);

        Button btnGps = makeBtn("GPS", "#0D9488");
        btnGps.setOnClickListener(v -> showGpsDialog());
        header.addView(btnGps);

        Button btnBbm = makeBtn("+ BBM", "#2563EB");
        btnBbm.setOnClickListener(v -> showFuelDialog());
        header.addView(btnBbm);

        Button btnDok = makeBtn("Dokumen", "#9333EA");
        btnDok.setOnClickListener(v -> showDocsDialog());
        header.addView(btnDok);

        serverBtn = makeBtn("Aktifkan Server", "#7C3AED");
        serverBtn.setOnClickListener(v -> toggleServer());
        header.addView(serverBtn);

        root.addView(header);

        kmSummaryView = new TextView(this);
        kmSummaryView.setTextColor(Color.parseColor("#38BDF8"));
        kmSummaryView.setTextSize(18);
        kmSummaryView.setTypeface(null, android.graphics.Typeface.BOLD);
        kmSummaryView.setPadding(0, 8, 0, 14);
        root.addView(kmSummaryView);

        // Main content: left = list, right = QR panel
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        ScrollView scroll = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout);
        body.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        qrPanel = new LinearLayout(this);
        qrPanel.setOrientation(LinearLayout.VERTICAL);
        qrPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        qrPanel.setPadding(24, 16, 16, 16);
        qrPanel.setVisibility(View.GONE);

        qrView = new ImageView(this);
        qrPanel.addView(qrView, new LinearLayout.LayoutParams(240, 240));

        urlView = new TextView(this);
        urlView.setTextColor(Color.parseColor("#38BDF8"));
        urlView.setTextSize(14);
        urlView.setPadding(0, 12, 0, 0);
        urlView.setGravity(Gravity.CENTER);
        qrPanel.addView(urlView);

        body.addView(qrPanel, new LinearLayout.LayoutParams(300, LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refreshList();
        if (MaintenanceStore.load(this).optInt("currentKm", 0) == 0) {
            showKmDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
    }

    private Button makeBtn(String text, String hexColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(hexColor));
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = 10;
        b.setLayoutParams(lp);
        return b;
    }

    private void toggleServer() {
        if (server.isRunning()) {
            server.stop();
            serverBtn.setText("Aktifkan Server");
            qrPanel.setVisibility(View.GONE);
        } else {
            server.start();
            String url = "http://" + MaintenanceServer.getLocalIpAddress() + ":" + MaintenanceServer.PORT;
            Bitmap bmp = QrCodeUtil.encodeToBitmap(url, 240);
            qrView.setImageBitmap(bmp);
            urlView.setText("Scan QR atau buka:\n" + url);
            qrPanel.setVisibility(View.VISIBLE);
            serverBtn.setText("Matikan Server");
        }
    }

    private void refreshList() {
        JSONObject data = MaintenanceStore.load(this);
        int curKm = data.optInt("currentKm", 0);
        String lbl = data.optString("customLabel", MaintenanceStore.DEFAULT_LABEL);
        boolean wEnabled = data.optBoolean("widgetEnabled", true);
        kmSummaryView.setText("KM Saat Ini: " + MaintenanceStore.fmtKm(curKm) + " km   |   Inisial: " + lbl + "   |   Widget: " + (wEnabled ? "Aktif" : "Mati"));

        listLayout.removeAllViews();
        JSONArray parts = data.optJSONArray("parts");
        if (parts == null) return;
        for (int i = 0; i < parts.length(); i++) {
            final int idx = i;
            JSONObject p = parts.optJSONObject(i);
            if (p == null) continue;
            int lastKm = p.optInt("lastKm", 0);
            int targetKm = MaintenanceStore.effectiveTargetKm(p);
            int rem = targetKm - curKm;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundColor(Color.parseColor("#161E2E"));
            card.setPadding(20, 14, 20, 14);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = 10;
            card.setLayoutParams(clp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(p.optString("name", "Part"));
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(16);
            info.addView(tvName);

            String extra = "";
            String brand = p.optString("brand", "");
            String type = p.optString("type", "");
            int price = p.optInt("price", 0);
            if (!brand.isEmpty() || !type.isEmpty()) extra += " | " + (brand + " " + type).trim();
            if (price > 0) extra += " | Rp" + MaintenanceStore.fmtKm(price);

            TextView tvSub = new TextView(this);
            tvSub.setText("Terakhir: " + MaintenanceStore.fmtKm(lastKm) + " km -> Target: " + MaintenanceStore.fmtKm(targetKm) + " km" + extra);
            tvSub.setTextColor(Color.parseColor("#94A3B8"));
            tvSub.setTextSize(12);
            info.addView(tvSub);

            card.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvRem = new TextView(this);
            tvRem.setText("Sisa " + MaintenanceStore.fmtKm(rem) + " km");
            tvRem.setTextColor(rem <= 0 ? Color.parseColor("#FB7185") : (rem <= 1000 ? Color.parseColor("#FBBF24") : Color.parseColor("#34D399")));
            tvRem.setTextSize(14);
            tvRem.setPadding(12, 0, 16, 0);
            card.addView(tvRem);

            Button bEdit = makeBtn("Edit", "#334155");
            bEdit.setOnClickListener(v -> showPartDialog(idx));
            card.addView(bEdit);

            listLayout.addView(card);
        }
    }

    private void showKmDialog() {
        JSONObject data = MaintenanceStore.load(this);
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(data.optInt("currentKm", 0)));
        new AlertDialog.Builder(this)
            .setTitle("Masukkan KM Mobil Saat Ini")
            .setView(et)
            .setPositiveButton("Simpan", (d, w) -> {
                try {
                    int km = Integer.parseInt(et.getText().toString().trim());
                    MaintenanceStore.updateCurrentKm(this, km);
                    refreshList();
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void showLabelDialog() {
        JSONObject data = MaintenanceStore.load(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 16, 32, 16);

        EditText et = new EditText(this);
        et.setHint("Inisial pojok kiri bawah");
        et.setText(data.optString("customLabel", MaintenanceStore.DEFAULT_LABEL));
        box.addView(et);

        CheckBox cb = new CheckBox(this);
        cb.setText("Tampilkan Widget di Halaman Utama");
        cb.setChecked(data.optBoolean("widgetEnabled", true));
        box.addView(cb);

        new AlertDialog.Builder(this)
            .setTitle("Inisial & Widget Home")
            .setView(box)
            .setPositiveButton("Simpan", (d, w) -> {
                try {
                    String lbl = et.getText().toString().trim();
                    data.put("customLabel", lbl.isEmpty() ? MaintenanceStore.DEFAULT_LABEL : lbl);
                    data.put("widgetEnabled", cb.isChecked());
                    MaintenanceStore.save(this, data);
                    refreshList();
                    Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void showPartDialog(int idx) {
        JSONObject data = MaintenanceStore.load(this);
        JSONArray parts = data.optJSONArray("parts");
        JSONObject p = idx >= 0 && parts != null ? parts.optJSONObject(idx) : null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 12, 24, 12);

        EditText eName = new EditText(this); eName.setHint("Nama Part");
        EditText eLast = new EditText(this); eLast.setHint("KM Terakhir"); eLast.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText eTarget = new EditText(this); eTarget.setHint("Target KM"); eTarget.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText eBrand = new EditText(this); eBrand.setHint("Merk (opsional)");
        EditText eType = new EditText(this); eType.setHint("Tipe (opsional)");
        EditText ePrice = new EditText(this); ePrice.setHint("Harga Rp (opsional)"); ePrice.setInputType(InputType.TYPE_CLASS_NUMBER);

        if (p != null) {
            eName.setText(p.optString("name", ""));
            eLast.setText(String.valueOf(p.optInt("lastKm", 0)));
            eTarget.setText(String.valueOf(p.optInt("targetKm", 0)));
            eBrand.setText(p.optString("brand", ""));
            eType.setText(p.optString("type", ""));
            ePrice.setText(String.valueOf(p.optInt("price", 0)));
        } else {
            int cur = data.optInt("currentKm", 0);
            eLast.setText(String.valueOf(cur));
            eTarget.setText(String.valueOf(cur + 5000));
        }

        box.addView(eName); box.addView(eLast); box.addView(eTarget);
        box.addView(eBrand); box.addView(eType); box.addView(ePrice);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle(p == null ? "Tambah Moving Part" : "Edit Moving Part")
            .setView(sv)
            .setPositiveButton("Simpan", (d, w) -> {
                try {
                    JSONObject obj = p != null ? p : new JSONObject();
                    if (p == null) obj.put("id", String.valueOf(System.currentTimeMillis()));
                    obj.put("name", eName.getText().toString().trim());
                    obj.put("lastKm", parseInt(eLast.getText().toString(), 0));
                    obj.put("targetKm", parseInt(eTarget.getText().toString(), 0));
                    obj.put("brand", eBrand.getText().toString().trim());
                    obj.put("type", eType.getText().toString().trim());
                    obj.put("price", parseInt(ePrice.getText().toString(), 0));
                    if (p == null && parts != null) parts.put(obj);
                    MaintenanceStore.save(this, data);
                    refreshList();
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("Batal", null);

        if (p != null && parts != null) {
            b.setNeutralButton("Hapus", (d, w) -> {
                parts.remove(idx);
                MaintenanceStore.save(this, data);
                refreshList();
            });
        }
        AlertDialog dlg = b.create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
    }

    private void showSwotDialog() {
        JSONObject data = MaintenanceStore.load(this);
        int cur = data.optInt("currentKm", 0);
        JSONArray parts = data.optJSONArray("parts");
        int total = parts == null ? 0 : parts.length();
        StringBuilder crit = new StringBuilder();
        StringBuilder warn = new StringBuilder();
        int okCount = 0;
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.optJSONObject(i);
                if (p == null) continue;
                int rem = MaintenanceStore.effectiveTargetKm(p) - cur;
                if (rem <= 0) {
                    if (crit.length() > 0) crit.append(", ");
                    crit.append(p.optString("name", ""));
                } else if (rem <= 1000) {
                    if (warn.length() > 0) warn.append(", ");
                    warn.append(p.optString("name", ""));
                } else {
                    okCount++;
                }
            }
        }
        int health = total > 0 ? Math.round((okCount * 100f) / total) : 100;
        JSONArray fuels = data.optJSONArray("fuelLogs");
        int fuelCnt = fuels == null ? 0 : fuels.length();
        double totLit = 0;
        int totFuelRp = 0;
        if (fuels != null) {
            for (int i = 0; i < fuels.length(); i++) {
                JSONObject f = fuels.optJSONObject(i);
                if (f != null) { totLit += f.optDouble("liters", 0); totFuelRp += f.optInt("price", 0); }
            }
        }
        int gpsCnt = TripLogger.load(this).length();
        String msg = "RINGKASAN ANALITIK:\n"
            + "• Health Score: " + health + "%\n"
            + "• Log BBM: " + fuelCnt + "x (" + String.format(java.util.Locale.US, "%.1f", totLit) + " L | Rp" + MaintenanceStore.fmtKm(totFuelRp) + ")\n"
            + "• Log GPS: " + MaintenanceStore.fmtKm(gpsCnt) + " titik (" + TripLogger.getStorageSizeText(this) + ")\n\n"
            + "STRENGTHS:\n• " + total + " komponen terpantau aktif.\n\n"
            + "WEAKNESSES:\n• " + (warn.length() > 0 ? "Mendekati jadwal: " + warn : "Tidak ada part mendekati batas.") + "\n\n"
            + "OPPORTUNITIES:\n• Jadwalkan servis gabungan agar lebih hemat waktu & biaya.\n\n"
            + "THREATS:\n• " + (crit.length() > 0 ? "SEGERA GANTI: " + crit : "Semua komponen masih dalam batas aman.");
        new AlertDialog.Builder(this)
            .setTitle("Analitik & SWOT")
            .setMessage(msg)
            .setPositiveButton("Tutup", null)
            .show();
    }


    private void showGpsDialog() {
        JSONArray arr = TripLogger.load(this);
        StringBuilder sb = new StringBuilder();
        if (arr.length() == 0) {
            sb.append("Belum ada catatan perjalanan GPS.");
        } else {
            for (int i = arr.length() - 1; i >= Math.max(0, arr.length() - 50); i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                sb.append(o.optString("ts", ""))
                  .append("  |  ").append(o.optInt("kmh", 0)).append(" km/j")
                  .append("\n").append(o.optDouble("lat", 0)).append(", ").append(o.optDouble("lon", 0))
                  .append("\n\n");
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("History GPS (" + arr.length() + " titik | " + TripLogger.getStorageSizeText(this) + ")")
            .setMessage(sb.toString())
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Atur Pengingat", (d, w) -> showReminderDaysDialog())
            .show();
    }


    private void showFuelDialog() {
        JSONObject data = MaintenanceStore.load(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 12, 24, 12);
        EditText eKm = new EditText(this); eKm.setHint("KM Saat Ini"); eKm.setInputType(InputType.TYPE_CLASS_NUMBER);
        eKm.setText(String.valueOf(data.optInt("currentKm", 0)));
        EditText eLit = new EditText(this); eLit.setHint("Jumlah Liter (misal 25.5)"); eLit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText ePrice = new EditText(this); ePrice.setHint("Total Harga Rp"); ePrice.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(eKm); box.addView(eLit); box.addView(ePrice);
        JSONArray flCur = data.optJSONArray("fuelLogs");
        if (flCur != null && flCur.length() > 0) {
            StringBuilder fsb = new StringBuilder("\n20 Catatan Terakhir:\n");
            for (int i = flCur.length() - 1; i >= Math.max(0, flCur.length() - 20); i--) {
                JSONObject fo = flCur.optJSONObject(i);
                if (fo != null) fsb.append("• ").append(fo.optString("date", "")).append(" | ").append(MaintenanceStore.fmtKm(fo.optInt("km", 0))).append(" km | ").append(fo.optDouble("liters", 0)).append(" L | Rp").append(MaintenanceStore.fmtKm(fo.optInt("price", 0))).append("\n");
            }
            TextView ftv = new TextView(this);
            ftv.setText(fsb.toString());
            ftv.setTextSize(11);
            box.addView(ftv);
        }
        ScrollView fsv = new ScrollView(this);
        fsv.addView(box);
        new AlertDialog.Builder(this)
            .setTitle("Catat Isi BBM")
            .setView(fsv)
            .setPositiveButton("Simpan", (d, w) -> {
                try {
                    JSONArray fl = data.optJSONArray("fuelLogs");
                    if (fl == null) { fl = new JSONArray(); data.put("fuelLogs", fl); }
                    JSONObject o = new JSONObject();
                    o.put("id", String.valueOf(System.currentTimeMillis()));
                    o.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()));
                    o.put("km", parseInt(eKm.getText().toString(), 0));
                    o.put("liters", Double.parseDouble(eLit.getText().toString().trim()));
                    o.put("price", parseInt(ePrice.getText().toString(), 0));
                    fl.put(o);
                    MaintenanceStore.save(this, data);
                    Toast.makeText(this, "BBM tersimpan", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void showDocsDialog() {
        JSONObject data = MaintenanceStore.load(this);
        JSONArray docs = data.optJSONArray("documents");
        if (docs == null) return;
        String[] items = new String[docs.length()];
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.optJSONObject(i);
            String due = doc.optString("dueDate", "");
            items[i] = doc.optString("name", "") + " (" + (due.isEmpty() ? "Belum diisi" : due) + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle("Pengingat Dokumen (Tap untuk edit)")
            .setItems(items, (d, idx) -> {
                JSONObject doc = docs.optJSONObject(idx);
                EditText et = new EditText(this);
                et.setHint("YYYY-MM-DD (misal 2027-05-20)");
                et.setText(doc.optString("dueDate", ""));
                new AlertDialog.Builder(this)
                    .setTitle(doc.optString("name", "Dokumen"))
                    .setView(et)
                    .setPositiveButton("Simpan", (d2, w2) -> {
                        try {
                            doc.put("dueDate", et.getText().toString().trim());
                            MaintenanceStore.save(this, data);
                            Toast.makeText(this, "Dokumen disimpan", Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) {}
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            })
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Atur Pengingat", (d, w) -> showReminderDaysDialog())
            .show();
    }

    private void showReminderDaysDialog() {
        JSONObject data = MaintenanceStore.load(this);
        int[] opts = {30, 14, 7, 3, 2, 1, 0};
        String[] labels = {"30 hari sebelum", "14 hari sebelum", "7 hari sebelum", "3 hari sebelum", "2 hari sebelum", "1 hari sebelum", "Hari-H (0 hari)"};
        boolean[] checked = new boolean[opts.length];
        JSONArray cur = data.optJSONArray("docReminderDays");
        for (int i = 0; i < opts.length; i++) {
            if (cur != null) {
                for (int j = 0; j < cur.length(); j++) {
                    if (cur.optInt(j, -1) == opts[i]) checked[i] = true;
                }
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("Pilih Jadwal Pengingat (Multiple)")
            .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("Simpan", (d, w) -> {
                try {
                    JSONArray arr = new JSONArray();
                    for (int i = 0; i < opts.length; i++) if (checked[i]) arr.put(opts[i]);
                    data.put("docReminderDays", arr);
                    MaintenanceStore.save(this, data);
                    Toast.makeText(this, "Jadwal pengingat disimpan", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }
}
