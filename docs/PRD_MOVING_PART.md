# Product Requirements Document (PRD): Moving Part Tracker, Custom Brand & Offline Quran

## 1. Latar Belakang & Tujuan
Menambahkan fitur pencatatan perawatan berkala (*moving part*) kendaraan pada **KlikDash** agar pengguna dapat memantau jadwal penggantian komponen (oli, busi, kampas rem, ban, skok, dll.) langsung dari Head Unit (HU) maupun dari HP melalui webserver lokal. Selain itu, menambahkan kustomisasi inisial/watermark di pojok kiri bawah serta fitur unduh audio Al-Quran secara penuh (*offline mode*).

---

## 2. Ruang Lingkup Fitur

### A. Pencatatan Moving Part & Estimasi KM
1. **Input Odometer (KM Saat Ini) Manual:**
   * Karena HU tidak terhubung ke ECU/OBD2 secara realtime, saat membuka menu Moving Part (atau lewat HP), sistem meminta/menyediakan input **KM Saat Ini** secara manual.
   * Sistem menghitung otomatis **Sisa KM** dan **Estimasi/Status** (*Aman*, *Segera Ganti*, *Lewat Target*) berdasarkan `Target KM - KM Saat Ini`.
2. **Data Tiap Penggantian Part:**
   * **Wajib:** Nama Part, KM Terakhir Ganti, Target KM Berikutnya (atau Interval KM).
   * **Opsional:** Harga (Rp), Merk, Tipe/Spesifikasi, Tanggal Ganti, Catatan.
3. **Daftar Part Default + Custom:**
   * Default: *Oli Mesin, Oli Gardan/Transmisi, Busi, Kampas Rem Depan, Kampas Rem Belakang, Ban Depan, Ban Belakang, Skok/Shockbreaker, Filter Udara, Aki*.
   * Pengguna dapat menambah, mengubah, dan menghapus part secara manual (custom) dari HU maupun HP.
4. **Penyimpanan Lokal (JSON):**
   * Disimpan dalam file JSON di internal storage HU (`maintenance.json` di `getFilesDir()`).

### B. Webserver Lokal + QR Code (`ip:port`)
1. **Aktivasi On-Demand:**
   * Webserver **tidak** berjalan terus-menerus di background.
   * Di dalam menu Moving Part di HU, terdapat tombol **"Aktifkan Server"** (dan "Matikan Server").
   * Saat aktif, layar HU menampilkan **QR Code** serta link URL **`http://<IP-HU>:<PORT>`** di bawahnya.
2. **Antarmuka Web Modern (Tailwind CSS):**
   * Halaman web yang diakses dari HP menggunakan **Tailwind CSS** (CDN/embedded) dengan desain modern, responsif, dan *mobile-friendly*.
   * Pengguna dapat mengupdate KM saat ini, menambah/edit/hapus data moving part, serta mengubah inisial/watermark HU dari HP.

### C. Menu Khusus & Widget Halaman Utama
1. **Menu & Icon Khusus di HU:**
   * Menambahkan `MaintenanceActivity` dan icon khusus di grid menu utama HU.
2. **Widget di Halaman Utama (Area Icon):**
   * Widget Moving Part dapat diaktifkan/nonaktifkan melalui Settings.
   * Jika **Widget Musik** dan **Widget Moving Part** sama-sama aktif, Widget Moving Part **menggunakan area icon lain (berdampingan)**, tidak menimpa/berbagi slot yang sama dengan Widget Musik.
   * Menampilkan ringkasan part yang paling dekat jatuh tempo (misal: nama part, sisa KM / target KM).

### D. Kustomisasi Inisial / Watermark Pojok Kiri Bawah
* Inisial default tetap **`ardith666`**.
* Pengguna dapat mengubah teks inisial tersebut melalui menu Moving Part / Settings (dan juga via web UI HP), tersimpan di `SharedPreferences` / JSON.

### E. Al-Quran Offline Audio Downloader
* Pada aplikasi Al-Quran (`QuranActivity` / `QuranService`), menambahkan fitur **Download Semua Audio Offline** (mengunduh seluruh surah/ayat dari `everyayah.com` ke penyimpanan lokal HU dengan progress indicator & kemampuan resume/skip file yang sudah ada).
* Saat audio sudah tersimpan lokal, pemutaran menggunakan file lokal tanpa perlu download ulang atau koneksi internet.
