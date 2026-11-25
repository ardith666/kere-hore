# Panduan Setup, Debloat & Optimasi Head Unit Android (Untuk AI Agent & Pengguna)

Dokumen ini dirancang sebagai **SOP step-by-step** yang dapat diikuti langsung oleh **AI Agent** maupun pengguna untuk menyiapkan Head Unit (HU) Android *kere-hore*, melakukan debloat, menginstal launcher **KERE-HORE**, dan meringankan performa sistem.

---

## Langkah 1: Aktifkan Mode Developer / Factory Settings di HU
1. Buka **Settings (Pengaturan)** di Head Unit Android.
2. Masuk ke **System / Car Settings / About Device** (tergantung firmware HU).
3. Jika diminta **PIN / Password Developer atau Factory Settings**, coba format berikut:
   - **Tanggal hari ini:** `YYYY-MM-DD` atau `YYYYMMDD` (contoh: `2026-11-25` atau `20261125`).
   - **Kode umum HU Android (NWD/TS10/FY/AC8227L):** `123456`, `8888`, `3368`, `1617`, `7890`, `0000`.
4. Aktifkan **Developer Options** -> nyalakan **USB Debugging** dan **ADB over Network / Wireless Debugging**.

---

## Langkah 2: Cek IP & Port ADB Head Unit
1. Pastikan HU dan komputer/Mac berada di **jaringan Wi-Fi / Hotspot yang sama**.
2. Cek IP HU di **Settings -> Wi-Fi -> Detail Jaringan** (misal: `192.168.1.22`).
3. Port ADB default adalah **`5555`**, namun beberapa firmware HU menggunakan port **`8555`** atau port acak (cek di menu *Wireless Debugging* jika Android 11+).

### Perintah untuk AI Agent:
```bash
# Uji konektivitas & port
ping -c 3 <IP_HU>
nc -zv -w 2 <IP_HU> 5555 || nc -zv -w 2 <IP_HU> 8555

# Hubungkan ADB
adb connect <IP_HU>:<PORT>
adb devices
```
*(Jika muncul pop-up **"Allow USB debugging?"** di layar HU, minta pengguna klik **Always allow** -> **Allow**).*

---

## Langkah 3: Debloat Aplikasi Bawaan yang Memberatkan HU
Sebelum menghapus, AI Agent **wajib** mengecek daftar paket terpasang terlebih dahulu:
```bash
adb -s <IP_HU>:<PORT> shell pm list packages -3
```
Gunakan `pm uninstall -k --user 0 <package>` (aman, tidak merusak partisi `/system`, bisa dikembalikan dengan `cmd package install-existing <package>`):
```bash
# Contoh bloatware umum HU yang aman dihapus jika tidak dipakai:
adb -s <IP_HU>:<PORT> shell pm uninstall -k --user 0 com.google.android.youtube
adb -s <IP_HU>:<PORT> shell pm uninstall -k --user 0 com.android.chrome
adb -s <IP_HU>:<PORT> shell pm uninstall -k --user 0 com.google.android.apps.maps
```
*(Tanyakan ke pengguna terlebih dahulu aplikasi mana saja yang masih ingin dipertahankan sebelum menjalankan `pm uninstall`).*

---

## Langkah 4: Instalasi & Aktivasi Launcher KERE-HORE
```bash
# Build (jika dari source)
./build.sh

# Instal ke HU
adb -s <IP_HU>:<PORT> install -r build/kere-hore.apk

# Jadikan launcher default (opsional, tergantung firmware)
adb -s <IP_HU>:<PORT> shell cmd package set-home-activity com.klikdash.app/.HomeActivity
```

---

## Langkah 5: Optimasi Tambahan untuk Meringankan HU
Tawarkan kepada pengguna untuk menjalankan optimasi berikut via ADB:
1. **Matikan Animasi UI (terasa jauh lebih responsif):**
   ```bash
   adb -s <IP_HU>:<PORT> shell settings put global window_animation_scale 0
   adb -s <IP_HU>:<PORT> shell settings put global transition_animation_scale 0
   adb -s <IP_HU>:<PORT> shell settings put global animator_duration_scale 0
   ```
2. **Bersihkan Cache & Proses Latar Belakang:**
   ```bash
   adb -s <IP_HU>:<PORT> shell pm trim-caches 1024M
   adb -s <IP_HU>:<PORT> shell am kill-all
   ```
3. **Nonaktifkan Aplikasi Berat di Latar Belakang** (misal Google Play Services jika HU murni offline untuk musik/navigasi lokal).
