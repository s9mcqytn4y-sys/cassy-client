---
name: Cassy Android POS Material 3 Adaptive
colors:
  surface: '#F7F5F0'
  surface-container-lowest: '#FFFFFF'
  surface-container: '#EFECE4'
  on-surface: '#17211E'
  on-surface-variant: '#56605B'
  outline: '#8C877C'
  outline-variant: '#E2DDD2'
  primary: '#1F5F52'
  on-primary: '#FFFFFF'
  secondary: '#E9A23B'
  on-secondary: '#17211E'
  tertiary: '#8A5A12'
  error: '#B3261E'
  error-container: '#FBE7E5'
---

## 1. Visual Atmosphere & Material 3 Adaptive Architecture
- **Role:** Cassy Android POS (`com.azuredevid.cassy`) for F&B cashiers, waitstaff, baristas, and kitchen stations in Indonesia.
- **Material Design 3 Base (`androidx.compose.material3` + `NavigationSuiteScaffold`):**
  - **Mobile Compact (`412x915` Portrait, `< 600dp`):** Bottom `NavigationBar` (`64dp` height), single-column content, sticky Cart Summary Bar above bottom nav opening a `ModalBottomSheet` for Order Summary, and M3 Segmented Tabs for multi-column boards (KDS, Settings).
  - **Tablet Expanded (`1280x800` Landscape, `>= 840dp`):** Fixed `88dp` left `NavigationRail` (`64dp` item height, `24dp` icon, `14sp` label, Printer status indicator at bottom), flexible center workspace (`GridCells.Adaptive(minSize = 168.dp)` capped at `200dp` width), and fixed `420dp` right Order Summary Panel.
  - **Android TV Companion (`1920x1080` Landscape):** 10-foot UI (`TvQueueDisplay`) with 40/60 split (`Dipanggil Sekarang` Kunyit hero vs `Sedang Disiapkan` & `Siap Diambil` grid).
- **Taste Dials:** DESIGN_VARIANCE 4 (Disciplined Operational Precision), MOTION_INTENSITY 2 (`150ms` tactile M3 ripple & spring state transitions, `LocalReducedMotion` compliant), VISUAL_DENSITY 6 (High-clarity F&B Cashier Cockpit).

## 2. Color Calibration (`packages/design-tokens/tokens.json`)
- **Putih Tulang (`#F7F5F0` / Dark `#0F1513`):** Primary `surface` / canvas reducing glare under bright counter lights.
- **Pure Surface (`#FFFFFF` / Dark `#18211E`):** `surfaceContainerLowest` for product cards, order panel, KDS ticket cards, and modal dialogs (`1dp` `#E2DDD2` outlineVariant).
- **Tonal Container (`#EFECE4` / Dark `#222D29`):** `surfaceContainer` for `NavigationRail`, `NavigationBar`, inactive filter chips, and search bars.
- **Pandan Forest Green (`#1F5F52` / Dark `#5FB8A2`):** `primary` for key cashier actions (`Bayar`, `Konfirmasi Tunai`, active nav indicator, `Mulai Masak`), always paired with `onPrimary` `#FFFFFF`.
- **Kunyit Amber (`#E9A23B` / Dark `#F2B65A`):** `secondary` for `QRIS Dinamis` button, focus rings, hurry badges (`7 mnt, segera`), and TV queue call highlight, always paired with `onSecondary` `#17211E`.
- **Status Semantic Tokens (Always with Text Label):**
  - OK: `#E3F2E6` bg / `#1E6B34` fg (`Printer siap`, `Online`, `Lunas`)
  - Warn: `#FDF0D8` bg / `#8A5300` fg (`7 mnt, segera`, `Perlu PIN Owner`)
  - Error: `#FBE7E5` bg / `#B3261E` fg (`12 mnt, terlambat`, `Koneksi terputus`, `Habis`)

## 3. Typography & Localization Rules
- **Font Family:** `Plus Jakarta Sans` (`Regular 400`, `Medium 500`, `SemiBold 600`, `Bold 700`) with mandatory `fontFeatureSettings = "tnum"` (`tabular-nums`) on all Rupiah amounts (`Rp 32.000`), quantities, timers, and cash keypad numbers.
- **Monospace:** `JetBrains Mono` for order codes (`A-041`), table codes (`M-04`), shift codes (`Shift #01`), and LAN IP/Port (`192.168.1.50:9100`).
- **Consistent Indonesian Cashier Copy:** Always use `Rp 32.000` (never `IDR 32.000`), `Kasir`, `Meja`, `Dapur`, `Katalog`, `Pengaturan`, `Buka Laci`, `Kirim ke Dapur`, `Bayar Tunai`, `QRIS Dinamis`, `Bagi Tagihan`, `Tagihan Terbuka`.

## 4. Layout & Ergonomic Safeguards (Fixes from `ui-v2.5.0` Audit)
- **Non-Blocking Top Connection Status Strip:** Network/WebSocket offline warnings (`Koneksi ke server terputus. Memakai cache lokal...`) render as a compact `40dp` top banner above the workspace, NEVER as a bottom floating snackbar covering `Bayar Tunai`, `QRIS Dinamis`, or `Bagi Tagihan`.
- **Touch Targets:** Minimum `48dp x 48dp` for all interactive elements (`8dp` minimum gap); primary payment CTAs (`Bayar Rp ...`, `Konfirmasi Tunai`) are `64dp` tall; cash keypad buttons are `72dp` tall.
- **Dialog Width Caps:** Forms max `560dp`, Cash/Split dialogs max `640dp` (or structured full-screen split workspace for By-Item Split).

## 5. Hard Bans
- Zero emojis anywhere in UI copy, badges, or logs.
- Zero em dashes (`U+2014`).
- Zero client-side authoritative tax/service calculation (all totals come from `POST /api/v1/pos/orders/quote`).
