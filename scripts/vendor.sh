#!/usr/bin/env bash
# Copies the native SDKs' sources at the tags in vendor.json into this package (ADR 0079 in the AntNexo monorepo):
#   ios/vendor/AntNexo{,Core}/*.swift      from AntNexo/antnexo-swift   Sources/AntNexo{,Core}
#   android/src/main/vendor/{kotlin,res}   from AntNexo/antnexo-android antnexo/src/main/{kotlin,res}
#   android/src/main/AndroidManifest.xml   from AntNexo/antnexo-android antnexo/src/main/AndroidManifest.xml
# and records the tags' commits in vendor.json. Never edit the vendored files by hand.
#
#   scripts/vendor.sh           write them
#   scripts/vendor.sh --check   exit 1 when the repo differs from what the tags give
# ANTNEXO_SWIFT_REPO / ANTNEXO_ANDROID_REPO override the clone URLs (tests, offline work).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
check=0
[[ "${1:-}" == "--check" ]] && check=1

swift_repo="${ANTNEXO_SWIFT_REPO:-https://github.com/AntNexo/antnexo-swift.git}"
android_repo="${ANTNEXO_ANDROID_REPO:-https://github.com/AntNexo/antnexo-android.git}"
swift_tag="$(node -p 'require(process.argv[1]).swift.tag' "$root/vendor.json")"
android_tag="$(node -p 'require(process.argv[1]).android.tag' "$root/vendor.json")"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

clone() { # repo tag dir
  git -c advice.detachedHead=false clone -q --depth 1 --branch "$2" "$1" "$3"
  git -C "$3" rev-parse HEAD
}
swift_sha="$(clone "$swift_repo" "$swift_tag" "$work/swift")"
android_sha="$(clone "$android_repo" "$android_tag" "$work/android")"

# The tree to write: the repo itself, or a copy to compare with it.
out="$root"
if [[ $check == 1 ]]; then
  out="$work/out"
  mkdir -p "$out"
fi

header() { # file repo tag sha
  local f="$1" line="Vendored from AntNexo/$2 $3 ($4) by scripts/vendor.sh. Do not edit."
  case "$f" in
    *.swift | *.kt) { printf '// %s\n' "$line"; cat "$f"; } >"$f.tmp" ;;
    *.xml)
      if head -1 "$f" | grep -q '^<?xml'; then
        { head -1 "$f"; printf '<!-- %s -->\n' "$line"; tail -n +2 "$f"; } >"$f.tmp"
      else
        { printf '<!-- %s -->\n' "$line"; cat "$f"; } >"$f.tmp"
      fi
      ;;
    *) return 0 ;;
  esac
  mv "$f.tmp" "$f"
}

# iOS: the two Swift modules become one (the pod's), so their imports of each other go and a module-qualified name
# points at the pod's module.
ios="$out/ios/vendor"
rm -rf "$ios"
mkdir -p "$ios"
for m in AntNexoCore AntNexo; do
  cp -R "$work/swift/Sources/$m" "$ios/$m"
done
while IFS= read -r -d '' f; do
  perl -ne 'next if /^\s*(\@_exported\s+|(?:public|package|internal|fileprivate|private)\s+)?import\s+AntNexoCore\b/; s/\bAntNexoCore\./AntNexoReactNative./g; print' "$f" >"$f.tmp"
  mv "$f.tmp" "$f"
  header "$f" antnexo-swift "$swift_tag" "$swift_sha"
done < <(find "$ios" -type f -name '*.swift' -print0)

# Android: unchanged; the library's namespace is com.antnexo, like the SDK's.
and="$out/android/src/main"
rm -rf "$and/vendor"
mkdir -p "$and/vendor"
cp -R "$work/android/antnexo/src/main/kotlin" "$and/vendor/kotlin"
cp -R "$work/android/antnexo/src/main/res" "$and/vendor/res"
cp "$work/android/antnexo/src/main/AndroidManifest.xml" "$and/AndroidManifest.xml"
while IFS= read -r -d '' f; do
  header "$f" antnexo-android "$android_tag" "$android_sha"
done < <(find "$and/vendor" "$and/AndroidManifest.xml" -type f -print0)

node -e '
const fs = require("fs");
const [file, st, ss, at, as] = process.argv.slice(1);
const v = { swift: { tag: st, commit: ss }, android: { tag: at, commit: as } };
fs.writeFileSync(file, JSON.stringify(v, null, 2) + "\n");
' "$out/vendor.json" "$swift_tag" "$swift_sha" "$android_tag" "$android_sha"

if [[ $check == 1 ]]; then
  status=0
  for p in ios/vendor android/src/main/vendor android/src/main/AndroidManifest.xml vendor.json; do
    if ! d="$(diff -r -x .DS_Store "$out/$p" "$root/$p" 2>&1)"; then
      echo "vendor.sh --check: $p differs from $swift_tag / $android_tag (run scripts/vendor.sh):" >&2
      echo "$d" | head -20 >&2
      status=1
    fi
  done
  exit $status
fi
echo "vendored antnexo-swift $swift_tag ($swift_sha), antnexo-android $android_tag ($android_sha)"
