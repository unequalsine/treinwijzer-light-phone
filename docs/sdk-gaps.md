# SDK and release limits

Reviewed on 9 October 2026 against Light SDK `0.2.0`, official upstream `311aa2d81e03e7df2603fa3943d05e4aad51d29b`. The 417 SDK/plugin/builder/signer/trust/example/lint source files match upstream exactly.

Location permission and leased updates, sealed-context storage, hosted building/signing, Tool Manager and public Tool Library submission are supported. Nearby-station coordinates are used only on the phone. Release metadata targets `com.lightos`; an explicit debug option selects the emulator without changing release metadata. Dependencies use public repositories.

## Background alerts

The SDK exposes `LightServerPushCredentials(pushEndpoint, pushRegistrationDate)`, but does not expose the encryption public key and authentication secret. Its push registration callback contains a TODO for saving the public-key set. Standard [UnifiedPush delivery requires encryption](https://unifiedpush.org/developers/spec/android/). Neither a callback nor a URL establishes a usable encrypted production transport.

This release disables push registration in `ToolEntryPoint`. Journey changes and alerts appear in the open tool. The backend keeps up to 10 unread alerts for 24 hours; reopening retrieves them, and dismissal acknowledges them. No remote background notification or plaintext journey push is sent. The UI states this limit in both languages.

## Backend and validation

Scoped public bootstrap, per-install ownership, persistent quotas, actual outgoing NS request budgets, bounded active tracking and the alert inbox are implemented in the isolated backend release branch. Existing private-watch/APNs checks pass. Deployment needs explicit approval because the new Durable Object class is a lifecycle migration with constrained rollback.

Automated checks include the real local Cloudflare runtime and a clean extracted arm64 release. They do not establish hosted signing acceptance or UI correctness. The user has an emulator available; human checks, screenshots and source review remain necessary. A minified development-signed emulator build is provided to exercise shrinking. See [release preparation](releasing.md).
