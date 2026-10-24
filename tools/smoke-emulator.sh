#!/usr/bin/env bash
# Emulator only: installs test builds and changes test preferences; never disables system packages.
set -euo pipefail
cd "$(dirname "$0")/.."
[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo 'Run only on an Android emulator.' >&2; exit 1; }
mkdir -p out/compat
resolve_home() {
  adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1
}
set_home() {
  local output
  printf 'Selecting HOME: %s\n' "$1" | tee -a out/compat/home-commands.txt
  for attempt in {1..10}; do
    output="$(adb shell cmd package set-home-activity --user 0 "$1" 2>&1)" || true
    printf '%s\n' "$output" | tee -a out/compat/home-commands.txt
    if [[ "$output" == *Success* ]]; then return 0; fi
    sleep 1
  done
  return 1
}
# Boot-completed can precede HOME package/role registration on fresh Google images.
original_home=""
for attempt in {1..30}; do
  candidate="$(resolve_home)"
  if [[ "$candidate" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$ && "$candidate" != *FallbackHome && "$candidate" != *ResolverActivity && "$candidate" != *ChooserActivity && "$candidate" != com.google.android.sdksetup/* ]]; then
    original_home="$candidate"
    break
  fi
  mapfile -t homes < <(adb shell cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | grep -E '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$' | grep -Ev 'FallbackHome$|ResolverActivity$|ChooserActivity$|^com\.google\.android\.sdksetup/')
  if [ "${#homes[@]}" = 1 ]; then original_home="${homes[0]}"; break; fi
  sleep 1
done
[ -n "$original_home" ] || { echo 'Cannot identify emulator launcher.' >&2; exit 1; }
set_home "$original_home"
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
# Exercise HOME selection and restore the original launcher, logging both responses.
set_home io.freemytv/io.freemytv.HomeActivity
resolve_home | tee -a out/compat/home-commands.txt | grep -q io.freemytv
set_home "$original_home"
resolve_home | tee -a out/compat/home-commands.txt | grep -Fx "$original_home"
