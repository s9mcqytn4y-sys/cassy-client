# DESIGN.md: Cassy POS Tablet (Android)

Reading this as: layar kasir F&B di tablet landscape 10 sampai 13 inci, dipakai berdiri, cepat, sering dengan tangan basah. Dial ENERGY 2 / RHYTHM 2 / MOTION 1.

## 1. Token (sumber `packages/design-tokens/tokens.json`)

| Peran | Terang | Gelap |
|---|---|---|
| Kanvas | `#F7F5F0` putih tulang | `#0F1513` |
| Permukaan panel | `#FFFFFF` | `#18211E` |
| Permukaan redup | `#EFECE4` | `#222D29` |
| Teks utama | `#17211E` tinta | `#ECEFEA` |
| Teks pendukung | `#56605B` | `#A9B3AD` |
| Aksi utama | `#1F5F52` pandan | `#5FB8A2` |
| Sorotan, fokus | `#E9A23B` kunyit | `#F2B65A` |
| Status ok, warn, err | hijau, kunyit tua, merah bata; selalu dengan label teks | |

Tanpa cokelat, tanpa gradasi, tanpa glassmorphism. Bayangan hanya untuk dialog dan bottom sheet.

## 2. Tipografi

Plus Jakarta Sans. Konten minimal 16sp, label tombol 16sp semibold, harga dan total `tabular-nums`, total tagihan 28sp atau lebih. Rupiah `Rp 28.000`.

## 3. Tata letak layar besar (masalah yang diselesaikan)

- Tiga kolom tetap pada lebar 1280dp: rail navigasi 88dp, katalog fleksibel, panel pesanan 400 sampai 440dp. Panel pesanan tidak melebar mengikuti layar.
- Katalog memakai grid dengan lebar kartu tetap 168 sampai 200dp; kolom bertambah, kartu tidak meregang.
- Form dan dialog dibatasi lebar 560dp (form) dan 640dp (dialog). Field tidak pernah selebar layar.
- Ruang kosong di layar 13 inci dipakai untuk informasi kerja (ringkasan meja, status printer, antrean), bukan dibiarkan kosong.
- Aksi selesai (BAYAR, KIRIM KE DAPUR) di sepertiga bawah kanan, tinggi 64dp. Aksi destruktif (void, hapus) jauh dari aksi utama dan selalu minta konfirmasi.

## 4. Mencegah salah input

- Nominal tunai lewat keypad besar dan tombol cepat uang pas, Rp 50.000, Rp 100.000; kembalian tampil besar sebelum konfirmasi.
- Jumlah item dengan stepper 48dp, bukan field teks.
- Pilihan bagi tagihan lewat penugasan unit yang terlihat; tombol konfirmasi aktif hanya bila semua unit sudah dibagi.
- Status selalu teks, tidak hanya warna. Target sentuh minimal 48dp, jarak antar target 8dp, tidak ada target di 16dp terluar layar.

## 5. Gerak

150ms untuk umpan balik tekan dan perubahan total. Tanpa animasi masuk.
