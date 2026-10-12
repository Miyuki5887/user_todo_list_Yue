#!/usr/bin/env bash
# Generates the release keystore and the keystore.properties that
# app/build.gradle.kts reads. Safe to re-run: it skips when the file exists.
set -euo pipefail

. "$(dirname "$0")/_env.sh"

KEYTOOL="${JAVA_HOME:-$ROOT/.tools/jdk-21}/bin/keytool"
STORE="$ROOT/keystore/yueji-release.jks"
PROPS="$ROOT/keystore.properties"

# These are throwaway defaults for local builds only. Export your own values
# (and keep keystore.properties out of version control) for a real release.
STOREPASS="${YUEJI_STOREPASS:-yueji2024}"
KEYPASS="${YUEJI_KEYPASS:-$STOREPASS}"
ALIAS="${YUEJI_KEYALIAS:-yueji}"
DNAME="${YUEJI_DNAME:-CN=YueJi, OU=Mobile, O=YueJi, L=Beijing, ST=Beijing, C=CN}"

mkdir -p "$ROOT/keystore"

if [ -f "$STORE" ]; then
  echo "keystore already present, reusing"
else
  "$KEYTOOL" -genkeypair -v \
    -keystore "$STORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$STOREPASS" -keypass "$KEYPASS" \
    -dname "$DNAME"
fi

cat > "$PROPS" <<EOF
storeFile=keystore/yueji-release.jks
storePassword=$STOREPASS
keyAlias=$ALIAS
keyPassword=$KEYPASS
EOF

echo "--- keystore.properties ---"
cat "$PROPS"
echo "--- keystore ---"
"$KEYTOOL" -list -keystore "$STORE" -storepass "$STOREPASS" | head -20
echo "KEYSTORE_OK"
