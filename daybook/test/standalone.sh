#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bt=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
platform="$ANDROID_HOME/platforms/android-35/android.jar"
out=build/standalone-tests
mkdir -p "$out"/{classes,dex}
"$bt/aapt2" link -I "$platform" --manifest test/StandaloneManifest.xml -o "$out/unsigned.apk"
javac -source 8 -target 8 -classpath "$platform:build/classes.jar" -d "$out/classes" test/StandaloneSmoke.java
jar cf "$out/classes.jar" -C "$out/classes" .
"$bt/d8" --min-api 28 --lib "$platform" --classpath build/classes.jar --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && zip -q -0 ../unsigned.apk classes.dex)
"$bt/apksigner" sign --ks "$RUNNER_TEMP/standalone.jks" --ks-pass pass:standalone-build --out "$out/test.apk" "$out/unsigned.apk"
adb install -r build/Daybook-Standalone.apk
adb install -r "$out/test.apk"
adb shell pm grant com.marcel.daybookstandalone android.permission.POST_NOTIFICATIONS
adb shell appops set com.marcel.daybookstandalone SCHEDULE_EXACT_ALARM allow
adb shell wm dismiss-keyguard
adb shell am instrument -w com.marcel.daybookstandalone.test/com.marcel.daybook.StandaloneSmoke | tee "$out/result.txt"
grep -q DAYBOOK_STANDALONE_TESTS_PASSED "$out/result.txt"
