#!/bin/bash
# Manual (Gradle-free) build of Daily Task v1.0 — Kotlin + Jetpack Compose, NO WebView.
set -e
set -o pipefail
export JAVA_HOME=~/workspace/.jdk
export PATH=$JAVA_HOME/bin:$PATH
APP=~/workspace/daily-task-app
MB=$APP/manual-build
BC=~/workspace/billcalc-native/manual-build
SDK=~/workspace/.android-sdk; BT=$SDK/build-tools/34.0.0
ANDROID_JAR=$SDK/platforms/android-34/android.jar
KEYSTORE=~/workspace/earning-apk-build/out/debug.keystore
KCE=/home/hatch/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.9.24/78dab849090e6c5e2eadb6e59a11cdc28fb67a08/kotlin-compiler-embeddable-1.9.24.jar
CCP=/home/hatch/.gradle/caches/modules-2/files-2.1/androidx.compose.compiler/compiler/1.5.14/78d62a968c6d23ded8476540f65b2ef850ddcaa7/compiler-1.5.14.jar

echo "=== 1. stage sources (clean) ==="
rm -rf $MB/stage $MB/gen $MB/compiled $MB/out
mkdir -p $MB/stage $MB/gen $MB/compiled $MB/out/classes $MB/out/dex
cp -r $APP/app/src/main/res $MB/stage/res
cp $APP/app/src/main/AndroidManifest.xml $MB/stage/
sed -i 's|<manifest xmlns:android=|<manifest package="com.zabii.dailytask" xmlns:android=|' $MB/stage/AndroidManifest.xml
cp -r $APP/app/src/main/java $MB/stage/src-java

echo "=== 2. aapt2 compile ==="
$BT/aapt2 compile --dir $MB/stage/res -o $MB/compiled/app-res.zip
for d in $BC/aar-res/*/; do
  name=$(basename "$d")
  if [ -n "$(ls -A "$d/res" 2>/dev/null)" ]; then
    $BT/aapt2 compile --dir "$d/res" -o "$MB/compiled/${name}.zip"
  fi
done

echo "=== 3. aapt2 link (versionCode 1 / versionName 1.0) ==="
$BT/aapt2 link -o $MB/out/app-unsigned.apk \
  -I $ANDROID_JAR \
  --manifest $MB/stage/AndroidManifest.xml \
  --java $MB/gen \
  --extra-packages "$(cat $BC/lib_packages.txt)" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 1 --version-name "1.0" \
  $MB/compiled/*.zip

echo "=== 4. sources ==="
find $MB/stage/src-java -name "*.kt" | sort > $MB/sources.txt
find $MB/gen -name "R.java" | sort >> $MB/sources.txt

echo "=== 5. kotlinc (classpath: android.jar FIRST, no -no-reflect) ==="
java -cp "$(cat $BC/kotlinc-bootcp.txt)" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin=$CCP -jvm-target 17 -no-stdlib \
  -cp "$ANDROID_JAR:$(tr '\n' ':' < $BC/lib_jars.txt):$MB/out/classes" \
  -d $MB/out/classes @$MB/sources.txt 2>&1 | grep -v "^warning:" | tail -8 || true

echo "=== 6. jar + d8 ==="
jar cf $MB/out/app-classes.jar -C $MB/out/classes .
$BT/d8 --min-api 24 --lib $ANDROID_JAR --output $MB/out/dex \
  $MB/out/app-classes.jar $(tr '\n' ' ' < $BC/lib_jars.txt)

echo "=== 7. zip dex, zipalign, sign ==="
cd $MB/out/dex && zip -j ../app-unsigned.apk classes*.dex > /dev/null && cd $MB
$BT/zipalign -f 4 $MB/out/app-unsigned.apk $MB/out/app-aligned.apk
$BT/apksigner sign --ks $KEYSTORE --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out $MB/out/DailyTask-v1.0.apk $MB/out/app-aligned.apk

echo "=== 8. verify ==="
$BT/apksigner verify $MB/out/DailyTask-v1.0.apk && echo "apksigner: PASS"
$BT/aapt dump badging $MB/out/DailyTask-v1.0.apk | grep -E "package:|application-label:|launchable-activity:|sdkVersion|targetSdk"
ls -la $MB/out/DailyTask-v1.0.apk
echo "BUILD OK"
