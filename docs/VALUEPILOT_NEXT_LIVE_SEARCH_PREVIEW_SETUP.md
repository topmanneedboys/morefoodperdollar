# ValuePilot Next preview: Live Search authorization

## Current state

The isolated debug preview now has its own Firebase Android registration and can use Live Search on the authorized Motorola test device. The preview package is `com.valuepilot.app.nextpreview`, its Firebase app ID is `1:17260882200:android:f49f015de6f879e0f2566d`, and its filtered configuration is stored at `android/app/src/debug/google-services.json`. The production package `com.valuepilot.app` keeps its existing Firebase identity and configuration. `android/app/build.gradle.kts` processes the debug Google Services file only when that file is present, so a checkout without the preview file still fails closed rather than reusing the Play app identity.

The preview device's Firebase App Check debug token was registered privately for development, and the consumer edge allowlist now contains both the production app ID and the preview app ID. Search requests continue to use normal App Check verification through `X-Firebase-AppCheck`; no verification bypass or production credential change was made. The debug source set selects `DebugAppCheckProviderFactory`; the release source set selects Play Integrity. Do not add debug tokens to source, build files, assets, or a production APK, and do not weaken the consumer edge's App Check verification.

## Required Firebase setup for the isolated preview

1. In the existing `valuepilot-argentina` Firebase project, register a **separate Android app** with the exact, case-sensitive package name `com.valuepilot.app.nextpreview`. Do not edit or replace the existing `com.valuepilot.app` registration. This step is complete for the current preview app ID above.
2. Download the configuration for the preview registration and place it at `android/app/src/debug/google-services.json`. The file must contain only a client matching `com.valuepilot.app.nextpreview`; do not copy the production root JSON into the debug source set. This step is complete in the current checkpoint, and the Gradle task is conditional on that file's presence.
3. Build and install only the isolated debug APK, then launch it on the authorized Motorola. The Firebase debug provider will generate a device token. Read it from the App Check debug-provider log locally; do not send it in chat or commit it.
4. In Firebase Console, open **App Check → Apps → the ValuePilot Next preview app → Manage debug tokens** and register that device token. Keep it private and revoke it after testing if it is no longer needed. This allowlist is what authorizes the debug provider; adding the Android app registration or JSON file alone does not authorize requests.
5. Retry Live Search. Confirm the request is accepted by the existing consumer edge with normal App Check verification. The current Motorola run returned 33 supported rows for `leche`, including an evidence-backed ARS 1,785 offer at Ángel Gallardo 940. If authorization is unavailable in a future checkout, keep the preview state visibly labeled and price-free until actual Search evidence is returned.

No production app registration, Play Integrity setting, signing key, release credential, backend trust policy, or Play listing needs to change for this isolated debug path. A new device or a fresh preview registration remains blocked until its own debug token is registered; the truthful unavailable state is expected until that authorization exists.

## Official Firebase references

- [Register an Android app and add its Firebase configuration](https://firebase.google.com/docs/android/setup)
- [Google Services Gradle plugin and build-type-specific configuration files](https://firebase.google.com/docs/android/google-services-plugin-and-file)
- [Use App Check with the Android debug provider](https://firebase.google.com/docs/app-check/android/debug-provider)
