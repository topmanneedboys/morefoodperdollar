# ValuePilot Argentina M12.3 — Android secure transport qualification

Updated: 2026-09-17

## Qualification label

`PLAY_INTERNAL_TESTING_READY_NOT_DEVICE_QUALIFIED`

This checkpoint qualifies the Android code-level secure-networking foundation,
the accepted and active Google Play Internal testing release, the Firebase
Play Integrity registration, and the deployed edge's negative security
boundary. It does not qualify a real Play Integrity attestation or a valid
Play-backed release request because no Android device is connected. M12.4/live
Search remains out of scope.

## Repository and Firebase identity

- Repository: `topmanneedboys/morefoodperdollar`
- Branch: `candidate/argentina-android-consumer-bridge-m12-v2`
- Current checkpoint head: `756c7c815b5e404e72ccadc3d1f12840543f158c`
- M12.3 code head: `f0cd928` (`Implement M12.3 secure Android edge transport`)
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
- Internal testing track: active, full rollout, one version code `10101`,
  version name `101.1.0`; the release is named `ValuePilot 101.1.0 M12.3
  Internal` and is available to internal testers.
- Play artifact: `10101.aab`, API levels `23+`, target SDK `36`.
- Internal tester list: `ValuePilot M12.3 Internal Trusted`, one account;
  opt-in URL: `https://play.google.com/apps/internaltest/4701231628553906260`.
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

## Remaining qualification boundary

The remaining boundary is physical-device proof. `adb devices -l` currently
shows no connected device, so there is no valid Play-backed App Check token or
release transport `200` to record. The one consolidated user checkpoint is:

1. Open the opt-in URL above on the Google account used by Play Store.
2. Join the internal test and install/update `ValuePilot` through Google Play.
3. Launch the Play-installed app; connect/authorize USB only if device logs or
   the final bounded transport smoke are wanted.

After that checkpoint, verify the release provider obtains a real Play Integrity
App Check token and that edge `/v1/status` returns `200`, while preserving the
negative `401`/`404`/`403` checks above. Until then the exact label above remains
the truthful status. Do not mark `ARGENTINA_ANDROID_SECURE_TRANSPORT_M12_3_QUALIFIED`
and do not start M12.4.
