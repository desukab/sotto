# Azure / CI

Sotto deliberately has two build paths:

1. **`scripts/build.sh`** — no Gradle, works in Termux and on any Linux runner
   with an Android SDK. This is the path used to produce the release APK.
2. **Gradle / Android Studio** — for day-to-day development on Azure-hosted
   agents or a workstation.

## What a runner needs

| Tool | Version |
|---|---|
| JDK | 17+ |
| Android platform | `platforms;android-35` |
| Build tools | `build-tools;30.0.3` (has `dx`) or `build-tools;35.0.0` (uses `d8`) |
| Rust | stable + `aarch64-linux-android` target |
| Android NDK | r26+ (only for the linker) |

The script auto-detects `dx` or `d8`.

## GitHub Actions

`.github/workflows/ci.yml` runs the Rust tests and assembles a signed debug APK.
It installs build-tools 30.0.3 because that is the last package that still ships
`dx`; the script can use `d8` instead if you prefer a newer build-tools.

## Azure Pipelines

`azure-pipelines.yml` does the same on `ubuntu-latest`:

```yaml
- script: |
    rustup target add aarch64-linux-android
    export ANDROID_JAR=$ANDROID_SDK_ROOT/platforms/android-35/android.jar
    export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang
    ./scripts/build.sh
```

Publish `sotto.apk` as a pipeline artifact.

### Release signing

The script creates a throwaway keystore on first run. For a real release, put
your keystore in a pipeline secret and pass it explicitly:

```sh
keytool -genkeypair -keystore release.keystore -alias sotto ...
base64 -w0 release.keystore   # store as KEYSTORE_B64
```

Then in the pipeline:

```sh
echo "$KEYSTORE_B64" | base64 -d > build/sotto.keystore
./scripts/build.sh
```

For Play Store delivery add a `bundletool` step once a Gradle module is in
place.

## Gradle migration

The `app/` tree is a standard Android module (`AndroidManifest.xml`, `res/`,
`src/`). To use Gradle:

1. Create `settings.gradle.kts` and a root `build.gradle.kts`.
2. Point `sourceSets` at `app/src` and `app/res`.
3. Add the Rust build as a `Exec` task that copies `libsotto.so` into
   `src/main/jniLibs/arm64-v8a/`.
4. Set `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, Java 8
   compatibility.

Keep the Rust target at `aarch64-linux-android` (most phones) and add
`armv7-linux-androideabi` if you need older 32-bit devices.

## Cost note

The app has no server component. The only Azure cost is CI minutes and,
optionally, a Static Web App for the landing page in `docs/`.
