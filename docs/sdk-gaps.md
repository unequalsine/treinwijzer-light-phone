# Journey updates and notifications

Treinwijzer refreshes active journeys while the tool is open. The backend keeps up to 10 unread journey alerts for 24 hours; reopening the tool retrieves them, and dismissing an alert acknowledges it.

Background push notifications are currently disabled. Light SDK 0.2.0 exposes a push endpoint but does not provide the public key and authentication secret needed for [encrypted UnifiedPush delivery](https://unifiedpush.org/developers/spec/android/). A future SDK update may make this available.

Nearby stations use the SDK’s location permission and a recent device fix. Distances are calculated locally; coordinates are not sent to the backend.
