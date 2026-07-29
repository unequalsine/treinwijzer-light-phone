# Current Light SDK gaps

This file records limitations observed at the pinned SDK revision `ddf33e40d04306b86dfbbe7e9dc64fb19e9fb525`. Treinwijzer does not patch the SDK or use restricted Android APIs to work around them.

## GPS and nearest stations

The current tool API does not expose location access. The nearest-stations screen therefore shows an explicit unsupported state. It does not infer location from network data, stale preferences or a hard-coded city.

## Visible notifications while closed

UnifiedPush can wake the tool callback and signal that authoritative journey data should be refreshed. The SDK does not currently offer a supported way for a community tool to publish a visible LightOS system alert while the tool is closed. Treinwijzer shows alerts immediately while open and stores up to ten unacknowledged alert events in the Worker for display on the next launch.

## Durable push-callback storage

The push callback has no SDK-provided durable storage surface. It therefore decodes a small versioned envelope into a replaying in-memory signal. The Worker event inbox is the durable fallback for user-visible alerts; the client always fetches `/journeys/active` for authoritative journey state.

## Time-sensitive background execution

LightOS controls background execution. Treinwijzer cannot guarantee exact callback or closed-tool refresh timing. The Worker continues polling once a minute and coalesces provider-specific delivery using the same journey-change logic as watchOS.

## Production push and distribution configuration

The public SDK does not yet document a complete production community-tool distribution and push configuration flow. This simulator milestone uses `serverPackage = "com.thelightphone.sdk.emulator"`. A hardware/distribution build must switch it to `com.lightos`, replace temporary tunnel configuration with an approved production endpoint and follow Light's published signing/distribution process when available.

## Revisit checklist

- Supported tool location API.
- Supported visible closed-tool notifications.
- Durable callback storage or background task API.
- Time-sensitive background execution guarantees.
- Production UnifiedPush endpoint and VAPID documentation.
- Builder-time secrets and per-environment configuration.
- Community-tool signing and distribution instructions.
