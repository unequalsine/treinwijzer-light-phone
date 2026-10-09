# Treinwijzer

### Catch your train. Keep your focus.

A free, open-source train planner for **Light Phone III**. Plan your trip, find your platform and follow your journey across the Netherlands — in a simple interface built for LightOS.

**Dutch rail travel. Dutch and English. Made to travel light.**

[Explore the tool](tool/src/main/kotlin/nl/treinwijzer/lightphone) · [Try it in the emulator](docs/simulator.md) · [Report a bug](https://github.com/unequalsine/treinwijzer-light-phone/issues) · [Release status](docs/releasing.md)

---

## From the first platform to the last connection

- **Plan your journey.** Compare travel options, departure and arrival times, transfers and available fare information.
- **Find your next train.** Look up stations, live departures, platforms and train details. Keep favourites and recent stations close at hand.
- **Know what has changed.** Check disruptions and follow an active journey as rail information updates.
- **Make your connection.** See each stop and transfer in a journey timeline, with platform details and alternatives when plans change.
- **Find a station nearby.** Use the phone’s location, with your permission, to rank nearby stations.
- **Choose your language.** The interface is available in Dutch and English.

Treinwijzer focuses on the information you need to get there. No ads, no paywall and no account to create.

## Coming to the LightOS Tool Library

This is the public source repository for Treinwijzer’s participation in the **LightOS community tool programme**. Built with the official [Light SDK](https://github.com/lightphone/light-sdk), it uses Light’s UI components and navigation to feel at home on your phone.

**Current status: 1.0.1 release candidate.** Automated tool and packaging checks have passed, and the dedicated Light backend is live. Final human emulator checks are still pending, including the latest layout fixes. Treinwijzer has **not yet been submitted, signed or approved by Light**, and is not yet available in the Tool Library.

Journey updates appear while the tool is open. Unread journey alerts can be recovered when you reopen it; this candidate does not deliver background notifications.

See the [release checklist](docs/releasing.md) and [validation record](docs/validation-2026-10-09.md). **UNVALIDATED - requires emulator testing.**

## Privacy, in plain language

Favourites and preferences stay on your phone. Nearby-station distances are calculated locally; your coordinates are not sent to the Treinwijzer backend.

Station searches, journey requests and active tracking are processed by the Treinwijzer service on Cloudflare, which obtains rail information from NS. The tool creates an installation identity automatically to protect your tracking and alert inbox. A first-run screen explains this before any backend request.

The Light service has its own storage and credentials. It shares backend code with [Treinwijzer for Apple Watch](https://github.com/unequalsine/treinwijzer), while keeping the two apps’ installations and journeys separate.

## Build and explore

You’ll need **JDK 17**, **Android SDK platform 36** and the included Gradle wrapper. Dependencies resolve from public repositories; GitHub Packages credentials are not required.

```sh
git clone https://github.com/unequalsine/treinwijzer-light-phone.git
cd treinwijzer-light-phone
```

Create an ignored `local.properties` with your Android SDK path:

```properties
sdk.dir=/path/to/Android/sdk
```

Build a debug tool against the public Light backend:

```sh
./gradlew -DlightSdk.toolOnly=true -Ptreinwijzer.production=true :tool:testDebugUnitTest :tool:lintDebug :tool:assembleDebug
```

For the emulator, follow the [setup guide](docs/simulator.md), then install:

```sh
./scripts/install-tool.sh
# Exercise the candidate with code and resource shrinking:
./scripts/install-tool.sh --minified
```

The installer selects the emulator and public backend automatically. Its environment can be configured with `ANDROID_SDK_ROOT` and `JAVA_17_HOME`. Normal builds target `com.lightos`; `-Ptreinwijzer.emulator=true` changes only debug builds. For a physical phone, use [Light’s Tool Manager installation flow](docs/sideloading/README.md).

To use your own development backend, add `treinwijzer.workerBaseUrl` and `treinwijzer.workerAccessToken` to ignored `local.properties` and omit the production flag. Those overrides affect debug only. Never commit credentials. Release configuration contains the dedicated public Light endpoint and no shared app token.

## Help shape the tool

Bug reports, Dutch/English copy improvements, accessibility feedback and thoughtful ideas for simpler train travel are welcome. [Open an issue](https://github.com/unequalsine/treinwijzer-light-phone/issues) with what you expected, what happened and steps to reproduce it. For visual issues, include a human-captured screenshot and your emulator or LightOS version.

Treinwijzer product code lives in [`tool/`](tool/). SDK modules, the plugin, builder, signer and examples follow official upstream source; keep tool changes within `tool/` and respect the SDK’s API and dependency restrictions. Read the [contribution guide](CONTRIBUTING.md) and [code of conduct](CODE_OF_CONDUCT.md) before proposing changes. Run `./gradlew check` and have a human test changed behaviour in the emulator before considering it validated.

## Open source, built for Light

Licensed under the [MIT License](LICENSE). Tool ID: `nl.treinwijzer.lightphone` · Candidate: `1.0.1` (code `2`) · Light SDK: `0.2.0`.

Light’s hosted builder builds and signs tools from public Git commits. A successful build and Tool Library approval are separate steps. This repository makes the tool source available for that process; see [Light’s submission instructions](https://github.com/lightphone/light-sdk#submitting-your-tool) and [Tool Library guidelines](TOOL_GUIDELINES.md).

Treinwijzer is an independent community project, not an official NS or Light Phone product.

[SDK documentation](docs/README.md) · [Tool metadata](docs/tool_metadata/README.md) · [SDK limitations](docs/sdk-gaps.md) · [Signing and installation](docs/tool_signing/README.md)
