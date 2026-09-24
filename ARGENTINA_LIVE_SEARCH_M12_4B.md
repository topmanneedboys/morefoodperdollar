# ValuePilot Argentina Live Search M12.4B

Status: `ARGENTINA_LIVE_SEARCH_M12_4B_REPAIR_BUILD_READY_NOT_DEVICE_QUALIFIED`

Updated: 2026-09-24
Repository: `topmanneedboys/morefoodperdollar`
Branch: `fix/argentina-live-search-response-contract`

## 10106 failure diagnosis and bounded repair

The first real Motorola Edge 2025 / Play-installed `10106` request reached the
public Consumer Edge and private M11 successfully: Cloud Run request logs show
`POST /v1/status` and `POST /v1/search` both returned HTTP 200, with the same
Play-installed user agent. The M11 search response was a valid discovery
response, but its additive `diagnostics.regionsQueried` field is a JSON array.
The Android parser accepted diagnostic strings, numbers, booleans and objects,
but rejected arrays, converting that valid 200 response into
`CONTRACT_INVALID`; the surface then showed the generic temporary-failure
message.

The repair keeps the contract strict and bounded: diagnostic arrays are
accepted only up to 32 scalar items, each item is bounded, and nested objects,
nested arrays and nulls remain rejected. The values are projected to stable
diagnostic text and never gain price, identity, ranking or availability
authority. Failures now retain only safe endpoint/status/request/release
metadata and the production Search trace logs those fields without query text,
response bodies, URLs, headers or App Check tokens.

The corrected build is Android `10107` / `101.1.6`. Its local Play-upload AAB
was signed and verified with the existing upload certificate (the same
protected mechanism used for 10103–10106); it has not been uploaded to Google
Play. The physical smoke remains pending because the attached phone currently
has 10106 and is PIN-locked. Use the single bounded
`tools/collect_live_search_10107_trace.ps1` script only after Google Play has
installed 10107 and the phone is unlocked.

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

The M12.4B backend image was built from this exact commit in Cloud Build
`0f8e511d-6d24-4d4c-888a-296ccf2a5951` and is pinned by digest
`sha256:5033e5bdd315c7954c8d1f4c0c6f7f9d034a6586819294beec31c3e4eada0eae`.
It is serving private M11 revision
`valuepilot-argentina-sa-east1-00037-nuj` at 100% traffic, with tags
`m124-gcs-auth` and `m11-4b-sa-10`. Its explicit environment gate is scoped
to `identity-search` and the pinned release id/date; it authorizes Android
Search only while the release is fresh, backend-qualified and exactly matches
those values.

## Current release gate and exact activated release

The exact official release acquired from the user-provided archive was
verified, mirrored to both regional runtime buckets, and activated by a
compare-and-swap pointer update. The immutable release identity is:

- release id: `argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2`
- release date: `2026-09-18`
- official source SHA-256: `619498b8e3230298b8a91f3df05ee7fe44e190a8f909e0e103cf43867fec73ee`
- official source bytes: `335486139`
- candidate manifest SHA-256: `9a558cafcb5d5a162eb04330448612418f437eda7d1f5a3e5ba44e9202c415cf`
- SearchPack objects: `1163` (`897007419` bytes); mobile profile `128/32`; backend profile `1024/32`
- active pointer generations: southamerica-east1 `1789973690114545`, us-east4 `1789973692306062`
- activation writes: one CAS update per runtime bucket; no rollback was required

The live status check on 2026-09-21 returned:

```text
service ready
releaseId argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2
releaseDate 2026-09-18
freshness FRESH
backendCodeQualified true
androidNetworkingAuthorized true
productionAndroidUiAuthorized false
```

This is the intended fail-closed boundary: the fresh release now permits the
bounded `identity-search` backend gate, while
`productionAndroidUiAuthorized` remains false until the signed Play artifact
and explicit physical-device smoke are completed. No date was extended, no
source was invented, and no networking bypass was added. Availability in the
live search response remains `UNKNOWN`; SEPA price publication is not inventory
evidence.

## Build and privacy evidence

The original candidate set Android version `10103` / `101.1.2`. Its release bundle task
completed successfully and produced the ignored local unsigned artifact:

`android/app/build/outputs/bundle/release/app-release.aab`

Unsigned AAB SHA-256: `189ba1bc1545fbd00fc2da80818dbc250501b9c217a3dd5db156987de88193ed`.
The existing protected Play upload keystore was found locally and its
certificate matches the known upload certificate. The current 10103 bundle was
signed locally (without uploading it) as the ignored artifact
`android/app/build/outputs/bundle/release/app-release-play-upload-signed-10103.aab`.
It is `36,307,058` bytes with SHA-256
`b195d6a6318db823a61f27035cc2b8c66adda392c2040a4b87d71e17b4cd3679`.

The corresponding unsigned release APK reports package `com.valuepilot.app`,
versionCode `10103`, versionName `101.1.2`, target SDK 36, and exactly the
intentional `INTERNET` and optional `CAMERA` permissions. It has no
`ACCESS_NETWORK_STATE` or location permission and has cleartext disabled.
`jarsigner` verification passed for the signed bundle and its embedded signer
certificate is the known upload certificate SHA-256
`A0:B6:5D:8A:5A:F9:44:9E:76:6F:05:39:6F:E1:78:20:D5:2F:26:7C:E9:D5:8A:B0:C2:F6:CD:E4:DC:B8:52:DA`.
Do not generate a replacement key. The signed artifact is not uploaded to
Google Play by this checkpoint.

### 10107 repair artifact

The repair build is Android `10107` / `101.1.6`. The unsigned release APK
reports package `com.valuepilot.app`, the intentional `INTERNET` and optional
`CAMERA` permissions, no `ACCESS_NETWORK_STATE` or location permission, and
cleartext disabled. The signed Play-upload bundle is the ignored local artifact
`android/app/build/outputs/bundle/release/app-release-play-upload-signed-10107.aab`;
it is `36,321,330` bytes with SHA-256
`a72f1155c98d75075be3a117ff56be670640f1be7c67645c2fcc0297df265179`.
`jarsigner` verification and the known upload-certificate fingerprint passed;
no password was printed and no Play upload was attempted.

## Verification

The following completed successfully on this candidate:

- `:shared-core:test`: 405 tests.
- `:app:testDebugUnitTest`: 1,681 tests.
- `:app:lintDebug` and `:app:lintRelease`.
- `:app:assembleDebug`, `:app:assembleRelease` and `:app:bundleRelease`.
- Backend compile plus 110 backend tests.
- Offline tooling compile plus 300 tooling tests.
- Regression coverage for the live array-shaped diagnostic, bounded scalar
  arrays, safe correlation/release metadata and the corrected 10107 boundary.
- Browser model/tests/Firefox lint: 30 tests, zero failures or skips.
- Cloud negative boundary: missing App Check 401, invalid App Check 401,
  unknown route 404, direct private M11 403; authenticated private status 200.
- Exact-head GCS image build `0f8e511d-6d24-4d4c-888a-296ccf2a5951` succeeded.
- The new no-traffic revision returned authenticated `/readyz` 200 with the
  exact release and `FRESH`, `/v1/status` 200 with
  `androidNetworkingAuthorized=true`, and `/v1/search` 200 for the explicit
  CABA `leche` query. The same checks passed after 100% traffic promotion.
- The release workflow recorded `CANDIDATE_VERIFIED`, zero active-pointer
  writes before promotion, and the candidate receipt/manifest; raw provider
  data remains outside Git. Both runtime-bucket mirrors have zero missing,
  size-mismatch or checksum-mismatch objects.
- Local Play-upload signing for version `10103` passed with the existing
  protected key; package/version and signer fingerprint were independently
  checked. No Play upload was attempted.

The normal exact-head GitHub workflows are the final remote gate after this
checkpoint is pushed. This report does not claim a physical-device result,
Play upload or production Android UI qualification.

## Remaining bounded actions

1. Upload the already verified signed 10103 bundle through the normal protected
   Play Console internal-track process; do not upload an unverified copy.
2. Install that signed bundle through Google Play and perform the explicit
   CABA identity-search smoke. Only then can this status advance to device
   qualified.

This M12.4B checkpoint did not authorize M12.5 work. M12.5 was later
separately authorized; its current status is recorded in
[CURRENT_STATE.md](CURRENT_STATE.md) and [CONTINUATION_CHECKPOINT.md](CONTINUATION_CHECKPOINT.md).
