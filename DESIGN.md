# DESIGN.md: Cassy Client Design System

Dasar keputusan: ADR-010 di repo backend. Web hanya melayani tamu QR, semua operasional tenant ada di Android.

Reading this as: halaman pesan dari meja untuk tamu kafe di ponsel, memakai brand gerai sebagai identitas, dial ENERGY 2 / RHYTHM 1 / MOTION 1.

## 1. Hierarki merek

- **Gerai (tenant)** tampil paling depan: nama, logo, foto, dan satu warna aksen dari `/public/stores/:slug/cms`.
- **Cassy** hanya teks kecil di footer. Tenant paket white-label bisa menyembunyikannya lewat `hide_powered_by_badge`.
- **AzureDevID** muncul di halaman legal saja.
- Package ID Operasional (`Cassy POS`): `com.azuredevid.cassy`.
- Package ID Eksekutif & Back-Office (`Cassy Manager`): `com.azuredevid.cassy.manager` (100% Native Android & Native iOS/iPadOS, responsif untuk HP, Tablet, & iPad — tanpa web back-office terpisah).
- **Pemisahan SCAMPER Operasional vs Management:** `Cassy POS` memakai kanvas terang hangat Putih Tulang (`#F7F5F0`) anti-silau untuk lantai kasir/dapur, sedangkan `Cassy Manager` memakai dial `cassyManagerExecutive` (header Deep Tinta `#17211E` + Pandan `#1F5F52` + Kunyit `#E9A23B`) untuk KPI, Menu Engineering Matrix BI, HPP Resep, BYOK Midtrans, dan Remote Approval Void/Refund.

## 2. Token warna (PRD v4: Pandan & Kunyit, sumber `packages/design-tokens/tokens.json`)

Token dasar berlaku bila gerai belum mengatur brand (tanpa warna cokelat/Espresso).

| Token | Terang (`cassyLight`) | Gelap (`cassyDark`) | Peran |
|---|---|---|---|
| `--bg-canvas` | `#F7F5F0` | `#0F1513` | Latar halaman putih tulang hangat / gelap tenang |
| `--bg-surface` | `#FFFFFF` | `#18211E` | Kartu menu, panel checkout, dan struk |
| `--bg-muted` | `#EFECE4` | `#222D29` | Pembeda area kategori dan ringkasan |
| `--text-primary` | `#17211E` | `#ECEFEA` | Teks utama (tinta hijau kehitaman) |
| `--text-secondary` | `#56605B` | `#A9B3AD` | Teks pendukung |
| `--brand-primary` | `#1F5F52` | `#5FB8A2` | Aksi utama produk Cassy (Pandan) |
| `--brand-accent` | `#E9A23B` | `#F2B65A` | Sorotan hangat dan indikator fokus (Kunyit) |
| `--guest-primary` | `#17211E` | `#ECEFEA` | Fallback netral tombol bayar tamu bila gerai tanpa warna sendiri |

Aturan warna gerai: nilai harus hex 6 digit. Klien menghitung kontras `--brand-primary` terhadap teks tombol. Bila di bawah 4,5:1, klien memakai token fallback netral (`guestPrimary`). Status (`ok`, `warn`, `err`) tidak bisa ditimpa gerai.

## 3. Tipografi

`Plus Jakarta Sans` untuk antarmuka produk, dimuat lewat `next/font` (`Bricolage Grotesque` untuk wordmark/landing). Harga dan nomor pesanan memakai `tabular-nums`. `JetBrains Mono` untuk hash dokumen legal dan diagnostik perangkat. Rupiah ditulis `Rp 28.000`.

## 4. Navigasi

Tidak ada menu situs, sidebar, atau tab bawah. Tamu datang dari QR dan punya satu tugas.

| Layar | Rute | Fokus |
|---|---|---|
| Instruksi | `/` | Minta tamu memindai QR |
| Menu | `/order/[token]` | Foto produk dan tombol tambah |
| Opsi produk | Bottom sheet | Varian, addon, sub-addon, catatan |
| Keranjang | Bottom sheet | Rincian total dari server |
| Bayar | `/order/[token]` langkah bayar | QRIS dan batas waktu |
| Bagi tagihan | Langkah sebelum bayar | Rata atau per item, tautan per orang |
| Bagian teman | Rute tautan | Nominal dan QRIS bagian itu |
| Status dan struk | `/order/[token]` langkah akhir | Status pesanan, tautan struk |
| Legal | `/legal` | Dokumen |

Pola menu: header gerai pendek, strip kategori lengket, daftar satu kolom dengan foto, dan bar keranjang tetap di bawah setelah ada item. Tombol kembali browser harus menutup bottom sheet.

## 5. Keadaan layar

Setiap kondisi punya kalimat dan tindakan sendiri, dalam bahasa tamu.

| Kondisi | Yang ditampilkan |
|---|---|
| QR tidak dikenal atau meja diarsipkan | Minta tamu memanggil staf |
| Pemesanan QR dijeda gerai | Pesan dari gerai, menu tetap bisa dibaca |
| Sesi habis | Minta memindai ulang, keranjang lokal dipertahankan |
| Produk habis | Tombol tambah nonaktif dengan label |
| Harga berubah | Tampilkan total baru, minta konfirmasi |
| QRIS kedaluwarsa | Tombol buat QR baru |
| Sebagian bagian belum lunas | Daftar status, pesanan belum masuk dapur |
| Koneksi putus | Banner, tombol coba lagi |

## 6. Interaksi dan aksesibilitas

- Target WCAG 2.2 AA. Target sentuh minimal 44x44px, tombol bayar 56px.
- Bottom sheet memakai `<dialog>`, fokus terkunci, dan bisa ditutup dengan Escape.
- Perubahan total dan status diumumkan lewat `aria-live`.
- Label terlihat di semua field, error di bawah field dan terhubung `aria-describedby`.
- Status selalu teks, tidak hanya warna.
- Tidak ada `user-scalable=no`.
- Bahasa `id-ID` utama, `en-US` kedua, seluruh teks lewat kamus.

## 7. Alasan keputusan (R-31)

- **Warna gerai sebagai satu aksen:** tamu harus merasa ini menu kafe tempat ia duduk.
- **Satu kolom dengan foto:** tamu memutuskan dari foto, dibaca dengan satu tangan.
- **Bottom sheet, bukan halaman baru:** posisi menu tidak hilang saat memilih opsi.
- **Satu keluarga huruf:** sudah cukup membedakan lewat ukuran, dan mengurangi unduhan.
- **Bayangan hanya di bottom sheet:** satu-satunya permukaan yang benar-benar melayang.
- **MOTION 1:** transisi 150ms untuk umpan balik, tanpa animasi masuk yang menunda pesanan.
- **Tanpa ikon dekoratif:** teks label sudah cukup, ikon hanya untuk tutup dan jumlah.

## 8. Referensi

Foto, jarak, dan ketegasan tipografi dipelajari dari [Alma](https://www.alma.food), hanya sebagai komposisi. Palet dan layout tidak ditiru. Android mengikuti token yang sama, dengan target sentuh 48px untuk kasir.
