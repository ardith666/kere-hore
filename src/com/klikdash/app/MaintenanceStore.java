package com.klikdash.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class MaintenanceStore {
    private static final String FILE_NAME = "maintenance.json";
    public static final String DEFAULT_LABEL = "ardith666";

    private MaintenanceStore() {}

    public static synchronized JSONObject load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                JSONObject obj = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                if (!obj.has("customLabel")) obj.put("customLabel", DEFAULT_LABEL);
                if (!obj.has("widgetEnabled")) obj.put("widgetEnabled", true);
                if (!obj.has("parts")) obj.put("parts", defaultParts());
                return obj;
            } catch (Throwable ignored) {}
        }
        JSONObject def = new JSONObject();
        try {
            def.put("currentKm", 0);
            def.put("customLabel", DEFAULT_LABEL);
            def.put("widgetEnabled", true);
            def.put("parts", defaultParts());
            save(ctx, def);
        } catch (Throwable ignored) {}
        return def;
    }

    public static synchronized boolean save(Context ctx, JSONObject data) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized void updateCurrentKm(Context ctx, int newKm) {
        JSONObject data = load(ctx);
        int oldKm = data.optInt("currentKm", 0);
        try {
            data.put("currentKm", newKm);
            if (oldKm == 0 && newKm > 0) {
                JSONArray parts = data.optJSONArray("parts");
                if (parts != null) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.optJSONObject(i);
                        if (p != null && p.optInt("lastKm", 0) == 0) {
                            int interval = p.optInt("targetKm", 5000);
                            if (interval <= 0) interval = 5000;
                            p.put("lastKm", newKm);
                            p.put("targetKm", newKm + interval);
                        }
                    }
                }
            }
            save(ctx, data);
        } catch (Throwable ignored) {}
    }

    public static java.util.List<JSONObject> getNearestParts(Context ctx, int maxCount) {
        JSONObject data = load(ctx);
        int cur = data.optInt("currentKm", 0);
        JSONArray parts = data.optJSONArray("parts");
        java.util.List<JSONObject> list = new java.util.ArrayList<>();
        if (parts == null) return list;
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p != null && p.optInt("targetKm", 0) > 0) list.add(p);
        }
        java.util.Collections.sort(list, (a, b) -> {
            int ra = effectiveTargetKm(a) - cur;
            int rb = effectiveTargetKm(b) - cur;
            return Integer.compare(ra, rb);
        });
        return list.size() > maxCount ? list.subList(0, maxCount) : list;
    }

    public static String getCustomLabel(Context ctx) {
        String lbl = load(ctx).optString("customLabel", DEFAULT_LABEL).trim();
        return lbl.isEmpty() ? DEFAULT_LABEL : lbl;
    }

    public static boolean isWidgetEnabled(Context ctx) {
        return load(ctx).optBoolean("widgetEnabled", true);
    }

    public static int effectiveTargetKm(JSONObject p) {
        int last = p.optInt("lastKm", 0);
        int target = p.optInt("targetKm", 0);
        if (target > 0 && target < last) return last + target;
        return target;
    }

    public static String fmtKm(int km) {
        return String.format(java.util.Locale.GERMAN, "%,d", km);
    }

    public static JSONObject getMostUrgentPart(Context ctx) {
        JSONObject data = load(ctx);
        int currentKm = data.optInt("currentKm", 0);
        JSONArray parts = data.optJSONArray("parts");
        if (parts == null || parts.length() == 0) return null;
        JSONObject best = null;
        int minRem = Integer.MAX_VALUE;
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p == null) continue;
            int target = effectiveTargetKm(p);
            if (target <= 0) continue;
            int rem = target - currentKm;
            if (rem < minRem) {
                minRem = rem;
                best = p;
            }
        }
        return best != null ? best : parts.optJSONObject(0);
    }

    private static JSONArray defaultParts() {
        JSONArray arr = new JSONArray();
        String[][] defs = {
            {"oli-mesin", "Oli Mesin", "0", "5000"},
            {"oli-gardan", "Oli Gardan / Transmisi", "0", "10000"},
            {"busi", "Busi", "0", "20000"},
            {"kampas-depan", "Kampas Rem Depan", "0", "20000"},
            {"kampas-belakang", "Kampas Rem Belakang", "0", "25000"},
            {"ban-depan", "Ban Depan", "0", "30000"},
            {"ban-belakang", "Ban Belakang", "0", "30000"},
            {"skok", "Skok / Shockbreaker", "0", "50000"},
            {"filter-udara", "Filter Udara", "0", "15000"},
            {"aki", "Aki / Battery", "0", "40000"}
        };
        for (String[] d : defs) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", d[0]);
                o.put("name", d[1]);
                o.put("lastKm", Integer.parseInt(d[2]));
                o.put("targetKm", Integer.parseInt(d[3]));
                o.put("brand", "");
                o.put("type", "");
                o.put("price", 0);
                o.put("date", "");
                o.put("notes", "");
                arr.put(o);
            } catch (Throwable ignored) {}
        }
        return arr;
    }
}
