import ActivityKit
import Foundation

/// A generic `ActivityAttributes` whose attributes and content state are opaque JSON strings
/// produced by the Kotlin side via `kotlinx.serialization`.
///
/// Every Live Activity started through KMP-LiveActivities, regardless of the consumer's Kotlin
/// attribute and state types, is backed by this single Swift type. The SwiftUI Widget Extension
/// decodes the `payload` strings into its own `Codable` structs to render the UI.
@available(iOS 16.1, *)
public struct KMPLiveActivityAttributes: ActivityAttributes {

    /// The dynamic part of the activity — replaced on every update.
    public struct ContentState: Codable, Hashable {

        /// JSON-encoded `LiveActivityContentState` from the Kotlin side.
        public var payload: String

        public init(payload: String) {
            self.payload = payload
        }

        /// Decodes `payload` into a consumer-defined `Decodable` state type.
        public func decoded<T: Decodable>(as type: T.Type) throws -> T {
            try JSONDecoder().decode(T.self, from: Data(payload.utf8))
        }
    }

    /// The fully-qualified Kotlin attributes type name (e.g. `"com.example.DeliveryAttributes"`).
    public var attributesTypeName: String

    /// JSON-encoded `LiveActivityAttributes` from the Kotlin side.
    public var payload: String

    public init(attributesTypeName: String, payload: String) {
        self.attributesTypeName = attributesTypeName
        self.payload = payload
    }

    /// Decodes `payload` into a consumer-defined `Decodable` attributes type.
    public func decoded<T: Decodable>(as type: T.Type) throws -> T {
        try JSONDecoder().decode(T.self, from: Data(payload.utf8))
    }
}
