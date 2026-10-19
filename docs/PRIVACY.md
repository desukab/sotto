# Privacy

Sotto's default configuration is local-only.

## What stays on the device

- All audio captured by the microphone is handed to Android's
  `SpeechRecognizer`. Whether that recognizer runs on-device or in the cloud is
  an Android platform decision. Sotto requests offline recognition when the
  "On-device only" setting is enabled.
- Final transcripts, history, snippets, dictionary entries and statistics are
  stored in the app's private storage.
- The Rust text engine runs in-process. Nothing is uploaded.

## What can leave the device

- Nothing, unless you enable an optional cloud feature in a future build.
- Google Play services may process speech according to the device's
  recognizer settings. Use "On-device only" to restrict this where the device
  supports it.

## What Sotto never does

- No accounts.
- No analytics SDKs.
- No advertising ID access.
- No clipboard history.
- No reading of passwords: editable nodes flagged `isPassword()` are skipped.
- No screen capture; the accessibility service reads only the focused editable
  field so it can insert text.

## Data deletion

Settings → Data → Clear history / Reset statistics, or clear app storage from
Android settings. Uninstalling removes everything.
