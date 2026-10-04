# ValuePilot Continuation Checkpoint

Updated: 2026-10-04 00:00 UTC

> Current cleanup boundary (2026-09-29): Canadian catalog, store-directory,
> Open Prices and Rakuten/Jamieson references in this historical checkpoint are
> not active runtime sources. The shipped app now uses the Argentina identity
> rail and private/provider-neutral evidence; see
> `LEGACY_CANADIAN_DATA_SOURCE_CLEANUP.md`.

## Unified Stage 7 legacy-surface consolidation checkpoint — 2026-10-04

The bounded Compare / Good Price / capture presentation slice is complete on
`work/valuepilot-confirmed-purchases` at implementation commit
`a19b1edb00fc4eb2370876a2eccd0bfbf27a1eba`. Compare uses shared cards and
progressive details while preserving exact evaluator output and suppressing
unsupported winners. Good Price visibly labels its basis as the exact entered
price only or matching private history; its longer evidence explanation is
progressively disclosed and remains non-live personal context.

Photo, barcode and shared-content adapters remain user-triggered and local. The
manual Compare path is primary, capture helpers sit below its result, and the
barcode/share review surfaces use the same warm cards and actions. Optional
Accessibility capture remains secondary adapter functionality. No new business,
price, quantity, ranking, provider, network or overlay authority was added.

VersionCode `10124` / versionName `101.1.23`; local release AAB:
`android/app/build/outputs/bundle/release/app-release.aab`, 35,026,755 bytes,
SHA-256 `735948BA159D77356E3CC2C72BC56C26866441C22F581B120CE91C5F487BB2A5`.

Exact-head local verification is green: 411 shared-core tests, 1,650 Android
JVM tests, debug/release lint, debug/release builds, release bundle,
`git diff --check`, and privacy inspection. INTERNET remains intentional;
ACCESS_NETWORK_STATE and background location are absent. The AAB is unsigned;
no signing, Play upload, phone installation or GitHub Actions retry was done.

## Unified Trip Planner decision presentation checkpoint — 2026-10-04

The bounded Trip Planner presentation slice is complete on
`work/valuepilot-confirmed-purchases` at commit
`2522538e91c7447971d28655cc8aec16aee1afaa`. Existing trip-cost and whole-basket
decision projectors remain the only authority. The route now presents Basket
coverage, destination/origin, exact manual trip cost, a plain-language
break-even threshold and compact evidence chips for usable current,
history/personal, current unknown and needs-review lines. Unknown and
incompatible evidence remains explicit; no current-price, inventory, cheapest-
store or savings claim is inferred.

One primary next action is visible at a time. Secondary trip actions and exact
assumptions/details are progressively disclosed. Shared card/touch/accessibility
patterns and bounded reveal motion are reused, and the app shell preserves
Trip Planner scroll position across route changes and recreation. The View emits
typed actions only; no new business, store, price, ranking, network, location or
persistence authority was introduced.

VersionCode `10123` / versionName `101.1.22`; local release AAB:
`android/app/build/outputs/bundle/release/app-release.aab`, 35,023,894 bytes,
SHA-256 `25808575EB3B864A3D6A32C8A3F606C3310CAA5B228805F74EA7314BA8B300ED`.

Exact-head local verification is green: 411 shared-core tests, 1,645 Android
JVM tests, debug/release lint, debug/release builds, release bundle,
`git diff --check`, and privacy inspection. INTERNET remains intentional;
ACCESS_NETWORK_STATE and background location are absent. Foreground location
remains the existing optional user-triggered permission. The AAB is unsigned;
no signing, Play upload, phone installation or GitHub Actions retry was done.

## Unified personal shopping area checkpoint — 2026-10-04

The bounded Saved / confirmed-purchase-history / Price Watch presentation slice
is complete on `work/valuepilot-confirmed-purchases` at commit
`1348ed26da204b23d49295ec549b8f6153755bb8`. Existing projectors, typed actions
and private stores remain the only data authority. The three personal surfaces
now share `ValuePilotProductCard`, stable keyed row reuse, a single visible
primary action, and progressive More disclosure for secondary/destructive
actions and personal history. Unknown current offers remain unknown.

The app shell preserves personal scroll position across Saved, History and Price
Watch refreshes, route changes and recreation. No pricing, identity, provider,
ranking, persistence, network or View authority was added. VersionCode `10122`
/ versionName `101.1.21`; local AAB:
`android/app/build/outputs/bundle/release/app-release.aab`, 35,016,030 bytes,
SHA-256 `31068E7538CCBF8DDCC78E8386BC34BEFDF459A20CD2DCFBD38CC63BD7F3EDC3`.

Exact-head local verification is green: 411 shared-core tests, 1,642 Android
JVM tests, debug/release lint, debug/release builds, release bundle,
`git diff --check`, and privacy inspection. INTERNET remains intentional;
ACCESS_NETWORK_STATE and background location are absent. No signing, Play
upload, phone installation or GitHub Actions retry was performed.

## Exact product-to-nearby-store map checkpoint — 2026-09-29

The bounded Search → View on map milestone is complete locally on
`work/valuepilot-confirmed-purchases`, based on
`5214819a1ac3cfdaee7bf8d4a178e057815af57f`. Only qualified Argentina SEPA
identity results expose the map action. The selected Search
`productEvidenceKey` is carried into the existing direct shop request, and the
backend trusts it only when it is an explicit item field; arbitrary public
top-level identity injection is still ignored.

The actual qualified schema audit found product identity/optional GTIN,
commerce/banner/store identifiers, store name/address/locality/province,
latitude/longitude/geo status, exact product-to-store offer relationships,
published price and release/update provenance. The map adapter reuses the
existing plan lines and store declarations, never copies the national catalog
or invents a relationship. Wrong packages/GTINs, malformed coordinates and
coordinates outside a broad Argentina envelope fail closed; exact store keys
deduplicate; conflicting prices are hidden. SEPA publication remains release
evidence only, with availability and inventory UNKNOWN.

The map is a provider-neutral, tile-free coordinate canvas. MapLibre Native
Android was reviewed as a future renderer, but no tile provider or permanent
OSM tile backend was added. It works without location around the qualified
CABA search-area center and labels that origin. A separate explicit user action
can use one foreground location reading to re-sort pins by approximate
straight-line distance. There is no background/continuous tracking. Road
routing, driving distance/time, live stock and current-price claims are not
provided.

Map-focused coverage is 10 tests. Complete local verification is green: 438
shared-core tests, 1,784 Android JVM tests, 114 backend tests, 330 tooling
tests, debug/release lint, debug/release builds, release bundle, APK privacy
inspection (no `ACCESS_NETWORK_STATE` or background location), and
`git diff --check`.

Unsigned candidate: versionCode `10118`, versionName `101.1.17`, package
`com.valuepilot.app`; `android/app/build/outputs/bundle/release/app-release.aab`,
36,463,757 bytes, SHA-256
`7F62F2D70A4957FABD291C7EC2513A0BF9646F8A1FCA912EC5B60825A9C293C6`.
No signing, Play upload, phone install or GitHub Actions retry was performed;
the Actions allowance/billing lock remains external and unresolved.

## Connected Shopping Decision Engine checkpoint — 2026-09-29

The next shared-core milestone is complete locally on
`work/valuepilot-confirmed-purchases` at commit
`dbe1fe4aaaf0d53854197c9fe7f0b44b0aeab83a`. `ShoppingDecisionEngine` reuses the
existing exact Money/NormalizedQuantity, Basket identity, confirmed-purchase,
Price Watch, store-key and Trip Planner facts without copying them into a new
store. It evaluates bounded whole-basket scenarios for explicit stores and
keeps current authorized offers, historical retailer observations, confirmed
purchases, personal observations, watch targets, unknowns and estimated travel
as separate evidence classes.

The result includes per-line provenance IDs, deterministic package-count
arithmetic, unknown/conflict/currency blockers, partial and complete totals,
historical context, explicit trip cost, break-even additional savings and a
human-readable explanation. No store is ranked and no unsupported current
savings or inventory claim is produced. Trip Planner now displays the result
read-only from the current Home/Basket projection; it supplies no live offers.

Focused decision tests: 14. Full local verification: 433 shared-core tests,
1,777 Android JVM tests, debug/release lint, debug/release assemble, release
bundle, privacy check and diff check all passed. VersionCode `10117`,
versionName `101.1.16`, package `com.valuepilot.app`. Unsigned AAB:
`android/app/build/outputs/bundle/release/app-release.aab`; SHA-256
`884BC3AC8F92414F466D469F2836165A03323801E19F6986E26E6B35EB118CA7`;
size 36,441,056 bytes. No signing, Play upload, phone install, GitHub Actions
retry, network source or paid service was used.

## Offline Store and Trip Planner checkpoint — 2026-09-29

The bounded Store/Trip Planner milestone is committed on
`work/valuepilot-confirmed-purchases` at
`37d6182dfba9d2e6ee857162861fe1d2e089b66e`, from the verified Price Watch
checkpoint `2936c3c4e29cd8f85c0a6a85eb80b356181c22bb`. The Basket-owned route
uses the live private Basket projection rather than copying it into durable
trip state. Signed, source-attributed GTA/Metro Vancouver location records
and explicit private store names map to exact durable store keys; confirmed
purchases and personal price observations can carry an explicitly selected
preferred store key without becoming retailer offers.

Trip cost is shared-core exact arithmetic for ROUND_TRIP or ADDITIONAL_DRIVING
distance, whole-millilitre fuel consumption, exact currency/precision fuel
price, optional tolls and parking. Manual origin and assumptions are the
supported path. Foreground location is an optional one-shot permission with
no background tracking and no raw coordinate persistence. Atomic bounded
storage fails closed on corruption/read/write errors and retains the last
known-good scenario. Automatic road routing and map facts are not integrated;
external directions are only a handoff for a source-listed coordinate.
Current offers, inventory and savings are explicitly UNKNOWN and cannot affect
organic ranking. The scenario changes with Basket counts/revision and does
not mutate purchases or Price Watch.

Local verification is green at this exact code: 1,776 app JVM tests, 419
shared-core tests, debug/release lint, debug/release builds, release bundle,
and privacy inspection. The unsigned candidate is
`android/app/build/outputs/bundle/release/app-release.aab` with SHA-256
`78DF8AF169CCC5B1A1E894350AE90DAFEFC37F1A0428C75E936D2A55C6590C87`,
versionCode `10116` / versionName `101.1.15`, package
`com.valuepilot.app`. No Play signing/upload, device install, GitHub Actions
retry or paid service was performed.

## Confirmed purchases and private history checkpoint — 2026-09-29

The next bounded milestone is implemented on `work/valuepilot-confirmed-purchases`
at `9f7f4ee`, on top of the verified `6dbe3112fe6f6073b7335e11b268066531c48d9c`
hardening commit, based on the verified 10113
checkpoint `8722904f645660925008cd7a6c131bee660b00e9`. It adds a provider-neutral
shared-core `ConfirmedPurchase` model, an explicit confirmation boundary from
Basket, and bounded atomic device-only history exposed as a Saved-owned route.
Check-off, Search/Home identity additions, navigation, comparison completion and
private comparison price memory never create purchase records. Unknown quantity,
price, currency, date and store values stay unknown; exact money and quantity
(count, mass, or volume units) are validated without binary floating-point
arithmetic. History supports repeat-to-Home, correction and explicit deletion,
while malformed/read-failed state blocks mutation instead of being replaced with
an empty list. The codec also rejects malformed UTF-8 rather than accepting
replacement characters.

Local verification is green: shared-core tests, the complete app JVM test suite,
debug/release lint, debug/release builds, release bundle, and APK privacy inspection
(existing `INTERNET`/camera only; no `ACCESS_NETWORK_STATE`). The next local
candidate is versionCode `10114` / versionName `101.1.13`, unsigned and not
uploaded or installed. GitHub Actions and Play/phone work were not started.

## 10113 Google Images refinement checkpoint — 2026-09-29

The focused viewer refinement is committed at
`0cb655c3ebde2bfa219cfc4393e84e56543d0485` on
`work/valuepilot-m128-m129`. Supported Custom Tabs now request an adjustable
partial tab starting at 65% of the display height (with a 320dp small-screen
floor). If the provider rejects the partial launch, ValuePilot presents an
explicit full-screen choice instead of opening a browser automatically. The
existing unverified, explicit-tap-only Google Images query, double-tap guard,
state preservation, Back/rotation handling and no-download/no-cache boundary
remain unchanged.

The next local candidate is versionCode `10113` / versionName `101.1.12` at
`android/app/build/outputs/bundle/release/app-release.aab`; it is unsigned,
36,314,196 bytes, SHA-256
`00AD039ABEDEF27BA488783949BDD455163722CC361BD062DBBAAC7BFA91AB12`, and
has not been signed, uploaded or installed. The protected signed 10112 AAB is
preserved and does not contain this refinement.

Focused viewer tests and the complete local tree passed: 1,732 Android app
JVM tests, 405 shared-core tests, debug/release lint, debug/release builds,
release bundle, and privacy inspection (no `ACCESS_NETWORK_STATE`). GitHub
Actions retries and Play uploads remain paused while the account billing /
spending-limit blocker is unresolved; Motorola validation is deferred.

## 10112 integration checkpoint — 2026-09-29

The current stacked candidate is `work/valuepilot-m128-m129` at
`080f24e998679e8173862f4b6bd217dc195023f6`. PR #41 is based on `main`, PR #42
is based on PR #41, and PR #43 is based on PR #42; all three remain open and
GitHub reports them mergeable at their respective bases. A read-only
`git merge-tree --write-tree origin/main HEAD` produced tree
`4ea3ee61af529b5b339c21ef476509626b4412df` with no conflicts, so the complete
stack can be integrated after the normal review gates without duplicating or
prematurely merging commits.

The combined code retains Search query/page accumulation, bounded Load More,
navigation and recreation state rules; durable private Home/Basket identities;
exact-key deduplication and checklist projection; the Secadero partial-state
and malformed-durable-state protection; and independent Search Photo and Save
buttons with separate accessibility labels and callbacks. No price, stock,
ranking, provider or Android-network authority moved into these surfaces.

Final local verification at this code tree passed: 1,730 Android app JVM tests,
405 shared-core tests, debug/release lint, debug/release builds, and
`:app:bundleRelease`, with zero failures/errors/skips. Privacy inspection found
`INTERNET` as the existing edge permission and no `ACCESS_NETWORK_STATE`.
VersionCode `10112` / versionName `101.1.11` was checked as unused in Play
Console. The protected-key signed candidate is local only at
`android/app/build/outputs/bundle/release/app-release-play-upload-signed-10112.aab`,
SHA-256 `DBF0AC4212F8AB9F95BD16C5D3A7EDAF93878EFC3F1103B0EA70ADA708F05356`,
with upload certificate SHA-256
`A0:B6:5D:8A:5A:F9:44:9E:76:6F:05:39:6F:E1:78:20:D5:2F:26:7C:E9:D5:8A:B0:C2:F6:CD:E4:DC:B8:52:DA`.
It has not been uploaded or installed. Remaining gates are the blocked GitHub
Actions account billing/spending-limit state and later Motorola validation;
physical testing is intentionally not required in this integration checkpoint.

GitHub runs `36517192131` and `36517195056` were not started because recent
account payments failed or the spending limit must be increased. No rerun or
paid usage was requested.

## Historical M12.4–M12.9 checkpoint — 2026-09-27

Work continues on `work/valuepilot-m124-m129` from the verified Data Status
repair `dcb40372bd0ed6f45afc87fea405d1dfdf01775b`; that repair was not
repeated. The untracked `device-audit/` directory remains untouched. The
latest feature commit before this documentation checkpoint is
`d14a39565852fae3c3817d191476aced262e9d27` and
`910a694dc6505a9313924b609511e147b48de6f3`.
PR #29 and PR #30 merged these Search trust-copy repairs; remote `main` is
`0deba71ddf101c1f79827f0898bf0b6344c15e7b` after merged PR #30.
PR #24 exact-head workflow `36320656523` and post-merge main workflow
`36321271157` passed browser and Android verification. PR #25 exact-head
workflow `36321466323` and post-merge main workflow `36322095653` also passed
browser and Android verification; provenance/release-bundle jobs were skipped
by policy. PR #27 exact-head workflow `36324495187` and post-merge main
workflow `36325161398` passed browser and Android verification; provenance and
release-bundle jobs were skipped by policy.
PR #29 exact-head workflow `36327482792` and post-merge main workflow
`36328146814` passed browser and Android verification; PR #30 exact-head
workflow `36329032948` and post-merge main workflow `36329673905` also passed.
The merged repairs align both Search disclosure copies with the actual
identity-only boundary: package size appears only when explicitly supplied by
the source, while current prices, stock, availability, freshness and ranking
remain absent. Focused tests and the full local debug/release gate passed.
The Search surface now also reports the bounded number of deduplicated identity
suggestions currently visible after each page/Load More operation. This is
presentation-only and does not imply price, stock or availability.

PR #32 is merged on main at `c7d820a1246dc30c47b5fcd1438d05881db8a90b`.
The single [M12.4–M12.9 work ledger](M12_4_M12_9_WORK_LEDGER.md) records the
actual remaining gates. The M12.6 discovery audit now distinguishes the old
front-image-only measurement from the new bounded rights-aware exact-product
manifest; no full-export coverage or 90% result is claimed.

The merged M12.8 handoff lets a Search identity result enter the private Home
draft as one unresolved multi-word item. Search cards now show an explicitly
validated Argentina package size when the source supplies one; a missing
quantity stays unknown. These remain identity-only changes and do not create a
price, stock, availability, ranking or planner claim.

PR #21 (`Serialize Argentina SHADOW reconciliation`) is merged. It makes the
GitHub acquisition handoff run before SHADOW reconciliation, removing the
same-run durable-state race, while `always()` still permits recovery
reconciliation after an acquisition failure. Exact-head PR runs
`36315163770` and `36315146325` passed; post-merge main runs
`36315728824` and `36315728794` passed. Android test/lint/build, privacy,
single-signer, browser and acquisition-control checks were green. Release and
provenance uploads were skipped by policy; this is not a release promotion. PR #22 exact-head run
`36316729499` and post-merge main run `36317378112` also passed browser and Android test/lint/build,
privacy and single-signer checks; provenance and release-bundle jobs were skipped by policy.

The M12.5 acquisition controller now reconciles asynchronous SHADOW results
with exact execution identity, requires a write-free verified candidate for
success, records bounded failure evidence, alerts on unsafe terminal results,
and is idempotent. It does not activate unattended production promotion.
The deployed `valuepilot-argentina-release` workflow is ACTIVE at revision
`000013-32a`. Its latest scheduled execution (on revision `000012-0a4`) ran
Cloud Build successfully and persisted candidate
`argentina-sepa-2026-09-25-93ce53aac8d3db32-routing-v1-search-v2`, but returned
`STATE_WRITE_UNVERIFIED` because the discovery-state object could not be
verified. Promotion stayed `SHADOW_ONLY` with zero active-pointer writes, so
this does not qualify the unattended end-to-end path.
The current regional runtime pointers nevertheless read that same
2026-09-25 release, whose complete manifest carries source SHA-256
`93ce53aac8d3db322c07b0bc66900016a91aba6b4ccf858a835bfb5f3f13b680` and
explicitly records `automatedOfficialAcquisitionProven: false`. The backend
advertises the release and Android identity-search authorization, but this is
not physical App Check/pagination evidence.
Ordinary research artifact retention is 3 days; qualified provenance evidence
was preserved. GitHub run `36295847180` remains failed overall because only its
artifact uploads hit the full Actions quota; its reported code checks passed.
The build workflow now skips disposable uploads on ordinary work branches and
retains them only for explicit candidate/milestone pushes.
Rebuildable Open Prices reports are now uploaded only for manual workflow
dispatches; normal push/PR validation keeps the summary without an artifact.

The M12.6 image audit is deterministic and offline. Of 66,716 valid-GTIN
identity records (44,683 unique GTINs), 21 strict exact image URL candidates
were found (0.031477%), with 0 per-image licence-qualified and 0 display
eligible. The 389 `yerba` identities had 0 strict image candidates. See
[ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.md](ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.md).
No images or raw exports are stored in Git or Android.

The M12.4/M12.9 status in this historical section was superseded by the
10112 integration checkpoint above. The earlier unsigned 10108 / 101.1.7
candidate and installed 10107 build are historical records only; they are not
the current release candidate.

GitHub Actions storage remains free-first: two durable provenance artifacts
(`10085413603`, `10000282677`) total 76,864,219 bytes and active cache usage
is zero. They are preserved; no provenance or raw data was deleted. Run
`36295847180` remains overall failed because only its artifact uploads hit the
quota, even though its code checks passed.

Branch: `work/valuepilot-m124-m129`

## Latest continuation checkpoint — 2026-09-24

The immutable daily acquisition-state repair is prepared but not merged. PR #9
(`fix/argentina-immutable-acquisition-state`) is open at
`5ea105ab84c25d8784ad40a6f874ad97b4737f29`; acquisition-control and both
Android PR checks are green, and the complete checked-in tooling discovery
passes 86 tests. The repair uses a bounded append-only event journal below
`acquisition/daily-state-events/<day>/`, reads legacy v1 state, and never needs
archive-object overwrite/delete permission. It also rechecks an event with a
bounded `head()` when a provider listing omits its size.

The verified Sep 23 failure was a create-only archive-object replacement
attempt, not an unproven race. Sep 22 remains a separate HTTP 412 after a
SHADOW build; deployed workflow revision `000011-094` already reconciles only
an exact candidate/source/metadata/promotion identity. No production workflow
deployment, national acquisition, IAM change, pointer write, or Play upload
has occurred. M12.5 remains partial pending PR review/merge and one normal
scheduled OIDC run. M12.4 remains partial pending the Motorola Edge 2025
Play-installed Search smoke.

### Prior continuation checkpoint — 2026-09-23

M12.4 candidate code HEAD before this documentation update is
`2176ed8709c5b89ae4c529cfcfc186c982681e82`. Its visible Search implementation
remains **PARTIAL** until a signed Play-installed build passes the physical
Motorola Edge 2025 smoke. No device was attached during this checkpoint.

10104 was signed using the same existing protected local upload-keystore path
as 10103. The protected credential was consumed locally without prompting for
or printing a password. Its signature verifies against the same certificate
fingerprint as 10103; its SHA-256 is
`23633c008c21db9d6ca1a343d96c76f1dc1c5f06da19a32a3dd095476039d5bb`. The
10104 and subsequent 10105 bundles have not been uploaded to Play. The
M12.4 physical-device gate is still outstanding.

M12.5 is **PARTIAL**: the acquisition recovery implementation is merged to
`main` (PR #4), and the Cloud Workflows service-limit fix is merged as
`03b4cee652e46f5739879b05fc59d691baa27b8f` (PR #5). Exact-head Android and
acquisition-control checks passed; `python -m unittest discover -s tools/tests`
passed 78 tests.

The deployed Google Workflow `valuepilot-argentina-release` is ACTIVE at
revision `000011-094` in `us-east4`. Its source SHA-256
`b7d52f2876baeb88ae8766b88b239f89a7d2ece2a7915cc8e0481b2764258381` matches
merged `main`; the prior service account and `LOG_ERRORS_ONLY` logging remain
unchanged. Two live validation-only executions on that revision returned
`INVALID_HANDOFF`, `cloudBuildStarted=false`, and zero active-pointer writes,
covering archive-pair mismatch and missing same-day proof. No national build
was triggered. The next scheduled OIDC acquisition/recovery run remains needed
to qualify the end-to-end production path.

Both runtime regions still point to
`argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2` as active and
last-known-good. Pointer generations remain `1789973690114545` and
`1789973692306062`; no canary pointer exists. Do not start M12.6.

## Current M12.4B visible Argentina identity Search candidate

The bounded M12.4B implementation is complete in code at
`4881750886a26de9e203e52d16963156719733cc` and is documented in
[ARGENTINA_LIVE_SEARCH_M12_4B.md](ARGENTINA_LIVE_SEARCH_M12_4B.md). The visible
Search surface now sends only explicit user queries through the existing
M12.4A coordinator/bridge/transport, shows the qualified Buenos Aires (CABA)
launch area, renders identity-only results, and hands a selected name to the
existing Compare route. It has bounded cancellation/generation behavior and
explicit safe states for loading, no matches, authorization unavailable,
stale release, timeout and temporary failure. No sample Search provider,
startup probe, location permission, new persistence authority, price/stock/
availability/ranking authority or M12.5 work was added to that M12.4B change.
This is historical scope context; M12.5 was separately authorized later and is
tracked in the latest checkpoint above.

Local verification is green: 405 shared-core tests, 1,668 Android unit tests,
46 backend tests, 197 tooling tests and 30 browser tests, plus Android debug /
release assemble, lint and release-bundle tasks. The unsigned `10103 / 1.1.2`
release AAB has the expected package and privacy boundary. The exact official
2026-09-18 release is now mirrored and active; authenticated `/readyz`,
`/v1/status`, and CABA `leche` identity search all passed on the promoted
revision, and the backend reports `FRESH` with
`androidNetworkingAuthorized=true`. The candidate remains
**PLAY_BUILD_READY_NOT_DEVICE_QUALIFIED** because the physical-device smoke is
still pending. The protected upload key has been recovered and verified;
10103, 10104, and 10105 bundles were signed locally, but none has been uploaded
to Google Play. Do not bypass the device gate. The old “No M12.5 work has
started” statement at the original M12.4 checkpoint is superseded by the
separately authorized M12.5 work recorded above.

The signed local artifact is
`android/app/build/outputs/bundle/release/app-release-play-upload-signed-10103.aab`
with SHA-256
`b195d6a6318db823a61f27035cc2b8c66adda392c2040a4b87d71e17b4cd3679`; it has
not been uploaded to Google Play.

The active immutable release id is
`argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2`. The
official source SHA-256 is
`619498b8e3230298b8a91f3df05ee7fe44e190a8f909e0e103cf43867fec73ee`
(`335486139` bytes), the candidate manifest SHA-256 is
`9a558cafcb5d5a162eb04330448612418f437eda7d1f5a3e5ba44e9202c415cf`, and
the release contains 1,163 immutable SearchPack objects. Both runtime-bucket
mirror audits have zero missing, size-mismatch or checksum-mismatch objects;
the pointer update used CAS and required no rollback. Raw provider data is not
in Git.

The public edge/OIDC/App Check evidence and exact cloud details are in the
canonical M12.4B report below; the historical M12.4A and M12.3 sections remain
unchanged for provenance.

## Current M12.4A live Search foundation candidate

M12.4A is a separate candidate branch,
`candidate/argentina-live-search-m12-4`, based exactly on qualified M12.3
head `93f25a072881cb25db6bfc38517d4efc87523af9`. It does not alter the
qualified branch or start M12.4B. The new coordinator is not wired to visible
Search: it only provides a bounded, testable application-edge seam that needs
explicit geography, learns and pins the rolling release from `/v1/status`,
projects identity-only candidates through the existing mapper, and rejects
stale generations, stale releases, cancellation, malformed data, missing
context and an unauthorized Android network gate. No price, quantity,
inventory, ranking or provider-economics authority was added.

The public edge/private OIDC timeout hierarchy is now finite and cold-start
tolerant (12-second private upstream, 20-second Cloud Run service; Android
15-second read and 20-second total). Revision
`valuepilot-argentina-consumer-edge-00004-5lg` is serving 100% of traffic with
the preserved service identity, Firebase configuration and scale-to-zero
policy. Post-deploy checks are 401 without/with invalid App Check, 404 for an
unknown route, and 200 from the real Play-installed M12.3 status probe. The
existing 8.578-second cold 504 is retained as historical reference; Search
timing is marked not remeasured because no visible Search/new Android artifact
is allowed in this milestone. Full repository verification and exact-head
promotion remain the final gates for this candidate. See
[ARGENTINA_LIVE_SEARCH_FOUNDATION_M12_4A.md](ARGENTINA_LIVE_SEARCH_FOUNDATION_M12_4A.md).

## Current M12.2 public consumer-edge checkpoint

Code SHA `842f4b6b3825b98ea8599424206b3451561e6a83` adds the private M11
Cloud Run OIDC adapter and rejects partial target configuration at startup;
the deployed edge/OIDC test boundary is now qualified. The edge is deployed
in `southamerica-east1` as
`valuepilot-argentina-consumer-edge-00003-8fq`, serving image digest
`sha256:274280c381b5c052a661b7606f1219ac71e29556669b36bee292d473b3290a7b`
with scale-to-zero/max-one settings. Its dedicated service account has only
`roles/run.invoker` on `valuepilot-argentina-sa-east1`; direct unauthenticated
M11 remains `403` and the US service remains unbound. The organization policy
rejected an `allUsers` binding, so the edge uses Cloud Run's explicit
invoker-IAM-check disablement while its application boundary still requires
Firebase App Check. The active Firebase project is `valuepilot-argentina`;
`ValuePilot Android` (`com.valuepilot.app`, App ID
`1:17260882200:android:446eec76e5f1a175f2566d`) is registered with the current
debug signer SHA-256 and is the deployed edge's explicit App ID allow-list.

HTTPS smoke is `401` without/with invalid App Check and `404` for an unknown
route. A temporary Firebase debug token was exchanged, used only in memory,
and revoked; the valid-token edge `/v1/status` smoke returned `200` with the
private M11 status payload, and private M11 logs show authenticated requests.
Local edge/backend tests (40) and backend workflow **35062107407** are green;
Cloud Build **232e7ce4-d53e-4a26-9b83-dd05495fc486** and revision
`valuepilot-argentina-consumer-edge-00003-8fq` are ready. This qualifies the
deployed server/test-attestation path only. Real-device Play Integrity and
production Android networking remained unqualified at this M12.2 checkpoint;
the separately authorized M12.3 foundation is recorded below. See
[ARGENTINA_CONSUMER_EDGE_M12_2_QUALIFICATION.md](ARGENTINA_CONSUMER_EDGE_M12_2_QUALIFICATION.md).

## Current M12.3 qualified secure Android networking release

The current candidate contains a deliberately narrow Android HTTPS edge
adapter. `ValuePilotApplication` initializes Firebase and installs Play
Integrity only in release, while the debug source set alone installs the
Firebase debug provider. The transport obtains a normal cached/refreshable App
Check token and places it only in `X-Firebase-AppCheck`; it never sends a
token in a URL, stores it, logs it, or contacts the private M11 origin. The
public edge host and the five already-qualified `/v1/*` routes are
allow-listed. Request/response bytes, connect/read/total time, redirects,
strict UTF-8, cancellation, and typed network/HTTP failures are bounded and
covered by deterministic offline tests.

`INTERNET` is now intentional for the future user-triggered edge route;
`ACCESS_NETWORK_STATE` remains removed and cleartext remains disabled. No
Home/Search live wiring, background sync, provider data, or Android account
state was added. Local debug/release compile and focused transport tests pass.
The Firebase project is active, Play Integrity is linked to it, and Firebase
App Check is registered with Play Integrity (PLAY_RECOGNIZED and LICENSED
required; one-hour TTL). The `10102 / 1.1.1` build is now the qualified
Internal-testing release: Google Play installed it on a Motorola Edge 2025,
completed `requestIntegrityToken` for project `17260882200`, and the immediate
physical-device probe logged `M12_3_STATUS=200` through the public edge to
private M11. The first attempt recorded a bounded 504 cold-start timeout while
private M11 returned 200; carry that reliability requirement into M12.4 before
live Search is production-ready. The exact evidence is in
[ARGENTINA_ANDROID_SECURE_TRANSPORT_M12_3_QUALIFICATION.md](ARGENTINA_ANDROID_SECURE_TRANSPORT_M12_3_QUALIFICATION.md).

## Current M12.1 consumer-edge boundary checkpoint

The current M12.1 candidate is code SHA
`5e829251cc43653e42a8583c2acd2cb2e9c18fb6` (`Harden consumer edge
content-length handling`) on top of `d2d3d2c27e10114460bc46b932e2b4f174a32e8e`.
The separate `consumer_edge` package admits only the five consumer routes,
requires HTTPS/App Check, applies explicit bounded request/response JSON,
concurrency, timeout and rate policies, sanitises upstream failures, and
fails closed until M12.2 supplies a separately verified private M11
upstream. It contains no shopping/ranking/provider/release logic, credentials,
Android networking or provider data. Local verification passed 17 edge tests,
27 backend tests, and 197 tooling tests; exact-head backend workflow
**35058092106** passed. Exact-head Android workflow **35058092151** passed its
browser verification (**104672396676**), Android test/lint/build/privacy/
signing verification (**104672396323**), and release-bundle assembly
(**104674454452**); its milestone provenance proof was correctly skipped on the
candidate branch.
See [ARGENTINA_CONSUMER_EDGE_M12_1_QUALIFICATION.md](ARGENTINA_CONSUMER_EDGE_M12_1_QUALIFICATION.md).

## Current verified engineering head

The latest promoted consumer slice is code SHA `3ed5f7105f2c7d008822202824c3d100289e8a93` (`Clarify bounded barcode handoff capacity`). Candidate workflow **34070119262** passed: 405 shared-core tests, 1,619 Android tests, 30 browser tests, lint/build, privacy and release-bundle checks. Milestone workflow **34070476754** and provenance job **101586761130** verified the exact SHA and candidate artifact lineage (candidate bundle digest `sha256:3019b8be1e71af82c314bea69dba90fddbaed254fa8315f1de67622585a8710e`; provenance-copy digest `sha256:db4f41e5fb7959695669a1b302fe8234d811989645d43d5a1094653f9089353f`). Barcode copy now matches the append-when-capacity-remains behavior, while full drafts explain the 32-entry bound; the final-slot and no-overwrite cases are deterministic tests. No shopping or evidence authority changed. Physical-device and lawful provider activation remain open.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `98d2a16897a08ed42e431ffc1627a8090a3e8fac` (`Reduce barcode and shared-text handoff friction`). Candidate workflow **34068374176** passed for the exact SHA: Android job **101581077889** (405 shared-core tests, 1,617 Android tests, test/lint/build, privacy and single-signer), browser job **101581077789** (30 tests and Firefox package checks), and release-bundle job **101581853530**; candidate release artifact digest `sha256:0cf7ffd301cbac6df8085b4b4357112aa1c3ad1515671a990ddbbd3498d64499`. Milestone workflow **34068693057** and provenance job **101581946788** verified the exact promoted SHA, candidate lineage and bundle checksums (provenance-copy digest `sha256:9a9aa6020a4ae655f21e4857d27b1953dce794d6f2f4e3b7052d30e699b149e8`). Explicit barcode identity and intentionally shared text now fill the earliest blank Compare Here slot or append one next bounded slot when capacity remains; at the maximum they fail closed without replacing existing entries. These handoffs remain identity/raw-text-only: they do not infer package, quantity, price, store, availability, evidence or ranking facts, and the shopper still reviews and completes the entry. The change aligns them with the existing bounded OCR append behavior and changes no planner output, sample/demo meaning, identity, offer, store, availability, evidence, ranking, private-memory, persistence or networking authority. Local verification passed 405 shared-core tests, 1,617 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed catalogs and signed store-directory checks. Physical-device handoff/OCR/accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `aea8f4b525262ba56e2242c6d8731d4a0c079e42` (`Show private price movement on Home`). Candidate workflow **34066928777** passed for the exact SHA: Android job **101577217659** (405 shared-core tests, 1,615 Android tests, test/lint/build, privacy and single-signer), browser job **101577217709** (30 tests and Firefox package checks), and release-bundle job **101577859420**; candidate release artifact digest `sha256:2084af061991667f8153c674a78fcec7d7c31eebcd37b537099c13661995e08c`. Milestone workflow **34067224897** and provenance job **101578003778** verified the exact promoted SHA, candidate lineage and bundle checksums (provenance-copy digest `sha256:db4b5fc1935a1342cc733b33f238c3c95ecba16778ae03ae407db643d05be234`). Home now adds a deterministic `Since previous` movement line to each private exact-package history highlight when at least two comparable observations exist: Down, Up or Same; one-observation and non-comparable histories do not imply movement. This reuses the existing private-history projector/grouping and changes no planner output, sample/demo meaning, identity, offer, store, availability, evidence, ranking or networking authority. Local verification passed 405 shared-core tests, 1,615 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed catalogs and signed store-directory checks. Physical-device history/accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `05f5728fee413df4e4c39f3e7689be16baa24370` (`Show private price memory highlights on Home`). Candidate workflow **34065396943** passed for the exact SHA: Android job **101573144487** (405 shared-core tests, 1,614 Android tests, test/lint/build, privacy and single-signer), browser job **101573144374** (30 tests and Firefox package checks), and release-bundle job **101573719671**; candidate release artifact digest `sha256:77d32d5c4b7fed43535b0d0ea241718d8c060bf74a2777c4e4c388d49f0e0932`. Milestone workflow **34065762760** and provenance job **101574117585** verified the exact promoted SHA and candidate lineage (provenance-copy digest `sha256:8f9f849e1a50aafda0b54373410002fcceea3921e92411b4a27de6d365c4840f`). Home now shows up to three deterministic private exact-package history highlights with last price, lowest observed price, unit-rate range, observation count and UTC date, explicitly labeled personal historical context rather than live store pricing; unreadable history remains hidden and existing review/export/forget actions remain available. This reuses the existing bounded history projector and changes no planner output, sample/demo meaning, identity, offer, store, availability, evidence, ranking or networking authority. Local verification passed 405 shared-core tests, 1,614 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed catalogs and signed store-directory checks. Physical-device history/accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `7131f5d88c47344e43240151d9298a9b43d253e4` (`Explain multi-image share boundary`). Candidate workflow **34063749025** passed for the exact SHA: Android job **101568792509** (405 shared-core tests, 1,612 Android tests, test/lint/build, privacy and single-signer), browser job **101568792650** (30 tests and Firefox package checks), and release-bundle job **101569513004**; the candidate release artifact digest is `sha256:7dab58da05ec2e7a5bdc243e98b729ab6b8003243e91bb3273ee2f4908147c4b`. Milestone workflow **34064071025** and provenance job **101569636233** verified the exact promoted SHA and candidate lineage (provenance-copy digest `sha256:2d99922178e5873e438033ce38445ba268cd7187b8a3d45c55e42c9f4e74f0bc`). Share-to-ValuePilot accepts one bounded image through `EXTRA_STREAM` or single-item `ClipData`; a multi-image share now explains that the shopper must choose one image instead of looking empty or silently dropping pages. No parsing, OCR, evidence, persistence, ranking or network authority changed. Local verification passed 405 shared-core tests, 1,612 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus Android lint/debug+release assembly, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device share/OCR, barcode/camera, accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The latest promoted consumer slice is code SHA `7a119e60aa7187eb21389c77f7e9db9ebea52fc2` (`Accept ClipData image shares`). Candidate workflow **34062492992** passed for the exact SHA: Android job **101565374797** (405 shared-core tests, 1,606 Android tests, test/lint/build, privacy and single-signer), browser job **101565374948** (30 tests and Firefox package checks), and release-bundle job **101566120560**. The candidate release artifact digest is `sha256:245c070efa1b82541db99ce690caddcd687869f19e8234dc1fb0fe08068f9eb2`. Milestone workflow **34062799732** and provenance job **101566229176** verified the promoted milestone ref and exact candidate artifact lineage (provenance-copy artifact digest `sha256:115998f286d75e6792882c90f53a125ee75ffb28e75b40f932a20e87395aaf0d`). Share-to-ValuePilot now accepts a single image from either `EXTRA_STREAM` or single-item `ClipData`; multi-item shares fail closed rather than silently dropping pages. The URI remains bounded/content-only and is handed to the existing local Compare Here photo/OCR review path with temporary read permission, without bytes, parsing, evidence, persistence or ranking authority. Image OCR remains editable and unconfirmed, with exact package, currency, price, promotion, like-for-like, evidence, private-memory and planner gates unchanged. Text sharing and the prior transient OCR lifecycle protection remain supported. Local verification passed 405 shared-core tests, 1,606 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/debug+release assembly, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks passed. Physical-device share/OCR, barcode/camera, accessibility and latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The latest promoted Scan & Compare/Good Price consumer slice is `32184ca0782dc7f82ac7583364edee6660b79236` (`Streamline unambiguous barcode identity handoff`). Candidate workflow **34047828847** passed for the exact SHA: Android job **101525964170** (402 shared-core tests, 1,591 Android tests, lint/build, privacy and single-signer), browser job **101525964196** (30 tests and Firefox package checks), and release-bundle job **101526604524**. Milestone workflow **34048136063** and provenance job **101526778010** verified the promoted ref and exact candidate artifact lineage. With an empty Compare Here slot or blank Good Price field, one exact offline barcode identity now flows directly into the existing editable name field without a redundant choice dialog; quantity, currency, price, promotion and evidence remain manual and unconfirmed. Multiple matches and replacement/data-loss cases retain explicit confirmation. This presentation-only handoff preserves exact money, quantity, evidence, private-memory, evaluator, planner, ranking, persistence, networking, account, tracking, demo-data and provider-economics boundaries. Local verification also passed 80 Python/catalog tests and 30 browser tests; signed 30,000-record GTA/Metro Vancouver catalogs and the signed 6,093-location store directory verified. Physical-device barcode/camera, accessibility and latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The latest promoted Good Price trust slice is `7229879d9fe1df9bec6d4b2d5dd25e360c8242f2` (`Clarify Good Price history confidence`). Candidate workflow **34046055514** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101521994885**; milestone workflow **34046391345** and provenance job **101522085801** verified the promoted ref and exact candidate artifact lineage. When no exact personal history matches a checked package, Good Price now says that plainly and its disclosure says the answer uses this exact entered price only; it no longer implies that history exists or promises a save before storage success is known. Matching-history disclosures remain unchanged. This wording-only correction preserves exact money, quantity, promotion, private-memory, evaluator, planner, ranking, persistence, networking, account, tracking, demo-data and provider-economics boundaries. Local verification passed 402 shared-core tests, 1,589 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed offline catalogs/store directory and the candidate release bundle passed. Physical-device Good Price/Scan & Compare review, accessibility and latency behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The latest promoted Scan & Compare reliability slice is `a441190602a2968033c85c9d0d324294b2394499` (`Clarify OCR cancellation completion state`). Candidate workflow **34044501200** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101517762235**; milestone workflow **34044815415** and provenance job **101517879027** verified the promoted ref and exact candidate artifact lineage. When a shopper cancels or edits the comparison draft while ML Kit is still finishing, the photo status now says `Stopping photo reading… No suggestions will be added.` until the matching worker callback releases the lifecycle gate, then announces the final cancellation or draft-change result. Request-keyed stop state prevents stale callbacks from clearing a different stop reason. Pure stop-policy tests cover active-worker tracking, matching completion and stale completion; the change remains lifecycle/presentation-only and carries no OCR text, product, package, price, currency, evidence, comparison, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,586 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed offline catalogs/store directory and the candidate release bundle passed. Physical-device photo/OCR review, accessibility and latency behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The authoritative promoted code head is `57d753ea9c33041c5097e04d591e321fb09660a0` (`Bound concurrent Compare Here OCR reads`). Candidate workflow **34041531253** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101509888609**; milestone workflow **34041928579** and provenance job **101510143113** verified the promoted ref and exact candidate artifact lineage. Scan & Compare now keeps one asynchronous ML Kit OCR worker active at a time: cancellation invalidates the result immediately, while capture/retry controls remain closed until that worker's callback releases the slot, preventing a slow cancelled read from running beside a new read. Stale callbacks cannot release a newer worker, and teardown still rejects late results. This lifecycle-only gate carries no OCR text, product, package, price, currency, evidence or comparison authority; all existing editable/unconfirmed OCR and exact money/quantity, promotion, like-for-like and evidence gates remain authoritative. Home, Basket, plan-result/share and Watch store-status boundaries remain unchanged; no planner, projector, parser, product, offer, store, availability, private-memory, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority moved. Local verification passed 402 shared-core tests, 1,586 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed 30,000-record GTA/Metro Vancouver catalogs and signed 6,093-location store directory checks passed. Physical-device OCR review/accessibility behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior head

The authoritative promoted code head is `306a74a20f23c08b2bb74e64bf4e645cf9a45a68` (`Keep complete OCR review controls visible`). Candidate workflow **34040101037** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101505947713**; milestone workflow **34040422436** and provenance job **101506039802** verified the promoted ref and exact candidate artifact lineage. After a fully selected detected-details OCR commit, Scan & Compare still focuses the populated editable block but no longer forces the software keyboard open, keeping review and comparison controls visible; names-only, raw, or mixed/incomplete commits retain the immediate-editing keyboard affordance. All OCR values remain ordinary editable, unconfirmed text, and the existing exact package-quantity, currency, price, promotion, like-for-like and evidence gates remain authoritative. This bounded navigation correction leaves Home, Basket, plan-result/share and Watch store-status boundaries unchanged; no planner, projector, parser, money/quantity, product, offer, store, availability, private-memory, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority moved. Local verification passed 402 shared-core tests, 1,584 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed 30,000-record GTA/Metro Vancouver catalogs and signed 6,093-location store checks passed. Physical-device OCR review/accessibility behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

## Earlier promoted checkpoints

The entries below retain their original wording for historical audit; the authoritative current head is recorded above.

The latest promoted consumer-trust slice is `f4e3b2921741b37c4bceb877ac7508f6b7eba3ae` (`Streamline Compare Here keyboard flow`). Candidate workflow **34013976518** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101435006274**; milestone workflow **34014203936** and provenance job **101435073885** verified the promoted ref and exact candidate artifact lineage. Compare Here’s existing multiline product editors now accept the keyboard’s Done/Next action as a navigation shortcut: it focuses the next product box, and on the final box it invokes the same already-enabled Compare exact value action. The handoff never parses, confirms, ranks or creates evidence; it only uses the existing visible button gate, while all exact route and privacy boundaries remain unchanged. Home, Basket, plan-result and share surfaces retain their planned-stop disclosure, and Watch retains its candidate-store boundary. Local verification passed 402 shared-core tests, 1,578 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed 30,000-record GTA/Metro Vancouver catalogs and signed 6,093-location store checks passed. Physical-device keyboard behavior/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted Home consumer slice is `e00d0fc39b3814d3f7a1c14d3a8497cf8ad1926` (`Expose observed-price action for missing line prices`). Candidate workflow **34007700546** passed for the exact SHA, and milestone provenance workflow **34007925720** (provenance job **101418438043**) verified the promoted ref and exact release artifact lineage. A covered item whose projected basket total lacks an optional line-item price breakdown now exposes the same private `Add observed price` handoff already used for a missing store price. This is a presentation-only evidence-gap affordance: it opens the existing exact Good Price route, keeps the sample plan unchanged, and adds no live offer, availability, planner, product, store, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,576 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed offline catalog/store checks and candidate release-bundle job **101418380749** passed. Physical-device confirmation and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted Home consumer slice is `bd6eb3dc8dcb94ebf52e503c0bbd541f31964532` (`Surface Home first-value actions earlier`). Candidate workflow **34006273070** passed for the exact SHA, and milestone provenance workflow **34006546562** (provenance job **101414694656**) verified the promoted ref and exact release artifact lineage. Home now places a `Quick answers` heading and the existing `Scan & compare prices` / `Is this a good price?` owner-driven actions immediately below `Plan my shop`, before the longer sample/list/result content. This is navigation/presentation only: callbacks still fail closed when detached, and no planner, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,570 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed offline catalog/store checks and candidate release-bundle job **101414624536** passed. Physical-device first-launch action discovery, scroll/keyboard/accessibility and camera/OCR ergonomics plus lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted Home consumer slice is `8acec4041223d3fa82ff79e17dc934aee36cd77b` (`Add Home quick-add sample shortcuts`). Candidate workflow **34004675643** passed for the exact SHA, and milestone provenance workflow **34004938861** verified the promoted ref and exact release artifact lineage. The clearly labelled fictional offline Home demo now shows bounded `Try a sample item` chips—Eggs, Milk, Bananas, Bread and Chicken—only while idle with a blank list. Each tap appends a bounded text token through the existing Home query reducer, repeated tokens are idempotent, over-limit additions fail closed and the row hides once a query exists. This is a text-entry convenience only: it adds no identity, price, store, stock, availability, evidence, planner, ranking, persistence or Android networking authority. Local verification passed 402 shared-core tests, 1,569 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release assembly, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed offline catalog/store checks and the candidate release bundle passed. Physical-device quick-add/scroll/keyboard/accessibility and camera/OCR ergonomics plus lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `67e9a82709e60b1b48a05b7963f728ed51230714` (`Recognize bare decimal shelf prices in OCR review`). Candidate workflow **34003231756** passed for the exact SHA, and milestone provenance workflow **34003504444** verified the promoted ref and exact release artifact lineage. Scan & Compare's user-triggered photo OCR now retains conservative bare two-decimal shelf-price text such as `4.99` when no currency symbol/code is visible, while excluding common package-unit decimals such as `1.75 L`. The value is shown only as an unconfirmed `currency needs review` signal; it is not an exact price, currency, evidence or automatic prefill, and the existing editable review plus exact package/price/currency route remains authoritative. This bounded OCR-recall/presentation slice adds no planner/projector, money/quantity, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,569 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build, APK privacy, single-signer, signed offline asset checks and the candidate release bundle passed. Physical-device keyboard/camera/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted tooling slice is `083f93ad7ded1769679f1a215e2fbd5232d86124` (`Allow offline verifier shared source roots`). Candidate workflow **34001773717** passed for the exact SHA, and milestone provenance workflow **34002077934** verified the promoted ref and exact release artifact lineage. The offline-catalog verifier CLI now accepts `--source-root` for the APK's intentionally shared source payload, while standalone snapshots keep their colocated-source default. This tooling/test-only correction adds no Android/runtime, product, offer, store, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,563 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build, APK privacy, single-signer, signed 30,000-record identity snapshots and signed 6,093-record store-directory checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `f755d6db916ecab5e9bf920a5ba41007b0a23bcc` (`Reuse offline catalog for barcode identity lookups`). Candidate workflow **34000381235** passed for the exact SHA, and milestone provenance workflow **34000674093** verified the promoted ref and exact release artifact lineage. Comparison and Good Price barcode identity lookups now reuse one Activity-scoped bounded merged index after the first signed GTA/Metro Vancouver catalog load. Reuse rechecks freshness and rollback limits, and teardown closes the session; existing request-id/lifecycle guards reject late callbacks. The cache stores only the bounded identity index and generation metadata. Barcode output remains an untrusted identity suggestion and cannot establish package quantity, currency, price, store, stock, availability, evidence or ranking. No planner/projector, product, offer, store, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,563 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory; candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `9f142bc2c2c692a71605182eb2efe2f8ad495187` (`Cover thirty-thousand catalog cache bound`). Candidate workflow **33999251567** passed for the exact SHA, and milestone provenance workflow **33999546339** verified the promoted ref and exact release artifact lineage. Home’s unresolved-item identity lookup and Search’s explicit identity lookup now share one Activity-scoped, bounded merged index after the first signed two-metro load. Reuse rechecks the caller-supplied freshness and rollback boundaries; expiry, future time, rollback or Activity teardown clears/rejects the cache. No raw catalog text, query results, price, stock, availability or ranking facts are cached, and the existing identity-only admission/region fail-closed loader remains the authority. Local verification passed 402 shared-core tests, 1,563 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory; candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `1961a15f2a635a9705754158340482c6245d1c64` (`Clarify insufficient Good Price evidence`). Candidate workflow **33997214915** passed for the exact SHA, and milestone provenance workflow **33997549479** verified the promoted ref and exact release artifact lineage. Good Price’s insufficient-history answer now uses the plain-language sentence `Not enough evidence yet to know whether this is unusually good.` and the shorter device-only guidance `ValuePilot will remember this exact package on this device.` The existing exact evaluator, private-memory capture/replay and neutral tone remain the authorities; no planner/projector, product, offer, store, availability, ranking, persistence, networking, account, tracking, demo-data or provider-economics boundary changed. Local verification passed 402 shared-core tests, 1,558 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory; candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `16a14798ed2ffd2b4471aa13381267b1cab76d77` (`Associate Compare Here editors with labels`). Candidate workflow **33995987426** passed for the exact SHA, and milestone provenance workflow **33996295982** verified the promoted ref and exact release artifact lineage. Each runtime-created Compare Here editor now has a generated view ID, and its visible `Product N` label points to that ID with `labelFor`, giving TalkBack a deterministic field context without persisting or exposing product identity. This remains presentation/accessibility-only: exact parsing, money/quantity, promotion, evidence and private-memory authorities remain unchanged, as do planner/projector, product, offer, store, availability, ranking, persistence, networking, account, tracking, demo-data and provider-economics boundaries. Local verification passed 402 shared-core tests, 1,558 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory; candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `571a53ac8bbb4ac1f99fa57bd77a5c6e3cd18568` (`Focus newly added Compare Here product input`). Candidate workflow **33994946204** passed for the exact SHA, and milestone provenance workflow **33995260282** verified the promoted ref and exact release artifact lineage. An explicit `Add another product` action now focuses the newly-created bounded Compare Here editor block, moves its cursor to the end and requests the keyboard through the existing helper. The next product can therefore be entered immediately without another tap. This is navigation-only: existing exact parsing, money/quantity, promotion, evidence and private-memory authorities remain unchanged, as do planner/projector, product, offer, store, availability, ranking, persistence, networking, account, tracking, demo-data and provider-economics boundaries. Local verification passed 402 shared-core tests, 1,557 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline catalog verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory. Candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `e73b09454773e3b9b98a0f12a6277db33ca7ddea` (`Reveal Home plan after explicit actions`). Candidate workflow **33993225356** passed for the exact SHA, and milestone provenance workflow **33993536868** verified the promoted ref and exact release artifact lineage. Home now asks its existing parent scroll container to reveal the already-projected result after an explicit submit, chicken refinement choice or Shop Again action, using a bounded rectangle after layout. Passive query edits, state restore and private-memory refresh continue to render without a scroll jump. This is a presentation/navigation-only correction: the renderer does not infer or recalculate any plan, and no product, offer, store, availability, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,555 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate Android lint/build, APK privacy, one-signer and checksummed release-bundle checks passed. Physical-device scroll/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `f4d8392fc3eb8da445d71dd4181cd3d5ac666539` (`Reduce product handoff friction safely`). Candidate workflow **33991590581** passed for the exact SHA, and milestone provenance workflow **33991889058** verified the promoted ref and exact release artifact lineage. Shared text handoff now focuses and reopens the populated editable Compare Here block; Home and Saved product-name handoffs focus and reopen Good Price after idle projection, while restored drafts remain untouched. If a Good Price draft is nonempty, barcode identity choices explicitly say that accepting one will replace the draft and remove its package/price text; replacement happens only on the explicit action, and the barcode remains an identity suggestion. Exact package quantity, currency, observed price, promotion, like-for-like and private-memory gates remain authoritative. No parser, product, offer, store, availability, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,553 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate Android lint/build, APK privacy, one-signer and checksummed release-bundle checks passed. Physical-device keyboard/camera ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `7cdd9d72c9aceb7daca366b7958664d52d3309b3` (`Reopen keyboard after identity handoff`). Candidate workflow **33990220399** passed for the exact SHA, and milestone provenance workflow **33990587690** verified the promoted ref and exact release artifact lineage. After a reviewed OCR suggestion or barcode identity fills an editable Compare Here entry, the navigation handoff now explicitly reopens the on-device keyboard; Good Price does the same after barcode identity selection. These values remain ordinary editable text, so exact package quantity, currency, observed price, promotion and like-for-like confirmation remain required before comparison or private memory. No parser, product, offer, store, availability, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,550 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks passed. Physical-device keyboard/camera ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `74e593fc4985a293501f8cd02e5dc4de8c6587e3` (`Share projected Home plans safely`). Candidate workflow **33989029339** passed for the exact SHA, and milestone provenance workflow **33989343043** verified the promoted ref and exact release artifact lineage. Home now exposes an explicit `Share this plan` action only when an existing projected store plan has a primary result. The bounded preview/share card formats existing total, store, coverage, travel, evidence and optional second-stop facts, omits item names and private price history, and repeats the fictional/offline disclosure. No-coverage, malformed/unsafe, stale-dialog and missing-owner paths fail closed; no planner, projector, ranking, money, evidence, persistence, networking, account, tracking, demo-data or provider-economics authority moved into the View or Activity. Local verification passed 402 shared-core tests, 1,548 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate verification passed Android lint/build, APK privacy, one-signer, and release-bundle checks. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `22e7989e0146a9acfdb7dacf3480630103f0c4c9` (`Allow Good Price keyboard submission`). Candidate workflow **33987076763** passed for the exact SHA, and milestone provenance workflow **33987368167** verified the promoted ref and exact release artifact lineage. Good Price's multiline entry now accepts the keyboard `Done` action only while the existing visible check button is enabled, then uses the same exact route; result projection still dismisses the keyboard and clears editor focus first. This is a bounded interaction correction only: existing coordinators remain the parsing, exact money/quantity, evidence, ranking and private-memory authorities, with no planner, product, offer, store, availability, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,541 Android tests, and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `591ba936ee55373a3867084a4a930a69e40bb5ed` (`Keep exact price results visible after entry`). Candidate workflow **33986142487** passed for the exact SHA, and milestone provenance workflow **33986488417** verified the promoted ref and exact release artifact lineage. Compare Here and Good Price now dismiss the on-screen keyboard and clear editor focus before projecting their exact result, keeping the unit-value answer and any device-only memory outcome visible immediately after an aisle-side action. This is a bounded Android interaction correction only: existing coordinators still own parsing, exact money/quantity, evidence, ranking and private-memory authority, and no planner, product, offer, store, availability, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,540 Android tests, and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `3864a339ded3c975f909789add947397e27dbc3b` (`Clarify fictional Shop Again disclosure`). Candidate workflow **33984116186** passed for the exact SHA, and milestone provenance workflow **33984866308** verified the promoted ref and exact release artifact lineage. Home's repeat-list action now says it reuses offline fictional demo data and is not a live retailer plan, so its standalone accessibility description cannot imply current retailer evidence. This presentation-only correction adds no planner, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,538 Android tests, and 79 Python/catalog tests with zero failures or skips. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `445af5f80d5624e35af2c4c27cbd2b04516bcb18` (`Clarify projected savings as potential`). Candidate workflow **33981634428** passed for the exact SHA, and milestone provenance workflow **33982413282** verified the promoted ref at docs head `2a11bdde5863f1e565726b954f68b7ca34be0cb1` and release artifact lineage. The optional second-stop alternative now says `Could save ...` so a projected, evidence-backed scenario is clearly distinguished from money already saved; exact savings math and eligibility remain owned by the existing planner/projector. This presentation-only correction adds no product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,537 Android tests, and 79 Python/catalog tests with zero failures or skips. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code milestone is `195aa654b05bea040a245ce454a06fa367deee76` (`Allow removing one private history observation`). Candidate workflow **33980306927** passed for the exact SHA, and milestone provenance workflow **33980621340** verified the promoted ref and release artifact lineage. Home's `Forget one observation` action is available only for readable, nonempty device-only history. Its bounded newest-first picker exposes at most 32 exact rows and carries the immutable SHA-256 observation fingerprint through an explicit confirmation; the local store is re-read immediately before deletion, so stale, erased, corrupt, oversized, missing or failed mutations fail closed without touching another entry. Individual deletion now complements existing review, clear-all and export ownership controls. This remains presentation/storage convenience only: private history never becomes a live offer, store fact, availability claim, planner input or ranking authority, and no networking, account, tracking, demo-data or provider-economics authority moved. Candidate Linux verification passed 402 shared-core tests and 1,533 Android tests with zero failures or skips, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device removal/share and camera/OCR ergonomics plus lawful production Home activation remain open; provider feeds remain blocked pending rights.

The latest promoted code milestone is `b94508453b4d51fbf192a9216d919b3b5818de9e` (`Add explicit private history export from Home`). Candidate workflow **33978834368** passed for the exact SHA, and milestone provenance workflow **33979149725** verified the promoted ref and release artifact lineage. Home's `Export private history` route is available only for readable, nonempty device-only comparison memory. A bounded pure exporter produces deterministic newest-first copy containing each exact price, package quantity, unit rate, price basis, promotion terms, observation time and source label, with a clear disclosure that it is historical personal context rather than live retailer pricing, inventory, availability or a guarantee; no store is named because the memory model does not establish one. Home presents only a five-row preview plus a privacy warning, then reloads the atomic local store immediately before the explicit Android text-share chooser. Empty, corrupt, changed, erased or oversized histories fail closed and no data is truncated or turned into planner input. This focused ownership/convenience slice adds no planner, projector, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority; existing review and clear-all actions remain intact. Candidate Linux verification passed 402 shared-core tests and 1,533 Android tests with zero failures or skips, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device export/share and camera/OCR ergonomics plus lawful production Home activation remain open, and provider feeds remain blocked pending rights. The manual device protocol is in `DEVICE_VALIDATION.md` and has not been run here.

The latest promoted code milestone is `2bdf246c5a5843ff9f7384f4743c472e2b71dfda` (`Focus first OCR addition in Compare Here`). Candidate workflow **33976684845** passed for the exact SHA, and milestone provenance workflow **33977035497** verified the promoted ref and release artifact lineage. After an explicit OCR review action, the existing bounded photo-draft helper now reports `firstAddedIndex`; Compare Here focuses that newly populated block only after the dialog is dismissed. The previously promoted barcode handoff focuses the selected identity's populated block, and Good Price focuses its manual product field after barcode identity selection. These are navigation-only improvements: OCR/barcode values remain untrusted, exact quantity/currency/price and like-for-like review remain authoritative, and no product, offer, store, availability, evidence, planner, ranking, persistence, provider or Android networking authority changed. Candidate Linux verification passed 402 shared-core tests and 1,529 Android tests with zero failures or skips, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics, accessibility and lawful production Home activation remain open. The manual device protocol is in `DEVICE_VALIDATION.md` and has not been run here.

The latest promoted code milestone is `06c37ae4e3c2834bb01dc73d7b85f7e6815c9cd0` (`Preserve Home refinements when shopping again`). Candidate workflow **33974689463** passed for the exact SHA, and milestone provenance workflow **33975020123** verified the promoted ref and release artifact lineage. `Shop again` now starts from the completed Home session model and invokes the existing reducer directly, so explicit refinements such as a selected chicken cut survive one-tap replay instead of being discarded by a new-draft transition. Stable typed request details are reconciled as before. This is a bounded session/consumer-flow correction with no planner, projector, price, evidence, offer, store, availability, ranking, persistence, provider or Android networking authority change. Candidate Linux verification passed 402 shared-core tests and 1,526 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `cfd4ba6663978dcc2f73447f218b0b4df643c568` (`Prefill complete OCR comparison drafts`). Candidate workflow **33973764156** passed for the exact SHA, and milestone provenance workflow **33974081700** verified the promoted ref and release artifact lineage. Scan & compare photo review now has an explicit optional `Add with detected details` action. It is offered only when the bounded parser finds one concrete ISO-currency price, an exact non-derived package quantity, a usable name and a promotion shape the existing exact route can replay; otherwise the selected raw OCR remains the draft. The generated value is ordinary editable text, the dialog/status still call it an unconfirmed suggestion, and the shopper must review it before exact comparison or private-memory capture. Multiple/ambiguous prices, estimated or derived quantities and unsupported promotions never get silently rewritten. The slice also fixes the post-add skipped-snippet count so the status does not double-count skips. No parser, product, offer, store, availability, planner, ranking, persistence, provider or Android networking authority changed. Candidate Linux verification passed 402 shared-core tests and 1,525 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `55b1ac9c411444b5285fdcf6dc9740f3f8848cce` (`Explain OCR signals during photo review`). Candidate workflow **33972120095** passed for the exact SHA, and milestone provenance workflow **33972485006** verified the promoted ref and release artifact lineage. The Scan & compare photo-review dialog now projects bounded, deterministic parser signals for possible name, price text and package size next to the original OCR suggestion, with an explicit `Review only` notice. Multiple prices, ambiguous currency and estimated or missing quantities remain visible review requirements. Only the shopper-selected raw OCR block is appended, and it still goes through the existing editable-entry, exact quantity/currency/price and like-for-like route; no parser signal is admitted as evidence automatically. This presentation-only friction slice adds no OCR, product, offer, store, availability, planner, ranking, persistence, provider or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,522 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `8499e8ad2b566947d6bd6af70d8ab24b47cbf5c2` (`Open Home catalog identities in Compare Here`). Candidate workflow **33970594096** passed for the exact SHA, and milestone provenance workflow **33970918134** verified the promoted ref and release artifact lineage. Home's signed offline identity result now offers `Use in Scan & compare` as a second, explicit next step for an unknown list word. It carries only a bounded display label through the existing untrusted text-share handoff; the shopper must still enter and review exact quantity, currency, price and like-for-like basis. Invalid or stale dialog selections fail closed, and exact product confirmation, the planner/projector, private memory, demo disclosure and no-network boundary are unchanged. Candidate Linux verification passed 402 shared-core tests and 1,515 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `a231f0ecc962737f815dc2bfa9fe894037dd2bf9` (`Cancel stale Compare Here photo work`). Candidate workflow **33969229838** passed for the exact SHA, and milestone provenance workflow **33969555817** verified the promoted ref and release artifact lineage. Scan & Compare now offers an explicit cancel action while its bounded on-device photo read is active. Editing or clearing the comparison draft, or tearing down the Activity, invalidates the old generation, rejects any late OCR callback, cleans temporary camera files and preserves the current entries; no suggestions are added from stale work. This is a reversible lifecycle/ergonomics correction with no parser, money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,515 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `9318a70d5b6a9a31ba0d4377efdfebd676ccf749` (`Restore Good Price results after recreation`). Candidate workflow **33967685757** passed for the exact SHA, and milestone provenance workflow **33967990673** verified the promoted ref and release artifact lineage. Good Price replays a previously evaluated exact check after activity recreation without writing a duplicate private-memory entry; the deterministic observation fingerprint excludes the replay from its own history. Draft edits, clear actions and private-memory changes clear the replay marker, while unreadable memory stays on the explicit idle/recovery path. This renderer/lifecycle slice adds no parser, money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider, or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,510 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `8c0f4e843b0c1cce266f17fcceda19f278b5711a` (`Preserve Good Price capture draft safely`). Candidate workflow **33966599555** passed for the exact SHA, and milestone provenance workflow **33966897180** verified the promoted ref and release artifact lineage. Good Price restores only its bounded typed product draft during activity recreation, preserving a fresh intent prefill when no saved instance value exists. The barcode capture boundary now gates duplicate launches, keeps capture/lookup disabled while in flight, and makes user cancellation explicit without changing the typed price-check draft. This is a local renderer/orchestration correction with no parser, money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider, or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,508 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

- The latest promoted code milestone is `9432604ca89572233e64b8268a0673ed35c6c0ce` (`Announce scanner availability accessibly`). Candidate workflow **33964877138** passed for the exact SHA, and milestone provenance workflow **33965190171** verified the promoted ref and release artifact lineage. Compare Here’s existing optional scanner status is now a polite accessibility live region, so scanner availability changes on resume are announced without moving authority into the legacy adapter or changing any shopping result. This renderer-only accessibility correction adds no parser, money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,505 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

- The latest promoted code milestone is `60bd0358bc03a50c5180fea1e29a1ee3e65597cd` (`Invalidate stale private memory on resume`). Candidate workflow **33964012615** passed for the exact SHA, and milestone provenance workflow **33964271194** verified the promoted ref and release artifact lineage. Good Price reloads its device-only memory on resume and clears the projected answer/share card when the memory state or load issue changes. Compare Here tracks the state behind its visible memory message and hides stale personal context when a paused screen detects changed or unreadable storage. This is a fail-closed lifecycle correction with no parser, money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority change. Candidate Linux verification passed 402 shared-core tests and 1,505 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

- The latest promoted code milestone is `d981fdcd81aa8101c3006f82b8b92744f3fd12ee` (`Announce private memory outcomes accessibly`). Candidate workflow **33963348099** passed for the exact SHA, and milestone provenance workflow **33963622041** verified the promoted ref and release artifact lineage. Good Price and Compare Here private-memory status views now use polite accessibility live regions, announcing saved, unavailable and clear-failure outcomes without changing the existing authorities or evidence semantics. Candidate Linux verification passed 402 shared-core tests and 1,504 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

- The latest promoted code milestone is `d6a0b25ef3e20a7c533cb98fb083c910e734b641` (`Improve Good Price result accessibility`). Candidate workflow **33962568132** passed for the exact SHA, and milestone provenance workflow **33962826152** verified the promoted ref and release artifact lineage. Good Price now exposes the projected answer and optional private-history card as coherent accessibility nodes, with decorative child labels hidden to prevent repeated or ambiguous announcements. The summary is renderer-only and carries no new parser, exact money/quantity, evaluator, memory, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,503 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

- The latest promoted code milestone is `44767d3a89c5fe79e3916c09ef91a589e42230dc` (`Invalidate Good Price result after memory clear`). Candidate workflow **33961384703** passed for the exact SHA, and milestone provenance workflow **33961699432** verified the promoted ref and release artifact lineage. Good Price now returns to its explicit idle presentation after private-history deletion succeeds, clearing any result card and share action that could otherwise retain erased personal context. Failed deletion still remains an error, and no exact evaluator, memory-store, planner, ranking, evidence, offer, store/availability, Android networking or demo authority changed. Candidate Linux verification passed 402 shared-core tests and 1,502 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

- The latest promoted code milestone is `9c164ecd2566f26e9805cbcfce1356090cdf9ad6` (`Make photo retry outcomes explicit`). Candidate workflow **33960585727** passed for the exact SHA, and milestone provenance workflow **33960929862** verified the promoted ref and release artifact lineage. Scan & Compare now classifies photo terminal outcomes explicitly: OCR/no-match and recoverable picker/camera creation failures expose `Try another photo`, while permission denial, cancellation and unavailable camera hardware remain explicit no-retry states. This closes a misleading same-action loop and adds import recovery without changing the existing review-first OCR, exact parser, package/price/currency, like-for-like, comparison or private-memory authorities. Candidate Linux verification passed 402 shared-core tests and 1,501 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `58c8732f1fe5dcb1a450fb9c0d28aa743f5dfb56` (`Add retry action for failed comparison photos`). Candidate workflow **33959540807** passed for the exact SHA, and milestone provenance workflow **33959812269** verified the promoted ref and release artifact lineage. After a user-triggered camera/import path fails or returns no usable OCR price text, Scan & Compare now exposes `Try another photo` for the same method. The retry is bounded by the existing in-flight/lifecycle gate, disappears when a new request starts or a review succeeds, and never mutates existing entries; exact parsing, package/price validation, like-for-like confirmation, comparison and private memory remain downstream authorities. This reversible consumer ergonomics slice adds no planner/projector, money, evidence, offer, store/availability, ranking, persistence, Android networking, demo-data or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,500 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `1db1b0d9edaa18e4b78ef11d08d317175d4e2902` (`Add repeat-list action to Practical Shopping Home`). Candidate workflow **33958293679** passed for the exact SHA, and milestone provenance workflow **33958570310** verified the promoted ref and release artifact lineage. A completed nonempty Home result now has an explicit owner-driven `Shop again` control that replays the same typed list through the existing deterministic Home session. It is hidden for idle, draft, refinement and error states, disabled until the surface has rendered, and cleared on Activity teardown; a detached or incomplete callback cannot mutate state. This reversible consumer handoff adds no planner/projector, price, evidence, offer, store, availability, persistence, Android networking, demo-data or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,497 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `42f86e7dbd6a1e03a694c85032f30f21b1ab752e` (`Let saved products reopen Good Price`). Candidate workflow **33957008185** passed for the exact SHA, and milestone provenance workflow **33957294707** verified the promoted ref and release artifact lineage. Saved product rows now provide a typed `Check price` action that opens the existing Good Price screen with a bounded, display-only name prefill. The shopper still supplies exact package quantity, currency and observed price, and the existing exact evaluator remains the only answer/private-memory authority; Saved identity never implies a current offer, stock, availability or store price. Busy Saved lifecycle states suppress the action, while the route coordinator treats it as navigation-only. No planner/projector, money/quantity, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority moved into the Saved View or shell. Candidate Linux verification passed 402 shared-core tests and 1,494 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `37cc19cb72cb231831dfecef8fd5eb0c0e5bca2b` (`Add review gate for OCR comparison suggestions`). Candidate workflow **33955298400** passed for the exact SHA, and milestone provenance workflow **33955607275** verified the promoted ref and release bundle lineage. Scan & Compare’s photo/camera OCR handoff now presents bounded raw suggestions in an explicit untrusted multi-choice review before any draft mutation. The shopper selects snippets, then the existing editable exact route remains responsible for parsing, package/price validation, like-for-like confirmation, comparison and private memory. Cancellation, dismissal, stale lifecycle callbacks, unsafe text, duplicates and capacity overflow preserve existing entries. No planner/projector, exact money/quantity, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority moved into capture or Views. Candidate Linux verification passed 402 shared-core tests and 1,490 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox, Python/catalog and release-bundle checks. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `0e1ffaaf24352ad9652631d84d081cd67db4699d` (`Clarify missing Home item price breakdowns`). Candidate workflow **33952630205** passed for that exact code SHA, and the resulting promoted tree was provenance-verified by milestone workflow **33953296196**. Home, Basket and the inactive production Home surface now explicitly say when a covered subtotal includes an item whose optional line-item breakdown was not supplied: exact breakdowns remain exact, while the new `Included in the basket total — exact item price not shown.` notice never fabricates a line price. The planner and shared projector remain unchanged authorities; missing, stale, conflicting and unknown states stay visibly distinct, and Basket accessibility carries the same explanation. A test-only source reader normalizes CRLF/CR before source-boundary comparisons, preserving whole-file assertions and removing the known Windows newline noise. Candidate Linux verification passed 402 shared-core tests and 1,485 Android tests with zero failures, lint/build, APK privacy and single-signer checks, browser/Firefox (30 tests) and release-bundle; physical-device ergonomics and lawful production Home activation remain open.

The latest candidate code milestone is `8025eb6e753d505dc75c19630683fd8f3afc2c29` (`Harden production Home UI handoff`), verified by candidate workflow **33933221103** for that exact SHA. The production Home host keeps its exact projection only for internal generation identity and now exposes a separate, demo-free `PracticalShoppingProductionHomeUiState` to renderers. That state reuses the existing `PracticalShoppingUiState`, adds bounded per-item store/price rows, keeps partial and no-coverage outcomes explicitly unknown, and fails closed for missing/unsafe display metadata or malformed assignments. The new production Home surface is inactive by default and reuses the existing result-card renderer; visible Home remains the fictional/sample controller until lawful current-offer/private-evidence inputs and a separate activation coordinator are available. Candidate Linux verification passed 402 shared-core tests and 1,482 Android tests with zero failures, browser/Firefox, lint/build, release-bundle, APK privacy and single-signer checks; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home activation remain open.

The latest promoted code milestone is `730794ed111658383ede5350fededfd57c98ece9` (`Add generation-safe production Home handoff`). Candidate workflow **33930515512** passed for that exact SHA, including the full shared-core/Android verification, browser/Firefox checks, release bundle, APK privacy and single-signer checks. Android now has a narrow production Home adapter that delegates valid decisions to the existing `PracticalShoppingUiProjector`, plus a bounded refresh host that re-evaluates raw orchestration inputs against current lifecycle/disposition registries and rejects stale, duplicate or same-generation-conflicting refreshes. Structural production failures become an explicit unavailable state; valid no-coverage remains a normal truthful result. The renderer receives no raw production result, provider identifiers, blocker diagnostics or demo data. This prepares the production Home seam but deliberately does not activate live retailer networking or replace the clearly labelled fictional Home controller. Candidate Linux verification covered 402 shared-core tests and 1,473 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and full production Home activation remain open.

The latest promoted code milestone is `3efdb688d5ff981b4f421bf349d49ef1432679e1` (`Add privacy-safe Good Price result sharing`). Candidate workflow **33927693684** and milestone provenance workflow **33928182141** completed successfully for that exact SHA. Good Price now offers `Share this price result` only after the existing route has produced one exact evaluated result. A review precedes Android’s `text/plain` Sharesheet; the bounded generic card shares only the exact entered price, package quantity, selected basis and unit-rate math, explicitly says it is not live store pricing, and contains no product name, private history, receipt, location, account or source identifiers. Blocked, incomplete, ambiguous or unsafe states remain unshareable. This is presentation and normal user-triggered navigation only: no shared planner/projector, money/quantity, evidence, offer, store/availability, persistence, networking, demo-data or provider-economics authority changed. Candidate Linux verification passed 402 shared-core tests and 1,465 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The latest promoted code milestone is `d53b4fca70c3e84b32fab49c7b8aad789e4e92e3` (`Let Home save confirmed offline product identities`). Candidate workflow **33923409718** and milestone provenance workflow **33923881317** completed successfully for that exact code SHA. Home’s resolved rows now offer an explicit `Choose exact product` action using the existing signed offline identity catalog. The shopper must select and confirm a candidate; only then does the existing source-revalidated exact-choice projector and asynchronous Saved transaction persist a private product identity preference. No package quantity, price, currency, store, stock, availability or freshness is supplied, unknown rows remain query-only, and the fictional sample planner is unchanged and clearly disclosed. No shared planner/projector duplication, Android networking, new persistence authority, ranking influence or provider economics were added. Candidate Linux verification passed 402 shared-core tests, 1,457 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `80b9efd5e33839e3f0dfd1b10e273c1a2547cfaf` (`Show private Home price observation date`). Candidate workflow **33919462842** and milestone provenance workflow **33920091991** completed successfully for the exact same SHA. Home’s exact name/package private-history context now includes the latest observation date in stable UTC form, so a remembered price cannot look like a current retailer offer; a missing/zero timestamp remains explicitly `date not recorded`, and mixed package, currency, basis or promotion histories keep the conservative name-only notice. The formatter is shared with the private-history review. This is read-only device context only: fictional planner result, exact money/quantity, evidence, offers, store/availability, ranking, persistence, networking and provider-economics boundaries remain unchanged. Candidate Linux verification passed 402 shared-core tests, 1,452 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `8fc5629e99cca13ff2aca2b462ddaf5ee1bc1dbf` (`Show comparable personal price context on Home`). Candidate workflow **33917203335** and milestone provenance workflow **33917694467** completed successfully for the exact same SHA. Home now passes each sample row’s normalized package quantity to the existing private-history presentation. For a normalized display-label/package match whose observations share currency, unit basis, price-selection and promotion terms, Home displays the deterministic last recorded personal price and remembered unit-rate range. Any mixed package, basis, currency or promotion shape stays on the conservative name-only notice; the copy says identity, brand and store may differ and is not live store pricing. This is read-only consumer context: the fictional planner result and exact money/quantity, evidence, offer, store/availability, ranking, persistence, networking and provider-economics boundaries are unchanged. Candidate Linux verification passed 402 shared-core tests, 1,451 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates. The local Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `f3afb2b274ad16dbc84454cc615277c2084ff124` (`Show exact item prices in Home plan`). Candidate workflow **33914080876** and milestone provenance workflow **33914604138** completed successfully for the exact SHA. The existing practical-shopping candidates now optionally carry exact per-covered-item prices. Candidate construction validates that a nonempty breakdown has exactly the covered keys, uses the candidate’s currency/precision and sums to its authoritative subtotal; the planner never reads this descriptive map. The existing Home renderer/projector passes those values through in request order and displays `Included in plan: X CAD`; incomplete rows remain unknown and legacy candidates without a map remain safe. Basket includes the projected price in its local check-off scope so changed explanations cannot reuse stale marks. This is consumer explanation only: no identity, offer, availability, evidence, ranking, planner, network, persistence or provider-economics authority changed. Candidate Linux Android/browser/privacy/signing/release and Python/catalog gates passed; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `d206331e2d3927e3b084b0fed1d92b40f16cdc68` (`Show Home personal history coverage`). Candidate workflow **33900346836** and milestone provenance workflow **33900879487** completed successfully for the exact same SHA. Home’s immutable projection now shows `Name-matched personal history: X of N list items.` for nonempty requested names when readable device-only comparison memory exists. This is normalized display-label context only and is explicitly **not current-price coverage**; it cannot establish exact identity, package, quantity, price, freshness, stock, availability, live offers or ranking. Empty/blank requested names keep the generic history summary, unreadable memory remains hidden, and the fictional planner remains separate. No shared planner/projector, money/evidence, persistence, Android networking or provider-economics authority changed. Candidate Linux Android/browser/privacy/signing/release and Python/catalog gates passed; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `41a0fc28466cd0ac5b794bcedfaf49ff7d15173a` (`Expose offline product identity search`). Candidate workflow **33898457412** and milestone provenance workflow **33898919053** completed successfully for the exact same SHA. Search now has a user-triggered, off-main-thread lookup against the separately signed 30,000-record offline Canada identity catalog. It presents only deterministic identity names, brands and match labels; it does not claim package quantity, price, stock, store availability, freshness or ranking. Choosing a name uses the existing untrusted `text/plain` handoff into Scan & compare, where exact quantity, currency and an observed price are still required. Query edits, quick/sample actions, route changes, cancellation and teardown dismiss the result and invalidate stale callbacks. The fictional sample value search remains explicitly separate. No Android networking, new persistence authority or shared planner/projector duplication was added. Candidate Linux Android/browser/privacy/signing/release and Python/catalog gates passed; the Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `713e4396c6643f3b6aa4b06af3a90cea0921416a` (`Add text share handoff to Compare Here`). Candidate workflow **33895387933** and milestone provenance workflow **33895985864** completed successfully for the exact same SHA. An intentional `text/plain` share now opens a bounded, clearly labelled untrusted preview before the shopper chooses `Open in Scan & compare`. The existing Compare Here draft receives the trimmed text in its earliest empty slot, preserves every existing entry, and rejects blank, oversized or full drafts without replacement. This is an input convenience boundary only: it does not parse or authorize product, GTIN, package, price, store, availability, evidence, planner or ranking facts, and it adds no Android networking or new persistence authority. Candidate Linux verification passed the full 1,432-test Android job, lint/build/privacy/single-signer/release checks, the 30-test browser/Firefox suite and the 79-test Python/catalog suite. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `45f52cdaa5d3933a30b14f0386c9d51310d9a0f3` (`Make Home private history deletion explicit`). Candidate workflow **33892635254** and milestone provenance workflow **33893217763** completed successfully for the exact same SHA. Home’s bounded private-history review now includes an explicit `Clear private history` action. It requires confirmation, calls only the existing atomic device-local memory store, resets the in-memory Home state only after an accepted deletion, and exposes an error dialog without falsely reporting success. Copy states that the Home list, identity catalog, store locations, live offers and recommendations are unchanged. Empty or unavailable memory retains the existing recovery behavior; no store or current-offer authority is introduced. Candidate Linux verification passed the full 1,419-test Android job, lint/build/privacy/single-signer/release checks, the 30-test browser/Firefox suite and the 79-test Python/catalog suite. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `7c7be8d5215d2f6261049398700486252dda407c` (`Carry no-coverage summary into Basket`). Candidate workflow **33887664746** and milestone provenance workflow **33888215450** completed successfully for the exact same SHA. Basket now carries Home’s existing renderer-owned `0 of N items priced yet.` aggregate into the Basket tab when the projected result has no primary plan, preserving the quickest coverage explanation across destinations. The summary is hidden for empty/refinement states and for any projected primary plan that already supplies exact coverage text. This is a presentation/accessibility-only change; planner/projector, exact money, price/offer/store/availability, network, persistence and provider-economics authority are unchanged. Local Python/browser/Android focused and build/privacy/signing checks passed; the Windows run retains the known 18 line-ending-sensitive boundary failures and physical-device ergonomics remain open.

The promoted milestone is now `1be6628b3fb48b67fa7ee272a8ed3a3a35984529` (`Clarify Home no-coverage summary`). Candidate workflow **33885576989** and milestone provenance workflow **33886216516** completed successfully for the exact same SHA. When Home’s existing projected result has no primary plan, the renderer adds a compact `0 of N items priced yet.` aggregate alongside the per-item unknown-price notices. The summary is hidden for empty/refinement states and for results whose projected primary plan already supplies coverage. This is a presentation-only improvement; planner/projector, exact money, price/offer/store/availability, network, persistence and provider-economics authority are unchanged. Focused tests, candidate Android/browser/privacy/signing/release checks and milestone provenance are green; the Windows run retains the known 18 line-ending-sensitive boundary failures and physical-device ergonomics remain open.

The promoted milestone is now `ec7ed377c27e7e6b4ece9030bedc5e7cbf997188` (`Add signed GTA GVA store directory snapshot`). Candidate workflow **33883155939** and milestone provenance workflow **33883690098** completed successfully for the exact same SHA. The shell’s `Data status` dialog now reads the signed, ODbL-attributed OpenStreetMap directory summary and reports 6,093 source-listed locations (4,311 GTA; 1,782 Metro Vancouver) with its observed date. Android verifies the directory’s detached signature, source hash, rights gates, bounded geography and freshness; stale or invalid data produces no rows. The directory remains location-only and cannot establish product identity, price, offer, stock, availability or ranking. The existing 30,000-record identity rail, `0` authorized current offers, private observations, flyer absence, offline networking boundary and fictional Home/Search data disclosure remain explicit. Candidate Linux Android/browser/privacy/signing/release and Python snapshot verification are green; the Windows run still has the known 18 line-ending-sensitive boundary failures. Physical-device ergonomics remain open.

The promoted milestone is now `16e1e68af516df2f4c4ac27b7f9105581a18a150` (`Add barcode identity handoff to Compare Here`). Candidate workflow **33876560165** and milestone provenance workflow **33877137830** passed for the exact same SHA. Scan & Compare now launches the existing user-triggered barcode photo/import activity. One checksum-valid GTIN is looked up against the signed offline identity snapshots off the main thread, and an explicit name choice fills only the first empty comparison entry. The helper preserves existing entries, refuses blank/oversized identities and reports a full draft without overwriting anything. Quantity, currency, observed price, selected price basis and like-for-like confirmation remain required by the existing exact comparison route; identity lookup adds no package, price, store, stock, availability, freshness, ranking, live-offer or network authority. Stale callbacks, edits, cancellation and teardown fail closed. Focused tests, candidate browser/Android verification, privacy, signing and release-bundle checks passed; the Windows run still shows the known line-ending-sensitive boundary failures. Physical-device validation remains open.

The promoted milestone is now `1f3f8763b525d8b6533b9b012ef5a0adc2476946` (`Add offline barcode identity handoff to Good Price`). Candidate workflow **33874530113** and milestone provenance workflow **33875052835** passed for this exact SHA. Good Price now has a user-triggered barcode photo/import route backed by the bundled on-device ML Kit model. The resolver accepts exactly one distinct checksum-valid GTIN, deterministically collapses equivalent UPC/EAN forms, ignores unrelated invalid values, and refuses multiple product codes. The signed offline identity snapshots are searched off the main thread and produce only an explicit, reviewable product-name suggestion. Selecting a name fills the existing editable draft; quantity and observed price remain required by the existing exact parser/check and private memory route. No product, package quantity, price, store, stock, availability, freshness, ranking, live-offer or network authority was added. Camera files are temporary cache artifacts deleted on success, failure, cancellation and teardown; the capture activity is non-exported, CAMERA remains optional, and the built APK has no INTERNET/ACCESS_NETWORK_STATE. Focused tests, candidate browser/Android verification, privacy, signing and release-bundle checks passed; the Windows run still shows the repository's known line-ending-sensitive boundary failures. Physical-device validation remains open.

The promoted milestone is now `1b47547ddb04cb06afc5ed8b6400d8429705a5f9` (`Clarify pending extra-stop evaluation on Home`). Candidate workflow **33871829169** and milestone provenance workflow **33872307030** completed successfully for the exact same SHA. When a Home result has a missing usable price, the collapsed extra-stop disclosure now says `Not evaluated yet` in its immutable renderer summary, matching the existing expanded notice that another stop is not evaluated until every requested item has a usable price. Complete plans retain the original threshold summary. This is a reversible presentation-only correction with no price, offer, store, availability, planner, ranking, network, provider-economics or persistence authority. Focused tests pass. The full Windows Android test task still reports the repository's known line-ending-sensitive source-boundary failures; Linux candidate Android verification, lint/build, privacy, signing, release bundle, browser checks and the Python/catalog suite are green.

The promoted milestone was `8f16e3fd6d6edf53a202c267fee87ba9e940bf99` (`Add consumer source and licence disclosure`). Candidate workflow **33870199022** and milestone provenance workflow **33870665728** completed successfully for the exact same SHA. The shell now exposes a clearly labelled `Sources & licences` action that explains the attributed Open Food Facts ODbL-1.0 identity snapshot, its offline-search-only limits, private observations, fictional Home/Search demo data, and the absence of a store directory, authorized current-offer feed or copied flyer content. The dialog is presentation-only and adds no price, offer, store, availability, planner, ranking, network, provider-economics or persistence authority. Focused tests pass. The full Windows Android test task still reports the repository's known line-ending-sensitive source-boundary failures; Linux candidate Android verification, lint/build, privacy, signing, release bundle, browser checks and the Python/catalog suite are green.

The promoted milestone is now `6743181977f1500772444599a667284b8d7ea2e3` (`Add actionable Home private history review route`). Candidate workflow **33867138431** and milestone provenance workflow **33867613305** completed successfully for the exact same SHA. When readable nonempty private comparison history exists, Home now exposes a renderer-owned `Review private price history` action that opens the existing Scan & compare screen; empty history stays quiet and unavailable history stays on its recovery notice. This is a reversible navigation refinement: it adds no price, offer, store, stock, availability, planner, ranking, network, provider-economics or persistence authority, and the existing non-live-price and package/promotion caveats remain explicit.

## Previous verified engineering head (superseded)

The promoted milestone was `22e45377d40511e5a42aa786264cb562f15a1336` (`Point Home memory summary to review route`). Candidate workflow **33864992864** and milestone provenance workflow **33865459380** completed successfully for the exact same SHA. When nonempty private comparison memory had no matching Home list label, the renderer-owned summary pointed the shopper to Scan & compare prices to review it. This was a reversible route-discovery refinement with no action, storage, price, offer, store, stock, availability, planner, ranking, network, provider-economics or persistence authority.

## Previous verified engineering head (superseded)

The promoted milestone was `08657541629f5e7beb44c046573a8ae5d79be22a` (`Clarify offline catalog list replacement action`). Candidate workflow **33864027611** and milestone provenance workflow **33864356368** completed successfully for the exact same SHA. Home’s offline identity-match dialog now labels its positive action `Replace list word` and gives it an explicit assistive-technology description: only the unresolved Home query is edited; no exact product, price or availability is confirmed. The existing bounded two-metro identity lookup, identity-only disclosure, review-before-Plan step, fictional sample planner and query-only replacement boundary remain unchanged. This is a reversible presentation/accessibility refinement with no price, offer, store, stock, availability, planner, ranking, network, provider-economics or persistence authority added.

## Previous verified engineering head (superseded)

The promoted milestone is now `e02e358eb29ea28e4287ebef717d2d800d2b0720` (`Record Home private memory summary milestone`). Candidate workflow **33862278396** and milestone provenance workflow **33862848704** passed for the exact same SHA. Scan & Compare offers a user-triggered `Take a price photo` action beside photo import. CAMERA is requested only after the tap; a temporary JPEG is written under the app cache through a FileProvider URI with explicit temporary grants, then reused by the existing bounded on-device OCR and review flow. A shared in-flight gate disables both photo actions, stale/lifecycle callbacks are ignored, temporary capture files are deleted on all terminal paths and teardown, and unavailable/denied/error states preserve existing entries. The OCR status is a polite accessibility live region. Home now additionally exposes a bounded count of nonempty private comparison observations when no current list name matches, while retaining the name/package/promotion mismatch and non-live-price boundary. This is a consumer-flow improvement only: no Android networking, live-price authority, stock/availability claim, planner/ranking authority, provider economics or persistent overlay foundation was added. The existing private comparison memory, good-price route, fictional sample disclosure, 30,000-record identity rail and atomic catalog release remain unchanged.

The promoted SHA includes catalog release `a00f948995300374f953053a53c298546b530a52`, verified by offline-catalog workflow **33840204939**, full candidate build **33840205073**, and milestone catalog workflow **33840982043**. The shared Canada identity source now contains exactly 30,000 signed identity records reused by both GTA and Metro Vancouver manifests. It carries no price, offer, package-quantity, stock, availability or ranking authority. Multi-region promotion is now one immutable generation plus one atomic root `active-generation.json` pointer with embedded last-known-good recovery; a failed pointer write can leave only an ignored orphan generation.

The current Home refinement makes automatic private comparison memory visible even when a list has no matching name: a renderer-only summary reports the bounded number of on-device observations and repeats the package/promotion mismatch and non-live-price boundary. Empty history remains quiet; an unreadable store still shows the recovery notice and suppresses history-derived row context. Deterministic helper, renderer and View-boundary tests cover this disclosure. No planner, ranking, current-offer, store, availability, network or provider authority was added.

The full candidate release workflow **33862278396** passed browser/Firefox checks, Android tests, lint, APK build, privacy permission inspection, single-signer verification and release-bundle checks. Physical-device testing remains required before calling camera/OCR ergonomics or launch readiness complete. No Android networking, accounts, tracking, current-price authority, planner/ranking authority, provider economics or persistent overlay foundation was added.

## Previous verified engineering head (superseded)

The promoted milestone was `a5cfa0e609d18f1e3730f934788a72ade88ba37a` (`Rollback coverage report write failures`), verified by candidate build workflow **33829634963** and milestone provenance workflow **33830070695**. The weekly multi-metro refresh kept pointer promotion and diagnostic coverage reporting in one rollback boundary: if any region promotion or the final report write failed, every captured `current.json` and `last-known-good.json` pointer was restored byte-for-byte and the prior coverage report remained unchanged. Deterministic tests covered both a later-region failure and a report-write failure. This added no price, offer, availability, planner/ranking, network, or identity authority. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; offline snapshot tests passed in workflow **33829634993**.

## Previous verified engineering head (superseded)

The promoted milestone is now `b462f01250d0987adfb16fcca7b908bc7bce9bd1` (`Rollback multi-region snapshot promotion failures`), verified by candidate build workflow **33829078507** and milestone provenance workflow **33829440551**. The weekly multi-metro refresh now captures each requested region’s `current.json` and `last-known-good.json` bytes before promotion and restores every pointer byte-for-byte if any later region promotion fails; a deterministic test covers a simulated second-region failure and preserves the prior coverage report. This closes the cross-region partial-promotion gap without adding price, offer, availability, planner/ranking, network, or identity authority. The existing signed 5,000-per-metro identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, rights gates, and Home stale-selection guidance remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the offline-catalog workflow **33829078425** also passed.

## Previous verified engineering head (superseded)

The promoted milestone is now `c05ea098f4f8a6d375817b551befa890b3d250e4` (`Explain stale offline match application failure`), verified by candidate build workflow **33827937894** and milestone provenance workflow **33828361403**. Home’s offline identity-match dialog identifies the exact unresolved word being searched and now explains when a selected identity can no longer be applied because the list changed while the bounded lookup was open; the stale action is disabled so the shopper can review and retry. This presentation/lifecycle correction adds no price, offer, availability, planner/ranking, network, or identity authority. The existing 5,000-per-metro signed identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, deterministic refresh quality gates, and no-price/no-availability disclosures remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the unchanged snapshot inputs remain covered by offline-catalog workflows **33826706509** and **33827098104**.

## Previous verified engineering head (superseded)

The promoted milestone is now `ee38c00792aaa2374e470fbd54bee30bfd3beb4e` (`Fail closed catalog quality regressions`), verified by candidate build workflow **33826706479** and milestone provenance workflow **33827098123**. The offline-match dialog still identifies the exact unresolved Home word it will search/replace in its title across loading, empty and selectable-result states; this gives users and screen readers clear context when multiple list words need clarification. The bundled Greater Toronto Area and Metro Vancouver identity snapshots contain 5,000 records each, 4,199 unique canonical identity names (`WITHIN_TARGET` for the 1,500–5,000 launch band), 4,960 grocery-hinted records, and 41 household-hinted records protected by the deterministic 10% household reserve. Current offers remain explicitly `0` / `NOT_INCLUDED`. Home’s offline catalog handoff states that selecting a bundled identity name only edits the Home list; because the planner is still a tiny fictional sample vocabulary, the inserted name may remain unresolved. An empty catalog result explicitly says that a miss in this snapshot does not mean the product is unavailable. The existing identity-only, no-price/no-availability disclosure remains intact, and catalog selections remain query-only until the separate exact-identity confirmation boundary is deliberately wired. The weekly refresh coordinator now fails closed before import/build/promotion when the selector reports an unsatisfied household reserve or identity-name variety outside the 1,500–5,000 launch target, while preserving existing pointers and reports on rejected regressions. These are identity/category measurements only, not demand, retailer availability, stock, price, package quantity, freshness, or ranking authority. The merchant-feed qualifier still reports identity coverage separately from structural current-offer and unit-value candidate counts; those latter measurements remain `STRUCTURAL_ONLY` with authority `NONE`. The Android build workflow includes both qualifier paths in its push/PR filters. This adds no prices, offers, availability, planner/ranking authority, Android networking, or any new identity claim. Signed manifests and promotion pointers remain authoritative. Clean-source verification passed 400 shared-core tests and 1,337 Android app tests with zero failures, all 58 Android tasks, 65 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification, exact-SHA release-bundle provenance, and exact-SHA offline-catalog workflows **33826706509** (candidate) and **33827098104** (milestone).

## Previous verified engineering head (superseded)

The promoted milestone was `b6018ab9d08f22558f01c0fe36609dad832b22f5` (`Search offline identity snapshots across supported metros`), verified by candidate workflow **33809477765** and milestone provenance workflow **33809973694**. The offline catalog refresh boundary is deterministic and offline for the supported Greater Toronto Area and Metro Vancouver identity snapshots, and Home searches both independently region-bound snapshots: identical identities are deduplicated, conflicting record ids are omitted, merged work is capped, and the combined lookup fails closed if either region is not admitted. Explicit source, rights, timestamps, and signing keys remain required; all candidates are selected, built, signature-verified, and coverage-checked before promotion; and a regression leaves existing per-region pointers unchanged. It remains identity-only and does not claim current prices, offers, stock, availability, or planner/ranking authority. Clean-source verification passed 397 shared-core tests and 1,333 Android app tests with zero failures, all 58 Android tasks, 61 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification and exact-SHA release-bundle provenance.

`5f36a19e3467fcf58a0bca84d5864f56dcdf74d6` (`Dismiss stale Saved confirmations on route changes`) is the promoted milestone, verified by candidate workflow **33756824438** and milestone provenance workflow **33756823998**. Saved now dismisses its window-level “Clear all” confirmation whenever the shell leaves Saved, including the Compare route, so a destructive confirmation opened for Saved cannot float over Home, Basket, Search or another destination. This is presentation/lifecycle-only: no planner, ranking, pricing, evidence, persistence, provider, provider-economics or network authority changed. Clean-source verification passed 1,696 JVM tests (375 shared-core + 1,321 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`cff0a346fb3ae37f59a52d92cdc718cd053ee030` (`Dismiss stale Home item details dialogs`) was the promoted milestone, verified by candidate workflow **33748458980** and milestone provenance workflow **33748992285**. The Home item-details editor is lifecycle-bound to the Home route: opening a new editor dismisses any prior instance, leaving Home or opening Compare dismisses it, and Activity teardown releases it. This prevents an old package-count/brand/exact-product dialog from remaining visibly actionable over another destination or reappearing with stale Home state. The change is presentation/lifecycle-only and does not alter the typed Home session, planner, ranking, pricing, evidence, persistence, provider, provider-economics or network authority. Clean-source verification passed 1,689 JVM tests (375 shared-core + 1,314 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`8182c1dd6bb6e7aea9c1fc22f5c40371eac1dec1` (`Name observed-price Saved selection actions`) was the promoted milestone, verified by candidate workflow **33747358617** and milestone provenance workflow **33747877782**. Observed-price Saved product/store controls now expose projected, display-name-specific accessibility descriptions such as “Select saved product Whole Milk,” so assistive technology can identify exactly which safe saved choice a typed action changes. This is presentation-only and does not alter Saved identity, persistence, prefill, planner, ranking, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,688 JVM tests (375 shared-core + 1,313 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`b2c93b52cb9fa47aac6bad3cdc5b2587b2fc515c` (`Fail closed observed-price Saved selection`) was the promoted milestone, verified by candidate workflow **33746227407** and milestone provenance workflow **33746657577**. The observed-price Saved-selection renderer now updates every product/store/clear-selection and prefill-check button immediately when its typed lifecycle owner is installed or cleared, so a detached surface cannot leave already-rendered controls looking actionable. This is presentation/lifecycle-only and does not alter Saved identity, persistence, prefill, planner, ranking, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,688 JVM tests (375 shared-core + 1,313 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`62c931ac136b6b4fd7853069cc1359a545b14bac` (`Dismiss detached Saved confirmations`) was the promoted milestone, verified by candidate workflow **33745130893** and milestone provenance workflow **33745673251**. Saved now dismisses an open “Clear all” confirmation when its lifecycle owner detaches, and when a fresh immutable projection replaces the state that opened it, so a detached or stale dialog cannot remain visibly actionable. The existing typed action is still emitted only after explicit confirmation; this is presentation/lifecycle-only and does not alter Saved persistence, identity, planner, ranking, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,687 JVM tests (375 shared-core + 1,312 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`fda5749f1a821f369a6a170c4175e3d9e0c912b5` (`Collapse detached Home extra-stop settings`) was the promoted milestone, verified by candidate workflow **33743998315** and milestone provenance workflow **33744536925**. Clearing the Home extra-stop owner now collapses an already-expanded settings panel immediately, so detached/reused Home surfaces cannot leave stale rule choices visibly open; the change is presentation/lifecycle-only and does not alter the existing immutable planner result. Clean-source verification passed 1,686 JVM tests (375 shared-core + 1,311 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`3e8c94865de43cb0d20fbe353b56b53afb4558c2` (`Release Home owners when activity is destroyed`) was the promoted milestone, verified by candidate workflow **33743220204** and milestone provenance workflow **33743655775**. MainActivity now releases every Home and Basket callback on destruction, so detached/reused surfaces cannot retain actionable owners or an Activity reference; the change is lifecycle-only and does not alter the existing immutable Home result rendering. Clean-source verification passed 1,685 JVM tests (375 shared-core + 1,310 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`daa70e4c9d62305a83022758b30cdf9072abc38b` (`Fail closed observed price draft editors`) was the promoted milestone, verified by candidate workflow **33741793189** and milestone provenance workflow **33742341341**. The price, observed-time, and proof-reference draft editors now update all text, radio, and Apply controls immediately when typed owner callbacks are installed or cleared; detached evidence drafts cannot remain editable without their owner, and each editor starts disabled. This is presentation/lifecycle-only: no parsing, policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`941f2c267f4913a7554ae7593f8df85bb9884af9` (`Fail closed observed price confirmation action`) was the promoted milestone, verified by candidate workflow **33740867817** and milestone provenance workflow **33741392931**. The observed-price confirmation button now updates its enabled state immediately when the typed owner callback is installed or cleared, and remains inert before the first immutable render; a detached confirmation surface cannot look ready to submit evidence. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`6ac3ae2cbc6a6a4a855017911d060c458b795408` (`Fail closed Basket navigation on owner changes`) was the promoted milestone, verified by candidate workflow **33739991354** and milestone provenance workflow **33740563023**. Basket’s Open Home navigation control now updates its enabled state immediately when the typed owner callback is installed or cleared, and remains inert before the first immutable render; a detached Basket surface cannot look actionable. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`bad95f10c83235596532ed1caf05793ad376219c` (`Fail closed Home controls on owner changes`) was the promoted milestone, verified by candidate workflow **33739072386** and milestone provenance workflow **33739628868**. Home query editing, submit, compare, item-detail/remove, refinement, and extra-stop controls now update their enabled state immediately when typed owner callbacks are installed or cleared; a detached Home surface cannot look actionable or accept edits without its owner, and controls remain inert before the first immutable render. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,683 JVM tests (375 shared-core + 1,308 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`d03bba4aa08ff1b21889f857aa6e0f1fcf37663d` (`Fail closed Watch setup controls on owner changes`) was the promoted milestone, verified by candidate workflow **33737614216** and milestone provenance workflow **33738144350**. Watch Saved selection controls, policy Apply/no-limit controls, and policy editors now update their enabled state immediately when typed owner callbacks are installed or cleared; detached surfaces cannot look actionable or remain editable without their owner. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`697c6319d40e4094b607135964f3a791e774c3be` (`Fail closed Saved launchers when owner clears`) was the promoted milestone, verified by candidate workflow **33736212427** and milestone provenance workflow **33737249953**. The Saved Watch My Staples and observed-price launchers now update their enabled state immediately when the lifecycle owner installs or clears the typed callback, so already-rendered Saved entry points cannot continue to look actionable after detachment. This is presentation/lifecycle-only: persistence, identity, provider execution, ranking, evidence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`d15fa825dc9e429a4f8043a543bcdba012a571ab` (`Fail closed Saved controls when owner clears`) was the promoted milestone, verified by candidate workflow **33735195361** and milestone provenance workflow **33735675051**. Saved action buttons now update their enabled state immediately when the lifecycle owner installs or clears the typed callback, so an already-rendered Saved surface cannot continue to look actionable after detachment. This is presentation/lifecycle-only: persistence, identity, provider execution, ranking, evidence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`f1ad62c4a1c7d76c52e9dae4a7fd84ce16773b7e` (`Gate Home extra-stop disclosure by owner`) was the promoted milestone, verified by candidate workflow **33731893804** and milestone provenance workflow **33732437637**. Home’s advanced extra-stop disclosure now obeys the same owner-driven fail-closed gate as its option chips: a detached/reused Home renderer cannot open the interactive panel without its typed owner callback, while an attached owner preserves the existing progressive disclosure. This is presentation/lifecycle-only: provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,681 JVM tests (375 shared-core + 1,306 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`7285a35da741eaad71d81914e17279c3ca7e334a` (`Disable duplicate Search quick-entry chip`) was the promoted milestone, verified by candidate workflow **33730319345** and milestone provenance workflow **33730738420**. Search’s quick-entry chips now mirror the existing immutable lifecycle guard: the chip for the query already `LOADING` is visibly disabled, while different quick queries remain enabled as explicit replacement choices. The click guard and controller no-op remain in place as defense-in-depth. This is presentation/lifecycle-only: provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,681 JVM tests (375 shared-core + 1,306 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`a6260eb2edfe40769013bf05241308d3c500a4e7` (`Reject duplicate Search submits while loading`) was the promoted milestone, verified by candidate workflow **33728615359** and milestone provenance workflow **33729137572**. The Search controller rejects a raw `Submit` while an existing request is `LOADING`, preserving the active immutable state and emitting no replacement request. `QueryChanged` remains the intentional cancellation/replacement boundary, and the visible button, keyboard path and quick-entry chips retain their readiness gates. This is lifecycle defense-in-depth only: provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,680 JVM tests (375 shared-core + 1,305 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`00f8e3fdff369b4ce3237f2b3a4cbc78984ba035` (`Announce Watch projected warnings`) was the promoted milestone, verified by candidate workflow **33727646911** and milestone provenance workflow **33728147638**. Watch My Staples marks its projected safety/display-metadata warning as a polite accessibility live region, so the specific warning is announced when it appears or changes instead of relying only on the broader status title. This is a presentation-only accessibility correction: the renderer consumes immutable `StapleWatchUiState`, while Watch policy, economics, notification, evidence, persistence, provider and network authority remain unchanged. Clean-source verification passed 1,679 JVM tests (375 shared-core + 1,304 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`3e3862fce53dfca7564c9c3b99764995efe67737` (`Prevent duplicate Search quick entries`) was the promoted milestone, verified by candidate workflow **33726614855** and milestone provenance workflow **33727114141**. Search's visible button, keyboard path and quick-entry chips now share one immutable lifecycle boundary: blank, over-limit and active-loading searches cannot submit, and tapping the same quick-entry chip during loading cannot restart identical provider work. A different quick query remains an explicit replacement choice. This is a presentation/lifecycle guard only: the helper consumes existing `UniversalSearchState`, while controller request identity, provider execution, ranking, evidence, persistence and network boundaries remain unchanged. Clean-source verification passed 1,678 JVM tests (375 shared-core + 1,303 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`6822542020da9a652b8fb067b5d9afde1793b22f` (`Guard Search keyboard duplicate submissions`) was the promoted milestone, verified by candidate workflow **33725677152** and milestone provenance workflow **33726058966**. Search's visible button and keyboard/quick-entry paths now share one immutable readiness gate: blank, over-limit and active-loading searches cannot submit, so an IME action cannot clear an active request or start duplicate work. This is a presentation/lifecycle guard only: the helper consumes existing `UniversalSearchState`, while controller request identity, provider execution, ranking, evidence, persistence and network boundaries remain unchanged. Clean-source verification passed 1,677 JVM tests (375 shared-core + 1,302 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`2a31c0d96e733c667a7fa7ed1636506eb423aad6` (`Explain incomplete Basket extra stop`) was the promoted milestone, verified by candidate workflow **33724167757** and milestone provenance workflow **33724622104**. Basket now carries Home's existing typed explanation that the extra-stop rule is not evaluated until every requested item has a usable price, so incomplete known-subtotal plans cannot imply that a second stop was evaluated. Complete plans remain unchanged; no-coverage plans keep the rule hidden. This is presentation-only parity: the renderer consumes immutable projected fields, does not infer or calculate prices, and does not change planner, ranking, evidence, persistence, provider or network authority. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`2690b78d3c8c61b2f844c357c4c096b792ce244a` (`Carry missing price notice into Basket`) is the promoted milestone, verified by candidate workflow **33723174528** and milestone provenance workflow **33723634750**. Basket now carries Home's existing typed per-item “No usable price yet — not included in this plan.” notice into no-coverage rows, so a shopper can see why an item is not part of the displayed plan. Incomplete plans retain Basket's existing “No usable price yet — not ready to collect” collection warning; complete plans and collectible rows remain unchanged. This is presentation-only parity: the renderer consumes immutable projected fields, does not infer or calculate prices, and does not change planner, ranking, evidence, persistence, provider or network authority. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`2bdb750d8d067371ee68bed88cbcfcfca82110f1` (`Explain missing Home item prices`) is the promoted milestone, verified by candidate workflow **33722478794** and milestone provenance workflow **33722879331**. Home’s existing item rows now expose a renderer-ready “No usable price yet — not included in this plan.” notice whenever the already-projected result has no store assignment for that item, including no-coverage results. Covered items retain their exact planned store; complete plans remain unchanged. The notice is presentation-only and cannot infer, repair, rank or calculate any price. The preceding Home extra-stop threshold control explains when a result is incomplete: the shared planner will not evaluate another stop until every requested item has a usable price. That notice appears in the expanded panel and its accessibility summary; complete results remain unchanged, and the saved threshold remains typed and selectable for later complete coverage. This is renderer-ready presentation only and does not recalculate money, rank stores, infer missing prices, or change planner policy. The preceding Saved observed-price entry point explains what a shopper must prepare before recording evidence: choose a saved product and store, then record a personally observed price with proof. When saved content exists but one or both prerequisites are missing, it projects a heading plus a precise missing-product/store notice; the action remains unavailable until the existing gate passes. Empty, loading and error states stay quiet. The new copy is renderer-ready and does not create current-price authority, infer evidence, or alter storage. The preceding observed-price confirmation action renderer still announces asynchronous confirmation, rejection and failure messages through a polite accessibility live region, while its button remains gated on both immutable readiness and the presence of its typed owner callback. Basket’s projected local check-off safety disclosure still announces politely when collection becomes available, so TalkBack can hear that marking items is only a foreground aid and does not place an order or change the plan. The top-level shell still announces its existing destination title politely; the main Saved surface still announces its projected unresolved display-metadata warning politely; existing Saved lifecycle status, Watch setup selection summary, fail-closed notice and foreground fact-resolution progress remain polite live regions; Watch policy setup still announces its projected validation notice and “Still needed” requirements card politely; Watch My Staples still announces projected readiness/economic status and exposes its exact switch candidate as one coherent accessibility summary; Compare Here still announces projected input/readiness and evaluated-result status with one-summary exact/blocked cards; Search result cards still expose complete projected summaries with explicit fictional-sample context; the shared projected Home/Basket result container remains a polite accessibility live region, while Home’s query wrapper, Search’s ready/loading/results/error status, Basket’s local check-off progress, Saved lifecycle status and Home refinement feedback remain gated and announced through their existing live regions. Basket still distinguishes a ready-but-unsubmitted list from an incomplete one with “Plan this list on Home.” No planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into a View. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Prior checkpoint (superseded)

Latest promoted head: `0cf9b253c7f6f28835cb4f2fd0019099b66ce783` (`Acknowledge Watch setup handoff`), candidate workflow **33674718972**, milestone workflow **33675293225**. The current app slice adds presentation-only acknowledgement after a configured Watch setup handoff: the user sees that the explicit selection was accepted, while the surface clearly says that no switch decision exists until current prices, route details and evidence checks are supplied. The acknowledgement is attached to the current immutable setup projection and clears when the selection or validated Saved snapshot changes; rejected attempts explain the fail-closed setup issue. It adds no fact, pricing, planner, ranking, persistence, networking, clock, alert or notification authority. The preceding Basket slice adds a local-only Clear check-off control that appears only when foreground collection marks exist, preserves the immutable plan and eligible item keys, and resets only check-off progress. The shared Home/Basket result-card refinement maps the existing immutable missing-price marker to a calm amber treatment for known subtotals, while complete baskets remain green. The current Watch setup selection summary remains intact: the selected staple count is shown against the existing two-item handoff minimum, alongside whether a usual store has been selected. The Saved Watch entry point explains that recurring saved items and a usual store are used to check whether a future switch is worth the trip, with no live-price, alert, or notification claim. Both summaries and the caution treatment are derived from immutable presentation state; physical Views only render supplied values and emit typed actions. The preceding bounded local-only cross-session persistence around `PracticalShoppingHomeSession.State` remains intact: the last Home list, chicken choice, extra-stop preference and opaque request-details bytes are restored only for the exact established request; missing/oversized/corrupt values fail closed, and unsubmitted drafts do not persist stale details. Existing per-item package count, brand and exact-product controls remain separate from planner arithmetic. No Watch setup copy, handoff acknowledgement, Home details, result-card styling, or Basket check-off reset feeds planner, ranking, pricing, evidence, clock, networking, or View authority. The provenance proof accepts both `candidate/*` and historical `work/valuepilot-*` candidates but excludes the milestone branch. Full candidate Android/browser/privacy/signature/release checks and milestone provenance passed.

Purpose: compact durable recovery point. Newer repository/account evidence overrides this file.

## Startup order

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `CONTINUATION_CHECKPOINT.md`
4. `VALUEPILOT_MASTER_CONTINUATION_PROMPT.md`
5. `PROVIDER_ACCOUNT_STATUS.md`
6. `OPEN_DATA_INTEGRATION_STATUS.md`
7. `ARCHITECTURE.md`
8. relevant provider/rights/data-audit files for the task

## Permanent architecture

ValuePilot is provider-neutral shopping intelligence, not an Accessibility/OCR/overlay product.

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic decision/ranking -> immutable presentation -> replaceable UI`

Rules: Product != Offer; claims stay separate; acceptance != conflict resolution; exact deterministic money/quantity math; affiliate/provider economics never rank; feed access != production rights; dataset recency != offer freshness; currency != geography; shared core owns no hidden clock; no provider credentials in source; no speculative Android networking/provider plumbing.

## 2026-08-29 strategic product decision

Provider-by-provider approval is no longer the primary launch path. It is too slow and fragile for a startup with effectively $0 cash. Rakuten/Jamieson/GS1/Walmart and future commercial feeds remain supplementary high-authority accelerators, not launch blockers.

The consumer proposition is NOT "5 million live-priced products." Open/free product identities can make recognition broad, but the app must optimize practical shopping decisions.

Primary promise:

**Given what I need, where I am, and how much inconvenience I will tolerate, what is the cheapest sensible way to shop?**

Default decision policy is **ONE STORE FIRST**.

- Find the best single-store plan within reasonable user constraints.
- Evaluate one optional second stop only when incremental savings are clearly worth the hassle; initial product default can be around a $15 minimum saving, to be validated empirically.
- Do not hide parking/walking/checkout/extra-stop friction inside an opaque score.
- Keep price, travel time/cost, item coverage, missing data and confidence/freshness visible separately.
- Unknown price remains UNKNOWN.
- A third store is not a normal recommendation; only consider it in an explicitly aggressive savings mode.

## Consumer experience / quality bar

The app must be extremely polished, fast, simple, obvious and non-confusing. Complexity belongs under the hood.

Primary experiences:

1. **Plan My Shop** — Home/hero flow. One simple request/list input; default result is one best-store recommendation card. Advanced controls use progressive disclosure.
2. **Compare Here** — in-store barcode/camera/OCR comparison with exact unit value. Must remain useful even with incomplete cross-store coverage.
3. **Watch My Staples** — retention via meaningful basket/staple savings alerts, not penny-saving notification spam.
4. **Receipt/import** — optional user-benefit feature only (automatic list reconstruction, spending/history/comparison). Never make receipt crowdsourcing the core model.

For every screen/change: one obvious primary action, useful defaults, minimal text/buttons, calm loading/empty/error/unknown states, bounded performance, no UI jumping, and no claim stronger than evidence.

## Free/open data direction

There is no known legitimate free complete dataset of fresh store-specific prices for every Canadian grocery retailer. Do not pretend otherwise and do not rely on unauthorized scraping/private endpoints/anti-bot circumvention.

Use provenance-separated rails where licences permit:

- Open Food Facts — broad packaged-product recognition/metadata.
- USDA FoodData Central — CC0/public-domain enrichment; not Canadian availability proof.
- validated produce/PLU identity source — loose produce/category identity.
- Open Prices — supplemental proof-backed observed/historical prices.
- OpenStreetMap — store/location/routing foundation where appropriate.
- user evidence adapters — barcode/shelf label/camera/product-page share/digital receipt, always giving immediate user utility rather than chores.
- merchant self-service later — stores may submit CSV/feed/API because inclusion can send customers.
- authorized provider/affiliate feeds — higher-authority accelerators when rights/geography/freshness pass gates.

Keep ODbL/share-alike sources source-isolated; do not collapse them into an incompatible proprietary master database.

Launch depth before breadth: one dense metro area, roughly 1,500–5,000 high-frequency grocery/household concepts for strong local utility, while millions of open identities remain backend recognition rather than a vanity KPI.

## Earlier verified engineering baseline

The current promoted baseline is `227ed24059784989bc2ee38845181297f0430d0e` (`Show Home plan on Basket tab`), verified by candidate workflow **33635484179** and promoted workflow **33636063647**. Basket now consumes the existing immutable Home render state and passes its exact projected result through unchanged. It exposes recognized and unresolved items, complete or honestly incomplete one-store results, any shared-core-approved optional second stop, the selected exact extra-stop rule, and the same explicit fictional/offline disclosure. Empty/refinement/unresolved states never fabricate a plan and provide one typed action back to Home. Home and Basket use one shared physical result renderer; neither Basket renderer nor View owns planning, ranking, money arithmetic, provider logic, or missing-fact inference. Independent clean-LF verification passed 1,546 JVM tests with zero failures/errors/skips, all 58 Android tasks, all 30 browser tests, Firefox lint, APK privacy inspection, and one-signer APK verification. The local worktree was clean at code verification.

The preceding verified Home/Compare baseline is `c6c36416ab136473d7ddc22eef232de6ed090e55` (`Bound Practical Shopping Home query input`). Home supports typed removal of resolved items and unresolved tokens, immediate deterministic replanning, preserved unknown/ambiguous states, item-specific accessibility descriptions, deterministic `and`/`&` list syntax, and an explicit “Price freshness” label on result evidence summaries. Its advanced extra-stop preference is progressively disclosed only after an actual result; immutable presentation owns availability, the View binds it mechanically, and the persisted exact threshold still governs the first plan and later replanning. The Home list editor exposes the existing 240-character policy as a visible counter and accepts at most the model's existing 241-character retained error sentinel. Compare Here retains bounded per-entry removal, explicit Current shelf prices / Member prices selection, pure readiness, 4,096-character physical editor bounds, fail-closed restoration, and no member-to-current price fallback.

Minimum known durable code baseline before 2026-08-29 documentation changes: `deaa478c9b679f8820b47a80d7f43c5b18787677` with verified code parent `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` (`Re-evaluate production search before display`). Always inspect the current branch tip before work.

Verified production ranking/presentation includes exact unit value, bounded Best Value ranking, immutable presentation, source/UI leakage hardening, generation ordering, 128-item projection cap, renderer isolation, inactive hidden production Search view, and display-time re-evaluation from raw evidence/current lifecycle.

Recent exact workflows passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

The production Search view is physically present but hidden and MainActivity does not drive it. Visible Search remains fictional/sample. Keep production evidence out of legacy `UniversalSearchController` / `ValueItem` / `Double` ranking paths.

## Current provider/account evidence

### Rakuten / Jamieson

Rakuten Product Catalog technical access is enabled. Jamieson partnership + advertiser Product Feed approval + actual complete 273-row feed availability are proven.

On 2026-08-29 Rakuten Publisher Support clarified:

- downloaded Product Catalog files may be used in the publisher's system for comparison;
- app use/comparison requires confirmation from the relevant advertiser;
- if partnership ends, affiliate/product links become inaccessible but downloaded feed files remain saved in the publisher's system;
- physical file retention does not itself prove ongoing display/use rights after partnership end;
- Android affiliate-link use requires advertiser permission/terms.

This substantially reduces Rakuten-side storage/comparison ambiguity and moves application/display/comparison permission to the advertiser-specific gate.

A Jamieson permission email has already been sent asking for Android display, search/comparison, cache/index/storage, affiliate-link use, retention/deletion requirements and confirmation that the approved feed represents products/offers intended for Canadian consumers. **Do not resend while waiting unless their reply is incomplete/new evidence creates a question.**

Jamieson feed quality checkpoint remains: 273 rows; 271 supplied valid GTINs; all CAD/in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank; Attribute 1 opaque. Never repair the two inverted rows or infer package quantity from opaque/title/image fields.

### Walmart Canada

Rakuten Walmart Canada MID 36751 currently blocks application through advertiser-supplied eligibility terms before an Apply action is available. Do not falsify account/channel details. If pursued later, ask Rakuten which exact eligibility condition fails. Walmart is not a launch blocker under the new strategy.

### GS1 Canada

ECCnet inquiry sent and acknowledged 2026-08-28; substantive eligibility/rights/technical response remains pending unless newer authenticated evidence exists. GS1 remains a package-content accelerator, not a launch dependency.

## Current Android privacy boundary

At the last verified checkpoint Android had no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Do not silently change this boundary. Future networking/server work needs an explicit milestone and privacy/offline/failure design.

## Immediate next engineering work

The promoted Home first-value action placement slice now puts Scan & compare and Good Price directly beneath Plan my shop; physical-device validation should confirm that order, focus and accessibility behavior.

Do not wire Rakuten/GS1 into production Search and do not keep extending speculative provider plumbing.

The bounded Practical Shopping MVP fixture/controller, Home projection, one-store/second-stop controls, and consumer correction actions are now implemented and verified at the current head. The original implementation sequence is retained below as historical acceptance criteria; do not treat it as unfinished work.

The latest promoted slice keeps no-coverage results honest across Home and Basket. It labels the Basket state “Price coverage needed,” uses state-safe shell copy, and suppresses generic check-off progress when no primary store plan exists, in addition to hiding the recommendation-only extra-stop rule and matching the guidance to the actual evidence state. It also keeps Saved shell copy neutral before lifecycle content is available. It changes no typed collection state, plan, totals, planner, ranking, pricing, evidence, persistence, clock, notification, network, or View authority; the existing Home extra-stop disclosure, Basket typed eligibility/item-preference context and local-only check-off notice remain intact.

The newest promoted slice adds item-specific assistive-technology descriptions to Saved product/store removal actions. The descriptions are projected only when the lifecycle enables mutation; loading, refreshing, updating and error states remain fail-closed with no row actions. It changes no Saved identity, persistence, planner, ranking, pricing, evidence, notification, network, or View authority.

The newest promoted slice extends that context to Watch product and usual-store selection actions. Each toggle names the saved choice it changes, while the selection projector continues to own identity reconciliation, readiness, and typed action direction. It changes no fact, price, route, policy, persistence, planner, ranking, notification, network, or View authority.

The latest promoted slice makes an explicit Home missing-price state actionable. Only rows already carrying the renderer’s “No usable price yet” notice expose `Add observed price`; the typed action opens Good Price with a bounded item name prefilled as untrusted text. The shopper must still enter exact package quantity and observed price, and any saved result remains device-only personal history. Complete rows stay quiet, blank/oversized external prefills are ignored, and Activity teardown clears the callback. No product/store/live-offer/planner/ranking/network authority changed.

The latest promoted slice also adds clearly labelled fictional offline `Try a sample item` chips to idle Home. Eggs, Milk, Bananas, Bread and Chicken are bounded text-entry shortcuts routed through the existing Home query reducer; duplicates are idempotent, over-limit additions fail closed and the row hides once a query exists. No identity, price, store, availability, evidence, planner, ranking, persistence or network authority changed.

The currently identified unblocked consumer presentation gaps are implemented through the promoted Home result-reveal and quick-add slices. The next meaningful gate is physical-device validation of Home quick-add/scroll/keyboard, camera/OCR and accessibility behavior; continue only with another direct local consumer benefit after that. Keep catalog selections query-only and the fictional/sample planner visibly offline until concrete local evidence and travel inputs are available; unknown prices remain unknown, one-store-first stays explicit, and Views remain limited to immutable rendering and typed actions. Do not activate production Home or add provider plumbing without explicit lawful rights.

Historical MVP sequence:

1. Inspect the existing Home/Search/Basket/Saved shell and immutable UI-state boundaries first.
2. Define the smallest deterministic shopping-request/store-plan domain model: requested items, candidate store, matched/missing coverage, exact known basket cost, travel metadata, confidence/freshness, and optional second-stop incremental-savings decision.
3. Reuse existing exact `Money` / quantity / deterministic value components; do not duplicate arithmetic.
4. Encode one-store-first as the default invariant and an explicit second-stop savings threshold/route constraint rather than a hidden hassle score.
5. Use a tiny fictional/sample multi-store fixture only to prove the flow. Never represent it as live merchant data.
6. Build the Home experience around one primary action and one recommendation card; progressive disclosure for advanced constraints.
7. Add performance budgets/tests early: bounded candidates/list sizes, no heavy main-thread work, stable immutable rendering, smooth scrolling/interactions, no random jump-to-top behavior.
8. After the decision flow is excellent, add source-isolated open product/location adapters.
9. `Compare Here` is the second major consumer slice; `Watch My Staples` is the third.
10. Provider/account replies continue in parallel and are incorporated only when they unlock real high-authority evidence/commerce value.

## Verification discipline

For each engineering slice: inspect repository first, state invariant, implement smallest durable boundary, add deterministic failure/unknown/stale/conflict/bounds tests as relevant, run existing tests/lint/build/privacy checks, review exact diff, commit only intended changes, and update these durable docs when state materially changes.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.
