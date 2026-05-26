import ActivityKit
import SwiftUI
import WidgetKit

/// A `Widget` that renders a KMP-LiveActivities Live Activity.
///
/// Add it to your Widget Extension's `WidgetBundle`. The library cannot — and by Apple's design
/// cannot — share UI to the Live Activity: the Lock Screen view and Dynamic Island must be
/// SwiftUI written in the Widget Extension. Decode `state.payload` into your own `Codable`
/// struct to drive the UI.
///
/// ```swift
/// @main
/// struct MyAppWidgets: WidgetBundle {
///     var body: some Widget {
///         KMPLiveActivityWidget { state in
///             DeliveryLockScreenView(jsonState: state.payload)
///         } dynamicIsland: { state in
///             DynamicIsland {
///                 DynamicIslandExpandedRegion(.leading) { /* ... */ }
///                 DynamicIslandExpandedRegion(.trailing) { /* ... */ }
///             } compactLeading: {
///                 /* ... */
///             } compactTrailing: {
///                 /* ... */
///             } minimal: {
///                 /* ... */
///             }
///         }
///     }
/// }
/// ```
@available(iOS 16.2, *)
public struct KMPLiveActivityWidget<LockScreen: View>: Widget {

    private let lockScreen: (KMPLiveActivityAttributes.ContentState) -> LockScreen
    private let dynamicIsland: (KMPLiveActivityAttributes.ContentState) -> DynamicIsland

    public init(
        @ViewBuilder lockScreen: @escaping (KMPLiveActivityAttributes.ContentState) -> LockScreen,
        dynamicIsland: @escaping (KMPLiveActivityAttributes.ContentState) -> DynamicIsland
    ) {
        self.lockScreen = lockScreen
        self.dynamicIsland = dynamicIsland
    }

    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: KMPLiveActivityAttributes.self) { context in
            lockScreen(context.state)
        } dynamicIsland: { context in
            dynamicIsland(context.state)
        }
    }
}
