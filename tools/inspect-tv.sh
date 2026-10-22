#!/usr/bin/env bash
# Read-only. Connect to the TV with adb first. Optional argument: adb serial.
set -euo pipefail
adb_cmd=(adb)
if [ "$#" -gt 0 ]; then adb_cmd+=(-s "$1"); fi
for prop in ro.product.brand ro.product.model ro.build.version.release ro.build.version.sdk; do
  printf '%s: ' "$prop"
  "${adb_cmd[@]}" shell getprop "$prop"
done
"${adb_cmd[@]}" shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
"${adb_cmd[@]}" shell settings get secure screensaver_components
"${adb_cmd[@]}" shell pm list packages -s
"${adb_cmd[@]}" shell pm list packages -d
"${adb_cmd[@]}" shell cat /proc/meminfo
