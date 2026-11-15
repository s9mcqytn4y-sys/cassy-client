# Backlog and Audit Findings (Cassy Client PWA & Mobile)

Dokumen ini mencatat utang teknis, anomali worktree, serta temuan audit kualitas, keamanan, performa, dan SEO/GEO.

| ID | Sumber | Kategori | Deskripsi & Dampak | Prioritas | Status |
|---|---|---|---|---|---|
| W-1 | PWA `apps/web` | A11y / HTML | `page.tsx` merender `<main>` di dalam `<main id="main-content">` dari `layout.tsx` (nested `<main>` landmark) | Tinggi | Selesai v2.5.0 |
| W-2 | PWA `apps/web` | SEO Metadata | `src/app/r/[code]/page.tsx` memakai `"Struk Digital \| Cassy"` sehingga template layout menggandakannya menjadi `"Struk Digital \| Cassy \| Cassy"` | Sedang | Selesai v2.5.0 |
| W-3 | PWA `apps/web` | Repo Hygiene | `public/brand/KopSurat/` dan `ArchiveDuplicateLogoAzureDevId.jpg` berada di aset publik PWA tanpa dipakai rute Guest Order | Sedang | Selesai (`git rm`) |
| W-4 | Workspaces | Dev Tooling | `apps/web/package.json` `codegen:api` masih mengarah ke `../../../myCaffe-backend/...` yang sudah di-rename menjadi `CassyBackend` | Rendah | Selesai v2.5.0 |
| W-5 | PWA `apps/web` | UI State | Toast `"Ditambahkan ke keranjang."` masih terlihat melayang di atas layar `PaymentView` saat berpindah ke layar pembayaran (`VaBankTransferDesktop.png`); perlu clear toast saat `viewingPayment = true` | Rendah | Open (Next Sprint) |
| W-6 | PWA `apps/web` | Layout / Sticky | `[data-cart-bar]` `sticky bottom-0` di `OrderApp.tsx` menumpuk di tengah grid menu panjang saat full-page capture (`SenopatiMenuDesktop.png`); pertimbangkan dock `fixed bottom-0` atau padding-bottom konsisten | Rendah | Open (Next Sprint) |
| W-7 | PWA `apps/web` | Assets / Fallback | Item `"All-Day Brunch & Savory Kitchen"` dan `"Lainnya"` pada live DB menampilkan kotak gambar kosong / ikon patah ketika aset gambar belum tersedia di host | Sedang | Open (Next Sprint) |
| W-8 | PWA `apps/web` | Performance | Waterfall fetch sekuensial (`qr/:token` -> `catalog` + `cms` -> `session` -> 250ms debounce `orders/quote`) lambat pada WebKit konkurensi tinggi (`latency_ms` `/catalog` 13.8s & `/quote` 5.0s pada log dev); optimalkan prefetch/server proxy keepalive | Sedang | Open (Next Sprint) |
| W-9 | Git Worktrees | Env Parity | Sebelum sinkronisasi ke `main`, baseline `3e6ceaa` di `Worktrees/cassy-client-agy` (4 workers: `73 passed, 34 failed, 1 skipped`) dan `Worktrees/cassy-client-claude` (`--workers=1`: `97 passed, 10 failed, 1 skipped`) gagal akibat timeout `5000ms` dan 0 retry pada WebKit Windows; seluruh perbaikan `playwright.config.ts` ada di `main` | Rendah | Sync setelah Push |
| W-10 | PWA `apps/web` | SSR / WebKit | `<Suspense>` pada `src/app/legal/page.tsx` dan `legal/mitra/page.tsx` belum memiliki `fallback`, serta `next start` keep-alive 5s memicu timeout intermiten di WebKit Windows | Sedang | Open (Next Sprint) |
| W-11 | PWA `apps/web` | A11y / Motion | Axe `color-contrast` sesekali gagal sementara (`1 flaky`) pada tombol `.btn-primary` di `CartSheet` WebKit saat animasi transisi buka sheet masih berjalan | Rendah | Open (Next Sprint) |
