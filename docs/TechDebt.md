# Ledger utang teknis (PRD v3 N11, F2)

Satu baris per utang: apa, kenapa belum dibayar, dan kapan harus dibayar. Hapus baris saat lunas, catat PR-nya di commit. Review keamanan F1 ada di `cassy-backend/docs/Engineering/SecurityReviewF1.md`.

Prioritas: **T** tinggi (bayar sebelum pilot), **S** sedang (bayar dalam rilis berikutnya), **R** rendah (bayar saat menyentuh area itu). Lihat juga [BacklogAndDebt.md](ProjectManagement/BacklogAndDebt.md) untuk temuan audit PWA terbaru.

## Dari PRD v2 F2

| ID | Utang | Prioritas | Pemicu bayar |
|---|---|---|---|
| D1 | Image Playwright di CI (`mcr.microsoft.com/playwright:v1.65.0-noble`) diselaraskan manual dengan `@playwright/test` di lockfile. Saat ini cocok (1.65.0) | R | Langkah CI yang membandingkan keduanya, atau saat menaikkan Playwright |
| D2 | Isi legal dimuat di browser (`LegalView` client component) karena web tidak memanggil `BACKEND_INTERNAL_URL` saat render server | R | Saat SEO halaman legal penting atau saat menambah halaman server lain |
| D3 | Pesan cadangan `apiFetch` ("Tidak bisa terhubung ke server", "Permintaan gagal") hanya bahasa Indonesia. Sebagian besar pemanggil sudah memetakan ke `t.*` | R | Saat ada pemanggil baru yang menampilkan `err.message` |

## Ditemukan selama PRD v3

| ID | Utang | Prioritas | Pemicu bayar |
|---|---|---|---|
| S1 | Token bearer di path (struk, pelacakan, bagi tagihan) tercatat utuh di access log nginx | S | Sebelum log dikirim ke layanan pihak ketiga: samarkan dengan `map` di `log_format` |
| S2 | Repo client belum punya pemindai rahasia di CI (backend sudah Gitleaks) | R | Saat ada konfigurasi atau kunci baru di repo client |
| O2 | Port forwarding localhost Windows ke WSL putus; skrip lokal memakai IP WSL | R | Saat mengganti mesin atau setelah `wsl --shutdown` terjadwal |
| O3 | Override `next>postcss` di `pnpm-workspace.yaml` karena Next memasang postcss 8.4.31 | R | Hapus saat Next memakai postcss yang sudah ditambal |
| P2 | Ikon Cassy PNG dibuat dari SVG vault di repo web, belum disimpan balik ke vault | R | Saat Director merapikan aset vault |
| P3 | Halaman struk menampilkan nama varian tunggal seperti "Reguler" karena struk menyimpan snapshot varian. Disengaja, sama dengan struk kertas | R | Bila tenant meminta; butuh flag `has_variants` di snapshot pesanan |

## Ditemukan pada Cassy POS Android (Release One)

| ID | Utang | Prioritas | Pemicu bayar |
|---|---|---|---|
| A1 | Modul Owner/Manager belum memiliki halaman generator QR pairing perangkat di UI (saat ini POS memindai/tempel kode perangkat dari API `POST /api/v1/store/devices`; `apps/web` khusus Guest QR Table Ordering PWA) | T | Saat membangun layar manajemen perangkat Owner bersama short-lived `pairing_token` (`docs/SecurityQrPairingAdr.md`) |
| A2 | Endpoint backend `pairing_token` single-use TTL 5 menit sudah tersedia (`POST /api/v1/pos/devices/pairing-token`, `POST /api/v1/pos/devices/pair`, `cassy-backend` PR #148); Android belum memanggil `/pair` dan masih memakai `device_credential` statis | S | Saat layar manajemen perangkat Owner (`A1`) dikerjakan |
| A4 | Pengujian regresi visual screenshot Android belum memakai Roborazzi di JVM dan masih bergantung pada emulator KVM CI (`android-ui-screenshots.yml`) | R | Setelah screen Release One selesai dan stabil |
