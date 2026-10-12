#!/usr/bin/env bash
# Downloads a standalone Gradle distribution into .tools (workspace-local,
# so no writes escape the sandbox and no system install is needed).
set -uo pipefail

. "$(dirname "$0")/_env.sh"

TOOLS="$ROOT/.tools"
GRADLE_VER=8.11.1
mkdir -p "$TOOLS"
cd "$TOOLS" || exit 1

if [ -x "$TOOLS/gradle-$GRADLE_VER/bin/gradle" ]; then
  echo "gradle $GRADLE_VER already present"
else
  curl -fL --retry 3 --retry-delay 3 -o gradle.zip \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip" \
    || { echo "GRADLE DOWNLOAD FAILED"; exit 40; }
  rm -rf "gradle-$GRADLE_VER"
  unzip -q -o gradle.zip || { echo "GRADLE EXTRACT FAILED"; exit 41; }
  rm -f gradle.zip
fi

export JAVA_HOME='D:\Todolist\.tools\jdk-21'
"$TOOLS/gradle-$GRADLE_VER/bin/gradle" --version 2>&1 | head -12
echo "GRADLE_OK"
