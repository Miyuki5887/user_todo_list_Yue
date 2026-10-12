#!/usr/bin/env bash
# Installs the Android SDK packages needed to build the app.
#
# The 16111833 cmdline-tools package ships a *shim* sdkmanager that tries to
# bootstrap a new "Android CLI" from the network and died with
# "gzip decompression failed: Peer disconnected". So we use the older
# 11076708 package, whose sdkmanager is a real, self-contained tool.
set -uo pipefail

. "$(dirname "$0")/_env.sh"

TOOLS="$ROOT/.tools"
SDK="$ROOT/.android-sdk"
WIN_SDK="$ROOT_WIN_B\\.android-sdk"
export JAVA_HOME="${JAVA_HOME:-$ROOT_WIN_B\\.tools\\jdk-21}"

CLT_VERSION=11076708
CLT_URL="https://dl.google.com/android/repository/commandlinetools-win-${CLT_VERSION}_latest.zip"

step() { echo; echo "########## $* ##########"; }

step "download cmdline-tools $CLT_VERSION"
cd "$TOOLS" || exit 1
if [ ! -f "clt-$CLT_VERSION.zip" ]; then
  curl -fL --retry 3 --retry-delay 3 -o "clt-$CLT_VERSION.zip" "$CLT_URL" || { echo "CLT DOWNLOAD FAILED"; exit 30; }
fi
ls -la "clt-$CLT_VERSION.zip"

step "extract"
rm -rf clt-old clt-oldtmp
mkdir -p clt-oldtmp
if command -v unzip >/dev/null 2>&1; then
  unzip -q -o "clt-$CLT_VERSION.zip" -d clt-oldtmp || { echo "UNZIP FAILED"; exit 31; }
else
  tar -xf "clt-$CLT_VERSION.zip" -C clt-oldtmp || { echo "EXTRACT FAILED"; exit 31; }
fi
ls clt-oldtmp/cmdline-tools

# Keep the broken package around under cmdline-tools/broken-16111833, but put
# the working one in .android-sdk/cmdline-tools/latest so ANDROID_HOME layout
# stays standard.
rm -rf "$SDK/cmdline-tools/latest-new"
mv clt-oldtmp/cmdline-tools "$SDK/cmdline-tools/latest-new" || { echo "MOVE FAILED"; exit 32; }
mv "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/broken-16111833" 2>/dev/null
mv "$SDK/cmdline-tools/latest-new" "$SDK/cmdline-tools/latest" || { echo "SWAP FAILED"; exit 33; }
rm -rf clt-oldtmp
ls "$SDK/cmdline-tools/latest/bin" | head

step "licenses"
SDKMGR="$SDK/cmdline-tools/latest/bin/sdkmanager.bat"
( yes 2>/dev/null | "$SDKMGR" --sdk_root="$WIN_SDK" --licenses ) >"$TOOLS/sdk-licenses.log" 2>&1
echo "licenses exit=$?"
ls "$SDK/licenses" 2>/dev/null

step "packages"
"$SDKMGR" --sdk_root="$WIN_SDK" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" 2>&1 | tail -25
echo "packages exit=$?"

step "RESULT"
ls "$SDK"
echo "--- platforms ---"; ls "$SDK/platforms" 2>/dev/null
echo "--- build-tools ---"; ls "$SDK/build-tools" 2>/dev/null
"$SDK/platform-tools/adb.exe" version 2>&1 | head -2
echo "SDK_OK"
