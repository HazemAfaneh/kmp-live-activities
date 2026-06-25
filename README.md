# KMP Live Activities

A Kotlin Multiplatform library that brings **iOS Live Activities** (Dynamic Island + Lock Screen)
and **Android 16 Live Updates** (status-bar chip + top-of-shade) behind a single, unified API.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hazemafaneh.liveactivities/live-activities)](https://central.sonatype.com/artifact/io.github.hazemafaneh.liveactivities/live-activities)
[![Android 16+](https://img.shields.io/badge/Android-16%2B-3DD68C?logo=android&logoColor=white)](https://developer.android.com/about/versions/16)
[![iOS 16.2+](https://img.shields.io/badge/iOS-16.2%2B-A78BFA?logo=apple&logoColor=white)](https://developer.apple.com/documentation/activitykit)
[![License](https://img.shields.io/badge/license-Apache%202.0-lightgrey)](LICENSE)

> See the [Pizza Delivery example app](https://github.com/hazemafaneh/LiveActivitiesExample) for a
> full end-to-end integration on both platforms.

---

## Screenshots

<table>
  <tr>
    <th align="center" colspan="2">Android (Samsung Galaxy)</th>
    <th align="center" colspan="2">iOS (Dynamic Island)</th>
  </tr>
  <tr>
    <td align="center"><img src="images/android-home-screen.png" width="160" alt="Android home screen — Live Update at top"/></td>
    <td align="center"><img src="images/android-lock-screen.png" width="160" alt="Android lock screen — promoted Live Update"/></td>
    <td align="center"><img src="images/ios-dynamic-island-compact.png" width="160" alt="iOS Dynamic Island compact"/></td>
    <td align="center"><img src="images/ios-dynamic-island-expanded.png" width="160" alt="iOS Dynamic Island expanded"/></td>
  </tr>
  <tr>
    <td align="center"><sub>Home screen</sub></td>
    <td align="center"><sub>Lock screen</sub></td>
    <td align="center"><sub>Compact</sub></td>
    <td align="center"><sub>Expanded</sub></td>
  </tr>
</table>

---

## Features

- **One shared API** — `start`, `update`, `end` from your KMP shared module; the library handles each platform.
- **Your UI, fully** — Android notification content is controlled by a renderer you provide; iOS Live Activity UI is written in SwiftUI in your Widget Extension.
- **Push-ready** — APNs push tokens on iOS; a vendor-neutral `LiveActivityPushHandler` for FCM on Android.
- **Typed & safe** — Generic over your own `LiveActivityAttributes` and `LiveActivityContentState`. All failures surface as sealed `Result` values, never thrown.

---

## Requirements

| | Minimum |
|---|---|
| Android (promoted Live Update) | API 36 (Android 16) |
| Android (minSdk / graceful fallback) | API 24 |
| iOS | 16.2 |
| Kotlin | 2.2 |
| AGP | 8.13 |

> **Note:** On Android versions below 16 the library still posts a standard ongoing notification;
> status-bar promotion simply won't occur.

---

## Installation

### Gradle

Add the dependency to your **shared** module and export it for the iOS framework:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.hazemafaneh.liveactivities:live-activities:0.1.0")
        }
    }

    // Export so the Kotlin types are visible in the iOS framework
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export("io.github.hazemafaneh.liveactivities:live-activities:0.1.0")
        }
    }
}
```

### Swift Package (iOS)

In Xcode, add the package to your **Widget Extension** target (not the main app):

```
https://github.com/hazemafaneh/kmp-live-activities
```

Minimum version: `0.1.0` · Product: `KMPLiveActivities`

### Android manifest

The library's manifest merges the foreground service and dismiss receiver automatically.
Your app only needs to declare:

```xml
<!-- Required at runtime on API 33+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Normal-protection permission for Android 16 status-bar promotion.
     No runtime request needed; manifest declaration is sufficient. -->
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

---

## Quick start

### 1. Define your types in shared code

Both `LiveActivityAttributes` (static, set once) and `LiveActivityContentState`
(dynamic, updated over time) must be annotated with `@Serializable`:

```kotlin
// commonMain
@Serializable
data class OrderAttributes(
    val orderId: String,
    val vendorName: String,
    val itemSummary: String,
) : LiveActivityAttributes

@Serializable
data class OrderState(
    val status: String,          // "preparing" | "on_the_way" | "delivered"
    val etaMinutes: Int,
    val progressPercent: Int,
) : LiveActivityContentState
```

### 2. Android — initialize and register a renderer

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveActivityManager.init(this)

        LiveActivityManager.registerAttributedRenderer(
            stateType = OrderState::class,
            renderer = AttributedLiveActivityRenderer { attributes, state ->
                LiveActivityNotificationContent(
                    title = attributes.vendorName,
                    text  = "ETA ${state.etaMinutes} min · ${statusLabel(state.status)}",
                    subText = attributes.itemSummary,
                    progressStyle = ProgressStyleData(progress = state.progressPercent),
                )
            },
        )
    }
}
```

### 3. iOS — wire up the bridge

In your app's `@main` entry point:

```swift
@main
struct MyApp: App {
    init() {
        if #available(iOS 16.2, *) {
            LiveActivityManager.shared.register(bridge: LiveActivityKitBridge())
        }
    }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

See [iOS bridge adapter](#ios-bridge-adapter) below for the `LiveActivityKitBridge` implementation.

### 4. Build your Widget Extension

```swift
struct MyOrderWidget: Widget {
    var body: some WidgetConfiguration {
        KMPLiveActivityWidget.configuration { context in
            let state = try? context.state.decoded(as: OrderStateDTO.self)
            return OrderLockScreenView(state: state)
        } dynamicIsland: { context in
            let state = try? context.state.decoded(as: OrderStateDTO.self)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.center) { Text(state?.statusLabel ?? "") }
            } compactLeading: {
                Image(systemName: "bag.fill")
            } compactTrailing: {
                Text("\(state?.etaMinutes ?? 0)m")
            } minimal: {
                Image(systemName: "bag")
            }
        }
    }
}

// Mirror the Kotlin OrderState fields
struct OrderStateDTO: Decodable {
    let status: String
    let etaMinutes: Int
    let progressPercent: Int
}
```

### 5. Start, update, and end

```kotlin
// Start
val activity = LiveActivityManager.start(
    attributes = OrderAttributes(
        orderId     = "ORD-5519",
        vendorName  = "Green Bowl",
        itemSummary = "Falafel wrap × 2",
    ),
    initialState = OrderState(status = "preparing", etaMinutes = 20, progressPercent = 10),
    config = LiveActivityConfig(androidSmallIconResName = "ic_notification"),
).getOrElse { /* handle LiveActivityException */ return }

// Update
LiveActivityManager.update(
    activityId = activity.id,
    newState   = OrderState(status = "on_the_way", etaMinutes = 8, progressPercent = 65),
)

// End (keep visible for 5 seconds)
LiveActivityManager.end(
    activityId      = activity.id,
    dismissalPolicy = DismissalPolicy.After(5.seconds),
)
```

---

## API reference

### `LiveActivityManager`

The single entry point. Every operation returns a `Result`; failures are reported as
`LiveActivityException` inside `Result.failure` and are never thrown.

| Member | Description |
|---|---|
| `activities: StateFlow<List<LiveActivity<*, *>>>` | All tracked activities, including ended ones. |
| `areActivitiesEnabled: StateFlow<Boolean>` | Whether the user permits Live Activities. |
| `start(attributes, initialState, config)` | Creates a new Live Activity. |
| `update(activityId, newState)` | Replaces the content state of a running activity. |
| `end(activityId, dismissalPolicy)` | Ends a single activity. |
| `endAll(dismissalPolicy)` | Ends all running activities. |

### `LiveActivity<A, S>`

A handle to a running activity. Stays usable until `status` reaches `Dismissed`.

| Property | Type | Description |
|---|---|---|
| `id` | `String` | Stable identifier — pass to `update` / `end`. |
| `attributes` | `A` | The static attributes set at start time. |
| `state` | `StateFlow<S>` | The latest content state; emits on every update. |
| `pushToken` | `StateFlow<String?>` | APNs push token (iOS only, when `PushType.Token` is used). Arrives asynchronously. |
| `status` | `StateFlow<LiveActivityStatus>` | Lifecycle state: `Active → Stale → Ended → Dismissed`. |

### `DismissalPolicy`

| Variant | Behaviour |
|---|---|
| `DismissalPolicy.Immediate` | Remove from screen at once. Default. |
| `DismissalPolicy.After(duration)` | Stay visible for the given duration after ending. |
| `DismissalPolicy.At(instant)` | Stay visible until a specific `Instant`. |

### `LiveActivityConfig`

```kotlin
LiveActivityConfig(
    pushType                = PushType.None,         // or PushType.Token for APNs (iOS)
    staleAfter              = 30.minutes,             // mark Stale if no update arrives
    androidChannelId        = "live_activities",
    androidSmallIconResName = "ic_notification",
    androidConfig = LiveActivityAndroidConfig(
        channelName        = "Order updates",
        channelDescription = "Live order tracking",
        statusChip = StatusChipConfig.Chronometer(
            baseEpochMillis = arrivalEpochMs,
            countDown       = true,
        ),
    ),
)
```

---

## Android notification content

Your renderer returns a `LiveActivityNotificationContent` that the library converts into a
`NotificationCompat.ProgressStyle` notification — the style Android 16 requires for
status-bar promotion.

| Field | Type | Notes |
|---|---|---|
| `title` | `String` | Bold first line. Falls back to the app name if blank. |
| `text` | `String` | Main notification body text. |
| `progressStyle` | `ProgressStyleData?` | Enables Android 16 promotion. Supports segments, points, and start/end icons. |
| `subText` | `String?` | Short text next to the app name. |
| `statusChip` | `StatusChipConfig?` | `CriticalText(text)` or `Chronometer(baseEpochMillis, countDown)`. |
| `accentColor` | `@ColorInt Int?` | Tints the app name and small-icon background. |
| `contentIntent` | `PendingIntent?` | Tap action. Defaults to the launcher activity. Supply a custom intent to deep-link into a specific screen. |

### Renderers

| Type | When to use |
|---|---|
| `LiveActivityRenderer<S>` | You only need the dynamic state to render content. |
| `AttributedLiveActivityRenderer<A, S>` | You also need the static attributes (vendor name, order ID, etc.). |
| `DefaultProgressRenderer<S>` | Pre-built renderer; pass lambdas for each field to avoid boilerplate. |

---

## Remote push updates

### Android (FCM)

The library has no FCM dependency. Call `LiveActivityPushHandler` from your existing messaging service:

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val activityId = message.data["activityId"] ?: return
        val stateJson  = message.data["state"]      ?: return
        runBlocking {
            LiveActivityPushHandler.handleStateUpdate(activityId, stateJson)
        }
    }
}
```

`stateJson` must be the kotlinx.serialization output of your `LiveActivityContentState` type —
the same schema the library produces when calling `update()` locally.

### iOS (APNs)

Request an APNs push token with `pushType = PushType.Token`, then observe it on the activity handle:

```kotlin
val activity = LiveActivityManager.start(
    attributes   = attributes,
    initialState = initialState,
    config = LiveActivityConfig(pushType = PushType.Token),
).getOrElse { return }

// Collect the token and register it with your server
activity.pushToken.filterNotNull().first().let { token ->
    myServer.registerPushToken(activityId = activity.id, token = token)
}
```

---

## Error handling

All operations return `Result<T>`. Failures are sealed subclasses of `LiveActivityException`
and are never thrown across the public API:

```kotlin
LiveActivityManager.start(attributes, state)
    .onSuccess { activity -> /* running */ }
    .onFailure { error ->
        when (error) {
            is LiveActivityException.NotAuthorized          -> { /* prompt user */ }
            is LiveActivityException.NotSupportedOnPlatform -> { /* skip gracefully */ }
            is LiveActivityException.BudgetExceeded         -> { /* too many active (iOS) */ }
            is LiveActivityException.PayloadTooLarge        -> { /* trim your state */ }
            is LiveActivityException.ActivityNotFound       -> { /* stale id */ }
            is LiveActivityException.SerializationFailed    -> { /* check @Serializable */ }
            else                                            -> { /* log */ }
        }
    }
```

| Exception | Cause |
|---|---|
| `NotAuthorized` | User disabled Live Activities (iOS) or `POST_NOTIFICATIONS` not granted (Android 13+). |
| `NotSupportedOnPlatform` | Runtime OS below iOS 16.2 or Android 16. |
| `BudgetExceeded` | iOS rejected: too many Live Activities already running. |
| `PayloadTooLarge` | Serialized state exceeds 4 KB (iOS limit). Slim down your content state. |
| `ActivityNotFound` | No running activity matches the id. |
| `SerializationFailed` | Attributes or state not annotated with `@Serializable`. |

---

## iOS bridge adapter

Create a Swift adapter in your **main app target** that wires `KMPLiveActivityController`
to the Kotlin `LiveActivityBridge` protocol:

```swift
// LiveActivityKitBridge.swift — main app target
import KMPLiveActivities
import shared // your KMP shared module

@available(iOS 16.2, *)
final class LiveActivityKitBridge: LiveActivityBridge {

    init() {
        let c = KMPLiveActivityController.shared
        c.onStartResult  = { id, ok, err in
            LiveActivityManager.shared.notifyStartResult(activityId: id, success: ok, errorKind: err)
        }
        c.onPushToken    = { id, tok in
            LiveActivityManager.shared.notifyPushToken(activityId: id, token: tok)
        }
        c.onStatusChanged = { id, s in
            LiveActivityManager.shared.notifyStatusChanged(activityId: id, status: s)
        }
    }

    func areActivitiesEnabled() -> Bool { KMPLiveActivityController.shared.areActivitiesEnabled }

    func start(activityId: String, attributesTypeName: String, attributesJson: String,
               contentStateJson: String, staleAfterSeconds: Double, requestPushToken: Bool) {
        KMPLiveActivityController.shared.start(
            activityId: activityId, attributesTypeName: attributesTypeName,
            attributesJson: attributesJson, contentStateJson: contentStateJson,
            staleAfterSeconds: staleAfterSeconds, requestPushToken: requestPushToken)
    }

    func update(activityId: String, contentStateJson: String, staleAfterSeconds: Double) {
        KMPLiveActivityController.shared.update(
            activityId: activityId, contentStateJson: contentStateJson,
            staleAfterSeconds: staleAfterSeconds)
    }

    func end(activityId: String, finalContentStateJson: String?, dismissalSeconds: Double) {
        KMPLiveActivityController.shared.end(
            activityId: activityId, finalContentStateJson: finalContentStateJson,
            dismissalSeconds: dismissalSeconds)
    }
}
```

---

## Example app

[**LiveActivitiesExample**](https://github.com/hazemafaneh/LiveActivitiesExample) is a full KMP
project (Android + iOS) showing a **pizza delivery tracker** that moves through all four delivery
stages: *Preparing → On the way → Arriving → Delivered*.

It demonstrates:

- Defining shared `LiveActivityAttributes` and `LiveActivityContentState` models in `commonMain`
- Starting, updating, and ending a `LiveActivity` from shared Compose UI
- The `LiveActivityKitBridge` Swift adapter wiring `KMPLiveActivityController` to the Kotlin protocol
- A SwiftUI Widget Extension with Lock Screen and Dynamic Island views
- Decoding the JSON payload into Swift DTOs to drive the UI

---

## License

```
Copyright 2026 Hazem Afaneh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0
```
