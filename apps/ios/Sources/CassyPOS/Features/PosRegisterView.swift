import SwiftUI

/// Adaptive POS Register supporting both iPadOS Landscape (split-pane) and iOS Portrait (cart sheet).
@MainActor
@Observable
public final class PosRegisterViewModel {
    public var products: [CatalogProduct] = []
    public var selectedQuantities: [String: Int] = [:]
    public var serverQuote: ServerQuoteResponse?
    public var isCartSheetPresented = false
    public var errorMessage: String?

    private let apiClient: CassyApiClient

    public init(apiClient: CassyApiClient) {
        self.apiClient = apiClient
    }

    public func loadCatalog() async {
        do {
            products = try await apiClient.fetchCatalog()
            errorMessage = nil
        } catch {
            errorMessage = "Gagal memuat katalog dari server."
        }
    }

    public func addProduct(_ product: CatalogProduct) {
        guard product.isAvailable else { return }
        selectedQuantities[product.id, default: 0] += 1
    }
}

public struct PosRegisterView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var viewModel: PosRegisterViewModel

    public init(viewModel: PosRegisterViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        NavigationStack {
            Group {
                if horizontalSizeClass == .regular {
                    HStack(spacing: 0) {
                        catalogGrid
                            .frame(maxWidth: .infinity)
                        Divider()
                        cartPanel
                            .frame(width: 360)
                    }
                } else {
                    ZStack(alignment: .bottom) {
                        catalogGrid
                        if !viewModel.selectedQuantities.isEmpty {
                            Button {
                                viewModel.isCartSheetPresented = true
                            } label: {
                                HStack {
                                    Image(systemName: "bag.fill")
                                    Text("Lihat Keranjang (\(viewModel.selectedQuantities.values.reduce(0, +)))")
                                        .fontWeight(.semibold)
                                    Spacer()
                                    if let total = viewModel.serverQuote?.grandTotalIdr {
                                        Text(RupiahFormatter.format(total))
                                            .monospacedDigit()
                                    }
                                }
                                .padding(.horizontal, 16)
                                .frame(minHeight: CassyTheme.minTouchTarget)
                                .background(CassyTheme.pandan)
                                .foregroundStyle(.white)
                                .clipShape(RoundedRectangle(cornerRadius: CassyTheme.buttonCornerRadius))
                            }
                            .padding(16)
                        }
                    }
                    .sheet(isPresented: $viewModel.isCartSheetPresented) {
                        cartPanel
                    }
                }
            }
            .background(CassyTheme.putihTulang)
            .navigationTitle("Cassy POS")
            .task {
                await viewModel.loadCatalog()
            }
        }
    }

    private var catalogGrid: some View {
        ScrollView {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 160), spacing: 12)],
                spacing: 12
            ) {
                ForEach(viewModel.products) { product in
                    Button {
                        viewModel.addProduct(product)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(product.name)
                                .font(.headline)
                                .foregroundStyle(CassyTheme.tinta)
                                .lineLimit(2)
                            Text(RupiahFormatter.format(product.basePriceIdr))
                                .font(.subheadline.weight(.semibold))
                                .monospacedDigit()
                                .foregroundStyle(CassyTheme.pandan)
                        }
                        .frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
                        .padding(12)
                        .background(CassyTheme.surface)
                        .clipShape(RoundedRectangle(cornerRadius: CassyTheme.cardCornerRadius))
                        .overlay(
                            RoundedRectangle(cornerRadius: CassyTheme.cardCornerRadius)
                                .stroke(CassyTheme.borderSubtle, lineWidth: 1)
                        )
                    }
                    .disabled(!product.isAvailable)
                }
            }
            .padding(16)
        }
    }

    private var cartPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Ringkasan Pesanan")
                .font(.title3.weight(.bold))
                .foregroundStyle(CassyTheme.tinta)
            if viewModel.selectedQuantities.isEmpty {
                ContentUnavailableView(
                    "Keranjang kosong",
                    systemImage: "bag",
                    description: Text("Pilih menu dari katalog untuk memulai pesanan")
                )
            } else {
                List {
                    ForEach(viewModel.products.filter { (viewModel.selectedQuantities[$0.id] ?? 0) > 0 }) { item in
                        HStack {
                            Text(item.name)
                            Spacer()
                            Text("x\(viewModel.selectedQuantities[item.id] ?? 0)")
                                .monospacedDigit()
                        }
                    }
                }
                .listStyle(.plain)
            }
            Spacer()
        }
        .padding(16)
        .background(CassyTheme.surface)
    }
}
