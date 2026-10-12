#!/usr/bin/env bash
# Walks the app's main screens on a connected device and saves one PNG per
# screen into .shots/. Coordinates are for the 1080x1920 MuMu profile.
set -uo pipefail
export MSYS_NO_PATHCONV=1

. "$(dirname "$0")/_env.sh"

DEV="${1:-$DEV}"
require_device

mkdir -p "$OUT"

shot() { # shot <name>
  sleep 3
  "$ADB" -s "$DEV" exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}

tap() { "$ADB" -s "$DEV" shell input tap "$1" "$2"; sleep 2; }
back() { "$ADB" -s "$DEV" shell input keyevent 4; sleep 2; }

"$ADB" -s "$DEV" shell am force-stop $PKG
"$ADB" -s "$DEV" shell am start -n $PKG/.MainActivity >/dev/null
shot 10-home

tap 285 1100;  shot 11-notes;   back
tap 795 1100;  shot 12-todos;   back
tap 285 1445;  shot 13-weekly;  back
tap 795 1445;  shot 14-settings; back

echo "TOUR_OK"
