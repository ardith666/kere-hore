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
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class TripLogger {
    private static final String LEGACY_FILE = "trip_history.json";
    private static final String TRIPS_DIR = "trips";
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L; // 5 MB per file
    private static final float PARK_RADIUS_M = 25f;
    private static final float MOVE_DIST_M = 200f;
    private static final long MOVE_TIME_MS = 60_000L;
    private static final long TRIP_GAP_MS = 15 * 60_000L;

    private static Location lastLoc;
    private static long lastTimeMs;
    private static long currentTripId;
    private static String currentTripUuid;
    private static int currentPart = 1;

    private TripLogger() {}

    private static File tripsDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), TRIPS_DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File currentFile(Context ctx) {
        return new File(tripsDir(ctx), "trip_" + currentTripId + "_part" + currentPart + ".json");
    }

    public static synchronized void log(Context ctx, Location loc) {
        if (loc == null) return;
        long now = System.currentTimeMillis();
        String tsStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(now));
        int kmh = Math.round(loc.getSpeed() * 3.6f);

        try {
            if (currentTripId == 0 || (lastTimeMs > 0 && (now - lastTimeMs) > TRIP_GAP_MS)) {
                currentTripId = now;
                currentTripUuid = UUID.randomUUID().toString();
                currentPart = 1;
            }

            File f = currentFile(ctx);
            JSONArray arr = readArray(f);

            if (lastLoc != null && arr.length() > 0) {
                float dist = loc.distanceTo(lastLoc);
                long dt = now - lastTimeMs;
                if (dist < PARK_RADIUS_M && kmh == 0) {
                    JSONObject last = arr.optJSONObject(arr.length() - 1);
                    if (last != null) {
                        last.put("endTs", tsStr);
                        last.put("park", true);
                        writeArray(f, arr);
                        lastTimeMs = now;
                        return;
                    }
                }
                if (dist < MOVE_DIST_M && (dt < MOVE_TIME_MS || dist < PARK_RADIUS_M)) {
                    return;
                }
            }

            if (f.exists() && f.length() >= MAX_FILE_BYTES) {
                currentPart++;
                f = currentFile(ctx);
                arr = new JSONArray();
            }

            lastLoc = new Location(loc);
            lastTimeMs = now;

            JSONObject o = new JSONObject();
            o.put("tripId", currentTripId);
            o.put("tripUuid", currentTripUuid);
            o.put("part", currentPart);
            o.put("ts", tsStr);
            o.put("endTs", tsStr);
            o.put("lat", Math.round(loc.getLatitude() * 100000.0) / 100000.0);
            o.put("lon", Math.round(loc.getLongitude() * 100000.0) / 100000.0);
            o.put("kmh", kmh);
            o.put("park", kmh == 0);
            arr.put(o);
            writeArray(f, arr);
        } catch (Throwable ignored) {}
    }

    public static synchronized JSONArray load(Context ctx) {
        JSONArray all = new JSONArray();
        File legacy = new File(ctx.getFilesDir(), LEGACY_FILE);
        if (legacy.exists()) {
            JSONArray leg = readArray(legacy);
            for (int i = 0; i < leg.length(); i++) all.put(leg.opt(i));
        }
        File[] files = tripsDir(ctx).listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : files) {
                if (!f.getName().endsWith(".json")) continue;
                JSONArray arr = readArray(f);
                for (int i = 0; i < arr.length(); i++) all.put(arr.opt(i));
            }
        }
        return all;
    }

    public static String getStorageSizeText(Context ctx) {
        long bytes = new File(ctx.getFilesDir(), LEGACY_FILE).length()
                   + new File(ctx.getFilesDir(), "maintenance.json").length();
        File[] files = tripsDir(ctx).listFiles();
        if (files != null) {
            for (File f : files) bytes += f.length();
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 0.1) return String.format(Locale.US, "%.2f MB", mb);
        return String.format(Locale.US, "%.1f KB (%.3f MB)", bytes / 1024.0, mb);
    }

    public static synchronized void clear(Context ctx) {
        lastLoc = null;
        currentTripId = 0;
        currentTripUuid = null;
        currentPart = 1;
        File legacy = new File(ctx.getFilesDir(), LEGACY_FILE);
        if (legacy.exists()) legacy.delete();
        File[] files = tripsDir(ctx).listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    public static synchronized void clearTrip(Context ctx, long tripId) {
        String prefix = "trip_" + tripId + "_";
        File[] files = tripsDir(ctx).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(prefix)) f.delete();
            }
        }
    }

    private static JSONArray readArray(File f) {
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

    private static void writeArray(File f, JSONArray arr) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }
}
