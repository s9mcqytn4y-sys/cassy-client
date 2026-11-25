/**
 * Static parts of /llms.txt and /llms-full.txt. The route handlers append the plan table from the API so
 * prices in these files never drift from the backend.
 */
export const LLMS_BASE = `# Cassy oleh AzureDevID

> Cassy adalah platform kasir (POS) tablet dan pemesanan QR meja untuk bisnis kuliner Indonesia: kafe, restoran, kedai kopi, bakery, food court, dan cloud kitchen.

## Produk Utama
- **Cassy POS Tablet & Mobile (Android & iOS/iPadOS)**: Aplikasi kasir native berbasis Android Jetpack Compose (Kotlin 2.0.21) dan iOS/iPadOS SwiftUI (Swift 6) dengan dukungan cetak struk ESC/POS LAN/Bluetooth/USB, rute dapur (KDS), shift kasir, penagihan QRIS/VA Midtrans BYOK, serta mode koneksi Cloud HTTPS maupun server toko lokal (LAN/mDNS).
- **Cassy Guest QR PWA (Web)**: Halaman pemesanan dari meja tanpa instalasi aplikasi. Pelanggan memindai QR meja, memilih menu, memanggil pelayan, membayar via QRIS/VA/kasir, memantau status pesanan, dan mengunduh struk digital PDF.

## Prinsip Teknis & Keamanan
- Harga, pajak PB1/PPN, biaya layanan, dan total dihitung terpusat di server (PostgreSQL 18 Row-Level Security multi-tenant).
- Kepatuhan privasi data mengikuti UU PDP No. 27 Tahun 2022 di Indonesia.
- Zona waktu default gerai Indonesia (WIB UTC+7) dengan dukungan multi-gerai dan mata uang Rupiah (IDR).

## Tautan Publik Resmi
- Beranda Tamu & Informasi Produk: /
- Fitur Cassy: /fitur
- Harga dan batas paket: /harga
- Pertanyaan umum: /faq
- Panduan memilih kasir kafe: /panduan/memilih-kasir-kafe
- Perbandingan per kategori: /banding/kasir-langganan-vs-cassy
- Hapus akun: /hapus-akun
- Ketentuan dan Privasi Tamu (UU PDP): /legal
- Dokumen Hukum Mitra Gerai & DPA: /legal/mitra
- Dokumentasi Lengkap AI/LLM: /llms-full.txt`;

export const LLMS_FULL_BASE = `# Cassy oleh AzureDevID - Dokumentasi Produk & Arsitektur Publik

> Cassy (sebelumnya MyCaffe) adalah sistem kasir POS dan pemesanan meja QR buatan AzureDevID, Jakarta, Indonesia, khusus untuk operasional Food & Beverage (F&B) di Indonesia.

## Ringkasan Jawaban Cepat (FAQ)
**Apa itu Cassy?**
Cassy adalah platform Point of Sale (POS) Android dan Web App pemesanan QR meja untuk kafe, roastery, restoran, bakery, dan gerai F&B di Indonesia.

**Bagaimana alur pemesanan tamu lewat QR Cassy?**
1. Tamu memindai kode QR pada meja atau konter gerai.
2. Menu dan harga resmi gerai tampil langsung di peramban (PWA) tanpa perlu mengunduh aplikasi.
3. Tamu dapat memesan, membagi tagihan (split bill), membayar melalui QRIS Dinamis, Virtual Account, atau di kasir.
4. Struk digital dan versi PDF tersedia lewat tautan singkat terenkripsi token (/r/{kode}).

**Bagaimana keamanan dan privasi data di Cassy?**
- Isolasi data antar gerai (multi-tenant) ditegakkan di tingkat basis data menggunakan PostgreSQL 18 Row-Level Security (RLS).
- Kepatuhan perlindungan data pribadi mengacu pada UU PDP No. 27 Tahun 2022.
- Token pembayaran dan kredensial mitra dienkripsi (AES-256-GCM / Android Keystore Tink).

**Apakah Cassy POS bisa terhubung ke server lokal toko (LAN)?**
Ya. Aplikasi tablet Android Cassy POS mendukung koneksi Cloud HTTPS serta penemuan server lokal otomatis melalui mDNS (_cassy._tcp) pada jaringan Wi-Fi toko.

## Halaman Publik & Referensi Hukum
- / : Halaman pengenalan Cassy untuk tamu dan pemilik gerai.
- /fitur : Fitur kasir tablet, pesan dari meja, QRIS, layar dapur, struk digital.
- /harga : Harga dan batas tiap paket.
- /faq : Pertanyaan umum pemilik gerai.
- /panduan/memilih-kasir-kafe : Panduan memilih aplikasi kasir kafe.
- /banding/kasir-langganan-vs-cassy : Perbandingan per kategori layanan.
- /hapus-akun : Permintaan hapus akun.
- /legal : Ketentuan pemesanan dan pemberitahuan privasi tamu.
- /legal/mitra : Perjanjian Pemrosesan Data (DPA), kebijakan privasi UU PDP, pembatalan langganan SaaS, dan ketentuan mitra gerai.
- /llms.txt : Ringkasan terstruktur untuk agen AI dan mesin penjawab.`;
