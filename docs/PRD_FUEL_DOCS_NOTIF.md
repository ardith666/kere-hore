# PRD: Fuel Log, Pengingat Dokumen, Backup/Restore & Notifikasi Servis HU

## 1. Tujuan
Melengkapi **KERE-HORE** dengan pencatatan BBM (*Fuel Log*), pengingat masa berlaku dokumen kendaraan, fitur Backup/Restore 1-klik, serta sistem notifikasi servis yang jelas namun tidak mengganggu di Head Unit (HU).

---

## 2. Ruang Lingkup Fitur

### A. Sistem Notifikasi di HU (Banner Volume + Popup Home)
1. **Label Banner di Sebelah Icon Volume (Non-Floating):**
   * Posisi di top bar, tepat di sebelah indikator Volume.
   * Menampilkan teks ringkas (misal: `⚠ SERVIS: OLI MESIN` atau `⚠ 2 PERINGATAN`) dengan efek **glow/highlight** ringan agar pengguna langsung *notice*.
2. **Popup Peringatan di Home HU:**
   * Muncul otomatis saat HU menyala jika ada part yang **lewat target KM** (`sisa <= 0`) atau dokumen yang **jatuh tempo <= 14 hari** (atau saat banner di-tap).
3. **Tindakan pada Notifikasi:**
   * **Ingatkan Nanti (Snooze):** Menutup popup untuk sesi saat ini; banner kecil di sebelah volume tetap tampil sebagai pengingat pasif.
   * **Selesaikan / Sudah Servis:** Langsung menandai part sudah diganti (`lastKm = currentKm`, target KM diperbarui) atau memperbarui tanggal dokumen, lalu menghilangkan notifikasi tersebut.

### B. Fuel Log (Catatan BBM)
* Input via Webserver HP: Tanggal, Odometer (KM), Jumlah Liter, Total Harga (Rp), dan opsi *Full Tank*.
* Analitik otomatis di tab **Analytic**: Konsumsi rata-rata (**km/liter**) dan biaya bahan bakar per km (**Rp/km**).

### C. Pengingat Dokumen (STNK, Pajak 1/5 Tahunan, SIM, Asuransi)
* Input nama dokumen & tanggal jatuh tempo (`YYYY-MM-DD`).
* Menghitung sisa hari otomatis dan memicu notifikasi jika sisa **≤ 14 hari**.

### D. Backup & Restore 1-Klik
* **Export Backup (`.json`):** Mengunduh gabungan `maintenance.json` (part, fuel, dokumen, history) + `trips/*.json` dari Webserver.
* **Import Restore:** Mengunggah file backup `.json` untuk memulihkan seluruh data di HU baru/setelah reset.
