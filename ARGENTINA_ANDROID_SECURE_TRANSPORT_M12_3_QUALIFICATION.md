# ValuePilot Argentina M12.3 — Android secure transport qualification

Updated: 2026-09-16

## Qualification label

`IMPLEMENTED_TESTED_NOT_PLAY_QUALIFIED`

This checkpoint qualifies the Android code-level secure-networking foundation
and its deterministic offline tests. It does not qualify a production Play
Integrity attestation or a live release-APK request because no Android device
is connected and the available Play Console session is still at developer
account signup. M12.4/live Search remains out of scope.

## Repository and Firebase identity

- Repository: `topmanneedboys/morefoodperdollar`
- Branch: `candidate/argentina-android-consumer-bridge-m12-v2`
- Starting head: `75fefc824730b57e71c68729fdc667894994b7b5`
- M12.3 code head: `f0cd928` (`Implement M12.3 secure Android edge transport`)
- Firebase project: `valuepilot-argentina` (ACTIVE, project number `17260882200`)
- Android app: `ValuePilot Android`, package `com.valuepilot.app`
- Firebase App ID: `1:17260882200:android:446eec76e5f1a175f2566d`
- Registered debug SHA-256: `0f31bb4d297d70d1d0830cecd74a2e26a08dd50a3337d00b2d5e33ba23240638`
- `google-services.json` is the authoritative Firebase client configuration;
  it contains client identifiers only, not a service-account key or private
  credential.

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

## Remaining qualification boundary

Play Integrity requires a Play Console app and Cloud-project linkage. The
available signed-in session shows only “Creating a Play Console developer
account”; no account, app, or linkage was changed by this milestone. The one
manual action needed before a real-device smoke is:

> In Google Play Console, open or create the Android app whose exact package
> name is `com.valuepilot.app`, then go to **Release → App integrity → Play
> Integrity API → Link Cloud project** and select `valuepilot-argentina`.

After that action, the next bounded checkpoint can verify the release signer
and Play Integrity App Check on a physical device. Until then the exact label
above remains the truthful status.
