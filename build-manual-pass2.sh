#!/bin/bash
# Manual TryFit APK build - Pass 2: D8 dex + APK package + sign
set -e
set -o pipefail
ROOT=~/workspace/virtual-tryon-app
SDK=~/workspace/android-sdk/android
BT=$SDK/build-tools/35.0.1
ANDROID_JAR=$SDK/platforms/android-35/android.jar

echo "=== Jar app classes ==="
rm -rf $ROOT/.build/dex
mkdir -p $ROOT/.build/dex
cd $ROOT/.build/classes
jar cf $ROOT/.build/dex/app-classes.jar .
cd $ROOT
echo "=== D8 dex (app classes + all dependencies) ==="
# D8 inputs: app jar + deduplicated merged dependencies jar
D8INPUTS="$ROOT/.build/dex/app-classes.jar $ROOT/.manual-libs/deps-merged.jar"
timeout 900 $BT/d8 \
  --lib $ANDROID_JAR \
  --min-api 26 \
  --output $ROOT/.build/dex \
  $D8INPUTS 2>&1 | tail -25
echo "D8 done"
ls -lh $ROOT/.build/dex/

echo "=== Add dex to APK ==="
cp $ROOT/.build/apk/base.apk $ROOT/.build/apk/tryfit-unsigned.apk
python3 <<'PYEOF'
import zipfile, glob, os
apk = '/home/hatch/workspace/virtual-tryon-app/.build/apk/tryfit-unsigned.apk'
dexes = sorted(glob.glob('/home/hatch/workspace/virtual-tryon-app/.build/dex/*.dex'))
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    for d in dexes:
        # classes.dex, classes2.dex, ...
        arc = os.path.basename(d)
        z.write(d, arc)
        print("added", arc)
PYEOF

echo "=== Zipalign ==="
$BT/zipalign -f 4 $ROOT/.build/apk/tryfit-unsigned.apk $ROOT/.build/apk/tryfit-aligned.apk 2>&1 | tail -3

echo "=== Sign ==="
if [ ! -f $ROOT/.build/debug.keystore ]; then
  keytool -genkeypair -keystore $ROOT/.build/debug.keystore -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -2
fi
$BT/apksigner sign \
  --ks $ROOT/.build/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out $ROOT/.build/apk/TryFit-debug.apk \
  $ROOT/.build/apk/tryfit-aligned.apk 2>&1 | tail -5
echo "=== Verify ==="
$BT/apksigner verify --print-certs $ROOT/.build/apk/TryFit-debug.apk 2>&1 | head -5
ls -lh $ROOT/.build/apk/TryFit-debug.apk
