#!/usr/bin/env bash
# Type-check shared code against Android 9. Only the isolated, version-gated adapters use a newer SDK.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AJ="$SDK/platforms/android-28/android.jar"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
mkdir -p out/api28
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" \
  -classpath "$AJ:out/classes" -d out/api28 \
  $(find app/src out/gen -name '*.java' ! -name DisplayLayer.java ! -name ShaderProgram.java ! -name Frost.java)
echo 'Common sources compile against Android API 28.'
