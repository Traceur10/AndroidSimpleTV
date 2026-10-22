#!/usr/bin/env bash
# Install and open only AndroidSimpleTV. Never change the default home or system packages.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 DEVICE_SERIAL [APK_PATH]" >&2
  exit 2
fi
serial=$1
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
apk=${2:-"$repo_dir/app/build/outputs/apk/debug/app-debug.apk"}
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }
[[ $(adb -s "$serial" get-state) == device ]] || exit 1
home_before=$(adb -s "$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r')
echo "Current Home: $home_before"
adb -s "$serial" install -r "$apk"
adb -s "$serial" shell am start -W -n com.traceur.androidsimpletv/.MainActivity
home_after=$(adb -s "$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r')
if [[ "$home_before" != "$home_after" ]]; then
  echo "Home resolution changed after installation; inspect TV settings before continuing. No default was set by this script." >&2
  exit 1
fi
echo "Home resolution unchanged. Use the remote Home button to return to your existing launcher."
