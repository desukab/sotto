#!/usr/bin/env bash
# Build Sotto without Gradle.
#
# Tested in Termux, where aapt2/dx/zipalign/apksigner are provided as packages.
# In CI, install Android build-tools (dx or d8) and set ANDROID_JAR.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app"
CORE="$ROOT/core"
BUILD="$ROOT/build"
ANDROID_JAR="${ANDROID_JAR:-/data/data/com.termux/files/home/asdk/android-35/android.jar}"
TARGET="${TARGET:-aarch64-linux-android}"
MIN_SDK="${MIN_SDK:-26}"
TARGET_SDK="${TARGET_SDK:-35}"
VERSION_NAME="${VERSION_NAME:-1.1.0}"
VERSION_CODE="${VERSION_CODE:-2}"

case "$TARGET" in
  aarch64-linux-android) ABI="arm64-v8a" ;;
  armv7-linux-androideabi|arm-linux-androideabi) ABI="armeabi-v7a" ;;
  x86_64-linux-android) ABI="x86_64" ;;
  i686-linux-android) ABI="x86" ;;
  *) ABI="$TARGET" ;;
esac

# Termux ships a native cargo; the rustup shim cannot lock its settings there.
if [ -x /data/data/com.termux/files/usr/bin/cargo ]; then
  export PATH="/data/data/com.termux/files/usr/bin:$PATH"
  export CARGO_HOME="${CARGO_HOME:-$HOME/.cargo-termux}"
  export RUSTUP_TOOLCHAIN=""
fi

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 1; }; }

need javac
need keytool
need zip
need unzip
[ -f "$ANDROID_JAR" ] || { echo "ANDROID_JAR not found: $ANDROID_JAR" >&2; exit 1; }

rm -rf "$BUILD"
mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/lib/$ABI" "$BUILD/dex"

echo "==> rust core"
(
  cd "$CORE"
  if command -v cargo >/dev/null 2>&1; then
    CARGO_HOME="${CARGO_HOME:-$HOME/.cargo-termux}" \
    RUSTUP_TOOLCHAIN="${RUSTUP_TOOLCHAIN:-}" \
    cargo build --release --target "$TARGET"
  else
    echo "cargo not found" >&2
    exit 1
  fi
)
cp "$CORE/target/$TARGET/release/libsotto.so" "$BUILD/lib/$ABI/libsotto.so"

echo "==> resources"
need aapt2
aapt2 compile --dir "$APP/res" -o "$BUILD/res.zip"
aapt2 link \
  -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "$BUILD/res.zip"

echo "==> java"
find "$APP/src" -name '*.java' > "$BUILD/sources.txt"
find "$BUILD/gen" -name '*.java' >> "$BUILD/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>&1 | grep -v "bootstrap class path\|source value 8\|target value 8\|deprecat" || true

if [ -f "$BUILD/classes/app/sotto/voice/MainActivity.class" ]; then
  :
else
  echo "javac failed" >&2
  exit 1
fi

echo "==> dex"
if command -v dx >/dev/null 2>&1; then
  dx --dex --min-sdk-version="$MIN_SDK" --output="$BUILD/classes.dex" "$BUILD/classes"
elif command -v d8 >/dev/null 2>&1; then
  d8 --lib "$ANDROID_JAR" --min-api "$MIN_SDK" --output "$BUILD/dex" "$BUILD/classes"/*.class
  # d8 needs the full class tree; pass it through find when available
  find "$BUILD/classes" -name '*.class' -print0 | xargs -0 d8 --lib "$ANDROID_JAR" \
    --min-api "$MIN_SDK" --output "$BUILD/dex"
  cp "$BUILD/dex/classes.dex" "$BUILD/classes.dex"
else
  echo "missing: dx or d8" >&2
  exit 1
fi

echo "==> package"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
mkdir -p "$BUILD/stage/lib/$ABI"
cp "$BUILD/lib/$ABI/libsotto.so" "$BUILD/stage/lib/$ABI/libsotto.so"
cp "$BUILD/classes.dex" "$BUILD/stage/classes.dex"
(cd "$BUILD/stage" && zip -q -r "$BUILD/unsigned.apk" .)

need zipalign
zipalign -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

if [ ! -f "$BUILD/sotto.keystore" ]; then
  keytool -genkeypair -v \
    -keystore "$BUILD/sotto.keystore" \
    -storepass android -keypass android \
    -alias sotto -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Sotto, O=Sotto, C=US" >/dev/null 2>&1
fi

need apksigner
apksigner sign \
  --ks "$BUILD/sotto.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias sotto \
  --out "$BUILD/sotto.apk" \
  "$BUILD/aligned.apk"

apksigner verify --print-certs "$BUILD/sotto.apk" >/dev/null
cp "$BUILD/sotto.apk" "$ROOT/sotto.apk"

echo
echo "built $ROOT/sotto.apk"
ls -lh "$ROOT/sotto.apk" "$BUILD/lib/$ABI/libsotto.so"
