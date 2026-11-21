import Foundation

/// Models for cashier settlement of unpaid guest or POS split shares (server-authoritative `Int64`).
public struct SplitShareItem: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let label: String
    public let amountIdr: Int64
    public let status: String // unpaid | pending | paid | expired

    public var isSettled: Bool { status.lowercased() == "paid" }
    public var isCollectable: Bool { !isSettled }
}

public struct OpenSplitBillSummary: Identifiable, Codable, Sendable, Hashable {
    public let orderId: String
    public let orderNumber: String
    public let tableLabel: String
    public let totalAmountIdr: Int64
    public let remainingAmountIdr: Int64
    public let unpaidShareCount: Int

    public var id: String { orderId }
}

public struct SplitBillDetail: Codable, Sendable {
    public let orderId: String
    public let orderNumber: String
    public let paymentStatus: String
    public let totalAmountIdr: Int64
    public let shares: [SplitShareItem]

    public var isFullyPaid: Bool { paymentStatus.lowercased() == "paid" }
}

public struct SettleSplitShareRequest: Codable, Sendable {
    public let splitId: String
    public let method: String // "cash" | "qris"
    public let cashTenderedAmount: Int64?
    public let idempotencyKey: String
}

/// Swift 6 actor repository for listing and settling unpaid split shares at the iOS/iPadOS register.
public actor SplitBillRepository {
    private let baseURL: URL
    private let session: URLSession
    private let keychain: KeychainStore

    public init(baseURL: URL, keychain: KeychainStore, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.keychain = keychain
        self.session = session
    }

    public func fetchOpenSplitBills() async throws -> [OpenSplitBillSummary] {
        let url = baseURL.appendingPathComponent("api/v1/pos/orders")
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "payment_status", value: "partially_paid")]
        guard let targetUrl = components?.url else { throw CassyApiError.invalidResponse }

        var request = URLRequest(url: targetUrl)
        request.httpMethod = "GET"
        if let token = await keychain.read(forKey: "access_token") {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CassyApiError.invalidResponse
        }
        return try JSONDecoder().decode([OpenSplitBillSummary].self, from: data)
    }

    public func fetchSplitBill(orderId: String) async throws -> SplitBillDetail {
        let url = baseURL.appendingPathComponent("api/v1/pos/orders/\(orderId)/splits")
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = await keychain.read(forKey: "access_token") {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CassyApiError.invalidResponse
        }
        return try JSONDecoder().decode(SplitBillDetail.self, from: data)
    }

    public func settleShare(orderId: String, payload: SettleSplitShareRequest) async throws {
        let url = baseURL.appendingPathComponent("api/v1/pos/orders/\(orderId)/payments")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(payload.idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        if let token = await keychain.read(forKey: "access_token") {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONEncoder().encode(payload)
        let (_, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CassyApiError.serverMessage("Gagal memproses pembayaran bagian tagihan.")
        }
    }
}
