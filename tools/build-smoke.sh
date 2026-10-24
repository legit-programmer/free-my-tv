#!/usr/bin/env bash
# Build a separate, locally signed instrumentation APK. Never shipped in the app.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$SDK/platforms/android-34/android.jar"
mkdir -p out/smoke/classes
"$BT/aapt2" link -o out/smoke/base.apk -I "$AJ" --manifest tests/android/AndroidManifest.xml --min-sdk-version 28 --target-sdk-version 33
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" \
  -classpath "$AJ:out/classes" -d out/smoke/classes $(find tests/android -name '*.java')
"$BT/d8" --release --min-api 28 --lib "$AJ" --classpath out/classes --output out/smoke $(find out/smoke/classes -name '*.class')
(cd out/smoke && zip -qj base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/smoke/base.apk out/smoke/aligned.apk
"$BT/apksigner" sign --ks freemytv.keystore --ks-pass pass:freemytv --out out/smoke/tests.apk out/smoke/aligned.apk
