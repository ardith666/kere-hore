package com.klikdash.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.nio.charset.StandardCharsets;

/** Minimal pure-Java QR Code generator (Byte mode, ECC-L, Versions 1..4) -> Bitmap. */
public final class QrCodeUtil {
    private QrCodeUtil() {}

    public static Bitmap encodeToBitmap(String text, int sizePx) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = 1;
        int[] cap = {0, 17, 32, 53, 78};
        while (ver <= 4 && data.length > cap[ver]) ver++;
        if (ver > 4) ver = 4;

        int n = 17 + 4 * ver;
        boolean[][] m = new boolean[n][n];
        boolean[][] res = new boolean[n][n];

        drawFinder(m, res, 0, 0);
        drawFinder(m, res, n - 7, 0);
        drawFinder(m, res, 0, n - 7);
        for (int i = 8; i < n - 8; i++) {
            boolean b = (i % 2 == 0);
            m[6][i] = b; res[6][i] = true;
            m[i][6] = b; res[i][6] = true;
        }
        if (ver >= 2) {
            int pos = n - 7;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    boolean b = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                    m[pos + dy][pos + dx] = b;
                    res[pos + dy][pos + dx] = true;
                }
            }
        }
        m[n - 8][8] = true; res[n - 8][8] = true;
        for (int i = 0; i < 9; i++) { res[8][i] = true; res[i][8] = true; }
        for (int i = n - 8; i < n; i++) { res[8][i] = true; res[i][8] = true; }

        int[] totalCw = {0, 19, 34, 55, 80};
        int[] eccCw = {0, 7, 10, 15, 20};
        int dcw = totalCw[ver];
        int ecw = eccCw[ver];

        int[] bits = new int[dcw * 8];
        int bp = 0;
        bp = putBits(bits, bp, 4, 4); // Byte mode
        bp = putBits(bits, bp, Math.min(data.length, cap[ver]), 8);
        for (int i = 0; i < Math.min(data.length, cap[ver]); i++) {
            bp = putBits(bits, bp, data[i] & 0xFF, 8);
        }
        bp = putBits(bits, bp, 0, Math.min(4, dcw * 8 - bp));
        while (bp % 8 != 0 && bp < dcw * 8) bits[bp++] = 0;
        int padIdx = 0;
        while (bp < dcw * 8) {
            bp = putBits(bits, bp, (padIdx++ % 2 == 0) ? 0xEC : 0x11, 8);
        }

        byte[] codewords = new byte[dcw + ecw];
        for (int i = 0; i < dcw; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) v = (v << 1) | bits[i * 8 + j];
            codewords[i] = (byte) v;
        }
        byte[] ecc = rsCompute(codewords, dcw, ecw);
        System.arraycopy(ecc, 0, codewords, dcw, ecw);

        int bitIdx = 0, totalBits = codewords.length * 8;
        int dir = -1, y = n - 1;
        for (int x = n - 1; x > 0; x -= 2) {
            if (x == 6) x--;
            while (y >= 0 && y < n) {
                for (int dx = 0; dx < 2; dx++) {
                    int cx = x - dx;
                    if (!res[y][cx]) {
                        boolean bit = false;
                        if (bitIdx < totalBits) {
                            bit = ((codewords[bitIdx >> 3] >> (7 - (bitIdx & 7))) & 1) != 0;
                            bitIdx++;
                        }
                        if (((y + cx) & 1) == 0) bit = !bit; // Mask 0
                        m[y][cx] = bit;
                    }
                }
                y += dir;
            }
            dir = -dir;
            y += dir;
        }

        // Format info for ECC-L (01), Mask 0 (000) -> 0x77C4
        int fmt = 0x77C4;
        for (int i = 0; i <= 5; i++) m[8][i] = ((fmt >> (14 - i)) & 1) != 0;
        m[8][7] = ((fmt >> 8) & 1) != 0;
        m[8][8] = ((fmt >> 7) & 1) != 0;
        m[7][8] = ((fmt >> 6) & 1) != 0;
        for (int i = 9; i < 15; i++) m[14 - i][8] = ((fmt >> (14 - i)) & 1) != 0;
        for (int i = 0; i < 8; i++) m[n - 1 - i][8] = ((fmt >> (14 - i)) & 1) != 0;
        for (int i = 8; i < 15; i++) m[8][n - 15 + i] = ((fmt >> (14 - i)) & 1) != 0;

        int pad = 2;
        int grid = n + pad * 2;
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        int[] px = new int[sizePx * sizePx];
        for (int py = 0; py < sizePx; py++) {
            int gy = (py * grid) / sizePx - pad;
            for (int pxX = 0; pxX < sizePx; pxX++) {
                int gx = (pxX * grid) / sizePx - pad;
                boolean dark = (gx >= 0 && gx < n && gy >= 0 && gy < n && m[gy][gx]);
                px[py * sizePx + pxX] = dark ? Color.BLACK : Color.WHITE;
            }
        }
        bmp.setPixels(px, 0, sizePx, 0, 0, sizePx, sizePx);
        return bmp;
    }

    private static void drawFinder(boolean[][] m, boolean[][] res, int x0, int y0) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                int x = x0 + dx, y = y0 + dy;
                if (x < 0 || y < 0 || x >= m.length || y >= m.length) continue;
                boolean dark = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6 &&
                    (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                m[y][x] = dark;
                res[y][x] = true;
            }
        }
    }

    private static int putBits(int[] bits, int pos, int val, int len) {
        for (int i = len - 1; i >= 0; i--) bits[pos++] = (val >> i) & 1;
        return pos;
    }

    private static byte[] rsCompute(byte[] data, int dcw, int ecw) {
        int[] gen = new int[ecw + 1];
        gen[0] = 1;
        for (int i = 0; i < ecw; i++) {
            int root = gfPow(2, i);
            for (int j = i + 1; j > 0; j--) {
                gen[j] = gen[j - 1] ^ gfMul(gen[j], root);
            }
            gen[0] = gfMul(gen[0], root);
        }
        int[] rem = new int[ecw];
        for (int i = 0; i < dcw; i++) {
            int factor = (data[i] & 0xFF) ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, ecw - 1);
            rem[ecw - 1] = 0;
            for (int j = 0; j < ecw; j++) {
                rem[j] ^= gfMul(gen[ecw - 1 - j], factor);
            }
        }
        byte[] out = new byte[ecw];
        for (int i = 0; i < ecw; i++) out[i] = (byte) rem[i];
        return out;
    }

    private static int gfMul(int a, int b) {
        int r = 0;
        while (b > 0) {
            if ((b & 1) != 0) r ^= a;
            a <<= 1;
            if ((a & 0x100) != 0) a ^= 0x11D;
            b >>= 1;
        }
        return r;
    }

    private static int gfPow(int a, int p) {
        int r = 1;
        for (int i = 0; i < p; i++) r = gfMul(r, a);
        return r;
    }
}
