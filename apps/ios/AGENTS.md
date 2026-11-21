# Cassy iOS / iPadOS POS (Swift 6 + SwiftUI 2026)

> Keputusan Arsitektur: `MyCaffeHardwareBridge` (Objective-C) telah dibatalkan dan digantikan oleh aplikasi native **iOS/iPadOS POS penuh** memakai **Swift 6**, **SwiftUI**, dan **iOS 18+**.

## Aturan Non-Negosiasi iOS
- **Swift 6 Strict Concurrency:** Gunakan `actor` untuk networking/Keychain, `@MainActor @Observable` untuk ViewModel, serta tipe model `Sendable`.
- **Uang Integer (`Int64`):** Klien dilarang menghitung total atau pajak otoritatif; seluruh angka harga diformat dengan `monospacedDigit()` / `RupiahFormatter`.
- **Design Tokens:** Warna wajib mengikuti `CassyTheme` yang disinkronkan dari `../../packages/design-tokens/tokens.json` (`Pandan #1F5F52`, `Kunyit #E9A23B`, `Putih Tulang #F7F5F0`, `Tinta #17211E`).
- **Adaptive Form Factor:** iPadOS landscape menggunakan split-pane (`horizontalSizeClass == .regular`), iPhone portrait menggunakan bottom sheet keranjang dengan target sentuh >= `48pt`.
- **Keamanan:** Token dan device binding hanya disimpan di Apple **Keychain Services** (`KeychainStore`), dilarang di `UserDefaults`.
