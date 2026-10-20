#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

die() {
    echo "error: $*" >&2
    exit 1
}

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
    *) EXE="" ;;
esac

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || die "set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK"

if [ -n "${BUILD_TOOLS:-}" ]; then
    BT="$BUILD_TOOLS"
elif [ -x "$SDK/build-tools/34.0.0/aapt2$EXE" ]; then
    BT="$SDK/build-tools/34.0.0"
else
    BT=""
    for candidate in "$SDK"/build-tools/*; do
        [ -x "$candidate/aapt2$EXE" ] && BT="$candidate"
    done
fi
[ -x "${BT:-}/aapt2$EXE" ] || die "no Android build-tools found; set BUILD_TOOLS"

PLATFORM="${ANDROID_PLATFORM:-$SDK/platforms/android-34/android.jar}"
[ -f "$PLATFORM" ] || die "android.jar not found: $PLATFORM (set ANDROID_PLATFORM)"

for tool in java javac jar keytool; do
    command -v "$tool" >/dev/null 2>&1 || die "$tool not found; install JDK 17"
done

VERSION_NAME=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -n1)
[ -n "$VERSION_NAME" ] || die "cannot read versionName from AndroidManifest.xml"

BUILD="build"
OUT="dist"
rm -rf "$BUILD" "$OUT"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT"

"$BT/aapt2$EXE" compile --dir res -o "$BUILD/res.zip"
"$BT/aapt2$EXE" link \
    -o "$BUILD/base.apk" \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    -R "$BUILD/res.zip" \
    --java "$BUILD/gen" \
    --auto-add-overlay

find src "$BUILD/gen" -name '*.java' -print0 | xargs -0 \
    javac --release 8 -Xlint:-options -classpath "$PLATFORM" -d "$BUILD/classes"

jar cf "$BUILD/classes.jar" -C "$BUILD/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 21 \
    --lib "$PLATFORM" \
    --output "$BUILD/dex" \
    "$BUILD/classes.jar"

(cd "$BUILD/dex" && jar uf ../base.apk classes.dex)
"$BT/zipalign$EXE" -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

if [ -n "${PERCH_KEYSTORE:-}" ]; then
    : "${PERCH_KS_PASS:?set PERCH_KS_PASS}"
    : "${PERCH_KEY_ALIAS:?set PERCH_KEY_ALIAS}"
    KEY_PASS="${PERCH_KEY_PASS:-$PERCH_KS_PASS}"
    SIGNED="$OUT/perch-$VERSION_NAME.apk"
else
    mkdir -p signing
    PERCH_KEYSTORE="signing/debug.keystore"
    PERCH_KS_PASS="android"
    PERCH_KEY_ALIAS="androiddebugkey"
    KEY_PASS="android"
    if [ ! -f "$PERCH_KEYSTORE" ]; then
        keytool -genkeypair -noprompt \
            -keystore "$PERCH_KEYSTORE" \
            -storetype JKS \
            -alias "$PERCH_KEY_ALIAS" \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass "$PERCH_KS_PASS" -keypass "$KEY_PASS" \
            -dname "CN=Perch Debug, OU=Debug, O=Perch, C=US"
    fi
    SIGNED="$OUT/perch-$VERSION_NAME-debug.apk"
fi

java -jar "$BT/lib/apksigner.jar" sign \
    --ks "$PERCH_KEYSTORE" \
    --ks-key-alias "$PERCH_KEY_ALIAS" \
    --ks-pass "pass:$PERCH_KS_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --min-sdk-version 21 \
    --out "$SIGNED" \
    "$BUILD/aligned.apk"

java -jar "$BT/lib/apksigner.jar" verify --verbose --min-sdk-version 21 "$SIGNED"
echo
echo "APK: $SIGNED"
