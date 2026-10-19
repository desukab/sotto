# Sotto architecture

## Layers

1. **Shell (Java, `app/src`)** — Activity, settings, overlay bubble, foreground
   service, accessibility service, speech recognizer.
2. **Core (Rust, `core/`)** — text normalization, commands, tone, snippets,
   dictionary. Compiled into `libsotto.so` and called over JNI.
3. **Storage (`core/` Android classes)** — `SharedPreferences` for settings and
   stats; small JSON files for history, snippets and dictionary.

## Why Rust

The text pipeline is called on every final utterance and is the one place where
latency is visible to the user. Rust gives deterministic performance, no GC
pauses, and zero-cost string handling. It is also fully unit-testable on the
host without an emulator.

The Android UI stays in Java because Kotlin's compiler/Gradle toolchain is not
available in the Termux build environment used to produce the shipped APK. ART
executes Java and Kotlin through the same runtime, so there is no performance
difference in the shell. Every shell class is Kotlin-interoperable.

## Data flow

```
SpeechRecognizer (on-device preferred)
  ├─ partial results → bubble live text
  └─ final result
       ├─ Engine.detectCommand → execute (undo, send, new paragraph, …)
       └─ Engine.process
            ├─ protect URLs / emails / decimals
            ├─ spoken punctuation
            ├─ snippets + dictionary
            ├─ filler removal
            ├─ punctuation + casing
            ├─ tone profile
            └─ restore protected spans
       ├─ AccessibilityService.insertText
       ├─ HistoryStore.add
       └─ Settings.recordSession
```

## Insertion strategy

1. Find the focused editable node (`FOCUS_INPUT`, fallback BFS).
2. Snapshot the field text for undo.
3. Try clipboard + `ACTION_PASTE` (preserves selection/composing state).
4. Fall back to `ACTION_SET_TEXT` with the composed string.
5. If no field is focused, copy to clipboard and say so.

## Bubble

A `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`) so
it never steals focus from the target field. Drag to move, tap to toggle,
long-press to open the app. Position and size persist in preferences.

## Permissions and privacy

- Microphone: required to listen.
- Accessibility: required to insert into other apps; the service only reads the
  focused editable node and never stores screen content.
- Overlay: required for the bubble.
- Network: not used in the default configuration.

## Failure modes

- No recognizer installed → show a setup hint and keep working in "type/clean"
  mode.
- No accessibility service → dictation still works in-app and copies to
  clipboard.
- Process killed → `START_STICKY` + boot receiver restart the bubble when the
  user opted in.
