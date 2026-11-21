import SwiftUI

@MainActor
@Observable
public final class SplitSettlementViewModel {
    public var openBills: [OpenSplitBillSummary] = []
    public var activeBill: SplitBillDetail?
    public var payingShareId: String?
    public var errorMessage: String?

    private var idempotencyKeys: [String: String] = [:]
    private let repository: SplitBillRepository

    public init(repository: SplitBillRepository) {
        self.repository = repository
    }

    public func loadOpenBills() async {
        do {
            openBills = try await repository.fetchOpenSplitBills()
            errorMessage = nil
        } catch {
            errorMessage = "Gagal memuat daftar tagihan split terbuka."
        }
    }

    public func openBill(orderId: String) async {
        do {
            activeBill = try await repository.fetchSplitBill(orderId: orderId)
            errorMessage = nil
        } catch {
            errorMessage = "Gagal memuat rincian split bill."
        }
    }

    public func collectShareCash(share: SplitShareItem) async {
        guard let bill = activeBill, share.isCollectable else { return }
        let key = idempotencyKeys[share.id] ?? UUID().uuidString
        idempotencyKeys[share.id] = key
        payingShareId = share.id
        defer { payingShareId = nil }

        do {
            try await repository.settleShare(
                orderId: bill.orderId,
                payload: SettleSplitShareRequest(
                    splitId: share.id,
                    method: "cash",
                    cashTenderedAmount: share.amountIdr,
                    idempotencyKey: key
                )
            )
            idempotencyKeys.removeValue(forKey: share.id)
            await openBill(orderId: bill.orderId)
        } catch {
            errorMessage = "Pembayaran tunai gagal. Coba lagi dengan aman (idempoten)."
        }
    }
}

public struct SplitSettlementView: View {
    @State private var viewModel: SplitSettlementViewModel

    public init(viewModel: SplitSettlementViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        NavigationStack {
            List {
                if let bill = viewModel.activeBill {
                    Section("Rincian Bagian — #\(bill.orderNumber)") {
                        ForEach(bill.shares) { share in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(share.label)
                                        .font(.headline)
                                        .foregroundStyle(CassyTheme.tinta)
                                    Text(RupiahFormatter.format(share.amountIdr))
                                        .font(.subheadline.weight(.semibold))
                                        .monospacedDigit()
                                        .foregroundStyle(CassyTheme.pandan)
                                }
                                Spacer()
                                if share.isSettled {
                                    Text("Lunas")
                                        .font(.caption.weight(.bold))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(CassyTheme.okBg)
                                        .foregroundStyle(CassyTheme.okFg)
                                        .clipShape(Capsule())
                                } else {
                                    Button("Bayar Tunai") {
                                        Task { await viewModel.collectShareCash(share: share) }
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(CassyTheme.pandan)
                                    .frame(minHeight: CassyTheme.minTouchTarget)
                                    .disabled(viewModel.payingShareId == share.id)
                                }
                            }
                        }
                    }
                } else {
                    Section("Tagihan Split Belum Lunas") {
                        ForEach(viewModel.openBills) { item in
                            Button {
                                Task { await viewModel.openBill(orderId: item.orderId) }
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("#\(item.orderNumber) • \(item.tableLabel)")
                                            .font(.headline)
                                            .foregroundStyle(CassyTheme.tinta)
                                        Text("\(item.unpaidShareCount) bagian belum lunas")
                                            .font(.caption)
                                            .foregroundStyle(CassyTheme.warnFg)
                                    }
                                    Spacer()
                                    Text(RupiahFormatter.format(item.remainingAmountIdr))
                                        .font(.subheadline.weight(.bold))
                                        .monospacedDigit()
                                        .foregroundStyle(CassyTheme.tinta)
                                }
                                .frame(minHeight: CassyTheme.minTouchTarget)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Selesaikan Split Bill")
            .task { await viewModel.loadOpenBills() }
        }
    }
}
