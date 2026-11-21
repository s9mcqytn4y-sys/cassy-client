import SwiftUI

/// Single source of truth for Cassy iOS/iPadOS design tokens, synced with
/// `packages/design-tokens/tokens.json` (Pandan, Kunyit, Putih Tulang, Tinta).
public enum CassyTheme {
    public static let pandan = Color(hex: 0x1F5F52)
    public static let kunyit = Color(hex: 0xE9A23B)
    public static let tinta = Color(hex: 0x17211E)
    public static let putihTulang = Color(hex: 0xF7F5F0)
    public static let surface = Color.white
    public static let muted = Color(hex: 0xEFECE4)
    public static let textSecondary = Color(hex: 0x56605B)
    public static let borderSubtle = Color(hex: 0xE2DDD2)

    public static let okBg = Color(hex: 0xE3F2E6)
    public static let okFg = Color(hex: 0x1E6B34)
    public static let warnBg = Color(hex: 0xFDF0D8)
    public static let warnFg = Color(hex: 0x8A5300)
    public static let errBg = Color(hex: 0xFBE7E5)
    public static let errFg = Color(hex: 0xB3261E)

    public static let minTouchTarget: CGFloat = 48
    public static let cardCornerRadius: CGFloat = 12
    public static let buttonCornerRadius: CGFloat = 10
}

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}

public enum RupiahFormatter {
    public static func format(_ amountIdr: Int64) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = "."
        formatter.locale = Locale(identifier: "id_ID")
        let formatted = formatter.string(from: NSNumber(value: amountIdr)) ?? "\(amountIdr)"
        return "Rp \(formatted)"
    }
}
