---
name: Cassy Warm Editorial Hospitality
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
- **Taste Dials:** DESIGN_VARIANCE 6 (Editorial Asymmetric Header + Clean 2-Column Catalog Grid), MOTION_INTENSITY 3 (Restrained Tactile Spring Feedback, prefers-reduced-motion compliant), VISUAL_DENSITY 5 (Balanced Mobile Hospitality).
- **Viewport Coverage:** Mobile Portrait (`390x844`), Mobile Landscape (`844x390`), Tablet Portrait (`800x1280`), Tablet Landscape (`1280x800`). Always use `min-h-[100dvh]`.

## 2. Color Calibration (`packages/design-tokens/tokens.json`)
- **Putih Tulang (`#F7F5F0` / Dark `#0F1513`):** Warm paper canvas that reduces glare under restaurant lighting.
- **Pure Surface (`#FFFFFF` / Dark `#18211E`):** Menu cards, modal sheets, and digital receipt container with 1px subtle border `#E2DDD2`.
- **Pandan Forest Green (`#1F5F52` / Dark `#5FB8A2`):** Primary CTA, active category pill, floating cart bar, and verified price highlights (WCAG 2.2 AA `>= 7.4:1`).
- **Kunyit Amber (`#E9A23B` / Dark `#F2B65A`):** Single deliberate accent for active focus rings (`#B8771A`), popular badges, and live queue call highlights.
- **Tinta Charcoal (`#17211E` / Dark `#ECEFEA`):** Primary text and neutral fallback guest action (`15.14:1` contrast). Never use pure black `#000000` or AI purple/blue neon gradients.

## 3. Typographic Architecture
- **Brand Display:** `Bricolage Grotesque` (600, 700, 800) for store titles, hero headers, and queue call numbers.
- **UI & Receipt Body:** `Plus Jakarta Sans` (400, 500, 600, 700) with mandatory `font-variant-numeric: tabular-nums` on every Rupiah amount (`Rp 28.000`), queue code (`A-014`), and countdown timer (`14:52`).
- **Technical / VA / Order Code:** `JetBrains Mono` (500, 700) for Virtual Account numbers, QRIS/order codes, and HTTP status codes.

## 4. Component Behaviors & User Stories
- **StoreHeader:** Leads 100% with the tenant store identity (`Cassy Roastery Senopati`), table pill (`Meja A1 Indoor`), announcement banner, `Panggil Pelayan` trigger, and `Pesanan (N)` active session drawer button.
- **CategoryNav:** Sticky top horizontal pill bar with `scroll-mt-20` section anchors.
- **Menu Cards & ProductOptionsSheet:** 2-column mobile / 3-column tablet grid with high-res 4:3 WebP food photography, 44px+ quick-add `+` button for simple items, and bottom sheet dialog for variants/modifiers (`Wajib` vs `Opsional`) plus smart pairing add-ons.
- **FloatingCartBar & CartSheet:** Sticky thumb-zone bar showing item count and server-quoted total (`POST /api/v1/public/stores/{slug}/orders/quote`). Never compute authoritative tax/service on the client.
- **PaymentView & SplitView:** Direct QRIS (`Midtrans BYOK`, 0% platform commission), VA Bank Transfer (`PaymentSwitcherSheet`), live WebSocket `/ws` status transition, and equal split bill (`/bagian/[token]`).
- **ReceiptView & QueueDisplay:** Verified digital receipt (`/receipts/[token]` & `/r/[code]`) with PDF download, WhatsApp send, and subtle organic B2B acquisition footer (`Ditenagai Cassy oleh AzureDevID` -> `/legal/mitra`).

## 5. Anti-Patterns (Hard Bans)
- Zero emojis anywhere in UI copy, badges, or placeholders.
- Zero em dashes (`U+2014`) in Indonesian or English copy.
- Zero client-side authoritative price calculation or fake social proof counters.
- Zero dead buttons: every interactive control opens a real sheet, triggers a server action, or navigates to a verified route.
