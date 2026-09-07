# Known Issues

Updated: 2026-09-07

## Current verification status

The current promoted Practical Shopping Home consumer slice is code SHA `98d2a16897a08ed42e431ffc1627a8090a3e8fac` (`Reduce barcode and shared-text handoff friction`). Candidate workflow **34068374176** passed (Android job **101581077889**, browser job **101581077789**, release-bundle job **101581853530**) and recorded candidate release digest `sha256:0cf7ffd301cbac6df8085b4b4357112aa1c3ad1515671a990ddbbd3498d64499`. Milestone workflow **34068693057** and provenance job **101581946788** verified the exact promoted ref, candidate lineage and bundle checksums (provenance-copy digest `sha256:9a9aa6020a4ae655f21e4857d27b1953dce794d6f2f4e3b7052d30e699b149e8`). Explicit barcode identity and intentionally shared text now fill the earliest blank Compare Here slot or append one next bounded slot when capacity remains; at the maximum they fail closed without replacing existing entries. These identity/raw-text-only handoffs do not infer package, quantity, price, store, availability, evidence or ranking facts, and the shopper still reviews and completes the entry. The change aligns them with the existing bounded OCR append behavior and changes no planner output, sample/demo meaning, identity, offers, stores, availability, evidence, ranking, private-memory, persistence or networking authority. Local verification passed 405 shared-core tests, 1,617 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device handoff/OCR/accessibility/latency validation and lawful production Home activation remain open; provider/current-offer activation remains blocked pending explicit rights.

### Superseded prior verification status

The current promoted Practical Shopping Home consumer slice is code SHA `aea8f4b525262ba56e2242c6d8731d4a0c079e42` (`Show private price movement on Home`). Candidate workflow **34066928777** passed (Android job **101577217659**, browser job **101577217709**, release-bundle job **101577859420**) and recorded candidate release digest `sha256:2084af061991667f8153c674a78fcec7d7c31eebcd37b537099c13661995e08c`. Milestone workflow **34067224897** and provenance job **101578003778** verified the exact promoted ref, candidate lineage and bundle checksums (provenance-copy digest `sha256:db4b5fc1935a1342cc733b33f238c3c95ecba16778ae03ae407db643d05be234`). Home now adds a deterministic `Since previous` movement line to each private exact-package history highlight when at least two comparable observations exist: Down, Up or Same; one-observation and non-comparable histories remain without movement. This reuses the existing bounded history projector/grouping and changes no planner output, sample/demo meaning, identity, offers, stores, availability, evidence, ranking or networking authority. Local verification passed 405 shared-core tests, 1,615 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed offline-catalog and store-directory checks. Physical-device history/accessibility/latency validation and lawful production Home activation remain open; provider/current-offer activation remains blocked pending explicit rights.

### Superseded prior verification status

The current promoted Practical Shopping Home consumer slice is code SHA `05f5728fee413df4e4c39f3e7689be16baa24370` (`Show private price memory highlights on Home`). Candidate workflow **34065396943** passed (Android job **101573144487**, browser job **101573144374**, release-bundle job **101573719671**) and recorded candidate release digest `sha256:77d32d5c4b7fed43535b0d0ea241718d8c060bf74a2777c4e4c388d49f0e0932`. Milestone workflow **34065762760** and provenance job **101574117585** verified the exact promoted ref and candidate lineage (provenance-copy digest `sha256:8f9f849e1a50aafda0b54373410002fcceea3921e92411b4a27de6d365c4840f`). Home now exposes up to three deterministic private exact-package history highlights with last price, lowest observed price, unit-rate range, observation count and UTC date, explicitly labeled personal historical context rather than live store pricing; unreadable history remains hidden and existing review/export/forget actions remain available. This reuses the existing bounded history projector and does not change planner output, sample/demo meaning, identity, offers, stores, availability, evidence, ranking or networking authority. Local verification passed 405 shared-core tests, 1,614 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed offline-catalog and store-directory checks. Physical-device history/accessibility/latency validation and lawful production Home activation remain open; provider/current-offer activation remains blocked pending explicit rights.

### Superseded prior verification status

The current promoted Practical Shopping Home consumer slice is code SHA `7131f5d88c47344e43240151d9298a9b43d253e4` (`Explain multi-image share boundary`). Candidate workflow **34063749025** passed (Android job **101568792509**, browser job **101568792650**, release-bundle job **101569513004**) and recorded candidate release digest `sha256:7dab58da05ec2e7a5bdc243e98b729ab6b8003243e91bb3273ee2f4908147c4b`. Milestone workflow **34064071025** and provenance job **101569636233** verified the exact promoted ref and candidate lineage (provenance-copy digest `sha256:2d99922178e5873e438033ce38445ba268cd7187b8a3d45c55e42c9f4e74f0bc`). A multi-image ClipData share now fails closed with an explicit one-image explanation; single-image shares still use the bounded content-only local OCR review route, and no parsing, evidence, persistence, ranking or networking authority was added. Local verification passed 405 shared-core tests, 1,612 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed offline-catalog and store-directory checks. Physical-device share/OCR, barcode/camera/accessibility/latency validation and lawful production Home activation remain open; provider/current-offer activation remains blocked pending explicit rights.

### Superseded prior verification status

The latest promoted Practical Shopping Home consumer slice is code SHA `7a119e60aa7187eb21389c77f7e9db9ebea52fc2` (`Accept ClipData image shares`), following `7b002bf5ee193ef122ef6b30c9c61230fd2b988c` (`Retain shared image while OCR is pending`). Candidate workflow **34062492992** passed (Android job **101565374797**, browser job **101565374948**, release-bundle job **101566120560**) and recorded release artifact digest `sha256:245c070efa1b82541db99ce690caddcd687869f19e8234dc1fb0fe08068f9eb2`. Milestone workflow **34062799732** and provenance job **101566229176** verified the promoted milestone ref and exact candidate artifact lineage (provenance-copy artifact digest `sha256:115998f286d75e6792882c90f53a125ee75ffb28e75b40f932a20e87395aaf0d`). Share-to-ValuePilot accepts one bounded image through either `EXTRA_STREAM` or single-item `ClipData`; multi-item shares fail closed. The URI reaches the existing local Compare Here photo/OCR review path with temporary read permission and no bytes, parsing, evidence, persistence or ranking authority. OCR remains editable/unconfirmed and all exact package, currency, price, promotion, evidence, private-memory and planner gates remain unchanged. Local verification passed 405 shared-core tests, 1,606 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint, debug/release assembly, APK privacy, single-signer, signed offline-catalog and store-directory checks. Physical-device share/OCR, barcode/camera/accessibility/latency validation and lawful production Home activation remain open; provider/current-offer activation remains blocked pending explicit rights.

### Superseded prior verification status

The previously recorded promoted Practical Shopping Home consumer slice was code SHA `7f4126932713f6844363cf81d9f907d8089cb18f` (`Surface saved exact product context on Home`) with candidate workflow **34056970343** and milestone provenance workflow **34058400728**.

The latest promoted code tree at `32184ca0782dc7f82ac7583364edee6660b79236` has passed candidate
workflow **34047828847** (Android job **101525964170**, browser job **101525964196**, release-bundle
job **101526604524**) and milestone provenance workflow **34048136063** (job **101526778010**).
With an empty Compare Here slot or blank Good Price field, one exact offline barcode identity now
flows directly into the existing editable name field without a redundant choice dialog; exact
quantity, currency, price, promotion and evidence remain manual and unconfirmed. Multiple matches
and replacement/data-loss cases retain explicit confirmation. Local verification passed 402
shared-core tests, 1,591 Android tests, 80 Python/catalog tests and 30 browser tests, plus lint,
debug/release assembly, APK privacy, single-signer, signed offline-catalog and store-directory
checks. Physical-device barcode/camera/accessibility/latency validation and lawful production Home
activation remain open; provider/current-offer activation remains blocked pending explicit rights.
The preceding promoted Good Price tree at `7229879d9fe1df9bec6d4b2d5dd25e360c8242f2` remains in
history with candidate workflow **34046055514** and milestone provenance workflow **34046391345**.

The latest promoted code tree at `7229879d9fe1df9bec6d4b2d5dd25e360c8242f2` has passed candidate
workflow **34046055514** (Android job **101521179647**, browser job **101521179493**, release-bundle
job **101521994885**) and milestone provenance workflow **34046391345** (job **101522085801**).
When no exact personal history matches a Good Price check, its result now states that no matching
history is available and its disclosure uses only the exact entered price; it no longer promises a
save before storage success is known. The preceding promoted Scan & Compare tree at
`a441190602a2968033c85c9d0d324294b2394499` remains in history with its candidate workflow
**34044501200** and milestone provenance workflow **34044815415**.
The old local-toolchain blockers described below are historical baseline notes, not the current
build state. The preceding promoted documentation tree at `dc9811ee92d054fe5033e7af073b313f1d145370` had passed the
candidate workflow **34042210768** (Android job **101510898036**, browser job **101510897877**,
release-bundle job **101511704903**) and milestone provenance workflow **34042551442** (job
**101511807873**). Local verification also passes 402 shared-core tests, 1,586 Android tests, 80
Python/catalog tests and 30 browser tests, plus lint, debug/release assembly, APK privacy,
single-signer, signed offline-catalog and store-directory checks. The remaining milestone issue is
physical-device validation on the Motorola Edge 2025; provider/current-offer activation remains
blocked pending explicit lawful rights.

## Crash evidence

- The latest Motorola crash is not root-caused: the repository contains no AndroidRuntime, ANR, tombstone, or bugreport evidence. Confirmed lifecycle/concurrency risks and the exact next-run capture commands are in `CRASH_ROOT_CAUSE.md`.
- The legacy overlay still owns domain results/filter/ranking presentation decisions. New immutable state/intents/contracts exist, but full migration is intentionally incomplete in Session 1.
- Android Live Accessibility/overlay/OCR behavior is experimental and is no longer the permanent product foundation.
- Historical baseline (superseded): Session 1 source changes were not yet build-verified when Gradle distribution retrieval failed with `Network is unreachable` and browser integration/Firefox validation lacked npm dependencies. The pinned JDK 17/API 36/Gradle/npm toolchain and current hosted/local gates now pass; retain this note only for incident history.
- Historical baseline (superseded): Session 2 `shared-core` Gradle wiring and Kotlin tests were once uncompiled locally. The platform-neutral source is now covered by the promoted shared-core and Android verification gates above.
- Legacy `ValueEngine`, session detection, relevance, repository, and application-state code remain in the app module because they still depend on app-local parsed models, JVM canonicalization/display formatting, `Double` calculations, or Android-backed model loading. See `PLATFORM_DEPENDENCY_MAP.md`; do not copy them wholesale into shared core.
- Session 3 removed hidden session clocks, global model calls from `ValueEngine`, and locale-formatted identity keys; those Kotlin changes are now build-verified by the current gates. Legacy promotion/budget/ranking money still uses `Double`; convert only behind golden compatibility tests.

## Milestone blockers

- Physical-device validation is pending. The automated 20–500 fixtures and operation-count evidence pass, but the reported Motorola Edge 2025 has not run this v101.1 APK. Sustained responsiveness, frame timing, memory, and real Uber Eats/Walmart navigation remain not-yet-device-verified.

## Verified platform limits

- Some apps omit product text, expose unstable accessibility paths, or use canvas/custom surfaces. ValuePilot reports missing evidence and will not coordinate-click an uncertain row.
- Exact row reopening can fail safely if the search/store changes, the app changes a price or size, the card fingerprint changes, duplicate cards are ambiguous, or the list cannot scroll far enough. A failed search can leave the underlying list at a different scroll position.
- Off-screen reacquisition searches downward and then upward, up to 90 steps per direction. Very large or nonstandard pagers may exceed that bound.
- Secure windows can reject screenshot capture. OCR then reports the limitation and leaves Accessibility results intact.
- Search-field/store/page detection is heuristic because third-party apps control their accessibility semantics. Strong text-change sessions, explicit IDs/headings, store/page fingerprints, and final query relevance provide layered protection.
- The supplied APK is debug-signed for testing, not a Play-distribution release.
- The uploaded v101 APK and this locally built v101.1 testing APK use different Android debug certificates (`6b3069…e4a9` versus `63f085…4890`). Android will reject an in-place update; uninstall the old v101 app before installing this test build. That clears ValuePilot's local settings.

## Build maintenance

- The project targets/compiles API 36. The installed lint version reports that API 37 is available; upgrade only after adding SDK 37 and completing compatibility testing.
- RecyclerView 1.2.1 is selected consistently with the current Material dependency graph. Lint reports 1.4.0 as available; upgrading requires a clean dependency build and regression run.
- Android lint passes but retains version-availability warnings noted above.

No iOS, backend, Supabase, cross-store, Universal Cart, or Basket Optimizer implementation has started.
