# Cassy Client Monorepo

Single source of agent instructions for this repo. `CLAUDE.md` and `GEMINI.md` import this file. App-specific rules live in `apps/web/AGENTS.md`, `apps/android/AGENTS.md`, and `apps/ios/AGENTS.md`. Global style, Caveman communication, and Karpathy coding rules are inherited from user global configuration.

## What This Repo Covers

Client applications for **Cassy by AzureDevID** (F&B POS and QR table ordering, Indonesia). `MyCaffe` remains only in legacy technical identifiers until scheduled refactors.

| Path | App | Stack |
|---|---|---|
| `apps/web` | Guest QR ordering PWA | Next.js 15 App Router, React 19, Tailwind CSS v4, Playwright + axe |
| `apps/android` | Cassy POS tablet | Kotlin, Jetpack Compose, Koin, Ktor, DataStore + Tink (`com.azuredevid.cassy`) |
| `apps/ios` | Hardware printer bridge | Objective-C (ARC), no UI |
| `packages/design-tokens` | Master visual tokens | `tokens.json` -> CSS / Kotlin / Compose tokens |

Sibling folders inside `C:\Projects\CassyWorkspace`:
- Backend (`CassyBackend`, repo `s9mcqytn4y-sys/cassy-backend`): Go 1.27, PostgreSQL 18 RLS (owns all pricing, taxes, inventory, and payment state).
- Docker (`Docker`, repo `s9mcqytn4y-sys/cassy-docker`): Standalone Compose, Nginx, Monitoring, and VPS Gateway definitions.
- Business Vault (`C:\Users\Acer\OneDrive\Documents\AzureDevId`): Brand, legal, payment, and recovery docs (readable by authorized workspace config; never print, copy, or commit secret values).

Source of Truth docs: `docs/PrdCassyV4.md`, `docs/ProjectManagement/README.md`, `docs/TechDebt.md`, `apps/android/docs/ReleaseOnePlan.md`.

## Non-Negotiable Client Rules

- **Server is source of truth**: Prices, taxes, service charges, discounts, and payment state come strictly from backend APIs. Never calculate authoritative totals on clients.
- **Money is integer rupiah**: `Long` on Kotlin / integer `number` on TS, zero floats.
- **No hardcoded tenant data**: Store config, IDs, catalog, tax rules, and printer targets come from authenticated bootstrap or server endpoints.
- **Fail closed**: Show a locked or explicit error state without valid session, store binding, or token.
- **Visual tokens (PRD v4)**: Pandan `#1F5F52`, Kunyit `#E9A23B`, Putih Tulang `#F7F5F0`, Tinta `#17211E` (from `packages/design-tokens/tokens.json`). No Espresso/brown tones. Outlets without custom color fall back to neutral ink.
- **Icons & Copy**: Web uses `lucide-react`; Android uses Material Symbols. No emoji in code, UI copy, logs, or commits. UI copy is polite, clear Bahasa Indonesia with English parity (`values-en` / `messages.ts`).
- **Evidence Screenshots**: Android saves local evidence under `apps/android/screenshots/ui-v2.4.0/...`; PWA saves Desktop/Mobile evidence under `apps/web/screenshots/ui-v2.5.0/<PageGroup>/<StatePascalCase>.png` (gitignored locally, inspectable before PR).

## Branches & Verification Gates

Trunk-based (`main`), short-lived branches (`feat/*`, `fix/*`, `docs/*`, `chore/*`) via PR with Conventional Commits and green CI.

| Area | Verification Command |
|---|---|
| Design Tokens | `pnpm run tokens:verify` |
| Web PWA | `pnpm --filter @azuredevid/mycaffe-web typecheck`, `test:unit`, `build`, `test:e2e` |
| Android POS | `apps/android/gradlew.bat -p apps/android test bundleDebug --no-daemon --max-workers=2` |
| iOS Bridge | `pnpm run ios:lint-objc` |

Tooling paths on Windows: `gh`, `adb` at `C:\Users\Acer\scoop\shims\adb.exe`, Android CLI at `C:\Users\Acer\AppData\AndroidCLI\android.exe`.
