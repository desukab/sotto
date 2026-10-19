#!/usr/bin/env bash
# Dump the same observables used in docs/WISPR-FLOW-DOSSIER.md.
#
# Usage: ./scripts/analyze_apk.sh /path/to/base.apk
#
# Run this on your own copy. The script never uploads anything.
set -euo pipefail

APK="${1:-}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "usage: $0 <base.apk>" >&2
  exit 1
fi

OUT="${OUT:-analysis}"
mkdir -p "$OUT"
BASE="$OUT/observables.txt"
: > "$BASE"

echo "== package ==" >> "$BASE"
if command -v aapt2 >/dev/null 2>&1; then
  aapt2 dump badging "$APK" >> "$BASE" 2>&1 || true
  echo "== manifest ==" >> "$BASE"
  aapt2 dump xmltree --file AndroidManifest.xml "$APK" >> "$BASE" 2>&1 || true
else
  echo "aapt2 not found" >> "$BASE"
fi

echo "== files ==" >> "$BASE"
unzip -l "$APK" >> "$BASE" 2>&1 || true

echo "== dex strings ==" >> "$BASE"
TMP="$(mktemp -d)"
unzip -q -o "$APK" 'classes*.dex' -d "$TMP" 2>/dev/null || true
for dex in "$TMP"/classes*.dex; do
  [ -f "$dex" ] || continue
  strings -n 4 "$dex" >> "$BASE" || true
done
rm -rf "$TMP"

echo "wrote $BASE ($(wc -l < "$BASE") lines)"
echo
echo "Useful greps:"
echo "  grep -i 'grpc\\|transcri\\|format\\|opus\\|lpcm' $BASE"
echo "  grep -i 'style\\|language\\|word limit\\|snippet' $BASE"
