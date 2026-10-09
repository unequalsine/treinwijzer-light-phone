#!/bin/sh
set -eu

ANDROID_SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
"$ANDROID_SDK_PATH/platform-tools/adb" forward tcp:8090 tcp:8090
echo "Copy the generated https://*.trycloudflare.com URL to pushDomain in local.properties, then reinstall LightOS."
exec cloudflared tunnel --url http://127.0.0.1:8090
