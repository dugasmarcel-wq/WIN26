#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
tag=win26-update-100141
meta=dayboox-update.json
code=$(jq -er '.versionCode|numbers' "$meta")
name="Dayboox-$code.apk"
base="https://github.com/dugasmarcel-wq/WIN26/releases/download/$tag"
test "$(jq -r '.apkUrl' "$meta")" = "$base/$name"
check=$(sha256sum "$name" | cut -d' ' -f1)
test "$check" = "$(jq -r '.sha256' "$meta")"
bt=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
"$bt/apksigner" verify --print-certs "$name" > "$RUNNER_TEMP/dayboox-certs"
grep -F 'certificate SHA-256 digest: 78f942aa7b29f5cc057401bfa61029b0e7affb5ac50c06136b2edd0ae09e0510' "$RUNNER_TEMP/dayboox-certs"
"$bt/aapt2" dump badging "$name" > "$RUNNER_TEMP/dayboox-package"
grep -F "package: name='com.marcel.dayboox' versionCode='$code'" "$RUNNER_TEMP/dayboox-package"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
gh release view "$tag" --json assets --jq '.assets[].name' > "$tmp/assets"
if grep -Fxq "$meta" "$tmp/assets"; then
  gh release download "$tag" -p "$meta" -D "$tmp"
  old=$(jq -er '.versionCode|numbers' "$tmp/$meta")
  test "$code" -ge "$old"
  if [ "$code" -eq "$old" ]; then test "$check" = "$(jq -r '.sha256' "$tmp/$meta")"; fi
fi
if grep -Fxq "$name" "$tmp/assets"; then
  gh release download "$tag" -p "$name" -D "$tmp"
  cmp "$name" "$tmp/$name"
else
  gh release upload "$tag" "$name"
fi
gh release upload "$tag" "$meta" --clobber
curl -fsSL --retry 5 --retry-all-errors "$base/$name" -o "$tmp/live.apk"
cmp "$name" "$tmp/live.apk"
curl -fsSL --retry 5 --retry-all-errors "$base/$meta?version=$code" -o "$tmp/live.json"
cmp "$meta" "$tmp/live.json"
