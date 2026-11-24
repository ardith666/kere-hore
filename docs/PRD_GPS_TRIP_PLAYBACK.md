# PRD: Enriching GPS Trip Logger, Deduplication & Map Playback

## 1. Masalah Saat Ini
* `TripLogger` masih mencatat titik berulang meskipun mobil diam/parkir di lokasi yang sama, sehingga file `trip_history.json` cepat penuh oleh data redundan.
* Data GPS belum dikelompokkan per perjalanan (*trip*), dan belum ada fitur *playback* animasi rute di peta Leaflet JS.

---

## 2. Fitur Utama yang Akan Dibangun

### A. Deduplikasi Titik Parkir / Diam
* Jika lokasi baru berada dalam radius **< 25 meter** dari titik terakhir **dan** kecepatan `0 km/j`:
  * **Tidak membuat entri baru**, melainkan hanya meng-update `endTs` (waktu terakhir) pada entri terakhir.
  * Menandai status titik sebagai **Parkir** (`type: "park"`), sehingga terlihat jelas kapan mulai parkir (`ts`) hingga selesai (`endTs`).

### B. Pengelompokan Perjalanan (Trip Segmentation)
* Setiap entri memiliki `tripId` (otomatis membuat `tripId` baru jika jarak jeda waktu parkir melebihi **15 menit** atau saat HU baru dinyalakan).
* Di Webserver, pengguna dapat memilih:
  * **Per Perjalanan (Trip 1, Trip 2, ...)**, atau
  * **Tampilkan Semua Titik**.

### C. Playback & Animasi Rute di Leaflet JS
* Saat memilih 1 perjalanan, tersedia kontrol **Play / Pause / Reset** dan pilihan kecepatan (**1x, 2x, 5x**).
* Marker kendaraan bergerak animasi mengikuti garis *polyline* rute, disertai info *real-time* timestamp dan kecepatan (`km/j`) di setiap titik.

### D. Tambahan Nilai Guna (Ringan)
1. **Statistik Trip:** Total jarak tempuh (`km`), durasi jalan vs durasi parkir, kecepatan rata-rata, dan *top speed*.
2. **Color-Coded Route:** Warna garis rute menyesuaikan kecepatan (pelan/macet, normal, tinggi).
3. **Export GPX:** Unduh rute perjalanan dalam format `.gpx` standar.
