# ValuePilot Argentina M12.3 — Android secure transport qualification

Updated: 2026-09-19

## Qualification label

`ARGENTINA_ANDROID_SECURE_TRANSPORT_M12_3_QUALIFIED`

This checkpoint qualifies the real Google Play-installed 10102/1.1.1 Android
release on a Motorola Edge 2025. Play Integrity requested a token for cloud
project `17260882200`, Google Play completed `requestIntegrityToken`, and the
release-only probe reached the public Consumer Edge with Firebase App Check
and the existing authenticated private M11 path. M12.4 remains not started.

## Repository and Firebase identity

- Repository: `topmanneedboys/morefoodperdollar`
- Branch: `candidate/argentina-android-consumer-bridge-m12-v2`
- Current checkpoint head before this documentation closure:
  `1573a853baffa65260aa3c30b7989cae4a66dd63`
- M12.3 implementation heads: `f0cd928` (transport foundation), `ea3cded`
  (10102 qualification probe), and `5cf09af3a4703842978f0ee0f3c250a881f63575`
  (bounded diagnostic hardening).
- Firebase project: `valuepilot-argentina` (ACTIVE, project number `17260882200`)
- Android app: `ValuePilot Android`, package `com.valuepilot.app`
- Firebase App ID: `1:17260882200:android:446eec76e5f1a175f2566d`
- Registered debug SHA-256: `0f31bb4d297d70d1d0830cecd74a2e26a08dd50a3337d00b2d5e33ba23240638`
- Registered Google Play app-signing SHA-256: `744117326b67e857fd24bf4dab57103aca1a89564467555b145df3c28cbe3182`
- `google-services.json` is the authoritative Firebase client configuration;
  it contains client identifiers only, not a service-account key or private
  credential.

## Play and Firebase live configuration

- Play app: `ValuePilot`, package `com.valuepilot.app`.
- Internal testing qualification: version code `10102`, version name `101.1.1`,
  installed by Google Play (`com.android.vending`) on the Motorola Edge 2025.
- Historical previous internal release: `10101 / 1.1.0`; it is retained only
  as historical context and is not the qualified M12.3 build.
- Internal tester list: `ValuePilot M12.3 Internal Trusted`, one account;
  opt-in URL: `https://play.google.com/apps/internaltest/4701231628553906260`.
- Qualified Play-installed build: `10102 / 1.1.1`; unsigned source bundle
  `android/app/build/outputs/bundle/release/app-release.aab` and separate
  signed upload bundle
  `android/app/build/outputs/bundle/release/app-release-play-upload-signed-10102.aab`.
- Signed 10102 upload bundle SHA-256:
  `3B9526F5A5F4AEB702E6FBA0D375E5C97CD0FDCE8FF5D7765459AB4AFADD2F3F`.
- The signed 10102 bundle is the Play-installed and qualified M12.3 build.
- Installation provenance: `installerPackageName` and `initiatingPackageName`
  were both `com.android.vending`.
- Local upload certificate SHA-256: `A0:B6:5D:8A:5A:F9:44:9E:76:6F:05:39:6F:E1:78:20:D5:2F:26:7C:E9:D5:8A:B0:C2:F6:CD:E4:DC:B8:52:DA`.
- Google Play app-signing certificate SHA-256 (read from Play's generated
  universal APK): `74:41:17:32:6B:67:E8:57:FD:24:BF:4D:AB:57:10:3A:CA:1A:89:56:44:67:55:5B:14:5D:F3:C2:8C:BE:31:82`.
- Play Integrity is linked to `valuepilot-argentina` (`17260882200`), and the
  Play Console response fields `LICENSED`, `PLAY_RECOGNIZED`, and
  `MEETS_DEVICE_INTEGRITY` are enabled.
- Firebase App Check for `ValuePilot Android` is `Registered` with the Play
  Integrity provider. Both the debug and Google Play app-signing fingerprints
  are present. Token TTL is one hour. Advanced policy requires
  `PLAY_RECOGNIZED` and `LICENSED`; minimum device integrity is **Don't
  explicitly check device integrity level**, matching Firebase's Play-only
  distribution guidance.

## Android configuration

- Google services Gradle plugin `4.5.0` and Firebase BoM `34.19.0` are wired.
- Release source set installs `PlayIntegrityAppCheckProviderFactory`.
- Debug source set alone installs `DebugAppCheckProviderFactory`; no debug
  token is committed, persisted, logged, or included in release sources.
- `INTERNET` is intentional for the bounded public-edge foundation.
- `ACCESS_NETWORK_STATE` remains removed from the merged APK.
- `android:usesCleartextTraffic="false"` remains enabled; no HTTP fallback or
  network-security exception was added.
- `ValuePilotApplication` initializes Firebase and installs the provider before
  any transport can be created. Missing/failed Firebase setup is fail-closed.

## Transport boundary

`ArgentinaConsumerEdgeTransport` is an application-edge adapter, not shared
core. It accepts only the five existing `/v1/*` contract routes and the public
host `valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app`. It never
represents or contacts the private M11 origin.

Before each request it obtains a normal cached-or-refreshable App Check token
and sends it only as `X-Firebase-AppCheck`. It never places the token in a URL,
query, log, or storage. Requests are UTF-8 byte-bounded at 16 KiB; responses
are content-length/stream bounded at 512 KiB and decoded with strict UTF-8.
Connect, read, and total timeouts are explicit and capped. Redirects and
caches are disabled. Cancellation is checked before token acquisition, before
connect/write, between bounded reads, and before returning. Typed failures
cover App Check, configuration/route, size, connectivity, TLS, timeout,
cancellation, generic network, and common HTTP statuses. There are no retries.

The existing in-memory transport remains the only path used by offline Home
and Search previews. No live UI wiring, background sync, provider data, or
private-memory upload was added.

## Verification

The focused M12.3 suite covers:

- authoritative Firebase config identity and absence of server credentials;
- release/debug provider isolation;
- manifest permission and cleartext boundaries;
- App Check header-only placement and fail-closed factory behavior;
- HTTPS host and route allow-lists;
- request/response bounds and strict UTF-8;
- typed HTTP, network, TLS, timeout, and cancellation outcomes;
- redirect/cache settings and deterministic fake-connection behavior.

The normal repository Android workflow must additionally run shared-core tests,
all Android unit tests, lint, debug/release builds, APK permission/signature
checks, browser verification, and release-bundle checks. The workflow privacy
gate now expects `INTERNET` and rejects `ACCESS_NETWORK_STATE`; it does not
upload national data or build directories.

The current local `gradle test` run completed successfully. The deployed
negative checks are also current: edge `/v1/status` without App Check returned
`401 {"error":"UNAUTHENTICATED"}`, the same request with an invalid token
returned `401`, an unknown edge route returned `404 {"error":"NOT_FOUND"}`,
and direct private M11 `/v1/status` returned `403 Forbidden`. No valid Play
token was manufactured or substituted.

The final local release verification completed successfully: 405 shared-core
tests and 1,652 Android unit tests passed with zero failures or skips; debug
and release lint, debug/release APK assembly, and release AAB compilation
passed. The release APK manifest reports package `com.valuepilot.app`,
versionCode `10102`, versionName `101.1.1`, min API 23 and target SDK 36; it
contains `INTERNET` and optional `CAMERA`, omits `ACCESS_NETWORK_STATE`, and
keeps cleartext disabled. Release BuildConfig enables the probe while debug
BuildConfig disables it. The signed AAB reports the preserved upload
certificate above and `jarsigner` verification passed. The probe logs only a
bounded `M12_3_STATUS=<100..599>` or typed failure code and has deterministic
coverage for response-body, exception-detail and out-of-range-status secrecy.

## Physical-device qualification evidence

The first real Play-installed cold-process probe recorded:

- Consumer Edge `GET /v1/status`: HTTP `504`, latency `8.578306877s`.
- Private M11 `southamerica-east1` `GET /v1/status`: HTTP `200`, latency
  `4.898859329s`.

The paired result proves App Check authentication passed and the request was
forwarded through the public edge to private M11; the first bounded request
hit the scale-to-zero/cold-upstream path. An immediate second cold-process
probe succeeded and logged exactly:

`ValuePilotM12_3: M12_3_STATUS=200`

The preserved negative boundaries remain: missing or invalid App Check returns
`401`, an unknown edge route returns `404`, and direct private M11 access
returns `403`. No token, credential, authorization header, service identity or
response body is logged.

The 504 is a known reliability observation, not a security qualification
failure. M12.4 must measure and harden this scale-to-zero/cold-upstream path
before live Search is considered production-ready. M12.4 is **NOT_STARTED**.
