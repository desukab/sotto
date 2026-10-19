# Sotto

**Speak. It's written.**

Sotto is a private, unlimited voice-dictation layer for Android. Tap a floating
bubble in any app, talk normally, and polished text appears in the field you were
already using.

- **No word limits.** Ever. No weekly counters, no 5-minute sessions.
- **Private by default.** The text brain runs locally. Audio never leaves the
  device unless you deliberately enable a cloud provider.
- **Works everywhere.** Insertion happens through Android's accessibility layer,
  so it works in messaging apps, email, notes, browsers and editors.
- **Sounds like you.** Tone profiles, per-app tone memory, snippets with
  variables, a personal dictionary and voice commands.
- **Works where you are.** A full-screen dictation pad with a live waveform,
  plus the floating bubble for any other app.
- **Open source.** Apache-2.0. Audit it, fork it, ship it.

**Download:** [`sotto.apk`](https://github.com/desukab/sotto/releases/latest/download/sotto.apk)
(1.1.0, arm64-v8a, Android 8+, 709 KB).

## What's in 1.1.0

- **Dictation pad** — tap “Open dictation pad” for a live transcript with a real
  microphone waveform, then Insert / Copy / reshape to Formal, Casual or Notes.
- **Verified insertion** — Sotto pastes, re-reads the field, and falls back to
  `ACTION_SET_TEXT` if the app ignored the paste. Undo restores the exact
  previous value.
- **Per-app tone memory** — say “make it formal” in Gmail and it sticks, without
  changing your global tone.
- **Snippet variables** — `{date}`, `{time}`, `{day}`, `{datetime}`,
  `{clipboard}`.
- **Optional cloud polish** — bring your own OpenAI-compatible endpoint and key.
  Off by default; only the transcript text is ever sent, never audio.
- **Strict offline mode** — refuses to dictate instead of silently falling back
  to a networked recognizer. See [`docs/OFFLINE-ASR.md`](docs/OFFLINE-ASR.md).
- **Library transfer** — export and import snippets and dictionary as JSON.

## Why another dictation app?

The current generation of AI dictation tools are excellent at one thing and
expensive at another: they stream your microphone to a cloud model, cap the free
tier by the word, and lock formatting behind a subscription. Sotto takes the
opposite position:

| | Typical cloud dictation | Sotto |
|---|---|---|
| Core transcription | Cloud round-trip | Device recognizer (cloud optional) |
| Free tier | Weekly word cap | Unlimited |
| Session limit | 5 minutes | Unlimited |
| Latency | network + model | local |
| Privacy | audio uploaded | local by default |
| Price | subscription | free core, optional pro |
| Extensible | closed | Apache-2.0 |

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Android shell (Java)                  │
│  MainActivity · Settings · Library · History · Onboarding     │
│  SottoService (foreground + overlay bubble)                   │
│  SottoAccessibilityService (read focus, insert text)          │
│  SpeechRecognizer (on-device when available)                  │
└───────────────────────────┬──────────────────────────────────┘
                            │ JNI
┌───────────────────────────▼──────────────────────────────────┐
│                     Rust core (libsotto.so)                   │
│  normalizer · spoken punctuation · filler removal             │
│  dictionary · snippets · voice commands · tone engine         │
│  sentence casing · list formatting · stats                    │
└──────────────────────────────────────────────────────────────┘
```

The Rust crate is deliberately dependency-light and has no Android imports, so
the entire text pipeline is unit-testable on a normal machine.

## Build

### Termux / no Gradle (what this repo ships with)

```sh
./scripts/build.sh
```

Requires a JDK, Android SDK `android.jar`, `aapt2`, `dx`, `zipalign`,
`apksigner`, and a Rust toolchain with the `aarch64-linux-android` target.

### Azure / Android Studio

The `app/` directory is a plain Android module (manifest + `res/` + `src/`).
Import it into a Gradle project or point an Azure Pipeline at
`scripts/build.sh`. See [`docs/AZURE.md`](docs/AZURE.md).

## Status

Early. The complete v1 source, Rust core, build script and APK are in this
repository. See [`docs/TESTING.md`](docs/TESTING.md) for what is verified and
what still needs a physical-device pass.

## Documentation

- [`docs/WISPR-FLOW-DOSSIER.md`](docs/WISPR-FLOW-DOSSIER.md) — evidence-backed
  analysis of the reference product's architecture and behavior.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — how Sotto is put together.
- [`docs/OFFLINE-ASR.md`](docs/OFFLINE-ASR.md) — the offline recognizer path and
  how to bundle a model.
- [`docs/PRIVACY.md`](docs/PRIVACY.md) — data handling.
- [`docs/AZURE.md`](docs/AZURE.md) — build and deployment plan.
- [`docs/TESTING.md`](docs/TESTING.md) — verification log.

## License

Apache-2.0. See [`LICENSE`](LICENSE).
