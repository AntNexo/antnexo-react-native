#!/usr/bin/env bash
# Checks scripts/vendor.sh: the vendored tree is what the tags give, imports of AntNexoCore are gone, every file has
# its header, and --check catches a hand edit. Run from the repo root after scripts/vendor.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "vendor.test.sh: $*" >&2; exit 1; }

[[ -f ios/vendor/AntNexo/AntNexo.swift && -f ios/vendor/AntNexoCore/Bridge.swift ]] || fail "Swift sources missing"
[[ -f android/src/main/vendor/kotlin/com/antnexo/AntNexo.kt ]] || fail "Kotlin sources missing"
[[ -f android/src/main/vendor/res/values/strings.xml ]] || fail "Android resources missing"
grep -q 'com.antnexo.FeedbackActivity' android/src/main/AndroidManifest.xml || fail "manifest has no FeedbackActivity"
if grep -rqE 'import[[:space:]]+AntNexoCore|AntNexoCore\.' ios/vendor; then fail "AntNexoCore still referenced"; fi
while IFS= read -r -d '' f; do
  grep -q 'by scripts/vendor.sh. Do not edit.' "$f" || fail "no header in $f"
done < <(find ios/vendor android/src/main/vendor -type f \( -name '*.swift' -o -name '*.kt' -o -name '*.xml' \) -print0)
node -e 'const v=require("./vendor.json"); for (const k of ["swift","android"]) if (!/^[0-9a-f]{40}$/.test(v[k].commit)) process.exit(1)' ||
  fail "vendor.json lacks commits"

scripts/vendor.sh --check || fail "--check fails on a clean tree"
# the original is kept outside the vendored tree: an extra file there would fail --check by itself
f=android/src/main/vendor/kotlin/com/antnexo/AntNexo.kt
bak="$(mktemp)"
trap 'rm -f "$bak"' EXIT
cp "$f" "$bak"
echo "// hand edit" >>"$f"
if scripts/vendor.sh --check 2>/dev/null; then cp "$bak" "$f"; fail "--check missed a hand edit"; fi
cp "$bak" "$f"
scripts/vendor.sh --check || fail "--check fails after restoring"
echo "vendor.test.sh: ok"
