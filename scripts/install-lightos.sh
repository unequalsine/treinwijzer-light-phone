#!/bin/sh
set -eu

PROJECT_PATH=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
JAVA_17_PATH="${JAVA_17_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
ADB="$ANDROID_SDK_PATH/platform-tools/adb"
GRADLE_JAVA="$JAVA_17_PATH/bin/java"
GRADLE_WRAPPER="$PROJECT_PATH/gradle/wrapper/gradle-wrapper.jar"
EMULATOR_APK="$PROJECT_PATH/sdk/emulator/build/outputs/apk/debug/emulator-debug.apk"

wait_for_boot() {
  "$ADB" wait-for-device
  while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 1
  done
  while ! "$ADB" shell pm path android >/dev/null 2>&1; do
    sleep 1
  done
}

cd "$PROJECT_PATH"
"$GRADLE_JAVA" -classpath "$GRADLE_WRAPPER" org.gradle.wrapper.GradleWrapperMain :sdk:emulator:assembleDebug
wait_for_boot
"$ADB" root
if ! "$ADB" remount; then
  "$ADB" disable-verity
  "$ADB" reboot
  wait_for_boot
  "$ADB" root
  "$ADB" remount
fi
"$ADB" shell mkdir -p /system/priv-app/LightOSEmulator
"$ADB" push "$EMULATOR_APK" /system/priv-app/LightOSEmulator/LightOSEmulator.apk
"$ADB" reboot
wait_for_boot
"$ADB" shell cmd package set-home-activity com.thelightphone.sdk.emulator/.MainActivity
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
