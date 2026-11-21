import Foundation

/// Authoritative server domain models. All monetary amounts are integer Rupiah (`Int64`).
/// The iOS/iPadOS client never computes final totals, tax, or service charges locally.
public struct CatalogProduct: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let name: String
    public let categoryId: String
    public let basePriceIdr: Int64
    public let prepStation: String
    public let isAvailable: Bool
    public let imageUrl: String?
}

public struct ServerQuoteResponse: Codable, Sendable {
    public let subtotalIdr: Int64
    public let taxIdr: Int64
    public let serviceChargeIdr: Int64
    public let grandTotalIdr: Int64
}

public struct KdsTicket: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let orderNumber: String
    public let tableLabel: String
    public let station: String
    public let status: String
    public let items: [String]
}

public enum CassyApiError: Error, Sendable {
    case unauthorized
    case invalidResponse
    case serverMessage(String)
}

/// Shared Swift 6 actor HTTP client with single-flight token refresh.
public actor CassyApiClient {
    private let baseURL: URL
    private let session: URLSession
    private let keychain: KeychainStore

    public init(baseURL: URL, keychain: KeychainStore, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.keychain = keychain
        self.session = session
    }

    public func fetchCatalog() async throws -> [CatalogProduct] {
        let url = baseURL.appendingPathComponent("api/v1/catalog/products")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = await keychain.read(forKey: "access_token") {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw CassyApiError.invalidResponse
        }
        if http.statusCode == 401 {
            throw CassyApiError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            throw CassyApiError.serverMessage("HTTP \(http.statusCode)")
        }
        return try JSONDecoder().decode([CatalogProduct].self, from: data)
    }
}
