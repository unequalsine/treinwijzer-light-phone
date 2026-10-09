# Treinwijzer LightOS simulator

The local simulator is named `LightPhoneIII_API34` and uses the API 34 AOSP `default` ARM64 image at 1080 × 1240 and 420 dpi. It intentionally has no Play Store image.

## Prerequisites

- Android Studio and Android SDK platform tools.
- API 34 AOSP `default` ARM64 system image.
- Java 17 and Android SDK platform 36 for the updated SDK's Gradle toolchain.
- `cloudflared` for temporary simulator UnifiedPush delivery.
- The documented AOSP test `sdk/emulator/keys/platform.jks`.

## Commands

Use the scripts in `scripts/`:

- `start-emulator.sh` boots the AVD with a writable system partition.
- `install-lightos.sh` builds and installs LightOS as a privileged system app, sets it as launcher and disables Android transition animations.
- `install-tool.sh` assembles and installs Treinwijzer with emulator binding and production backend access. Use `--minified` for the shrinking check. Production deployment must be approved and verified first. Normal builds and all release builds target `com.lightos`; the emulator option affects debug only.
- `verify-emulator.sh` verifies the test-key image, LightOS system uid, launcher, SDK endpoint and discovered Treinwijzer package.
- `start-push-tunnel.sh` forwards emulator port 8090 and starts an HTTPS tunnel. Set the printed host as the emulator's `pushDomain`, then rebuild/reinstall LightOS.

The emulator's `pushDomain` is distribution-time state owned by the LightOS emulator. The current release does not register or send to push endpoints. Keep temporary tunnel domains out of production; the old development transport is not evidence of encrypted public delivery.

Normal debug Worker configuration comes from ignored `local.properties`. The installer adds `-Ptreinwijzer.production=true`, which ignores those private debug values and selects public production access. Release always selects production without a shared token. This release disables push registration and relies on foreground updates plus the durable alert inbox. See [release preparation](releasing.md).

On a physical phone, use the supported [Tool Manager installation flow](sideloading/README.md). This project has not been validated visually by AI; a human must perform the emulator and device checks.
