# Raw observables — Wispr Flow 2.6.2 (170)

These are the exact values behind the dossier. They were read from a locally
installed, licensed copy. Nothing here is code or an asset from the package.

## Package

```
package: com.wispr.flowapp
versionName: 2.6.2
versionCode: 170
minSdkVersion: 33
targetSdkVersion: 36
compileSdkVersion: 36
base APK: 71,683,304 bytes
split: arm64_v8a, en, xxhdpi
ABI: arm64-v8a
```

Native libraries in the arm64 split:

```
lib/arm64-v8a/libsentry.so
lib/arm64-v8a/libsentry-android.so
lib/arm64-v8a/libandroidx.graphics.path.so
```

No ASR/inference library is present.

## Permissions

```
android.permission.SYSTEM_ALERT_WINDOW
android.permission.POST_NOTIFICATIONS
android.permission.RECORD_AUDIO
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.VIBRATE
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MICROPHONE
android.permission.FOREGROUND_SERVICE_SPECIAL_USE
android.permission.WAKE_LOCK
com.android.vending.BILLING
com.google.android.gms.permission.AD_ID
com.google.android.c2dm.permission.RECEIVE
```

## Components

Activities:

```
com.wispr.flowapp.activity.MainActivity                    (exported, launcher)
com.wispr.flowapp.activity.PlanPageEntryActivity
com.wispr.flowapp.activity.SpeechPermissionActivity
com.wispr.flowapp.activity.AccessibilitySettingsPipActivity
com.wispr.flowapp.activity.AccessibilityShortcutInfoActivity
com.wispr.flowapp.activity.PromptActivity
com.wispr.flowapp.activity.WordLimitSheetActivity
```

Services:

```
com.wispr.flowapp.service.FlowService
  foregroundServiceType = microphone|specialUse
  PROPERTY_SPECIAL_USE_FGS_SUBTYPE =
    "overlay-ui-host: keeps the Wispr Flow bubble visible while RECORD_AUDIO
     is revoked to allow re-grant"

com.wispr.flowapp.service.FlowAccessibilityService
  permission = android.permission.BIND_ACCESSIBILITY_SERVICE
  android:isAccessibilityTool = true
  android:process = ":accessibility"

com.wispr.flowapp.service.AccessibilityBridgeService
com.wispr.flowapp.service.FlowRecoveryJobService
com.wispr.flowapp.service.OnboardingService
com.wispr.flowapp.service.BatteryOptimizationMonitorService
com.wispr.flowapp.service.MainServiceWatchdog
```

Receivers:

```
com.wispr.flowapp.receiver.BootCompletedReceiver            (exported)
com.wispr.flowapp.receiver.ScreenUnlockReceiver             (exported)
```

## Accessibility configuration

```
accessibilityEventTypes   = typeWindowsChanged
accessibilityFeedbackType = feedbackGeneric
notificationTimeout       = 10
accessibilityFlags        = flagReportViewIds
                          | flagRequestFilterKeyEvents
                          | flagRetrieveInteractiveWindows
                          | flagInputMethodEditor
canRetrieveWindowContent  = true
canRequestFilterKeyEvents = true
canPerformGestures        = true
isAccessibilityTool       = true
```

Notable: it does **not** subscribe to every view event. It watches window
changes and filters key events instead of polling the tree continuously.

## gRPC surface (class names in DEX)

```
flow_api.v1.TranscriptionService
flow_api.transcribe.v1.Request
flow_api.transcribe.v1.Response
flow_api.transcribe.v1.Init
flow_api.transcribe.v1.Payload
flow_api.transcribe.v1.Result
flow_api.transcribe.v1.CandidateText
flow_api.transcribe.v1.WordAlignments
flow_api.transcribe.v1.TranscriberFinished
flow_api.format.v1.FormatService
flow_api.format.v1.Request
flow_api.format.v1.Response
flow_api.format.v1.Result
flow_api.format.v1.Status
flow_api.format.v1.Payload
flow_api.v1.Context
flow_api.v1.Preferences
flow_api.v1.Personalization
flow_api.v1.State
flow_api.v1.Metadata
flow_api.v1.Client
flow_api.v1.Text
flow_api.v1.Payload
com.wispr.grpc.GrpcChannelProvider
com.wispr.grpc.GrpcGeoProber
com.wispr.grpc.GrpcHostResolver
com.wispr.transcription.TranscribeRepository
com.wispr.transcribeGrpcInteractorProvider
com.wispr.TranscribeInteractor
```

Streaming helpers present: `bidiStreamingRpcFunction`,
`clientStreamingRpcFunction`, `serverStreamingRpcFunction`, `WebSocket`.

Observed host string:

```
model-v31pl413.grpc.api.baseten.co
```

## Audio strings

```
TYPE_Opus
TYPE_lpcm
opusMagic
PCM_BUFFER_MULTIPLICATION_FACTOR
setPcmBufferMultiplicationFactor
AsrAudioChunksRequest(accessToken=
TranscribeAudio(isHoldToDictate=
android.dictation.head_silence_ms
dictation.audio_silence_detected
silenceMs
dictationReliabilityGateProvider
dictation_short_circuit_provider
android_word_limit_dictation_gate
polish_id
UploadPolishResponse
```

## Formatting and product strings

```
plan_feature_free_android_words
"You've used %1$s of %2$,d words this week"
"You've hit your weekly word limit."
"No words remaining."
"Flow supports up to 5 minutes per session"
"Session ended, 5-minute limit reached"
dictation_limit_toast
styles_apps_consumer_messengers
styles_apps_email
styles_apps_workplace_messengers
styles_apps_other
styles_english_only = "Styles currently apply in English only"
language_selector_onboarding_description = "Wispr Flow works in 100+ languages"
```

Style labels: `Default`, `Formal`, `Casual`, `very casual`, `Excited!`.
Plans: `Free`, `Pro`, `Pro Trial`, `Growth`, `Enterprise`, `Student`.

Snippets: personal and team-shared, search, filter, 60-character trigger limit.

## Analytics / SDK surface

```
Firebase (messaging, analytics)
Customer.io
AppsFlyer
Sentry
RevenueCat
Google Play Billing
protobuf + gRPC over OkHttp
Ktor
Kotlin coroutines + Flow
Jetpack Compose
Room
WorkManager
Media3 / ExoPlayer
```

## Look-around note

`dx`-era tooling is not relevant here, but for anyone porting text rules: the
Rust `regex` crate does not support look-around. Sotto's engine uses word
boundaries instead.
