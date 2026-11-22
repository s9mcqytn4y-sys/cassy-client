---
name: Cassy Warm Editorial Hospitality (v4)
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

## 1. Visual Atmosphere & Taste Calibration
- **Brand Archetype:** The Dependable Sous-Chef & Ledger Keeper (calm under lunch-rush pressure, exact with integer rupiah numbers, warm and appetizing for guests).
- **Taste Dials:** DESIGN_VARIANCE 6 (Editorial Asymmetric Header + Clean 2-Column Catalog Grid), MOTION_INTENSITY 4 (Apple HIG Spring Tactile Feedback, `prefers-reduced-motion` compliant), VISUAL_DENSITY 5 (Balanced Mobile Hospitality).
- **Viewport Coverage:** Mobile Portrait (`390x844`), Mobile Landscape (`844x390`), Tablet Portrait (`800x1280`), Tablet Landscape (`1280x800`). Always use `min-h-[100dvh]`.

## 2. Color Calibration (`packages/design-tokens/tokens.json`)
- **Putih Tulang (`#F7F5F0` / Dark `#0F1513`):** Warm paper canvas that reduces glare under restaurant lighting.
- **Pure Surface (`#FFFFFF` / Dark `#18211E`):** Menu cards, modal sheets, email receipt card, and digital receipt container with 1px subtle border `#E2DDD2`.
- **Pandan Forest Green (`#1F5F52` / Dark `#5FB8A2`):** Primary CTA, active category pill, floating cart bar, and `Favorit` badge background **ALWAYS paired with `text-brand-fg` (`#FFFFFF` / `#F8F7F2`)** (WCAG 2.2 AA `>= 7.4:1`). NEVER use dark `text-brand-ink` on `bg-brand`.
- **Kunyit Amber (`#E9A23B` / Dark `#F2B65A`):** Deliberate accent for focus rings (`#B8771A`), promo badges (`#17211E` ink), and live queue call highlights.
- **Tinta Charcoal (`#17211E` / Dark `#ECEFEA`):** Primary text and neutral fallback guest action (`15.14:1` contrast). Never use pure black `#000000` or AI purple/blue neon gradients.

## 3. Typographic Architecture
- **Brand Display:** `Bricolage Grotesque` (600, 700, 800) for store titles, hero headers, and queue call numbers.
- **UI & Receipt Body:** `Plus Jakarta Sans` (400, 500, 600, 700) with mandatory `font-variant-numeric: tabular-nums` on every Rupiah amount (`Rp 28.000`), queue code (`A-014`), and countdown timer (`14:52`).
- **Technical / VA / Order Code:** `JetBrains Mono` (500, 700) for Virtual Account numbers, QRIS/order codes, and HTTP status codes.

## 4. Apple HIG Motion & Micro-Interaction Standard
- **Spring Curves:** `--ease-spring: cubic-bezier(0.22, 1, 0.36, 1)` for modals/sheets, `--ease-tactile: cubic-bezier(0.32, 0.72, 0, 1)` for press feedback (`active:scale-[0.97]`).
- **Toast & Bell Chime:** Top-floating glassmorphic pill (`backdrop-blur-md`, semantic icon, dismiss button) synchronized with Web Audio bell chime when order status transitions to `ready` or `served`.
- **Reduced Motion:** Every keyframe (`animate-sheet-up`, `animate-toast-in`, `animate-bell-ring`, `animate-state-float`) MUST be disabled under `@media (prefers-reduced-motion: reduce)`.

## 5. Component Behaviors & User Stories (v4)
- **Public Landing (`/`):** Zero-scroll `390x844` fit (`py-5 gap-4`), `min-h-[48px]` CTA buttons (`Untuk Tamu: Privasi`, `Untuk Mitra Gerai`), trust badges (`Harga Dihitung Server`, `Patuh UU PDP No. 27/2022`).
- **StoreHeader & CategoryNav:** Unwrapped H1 (`text-wrap: balance`), table pill on top utility row, sticky horizontal capsule categories with `scroll-padding-inline: 1.25rem` and subtle right-edge fade mask.
- **Menu Cards & ProductOptionsSheet:** High-contrast `Favorit` badge (`bg-brand text-brand-fg`), 4:3/1:1 WebP photography, 48px touch target `+` button.
- **CartSheet:** `48x48` (`rounded-xl`) menu thumbnails on every cart item and *Cocok dipesan bersama* pairing item; segmented split bill control (`Bayar Penuh | Bagi Rata | Per Item`); non-clipped sticky summary above `Bayar`.
- **PaymentView, SplitView & SharePayView:** Direct QRIS (`Midtrans BYOK`), `Unduh QR` (PNG/SVG) button, multi-payment switcher (`QRIS | Virtual Account | Bayar di Kasir`) when enabled by store feature flag, **Batalkan Pesanan** (unpaid only, confirmation modal with reason chips), and **Expired / Refunded** journey with Midtrans/Kasir SLA timeline.
- **PaymentPaidSection:** Clean mobile 4-step vertical/compact progress indicator, aligned `Estimasi` + `Bunyi Bell` bar, anti-spam email receipt input (typo domain hint `.con` -> `.com`, 60s resend cooldown, sent badge `Terkirim ke ...`, max 3x indicator).
- **Email Receipt v2:** Light Editorial Card (`#F7F5F0` outer + `#FFFFFF` card + `#1F5F52` CTA `Lihat Struk & Unduh PDF`), NO raw `store_slug` (`central-cafe`), clean footer & deliverability metadata.
- **Error/State Illustrations:** Transparent-background editorial SVG/WebP assets in `public/brand/states/` (`404`, `503`, `Offline`, `Expired`, `Refunded`).
- **Legal (`/legal`, `/legal/mitra`):** Clean metadata footer (Version, Effective Date, `legal@azuredevid.com`, switcher). ZERO SHA-256 hash box.

## 6. Anti-Patterns (Hard Bans)
- Zero emojis anywhere in UI copy, badges, emails, or placeholders.
- Zero em dashes (`U+2014`) in Indonesian or English copy.
- Zero client-side authoritative price calculation or fake social proof counters.
- Zero dead buttons: every interactive control opens a real sheet, triggers a server action, or navigates to a verified route.
