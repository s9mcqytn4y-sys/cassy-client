# Panduan Pengujian & CI Android (`apps/android`)

Dokumen ini menjadi rujukan resmi tim engineering dan AI agent (termasuk Hermes) untuk menjalankan pengujian Android secara andal, hemat memori di host Windows, dan konsisten dengan GitHub Actions.

## 1. Piramida Pengujian (3 Lapisan)

| Lapisan | Lokasi & Alat | Tujuan | Kapan Dijalankan |
|---|---|---|---|
| **Tier 1: JVM Unit Tests** | `app/src/test/...` (`ktor-client-mock`, `kotlinx-coroutines-test`) | Menguji validasi form, state ViewModel, mapping error domain, routing KDS, dan parser QR tanpa emulator | Setiap edit lokal & wajib hijau pada `.github/workflows/android-ci.yml` (`./gradlew test`) |
| **Tier 2: KVM Instrumentation & UI Showcase** | `app/src/androidTest/...` (`CassyUiShowcaseTest`, `.github/scripts/android-ui-screenshots.sh`) | Merender screen tablet lanskap (`1280x800`), menguji fokus/keyboard/permission dialog/scanner Compose nyata, serta mengambil screenshot PNG | Pada PR/push yang menyentuh `apps/android/**` via `.github/workflows/android-ui-screenshots.yml` (Ubuntu KVM) |
| **Tier 3: Screenshot Diff (Backlog)** | Roborazzi / Compose Preview diffing (tercatat di `docs/TechDebt.md`) | Deteksi regresi visual pixel-per-pixel pada JVM tanpa booting emulator penuh | Tahap lanjutan setelah PR Release One stabil |

## 2. Kendala Host Windows (8 GB RAM)

Mesin pengembang Windows memiliki kapasitas commit memory terbatas:

1. **Selalu pakai batas worker tanpa daemon untuk Gradle:**
   ```powershell
   .\gradlew testDevDebugUnitTest bundleDevDebug --no-daemon --max-workers=2
   ```
   Sebelum PR, jalankan juga `./gradlew test --no-daemon --max-workers=2` karena `android-ci.yml` menjalankan task `test` lengkap.
2. **Jangan menjalankan emulator Android lokal bersamaan dengan Gradle, PostgreSQL/WSL backend, atau Node typecheck berat.** Gunakan runner GitHub Actions berbasis Linux KVM untuk UI instrumentation dan capture screenshot agar hasil deterministik.
3. **Bila harus memverifikasi di AVD lokal:** build APK dulu dengan `--no-daemon`, tutup Gradle/proses berat, jalankan `emulator -avd Medium_Tablet -no-window -no-audio -no-boot-anim`, baru install lewat `adb install -r`.

## 3. Aturan UI Showcase & Screenshot

- **Tema Manifest Test:** `app/src/main/AndroidManifest.xml` menetapkan `@style/Theme.Cassy` (`NoActionBar`) pada `<application>` dan `@style/Theme.Cassy.Splash` khusus pada `MainActivity`, sedangkan `app/src/androidTest/AndroidManifest.xml` memakai `@style/Theme.CassyTest`. Ini menghilangkan bilah ActionBar abu-abu default Android di screenshot Compose test.
- **Struktur Folder Lokal (`apps/android/screenshots/`):** folder ini di-gitignore dan diorganisasi dalam subfolder PascalCase per screen (mirror dari artefak CI `ui-v2.2.0`):
  - `StartupSplash/` (`OsSplash.png`)
  - `StorePairing/` (`DefaultOnline.png`, `ValidationAndOffline.png`, `CameraPermissionModal.png`, `HidScannerDialog.png`, `QrCameraScanner.png`)
  - `ServerConfig/` (`ServerConfigDefault.png`)
  - `PosLandscape/`
  - `CatalogManagement/`
  - `StoreSettings/`
- **Aturan Anti-Kebocoran:** jangan pernah menyertakan `.agents/`, `docs/android-adb/`, `tools/`, atau `screenshots/` ke dalam commit git.
