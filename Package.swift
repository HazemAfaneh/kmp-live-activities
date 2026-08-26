// swift-tools-version:5.9
import PackageDescription

// This manifest has to sit at the repository root: SwiftPM resolves a package URL by looking
// for Package.swift there, and Xcode's "Add Package Dependencies…" fails outright without it.
// The sources stay under ios-swift-package/.
let package = Package(
    name: "KMPLiveActivities",
    platforms: [
        .iOS(.v16),
    ],
    products: [
        .library(name: "KMPLiveActivities", targets: ["KMPLiveActivities"]),
    ],
    targets: [
        .target(
            name: "KMPLiveActivities",
            path: "ios-swift-package/Sources/KMPLiveActivities"
        ),
    ]
)
