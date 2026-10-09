# SDK migration checks — 8 October 2026

Status: **UNVALIDATED - requires emulator testing**. No emulator display, screenshots or physical phone was accessed by AI.

## Automated evidence

- Imported official upstream source through `810ea0ad7cbf72b89022ff50c077b0bd4fdbeffc`, SDK `0.2.0`. All 417 source files under SDK, plugin, builder, signer, trust-format, examples and lint-rules match upstream byte for byte. SDK file changes are upstream imports, not custom patches.
- Tool debug unit tests: **35 passed**, including nearby-station ordering, invalid/old/inaccurate fixes and development/production credential isolation.
- Tool lint/debug assembly: passed. Three lint warnings remain: two existing Compose modifier-parameter ordering warnings and the generated manifest's missing application icon.
- Repository-wide `./gradlew check`: passed, including SDK, plugin, tool and examples. The final tool build script also passed these checks after importing upstream's unchanged `uploadTool` task. Task discovery confirms `:tool:uploadTool` is available; no upload was attempted.
- The official builder extractor accepted **12 files** from the tool. These were overlaid on a clean upstream SDK workspace without `local.properties` and built successfully as an unsigned arm64 release using `-DlightSdk.toolOnly=true -DlightSdk.unsigned=true -DlightSdk.abiFilters=arm64-v8a`.
- Release was deliberately built with `-Ptreinwijzer.emulator=true` as well. Its merged manifest still selects `com.lightos`, SDK `0.2.0`, package `nl.treinwijzer.lightphone`, code `2` and portrait orientation. Emulator debug selects `com.thelightphone.sdk.emulator`.
- Both the normal workspace (with private local configuration) and clean extracted workspace generate release configuration with the production URL and an empty shared token. Private debug values do not enter release configuration.
- The release APK packages only `arm64-v8a` native libraries and is unsigned. It is not a Light-signed or approved tool.
- Treinwijzer-specific whitespace checks and installer shell syntax passed.

The upstream release shrinker emits Kotlin metadata compatibility warnings with its current Kotlin/Android build-tool versions. The build succeeds, but a human must test the minified release as well as the debug build. Upstream SDK/toolchain files were left unchanged.

## Backend evidence and limits

A credential-free read of production `/stations` returned HTTP 401. Backend source inspected in the adjacent Treinwijzer repository at `136be08` requires the shared app token and lacks Light push/event-inbox endpoints. This source checkout was neither modified nor deployed. Deployed authenticated routes were not tested.

See [release preparation](releasing.md) for the required production access, push/inbox work, human tests and dashboard steps. The extraction build used the local JDK/Android toolchain, not Light's hosted Docker image or signing service. No hosted build or submission was performed.
