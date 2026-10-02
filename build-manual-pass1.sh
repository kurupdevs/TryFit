#!/bin/bash
# Manual TryFit APK build (Gradle sandbox-blocked; see BUILD-MANUAL.md)
set -e
set -o pipefail
ROOT=~/workspace/virtual-tryon-app
SDK=~/workspace/android-sdk/android
TC=$ROOT/.manual-libs/toolchain
KSPCP=$(ls $ROOT/.manual-libs/ksp/*.jar | tr '\n' ':')
CP=$(cat $ROOT/.manual-libs/classpath.txt)
ANDROID_JAR=$SDK/platforms/android-35/android.jar
STDLIB=$TC/kotlin-stdlib-2.1.21.jar
KOTLINC_CP=$TC/kotlin-compiler-embeddable-2.1.21.jar:$STDLIB
# K2 compiler needs coroutines + trove on its own runtime classpath
COROUTINES_CORE=$(ls $ROOT/.manual-libs/jars/org.jetbrains.kotlinx_kotlinx-coroutines-core-jvm-1.10.1.jar)
TROVE_JAR=$(ls /tmp/kotlinc-dist/kotlinc/lib/trove4j.jar 2>/dev/null || echo "")
ANNOT_JAR=$(ls $ROOT/.manual-libs/jars/org.jetbrains_annotations-26.1.0.jar)
KOTLINC_CP=$KOTLINC_CP:$COROUTINES_CORE${TROVE_JAR:+:$TROVE_JAR}:$ANNOT_JAR

rm -rf $ROOT/.build/classes $ROOT/.build/ksp
mkdir -p $ROOT/.build/classes $ROOT/.build/ksp/{classes,java,kotlin,resources,caches} $ROOT/.build/dex

SOURCES=$(find $ROOT/app/src/main/java $ROOT/modules/tryon-engine/src/main/kotlin -name "*.kt" | tr '\n' ' ')

echo "=== kotlinc pass 1 (KSP: Room codegen) ==="
timeout 1200 java -Xmx4g -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect \
  -jvm-target 17 \
  -cp "$CP:$ANDROID_JAR:$STDLIB" \
  -Xplugin=$TC/kotlin-compose-compiler-plugin-embeddable-2.1.21.jar \
  -Xplugin=$TC/kotlin-serialization-compiler-plugin-embeddable-2.1.21.jar \
  -Xplugin=$TC/symbol-processing-2.1.21-2.0.2.jar \
  -Xplugin=$TC/symbol-processing-api-2.1.21-2.0.2.jar \
  -Xplugin=$TC/symbol-processing-common-deps-2.1.21-2.0.2.jar \
  -P plugin:com.google.devtools.ksp.symbol-processing:apclasspath=$KSPCP \
  -P plugin:com.google.devtools.ksp.symbol-processing:projectBaseDir=$ROOT \
  -P plugin:com.google.devtools.ksp.symbol-processing:classOutputDir=$ROOT/.build/ksp/classes \
  -P plugin:com.google.devtools.ksp.symbol-processing:javaOutputDir=$ROOT/.build/ksp/java \
  -P plugin:com.google.devtools.ksp.symbol-processing:kotlinOutputDir=$ROOT/.build/ksp/kotlin \
  -P plugin:com.google.devtools.ksp.symbol-processing:resourceOutputDir=$ROOT/.build/ksp/resources \
  -P plugin:com.google.devtools.ksp.symbol-processing:cachesDir=$ROOT/.build/ksp/caches \
  -P plugin:com.google.devtools.ksp.symbol-processing:kspOutputDir=$ROOT/.build/ksp \
  -P plugin:com.google.devtools.ksp.symbol-processing:incremental=false \
  -d $ROOT/.build/classes \
  $SOURCES $ROOT/.build/gen/com/kurupdevs/tryfit/R.java $ROOT/.build/gen/com/kurupdevs/tryfit/BuildConfig.java \
  2>&1 | tail -40
echo "PASS1_EXIT=$?"
