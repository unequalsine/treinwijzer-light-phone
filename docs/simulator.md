# Running Treinwijzer in the emulator

## Setup

Use JDK 17, Android SDK platform 36 and an API 34 AOSP `default` ARM64 system image without Google Play services. Create an AVD named `LightPhoneIII_API34` with a 1080 × 1240 screen and 420 dpi. Follow the [LightOS system-app setup](system_app/README.md) for the test platform signing key.

The scripts use `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) for the Android SDK. On macOS they default to `$HOME/Library/Android/sdk`. Set `JAVA_17_HOME` to your JDK 17 directory; the default is the Homebrew JDK 17 path on macOS.

```sh
./scripts/start-emulator.sh
./scripts/install-lightos.sh
./scripts/install-tool.sh
```

The first command opens the emulator. The second installs LightOS as its system launcher. The third builds and installs Treinwijzer using the public backend. Run the commands with only the intended emulator connected.

```sh
./scripts/install-tool.sh --minified
./scripts/verify-emulator.sh
```

The minified build tests code and resource shrinking. The verification script checks the LightOS installation, tool package and SDK version.

Normal builds target `com.lightos`. The installer uses `-Ptreinwijzer.emulator=true` for debug builds only and `-Ptreinwijzer.production=true` for public backend access. To use a development backend instead, omit the production flag and set `treinwijzer.workerBaseUrl` and `treinwijzer.workerAccessToken` in ignored `local.properties`. Release builds always use the public Light service without a shared app token.

Journey updates work while the tool is open, with unread alerts retrieved on reopening.

For a physical phone, use [Tool Manager](sideloading/README.md).
