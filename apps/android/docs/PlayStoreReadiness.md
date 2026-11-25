# Play Store Readiness (Cassy POS, `com.azuredevid.cassy`)

Status 2026-11-25, FL1 W3 (`Docs/PRD/PrdFrontendUxAndLaunch.md` bagian 7, FL-Q8, FL-Q9). Listing copy siap tempel ada di [GooglePlayAsoMetadata.md](GooglePlayAsoMetadata.md); dokumen ini memuat jawaban formulir Play Console, deklarasi izin, checklist listing, rencana track, dan sisa pekerjaan Director.

## 1. Ringkasan

Sisi engineering siap untuk track internal: workflow `android-release.yml` membangun AAB prod (R8, `versionCode` dari nomor run CI, `versionName` dari tag `v*`), halaman hapus akun `/hapus-akun` aktif, dan tautan "Hapus akun toko" ada di Pengaturan Toko (hanya owner). Yang memblokir unggahan pertama ada di tangan Director: D-U-N-S, akun Play organisasi, konfirmasi upload key, dan dua keputusan Data Safety di bagian 8.

## 2. Build rilis

| Item | Nilai |
|---|---|
| Workflow | `.github/workflows/android-release.yml` (tag `v*` atau `workflow_dispatch` dengan `version_name`) |
| Task | `bundleProdRelease` (flavor `prod`, `BASE_API_URL` `https://api.abbdullaziz.online`, `PUBLIC_WEB_URL` `https://pos.abbdullaziz.online`) |
| `versionCode` | `github.run_number` lewat `ANDROID_VERSION_CODE` (lokal tetap 1) |
| `versionName` | tag tanpa awalan `v`, wajib semver |
| Tanda tangan | Upload key hanya bila secret `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` ada; tanpa secret AAB tidak bertanda tangan dan tidak bisa diunggah |
| Artefak | `cassy-pos-<versi>-<run>-aab` (30 hari) dan `cassy-pos-<versi>-<run>-mapping` (`mapping.txt`, 90 hari; unggah ke Play Console untuk deobfuscation crash) |
| Signing produksi | Play App Signing (Google memegang app signing key; kita hanya memegang upload key) |

Catatan: tag `v*.*.*` juga memicu `web-publish.yml`. Satu tag = rilis web dan Android bersama.

## 3. Data Safety (jawaban formulir)

Data diproses untuk gerai (mitra) yang memakai Cassy; tamu dan pelanggan gerai tidak memasang aplikasi ini.

| Kategori Play | Tipe data | Dikumpulkan | Dibagikan | Tujuan | Wajib/opsional |
|---|---|---|---|---|---|
| Personal info | Nama (staf, pelanggan, tamu) | Ya | Tidak | App functionality, Account management | Wajib untuk staf, opsional untuk pelanggan |
| Personal info | Alamat email (staf, pemilik, pelanggan untuk struk) | Ya | Tidak | App functionality, Account management | Wajib untuk staf, opsional untuk pelanggan |
| Personal info | Nomor telepon (staf, pelanggan, tamu) | Ya | Tidak | App functionality, Account management | Opsional |
| Financial info | Riwayat pembelian (pesanan, pembayaran, refund) | Ya | Tidak | App functionality | Wajib |
| Device or other IDs | ID perangkat kasir (kredensial pairing, bukan Android ID) | Ya | Tidak | App functionality, Fraud prevention and security | Wajib |

- Tidak dikumpulkan: lokasi (izin lokasi hanya sampai SDK 30 untuk pemindaian Bluetooth lama, data lokasi tidak dibaca), kontak, foto/video (kamera hanya memindai QR pairing di perangkat, gambar tidak disimpan atau dikirim), audio, file, kesehatan, pesan, aktivitas web, data iklan.
- Pembayaran tamu diproses Midtrans dengan kredensial milik gerai (BYOK). Midtrans bertindak sebagai pemroses atas nama gerai, jadi dijawab "tidak dibagikan" sesuai definisi Play untuk penyedia layanan.
- Tidak ada SDK analitik, iklan, atau crash reporting pihak ketiga di build prod.
- Encrypted in transit: Ya, lihat syarat di bagian 8.1.
- Pengguna dapat meminta penghapusan data: Ya.
  - URL hapus akun: `https://pos.abbdullaziz.online/hapus-akun`
  - Isi: OTP ke email pemilik, masa tenggang 30 hari (bisa dibatalkan dari web atau POS), lalu nama, email, telepon, PIN staf, kontak pelanggan/tamu, dan kontak pemilik dianonimkan; toko dan perangkat dinonaktifkan. Catatan transaksi, pembayaran, shift, laporan, dan faktur disimpan tanpa data pribadi selama masa retensi pajak.
- URL kebijakan privasi: `https://pos.abbdullaziz.online/legal` (tamu) dan `https://pos.abbdullaziz.online/legal/mitra` (mitra). Gunakan `/legal/mitra` di Play Console karena pengguna aplikasi adalah mitra.

## 4. Deklarasi izin

| Izin | Batas | Alasan untuk Play Console |
|---|---|---|
| `CAMERA` (`android.hardware.camera` tidak wajib) | Semua SDK | Memindai kode QR pairing tablet kasir ke toko. Gambar tidak disimpan |
| `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT` | SDK 31+ | Mencari dan menyambung printer struk thermal Bluetooth |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | `maxSdkVersion=30` | Printer Bluetooth di Android 11 ke bawah |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | `maxSdkVersion=30` | Syarat sistem untuk pemindaian Bluetooth di Android 11 ke bawah; aplikasi tidak membaca lokasi. Tidak ada izin lokasi latar belakang |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` | Semua SDK | API Cassy, printer LAN port 9100, penemuan server toko lewat mDNS |

Tidak ada izin sensitif yang memicu formulir deklarasi khusus (SMS, call log, all files access, background location, accessibility).

## 5. Checklist listing

| Item | Spesifikasi | Status |
|---|---|---|
| Ikon aplikasi | 512 x 512 PNG 32-bit | Sumber ada (`apps/web/public/AndroidChrome512.png`); perlu ekspor final tanpa transparansi tepi |
| Feature graphic | 1024 x 500 PNG/JPG, palet Pandan/Putih Tulang/Kunyit | Belum dibuat |
| Screenshot tablet 10 inci | Minimal 4, landscape 16:10 | Ambil dari artefak CI `android-ui-v2.5.0-screenshots` (`android-ui-screenshots.yml`); daftar layar di GooglePlayAsoMetadata.md bagian 3 |
| Screenshot ponsel | Minimal 2 | Belum ada AVD ponsel di workflow screenshot; tambahkan atau ambil manual |
| Judul, deskripsi singkat ID | 30 / 80 karakter | Siap (GooglePlayAsoMetadata.md bagian 1) |
| Deskripsi lengkap ID | maks 4.000 karakter | Siap (bagian 1) |
| Judul, deskripsi singkat EN | 30 / 80 karakter | Siap (bagian 2) |
| Deskripsi lengkap EN | maks 4.000 karakter | Belum ditulis |
| Kategori | Business | Pilih di Play Console |
| Email kontak | Alamat dukungan AzureDevID | Ditentukan Director |
| Content rating | Kuesioner IARC (aplikasi bisnis, tanpa konten pengguna publik) | Diisi di Play Console |
| Target audience | 18+ (alat kerja usaha) | Diisi di Play Console |
| Ads | Tidak ada iklan | Diisi di Play Console |
| App access | Akun demo untuk reviewer (kode toko, email, PIN) | Siapkan toko demo khusus reviewer |

## 6. Rencana track

1. **Internal testing**: AAB bertanda tangan dari tag `v1.0.0`, penguji = tim AzureDevID. Exit: terpasang dari Play di tablet uji, pairing, transaksi tunai dan QRIS sandbox, cetak struk.
2. **Closed testing**: 2 sampai 5 gerai pilot. Akun organisasi tidak terkena aturan 12 penguji x 14 hari (FL-Q8). Exit: satu minggu tanpa crash kritis di Play Console (mapping R8 sudah diunggah).
3. **Production**: rollout bertahap 20%, 50%, 100%.

Semua track menunggu D-U-N-S AzureDevID dan akun Play organisasi.

## 7. Yang sudah diverifikasi

- Workflow rilis: run uji `workflow_dispatch` 45665468221 di `main` (`versionName` `0.0.0-test`, `versionCode` 1) lulus, menghasilkan `cassy-pos-0.0.0-test-1-aab` (19,8 MB) dan `cassy-pos-0.0.0-test-1-mapping`. AAB ditandatangani karena keempat secret upload key sudah ada di repo.
- `/hapus-akun`: alur OTP, permintaan 30 hari, permintaan ganda, dan pembatalan diuji e2e (Playwright + axe) dan terhadap backend (integration test backend PR #183).
- Tautan "Hapus akun toko": hanya tampil untuk peran owner, membuka `PUBLIC_WEB_URL/hapus-akun` di browser.

## 8. Sisa pekerjaan Director

1. **Konfigurasi cleartext prod.** `network_security_config.xml` di `src/main` mengizinkan HTTP ke `192.168.10.10`, `localhost`, `127.0.0.1`, `10.0.2.2` (server toko di LAN). Bila server LAN dipakai di produksi, data pesanan bisa berjalan tanpa TLS dan jawaban "encrypted in transit: Ya" tidak akurat. Pilihan: hapus pengecualian dari flavor prod (disarankan; engineering bisa mengerjakan), atau jawab sesuai kenyataan.
2. **Masa retensi** catatan keuangan dan pajak: konfirmasi dengan penasihat hukum (saat ini 10 tahun di `ledger_retention_until`).
3. **D-U-N-S** untuk AzureDevID, lalu daftar akun Play organisasi dan verifikasi identitas.
4. **Upload key**: keempat secret di bagian 2 sudah terisi di repo `cassy-client`. Pastikan keystore itu tersimpan di Vault `SecurityRecovery` dan sertifikatnya yang didaftarkan sebagai upload key saat mengaktifkan Play App Signing. Bila bukan, ganti secret; engineering tidak membuat kunci atau kredensial.
5. **Aset listing**: feature graphic 1024 x 500, ekspor ikon 512 final, screenshot ponsel, deskripsi lengkap EN.
6. **Akun reviewer** di toko demo dan email dukungan untuk listing.
7. **Play Console**: isi Data Safety (bagian 3), deklarasi izin (bagian 4), content rating, target audience, kategori Business, lalu unggah AAB dan `mapping.txt` ke track internal.
