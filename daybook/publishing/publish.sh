#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
tag=win26-update-100141
meta=daybook-update.json
code=$(jq -er '.versionCode|numbers' "$meta")
name="Daybook-$code.apk"
base="https://github.com/dugasmarcel-wq/WIN26/releases/download/$tag"
test "$(jq -r '.apkUrl' "$meta")" = "$base/$name"
check=$(sha256sum "$name" | cut -d' ' -f1)
test "$check" = "$(jq -r '.sha256' "$meta")"
bt=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
"$bt/apksigner" verify --print-certs "$name" > "$RUNNER_TEMP/daybook-certs"
grep -F 'certificate SHA-256 digest: a220d3397070350e68a8f3d3241c66fcda8b676581938d0745fe73ae0372a19f' "$RUNNER_TEMP/daybook-certs"
"$bt/aapt2" dump badging "$name" > "$RUNNER_TEMP/daybook-package"
grep -F "package: name='com.marcel.daybook' versionCode='$code'" "$RUNNER_TEMP/daybook-package"
tmp=$(mktemp -d)
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
# Check the actual public endpoints before treating publication as successful.
curl -fsSL --retry 5 --retry-all-errors "$base/$name" -o "$tmp/live.apk"
cmp "$name" "$tmp/live.apk"
curl -fsSL --retry 5 --retry-all-errors "$base/$meta?version=$code" -o "$tmp/live.json"
cmp "$meta" "$tmp/live.json"
