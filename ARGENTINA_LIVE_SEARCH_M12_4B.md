# ValuePilot Argentina Live Search M12.4B

Status: `ARGENTINA_LIVE_SEARCH_M12_4B_PLAY_BUILD_READY_NOT_DEVICE_QUALIFIED`

Updated: 2026-09-20  
Repository: `topmanneedboys/morefoodperdollar`  
Branch: `candidate/argentina-live-search-m12-4`  
Implementation commit: `ccf7cca543ceac64e9a6437c07f00324dd95369a`

## Scope completed

This bounded milestone makes Argentina identity Search visible in the existing
Android shell. It does not redesign the M12.4A coordinator, bridge or edge
transport and does not start M12.5. MainActivity only renders immutable state
and emits typed user actions. Search is user-triggered; initialization,
background work and route changes do not submit requests.

The result surface is identity-only. It shows a product name, optional brand,
deterministic match label, source attribution and a permanent caveat. It does
not claim a current price, package quantity, stock, availability, promotion,
delivery, freshness or Best Value ranking. Tapping a result hands the untrusted
display name to the existing Compare review route, where the existing exact
evidence gates remain authoritative.

The old fictional sample provider/controller is not used by the visible Search
path. The M12.3 startup status probe is removed from application startup and
the qualification class remains only as historical/test evidence. Android
networking is not authorized by the app itself; the backend gate remains the
authority.

## Explicit launch area

The visible surface uses one documented launch area rather than device location,
IP geolocation or location permission:

| Field | Value |
| --- | --- |
| Label | Buenos Aires (CABA) |
| Area id | `ar-caba-buenos-aires` |
| Latitude / longitude | `-34.605506`, `-58.442562` |
| Radius | 25 km (maximum allowed: 50 km) |
| Region | `ar-caba` |
| Provider | `ARGENTINA_SEPA_PRECIOS_CLAROS` |
| Source release date | 2026-09-06 |
| Snapshot manifest SHA-256 | `49c88fb9fc9b745788562bf292352649692196e3c0f68a01737ddc779850eda3` |
| Stores SHA-256 | `de38f7c8e2715cca07a2ba734cc5c7b4d65ba7a3ed187e710c2bfb795ebf26ad` |

The center is the median latitude/longitude of 973 source-marked valid CABA
stores in the qualified snapshot; the 25 km radius is the deterministic p95
coverage radius. This is launch-area context only and never establishes live
store inventory or price.

## Firebase, App Check and OIDC boundary

The Google Cloud project is active: `valuepilot-argentina` (project number
`17260882200`). The registered Android app is `ValuePilot Android`, package
`com.valuepilot.app`, Firebase App ID
`1:17260882200:android:446eec76e5f1a175f2566d`. The existing M12.3 Firebase
App Check registration uses Play Integrity in release and the debug provider
only in debug/test builds. The public edge continues to require App Check;
the rechecked negative boundary returned 401 for both missing and invalid
App Check, and 404 for an unknown route.

The private M11 service continues to accept only authenticated service-to-
service OIDC. A direct unauthenticated request returned 403; an authenticated
identity-token status request returned 200. The dedicated edge service account
remains the only `roles/run.invoker` binding on private M11. The public edge
is unchanged at revision `valuepilot-argentina-consumer-edge-00004-5lg`, with
max scale 20 and image digest
`sha256:315e37021aa7b1adf2f77eba88bc85f09984025ebac98e89612ff40168238581`.

The M12.4B backend image is deployed to private M11 revision
`valuepilot-argentina-sa-east1-00029-gef`, digest
`sha256:83aa126d01b34c5b284465a6ff4cd9334fc162710c0e9b33615383dcbdd7107d`.
Its explicit environment gate is scoped to `identity-search` and the pinned
release id/date; it cannot authorize Android Search unless the release is
fresh, backend-qualified and exactly matches those values.

## Current release gate

The live status check on 2026-09-20 returned:

```text
service ready
releaseId argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1-search-v2
releaseDate 2026-09-08
freshness STALE
backendCodeQualified true
androidNetworkingAuthorized false
productionAndroidUiAuthorized false
```

This is an intentional fail-closed result. The current active release is older
than the configured freshness window, so the Android coordinator maps it to a
stale/authorization-unavailable state. No date was extended, no source was
invented, and no networking bypass was added. A newly qualified release must
be activated before a real device can qualify live Search.

## Build and privacy evidence

The candidate sets Android version `10103` / `1.1.2`. The release bundle task
completed successfully and produced the ignored local unsigned artifact:

`android/app/build/outputs/bundle/release/app-release.aab`

The corresponding unsigned release APK reports package `com.valuepilot.app`,
versionCode `10103`, versionName `101.1.2`, target SDK 36, and exactly the
intentional `INTERNET` and optional `CAMERA` permissions. It has no
`ACCESS_NETWORK_STATE` or location permission and has cleartext disabled. The
candidate is not signed for Play here because the existing upload keystore and
credentials are not present in this workspace. Do not generate a replacement
key: the existing Play upload certificate must be used. The previously signed
10102 artifact identifies that upload certificate as SHA-256
`A0:B6:5D:8A:5A:F9:44:9E:76:6F:05:39:6F:E1:78:20:D5:2F:26:7C:E9:D5:8A:B0:C2:F6:CD:E4:DC:B8:52:DA`.

## Verification

The following completed successfully on this candidate:

- `:shared-core:test`: 405 tests.
- `:app:testDebugUnitTest`: 1,668 tests.
- `:app:lintDebug` and `:app:lintRelease`.
- `:app:assembleDebug`, `:app:assembleRelease` and `:app:bundleRelease`.
- Backend compile plus 46 backend tests.
- Offline tooling compile plus 197 tooling tests.
- Browser model/tests/Firefox lint: 30 tests, zero failures or skips.
- Cloud negative boundary: missing App Check 401, invalid App Check 401,
  unknown route 404, direct private M11 403; authenticated private status 200.

The normal exact-head GitHub workflows are the final remote gate after this
checkpoint is pushed. This report does not claim a physical-device result,
Play upload, fresh-release activation or live Search qualification.

## Remaining bounded actions

1. Activate a newly qualified, fresh Argentina release and confirm the status
   gate changes to `androidNetworkingAuthorized=true`; do not relax the stale
   policy.
2. Provide the existing Play upload keystore/alias/password through the normal
   protected release process, sign `app-release.aab`, and verify package,
   version, signer and SHA-256 before uploading to the Play Console internal
   track.
3. Install that signed bundle through Google Play and perform the explicit
   CABA identity-search smoke. Only then can this status advance to device
   qualified.

No M12.5 work is authorized by this checkpoint.
