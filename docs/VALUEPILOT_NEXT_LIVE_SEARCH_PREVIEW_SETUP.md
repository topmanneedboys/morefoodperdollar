# ValuePilot Next preview: Live Search authorization

## Current state

The isolated debug preview is intentionally unable to initialize Firebase in this checkout. The module-level Firebase configuration identifies only `com.valuepilot.app`, while the debug build uses `com.valuepilot.app.nextpreview`. The `processDebugGoogleServices` task is explicitly disabled so the preview cannot silently reuse the Play app's Firebase identity. `ValuePilotFirebaseRuntime` fails closed when initialization has no matching configuration, and the consumer edge sends Search requests only with a Firebase App Check token in `X-Firebase-AppCheck`.

The debug source set already selects `DebugAppCheckProviderFactory`; the release source set selects Play Integrity. Keep that boundary. Do not add debug tokens to source, build files, assets, or a production APK, and do not weaken the consumer edge's App Check verification.

## Required Firebase setup for the isolated preview

1. In the existing `valuepilot-argentina` Firebase project, register a **separate Android app** with the exact, case-sensitive package name `com.valuepilot.app.nextpreview`. Do not edit or replace the existing `com.valuepilot.app` registration. A separate Firebase project would require a separately authorized staging edge trust configuration, so it cannot authenticate to the current edge by itself.
2. Download the configuration for the new preview registration and place it at `android/app/src/debug/google-services.json`. The file must contain a client matching `com.valuepilot.app.nextpreview`. The existing `processDebugGoogleServices` task is disabled in `android/app/build.gradle.kts`; enabling Firebase for this variant requires a narrowly scoped Gradle change that processes the debug-specific file while preserving the current release configuration. Do not copy the production root JSON into the debug source set.
3. Build and install only the isolated debug APK, then launch it on the authorized Motorola. The Firebase debug provider will generate a device token. Read it from the App Check debug-provider log locally; do not send it in chat or commit it.
4. In Firebase Console, open **App Check → Apps → the ValuePilot Next preview app → Manage debug tokens** and register that device token. Keep it private and revoke it after testing if it is no longer needed. This allowlist is what authorizes the debug provider; adding the Android app registration or JSON file alone does not authorize requests.
5. Retry Live Search. Confirm the request is accepted by the existing consumer edge with normal App Check verification. Keep the local preview sample visibly labeled and price-free unless actual Search evidence is returned.

No production app registration, Play Integrity setting, signing key, release credential, backend trust policy, or Play listing needs to change for this isolated debug path. Until steps 1–4 are completed, Live Search remains blocked; the truthful unavailable state is expected.

## Official Firebase references

- [Register an Android app and add its Firebase configuration](https://firebase.google.com/docs/android/setup)
- [Google Services Gradle plugin and build-type-specific configuration files](https://firebase.google.com/docs/android/google-services-plugin-and-file)
- [Use App Check with the Android debug provider](https://firebase.google.com/docs/app-check/android/debug-provider)
