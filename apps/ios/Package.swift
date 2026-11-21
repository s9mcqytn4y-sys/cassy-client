// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "CassyPOS",
    defaultLocalization: "id",
    platforms: [
        .iOS(.v18)
    ],
    products: [
        .library(
            name: "CassyPOSCore",
            targets: ["CassyPOSCore"]
        )
    ],
    targets: [
        .target(
            name: "CassyPOSCore",
            path: "Sources/CassyPOS"
        ),
        .testTarget(
            name: "CassyPOSCoreTests",
            dependencies: ["CassyPOSCore"],
            path: "Tests/CassyPOSTests"
        )
    ]
)
