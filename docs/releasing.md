# Treinwijzer LightOS release preparation

Reviewed: 9 October 2026. **UNVALIDATED - requires emulator testing.** Release candidate `1.0.1` (code `2`) is built and the backend integration is implemented/tested. Production deployment is pending explicit approval; no dashboard submission has been made.

## Build and backend inputs

The official SDK is imported through `311aa2d81e03e7df2603fa3943d05e4aad51d29b`, SDK `0.2.0`. All 417 SDK/plugin/builder/signer/trust/example/lint files match upstream. Treinwijzer product changes are confined to `tool/`; release configuration contains the production Worker URL and an empty shared token. Metadata always declares `com.lightos`. The debug-only manifest placeholder selects the emulator; even an emulator flag cannot change the release server package.

Light’s hosted builder extracts only the tool build script, metadata and allowlisted main sources/resources/assets into its pinned SDK workspace. Root build configuration, SDK patches, tests, scripts and ignored `local.properties` do not supply release configuration. The clean extraction build accepted 12 files and produced an unsigned arm64 APK. This checks local extraction/compilation; it is not a hosted/signing-service acceptance result.

Backend source is prepared on `codex/lightos-release-backend` in the isolated worktree `local/backend-release`, based on fetched production-repository main `4e779b04621cad683326fbc3a82927798fb19638`. Its Worker files match the original widget-fix branch before these scoped changes. The adjacent watch checkout and its unrelated purchase investigation are untouched.

The local backend implementation and deployment runbook is `local/backend-release/docs/lightos-release.md`; that isolated worktree is intentionally ignored by this repository. The backend commit is `bf45353ee3d42339d456c609b2ec40ca08abe345`. It adds public `/install/register/light`, server-generated Light IDs, hashed 256-bit secrets, ownership checks, a SQLite-backed rate limiter/inbox and a Light-only disable switch. Private watch registration and APNs stay separate. Credentials expire after 90 idle days; tracking is bounded, and unread alerts are limited to 10 for 24 hours.

The tool registers publicly without a private app token, renews an existing identity on launch and recovers an expired identity. Switching backend URLs preserves favourites/preferences while discarding the previous credentials and backend-bound active tracking. First-run disclosure precedes network registration. Inbox reads do not acknowledge; dismissal does.

## Delivery scope

This release supports foreground journey updates and unread-alert recovery when reopened. Push registration is disabled because the public SDK does not expose UnifiedPush encryption keys. It does not claim background notifications. Both localisations disclose this. A future push release requires a supported encrypted SDK transport and distribution-endpoint validation; see [SDK limits](sdk-gaps.md).

## Automated evidence and artifacts

See [9 October validation](validation-2026-10-09.md) for results and artifact hashes. Local APKs and extracted source are in `local/releases/1.0.1/` and are intentionally ignored by Git. The unsigned release needs Light’s signing service; it cannot be directly installed.

```sh
./gradlew -Ptreinwijzer.production=true -Ptreinwijzer.emulator=true check :tool:assembleDebug
./gradlew -DlightSdk.toolOnly=true -DlightSdk.unsigned=true -DlightSdk.abiFilters=arm64-v8a :tool:assembleRelease
```

The upstream shrinker emits Kotlin metadata compatibility warnings. The build succeeds; human minified testing remains required. Upstream tooling was not patched to hide those warnings.

## Deployment gate

Automatic approval review rejected production deployment because preparation did not explicitly authorise the Durable Object lifecycle migration. Production version `5de48759-2e35-46d3-a143-a2a40a5f6c48`, observed read-only, is unchanged. Approval must cover deploying this backend branch to the existing production Worker, creating `LightState` and retaining current secrets/KV/cron. Ordinary rollback cannot cross this class creation; disable `LIGHT_ENABLED` and redeploy, or make a corrective deployment while retaining the class. See [Cloudflare restrictions](https://developers.cloudflare.com/workers/versions-and-deployments/rollbacks/).

After approval: recheck the source, deploy, verify public bootstrap/stations/trips/start-read-stop tracking and revoke the smoke-test installation without logging secrets. Until this succeeds, production-mode emulator registration will fail.

## Human emulator checks

The user has **emulator only**. No AI emulator display, screenshots or UI validation has been performed. After deployment:

1. Start/update the emulator using [simulator setup](simulator.md).
2. Install the candidate with `./scripts/install-tool.sh`; fresh launch must show privacy information before registration. Continue and verify connection, station search, departures, disruptions, planning and fares.
3. Check Dutch/English layouts, favourites, back navigation, tracking, stopping and reopening. Close with an unread alert; it must remain available until dismissed. Check an offline launch and retry, and expired-identity recovery.
4. Test nearby stations with denied/granted permission, returning from the permission screen, missing/stale/inaccurate fixes, retry and back navigation. Leaving lookup should release the location lease.
5. Run `./scripts/install-tool.sh --minified` and repeat the critical planning/tracking/navigation path. This is development-signed debug with shrinking and emulator binding, not a Light-signed physical release.
6. Capture screenshots and record the emulator/LightOS version and results. All changes remain **UNVALIDATED** until a human confirms them under `AGENTS.md`.

## Dashboard submission

Follow [Light’s current instructions](https://github.com/lightphone/light-sdk#submitting-your-tool): human-review and publish the source, then Developer Account → Manage Custom Tools → Submit New Tool. Supply human-captured screenshots (at least one is required) and human-authored external text/changelog. The package ID is taken from the default branch and cannot change later. Submit a reviewed full commit SHA. Verify code `2` exceeds the dashboard’s last version; raise it and rebuild if necessary.

A successful hosted build produces a Light-signed APK pending approval. Build/signing and public-library approval are separate. Check [Tool Library guidelines](../TOOL_GUIDELINES.md), including privacy and third-party terms. No PR text, dashboard submission or public release has been sent by this preparation.
