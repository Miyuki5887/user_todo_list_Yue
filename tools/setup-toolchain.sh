#!/usr/bin/env bash
# Toolchain bootstrap for the YueJi Android app.
# Installs into the workspace root so nothing escapes the sandbox:
#   .tools/jdk-21        Temurin JDK 21 (AGP-friendly)
#   .android-sdk         Android SDK (cmdline-tools, platform-tools, platforms, build-tools)
# Logs progress to stdout so the background job can be tailed.
set -uo pipefail

. "$(dirname "$0")/_env.sh"

TOOLS="$ROOT/.tools"
SDK="$ROOT/.android-sdk"
WIN_ROOT="$ROOT_WIN_B"
WIN_SDK="$WIN_ROOT\\.android-sdk"
WIN_JDK="$WIN_ROOT\\.tools\\jdk-21"

mkdir -p "$TOOLS" "$SDK"
cd "$TOOLS" || exit 1

extract() { # extract <archive> <destdir>
  local a="$1" d="$2"
  mkdir -p "$d"
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$a" -d "$d"; return $?; fi
  if tar -xf "$a" -C "$d" 2>/dev/null; then return 0; fi
  powershell -NoProfile -Command "Expand-Archive -LiteralPath '$a' -DestinationPath '$d' -Force" >/dev/null 2>&1
}

step() { echo; echo "########## $* ##########"; }

# ---------------------------------------------------------------- JDK 21
step "JDK 21"
if [ -x "$TOOLS/jdk-21/bin/java.exe" ]; then
  echo "already present"
else
  curl -fL --retry 3 --retry-delay 3 -o jdk21.zip \
    "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" \
    || { echo "JDK DOWNLOAD FAILED"; exit 20; }
  rm -rf jdk21tmp && extract jdk21.zip jdk21tmp || { echo "JDK EXTRACT FAILED"; exit 21; }
  mv jdk21tmp/jdk-21* "$TOOLS/jdk-21" || { echo "JDK MOVE FAILED"; exit 22; }
  rm -rf jdk21tmp jdk21.zip
fi
"$TOOLS/jdk-21/bin/java.exe" -version 2>&1 | head -2

# ------------------------------------------------------- cmdline-tools
step "cmdline-tools"
CLT="$SDK/cmdline-tools/latest"
if [ -x "$CLT/bin/sdkmanager.bat" ]; then
  echo "already present"
else
  curl -fL --retry 3 --retry-delay 3 -o clt.zip \
    "https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip" \
    || { echo "CLT DOWNLOAD FAILED"; exit 30; }
  rm -rf clt-tmp && extract clt.zip clt-tmp || { echo "CLT EXTRACT FAILED"; exit 31; }
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$CLT"
  mv clt-tmp/cmdline-tools "$CLT" || { echo "CLT MOVE FAILED"; exit 32; }
  rm -rf clt-tmp clt.zip
fi
ls "$CLT/bin" | head

# ------------------------------------------------------------ packages
step "sdkmanager licenses + packages"
export JAVA_HOME="$WIN_JDK"
SDKMGR="$CLT/bin/sdkmanager.bat"
yes 2>/dev/null | "$SDKMGR" --sdk_root="$WIN_SDK" --licenses >"$TOOLS/sdk-licenses.log" 2>&1
echo "licenses exit=$? (log: .tools/sdk-licenses.log)"
"$SDKMGR" --sdk_root="$WIN_SDK" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" 2>&1 | tail -20
echo "packages exit=$?"

step "RESULT"
ls "$SDK" 2>/dev/null
"$SDK/platform-tools/adb.exe" version 2>&1 | head -2
echo "TOOLCHAIN_OK"
