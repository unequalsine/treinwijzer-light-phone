#!/bin/sh
set -eu

ANDROID_SDK_PATH="${ANDROID_SDK_ROOT:-/Users/jeroen/Library/Android/sdk}"
exec "$ANDROID_SDK_PATH/emulator/emulator" \
  -avd LightPhoneIII_API34 \
  -writable-system \
  -no-snapshot-load \
  -no-snapshot-save
