# Offline speech recognition

Sotto's default offline path uses the recognizer the device already has.

## How it works

Android exposes two recognizers:

| | `createSpeechRecognizer` | `createOnDeviceSpeechRecognizer` (API 31+) |
|---|---|---|
| Runs | where the vendor decides | on the device |
| Needs network | usually | no |
| Audio leaves device | usually | no |
| Model | installed by the OS | installed by the OS |
| App size cost | zero | zero |

Sotto prefers the second when **Settings → On-device only** is enabled.

### Strict mode

"On-device only" is strict: if the device has no on-device model, Sotto refuses
to start rather than silently falling back to a networked recognizer. That keeps
the privacy claim honest. The Recognizer panel in Settings shows whether an
on-device model exists and links to the system speech settings.

This is why the APK is under 1 MB instead of shipping a 70 MB model, and why
there is no first-run model download.

## Optional: bundling a real ASR model

If you want Sotto to work on devices with no system recognizer at all, bundle
`whisper.cpp` (or whisper-onnx / Moonshine / Parakeet) inside the Rust core.

### Steps

1. Add the model as an asset, or download it on first use into
   `context.getFilesDir()/models/`. Sizes:
   - `tiny.en` ~75 MB, fast, English only
   - `base.en` ~142 MB, better accuracy
   - `small` ~466 MB, multilingual
2. Cross-compile `whisper.cpp` for `aarch64-linux-android` and link it into
   `core/` behind a Cargo feature, e.g. `features = ["whisper"]`.
3. Capture PCM with `AudioRecord` (16 kHz mono, PCM16) instead of
   `SpeechRecognizer`, run a simple energy/RMS gate to trim silence, and feed
   30-second windows to the model.
4. Expose it through the existing JNI surface
   (`NativeCore_process` style) as `NativeCore_transcribe(byte[] pcm, int sampleRate)`.
5. Keep `libsotto.so` per-ABI, or ship the model as a Play Asset Delivery
   module so the base APK stays small.

### Tradeoff

The moment you bundle a model you inherit: a multi-hundred-megabyte download,
per-architecture builds, thermal and battery cost, and a slower first-run. Most
users already have a perfectly good on-device recognizer from the OS vendor,
which is why the shipped default avoids this.

### Wiring the capture path

If you implement it, the service already has the right shape:

- `SottoService.startListening()` → replace the `SpeechRecognizer` block.
- `SottoService.deliver(text, styleKey, record)` → unchanged; it inserts,
  records history and updates the bubble.
- The bubble's `WaveView` just needs `push(level)` from your RMS callback
  instead of `onRmsChanged`.

Everything downstream of the transcript is model-agnostic.
