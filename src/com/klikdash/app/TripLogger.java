package com.klikdash.app;

import android.content.Context;
import android.location.Location;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TripLogger {
    private static final String FILE_NAME = "trip_history.json";
    private static final int MAX_ENTRIES = 1000;
    private static final float MIN_DIST_M = 25f;
    private static final long TRIP_GAP_MS = 15 * 60_000L;
    private static Location lastLoc;
    private static long lastTimeMs;
    private static long currentTripId;

    private TripLogger() {}

    public static synchronized void log(Context ctx, Location loc) {
        if (loc == null) return;
        long now = System.currentTimeMillis();
        String tsStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(now));
        int kmh = Math.round(loc.getSpeed() * 3.6f);

        try {
            JSONArray arr = load(ctx);
            if (currentTripId == 0 || (lastTimeMs > 0 && (now - lastTimeMs) > TRIP_GAP_MS)) {
                currentTripId = now;
            }

            if (lastLoc != null && arr.length() > 0) {
                float dist = loc.distanceTo(lastLoc);
                if (dist < MIN_DIST_M && kmh == 0) {
                    JSONObject last = arr.optJSONObject(arr.length() - 1);
                    if (last != null) {
                        last.put("endTs", tsStr);
                        last.put("park", true);
                        save(ctx, arr);
                        lastTimeMs = now;
                        return;
                    }
                }
            }

            lastLoc = new Location(loc);
            lastTimeMs = now;

            JSONObject o = new JSONObject();
            o.put("tripId", currentTripId);
            o.put("ts", tsStr);
            o.put("endTs", tsStr);
            o.put("lat", Math.round(loc.getLatitude() * 100000.0) / 100000.0);
            o.put("lon", Math.round(loc.getLongitude() * 100000.0) / 100000.0);
            o.put("kmh", kmh);
            o.put("park", kmh == 0);
            arr.put(o);
            while (arr.length() > MAX_ENTRIES) arr.remove(0);
            save(ctx, arr);
        } catch (Throwable ignored) {}
    }

    public static synchronized JSONArray load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                return new JSONArray(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            } catch (Throwable ignored) {}
        }
        return new JSONArray();
    }

    public static String getStorageSizeText(Context ctx) {
        long bytes = new File(ctx.getFilesDir(), FILE_NAME).length()
                   + new File(ctx.getFilesDir(), "maintenance.json").length();
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 0.1) return String.format(Locale.US, "%.2f MB", mb);
        return String.format(Locale.US, "%.1f KB (%.3f MB)", bytes / 1024.0, mb);
    }

    public static synchronized void clear(Context ctx) {
        lastLoc = null;
        currentTripId = 0;
        save(ctx, new JSONArray());
    }

    private static void save(Context ctx, JSONArray arr) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }
}
