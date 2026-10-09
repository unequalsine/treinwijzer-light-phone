# Release candidate checks — 9 October 2026

Status: **UNVALIDATED - requires emulator testing**. Production deployment was blocked by automatic approval review and has not occurred. No emulator display, screenshot, physical phone, hosted signing or dashboard submission was accessed.

## Initial checks before deployment isolation

- Official upstream `311aa2d81e03e7df2603fa3943d05e4aad51d29b`, SDK `0.2.0`: all 417 SDK/plugin/builder/signer/trust/example/lint files match upstream byte for byte.
- Complete repository `check` passed (643 tasks), covering the imported SDK/plugin/examples and tool. Final product changes separately passed 35 tool unit tests, lint and debug assembly. Tests include location quality/ranking, backend credential/tracking isolation and both localised foreground-alert disclosures.
- Release `1.0.1` (code `2`) built from 12 allowlisted extracted tool files on a clean official SDK without local properties. APK: unsigned, arm64 only, portrait, tool ID `nl.treinwijzer.lightphone`, SDK `0.2.0`, server package `com.lightos`. Building with the emulator flag still preserves the release target.
- Emulator debug and minified emulator debug build and verify with the development signing key. Their server package is `com.thelightphone.sdk.emulator`. Both select production with an empty shared token. The unsigned release also selects production with no token.
- The upstream R8 Kotlin metadata compatibility warnings remain. A human must exercise the minified build; these warnings were not hidden with SDK patches.
- Backend branch `codex/lightos-release-backend`, commit `bf45353ee3d42339d456c609b2ec40ca08abe345`, based on fetched production-repository main `4e779b04621cad683326fbc3a82927798fb19638`: typecheck and 118 tests passed, including the previous private-watch/APNs suite. Concurrent quotas, cross-install rejection, expiry, revocation, inbox retention/ack and the public-access disable switch are covered.
- Real local Cloudflare runtime with SQLite Durable Object and mocked NS traffic passed bootstrap, scoped access, trip planning, active journey ownership, scheduled delay detection, inbox delivery/ack, stop and revoke. The mock rejects all unconfigured external destinations. This is fixture evidence, not production evidence.
- Worker dry-run bundled successfully and declared the intended existing KV namespaces/cron plus the new Light state binding/enable flag. No production writes were made.
- Installer shell syntax and Treinwijzer-specific whitespace checks passed.

## Local artifacts

Ignored directory: `local/releases/1.0.1/`. Emulator APKs are development-signed; the release is unsigned for Light’s signer. Extracted source is `extracted-source.zip`. Backend review patch is `backend-lightos-release.patch`. These files are local review artifacts, not publicly published binaries.

| Artifact | SHA-256 |
| --- | --- |
| `treinwijzer-1.0.1-emulator-minified.apk` | `24c66bff2a8e0416bedb058293efae1a19f34a01cad9a33119bfa1fdcbbf9ee7` |
| `treinwijzer-1.0.1-emulator.apk` | `673a425b09f2c3fe75a9c1ea6fbe8e58cb148ffe4b0540e86b907a34f3245817` |
| `treinwijzer-1.0.1-unsigned-arm64.apk` | `9f747802b714acf5c091aa1ff153ac32cec21e5ff99576cd4f551703caa761ba` |

## Remaining gates

The earlier watch-service deployment proposal is superseded. Approve only the separate Light service and its own storage; ordinary rollback across its class creation is restricted. Configure its NS API secret securely in Cloudflare. Then perform authenticated production smoke checks and revoke the test installation. Production currently remains on observed version `5de48759-2e35-46d3-a143-a2a40a5f6c48`.

The user has an emulator only. Human UI/permission/navigation, offline/retry, active tracking and unread-alert checks plus screenshots remain required under `AGENTS.md`. `./scripts/install-tool.sh --minified` exercises shrinking without a physical phone. No background push notifications are claimed.

Source publishing, third-party terms review, dashboard version-code comparison, a human-authored changelog and Light build/signing/library approval remain separate steps. See [release preparation](releasing.md). The 8 October report is historical migration evidence and is superseded by this candidate.

## Subsequent deployment isolation

The shared-production proposal is superseded by the separate Light deployment preparation, backend branch head `5309609e6384cb5d929f23256d4f3abe25134ed5`. The artifact table above now contains the rebuilt candidate hashes. The watch Wrangler configuration was restored to the main-branch version. A Light-only entry point reuses the same backend engine, rejects watch access, strips APNs secrets/bindings and lists only Light active journeys. The Light configuration declares its own KV namespaces and SQLite Durable Object. Typecheck, all 121 backend tests, the isolated local runtime integration and its deployment dry run pass. The tool’s 35 tests, lint and normal debug build pass; the minified emulator and clean extracted unsigned arm64 release have been rebuilt. All three APKs contain the isolated URL and exclude the watch URL. No remote changes were made.

The tool release URL now targets `https://treinwijzer-light.unequalsine.workers.dev`. Deployment and human emulator validation remain pending. Current artifact hashes are recorded in ignored `local/releases/1.0.1/artifact-hashes.json`; the earlier installed emulator APK uses the previous URL until reinstalled. Cost evidence and the user-confirmed paid plan are recorded in the backend runbook. Isolation adds no fixed subscription, but account allowances and any reused NS subscription quota remain shared.
