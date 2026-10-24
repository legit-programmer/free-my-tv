#!/usr/bin/env bash
# Emulator only: installs test builds and changes test preferences; never disables system packages.
set -euo pipefail
cd "$(dirname "$0")/.."
[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo 'Run only on an Android emulator.' >&2; exit 1; }
mkdir -p out/compat
original_home="$(adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
[[ "$original_home" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ ]] || { echo 'Cannot identify emulator launcher.' >&2; exit 1; }
trap 'adb shell cmd package set-home-activity --user 0 "$original_home" >/dev/null || true' EXIT
adb install -r FreeMyTV.apk
adb install -r out/smoke/tests.apk
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb logcat -c
adb shell am instrument -w io.freemytv.tests/io.freemytv.CompatSmoke | tee out/compat/result.txt
adb logcat -d -v threadtime > out/compat/logcat.txt
adb pull /sdcard/Android/data/io.freemytv/files/compat out/compat/screenshots
grep -q 'COMPAT_SMOKE_OK' out/compat/result.txt
if grep -E 'FATAL EXCEPTION|NoClassDefFoundError|NoSuchMethodError|VerifyError' out/compat/logcat.txt; then exit 1; fi
# Exercise the Android 9 package-manager command and put the emulator launcher back.
adb shell cmd package set-home-activity --user 0 io.freemytv/io.freemytv.HomeActivity | grep -q Success
adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | grep -q io.freemytv
adb shell cmd package set-home-activity --user 0 "$original_home" | grep -q Success
adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | grep -Fx "$original_home"
