# Treinwijzer LightOS release preparation

Reviewed: 9 October 2026. **UNVALIDATED - requires emulator testing.** Release candidate `1.0.1` (code `2`) is built and the backend integration is implemented/tested. The user-approved separate Light service is deployed; NS secret setup and live service checks are complete; human validation remains pending. No dashboard submission has been made.

## Build and backend inputs

The official SDK is imported through `311aa2d81e03e7df2603fa3943d05e4aad51d29b`, SDK `0.2.0`. All 417 SDK/plugin/builder/signer/trust/example/lint files match upstream. Treinwijzer product changes are confined to `tool/`; release configuration contains the production Worker URL and an empty shared token. Metadata always declares `com.lightos`. The debug-only manifest placeholder selects the emulator; even an emulator flag cannot change the release server package.

Light’s hosted builder extracts only the tool build script, metadata and allowlisted main sources/resources/assets into its pinned SDK workspace. Root build configuration, SDK patches, tests, scripts and ignored `local.properties` do not supply release configuration. The clean extraction build accepted 12 files and produced an unsigned arm64 APK. This checks local extraction/compilation; it is not a hosted/signing-service acceptance result.

Backend source is prepared on `codex/lightos-release-backend` in the isolated worktree `local/backend-release`, based on fetched production-repository main `4e779b04621cad683326fbc3a82927798fb19638`. Its Worker files match the original widget-fix branch before these scoped changes. The adjacent watch checkout and its unrelated purchase investigation are untouched.

The local backend implementation and deployment runbook is `local/backend-release/docs/lightos-release.md`; that isolated worktree is intentionally ignored by this repository. The original backend commit is `bf45353ee3d42339d456c609b2ec40ca08abe345`; isolation commit `6ebc8a0e2d063200603318f5a646390ee7505652` and activation instructions at `5309609e6384cb5d929f23256d4f3abe25134ed5` supersede its shared-production configuration. It adds public `/install/register/light`, server-generated Light IDs, hashed 256-bit secrets, ownership checks, a SQLite-backed rate limiter/inbox and a Light-only disable switch. A dedicated Light entry point rejects watch routes/credentials and strips APNs bindings. The separate Light configuration uses new KV namespaces and its own Durable Object, while the watch configuration matches production-repository main. Credentials expire after 90 idle days; tracking is bounded, and unread alerts are limited to 10 for 24 hours.

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

Automatic approval review rejected the earlier proposal to change the existing watch production Worker because preparation did not explicitly authorise its Durable Object migration. That proposal is superseded by a separate `treinwijzer-light` service; do not deploy the earlier shared-service configuration. The observed watch version `5de48759-2e35-46d3-a143-a2a40a5f6c48` is unchanged.

The proposed Light deployment creates separate KV namespaces and `LightState`, requires only an NS API secret, and does not change watch storage, credentials, APNs or deployment. Its own class creation still restricts Light rollback; disable `LIGHT_ENABLED` or make a corrective deployment while retaining the class. See [Cloudflare restrictions](https://developers.cloudflare.com/workers/versions-and-deployments/rollbacks/).

The user confirmed the existing US$5/month paid account. A second Worker adds no fixed subscription fee. Recent read-only usage indicates ample request/KV allowance, so additional charges are not expected at this scale, but account-wide overages remain possible. The backend runbook records the evidence and limitation; no remote resources or plan changes have been made.

After explicit deployment approval: use only `worker/wrangler.light.toml`, configure its NS secret, record new namespace IDs and verify public bootstrap/stations/trips/start-read-stop tracking and revocation without logging credentials. The approved Light-only deployment is complete (see the deployment result below); registration returns 503 until its NS secret is configured. The rebuilt minified candidate has now been installed and launched with the Light URL; see the live activation result below.

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

## Approved Light deployment — 9 October 2026

The user approved deployment of the separate service. Source `5309609e6384cb5d929f23256d4f3abe25134ed5` is live as version `e02542c2-4a29-435b-8f97-6b3c149b5456` at `https://treinwijzer-light.unequalsine.workers.dev`. Its newly created KV and SQLite Durable Object namespaces are separate from the watch service; IDs are recorded in the backend configuration. The watch deployment remained exactly unchanged at `5de48759-2e35-46d3-a143-a2a40a5f6c48`.

The endpoint returns the expected 503 because `NS_API_KEY` has not been configured. The user must add that secret in Cloudflare Workers & Pages → `treinwijzer-light` → Settings → Variables and Secrets, then save/deploy. Functional production smoke checks and reloading the rebuilt emulator candidate follow that setup. No watch/APNs secrets were copied, and no billing plan changed. The deployment itself succeeded; this is not yet a functional release validation.

## Live activation and emulator reload — 9 October 2026

The user configured the NS secret. Initial live checks exposed empty-body test-installation revocation returning 400. The scoped correction (`664f3fd3026941eb043f09f4999921b9f7be4841`) passed typecheck, 122 backend tests and local runtime integration, and is live as `6b93dfae-7751-4ef5-9e91-3a3da08ecb71`. The NS secret and all three Light storage IDs were preserved; no watch/APNs bindings were added.

Live registration/renewal, credential rejection, stations/search/departures/disruptions, trip planning, tracking start/read/stop, a real scheduled NS poll, empty owned inbox/ack and revocation all pass. The successful test installation was removed. The first attempt left an inactive installation after its cleanup failed; its credentials were not retained, and it expires under the normal 90-day policy. Backend details and limits are recorded in the runbook.

The rebuilt minified `1.0.1` (code `2`) APK is installed and running in the emulator. The foreground component is `nl.treinwijzer.lightphone/com.thelightphone.sdk.LightActivity`, and LightOS reports SDK `0.2.0`. No screenshots, emulator display access or human UI confirmation occurred. The APK hashes are unchanged from the isolated rebuild. Human checks above, source publishing and Light signing/submission remain outstanding.

## Screenshot feedback

The supplied human emulator screenshots exposed clipped home shortcut labels and timeline times, plus an oversized favourite action label. The candidate now lets shortcut buttons grow with their content, reduces utility action/time text and measures the complete time column consistently across the timeline. Recheck the home screen, favourite add/remove action and active/trip-detail timeline in English and Dutch before accepting this candidate. This is a tool-only layout correction; the live backend remains unchanged.

## Public source publication — 9 October 2026

At the user’s request, the prepared candidate and refreshed project README were published to public repository `unequalsine/treinwijzer-light-phone`, default branch `main`, at commit `3404401`. The repository remains MIT licensed. This completes source publication; the earlier references to source publishing being outstanding describe the state before this step. Human emulator confirmation, screenshots, hosted signing and Tool Library submission/approval remain pending. Publication did not deploy or change either backend.

## Upstream history reconciliation — 9 October 2026

The official SDK history through `311aa2d81e03e7df2603fa3943d05e4aad51d29b` is now merged, rather than represented only by the earlier source import. Two unchanged upstream build helpers (`builder/bin/build.sh` and `builder/bin/maven-proxy.sh`) were restored; an ignore exception prevents new files in that upstream directory from being skipped. All 417 tracked files under SDK/plugin/builder/signer/trust-format/examples/lint-rules match upstream, including executable modes. Treinwijzer tool files and the README are unchanged by the merge. The candidate APK and human validation status are unchanged.

## Station letter grid correction — 9 October 2026

Human screenshot feedback showed clipped letters in the A–Z selector. Letter buttons now use 2.1 grid units of height (84 px at 1080×1240), up from 1.55, with the redundant inner vertical padding removed. At the supplied viewport and normal font scale, even nine rows for all 26 letters leave approximately 72 px below the content, so scrolling should not be needed. The actual NS station index currently has eight rows. Lint and normal/minified emulator packaging pass; the updated minified candidate is installed. Human visual confirmation remains required.
