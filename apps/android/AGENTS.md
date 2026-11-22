# Cassy POS tablet (`apps/android`)

Read the root `AGENTS.md` first, then `docs/ReleaseOnePlan.md` and `docs/TestingAndCiGuide.md` before building or testing a screen. Scope and PR order live in `ReleaseOnePlan.md`.

## Architecture (PRD v4 Q11)
One module, strict layers per feature: `ui` (Compose + ViewModel) -> `domain` (models, repository interfaces) -> `data` (Ktor implementations). Koin for DI, one shared Ktor client, Preferences DataStore (`DataStoreServerSettingsRepository`) for settings, and Tink for secrets. No Paging 3 and no Retrofit in Release One.

## Rules
- Totals come from `POST /api/v1/pos/orders/quote` (`OrderQuoteRepository`). Never compute tax or service on the device.
- Theme colours must match `packages/design-tokens/tokens.json`; no Espresso & Crema values.
- Material 3 Skill (`hamen/material-3-skill`, installed under `.agents/skills/material-3`): follow MD3 Compose-first component anatomy, tonal surface elevation (not heavy shadows), adaptive window size classes, and `8dp` spacing scale while keeping brand colors locked to `CassyTheme` (`packages/design-tokens/tokens.json`).
- Icons: Material Symbols via `androidx.compose.material.icons`, each with a `contentDescription` or `null` when decorative.
- All UI text in `res/values/strings.xml` and `res/values-en/strings.xml`. Keep cashier copy free of technical jargon (`JSON`, `payload`, `HID`).
- Touch targets 48 dp or more. Rupiah formatted as integers.
- Printing: raw TCP port 9100 ESC/POS with a 2,500 ms timeout (`PrinterPort9100Manager`), kitchen routing in `KdsTicketRouter`.
- Security: fail closed without valid session/store binding; follow `docs/SecurityQrPairingAdr.md` for pairing revocation and cleartext warnings.

## Commands
```bash
./gradlew testDevDebugUnitTest bundleDevDebug --no-daemon --max-workers=2
./gradlew test --no-daemon --max-workers=2
```

## Testing & Screenshot Policy
- Local 8 GB RAM host: run Gradle with `--no-daemon --max-workers=2`; offload Compose UI instrumentation and screenshot captures (`CassyUiShowcaseTest`) to GitHub Actions KVM (`android-ui-screenshots.yml`, detail di `docs/TestingAndCiGuide.md`).
- Never stage or commit `screenshots/`, `.agents/`, `docs/android-adb/`, or `tools/`.

## Headless Emulator Test Workflow
1. Build APK lebih dulu tanpa Gradle daemon agar commit memory Windows tetap bebas:
   `./gradlew assembleDevDebug --no-daemon`
2. Hentikan AVD lain jika ada (`adb emu kill`), lalu start tablet AVD headless (AVD tablet API 36 mengalokasikan 4096 MB RAM):
   `emulator -avd Medium_Tablet -no-window -no-audio -no-boot-anim`
3. Tunggu boot selesai: `adb wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 1; done'`.
4. Pasang APK langsung via `adb` (jangan jalankan `./gradlew` saat emulator menyala agar tidak kehabisan commit memory):
   `adb -s <serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk`
   lalu jalankan: `adb -s <serial> shell am start -W -n com.azuredevid.cassy.dev/com.azuredevid.cassy.MainActivity`.
5. Inspeksi UI & verifikasi: `android layout --device <serial> --pretty` dan `android screen capture --device <serial> --output <path>.png` (atau `adb -s <serial> exec-out screencap -p > <path>.png` jika headless GPU menghasilkan frame hitam).

