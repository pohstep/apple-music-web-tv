#!/usr/bin/env bash
# Build Apple Music Web (TV) without Gradle. Run from the repo root.
#
# Required environment:
#   JAVA_HOME     JDK 17 (needs javac and keytool)
#   ANDROID_HOME  Android SDK root (contains build-tools/ and platforms/)
# Optional:
#   BUILD_TOOLS   build-tools version        (default: 34.0.0)
#   PLATFORM      platform android.jar level (default: android-31)
#   KEYSTORE      signing keystore           (default: debug.keystore)
#   KS_PASS       keystore/key password      (default: android)
#   KS_ALIAS      key alias                  (default: androiddebugkey)
set -euo pipefail
cd "$(dirname "$0")"

: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 install}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
export PATH="$JAVA_HOME/bin:$PATH"

BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS:-34.0.0}"
AJ="$ANDROID_HOME/platforms/${PLATFORM:-android-31}/android.jar"
KEYSTORE="${KEYSTORE:-debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KS_ALIAS="${KS_ALIAS:-androiddebugkey}"
OUT="apple-music-web-2.2.2.apk"

# Create a debug keystore on first run (never commit it; it is git-ignored).
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

rm -rf build && mkdir -p build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 31 --version-code 7 --version-name 2.2.2 \
  --java build/gen build/res.zip
javac -source 8 -target 8 -Xlint:-options -cp "$AJ" -d build/classes \
  build/gen/dev/pohstep/applemusicweb/R.java src/dev/pohstep/applemusicweb/*.java
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile("build/unsigned.apk", "a") as z:
    z.write("build/dex/classes.dex", "classes.dex")
PY
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT" build/aligned.apk
"$BT/apksigner" verify "$OUT" && echo "verified"
sha256sum "$OUT" | tee "$OUT.sha256"
