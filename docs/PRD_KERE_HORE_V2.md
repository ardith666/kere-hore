# Product Requirements Document (PRD): KERE-HORE v2.0 — Cleanup, Moving Part Analytics & GPS Trip History

## 1. Tujuan
Menyempurnakan aplikasi **KERE-HORE** (sebelumnya KlikDash) agar lebih ringan, bersih dari sisa branding lama dan fitur Asisten AI, serta menambahkan analitik servis kendaraan dan pencatatan histori perjalanan GPS yang dapat diakses melalui webserver lokal.

---

## 2. Ruang Lingkup Perubahan

### A. Rebranding Total ke `KERE-HORE` & Penghapusan Asisten AI
1. **Rebranding:**
   * Mengganti label aplikasi dan teks yang masih menyebut `KlikDash` menjadi `KERE-HORE` (termasuk `AndroidManifest.xml`, `SettingsActivity`, dan judul halaman web).
2. **Hapus Fitur Asisten AI:**
   * Menghapus/menonaktifkan tab Asisten AI di `SettingsActivity`, tile/launcher `AskActivity`, serta referensi terkait agar ukuran APK dan penggunaan memori lebih ringan.

### B. Moving Part History, Chart, SWOT & Export PDF (Webserver)
1. **History Penggantian Part (`maintenance.json`):**
   * Setiap penggantian/edit part dapat dicatat ke dalam `history` (tanggal, KM ganti, merk, tipe, harga, catatan).
2. **Dashboard Analitik di Webserver:**
   * **Chart & Grafik:** Visualisasi biaya servis dan persentase sisa umur pakai tiap part (menggunakan Chart.js ringan di web UI).
   * **Analisis SWOT & Insight:** Ringkasan otomatis (*Strengths, Weaknesses, Opportunities, Threats*) berdasarkan kondisi part (part kritis/lewat target, total biaya perawatan, pola servis).
   * **Export ke PDF:** Tombol cetak/simpan laporan servis & riwayat penggantian ke format PDF langsung dari browser HP.

### C. GPS Trip / Movement History Logger (Ringan)
1. **Background Logger Ringan:**
   * Memanfaatkan listener GPS yang sudah aktif di `HomeActivity` untuk mencatat titik pergerakan hanya ketika berpindah jarak signifikan (misal ≥ 200 meter) atau interval minimal (misal ≥ 2 menit), agar hemat CPU/storage.
2. **Penyimpanan JSON (`trip_history.json`):**
   * Menyimpan timestamp, latitude, longitude, kecepatan (km/jam), dan estimasi jarak akumulatif dengan batas maksimal entri (misal 500 titik terakhir) agar file tetap kecil.
3. **Tampilan di Webserver:**
   * Tab/bagian **Riwayat Perjalanan GPS** pada webserver lokal untuk melihat log waktu, kecepatan, dan koordinat perjalanan (beserta tautan buka di Google Maps).
