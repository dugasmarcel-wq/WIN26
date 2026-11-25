#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK required}}
platform="$sdk/platforms/android-35/android.jar"
bt=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
out=build
rm -rf "$out"
mkdir -p "$out"/{gen,classes,dex}
"$bt/aapt2" compile --dir res -o "$out/res.zip"
"$bt/aapt2" link --manifest AndroidManifest.xml -I "$platform" --java "$out/gen" -A assets -o "$out/packed.apk" "$out/res.zip"
find src "$out/gen" -name '*.java' > "$out/sources"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$platform" -d "$out/classes" @"$out/sources"
jar cf "$out/classes.jar" -C "$out/classes" .
"$bt/d8" --min-api 28 --lib "$platform" --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && zip -q -0 ../packed.apk classes.dex)
"$bt/zipalign" -f 4 "$out/packed.apk" "$out/Daybook-unsigned.apk"
