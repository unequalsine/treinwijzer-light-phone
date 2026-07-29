# Treinwijzer LightOS simulator

The local simulator is named `LightPhoneIII_API34` and uses the API 34 AOSP `default` ARM64 image at 1080 × 1240 and 420 dpi. It intentionally has no Play Store image.

## Prerequisites

- Android Studio and Android SDK platform tools.
- API 34 AOSP `default` ARM64 system image.
- Java 17 for this pinned SDK's Gradle toolchain.
- `cloudflared` for temporary simulator UnifiedPush delivery.
- The documented AOSP test `sdk/emulator/keys/platform.jks`.

## Commands

Use the scripts in `scripts/`:

- `start-emulator.sh` boots the AVD with a writable system partition.
- `install-lightos.sh` builds and installs LightOS as a privileged system app, sets it as launcher and disables Android transition animations.
- `install-tool.sh` assembles and installs Treinwijzer.
- `verify-emulator.sh` verifies the test-key image, LightOS system uid, launcher, SDK endpoint and discovered Treinwijzer package.
- `start-push-tunnel.sh` forwards emulator port 8090 and starts an HTTPS tunnel. Set the printed host as the emulator's `pushDomain`, then rebuild/reinstall LightOS.

The emulator's `pushDomain` is distribution-time state owned by the LightOS emulator. Temporary `trycloudflare.com` endpoints are accepted only by the `light-dev` Worker. Do not add such hosts to production configuration.
