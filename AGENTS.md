# Cassy Client Monorepo

Single source of agent instructions for this repo. `CLAUDE.md` and `GEMINI.md` import this file. App-specific rules live in `apps/web/AGENTS.md`, `apps/android/AGENTS.md`, and `apps/ios/AGENTS.md`. Global style, Caveman communication, and Karpathy coding rules are inherited from user global configuration.

## What This Repo Covers

Client applications for **Cassy by AzureDevID** (F&B POS and QR table ordering, Indonesia). `MyCaffe` remains only in legacy technical identifiers until scheduled refactors.

| Path | App | Stack |
|---|---|---|
| `apps/web` | Guest QR ordering PWA | Next.js 15 App Router, React 19, Tailwind CSS v4, Playwright + axe |
| `apps/android` | Cassy POS tablet | Kotlin, Jetpack Compose, Koin, Ktor, DataStore + Tink, Room for offline cache and queue (`com.azuredevid.cassy`) |
| `apps/ios` | Hardware printer bridge | Objective-C (ARC), no UI |
| `packages/design-tokens` | Master visual tokens | `tokens.json` -> CSS / Kotlin / Compose tokens |

Sibling folders inside `C:\Projects\CassyWorkspace`:
- Backend (`CassyBackend`, repo `s9mcqytn4y-sys/cassy-backend`): Go 1.27, PostgreSQL 18 RLS (owns all pricing, taxes, inventory, and payment state).
- Docker (`Docker`, repo `s9mcqytn4y-sys/cassy-docker`): Standalone Compose, Nginx, Monitoring, and VPS Gateway definitions.
- Business Vault (`C:\Users\Acer\OneDrive\Documents\AzureDevId`): Brand, legal, payment, and recovery docs (readable by authorized workspace config; never print, copy, or commit secret values).

Source of Truth docs: `docs/PrdCassyV4.md`, `docs/ProjectManagement/README.md`, `docs/TechDebt.md`, `apps/android/docs/ReleaseOnePlan.md`, plus workspace UI/UX specs `../Docs/UiUxHandoffSpec.md`, `../Docs/PwaUiUxDissection.md`, `../Docs/BackendUiUxProposals.md`, `apps/web/.stitch/SITE.md` (PWA Stitch: `https://stitch.withgoogle.com/projects/13375593037028476163`, 12 screens 1:1), and `apps/android/.stitch/SITE.md` (Android POS Stitch: `https://stitch.withgoogle.com/projects/13037968050970200135`, 17 screens 1:1). Active live demo domain: `https://pos.abbdullaziz.online` & `https://api.abbdullaziz.online` (`demo.abbdullaziz.online` is retired).

## Visual Language & Taste Design (Frontend Context)

- **Design Language:** *Warm Editorial Hospitality* (calm under lunch-rush pressure, exact with integer rupiah, zero AI slop).
- **Color Roles (`packages/design-tokens/tokens.json`):** Putih Tulang `#F7F5F0` canvas, Pure White `#FFFFFF` surface, Tonal `#EFECE4` containers, Pandan `#1F5F52` primary (`text-brand-fg` `#FFFFFF` ONLY, never dark `text-brand-ink` on `bg-brand`), Kunyit `#E9A23B` accent (`#17211E` ink), Tinta `#17211E` primary ink.
- **Typography & Tabular Numbers:** Web uses `Bricolage Grotesque` (display) + `Plus Jakarta Sans` (body) + `JetBrains Mono` (codes/VA); Android uses `Plus Jakarta Sans` (`fontFeatureSettings = "tnum"`) + `JetBrains Mono`. Always format currency as `Rp 32.000` (never `IDR 32.000`).
- **Adaptive & Ergonomic Rules:**
  - Web PWA (`apps/web`): `min-h-[100dvh]`, zero-scroll `390x844` landing (`py-5 gap-4`, `min-h-[48px]` CTAs), unwrapped H1 (`text-wrap: balance`), `dialog:modal { overscroll-behavior: contain; }`, `48x48` menu thumbnails in `CartSheet`, anti-spam email cooldown `60s`, **no SHA-256 hash box** on `/legal` & `/legal/mitra`, Apple-style spring curves (`--ease-spring: cubic-bezier(0.22, 1, 0.36, 1)`) with `prefers-reduced-motion`.
  - Android POS (`apps/android`): Material Design 3 Adaptive `NavigationSuiteScaffold` (`NavigationBar` + `ModalBottomSheet` on Mobile `< 600dp` <-> `88dp NavigationRail` + `420dp Order Panel` on Tablet `>= 840dp`), non-blocking `40dp` Top Connection Status Strip (never covering payment CTAs), touch targets `>= 48dp` (`64dp` pay, `72dp` cash keypad), Bahasa Indonesia kasir copy consistent.
- **Transparent Web-Standard UI Assets:** Error and empty state SVG illustrations with transparent backgrounds and `prefers-reduced-motion` live in `apps/web/public/brand/states/` (`state-404.svg`, `state-503.svg`, `state-offline.svg`, `state-expired.svg`, `state-refund.svg`, `state-empty-cart.svg`).

## Non-Negotiable Client Rules

- **Server is source of truth**: Prices, taxes, service charges, discounts, and payment state come strictly from backend APIs. Never calculate authoritative totals on clients.
- **Money is integer rupiah**: `Long` on Kotlin / integer `number` on TS, zero floats.
- **No hardcoded tenant data**: Store config, IDs, catalog, tax rules, and printer targets come from authenticated bootstrap or server endpoints.
- **Fail closed**: Show a locked or explicit error state without valid session, store binding, or token.
- **Visual tokens (PRD v4)**: Pandan `#1F5F52`, Kunyit `#E9A23B`, Putih Tulang `#F7F5F0`, Tinta `#17211E` (from `packages/design-tokens/tokens.json`). No Espresso/brown tones. Outlets without custom color fall back to neutral ink.
- **Icons & Copy**: Web uses `lucide-react`; Android uses Material Symbols. No emoji in code, UI copy, logs, or commits. UI copy is polite, clear Bahasa Indonesia with English parity (`values-en` / `messages.ts`).
- **Evidence Screenshots**: Android saves local evidence under `apps/android/screenshots/ui-v2.4.0/...`; PWA saves Desktop/Mobile evidence under `apps/web/screenshots/ui-v2.5.0/<PageGroup>/<StatePascalCase>.png` (gitignored locally, inspectable before PR).
- **Pre-Push Secret Hygiene (`gitleaks`)**: Never commit high-entropy mock tokens (e.g. `tk-xxx-yyyy`) in Stitch HTML files (`apps/web/.stitch/designs/*.html`), fixtures, or docs. Use semantic placeholders such as `demo-order-14`.

## Branches & Verification Gates

Trunk-based (`main`), short-lived branches (`feat/*`, `fix/*`, `docs/*`, `chore/*`) via PR with Conventional Commits and green CI.

| Area | Verification Command |
|---|---|
| Design Tokens | `pnpm run tokens:verify` |
| Web PWA | `pnpm --filter @azuredevid/cassy-web typecheck`, `test:unit`, `build`, `test:e2e` |
| Android POS | `apps/android/gradlew.bat -p apps/android testDevDebugUnitTest bundleDevDebug --no-daemon --max-workers=2` |
| iOS Bridge | `pnpm run ios:lint-objc` |

## Coding Standards & Engineering Discipline

- Enforce `/antislop`, `/antislop-code`, `/antislop-ui`, `/antislop-copywriting`, `/clean-code`, zero dead code, and core principles (`SOLID`, `DRY`, `KISS`, `YAGNI`, `WET`/`AHA`, `SoC`, `LoD`, `Composition`, `POLA`, `Fail Fast`, `Boy Scout Rule`).
- **TypeScript / React (`apps/web`)**: Document exported hooks, API mappers, and financial/cart helpers with concise `TSDoc` (`/** ... */`, explaining invariants, not repeating TS types). Keep UI strictly Unidirectional Data Flow (UDF) with server-recomputed pricing.
- **Kotlin / Android (`apps/android`)**: Document public ViewModels, repositories, and Tink/Ktor security boundaries with `KDoc` (`/** ... */`). Enforce structured concurrency (`viewModelScope` / injected dispatchers, zero `GlobalScope`), read-only `StateFlow`, `Channel` one-shot events, lambda event hoisting (UDF), and R8 compatibility.
- **Scripts & Markdown**: `.ps1` uses `Verb-Noun` `PascalCase` (`4` spaces, UTF-8); `.sh` uses `PascalCase` (`2` spaces, `LF`, `set -eu`); `.md` uses `PascalCase` (`LF`).

Tooling paths on Windows: `gh`, `adb` at `C:\Users\Acer\scoop\shims\adb.exe`, Android CLI at `C:\Users\Acer\AppData\AndroidCLI\android.exe`.
