# ADR & SOP: Keamanan Store Pairing QR & Kredensial Perangkat Cassy POS

Dokumen ini mendefinisikan langkah respons insiden (SOP) untuk kebocoran QR pairing atau kredensial perangkat, standar hardening klien Android saat ini, dan arsitektur masa depan (short-lived single-use `pairing_token`).

## 1. Model Kredensial Backend Saat Ini

Backend (`myCaffe-backend`) memisahkan identitas perangkat POS dan otentikasi kasir:

| Aset | Format Token | Penyimpanan Server | Endpoint Terbit & Cabut |
|---|---|---|---|
| Device Credential | `<store_id>.<device_id>.<secret>` | Hash-only di database (`RegisterDevice`, secret tampil sekali) | `POST /api/v1/store/devices`, `POST /api/v1/store/devices/:id/revoke` |
| Staff Badge | `<store_id>.<badge_id>.<secret>` | Hash-only di database (`IssueBadge`, selalu wajib 6-digit PIN + lockout gagal) | `POST /api/v1/staff/:id/badge`, `POST /api/v1/staff/:id/badge/revoke` |

Pada sisi Android (`apps/android`), `StorePairingViewModel.parseQrPayload` saat ini menerima JSON QR dengan field `store_slug`, `email`, `badge`, dan `device_credential` atau `pairing_token`. Semua kredensial di perangkat disimpan terenkripsi dengan Google Tink + Android Keystore.

## 2. SOP Respons Insiden: Kebocoran QR Pairing

Jalankan langkah berurutan ini bila QR pairing kasir/perangkat difoto, tersebar ke luar gerai, atau tablet hilang:

1. **Identifikasi objek yang bocor:** pastikan apakah QR berisi `device_credential`, `badge`, atau keduanya.
2. **Cabut Device Credential segera:** panggil `POST /api/v1/store/devices/:id/revoke`. Efek backend:
   - Semua request dari perangkat itu langsung ditolak dengan HTTP `401 DEVICE_INVALID`.
   - Verifier PIN offline serta akses `/api/v1/device/catalog` dibatalkan seketika.
3. **Cabut Staff Badge bila QR staf ikut bocor:** panggil `POST /api/v1/staff/:id/badge/revoke`. Meski login badge tetap membutuhkan PIN 6 digit dengan proteksi lockout, pencabutan memutus token lama secara permanen.
4. **Terbitkan kredensial pengganti hanya di outlet fisik:** gunakan `POST /api/v1/store/devices` untuk tablet pengganti dan pairing ulang secara langsung, lalu pastikan tablet yang tidak dikenali keluar dari sesi (`StorePairing` locked state).
5. **Audit log akses:** periksa log otentikasi tenant untuk percobaan login dari IP atau jam operasional di luar kebiasaan gerai.

## 3. Hardening Klien Android (Baseline Saat Ini)

- **Scan ulang bersih:** membuka pemindai kamera, membuka dialog USB Scanner, atau menghapus QR pada `StorePairingViewModel` membersihkan error validasi form sebelumnya agar operator tidak terkecoh oleh error lama.
- **Region of Interest (ROI) pemindai:** `CameraQrScannerView` memfilter hasil ML Kit melalui `isInsideCenterRoi` hanya pada kotak bidik tengah agar QR acak di pinggir meja tidak ikut terbaca.
- **Validasi URL Server ketat:** `ServerConfigViewModel` memvalidasi skema `http`/`https`, host non-kosong, dan rentang port `1..65535`, serta menampilkan peringatan cleartext (`showCleartextWarning`) untuk HTTP non-loopback agar kredensial tidak dikirim lewat jaringan terbuka tanpa HTTPS.
- **Copy aman untuk kasir:** UI tidak menampilkan jargon teknis (`JSON`, `payload`, `HID`) di layar kasir.

## 4. ADR Masa Depan: Short-Lived Single-Use `pairing_token`

### Konteks & Masalah
Membungkus `device_credential` jangka panjang ke dalam QR statis di Owner Dashboard membuat risiko kebocoran foto QR tetap berlaku hingga owner melakukan revoke manual.

### Keputusan Arsitektur (Target Backend + `apps/web` + `apps/android`)
1. **Owner Dashboard (`apps/web`):** belum memiliki UI pembuat QR pairing gerai (tercatat di `docs/TechDebt.md`). Ketika dibangun, Owner Dashboard tidak boleh menanamkan secret perangkat permanen ke QR.
2. **Endpoint penukaran token (`myCaffe-backend`):**
   - `POST /api/v1/store/pairing-tokens` menerbitkan `pairing_token` pendek ber-TTL 5 menit dan single-use (disimpan hash-nya + `expires_at` + `consumed_at`).
   - QR di Owner Dashboard hanya memuat `store_slug`, `pairing_token`, dan opsional `api_base_url`.
   - `POST /api/v1/pos/devices/pair` menukarkan `pairing_token` sekali pakai menjadi `device_credential` baru (gagal jika kedaluwarsa atau sudah dipakai), lalu `consumed_at` langsung dikunci dalam transaksi database.
3. **Klien Android:** `StorePairingViewModel.parseQrPayload` sudah siap menerima field `pairing_token`; PR implementasi penuh cukup menyambungkan penukaran token sebelum login staf.
