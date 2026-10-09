# Treinwijzer for Light Phone III

Treinwijzer is a free, Light-native planner and live journey companion for Dutch NS train travel: station search, departures, disruptions, planning, fares, transfer details, recovery journeys, favourites, Dutch/English copy and active-journey tracking.

- Tool ID: `nl.treinwijzer.lightphone`
- Tool version: `1.0.1` (version code `2`)
- Light SDK: `0.2.0`, with official upstream changes through `311aa2d81e03e7df2603fa3943d05e4aad51d29b`
- SDK upstream: [lightphone/light-sdk](https://github.com/lightphone/light-sdk)
- Backend source: [unequalsine/treinwijzer](https://github.com/unequalsine/treinwijzer)

Product code lives in `tool/`. SDK, plugin, builder, signer and example changes are imported from upstream without Treinwijzer patches.

**UNVALIDATED - requires emulator testing.** Automated checks passed, including a clean extracted release and the backend’s local Cloudflare runtime. Production deployment needs explicit approval; emulator validation and human screenshots are still required before submission. See [release preparation](docs/releasing.md).

## Development

Use JDK 17, Android SDK platform 36 and the included Gradle wrapper. SDK dependencies now resolve from public repositories, including JitPack; GitHub Packages credentials are no longer required.

Create an ignored `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
treinwijzer.workerBaseUrl=https://your-development-worker.example
treinwijzer.workerAccessToken=your-development-app-token
```

These Worker values affect **debug builds only**. Never commit credentials. Release builds select `https://treinwijzer-light.unequalsine.workers.dev` and contain no shared Worker token. The separate Light service reuses the watch backend code with its own storage and credentials. Deployment is pending approval; the watch service is unchanged.

```sh
./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest :tool:lintDebug :tool:assembleDebug
./gradlew check
```

Normal builds connect to `com.lightos`. For the LightOS emulator, pass `-Ptreinwijzer.emulator=true`; only the debug variant changes. [Simulator setup](docs/simulator.md) remains available, and `scripts/install-tool.sh` selects the emulator and production backend automatically. Add `--minified` to exercise resource/code shrinking on the emulator; no physical phone is needed for this check.

The official `:tool:uploadTool` task is also available for installation through Tool Manager; follow [Light's local installation instructions](docs/sideloading/README.md). Keep device authentication keys private.

Nearest stations request the SDK location permission and use a recent device fix. Distances are calculated locally; coordinates are not sent to the Worker. Favourites/preferences stay on the phone. Queries, installation credentials and tracked journeys are sent to the configured Worker, which obtains rail data from NS. This release does not register push endpoints or promise background notifications. Active journey changes are stored in an authenticated inbox and appear while open or on reopening. The first-run privacy screen explains external processing before any Treinwijzer backend request.

## Release and submission

Light builds and signs tools from public Git refs through the [developer dashboard](https://dashboard.thelightphone.com/). Its builder extracts only `tool/build.gradle.kts`, `tool/lighttool.toml` and allowlisted `tool/src/main/**` files into Light's own SDK workspace. Ignored files and local signing keys cannot supply release configuration.

```sh
./gradlew -DlightSdk.toolOnly=true -DlightSdk.unsigned=true -DlightSdk.abiFilters=arm64-v8a :tool:assembleRelease
```

This checks compilation/packaging; it does not create a Light-signed or approved tool. Follow [release preparation](docs/releasing.md), [Tool Library guidelines](TOOL_GUIDELINES.md) and [Light's submission instructions](https://github.com/lightphone/light-sdk#submitting-your-tool). External submission text and screenshots must be supplied by a human.

## SDK references

- [SDK documentation](docs/README.md)
- [Tool metadata and capabilities](docs/tool_metadata/README.md)
- [Tool signing](docs/tool_signing/README.md)
- [Local installation with Tool Manager](docs/sideloading/README.md)
- [Remaining SDK and backend gaps](docs/sdk-gaps.md)
