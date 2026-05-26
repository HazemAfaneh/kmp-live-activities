import ActivityKit
import Foundation

/// Performs all `ActivityKit` work for KMP-LiveActivities.
///
/// This type is pure Swift and has no dependency on the Kotlin framework, so it lives in the
/// shipped Swift package. A small adapter in the consuming app target conforms it to the Kotlin
/// `LiveActivityBridge` protocol and forwards the callback closures to `LiveActivityManager`.
/// See the project's `ios-setup.md` for that adapter.
@available(iOS 16.2, *)
public final class KMPLiveActivityController {

    /// Shared instance used by the Kotlin bridge adapter.
    public static let shared = KMPLiveActivityController()

    private init() {}

    /// Invoked with `(activityId, success, errorKind)` once a `start` request resolves.
    public var onStartResult: ((String, Bool, String?) -> Void)?
    /// Invoked with `(activityId, token)` when an APNs push token arrives or is cleared.
    public var onPushToken: ((String, String?) -> Void)?
    /// Invoked with `(activityId, statusName)` on every lifecycle change.
    public var onStatusChanged: ((String, String) -> Void)?

    private var activities: [String: Activity<KMPLiveActivityAttributes>] = [:]
    private var observers: [String: [Task<Void, Never>]] = [:]

    /// Whether the user currently permits Live Activities.
    public var areActivitiesEnabled: Bool {
        ActivityAuthorizationInfo().areActivitiesEnabled
    }

    public func start(
        activityId: String,
        attributesTypeName: String,
        attributesJson: String,
        contentStateJson: String,
        staleAfterSeconds: Double,
        requestPushToken: Bool
    ) {
        guard areActivitiesEnabled else {
            onStartResult?(activityId, false, "unauthorized")
            return
        }
        let attributes = KMPLiveActivityAttributes(
            attributesTypeName: attributesTypeName,
            payload: attributesJson
        )
        let content = ActivityContent(
            state: KMPLiveActivityAttributes.ContentState(payload: contentStateJson),
            staleDate: staleAfterSeconds > 0 ? Date().addingTimeInterval(staleAfterSeconds) : nil
        )
        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: content,
                pushType: requestPushToken ? .token : nil
            )
            activities[activityId] = activity
            onStartResult?(activityId, true, nil)
            observe(activityId: activityId, activity: activity, requestPushToken: requestPushToken)
        } catch {
            onStartResult?(activityId, false, Self.errorKind(of: error))
        }
    }

    public func update(activityId: String, contentStateJson: String, staleAfterSeconds: Double) {
        guard let activity = activities[activityId] else { return }
        let content = ActivityContent(
            state: KMPLiveActivityAttributes.ContentState(payload: contentStateJson),
            staleDate: staleAfterSeconds > 0 ? Date().addingTimeInterval(staleAfterSeconds) : nil
        )
        Task { await activity.update(content) }
    }

    public func end(activityId: String, finalContentStateJson: String?, dismissalSeconds: Double) {
        guard let activity = activities[activityId] else { return }
        let dismissalPolicy: ActivityUIDismissalPolicy = dismissalSeconds > 0
            ? .after(Date().addingTimeInterval(dismissalSeconds))
            : .immediate
        let finalContent: ActivityContent<KMPLiveActivityAttributes.ContentState>?
        if let json = finalContentStateJson {
            finalContent = ActivityContent(
                state: KMPLiveActivityAttributes.ContentState(payload: json),
                staleDate: nil
            )
        } else {
            finalContent = nil
        }
        Task {
            await activity.end(finalContent, dismissalPolicy: dismissalPolicy)
            activities[activityId] = nil
            observers[activityId]?.forEach { $0.cancel() }
            observers[activityId] = nil
            onStatusChanged?(activityId, "Dismissed")
        }
    }

    private func observe(
        activityId: String,
        activity: Activity<KMPLiveActivityAttributes>,
        requestPushToken: Bool
    ) {
        var tasks: [Task<Void, Never>] = []

        tasks.append(Task {
            for await state in activity.activityStateUpdates {
                onStatusChanged?(activityId, Self.statusName(of: state))
            }
        })

        if requestPushToken {
            tasks.append(Task {
                for await tokenData in activity.pushTokenUpdates {
                    let token = tokenData.map { String(format: "%02x", $0) }.joined()
                    onPushToken?(activityId, token)
                }
            })
        }

        observers[activityId] = tasks
    }

    private static func statusName(of state: ActivityState) -> String {
        switch state {
        case .active: return "Active"
        case .stale: return "Stale"
        case .ended: return "Ended"
        case .dismissed: return "Dismissed"
        @unknown default: return "Active"
        }
    }

    private static func errorKind(of error: Error) -> String {
        let text = String(describing: error).lowercased()
        if text.contains("denied") || text.contains("unentitled") { return "unauthorized" }
        if text.contains("maximum") || text.contains("budget") { return "budget" }
        if text.contains("toolarge") { return "payloadTooLarge" }
        if text.contains("unsupported") { return "unsupported" }
        return "unknown"
    }
}
