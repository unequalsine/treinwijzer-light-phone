#!/bin/sh
set -eu

PROJECT_PATH=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_SDK_PATH="${ANDROID_SDK_ROOT:-/Users/jeroen/Library/Android/sdk}"
JAVA_17_PATH="${JAVA_17_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
TOOL_APK="$PROJECT_PATH/tool/build/outputs/apk/debug/tool-debug.apk"

case "${1:-}" in
    "") ;;
    --minified) set -- -Ptreinwijzer.minifiedEmulator=true ;;
    *) echo "Usage: $0 [--minified]" >&2; exit 2 ;;
esac

cd "$PROJECT_PATH"
"$JAVA_17_PATH/bin/java" -classpath "$PROJECT_PATH/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain -DlightSdk.toolOnly=true -Ptreinwijzer.emulator=true -Ptreinwijzer.production=true "$@" :tool:assembleDebug
"$ANDROID_SDK_PATH/platform-tools/adb" install -r "$TOOL_APK"
