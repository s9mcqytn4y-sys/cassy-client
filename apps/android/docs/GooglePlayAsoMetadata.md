# Google Play Store ASO & Android App Links Metadata (Cassy POS)

Dokumen siap tempel untuk Play Console (`com.azuredevid.cassy`) versi `v2.5.0+`.

## 1. Play Store Listing (Bahasa Indonesia - Utama)

- **App Name (maks 30 karakter)**: `Cassy POS: Kasir & QR Meja` (26 karakter)
- **Short Description (maks 80 karakter)**: `Aplikasi kasir F&B Indonesia dengan QR meja, QRIS dinamis, KDS & cetak struk.` (79 karakter)
- **Full Description (ringkas, kata kunci F&B + keamanan UU PDP)**:
  Cassy POS oleh AzureDevID adalah sistem kasir (Point of Sale) khusus tablet Android untuk kedai kopi, roastery, kafe, restoran, bakery, food court, dan cloud kitchen di Indonesia.

  Fitur Utama Kasir Tablet:
  - Koneksi Fleksibel (Cloud & LAN Lokal): Sambungkan tablet kasir ke server Cloud HTTPS maupun server toko lokal pada jaringan Wi-Fi melalui pemindaian mDNS (_cassy._tcp).
  - Sinkronisasi Menu & Pajak Terpusat: Semua harga menu, varian, pajak restoran (PB1/PPN), biaya layanan, dan total dihitung akurat oleh server gerai.
  - Pesanan QR Meja & Rute Dapur (KDS): Terima pesanan mandiri tamu langsung ke layar kasir serta teruskan tiket dapur ke stasiun Bar, Kitchen, Pastry, atau Expediter.
  - Pembayaran Tunai, QRIS Dinamis & VA: Terintegrasi pembayaran non-tunai real-time dengan status tagihan tersinkronisasi dan struk digital singkat.
  - Cetak Thermal ESC/POS Port 9100: Cetak struk pelanggan, tiket dapur, laci kasir RJ11, dan laporan tutup shift via LAN, Bluetooth, atau USB.
  - Keamanan Multi-Gerai: Sesi gerai terenkripsi menggunakan Android Keystore (Tink), isolasi data tenant Row-Level Security, serta kepatuhan UU PDP No. 27 Tahun 2022.

## 2. English Listing (`en-US` / `en-ID`)

- **App Name (max 30 chars)**: `Cassy POS: F&B Cashier & QR` (27 chars)
- **Short Description (max 80 chars)**: `Indonesian F&B tablet POS with table QR ordering, dynamic QRIS & ESC/POS print.` (79 chars)

## 3. Checklist Aset Grafis Tablet Play Console

- App Icon: `512 x 512 px` PNG 32-bit (`MaskableIcon512` / `AndroidChrome512` varian Cassy Pandan)
- Feature Graphic: `1024 x 500 px` PNG/JPG (`Pandan #1F5F52`, `Putih Tulang #F7F5F0`, `Kunyit #E9A23B`)
- 7-inch & 10-inch Tablet Screenshots (`16:10` landscape, minimal 4 layar):
  1. `StartupSplash` & `ServerConfig/DefaultCloudHttps` (Endpoint Cloud HTTPS & mDNS)
  2. `ServerConfig/Modals/MdnsDiscoveryDialog` (Penemuan Server LAN Otomatis)
  3. `StorePairing/DefaultOnline` (Penyandingan QR Tablet Kasir)
  4. `CashierPos` / `ShiftXReport` (Layar Transaksi & Laporan Shift)

## 4. Android App Links (`/.well-known/assetlinks.json`)

Tempatkan berkas berikut pada host web produksi (`https://cassy.azuredevid.com/.well-known/assetlinks.json`) agar tautan gerai membuka Cassy POS secara terverifikasi tanpa chooser:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.azuredevid.cassy",
      "sha256_cert_fingerprints": [
        "<ISI_SHA256_UPLOAD_OR_PLAY_APP_SIGNING_KEY>"
      ]
    }
  }
]
```
