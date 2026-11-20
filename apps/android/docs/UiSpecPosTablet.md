# UI Spec: Kasir Tablet (Stitch, Source of Truth)

Status: v1 2026-11-20. Arah desain: [DesignPosTablet.md](DesignPosTablet.md). Dokumen ini yang mengikat; gambar Stitch hanya acuan komposisi.

## 1. Ringkasan Eksekutif

Layar kasir tablet saat ini membagi lebar dalam persentase (`PosLandscapeScreen`: rail 12%, katalog 53%, pesanan 35%). Di tablet 13 inci dan layar besar, rail menjadi sekitar 150dp, panel pesanan lebih dari 600dp, dan field ikut melebar. Rekomendasi: ganti ke lebar tetap (rail 88dp, panel pesanan 420dp, katalog sisa) dengan kartu katalog lebar tetap dan dialog yang dibatasi lebarnya. Ini satu PR tata letak, tanpa perubahan alur bisnis.

## 2. Sumber Stitch

Project `Cassy POS Tablet` (`3218044368979484496`), design system `assets/a8e62afbb8554174bf487a001cad5f7e`. Ambil HTML lewat Stitch MCP `get_screen` lalu `download_file`.

| Layar | Screen id | Gambar lokal (`apps/android/screenshots/stitch/PosLandscape/`) |
|---|---|---|
| Kasir utama | `1326d33887be4402910ca110d791fc18` | `CashierMain.png` |
| Bayar tunai | `a785373be4cc43248685011ee31ec565` | `PaymentCashDialog.png` |
| Bagi tagihan per item | `0b7f4ae112144b29999b895ae8db563b` | `SplitByItemDialog.png` |

Folder `screenshots/` di-gitignore, jadi gambar hanya ada di mesin lokal. Tabel di dokumen ini adalah acuan bersama.

## 3. Tata Letak Kasir Utama (`PosLandscapeScreen`)

| Area | Aturan | Komponen |
|---|---|---|
| Rail navigasi | Lebar tetap 88dp; item 64dp tinggi, ikon 24dp, label 14sp; status printer berteks "Printer siap"/"Printer mati" di bawah | `PosNavRail` |
| Bilah shift | Tinggi 48dp, nama kasir, jam buka, tombol "Kas masuk/keluar" 48dp | `ShiftStatusBanner` |
| Cari | `widthIn(max = 360.dp)`, tinggi 48dp | `PosCatalogGrid` |
| Kategori | Chip 48dp tinggi, gulir horizontal | `PosCatalogGrid` |
| Grid katalog | `GridCells.Adaptive(minSize = 168.dp)` dengan kartu `widthIn(max = 200.dp)`; foto 4:3, nama 16sp, harga 16sp bold tabular; produk habis berlabel "Habis", tidak bisa ditekan | `PosCatalogGrid` |
| Ruang lebar sisa | Di atas 1440dp, kolom katalog tidak melebar; tampilkan ringkasan kerja (pesanan aktif, meja terisi) di header katalog | `PosCatalogGrid` |
| Panel pesanan | Lebar tetap 420dp; header meja dengan tombol "Ganti meja" 48dp | `PosOrderSummaryPanel` |
| Baris pesanan | Tombol hapus 48dp di kiri; stepper 48x48dp di kanan; baris terkirim memakai tombol teks "Batalkan item" 48dp di kiri bawah, jauh dari stepper | `PosOrderSummaryPanel`, `PosTabItemVoidDialog` |
| Ringkasan | Subtotal, layanan, pajak, total 28sp tabular; semua angka dari kuotasi server | `PosOrderSummaryPanel` |
| Aksi | "Kirim ke dapur" 48dp sekunder; "Bayar Rp ..." 64dp primer, paling bawah kanan | `PosOrderSummaryPanel` |

Breakpoint: tiga kolom tetap mulai lebar 840dp (`WindowWidthSizeClass.Expanded`). Di bawahnya tetap memakai `PosMobilePortraitLayout`.

## 4. Dialog Bayar Tunai (`PosCashModal`)

- Lebar `widthIn(max = 640.dp)`, dua kolom: kiri total 32sp, "Uang diterima" baca saja, "Kembalian" 28sp warna pandan; kanan keypad 3x4, tombol 72dp.
- Tombol cepat 2x2 tinggi 64dp: "Uang pas", lalu nominal pembulatan ke atas dari total (contoh Rp 150.000, Rp 200.000). Nominal di bawah total nonaktif dengan teks "Kurang dari total".
- "Konfirmasi tunai" 64dp, nonaktif sampai uang diterima tidak kurang dari total. "Batal" 64dp di kiri.
- Koreksi dari keluaran Stitch: label 11-12px dinaikkan ke 14sp; tombol tutup 48dp (Stitch 40dp).

## 5. Dialog Bagi Tagihan per Item (`PosItemSplitDialog`)

- Layar penuh di tablet. Header: judul, ringkasan pesanan, stepper "Jumlah orang" 48dp, tombol tutup 48dp.
- Kiri: satu baris per unit (56dp) dengan chip tujuan "Orang n" atau "Belum dibagi" (gaya peringatan dengan teks).
- Kanan: kartu per orang `widthIn(max = 280.dp)`, berisi unitnya dan perkiraan nominal berlabel "Perkiraan, total final dari server".
- Bawah: "n unit belum dibagi", "Bagi rata saja" 48dp, "Buat tagihan per orang" 64dp nonaktif sampai semua unit dibagi.

## 6. Kriteria Terima (cek di PR implementasi)

1. Pada emulator 1280x800 dan 2560x1600 dp, lebar rail 88dp dan panel pesanan 420dp (ukur dari `CassyUiShowcaseTest`).
2. Tidak ada field atau dialog lebih lebar dari batasnya (360, 560, 640dp).
3. Semua target sentuh di layar ini 48dp atau lebih; aksi bayar 64dp.
4. Tidak ada teks di bawah 14sp kecuali keterangan sekunder 12sp.
5. Teks baru ada di `values/strings.xml` dan `values-en/strings.xml`.
6. Screenshot CI baru menggantikan `screenshots/ui-v2.4.0/PosLandscape/*` dan disimpan ke `screenshots/ui-v2.6.0/PosLandscape/<StatePascalCase>.png`.
