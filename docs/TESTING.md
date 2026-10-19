# Testing log

## Verified on this machine (Termux, aarch64 Android)

### Rust core

```
$ cargo test
running 10 tests
test tests::cleans_fillers_and_capitalizes ... ok
test tests::spoken_punctuation ... ok
test tests::protects_urls_and_decimals ... ok
test tests::snippets_and_dictionary ... ok
test tests::commands_are_short_only ... ok
test tests::formal_expands_contractions ... ok
test tests::very_casual_strips_sentence_marks ... ok
test tests::notes_bullets_sentences ... ok
test tests::parses_compact_pairs ... ok
test tests::style_inference ... ok
test result: ok. 10 passed; 0 failed
```

The tests run as native `aarch64-linux-android` binaries, i.e. the same target
that ships in the APK.

### JNI surface

```
$ nm -D core/target/aarch64-linux-android/release/libsotto.so | grep Java_
Java_app_sotto_voice_core_NativeCore_command
Java_app_sotto_voice_core_NativeCore_countWords
Java_app_sotto_voice_core_NativeCore_process
Java_app_sotto_voice_core_NativeCore_version
```

### APK

```
$ aapt2 dump badging sotto.apk
package: name='app.sotto.voice' versionCode='1' versionName='1.0.0'
minSdkVersion:'26'
targetSdkVersion:'35'
application-label:'Sotto'

$ apksigner verify --print-certs sotto.apk
V3.0 Signer: certificate DN: CN=Sotto, O=Sotto, C=US
V3.0 Signer: certificate SHA-256 digest: 6a0d2e3d...

$ unzip -l sotto.apk
lib/arm64-v8a/libsotto.so
classes.dex
AndroidManifest.xml
resources.arsc
```

APK size: 697 KB (signed, single ABI).

## Not yet verified (needs a physical device)

`adb` reported no attached devices in the build environment, so the following
are implemented but unexecuted:

- Install and first-run onboarding.
- `SYSTEM_ALERT_WINDOW` bubble rendering, drag and tap.
- Foreground service start from the activity on Android 14/15.
- Microphone capture through `SpeechRecognizer` and partial results.
- Accessibility insertion via paste and the `ACTION_SET_TEXT` fallback.
- Voice commands (undo, send, new paragraph, restyle).
- Boot receiver re-arming the bubble.

## Device test checklist

```sh
adb install -r sotto.apk
adb shell am start -n app.sotto.voice/.MainActivity
adb logcat -s Sotto AndroidRuntime
```

1. Grant microphone, overlay and accessibility.
2. Open a notes app, focus a field, tap the bubble, say:
   `hello comma this is sotto period`
   → expect `Hello, this is Sotto.`
3. Say `scratch that` → field returns to its previous value.
4. Say `new paragraph` → two line breaks are inserted.
5. Say `make it notes` → the field becomes a bulleted list.
6. Disable the bubble in Settings → the overlay disappears immediately.
7. Reboot with "start on boot" enabled → the bubble returns.
8. Type a password field → Sotto refuses to read or write it.

## Known limitations

- Insertion relies on the target app honouring `ACTION_PASTE` or
  `ACTION_SET_TEXT`. A few editors (some WebViews, some games) reject both; in
  that case the text is copied to the clipboard.
- The device's recognizer determines language coverage and whether an offline
  model exists. Sotto never bundles a 70 MB ASR model.
- Restyle applies the Rust/Java engine to the whole focused field. It is
  line-based, not an LLM rewrite.
