# Wispr Flow — technical dossier

**Method.** Static inspection of a locally installed, licensed copy of
`com.wispr.flowapp` 2.6.2 (versionCode 170) on an Android device. Evidence came
from the merged manifest, resource tables, protobuf/gRPC class names embedded in
the DEX files, and UI strings. No proprietary source code is reproduced here and
no code or assets were copied into Sotto. Findings marked **[observed]** are
directly present in the package; **[inferred]** are conclusions from those
observations and should be treated as hypotheses.

Analysis date: 2026-10-19.

---

## 1. Identity and packaging

| Property | Value |
|---|---|
| Package | `com.wispr.flowapp` |
| Version | 2.6.2 (170) |
| minSdk | 33 (Android 13) |
| targetSdk / compileSdk | 36 |
| APK size | ~69 MB base, split APKs |
| ABI | `arm64-v8a` only |
| Locales | `en` split + runtime language packs |
| Native libs | only `libsentry.so`, `libsentry-android.so`, `libandroidx.graphics.path.so` |

**Key finding [observed]:** there is **no bundled ASR model and no on-device
inference library** (no Whisper, ONNX Runtime, TensorFlow Lite, sherpa-onnx,
whisper.cpp). The only native code is crash reporting and a graphics helper.
Transcription therefore runs off-device through the network stack.

---

## 2. Process and service architecture

Manifest components (exported unless noted):

- `com.wispr.flowapp.activity.MainActivity` (launcher)
- `com.wispr.flowapp.activity.PromptActivity`
- `com.wispr.flowapp.activity.SpeechPermissionActivity`
- `com.wispr.flowapp.activity.AccessibilitySettingsPipActivity`
- `com.wispr.flowapp.activity.AccessibilityShortcutInfoActivity`
- `com.wispr.flowapp.activity.WordLimitSheetActivity`
- `com.wispr.flowapp.activity.PlanPageEntryActivity`
- `service.FlowService` — foreground service,
  `foregroundServiceType="microphone|specialUse"`, property
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "overlay-ui-host: keeps the Wispr Flow
  bubble visible while RECORD_AUDIO is revoked to allow re-grant"`
- `service.FlowAccessibilityService` — accessibility, `isAccessibilityTool=true`,
  runs in a **separate `:accessibility` process**
- `service.AccessibilityBridgeService` — non-exported
- `service.OnboardingService`, `service.FlowRecoveryJobService`,
  `service.BatteryOptimizationMonitorService`, `service.MainServiceWatchdog`
- `receiver.BootCompletedReceiver`, `receiver.ScreenUnlockReceiver` (exported)
- `io.sentry.android.core.SentryInitProvider`
- RevenueCat, Firebase Messaging, Customer.io, AppsFlyer, Google Play Billing

**Interpretation [inferred].** The product is an *overlay-first* design, not a
keyboard. A watchdog plus a recovery job suggests they fight Android's
background-process killer. The accessibility service is split into its own
process so a crash in the host UI does not take down text insertion. A
boot receiver plus screen-unlock receiver is used to re-arm the bubble after
reboots and unlocks.

---

## 3. Permissions

```
RECORD_AUDIO
INTERNET, ACCESS_NETWORK_STATE
SYSTEM_ALERT_WINDOW
POST_NOTIFICATIONS
VIBRATE
WAKE_LOCK
RECEIVE_BOOT_COMPLETED
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MICROPHONE
FOREGROUND_SERVICE_SPECIAL_USE
com.android.vending.BILLING
com.google.android.gms.permission.AD_ID
ACCESS_ADSERVICES_ATTRIBUTION
```

Notable permissions **not** requested: no `READ_CONTACTS`, no `READ_SMS`, no
storage permissions. The accessibility service is the only route by which it can
see screen content, and it is declared as an accessibility tool.

---

## 4. The dictation flow

**[observed]** UI strings and class names describe this loop:

```
open any app → focus a text box → tap the Flow Bubble → speak
→ live preview → final transcript → AI formatting → insert
```

Components found: `FlowBubble`, `PttDictatingLeftPreview`,
`PttDictatingRightPreview`, `PopoTranscribingPreview`,
`FlowBubbleActingButton`, `BubbleComposableIdleOfflinePreview`,
`onBubbleHiddenDuringDictation`, `Bubble size`, `snooze the bubble`.

**Behavior [observed via strings]:**

- The bubble is draggable and can be **snoozed by dragging it down in any text
  field**.
- Bubble size is configurable (`Settings → Bubble size`).
- There is both push-to-talk (`Ptt…`) and tap-to-toggle UI.
- Head-silence handling (`android.dictation.head_silence_ms`,
  `dictation.audio_silence_detected`) trims dead air.
- A session is capped at **5 minutes**:
  `dictation_limit_toast = "Session ended, 5-minute limit reached"`,
  warning at 1 minute remaining.
- A short utterance can bypass a full formatting round-trip
  (`dictation_short_circuit_provider`).

**Insertion [inferred].** Text is written into the focused node via the
accessibility service. The configuration requests:
`typeWindowsChanged`, `flagReportViewIds`, `flagRequestFilterKeyEvents`,
`flagRetrieveInteractiveWindows`, `flagInputMethodEditor`,
`canRequestFilterKeyEvents=true`, `canPerformGestures=true`.

That combination lets it (a) watch focus changes, (b) read the focused field,
(c) filter key events for a hold-to-talk shortcut, and (d) perform gestures such
as paste/tap. It does **not** use an InputMethodService; there is no IME service
in the manifest.

---

## 5. Transcription pipeline (the core learning)

**[observed]** DEX contains full gRPC/protobuf generated classes:

- `flow_api.v1.TranscriptionService`
- `flow_api.transcribe.v1.*` — `Request`, `Response`, `Init`, `Payload`,
  `CandidateText`, `Result`, `WordAlignments`, `TranscriberFinished`
- `flow_api.format.v1.FormatService` — `Request`, `Response`, `Result`, `Status`,
  `Payload`, `Init`
- `flow_api.v1.*` — `Context`, `Preferences`, `Personalization`, `State`,
  `Metadata`, `Client`, `Text`, `Payload`

Plus streaming plumbing: `bidiStreamingRpcFunction`, `clientStreamingRpcFunction`,
`serverStreamingRpcFunction`, `WebSocket`, `GrpcChannelProvider`,
`GrpcHostResolver`, `GrpcGeoProber`.

**[observed]** Codepaths/strings:

- `TRANSCRIBE_OVERHEAD_FIELD_NUMBER`, `TRANSCRIBER_FINISHED_FIELD_NUMBER`
- `AsrAudioChunksRequest(accessToken=…`
- `dictation.audio_silence_detected`
- `PCM_BUFFER_MULTIPLICATION_FACTOR`, `setPcmBufferMultiplicationFactor`
- `TYPE_Opus`, `TYPE_lpcm`, `opusMagic`
- `TranscribeAudio(isHoldToDictate=…`
- transcription and formatting are **two separate network services**
- `acceptedCandidates`, `wordAlignments`, `candidateText` → multi-hypothesis ASR
  with alignment data
- `polish_id`, `UploadPolishResponse` → formatting is tracked server-side
- `Personalization`, `Preferences`, `Context`, `AppContext`, `AdditionalContext`
  → formatting is conditioned on user and app context

**[inferred] Pipeline:**

```
AudioRecord (16-bit PCM)
   → silence/VAD gate + head-silence trim
   → Opus or LPCM framing
   → bidirectional gRPC stream to transcription host
   → partial hypotheses streamed back (live bubble text)
   → user stops / silence timeout
   → final transcript + word alignments
   → format/profile request (transcript + app context + user style)
   → formatted text returned
   → accessibility insert
```

**Hosting [observed].** One hard-coded host appears in DEX:
`model-v31pl413.grpc.api.baseten.co`. This is a Baseten model endpoint. The
presence of `GrpcGeoProber` + `GrpcHostResolver` + `region` fields indicates a
regional host-selection layer in front of model serving.

**Model vendors referenced in DEX [observed]:** `whisper`, `Cartesia`,
`Deepgram`, `Fireworks`, `OpenAI`, `Anthropic`, `Azure`, `Together`. These are
strings from SDKs/providers; the paid tier appears to route between providers
rather than shipping one fixed model. Treat this as routing/fallback evidence,
not proof of which model serves which plan.

**Latency design [inferred].** Smoothness comes from (1) streaming partials over
a persistent bidi gRPC channel, (2) separate cheap formatting call, (3)
short-circuit for short utterances, and (4) silence trimming so the network
carries less audio. The 5-minute cap keeps a single stream bounded.

---

## 6. Formatting and personalization

**[observed]** Strings:

- Styles: `Default` (standard formatting), `Formal` (caps + punctuation),
  `Casual` (caps + less punctuation), `very casual` (no caps + no punctuation),
  `Excited!` (caps + exclamation marks).
- Styles are **per app category**:
  `styles_apps_consumer_messengers`, `styles_apps_email`,
  `styles_apps_workplace_messengers`, `styles_apps_other`.
- `styles_english_only = "Styles currently apply in English only"`.
- Personalization onboarding: `styles_personalize_title =
  "Let's personalize Flow to you"`, `make_flow_sound_like_you_*`.
- `AppCategorizer` class → the app package is classified and mapped to a style.
- Dictionary: add/edit/delete words, validation against snippets.
- Snippets: personal + **team-shared** snippets, search, filter, max 60 chars
  for the trigger, expansion stored separately.
- History: `HistoryRepository`, `HistoryViewModel`.
- Prompt/AI quality: `AiPromptQuality`, `Personalization`, `Context`.

**[inferred]** Formatting is a server-side LLM call conditioned on: transcript,
selected style, target app category, user personalization profile, custom
dictionary, snippets, and possibly surrounding text (`AdditionalContext`,
`BeforeText`/`AfterText` accessors exist). The strongest moat is the per-app
style + personalization data, not the ASR itself.

---

## 7. Languages and translation

**[observed]**

- `language_selector_onboarding_description = "Wispr Flow works in 100+
  languages"`, multi-select language picker with search.
- `AllLanguages`, `Language` fields in protobuf; locale is sent to the service.
- No translation UI strings (no "translate to…", no target-language selector).
  The word `translate` in DEX is almost entirely AndroidX/Compose animation APIs.

**Conclusion:** Wispr Flow does **multilingual dictation** and code-switching,
not document translation. If a competitor offers true translation, that is a
genuine product gap.

---

## 8. "Mostly offline" — what is actually happening

**[observed]** The only offline-related app strings are:

- `BubbleComposableIdleOfflinePreview`, `FlowBubbleActingButtonOfflinePreview`,
  `PttDictatingOfflinePreview`, `PopoDictatingOfflinePreview` — Compose
  **preview** annotations used by developers, not runtime offline modes.
- `android.service.media.extra.OFFLINE` — Media3/ExoPlayer constant.
- `offline entitlements` — RevenueCat billing cache.

There is no offline ASR model, no downloaded language pack, and no local
formatting model.

**Conclusion:** The app is *UI-offline-resilient* (the bubble and history still
render, billing state is cached), but transcription and formatting are
cloud-dependent. The impression of "mostly offline" likely comes from the
persistent bubble, local history, and fast streaming partials that hide the
round-trip. A device-only transcription path is a real differentiator.

---

## 9. Plans, limits and monetization

**[observed]** Strings:

- Plans: Free, Pro, Pro Trial, Growth, Enterprise, Student.
- Free Android tier: **1,000 words per week** (soft cap: "Flow will slow down
  until next week"); hard cap: "You've hit your weekly word limit."
- **5-minute** per-session cap.
- Weekly usage counter in the drawer.
- RevenueCat + Google Play Billing.
- Trial and plan management screens; "Manage your Google Play subscription".
- Team plans with shared snippets.

---

## 10. Analytics and infrastructure

**[observed]** Firebase (messaging/analytics), Customer.io (messaging push),
AppsFlyer (install attribution), Sentry (crash reporting), RevenueCat (billing),
Google Play Billing, Play Core, protobuf + gRPC (OkHttp transport), Ktor client,
Kotlin coroutines/Flow, Jetpack Compose, Room, WorkManager, Media3/ExoPlayer.

Protobuf messages carry analytics-ish fields such as `AppCategory`,
`AppPackageName`, `AppVersion`, `Device`, `Platform`, `SessionId`, `Timezone`,
`WordsDictatedWeeklyPoint`, `DictationReliabilitySnapshot`, `Bias`,
`AuditLogStreamingRetention`. This strongly suggests they measure dictation
reliability and word usage server-side.

---

## 11. What to copy (the good ideas) and what to beat

Worth adopting:

1. Overlay bubble as the primary affordance — lower friction than switching
   keyboards.
2. Streaming partials in the bubble; perceived latency matters more than total
   latency.
3. Per-app tone mapping (email vs chat vs work chat).
4. Snippets + personal dictionary + word-level validation.
5. Separate, cheap formatting pass with a short-utterance fast path.
6. Silence trimming and head-silence detection before sending audio.
7. Watchdog/recovery for the foreground service; separate accessibility process.

Where Sotto is deliberately different:

1. **Device-first transcription.** Android's on-device recognizer when present;
   no audio upload by default. No bundled 70 MB model required.
2. **No word or session caps.**
3. **Rust text core** compiled to `libsotto.so`; deterministic, testable,
   microsecond-scale transformations instead of a cloud round-trip.
4. **Open source** (Apache-2.0) — auditable privacy claims.
5. **Bring-your-own cloud** is opt-in, never the default.
6. **Translation is a first-class command** in Sotto's roadmap rather than a
   hidden capability.

---

## 12. Reproducing this analysis on your own copy

```sh
adb shell pm path com.wispr.flowapp
adb pull <base.apk>
aapt2 dump badging base.apk
aapt2 dump xmltree --file AndroidManifest.xml base.apk
apktool d -s -o decoded base.apk
strings -n 4 decoded/classes*.dex > dex.txt
```

Everything in this dossier is derivable from those commands. No proprietary
code or assets are redistributed with Sotto.

---

## 13. Evidence index

| Claim | Source |
|---|---|
| No bundled ASR / native inference | split APK `lib/arm64-v8a` contents |
| Cloud gRPC transcription + formatting | `flow_api.transcribe.v1`, `flow_api.format.v1` in DEX |
| Baseten model host | string `model-v31pl413.grpc.api.baseten.co` |
| Opus / LPCM audio | `TYPE_Opus`, `TYPE_lpcm`, `opusMagic`, PCM accessors |
| 5-minute cap | `dictation_limit_toast` strings |
| 1,000 words/week free | `plan_feature_free_android_words`, `word_limit_*` |
| Per-app styles | `styles_apps_*`, `AppCategorizer` |
| 100+ languages | `language_selector_onboarding_description` |
| No offline model | no model assets, offline strings only in previews/billing |
| Accessibility insertion | service config flags + `InputAccessibilityService` |
| Separate accessibility process | `android:process=":accessibility"` |
| Bubble/overlay | `FlowService` specialUse subtype + bubble UI classes |
