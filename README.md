# Cassy Client Monorepo: Multi-Platform Hospitality Suite

[![Android CI](https://github.com/s9mcqytn4y-sys/cassy-client/actions/workflows/android-ci.yml/badge.svg)](https://github.com/s9mcqytn4y-sys/cassy-client/actions)
[![Web CI](https://github.com/s9mcqytn4y-sys/cassy-client/actions/workflows/web-ci.yml/badge.svg)](https://github.com/s9mcqytn4y-sys/cassy-client/actions)

Aplikasi klien resmi untuk **Cassy by AzureDevID** (usaha perorangan AzureDevID, Jakarta Pusat, DKI Jakarta).

```text
                      +-----------------------------+
                      |  Cassy Backend (Go 1.27/PG18)|
                      +--------------+--------------+
                                     |
              +----------------------+----------------------+
              |                      |                      |
              v                      v                      v
     +-----------------+    +-----------------+    +-----------------+
     | Cassy POS Tablet|    | Guest Order PWA |    | iOS Hardware    |
     | (apps/android)  |    | (apps/web)      |    | Bridge (apps/ios|
     +-----------------+    +-----------------+    +-----------------+
              |                      |
              v                      v
     +-----------------+    +-----------------+
     | ESC/POS Printers|    | WebSocket Real- |
     | (Port 9100 KDS) |    | time Sync Hub   |
     +-----------------+    +-----------------+
```

## Applications & Structure

| Package / App | Technology | Role | Status |
|---|---|---|---|
| `apps/android` | Kotlin, Jetpack Compose, Koin, Ktor, CameraX, ZXing | Cassy POS tablet (`com.azuredevid.cassy`) dengan KDS routing, ESC/POS TCP 9100, mDNS LAN discovery, offline cash sync | Production Ready (Release One) |
| `apps/web` | Next.js 15 App Router, React 19, Tailwind CSS v4, Playwright | Guest QR table ordering PWA, checkout Midtrans Core, live tracker, struk digital PDF, halaman legal UU PDP | Active (`v2.5.0`) |
| `apps/ios` | Objective-C (ARC), Foundation | Driver/bridge printer ESC/POS dan serial untuk iOS | Active |
| `packages/design-tokens` | JSON & generator CSS/Compose | Token visual Pandan & Kunyit tervalidasi rasio kontras WCAG 2.2 AA | Active |

## Core Capabilities

### 1. Cassy POS Tablet (`apps/android`)
- **Visual Ergonomics**: Tata letak lanskap 10-55-35 (Navigation Rail, Catalog Grid, Order Summary Panel).
- **KDS Kitchen Ticket Routing**: Rute cetak per stasiun dengan failover dan peringatan status printer.
- **Hardware Abstraction**: Driver ESC/POS TCP port 9100 (`DLE EOT 4` status query, RJ11 drawer kick, timeout 2.500 ms).
- **Dynamic QRIS & Bootstrap Pairing**: ZXing QRIS generator, quote harga resmi server, pairing via QR/PIN, dan discovery mDNS (`_cassy._tcp`).
- **Accessibility & i18n**: Bahasa Indonesia (`values`) dan English (`values-en`), minimal touch target 48 dp.

### 2. Guest Table Order PWA (`apps/web`)
- **Next.js 15 App Router**: Pemuatan cepat tanpa instalasi aplikasi bagi tamu restoran/kafe.
- **Real-Time Sync**: Hooks WebSocket dengan exponential backoff dan fallback polling.
- **AI Search & GEO Ready**: Metadata JSON-LD (`Organization`, `SoftwareApplication`, `FAQPage`), `sitemap.xml`, `robots.txt`, `/llms.txt`, dan `/llms-full.txt`.

## Development Setup

### Prerequisites
- Node.js >= 22.0.0 and `pnpm` 12.18.0
- Java JDK 17 (Eclipse Temurin or OpenJDK)
- Android SDK 34/35/36 with Android Build Tools
- Go 1.27 (untuk menjalankan `CassyBackend` saat E2E live)

### Commands

```powershell
# Install workspace dependencies
pnpm install

# Verify design tokens & WCAG contrast
pnpm run tokens:verify

# Web PWA: typecheck, unit test, build, E2E
pnpm --filter @azuredevid/cassy-web typecheck
pnpm --filter @azuredevid/cassy-web test:unit
pnpm --filter @azuredevid/cassy-web build
pnpm --filter @azuredevid/cassy-web test:e2e

# Android POS: unit tests & debug bundle
apps/android/gradlew.bat -p apps/android test bundleDebug --no-daemon --max-workers=2
```

## Project Management & Docs

- Indeks Project Management: [docs/ProjectManagement/README.md](docs/ProjectManagement/README.md)
- PRD Aktif: [docs/PrdCassyV4.md](docs/PrdCassyV4.md)
- Utang Teknis & Backlog: [docs/TechDebt.md](docs/TechDebt.md), [docs/ProjectManagement/BacklogAndDebt.md](docs/ProjectManagement/BacklogAndDebt.md)
- Repositori Terkait: [`cassy-backend`](https://github.com/s9mcqytn4y-sys/cassy-backend), [`cassy-docker`](https://github.com/s9mcqytn4y-sys/cassy-docker)
