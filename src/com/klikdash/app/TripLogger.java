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
    private static final int MAX_ENTRIES = 500;
    private static final float MIN_DIST_M = 200f;
    private static final long MIN_TIME_MS = 120_000L;
    private static Location lastLoc;
    private static long lastTimeMs;

    private TripLogger() {}

    public static synchronized void log(Context ctx, Location loc) {
        if (loc == null) return;
        long now = System.currentTimeMillis();
        if (lastLoc != null) {
            float dist = loc.distanceTo(lastLoc);
            if (dist < MIN_DIST_M && (now - lastTimeMs) < MIN_TIME_MS) return;
        }
        lastLoc = new Location(loc);
        lastTimeMs = now;

        try {
            JSONArray arr = load(ctx);
            JSONObject o = new JSONObject();
            o.put("ts", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(now)));
            o.put("lat", Math.round(loc.getLatitude() * 100000.0) / 100000.0);
            o.put("lon", Math.round(loc.getLongitude() * 100000.0) / 100000.0);
            o.put("kmh", Math.round(loc.getSpeed() * 3.6f));
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

    public static synchronized void clear(Context ctx) {
        save(ctx, new JSONArray());
    }

    private static void save(Context ctx, JSONArray arr) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }
}
