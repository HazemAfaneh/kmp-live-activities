import ActivityKit
import SwiftUI
import WidgetKit

/// Helpers for building a KMP-LiveActivities Live Activity.
///
/// The `Widget` protocol requires a no-argument `init()`, so a closure-taking wrapper cannot
/// itself conform to `Widget`. Instead, define your own `Widget` in the Widget Extension and
/// return `KMPLiveActivityWidget.configuration(...)` from its `body`. Decode `state.payload`
/// into your own `Codable` struct to drive the UI — the library cannot share UI with the Live
/// Activity, since Apple requires Lock Screen and Dynamic Island views to be SwiftUI written
/// in the Widget Extension.
///
/// ```swift
/// @main
/// struct MyAppWidgets: WidgetBundle {
///     var body: some Widget {
///         MyKMPLiveActivityWidget()
///     }
/// }
///
/// struct MyKMPLiveActivityWidget: Widget {
///     var body: some WidgetConfiguration {
///         KMPLiveActivityWidget.configuration { state in
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
public enum KMPLiveActivityWidget {

    public static func configuration<LockScreen: View>(
        @ViewBuilder lockScreen: @escaping (ActivityViewContext<KMPLiveActivityAttributes>) -> LockScreen,
        dynamicIsland: @escaping (ActivityViewContext<KMPLiveActivityAttributes>) -> DynamicIsland
    ) -> ActivityConfiguration<KMPLiveActivityAttributes> {
        ActivityConfiguration(for: KMPLiveActivityAttributes.self) { context in
            lockScreen(context)
        } dynamicIsland: { context in
            dynamicIsland(context)
        }
    }
}
