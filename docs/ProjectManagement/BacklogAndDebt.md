# Backlog and Audit Findings (Cassy Client PWA & Mobile)

Dokumen ini mencatat utang teknis, anomali worktree, serta temuan audit kualitas, keamanan, performa, dan SEO/GEO.

| ID | Sumber | Kategori | Deskripsi & Dampak | Prioritas | Status |
|---|---|---|---|---|---|
| W-1 | PWA `apps/web` | A11y / HTML | `page.tsx` merender `<main>` di dalam `<main id="main-content">` dari `layout.tsx` (nested `<main>` landmark) | Tinggi | Selesai v2.5.0 |
| W-2 | PWA `apps/web` | SEO Metadata | `src/app/r/[code]/page.tsx` memakai `"Struk Digital \| Cassy"` sehingga template layout menggandakannya menjadi `"Struk Digital \| Cassy \| Cassy"` | Sedang | Selesai v2.5.0 |
| W-3 | PWA `apps/web` | Repo Hygiene | `public/brand/KopSurat/` dan `ArchiveDuplicateLogoAzureDevId.jpg` berada di aset publik PWA tanpa dipakai rute Guest Order | Sedang | Selesai (`git rm`) |
| W-4 | Workspaces | Dev Tooling | `apps/web/package.json` `codegen:api` masih mengarah ke `../../../myCaffe-backend/...` yang sudah di-rename menjadi `CassyBackend` | Rendah | Selesai v2.5.0 |
| W-5 | PWA `apps/web` | UI State | Toast `"Ditambahkan ke keranjang."` masih terlihat melayang di atas layar `PaymentView` saat berpindah ke layar pembayaran (`VaBankTransferDesktop.png`); clear `actionToast` saat `viewingPayment = true` | Rendah | Selesai v2.5.0 |
| W-6 | PWA `apps/web` | Layout / Sticky | `[data-cart-bar]` `sticky bottom-0` di `OrderApp.tsx` diuji eksplisit oleh `guest-order.spec.ts` (`above footer`); efek menumpuk hanya muncul pada jahitan screenshot `fullPage: true` | Rendah | Evaluated By-Design |
| W-7 | PWA `apps/web` | Assets / Fallback | Item menu tanpa gambar valid menampilkan ikon fallback `UtensilsCrossed` pada `ProductImage.tsx` saat image error | Sedang | Selesai v2.5.0 |
| W-8 | PWA `apps/web` | Performance | Waterfall fetch pada `useOrderData.ts` diparalelkan (`catalog` + `cms` + `session`/`guest/orders` dalam satu `Promise.all`) | Sedang | Improved v2.5.0 |
| W-9 | Git Worktrees | Env Parity | Sebelum sinkronisasi ke `main`, baseline `3e6ceaa` di `Worktrees/cassy-client-agy` dan `Worktrees/cassy-client-claude` gagal timeout `5000ms` dan 0 retry pada WebKit Windows; disinkronkan via fast-forward ke `main` | Rendah | Selesai (Worktree Synced) |
| W-10 | PWA `apps/web` | SSR / WebKit | `<Suspense fallback=...>` ditambahkan pada `src/app/legal/page.tsx` dan `legal/mitra/page.tsx` | Sedang | Selesai v2.5.0 |
| W-11 | PWA `apps/web` | A11y / Motion | `evidence-and-live.spec.ts` menunggu `CartSheet` settled (button visible + 2x `rAF`) sebelum audit Axe `color-contrast` | Rendah | Selesai v2.5.0 |
