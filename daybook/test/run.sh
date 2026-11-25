#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="$ANDROID_HOME"
bt=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
platform="$sdk/platforms/android-35/android.jar"
out=build/tests
mkdir -p "$out"/{classes,dex}
"$bt/aapt2" link -I "$platform" --manifest test/AndroidManifest.xml -o "$out/unsigned.apk"
javac -source 8 -target 8 -classpath "$platform:build/classes.jar" -d "$out/classes" test/Smoke.java
jar cf "$out/classes.jar" -C "$out/classes" .
"$bt/d8" --min-api 28 --lib "$platform" --classpath build/classes.jar --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && zip -q -0 ../unsigned.apk classes.dex)
keytool -genkeypair -keystore "$out/test.p12" -alias test -keyalg RSA -keysize 2048 -validity 2 -storepass android -keypass android -dname "CN=Ephemeral emulator tests" >/dev/null
for pair in "build/Daybook-unsigned.apk app" "$out/unsigned.apk test"; do
  read -r source label <<< "$pair"
  "$bt/apksigner" sign --ks "$out/test.p12" --ks-pass pass:android --out "$out/$label.apk" "$source"
  adb install -r "$out/$label.apk"
done
adb shell pm grant com.marcel.daybook android.permission.POST_NOTIFICATIONS
adb shell appops set com.marcel.daybook SCHEDULE_EXACT_ALARM allow
adb shell appwidget grantbind --package com.marcel.daybook
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am instrument -w com.marcel.daybook.test/com.marcel.daybook.Smoke | tee "$out/result.txt"
grep -q DAYBOOK_TESTS_PASSED "$out/result.txt"
