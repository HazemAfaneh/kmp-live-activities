// swift-tools-version:5.9
import PackageDescription

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
            path: "Sources/KMPLiveActivities"
        ),
    ]
)
