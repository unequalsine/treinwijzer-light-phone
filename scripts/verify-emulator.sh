#!/bin/sh
set -eu

ANDROID_SDK_PATH="${ANDROID_SDK_ROOT:-/Users/jeroen/Library/Android/sdk}"
ADB="$ANDROID_SDK_PATH/platform-tools/adb"

"$ADB" wait-for-device
echo "Build signing:"
"$ADB" shell getprop ro.build.description
echo "LightOS path:"
"$ADB" shell pm path com.thelightphone.sdk.emulator
echo "LightOS uid (must be 1000):"
"$ADB" shell dumpsys package com.thelightphone.sdk.emulator | grep 'uid='
echo "Treinwijzer package:"
"$ADB" shell pm path nl.treinwijzer.lightphone
echo "SDK version endpoint:"
"$ADB" forward tcp:8090 tcp:8090
curl --fail --silent --show-error http://127.0.0.1:8090/version
echo
