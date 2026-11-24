# KlikDash Knowledge Base & Architecture

## 1. Build System & Struktur Project
* **Bahasa:** Pure Java Android (tanpa Gradle).
* **Build Script:** `build.sh` di root project.
* **Struktur Direktori:**
  * `src/com/klikdash/app/` — seluruh source code Java.
  * `res/` — resource Android (layout, drawable, values, mipmap).
  * `assets/` — aset statis (termasuk HTML/CSS/JS jika dibutuhkan).
  * `AndroidManifest.xml` — registrasi Activity, Service, Permission.

## 2. Komponen Utama yang Terkait
* `HomeActivity.java` — Halaman utama launcher, grid icon, dan penempatan widget (termasuk `BtNowPlaying` untuk widget musik).
* `SettingsActivity.java` / `Tweaks.java` — Pengaturan tampilan, toggle widget, dan preferensi.
* `BtNowPlaying.java` — Referensi pola widget musik di area icon halaman utama.
* `QuranData.java`, `QuranActivity.java`, `QuranService.java` — Fitur Al-Quran; audio saat ini streaming/cache dari `https://everyayah.com/data/Alafasy_128kbps/%03d%03d.mp3`.

## 3. Rencana Implementasi Teknis
1. **Storage (`MaintenanceStore.java`):**
   * Read/write `maintenance.json` menggunakan `org.json.JSONObject` & `JSONArray`.
   * Schema JSON:
     ```json
     {
       "currentKm": 45200,
       "customLabel": "ardith666",
       "parts": [
         {
           "id": "oli-mesin",
           "name": "Oli Mesin",
           "lastKm": 42000,
           "targetKm": 47000,
           "brand": "Motul",
           "type": "10W-40",
           "price": 150000,
           "date": "2026-11-01",
           "notes": ""
         }
       ]
     }
     ```
2. **Local HTTP Server (`MaintenanceServer.java`):**
   * `ServerSocket` pure Java (multi-threaded `ExecutorService` ringan) pada port `8765`.
   * Endpoint:
     * `GET /` — Mengembalikan halaman web Tailwind CSS.
     * `GET /api/data` — Mengembalikan isi `maintenance.json`.
     * `POST /api/data` — Menyimpan perubahan dari HP ke `maintenance.json` dan memicu refresh UI HU.
3. **QR Code Generator (`QrCodeUtil.java`):**
   * Generator QR Code pure Java ke `android.graphics.Bitmap` tanpa dependency eksternal.
4. **Offline Quran (`QuranService.java` / `QuranActivity.java`):**
   * Background downloader dengan `HttpURLConnection` yang mengunduh MP3 per surah/ayat ke `getExternalFilesDir("quran_audio")` atau `getFilesDir()/quran_audio/`, melewati file yang sudah ada, dan memutar dari file lokal jika tersedia.
