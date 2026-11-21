import Testing
@testable import CassyPOSCore

@Test func rupiahFormatterFormatsZeroAndThousands() {
    #expect(RupiahFormatter.format(0) == "Rp 0")
    #expect(RupiahFormatter.format(32_000) == "Rp 32.000")
    #expect(RupiahFormatter.format(1_250_000) == "Rp 1.250.000")
}

@Test func cassyThemeMeetsMinimumTouchTarget() {
    #expect(CassyTheme.minTouchTarget >= 48)
}
