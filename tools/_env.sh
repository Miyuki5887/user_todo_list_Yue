#!/usr/bin/env bash
# Shared paths for the helper scripts in this directory.
#
# Everything is derived from the repository location, so a checkout can live
# anywhere. Source it from a sibling script:
#
#     . "$(dirname "$0")/_env.sh"
#
# Every value can be overridden from the environment, e.g.
#     ADB=/path/to/adb DEV=emulator-5554 bash tools/demo-data.sh

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Windows spellings, needed because the Android SDK tools are .exe and because
# `adb push` sources must not be rewritten by Git Bash's path conversion.
ROOT_WIN="$(cd "$ROOT" && pwd -W 2>/dev/null || printf '%s' "$ROOT")"
ROOT_WIN_B="${ROOT_WIN//\//\\}"

ADB="${ADB:-$ROOT/.android-sdk/platform-tools/adb.exe}"
DEV="${DEV:-127.0.0.1:16416}"
PKG="${PKG:-com.yueji.memo}"

# Scratch and output locations, kept out of version control.
OUT="${OUT:-$ROOT/.shots}"
TMP="${TMP:-$ROOT/.tools/demo-tmp}"
WTMP="${WTMP:-$ROOT_WIN/.tools/demo-tmp}"

# Fail with a readable message instead of a cascade of adb errors.
require_device() {
  "$ADB" -s "$DEV" get-state >/dev/null 2>&1 && return 0
  echo "device '$DEV' is not reachable." >&2
  echo "Start the emulator, or pass a device id: $0 <device-id>" >&2
  exit 1
}
