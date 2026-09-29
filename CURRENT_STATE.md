# Current state

Updated: 2026-09-29 04:30 UTC

## 10113 Google Images refinement checkpoint — 2026-09-29

Focused refinement commit: `0cb655c3ebde2bfa219cfc4393e84e56543d0485` on
`work/valuepilot-m128-m129`. Google Images now requests an adjustable partial
Custom Tab beginning at 65% of display height, and a provider rejection prompts
before any full-screen browser fallback. Existing explicit Photo taps,
double-tap protection, unverified-result disclosure, state preservation and
offline/failure boundaries remain intact.

VersionCode `10113` / versionName `101.1.12` was built locally as the unsigned
candidate `android/app/build/outputs/bundle/release/app-release.aab` (SHA-256
`00AD039ABEDEF27BA488783949BDD455163722CC361BD062DBBAAC7BFA91AB12`). The
previous protected signed 10112 AAB remains unchanged and is not represented
as containing this code. Local verification passed: 1,732 Android app tests,
405 shared-core tests, debug/release lint and builds, release bundle and
privacy inspection with no `ACCESS_NETWORK_STATE`. No Play upload, signing,
installation, Motorola test or Actions retry was performed.

## 10112 integration checkpoint — 2026-09-29

The active stacked branch is `work/valuepilot-m128-m129` at
`080f24e998679e8173862f4b6bd217dc195023f6`. PR #41 targets `main`, PR #42
targets PR #41, and PR #43 targets PR #42; all are open and currently reported
mergeable at their stacked bases. A read-only merge-tree calculation against
`origin/main` produced `4ea3ee61af529b5b339c21ef476509626b4412df` with no
conflicts. No PR was merged or duplicated locally.

The integrated code preserves Search navigation/query/pagination state,
durable private Home/Basket identities, exact-key deduplication and
remove/re-add semantics, Secadero partial/invalid durable-state protection, and
separate Search Photo/Save touch targets, accessibility labels and callbacks.
The surfaces remain identity/private-evidence boundaries and do not invent
price, stock, ranking or provider authority.

Final local gates for this code tree are green: 1,730 Android app JVM tests,
405 shared-core tests, debug/release lint, debug/release builds and release
bundle. Privacy inspection confirms no `ACCESS_NETWORK_STATE` permission.
VersionCode `10112` / versionName `101.1.11` is unused in Play Console. The
signed local AAB is
`android/app/build/outputs/bundle/release/app-release-play-upload-signed-10112.aab`
with SHA-256
`DBF0AC4212F8AB9F95BD16C5D3A7EDAF93878EFC3F1103B0EA70ADA708F05356`; its
upload certificate matches the protected Play upload certificate. It has not
been uploaded or installed. Remaining gates are the GitHub Actions billing /
spending-limit blocker and later Motorola validation.

The latest PR #43 runs (`36517192131`, `36517195056`) were not started because
GitHub reports failed recent payments or a spending-limit requirement. No paid
usage or rerun was requested.

## Historical M12.4–M12.9 checkpoint — 2026-09-27

The active continuation branch is `work/valuepilot-m124-m129`. The verified
Data Status repair remains at `dcb40372bd0ed6f45afc87fea405d1dfdf01775b` and
was not repeated; the untracked `device-audit/` directory is preserved. The
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
The single [M12.4–M12.9 work ledger](M12_4_M12_9_WORK_LEDGER.md) now drives
the remaining goal. M12.6 has a new bounded, offline rights-aware image
evidence manifest tool and a discovery audit
([ARGENTINA_PRODUCT_IMAGE_DISCOVERY_M12_6.md](ARGENTINA_PRODUCT_IMAGE_DISCOVERY_M12_6.md));
it broadens documented role/variant discovery but does not claim remeasured
full-catalog coverage or download image bytes.

The M12.8 Search-to-Home handoff is now merged: an Argentina identity result
can be added to the private Home draft as one clearly unresolved multi-word
item. Search cards now also show an explicitly validated Argentina package
size when the source supplies one; missing quantity remains unknown. These
changes do not add price, stock, availability, ranking or planner authority;
the Home planner remains the fictional offline sample path.

M12.5 now has bounded SHADOW-result reconciliation, terminal failure alerts,
write-free success checks, and idempotent receipts. It does not enable
unattended production promotion. Its focused local tests pass (42 tests).
PR #21 additionally serializes the GitHub acquisition handoff before SHADOW
reconciliation and keeps recovery reconciliation enabled after acquisition
failure. Exact-head PR runs `36315163770` and `36315146325`, plus post-merge
main runs `36315728824` and `36315728794`, passed Android test/lint/build,
privacy, single-signer, browser and acquisition-control checks. Release and
provenance uploads were skipped by policy. The PR #22 exact-head run
`36316729499` and post-merge main run `36317378112` also passed browser and
Android test/lint/build, privacy and single-signer checks; provenance and
release-bundle uploads were skipped by policy.
The deployed `valuepilot-argentina-release` workflow is now ACTIVE at
revision `000013-32a`. Its latest scheduled execution was on the prior
revision `000012-0a4` (`c46e8991-4927-4737-b6ae-5af7a2818029`): Cloud Build
`f929bfbe-82fc-4a1d-862d-c5f7e65bdd36` succeeded and persisted candidate
`argentina-sepa-2026-09-25-93ce53aac8d3db32-routing-v1-search-v2`, but the
workflow could not verify/write its discovery-state object and therefore
returned `STATE_WRITE_UNVERIFIED`; promotion remained `SHADOW_ONLY` with zero
active-pointer writes. This is useful candidate evidence, not end-to-end
production qualification, and no unattended promotion is enabled.
Read-only runtime inspection now shows both regional `control/active.json`
pointers at
`argentina-sepa-2026-09-25-93ce53aac8d3db32-routing-v1-search-v2`; the active
manifest is complete and qualified from the official source hash
`93ce53aac8d3db322c07b0bc66900016a91aba6b4ccf858a835bfb5f3f13b680`, while
its own metadata records `automatedOfficialAcquisitionProven: false`. The
running backend advertises that release and `androidNetworkingAuthorized=true`.
This still does not prove a credentialed >5-result physical Search smoke.
Ordinary research artifact retention was reduced from 14 to 3 days; the two
durable release/provenance artifacts were deliberately preserved. GitHub run
`36295847180` is **not green**: its code tests passed, but artifact uploads
failed because the Actions storage quota was full.

The build workflow now keeps disposable browser/APK/release uploads only for
explicit `candidate/*` and `work/valuepilot-android-milestone` pushes;
ordinary `work/valuepilot-*` pushes still run the full checks without storing
duplicate outputs.
The two rebuildable Open Prices research workflows likewise keep their
reports in the job summary and upload them only for an explicit manual run.

M12.6's first real coverage audit is recorded in
[ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.md](ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.md).
Against 66,716 valid-GTIN Argentina identity records (44,683 unique GTINs),
only 21 strict exact Open Food Facts image candidates were found (0.031477%);
none had per-image licence evidence, so display-eligible coverage is 0%.
The 389 `yerba` identities had 0 strict image candidates. No images were
downloaded, committed, or added to Android. This is measured evidence, not a
90% claim.

The M12.4/M12.9 status in this historical section was superseded by the
10112 integration checkpoint above. The earlier unsigned 10108 / 101.1.7
candidate and installed 10107 build are historical records only.

The bounded M12.4 trace gate was strengthened in commit
`5a16e1166dd8d7d5a8d9c2893cfabe84ff8b4a97` and merged through PR #17 into
`main` at `7840cb6555200ef3692bec2ec319fb74840fa2bb`. Success diagnostics
contain only page, page-result count, pagination flags, bounded region IDs and
safe release correlation fields; they never log the query, response body,
product names, URL, headers or App Check token. The physical trace now follows
the existing bounded Load More control and fails unless it observes at least
six cards and the expected `ar-b` and `ar-caba` regions. The exact merged-main
verification run `36308327069` passed: 405 shared-core tests, 1,694 Android
tests, browser verification, lint/build, privacy and single-signer checks.
No Motorola is attached (`adb devices -l` is empty), so M12.4 is still not
claimed as physically verified.

Historical branch: `work/valuepilot-m124-m129`

The current branch preserves the untracked `device-audit/` evidence. GitHub
Actions currently stores only the two durable provenance artifacts
(`10085413603`, `10000282677`), totalling 76,864,219 bytes, with zero active
caches. They remain preserved; no provenance or raw provider data was deleted.

## M12.4 live Search repair — 2026-09-24

The real Play-installed Motorola `10106` failure is isolated to the Android
contract boundary, not App Check, Play Integrity, the public edge, OIDC, the
active release or M11. Cloud Run logs show the device's `/v1/status` and
`/v1/search` requests returning HTTP 200. The valid discovery response carries
`diagnostics.regionsQueried` as a bounded JSON array, which the old Android
diagnostic parser rejected as `CONTRACT_INVALID`; the Search surface therefore
showed its generic temporary-failure message.

The focused repair branch is `fix/argentina-live-search-response-contract`.
It accepts only bounded scalar diagnostic arrays, preserves strict rejection of
nested objects/arrays/nulls, carries safe endpoint/status/request/release
metadata on failures, and logs only those fields in production. It adds no
price, stock, availability, ranking, provider or Android-networking authority.
The corrected local Play-upload artifact is Android `10107` / `101.1.6`, signed
with the existing protected upload certificate and not uploaded to Play yet.
The physical smoke remains **PARTIAL** because the attached phone still has
10106 and is PIN-locked; `tools/collect_live_search_10107_trace.ps1` is the
single bounded trace action after Play installs 10107 and the phone is
unlocked. See [ARGENTINA_LIVE_SEARCH_M12_4B.md](ARGENTINA_LIVE_SEARCH_M12_4B.md)
for the repair evidence.

Candidate branch HEAD before this status update:
`0435a29d39ebaf8b44437daa1071969c15c6efea`

M12.4 candidate code baseline before the current documentation-only follow-up:
`c739346344c77fd00cb49809c8cc529e2893b66b`

Latest M12.5 production integration on `main`:
`84584173e27f94ef7d6c1329e59f1183cb8da243`

## M12.5 immutable acquisition-state fix — 2026-09-24

The September 23 `CONTROL_STORE_CONFLICT` failures are now explained by
production evidence, not a guessed race. Run `35911475904` had already
archived the official source and successfully wrote
`acquisition/daily-state/2026-09-23.json` (generation `1790192790919843`,
`METADATA_DISCOVERED`). Later state transitions attempted to replace that
object. The GitHub OIDC identity on the archive bucket has
`roles/storage.objectCreator` and `roles/storage.objectViewer`; it can create
and read objects but cannot replace an existing object. The old CAS retry loop
therefore mislabeled a permanent create-only permission boundary as
`same-day acquisition state CAS did not converge`. Run `35931479929` failed at
the same deduplication boundary before opening egress. No IAM broadening is
authorized or needed.

The September 22 execution
`12d9c499-1508-44c3-81e4-e399e60cb3b8` is a separate HTTP 412 after the
multi-hour SHADOW build. Deployed Cloud Workflow revision `000011-094` already
reads back the state and accepts only an exact candidate/source/metadata/
promotion identity; mismatches return `STATE_WRITE_CONFLICT` with pointer
writes preserved at zero. The new fix does not weaken that boundary.

The isolated fix branch `fix/argentina-immutable-acquisition-state` is frozen
at `5ea105ab84c25d8784ad40a6f874ad97b4737f29` with PR
[topmanneedboys/morefoodperdollar#9](https://github.com/topmanneedboys/morefoodperdollar/pull/9).
It replaces same-day state replacement with a bounded append-only immutable
event journal under `acquisition/daily-state-events/<day>/`, keeps legacy v1
state readable, adds bounded immediate-prefix listing to all object-store
adapters, rechecks event size when a provider listing is partial, and retains
fail-closed status/identity conflict handling. It does not change Android,
active/canary pointers, IAM, raw data, or the deployed Cloud Workflow.

Exact PR checks for the current commit are green: acquisition-control run
`35952625920`, Android build runs `35952625984` and `35952628699`. The complete
checked-in tooling discovery on the fix branch passed 86 tests. No national
acquisition, Cloud Workflow deployment, pointer write, or Play upload was
triggered. M12.5 is
still **PARTIAL** until PR #9 is reviewed/merged and one normal scheduled OIDC
run proves the event-journal receipt, candidate/SHADOW result, and zero active/
canary pointer writes. M12.4 remains **PARTIAL** for the Motorola Edge 2025
Play-installed Search smoke.

## M12.4 / M12.5 checkpoint update — 2026-09-23

### Verification refresh — 2026-09-23 12:15 UTC

At this verification snapshot, candidate code HEAD was
`4103220b6b88253938af449dcbe156d8d4b0ce35`; the local candidate worktree was
clean and the remote candidate ref matched it. Exact-head GitHub run
`35856892970` completed successfully at 12:00:45 UTC: Android test/lint/build,
privacy and signer checks passed, and browser verification passed. Candidate
provenance and release-bundle jobs were skipped by their branch guards, so
this run did not upload an artifact or claim promotion.

The integrated `main` head remains
`84584173e27f94ef7d6c1329e59f1183cb8da243`; its exact-head acquisition-control
run `35842285077` and Android build/test run `35842285058` both succeeded.
The local complete `tools/tests` suite also passed all 300 tests after adding
the installed Git OpenSSL directory to the test process's temporary `PATH`;
no repository or machine configuration was changed. The focused acquisition,
handoff, schedule-control, daily-release and workflow subset passed 57 tests.

M12.4 remains **PARTIAL only for the physical Play-installed Motorola Edge
2025 smoke**. `adb devices -l` reported no attached device; no Play upload or
physical-device result is claimed. The M12.4 Search code remains frozen.

M12.5 remains **PARTIAL** pending the first scheduled end-to-end acquisition
after the retry/outage integration. At 12:06 UTC, GitHub showed no September
23 scheduled acquisition run; the next expected primary check is 16:30 UTC.
No national acquisition was manually triggered. The deployed Cloud Workflow
was still ACTIVE at revision `000011-094`. The last direct GCS pointer read
remains the 11:07 UTC check recorded below; this refresh did not reread the
pointer. Observe the scheduled run's exact source SHA, bounded retry outcome,
OIDC handoff, candidate/SHADOW state, and active/canary pointer writes before
qualifying the end-to-end path.

### Scheduled gate configuration check — 2026-09-23 12:32 UTC

At this snapshot, candidate HEAD was the status-only commit
`5b4f5988df3916a52c89b4cc1db809556b016e01`, matching the remote candidate
ref. Its exact-head CI run `35859461711` succeeded: Android and browser jobs
passed; candidate release/provenance jobs were skipped by branch guards.

GitHub currently has repository variable
`ARGENTINA_AUTOMATED_ACQUISITION_ENABLED=true` and an environment-scoped variable
of the same name set to `false`. The workflow's scheduled-job condition is
job-level and reads `vars.ARGENTINA_AUTOMATED_ACQUISITION_ENABLED`. GitHub
documents that environment-level variables become available after the
environment is declared by the runner; the Sep 22 scheduled runs nevertheless
started the acquisition job with this environment variable already set to
`false`. The environment value therefore is not acting as a schedule kill
switch for this job. No GitHub setting was changed. Do not interpret the
environment-level `false` as disabled acquisition; the repository-level
`true` is presently allowing scheduled work. See [GitHub's variable-scope
documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-variables).

At 12:29 UTC, the latest scheduled GitHub run was still Sep 22 run
`35795809367`; no Sep 23 acquisition had run. The next primary check remains
scheduled for 16:30 UTC. Read-only GCS checks at 12:28 UTC confirmed both
regional `control/active.json` objects still name the Sep 18 release as active
and last-known-good. Their generations remain
`1789973690114545` (`southamerica-east1`) and `1789973692306062` (`us-east4`),
last updated 2026-09-21; no pointer mutation occurred in the observed window.

### Exact-head and device refresh — 2026-09-23 12:50 UTC

Candidate HEAD `0435a29d39ebaf8b44437daa1071969c15c6efea` is clean and matches
the remote branch. Exact-head GitHub run `35861180731` completed successfully
at 12:41 UTC: browser verification and Android test/lint/build, privacy and
single-signer verification passed. Candidate release-bundle and provenance
jobs were skipped by their branch guards; this run did not claim promotion.

The signed 10104 AAB was already present in the earlier local worktree and was
independently checked without changing or copying it. `jarsigner` reports the
archive verified with no invalid signatures or unsigned entries; it has one
signature block and the same certificate fingerprint as 10103. Its SHA-256 is
`23633c008c21db9d6ca1a343d96c76f1dc1c5f06da19a32a3dd095476039d5bb`. The
strict trust check reports that this self-issued upload certificate is not
rooted in a public CA; that is a trust-chain warning, not a signature mismatch,
and Play acceptance has not been tested. PowerShell history confirms the
existing local flow uses a current-user protected credential and an
environment-based signer input; no password prompt or secret output occurred.
No bundle was uploaded to Play.

`adb devices -l` still reports no attached Android device, so M12.4 remains
**PARTIAL** only for the signed Play-installed Motorola Edge 2025 smoke. At
12:50 UTC, the latest scheduled acquisition run was still Sep 22 run
`35795809367`; the Sep 23 16:30 UTC primary run has not occurred and no
national acquisition was manually triggered. Read-only reads of both regional
`control/active.json` pointers still show the Sep 18 release as both active and
last-known-good, at the same generations recorded above. M12.5 remains
**PARTIAL** pending the scheduled post-integration OIDC acquisition/recovery
run; no active-pointer mutation occurred in this refresh.

### Candidate follow-up — Android Search HTTP 503 contract

Candidate code commit `12b42984f45ec9dd2141c676fd36b3b70d39dcbb` corrects the
Edge-to-Android contract: HTTP 503 now maps to generic `SERVICE_UNAVAILABLE`,
not the price-only `CURRENT_PRICE_UNAVAILABLE`, and Search presents the
existing temporary-unavailability state. HTTP 502 remains a generic server
error. Android version is now `101.1.5` / `10106`. Deterministic tests cover
the 503 mapping, generic Search error, and 502 distinction. Two Android source
boundary tests now use the existing newline-normalizing reader so their
assertions are stable on Windows.

The local Windows failure was traced to Git `core.autocrlf=true`: all 10
affected signed-catalog text files were byte-for-byte the committed Git blobs
after undoing only CRLF conversion; the other two files already matched. The
catalog output is cryptographically byte-sensitive, so
`.gitattributes` now marks `android/app/src/main/assets/offline_catalog/**`
`-text`. Only the attribute rule is a repository content change; the local
catalog files were restored to their exact tracked bytes. Both signed regional
catalogs then passed the strict signature/hash verifier. The complete tooling
suite passed all 300 tests.

Local Android validation passed on this code: shared-core tests, Android unit
tests, debug/release lint, debug/release assembly, and release bundle assembly
(`BUILD SUCCESSFUL`, 122 actionable tasks). The debug APK passed the workflow's
privacy check (`INTERNET` present, `ACCESS_NETWORK_STATE` absent) and its
single-signer verification. The debug APK and release AAB each contain all 12
offline-catalog files byte-for-byte identical to the verified source assets.
No Android device was attached. The M12.5-focused tooling suite on its
integration worktree passed all 80 tests. Exact-head GitHub CI for
`12b42984f45ec9dd2141c676fd36b3b70d39dcbb` passed in run `35848056265`:
Android job `107138891858` and browser job `107138892324` succeeded; milestone
provenance and release-bundle jobs were skipped by their candidate-branch
guards. Routine artifact uploads were also skipped.

The Windows signed-catalog byte-preservation follow-up at code commit
`c739346344c77fd00cb49809c8cc529e2893b66b` adds a narrow `-text` attribute
for signed offline-catalog assets and a regression test for that checkout
boundary. Exact-head run `35852776932` passed Android verification job
`107154117037` (tests, lint/build, privacy and single-signer checks) and browser
job `107154117308`; offline-catalog snapshot run `35852776966` also passed at
the same SHA. Candidate-only provenance and normal artifact upload jobs were
skipped by their guards. Subsequent changes in this status/architecture
follow-up are documentation-only and are not represented by those code-run
results.

A supplemental local run of the broader candidate `backend/tests` suite under
CPython 3.14 was not clean: this machine lacks the pinned FastAPI/Boto3 test
dependencies, and two backend-access-shape byte-count assertions observed
240160 rather than their 240159 baseline. The same backend test/source and
fixture-builder paths are unchanged from candidate SHA `35cc732`; its full
backend/container workflow passed with Python 3.12 and installed requirements
in run `35822853987`. The subsequent M12.5 acquisition-specific changes were
independently covered by their focused Python 3.12 CI runs recorded below.

M12.4 remains **PARTIAL**: candidate code, local Android checks, and exact-head
Android/browser CI are green, but the signed Play-installed Motorola Edge 2025
Search smoke remains outstanding. No phone was attached during this
checkpoint. The locally signed 10104, 10105, and 10106 bundles have not been
uploaded to Google Play. Earlier candidate HEAD `2176ed8` passed Android tests, lint,
builds, privacy checks and the single-signer check in run
`35828919495`; release-bundle/provenance jobs were skipped by their branch
guards. The preceding status/documentation branch HEAD
`575f0989fd5bcb89c5471e087f442a39206dac03` passed exact-head Android and
browser CI in run `35839211038`; release-bundle/provenance jobs were skipped
by the same branch guards.

10104 was signed with the same protected local Play-signing mechanism used for
10103. The current-user DPAPI credential was consumed locally without a
password prompt or secret output. Its signature verifies against 10103's signer
certificate fingerprint
(`A0:B6:5D:8A:5A:F9:44:9E:76:6F:05:39:6F:E1:78:20:D5:2F:26:7C:E9:D5:8A:B0:C2:F6:CD:E4:DC:B8:52:DA`).
The 10104 AAB SHA-256 is
`23633c008c21db9d6ca1a343d96c76f1dc1c5f06da19a32a3dd095476039d5bb`; it was
not uploaded to Google Play. The 10105 AAB was also verified locally with the
same signer (SHA-256
`c982a6ab8c46eeddee196289205d464f3827c85d8f4754d409fbcdf3af825f2e`); it too
has not been uploaded. Candidate 10106 was signed and independently verified
through the same protected local mechanism without a password prompt or secret
output. Its SHA-256 is
`d8189b3fa0b5e7da3bb506d5c1e7d23af7502f93a0ba3360eb540af9dc7243e6`; its
certificate fingerprint matches 10103/10104/10105. Physical-device M12.4
validation remains outstanding.

M12.5 is **PARTIAL — the production workflow definition is deployed and its
fail-closed validation gates pass live smoke tests, but the next scheduled
OIDC acquisition/recovery run has not yet qualified the end-to-end path**.
M12.5 changes were integrated through PRs #4–#8. `main` is at
`84584173e27f94ef7d6c1329e59f1183cb8da243`.

The deployed Google Workflow `valuepilot-argentina-release` in `us-east4` is
ACTIVE at revision `000011-094`, with source SHA-256
`b7d52f2876baeb88ae8766b88b239f89a7d2ece2a7915cc8e0481b2764258381`, matching
the merged `main` source. Its existing service account
`valuepilot-release-workflow@valuepilot-argentina.iam.gserviceaccount.com` and
`LOG_ERRORS_ONLY` call logging were preserved. The first deployment attempt was
rejected before revision creation because two expressions exceeded Google's
400-character limit; PR #5 split them into equivalent bounded checks and added
a regression test.

Two bounded live executions on revision `000011-094` returned the expected
`INVALID_HANDOFF` reasons: mismatched archive key/hash, and missing verified
metadata-change evidence. Both completed successfully before discovery or
Cloud Build, reporting `cloudBuildStarted=false` and `activePointerWrites=0`.
These prove deployment parsing and early fail-closed gates only; they are not
evidence of a scheduled OIDC download, outage retry, or full candidate build.
No national acquisition was manually triggered. As of 2026-09-23 11:46 UTC,
the next scheduled 13:30 ART / 16:30 UTC acquisition had not yet occurred.
The Sep 22 scheduled run `35795809367` exhausted official metadata discovery
on HTTP 502 and failed during preflight before VPN, download, or Cloud Build;
it ran pre-integration code at `9dd3f01`. PR #6 adds an offline CLI regression
proving three exhausted 502 attempts return a handled outage report and exit
successfully, while preserving any reserved same-day candidate and reporting
zero pointer writes, zero source substitution, and no promotion. The first
scheduled run using the merged retry/outage behavior has not yet occurred.

The active release remains
`argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2`, age 5 of the
seven-Argentina-calendar-day limit and fresh on Sep 23. At the last direct
pointer-generation read (11:07 UTC), both regional generations were
`1789973690114545` and `1789973692306062`, both naming Sep 18 as active and
last-known-good. The Sep 22 build and later fail-closed workflow checks all
report zero active/canary writes; no Sep 23 scheduled acquisition has begun by
11:46 UTC. No canary pointer exists in either runtime bucket.

Final M12.5 verification: 80 local tooling tests passed; the focused workflow
regression suite passed 14 tests and `git diff --check` passed. PR #7 added a
regression proving only an upstream outage with still-fresh, verified active
release may be treated as handled; unsafe freshness remains a hard failure,
and the build reservation remains gated on `SOURCE_READY`. PR #8 extends the
run summary for unsafe freshness failures with the active release/freshness,
candidate, reservation and write diagnostics while preserving the existing
fail-closed outcome. Exact-head GitHub CI
passed on PR #4 head `87d11bf8001558357585713016bd2f105314770d` (runs
`35833958111`, `35833958234`, `35833964264`) and PR #5 head
`d52b8946b1b17bdaee63e69a9f9573c7393e012b` (runs `35834966494`,
`35834995735`, `35834966488`); both included Android unit tests/debug APK
build and acquisition-control regressions. PR #6 head
`c36a79042072eec47a264792d1bb5b897b911267` passed acquisition-control run
`35838406711` and Android runs `35838406722` / `35838439751`; it was squash
merged at `dc84302d3d0049c5b7ac81216f1bbe762b68a705`. Exact merged-head runs
`35838679332` (acquisition-control) and `35838679219` (Android tests/debug
build) also passed. Cloud Workflows accepted the merged source, and both safe
live gate executions succeeded. Routine CI now
builds/verifies the debug APK but uploads it only for an explicit manual
dispatch (3-day retention), avoiding normal push/PR APK artifacts.

GitHub Actions storage audit: the account warning showed `0.47 GB / 0.50 GB`.
This repository has two unexpired `ValuePilot-v101-provenance-proof` artifacts
at 36.65 MiB each; they are preserved as release evidence. No Actions caches
were deleted. The repository currently has 12 recent Gradle caches totaling
687.65 MiB; these are rebuildable CI dependencies and remain well below the
separate 10 GB per-repository cache allowance, so they were retained to keep
builds fast. [GitHub's billing guidance](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
states that cache storage is separate from the pooled artifact/Packages
allowance shown in the alert. One already-expired, disposable `github-pages`
artifact about 0.01 MiB was removed. The account-wide billing endpoint requires
a GitHub authorization scope unavailable to this session, so the remaining
artifact/Packages usage cannot be attributed to other repositories; the
rejected APK upload did not report a reliable artifact size. The current
artifact inventory contains no APK/AAB or Rust `target` build artifacts.
The M12.4 candidate branch remains substantially divergent from `main`; no PR
exists for that whole branch. Only the narrow M12.5 integration PRs were
merged. Do not merge the entire M12.4 branch as a narrow milestone.

Android version: 101.1.5 (10106 M12.4 Search candidate)

## Current M12.4B visible Argentina identity Search candidate

M12.4 status: **PARTIAL — implementation and automated validation are ready;
physical Motorola Edge 2025 smoke is still not performed.** Search code remains
frozen while that device-only gate is pending.

M12.4B Search was introduced at
`4881750886a26de9e203e52d16963156719733cc` and stabilized for live response
shape, ordering and request behavior by
`5dac9978ea842acc9c1afcd4651d5c0a3c6a72e5`. The current candidate version is
`10106` / `101.1.5`; the original M12.4B scope and launch-area evidence are
recorded in
[ARGENTINA_LIVE_SEARCH_M12_4B.md](ARGENTINA_LIVE_SEARCH_M12_4B.md).
Search is now a visible, user-triggered identity-only surface backed by the
existing bounded M12.4A coordinator/bridge/transport. It uses the explicit
qualified launch area `Buenos Aires (CABA)` (25 km, source-labelled CABA
snapshot), has deterministic idle/loading/results/no-match/unavailable,
authorization, stale-release and timeout states, and hands a selected identity
to the existing Compare route without adding price, quantity, stock,
availability, promotion, delivery or ranking authority. MainActivity remains a
thin renderer/action boundary; the old sample Search path and M12.3 startup
probe are not in the visible path.

The backend image for this candidate was built in Cloud Build
`0f8e511d-6d24-4d4c-888a-296ccf2a5951` and is deployed at private M11
revision `valuepilot-argentina-sa-east1-00037-nuj` (digest
`sha256:5033e5bdd315c7954c8d1f4c0c6f7f9d034a6586819294beec31c3e4eada0eae`)
with the identity-search authorization gate enabled in configuration. The
active release is
`argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2`; on
2026-09-21 it is explicitly `FRESH`, and the backend reports
`androidNetworkingAuthorized=true`. `productionAndroidUiAuthorized` remains
false. This is therefore backend-ready and Play-build-ready but **not
device/live-search qualified** until the signed app passes the explicit
physical-device smoke. The existing protected Play upload key was found and
verified; the earlier 10103 / 101.1.2 AAB is signed locally but not uploaded:
`android/app/build/outputs/bundle/release/app-release-play-upload-signed-10103.aab`
(SHA-256
`b195d6a6318db823a61f27035cc2b8c66adda392c2040a4b87d71e17b4cd3679`). The
later 10104 bundle was signed through the same protected local mechanism and
its signature verified without manual password entry or secret disclosure; it
also has not been uploaded.

The active release was independently verified from the official archived source
SHA-256 `619498b8e3230298b8a91f3df05ee7fe44e190a8f909e0e103cf43867fec73ee`
(`335486139` bytes), mirrored to both runtime buckets, and promoted by CAS
pointer updates. Its candidate manifest SHA-256 is
`9a558cafcb5d5a162eb04330448612418f437eda7d1f5a3e5ba44e9202c415cf`; the
manifest contains 1,163 immutable SearchPack objects and no raw provider data
is committed. Authenticated `/readyz`, `/v1/status`, and a CABA `leche`
`/v1/search` smoke passed on the promoted revision; availability remains
`UNKNOWN` by design.

The public edge remains revision `valuepilot-argentina-consumer-edge-00004-5lg`
with its existing Firebase App Check boundary and private OIDC destination.
Missing/invalid App Check return 401, unknown routes return 404, and direct
unauthenticated M11 returns 403; an authenticated private status request still
returns the bounded status payload. Firebase project `valuepilot-argentina`
is active and the registered Android app remains `com.valuepilot.app` /
`ValuePilot Android`. See the canonical report for the exact cloud, build,
privacy and remaining human-action evidence.

## Current M12.5 Argentina acquisition reliability candidate

Status: **PARTIAL — the production workflow integration is deployed and its
fail-closed gates pass live smoke tests; end-to-end scheduled OIDC acquisition
and outage-recovery behavior still needs one scheduled run.** The bounded
implementation commit `68acf33` originated on this candidate branch; production
integration was merged separately to `main`, most recently at
`84584173e27f94ef7d6c1329e59f1183cb8da243` (PR #8).

The bounded implementation adds three-attempt metadata discovery with
deterministic 0.5s / 1.0s retry delays for transient HTTP 408/425/429/500/502/
503/504, timeouts and temporary connection failures; permanent/semantic errors
are not blindly retried. Exhaustion is represented as
`UPSTREAM_TEMPORARILY_UNAVAILABLE` while the trusted active release is within
the explicit seven-Argentina-calendar-day freshness limit; approaching the
limit is surfaced as an operator warning. An outage path reports zero active
and canary pointer writes, no source substitution and no promotion.

The daily controller explicitly uses `DISCOVERY_PLUS_RECOVERY`: each scheduled
phase checks authoritative metadata, while a later phase may perform one
bounded byte recheck after an unchanged-metadata primary result. Same-day source
revisions require metadata-change evidence bound to the discovery response and
archived source identity. Daily state reads bracket the payload with matching
object generations; CAS retries preserve newer state; shadow results are
monotonic so a late failure cannot downgrade a recorded success. Exact
idempotent Cloud Workflow state-write conflicts are distinguished from real
conflicts. SHADOW remains the only automatic build mode; no active/canary
pointer operation was added.

The Sep 22, 2026 scheduled acquisition was verified read-only from GitHub and
GCP evidence. Primary run `35776323661` (19:50Z, pre-integration GitHub SHA
`9dd3f01dd547fa0cfcff4f316835045fd670d649`) downloaded the official Precios
Claros `Martes` ZIP: 321,841,103 bytes, SHA-256
`eaefeae3d9f2885a9e95558d936955be4f36e7cd2e69e4d4e064e3d44463b20d`. The
official metadata description and `lastModified` still said 2026-06-30, but
the acquired ZIP's internal dated package paths proved 2026-09-22; the
candidate release date is based on that internal evidence, not the stale CKAN
hint. The exact source archive manifest records `rawProviderDataCommitted=false`
and `runtimePublicationAllowed=false`. The local Downloads
`sepa_martes.zip` is a different 2026-09-08 file (325,118,492 bytes) and was
not used for this Sep 22 verification.

The candidate identity is
`argentina-sepa-2026-09-22-eaefeae3d9f2885a-routing-v1-search-v2`, reserved as
`github-35776323661-1`. Later same-day run `35787122024` (21:31Z) rediscovered
the same metadata fingerprint and source hash, returned `SOURCE_READY` and
`ALREADY_RESERVED`, and did not start a duplicate Cloud Workflow while the
first build was still running. The single Cloud Workflow execution was
`12d9c499-1508-44c3-81e4-e399e60cb3b8`, on old revision `000010-bf5`; its
Cloud Build `4eba2181-f40f-4fda-8639-0b183d3624b7` completed `SUCCESS` at
2026-09-23 00:07:59Z. The worker summary was `CANDIDATE_VERIFIED`, and the
release manifest records `completionState=COMPLETE`,
`qualificationStatus=QUALIFIED`, source date/hash as above, and
`automatedOfficialAcquisitionProven=false`. The immutable candidate archive
receipt is `PERSISTED`: 1,163 objects, 889,425,844 object bytes (889,653,903
total bytes), 1,159 new immutable objects and 4 reused. The candidate manifest
sidecar SHA-256 `c99b519c034be83db961714d16b3cc214dc19bb7c6f34813697144c62e26de3e`
was independently checked against its full manifest bytes.

After the successful build, old workflow revision `000010-bf5` failed its
conditional workflow-discovery-state write with GCS HTTP 412
(`STATE_WRITE_FAILED`). This was state bookkeeping after candidate validation,
not a failed Cloud Build: promotion remained `SHADOW_ONLY`, activation was
`NOT_EXECUTED`, and active/canary pointer writes were both zero. The subsequent
pre-integration scheduled run `35795809367` (23:06Z) exhausted official
metadata discovery on HTTP 502 before VPN, download or Cloud Build. Current
revision `000011-094` was deployed at 2026-09-23 08:06Z with the exact-match
412 reconciliation: it rereads the state generation, returns
`ALREADY_RECORDED` only for the same verified candidate identity, and reports
unverifiable or different state explicitly. Its live source hash matches the
merged `main` workflow. The first scheduled acquisition using the merged retry
and recovery behavior is still pending; no national acquisition was manually
triggered.

The currently trusted release remains
`argentina-sepa-2026-09-18-619498b8e3230298-routing-v1-search-v2`, source SHA-256
`619498b8e3230298b8a91f3df05ee7fe44e190a8f909e0e103cf43867fec73ee`. It was
age 4 of the 7 allowed days on Sep 22, therefore `FRESH`; no date was extended
and no new source was promoted.

Production integration is now deployed. Google Workflow
`valuepilot-argentina-release` in `us-east4` is ACTIVE at revision
`000011-094`, updated Sep 23, with source SHA-256
`b7d52f2876baeb88ae8766b88b239f89a7d2ece2a7915cc8e0481b2764258381`, matching
the merged `main` source. The existing service account and `LOG_ERRORS_ONLY`
call logging were preserved. Exact-source CI passed, including the later
outage/freshness failure-summary regressions recorded in the checkpoint above;
the actual Cloud Workflows service accepted the source after two over-limit
expressions were split.

Live execution checks `fcde61a5-3160-4480-91fb-d42c4c1bed06` and
`5f80333a-7f20-41ff-adfc-66058905f404` both succeeded on revision `000011-094`
and returned the expected early `INVALID_HANDOFF` reason, `cloudBuildStarted`
false, and zero active-pointer writes. They exercised the mismatched archive
pair and missing same-day evidence gates, not source discovery, OIDC acquisition,
retry handling, or a full build. No manually forced national build was run;
the next scheduled acquisition is the remaining live qualification gate.

At the initial M12.5 candidate checkpoint, the acquisition tooling was included
in focused Python CI and the Android/release workflow path filters watched the
M12.5 files. Local verification at that checkpoint: 73 focused acquisition/workflow tests,
296 tooling tests, 110 backend tests, Python compilation, YAML parsing, and
`git diff --check` all passed. The required Android shared-core/app unit-test,
debug lint and debug build gate passed (most tasks were up-to-date); APK
inspection found the existing `INTERNET` and `CAMERA` permissions, no
`ACCESS_NETWORK_STATE`, and one debug signer. GitHub checks passed on exact code
HEAD `35cc732a2671bc3d0cb771a90f9af77d864e6bb7`:

- `35822854005` Android shared-core/app tests, debug lint/build, privacy and
  single-signer checks; browser verification passed. The candidate branch's
  release-bundle/provenance jobs were skipped by their branch guards.
- `35822854045` acquisition-control regressions.
- `35822853987` production backend native tests, Python regressions, container
  builds and health-boundary smoke.
- `35822854003` merchant-feed qualification and regional tests.

PR #7 head `4fae5147d5ebfc22072dc5bc71e60c8fc5ff5ec8` passed exact-head
acquisition-control run `35840981247` and Android runs `35840981299` /
`35840998252`, then merged as `72c40d8b55c857626ed421395ab792108b24c076`.
PR #8 head `8fb99af345ca0b554975e85eb75bef9c53aef55d` passed exact-head
acquisition-control run `35842021415` and Android runs `35842021352` /
`35842038777`, then merged as `84584173e27f94ef7d6c1329e59f1183cb8da243`.
The exact merged-main acquisition-control and Android test/debug-build runs
`35842285077` and `35842285058` both passed. No APK artifact was uploaded by
the ordinary push run.

All four workflow runs concluded successfully for that earlier checkpoint; the
later merged M12.5 integration is verified by the exact-head runs above.

## Current M12.4A live Search foundation candidate

The bounded M12.4A candidate is on branch
`candidate/argentina-live-search-m12-4`, starting from the qualified M12.3
head `93f25a072881cb25db6bfc38517d4efc87523af9`. It adds an un-wired,
identity-only `ArgentinaLiveSearchCoordinator` around the existing bridge and
transport: explicit geography is required, rolling release identity is learned
from a fresh `/v1/status` handshake and pinned into `/v1/search`, stale or
superseded work is cancelled/rejected, and the executor has only one worker and
one pending latest-generation slot. No Search UI, Android bundle upload,
prices, package quantities, stock, availability, ranking, or provider
economics were added. The coordinator also fails closed before `/v1/search`
unless the status handshake explicitly sets `androidNetworkingAuthorized`.
M12.4B remains out of scope.

The public edge timeout budget is hardened to a 12-second private-upstream
bound, Android 15-second read/20-second total bounds, and a 20-second Cloud Run
service deadline. The existing service now runs revision
`valuepilot-argentina-consumer-edge-00004-5lg` with image digest
`sha256:315e37021aa7b1adf2f77eba88bc85f09984025ebac98e89612ff40168238581`;
its Firebase App Check allow-list, OIDC destination, runtime identity and
scale-to-zero/max-20 policy are preserved. Post-deploy checks remain 401 for
missing/invalid App Check, 404 for an unknown route, and 200 through the real
Play-installed M12.3 probe. The previous 8.578-second cold 504 is retained as
the reference; current `/v1/search` timing is explicitly not remeasured because
visible Search and a new Android artifact are not part of M12.4A. See
[ARGENTINA_LIVE_SEARCH_FOUNDATION_M12_4A.md](ARGENTINA_LIVE_SEARCH_FOUNDATION_M12_4A.md).

## Current M12.2 public consumer-edge checkpoint

The M12.2 server-side bridge is qualified for the deployed edge/OIDC test
boundary at code SHA `842f4b6b3825b98ea8599424206b3451561e6a83` (`Fail closed
on partial M11 edge configuration`). Cloud Run service
`valuepilot-argentina-consumer-edge` in `southamerica-east1` serves immutable
image digest `sha256:274280c381b5c052a661b7606f1219ac71e29556669b36bee292d473b3290a7b`
at revision `valuepilot-argentina-consumer-edge-00003-8fq`, with
scale-to-zero/max-one settings and the dedicated runtime identity
`vp-arg-consumer-edge@valuepilot-argentina.iam.gserviceaccount.com`. That
identity has exactly `roles/run.invoker` on the private
`valuepilot-argentina-sa-east1` M11 service; M11 remains directly
unauthenticated `403` and the US M11 service has no binding.

The edge is public through Cloud Run's invoker-IAM-check disablement because
the project organization policy rejects an `allUsers` member. App Check is
still required inside the edge: HTTPS requests without or with an invalid
token return `401`, and unknown routes return `404`. Firebase project
`valuepilot-argentina` is active, with Android app `ValuePilot Android`
(`com.valuepilot.app`, App ID
`1:17260882200:android:446eec76e5f1a175f2566d`) and the current debug signer
SHA-256 registered. A temporary debug App Check token was exchanged and
revoked; the valid-token edge `/v1/status` smoke returned the private M11
payload with `200`, and private M11 logs show authenticated requests. The
edge's ADC/OIDC adapter, fixed route allow-list, strict bounds and
partial-configuration fail-closed behavior are covered by 40 local backend
tests and exact-head backend workflow **35062107407**. Cloud Build image build
**232e7ce4-d53e-4a26-9b83-dd05495fc486** and the deployed revision are ready.

The canonical record is
[ARGENTINA_CONSUMER_EDGE_M12_2_QUALIFICATION.md](ARGENTINA_CONSUMER_EDGE_M12_2_QUALIFICATION.md).
The exact-head Android workflow **35062107472** also passed Android
verification (**104684419403**), browser verification (**104684419537**) and
release-bundle assembly (**104686868032**). Real-device Play Integrity and
production Android networking remained intentionally unqualified at this
M12.2 checkpoint; the separate M12.3 foundation is recorded below.

## Current M12.3 qualified secure Android networking release

M12.3 is implemented on this candidate as a narrow Android edge-transport
foundation, and the qualified Play-installed release is version `10102`
(`101.1.1`). Its release-only qualification probe makes exactly one bounded
`/v1/status` request per process and logs only a safe status or typed failure;
debug builds cannot enable it. The authoritative Firebase Android configuration is checked in
for `valuepilot-argentina` / `com.valuepilot.app`; Application startup installs
Play Integrity in release builds and the Firebase debug provider only in
debug/test builds. A bounded `HttpsURLConnection` adapter can call only the
public `valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app` service,
obtains a cached-or-refreshed Firebase App Check token, and sends it only in
`X-Firebase-AppCheck`. It has explicit request/response, connect/read/total
timeout, cancellation, strict UTF-8, redirect, route, and typed HTTP/network
error gates. No private M11 URL, credentials, token persistence/logging,
cleartext fallback, background sync, or live Home/Search wiring was added.

The manifest intentionally adds `INTERNET` for this future user-triggered
edge route while continuing to remove `ACCESS_NETWORK_STATE`; release and
debug provider selection is source-set isolated. Deterministic JVM tests cover
headers, limits, timeout/cancellation, host/route allow-lists, typed status
mapping, Firebase config provenance, and APK-boundary declarations. The
Firebase project is active, Play Integrity is linked to it, and the qualified
Play Internal release is `10102 / 1.1.1`; Firebase App Check is registered
with Play Integrity (PLAY_RECOGNIZED and LICENSED required; one-hour TTL).
The 10102 / 1.1.1 build is now the qualified Internal-testing
release, installed by `com.android.vending` on a Motorola Edge 2025. Real Play
Integrity token acquisition succeeded, and the immediate physical-device
probe logged `M12_3_STATUS=200` through the public edge to private M11. The
first attempt also recorded a bounded 504 cold-start timeout while private M11
returned 200; this is a known M12.4 reliability requirement, not a security
failure. See
[ARGENTINA_ANDROID_SECURE_TRANSPORT_M12_3_QUALIFICATION.md](ARGENTINA_ANDROID_SECURE_TRANSPORT_M12_3_QUALIFICATION.md).

## Current M12.0 candidate checkpoint

The current offline Argentina consumer-contract bridge candidate is
`48b1d747125d656b3cf0ec77603820c1390e7aae` (`Fix Android SDK setup package
list`). M12.0 adds a bounded, provider-neutral fake transport seam and strict
parsers for the already-qualified M11 `/v1/status`, `/v1/search`,
`/v1/interpret`, `/v1/shop-text`, and `/v1/shop` contracts. It maps only
validated identity/input/decision evidence into the existing demo-free Home
state; it does not rerun the shared planner, add Android networking, bundle
SEPA data, claim inventory, or expose a two-store result as one store. The
qualification report is
[ARGENTINA_CONSUMER_CONTRACT_BRIDGE_OFFLINE_QUALIFICATION.md](ARGENTINA_CONSUMER_CONTRACT_BRIDGE_OFFLINE_QUALIFICATION.md).

Local verification passed 405 shared-core tests, 1,630 Android unit tests, 11
backend tests, 197 tooling tests, and 30 browser tests with zero failures or
skips, plus Android lint, debug/release assembly, APK privacy and one-signer
checks. Exact-head GitHub Actions run [35053280023](https://github.com/topmanneedboys/morefoodperdollar/actions/runs/35053280023)
passed Android job 104658056547, browser job 104658056705, and release-bundle
job 104659354704. The production milestone branch remains unchanged; this is
an offline contract-only candidate checkpoint.

The free-first Actions storage audit removed 50,016,944,050 bytes of
disposable artifacts and deleted all 12 rebuildable caches. The final listing
after the candidate run contains eight live artifacts totaling 230,725,526
bytes; preserved release/provenance evidence remains identified in
[GITHUB_ACTIONS_STORAGE_POLICY.md](GITHUB_ACTIONS_STORAGE_POLICY.md). Candidate
release bundles expire after three days, browser/Android hand-off parts after
one day, and no Rust `target` directory or national provider data is uploaded.

## Current M12.1 consumer-edge boundary candidate

The current M12.1 implementation candidate is code SHA
`5e829251cc43653e42a8583c2acd2cb2e9c18fb6` (`Harden consumer edge
content-length handling`), building on `d2d3d2c27e10114460bc46b932e2b4f174a32e8e`.
It adds a separate Python/FastAPI edge package with
the five consumer routes only, HTTPS/proxy-scheme enforcement, Firebase App
Check verification, strict method/content/query/body/JSON/response bounds,
bounded concurrency and rate limiting, safe correlation/security headers,
sanitised upstream failures, and an injected upstream seam. The default
composition has no M11 upstream and fails closed; M12.2 private invocation,
IAM/OIDC, and production deployment are not claimed.

The edge contains no shopping, ranking, provider, release, acquisition,
Android, or credential authority. Android remains offline with no `INTERNET`
or `ACCESS_NETWORK_STATE` permission. Deterministic local coverage is 17 edge
tests plus the existing 11 backend tests; the existing 197 tooling tests also
pass. The qualification record is
[ARGENTINA_CONSUMER_EDGE_M12_1_QUALIFICATION.md](ARGENTINA_CONSUMER_EDGE_M12_1_QUALIFICATION.md).
The exact-head backend workflow **35058092106** passed, including both
container builds. Exact-head Android workflow **35058092151** also passed its
browser verification (**104672396676**), Android test/lint/build/privacy/
signing verification (**104672396323**), and release-bundle assembly
(**104674454452**). The candidate remains edge-only; M12.2 private invocation
and production deployment are not qualified.

## Argentina backend qualification report checkpoint

The existing Argentina Milestone 5 winner remains **128 logical partitions /
32 physical packs** for mobile/offline distribution. Its completed source-wide
equivalence, all-alternative verification and payload-gate evidence were
reused; no national ZIP verifier or immutable-root rebuild was repeated merely
for diagnostics. The Milestone 7 backend report now records the bounded
per-request measurements, the explicitly `NOT_REMEASURED` dense cases, and
the exact green CI evidence at code SHA
`72b75d14306a880de9077aea4684b493a9eef4be`. The backend's separate 1024/32
server profile remains unchanged, Android remains offline with no provider
artifacts or network permissions, and no new consumer milestone is authorized
by this documentation checkpoint.

## Current verified engineering head

The latest promoted consumer slice is code SHA `3ed5f7105f2c7d008822202824c3d100289e8a93` (`Clarify bounded barcode handoff capacity`). Candidate workflow **34070119262** passed for the exact SHA: Android job **101585784823** (405 shared-core tests, 1,619 Android tests, test/lint/build, privacy and single-signer), browser job **101585784565** (30 tests and Firefox package checks), and release-bundle job **101586571519**; candidate release artifact digest is `sha256:3019b8be1e71af82c314bea69dba90fddbaed254fa8315f1de67622585a8710e`. Milestone workflow **34070476754** and provenance job **101586761130** verified the exact promoted SHA, candidate lineage and bundle checksums (provenance-copy digest `sha256:db4f41e5fb7959695669a1b302fe8234d811989645d43d5a1094653f9089353f`). Barcode identity handoff copy now describes the bounded behavior: it fills the next available comparison entry or appends while capacity remains; a full comparison explains the 32-entry limit. Deterministic tests cover the final available slot and no-overwrite behavior. No planner, evidence, product, offer, store, availability, private-memory, persistence, sample/demo or networking authority changed. Local verification passed 405 shared-core tests, 1,619 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint/build, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device and lawful provider activation remain open.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `98d2a16897a08ed42e431ffc1627a8090a3e8fac` (`Reduce barcode and shared-text handoff friction`). Candidate workflow **34068374176** passed for the exact SHA: Android job **101581077889** (405 shared-core tests, 1,617 Android tests, test/lint/build, privacy and single-signer), browser job **101581077789** (30 tests and Firefox package checks), and release-bundle job **101581853530**; candidate release artifact digest is `sha256:0cf7ffd301cbac6df8085b4b4357112aa1c3ad1515671a990ddbbd3498d64499`. Milestone workflow **34068693057** and provenance job **101581946788** verified the exact promoted SHA, candidate lineage and bundle checksums (provenance-copy digest `sha256:9a9aa6020a4ae655f21e4857d27b1953dce794d6f2f4e3b7052d30e699b149e8`). In Compare Here, explicit barcode identity and intentionally shared text now fill the earliest blank slot or append one next bounded slot when capacity remains; at the maximum they fail closed without replacing existing entries. These handoffs remain identity/raw-text-only and do not infer package, quantity, price, store, availability, evidence or ranking facts; the shopper still reviews and completes the entry. The change aligns these paths with the existing bounded OCR append behavior and changes no planner output, sample/demo meaning, product identity, offers, stores, availability, evidence, ranking, private-memory, persistence or networking. Local verification passed 405 shared-core tests, 1,617 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus Android lint/debug+release assembly, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device handoff/OCR/accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `aea8f4b525262ba56e2242c6d8731d4a0c079e42` (`Show private price movement on Home`). Candidate workflow **34066928777** passed for the exact SHA: Android job **101577217659** (405 shared-core tests, 1,615 Android tests, test/lint/build, privacy and single-signer), browser job **101577217709** (30 tests and Firefox package checks), and release-bundle job **101577859420**; the candidate release artifact digest is `sha256:2084af061991667f8153c674a78fcec7d7c31eebcd37b537099c13661995e08c`. Milestone workflow **34067224897** and provenance job **101578003778** verified the exact promoted SHA, candidate lineage and bundle checksums (provenance-copy digest `sha256:db4b5fc1935a1342cc733b33f238c3c95ecba16778ae03ae407db643d05be234`). Home now adds a deterministic `Since previous` movement line to each private exact-package history highlight when at least two comparable observations exist: Down, Up or Same; one-observation and non-comparable histories do not imply a movement. This reuses the existing private-history projector/grouping and changes no planner output, sample/demo meaning, product identity, offers, stores, availability, evidence, ranking or networking. Local verification passed 405 shared-core tests, 1,615 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus Android lint/debug+release assembly, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device history/accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `05f5728fee413df4e4c39f3e7689be16baa24370` (`Show private price memory highlights on Home`). Candidate workflow **34065396943** passed for the exact SHA: Android job **101573144487** (405 shared-core tests, 1,614 Android tests, test/lint/build, privacy and single-signer), browser job **101573144374** (30 tests and Firefox package checks), and release-bundle job **101573719671**; the candidate release artifact digest is `sha256:77d32d5c4b7fed43535b0d0ea241718d8c060bf74a2777c4e4c388d49f0e0932`. Milestone workflow **34065762760** and provenance job **101574117585** verified the exact promoted SHA and candidate lineage (provenance-copy digest `sha256:8f9f849e1a50aafda0b54373410002fcceea3921e92411b4a27de6d365c4840f`). Home now exposes a bounded, deterministic preview of the shopper's private exact-package history with last price, lowest observed price, unit-rate range, observation count and UTC date, while clearly labeling it personal historical context rather than live store pricing; unreadable history remains hidden and the existing review/export/forget routes remain available. The preview reuses the existing private-history projector and does not change planner output, sample/demo meaning, product identity, offers, stores, availability, evidence, ranking or networking. Local verification passed 405 shared-core tests, 1,614 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus Android lint/debug+release assembly, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device history/accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The current promoted consumer slice is code SHA `7131f5d88c47344e43240151d9298a9b43d253e4` (`Explain multi-image share boundary`). Candidate workflow **34063749025** passed for the exact SHA: Android job **101568792509** (405 shared-core tests, 1,612 Android tests, test/lint/build, privacy and single-signer), browser job **101568792650** (30 tests and Firefox package checks), and release-bundle job **101569513004**; the candidate release artifact digest is `sha256:7dab58da05ec2e7a5bdc243e98b729ab6b8003243e91bb3273ee2f4908147c4b`. Milestone workflow **34064071025** and provenance job **101569636233** verified the exact promoted SHA and candidate lineage (provenance-copy digest `sha256:2d99922178e5873e438033ce38445ba268cd7187b8a3d45c55e42c9f4e74f0bc`). Share-to-ValuePilot accepts one bounded image through `EXTRA_STREAM` or single-item `ClipData`; a multi-image share now explains that the shopper must choose one image instead of looking empty or silently dropping pages. No parsing, OCR, evidence, persistence, ranking or network authority changed. Local verification passed 405 shared-core tests, 1,612 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus Android lint/debug+release assembly, APK privacy/single-signer, signed 30,000-record catalogs and signed 6,093-location store checks. Physical-device share/OCR, barcode/camera, accessibility/latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior checkpoint

The latest promoted consumer slice is code SHA `7a119e60aa7187eb21389c77f7e9db9ebea52fc2` (`Accept ClipData image shares`). Candidate workflow **34062492992** passed for the exact SHA: Android job **101565374797** (405 shared-core tests, 1,606 Android tests, test/lint/build, privacy and single-signer), browser job **101565374948** (30 tests and Firefox package checks), and release-bundle job **101566120560**. The candidate release artifact digest is `sha256:245c070efa1b82541db99ce690caddcd687869f19e8234dc1fb0fe08068f9eb2`. Milestone workflow **34062799732** and provenance job **101566229176** verified the promoted milestone ref and exact candidate artifact lineage (provenance-copy artifact digest `sha256:115998f286d75e6792882c90f53a125ee75ffb28e75b40f932a20e87395aaf0d`). Share-to-ValuePilot now accepts a single image supplied either through `EXTRA_STREAM` or a single-item `ClipData` URI; multi-item shares fail closed instead of silently dropping pages. The accepted URI remains bounded and content-only, is handed to the existing local Compare Here photo/OCR review path with temporary read permission, and carries no bytes, parsing, evidence, persistence or ranking authority. Image OCR remains ordinary editable, unconfirmed suggestions with existing exact package, currency, price, promotion, like-for-like, evidence, private-memory and planner gates; text sharing and the prior transient OCR lifecycle protection remain unchanged. This promoted slice passed local verification (405 shared-core tests, 1,606 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips), Android lint/debug+release assembly, APK privacy and single-signer checks, and signed 30,000-record GTA/Metro Vancouver catalogs plus the signed 6,093-location store directory. Physical-device share/OCR, barcode/camera, accessibility and latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The latest promoted Scan & Compare/Good Price consumer slice is `32184ca0782dc7f82ac7583364edee6660b79236` (`Streamline unambiguous barcode identity handoff`). Candidate workflow **34047828847** passed for the exact SHA: Android job **101525964170** (402 shared-core tests, 1,591 Android tests, lint/build, privacy and single-signer), browser job **101525964196** (30 tests and Firefox package checks), and release-bundle job **101526604524**. Milestone workflow **34048136063** and provenance job **101526778010** verified the promoted ref and exact candidate artifact lineage. With an empty Compare Here slot or blank Good Price field, one exact offline barcode identity now flows directly into the existing editable name field without a redundant choice dialog; quantity, currency, price, promotion and evidence remain manual and unconfirmed. Multiple matches and replacement/data-loss cases retain explicit confirmation. This presentation-only handoff preserves exact money, quantity, evidence, private-memory, evaluator, planner, ranking, persistence, networking, account, tracking, demo-data and provider-economics boundaries. Local verification also passed 80 Python/catalog tests and 30 browser tests; signed 30,000-record GTA/Metro Vancouver catalogs and the signed 6,093-location store directory verified. Physical-device barcode/camera, accessibility and latency review and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The latest promoted Good Price trust slice is `7229879d9fe1df9bec6d4b2d5dd25e360c8242f2` (`Clarify Good Price history confidence`). Candidate workflow **34046055514** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101521994885**; milestone workflow **34046391345** and provenance job **101522085801** verified the promoted ref and exact candidate artifact lineage. When no exact personal history matches a checked package, Good Price now says that plainly and its disclosure says the answer uses this exact entered price only; it no longer implies that history exists or promises a save before storage success is known. Matching-history disclosures remain unchanged. This wording-only correction preserves exact money, quantity, promotion, private-memory, evaluator, planner, ranking, persistence, networking, account, tracking, demo-data and provider-economics boundaries. Local verification passed 402 shared-core tests, 1,589 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed offline catalogs/store directory and the candidate release bundle passed. Physical-device Good Price/Scan & Compare review, accessibility and latency behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The latest promoted Scan & Compare reliability slice is `a441190602a2968033c85c9d0d324294b2394499` (`Clarify OCR cancellation completion state`). Candidate workflow **34044501200** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101517762235**; milestone workflow **34044815415** and provenance job **101517879027** verified the promoted ref and exact candidate artifact lineage. When a shopper cancels or edits the comparison draft while ML Kit is still finishing, the photo status now says `Stopping photo reading… No suggestions will be added.` until the matching worker callback releases the lifecycle gate, then announces the final cancellation or draft-change result. Request-keyed stop state prevents stale callbacks from clearing a different stop reason. Pure stop-policy tests cover active-worker tracking, matching completion and stale completion; the change remains lifecycle/presentation-only and carries no OCR text, product, package, price, currency, evidence, comparison, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,586 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build/privacy/single-signer, signed offline catalogs/store directory and the candidate release bundle passed. Physical-device photo/OCR review, accessibility and latency behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

### Superseded prior implementation head

The operational issue register in `KNOWN_ISSUES.md` was refreshed at `ce8f6a5d868a1ca9cb64a6da80462ccf9887da5b`: stale local-toolchain blockers are marked historical, while the Motorola Edge 2025 device check and lawful current-offer provider rights remain open.

The authoritative promoted code head is `57d753ea9c33041c5097e04d591e321fb09660a0` (`Bound concurrent Compare Here OCR reads`). Candidate workflow **34041531253** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101509888609**; milestone workflow **34041928579** and provenance job **101510143113** verified the promoted ref and exact candidate artifact lineage. Scan & Compare now keeps one asynchronous ML Kit OCR worker active at a time: cancelling a request invalidates its result immediately, but capture/retry controls remain closed until that worker's callback releases the slot, so a slow cancelled read cannot run alongside a new read. Stale callbacks cannot release a newer worker, and teardown still rejects late results. This lifecycle-only gate carries no OCR text, product, package, price, currency, evidence or comparison authority; all existing editable/unconfirmed OCR, exact money/quantity, promotion, like-for-like and evidence gates remain authoritative. Home, Basket, plan-result/share and Watch store-status boundaries remain unchanged; no planner, projector, parser, product, offer, store, availability, private-memory, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,586 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release assembly, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed 30,000-record GTA/Metro Vancouver catalogs and signed 6,093-location store directory checks passed. Physical-device OCR review/accessibility behavior and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

## Earlier promoted checkpoints

The entries below retain their original wording for historical audit; the authoritative current head is recorded above.

The latest promoted consumer-trust slice is `f4e3b2921741b37c4bceb877ac7508f6b7eba3ae` (`Streamline Compare Here keyboard flow`). Candidate workflow **34013976518** passed for the exact SHA, including Android privacy/single-signer checks and release-bundle job **101435006274**; milestone workflow **34014203936** and provenance job **101435073885** verified the promoted ref and exact candidate artifact lineage. Compare Here’s existing multiline product editors now accept the keyboard’s Done/Next action as a navigation shortcut: it focuses the next product box, and on the final box it invokes the same already-enabled Compare exact value action. The handoff never parses, confirms, ranks or creates evidence; it only uses the existing visible button gate, while all exact route and privacy boundaries remain unchanged. Local verification passed 402 shared-core tests, 1,578 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release assembly, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed 30,000-record GTA/Metro Vancouver catalogs and signed 6,093-location store directory checks passed. Physical-device keyboard behavior/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted Home consumer slice is `e00d0fc39b3814d3f7a1c14d3a8497cf8ad1926` (`Expose observed-price action for missing line prices`). Candidate workflow **34007700546** passed for the exact SHA, and milestone provenance workflow **34007925720** (provenance job **101418438043**) verified the promoted ref and exact release artifact lineage. Home now offers the existing private `Add observed price` handoff not only when an item has no usable store price, but also when a covered item is included in the projected basket total without an optional line-item price breakdown. The action remains explicitly private and demo-safe; it never changes the projected sample plan, adds a live offer or availability claim, or moves any planner, product, store, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority into the renderer. Local verification passed 402 shared-core tests, 1,576 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release assembly, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed 30,000-record GTA/Metro Vancouver catalog and 6,093-location store checks, and candidate release bundle job **101418380749** passed. Physical-device confirmation of this handoff and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted Home consumer slice is `bd6eb3dc8dcb94ebf52e503c0bbd541f31964532` (`Surface Home first-value actions earlier`). Candidate workflow **34006273070** passed for the exact SHA, and milestone provenance workflow **34006546562** (provenance job **101414694656**) verified the promoted ref and exact release artifact lineage. Home now surfaces a `Quick answers` heading with the existing `Scan & compare prices` and `Is this a good price?` owner-driven actions directly beneath `Plan my shop`, before the longer sample/list/result content, so first-session value is discoverable without scrolling. The buttons remain navigation-only and fail closed when their owner callbacks are absent; no planner, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,570 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release assembly, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed 30,000-record GTA/Metro Vancouver catalog and 6,093-location store checks, and candidate release bundle job **101414624536** passed. Physical-device first-launch action discovery, scroll/keyboard/accessibility and camera/OCR ergonomics plus lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted Home consumer slice is `8acec4041223d3fa82ff79e17dc934aee36cd77b` (`Add Home quick-add sample shortcuts`). Candidate workflow **34004675643** passed for the exact SHA, and milestone provenance workflow **34004938861** verified the promoted ref and exact release artifact lineage. When the clearly labelled fictional offline Home demo is idle with a blank list, it now offers bounded `Try a sample item` chips for Eggs, Milk, Bananas, Bread and Chicken. Tapping a chip appends only that text token through the existing Home query reducer; repeated tokens are idempotent, over-limit additions fail closed, and the row disappears once a query exists. This is text-entry convenience only: it does not resolve identities, assert prices, stores, availability or evidence, invoke planner/ranking authority, persist data or add networking. Local verification passed 402 shared-core tests, 1,569 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release assembly, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed 30,000-record GTA/Metro Vancouver catalog and 6,093-location store checks, and the candidate release bundle passed. Physical-device quick-add/scroll/keyboard/accessibility and camera/OCR ergonomics plus lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `67e9a82709e60b1b48a05b7963f728ed51230714` (`Recognize bare decimal shelf prices in OCR review`). Candidate workflow **34003231756** passed for the exact SHA, and milestone provenance workflow **34003504444** verified the promoted ref and exact release artifact lineage. User-triggered Scan & Compare photo OCR now keeps conservative two-decimal shelf-price text such as `4.99` in the bounded review list even when a label omits a currency symbol; common package-unit decimals such as `1.75 L` are excluded. The signal is explicitly unconfirmed, carries no currency and never pre-fills an exact observation: the shopper must still review/edit and supply exact package quantity, currency and price through the existing route. This recall-only presentation correction adds no parser, money/quantity, evidence, product, offer, store, availability, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,569 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint, debug/release build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), single-signer, signed offline catalog/store checks and the candidate release bundle passed. Physical-device keyboard/camera/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted tooling slice is `083f93ad7ded1769679f1a215e2fbd5232d86124` (`Allow offline verifier shared source roots`). Candidate workflow **34001773717** passed for the exact SHA, and milestone provenance workflow **34002077934** verified the promoted ref and exact release artifact lineage. The offline-catalog verifier CLI now accepts an explicit `--source-root`, so the two signed regional manifests can be checked directly against their intentionally shared source payload without copying or weakening it; standalone snapshots retain the original colocated-source default. The change is tooling/test-only and adds no Android/runtime, product, offer, store, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,563 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips; Android lint/build, APK privacy, single-signer, signed 30,000-record identity snapshots and signed 6,093-record store directory checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `f755d6db916ecab5e9bf920a5ba41007b0a23bcc` (`Reuse offline catalog for barcode identity lookups`). Candidate workflow **34000381235** passed for the exact SHA, and milestone provenance workflow **34000674093** verified the promoted ref and exact release artifact lineage. Comparison and Good Price barcode identity lookups now reuse one Activity-scoped bounded merged index after the first signed GTA/Metro Vancouver catalog load. Every reuse rechecks caller-supplied freshness and rollback limits, and Activity teardown closes the session; the existing request-id and lifecycle guards still reject late callbacks. The cache contains only the bounded identity index and generation metadata: barcode results remain untrusted identity suggestions and never become package quantity, currency, price, store, stock, availability, evidence or ranking facts. No planner/projector, product, offer, store, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,563 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory; candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `9f142bc2c2c692a71605182eb2efe2f8ad495187` (`Cover thirty-thousand catalog cache bound`). Candidate workflow **33999251567** passed for the exact SHA, and milestone provenance workflow **33999546339** verified the promoted ref and exact release artifact lineage. Home’s unresolved-item identity lookup and Search’s explicit identity lookup now share one Activity-scoped, bounded merged index after the first signed two-metro load. Reuse rechecks the caller-supplied freshness and rollback boundaries; expiry, future time, rollback or Activity teardown clears/rejects the cache. No raw catalog text, query results, price, stock, availability or ranking facts are cached, and the existing identity-only admission/region fail-closed loader remains the authority. Local verification passed 402 shared-core tests, 1,563 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline verification passed for both 30,000-record identity snapshots and the signed 6,093-record GTA/GVA store directory; candidate Android lint/build, privacy, one-signer and release-bundle checks passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `1961a15f2a635a9705754158340482c6245d1c64` (`Clarify insufficient Good Price evidence`). Candidate workflow **33997214915** passed for the exact SHA, and milestone provenance workflow **33997549479** verified the promoted ref and exact release artifact lineage. Good Price’s no-matching-history state now answers in plain shopper language: `Not enough evidence yet to know whether this is unusually good.` Its guidance only says `ValuePilot will remember this exact package on this device.` The existing exact evaluator, private-memory capture/replay and neutral tone are unchanged; this is wording-only and adds no product, offer, store, availability, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,558 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline catalog verification passed for 30,000 GTA identities, 30,000 Metro Vancouver identities and the 6,093-location GTA/GVA store directory. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks also passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `16a14798ed2ffd2b4471aa13381267b1cab76d77` (`Associate Compare Here editors with labels`). Candidate workflow **33995987426** passed for the exact SHA, and milestone provenance workflow **33996295982** verified the promoted ref and exact release artifact lineage. Dynamically-created Compare Here editors now receive runtime-generated view IDs and their visible `Product N` labels are associated through `labelFor`, so TalkBack can announce the editor context instead of only a generic text field. The IDs are scoped to the current view instance and do not persist or expose product identity. This is a presentation/accessibility-only correction: no parser, money, quantity, promotion, evidence, private-memory, planner, projector, product, offer, store, availability, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,558 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline catalog verification passed for 30,000 GTA identities, 30,000 Metro Vancouver identities and the 6,093-location GTA/GVA store directory. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks also passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `571a53ac8bbb4ac1f99fa57bd77a5c6e3cd18568` (`Focus newly added Compare Here product input`). Candidate workflow **33994946204** passed for the exact SHA, and milestone provenance workflow **33995260282** verified the promoted ref and exact release artifact lineage. After the shopper explicitly adds a product in Compare Here, the new bounded editable block is focused and its cursor is placed at the end, with the keyboard requested through the existing navigation helper. This removes a second tap for the next aisle-side entry while leaving exact parsing, money, quantity, promotion, evidence and private-memory gates unchanged. The change is navigation-only: no planner, projector, product, offer, store, availability, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,557 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Signed offline catalog verification passed for 30,000 GTA identities, 30,000 Metro Vancouver identities and the 6,093-location GTA/GVA store directory. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks also passed. Physical-device keyboard/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `e73b09454773e3b9b98a0f12a6277db33ca7ddea` (`Reveal Home plan after explicit actions`). Candidate workflow **33993225356** passed for the exact SHA, and milestone provenance workflow **33993536868** verified the promoted ref and exact release artifact lineage. After an explicit Home submit, chicken refinement choice or Shop Again action, the existing immutable projected result is revealed through the parent scroll container with a bounded rectangle; passive typing, restore and private-memory refresh renders do not jump. The reveal is presentation/navigation only: it does not infer coverage, recalculate money or rank candidates, and no product, offer, store, availability, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,555 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks also passed. Physical-device scroll/accessibility ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `f4d8392fc3eb8da445d71dd4181cd3d5ac666539` (`Reduce product handoff friction safely`). Candidate workflow **33991590581** passed for the exact SHA, and milestone provenance workflow **33991889058** verified the promoted ref and exact release artifact lineage. Explicit product handoffs now preserve reviewability: shared text focuses and reopens the editable Compare Here block, while Home/Saved product-name prefills focus and reopen Good Price after its idle projection; recreated/restored drafts are not force-focused. Barcode identity selection now labels and warns about replacing an existing Good Price draft (including package/price text) before the shopper explicitly confirms it; the barcode remains identity-only and exact facts still require review. Raw text remains untrusted, and exact package quantity, currency, observed price, promotion and like-for-like confirmation remain required. No parser, product, offer, store, availability, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,553 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks also passed. Physical-device keyboard/camera ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `7cdd9d72c9aceb7daca366b7958664d52d3309b3` (`Reopen keyboard after identity handoff`). Candidate workflow **33990220399** passed for the exact SHA, and milestone provenance workflow **33990587690** verified the promoted ref and exact release artifact lineage. After a reviewed OCR suggestion or barcode identity fills an editable Compare Here entry, the existing navigation handoff now explicitly reopens the on-device keyboard; the same barcode identity handoff is applied in Good Price. The fields remain ordinary editable text: exact package quantity, currency, observed price, promotion and like-for-like confirmation are still required, and no parser, product, offer, store, availability, evidence, planner, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,550 Android tests and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer and checksummed release-bundle checks also passed. Physical-device keyboard/camera ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `74e593fc4985a293501f8cd02e5dc4de8c6587e3` (`Share projected Home plans safely`). Candidate workflow **33989029339** passed for the exact SHA, and milestone provenance workflow **33989343043** verified the promoted ref and exact release artifact lineage. Practical Shopping Home now offers an explicit `Share this plan` action only for an existing projected store result. It previews and sends a bounded summary of the already-projected total, store, coverage, travel, evidence and any optional second stop, while omitting item names and private history and repeating the fictional/offline disclosure. No-coverage results, unsafe facts, stale dialogs and missing share handlers fail closed; the action never re-ranks, recalculates money, reads private history or implies live retailer data. This focused consumer presentation/lifecycle improvement adds no planner, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,548 Android tests, and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Candidate verification also passed Android lint/build, APK privacy (no INTERNET or ACCESS_NETWORK_STATE), one-signer verification and the checksummed release bundle. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `22e7989e0146a9acfdb7dacf3480630103f0c4c9` (`Allow Good Price keyboard submission`). Candidate workflow **33987076763** passed for the exact SHA, and milestone provenance workflow **33987368167** verified the promoted ref and exact release artifact lineage. Good Price now exposes the keyboard's `Done` action when its existing visible check gate is enabled, invoking the same exact route as the button; the result path also dismisses the keyboard and clears editor focus before projection. This removes a trip to a button below the editor without changing parsing, exact money/quantity, evidence, private-memory, planner, ranking, product, offer, store, availability, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,541 Android tests, and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `591ba936ee55373a3867084a4a930a69e40bb5ed` (`Keep exact price results visible after entry`). Candidate workflow **33986142487** passed for the exact SHA, and milestone provenance workflow **33986488417** verified the promoted ref and exact release artifact lineage. Compare Here and Good Price now dismiss the on-screen keyboard and clear editor focus before their exact result is projected, so the unit-value answer and any device-only memory outcome are immediately visible after an aisle-side action. This is a bounded Android interaction correction: the existing coordinators remain the only parsing, exact-money, quantity, evidence and private-memory authorities, and no planner, product, offer, store, availability, ranking, networking, account, tracking, demo-data or provider-economics authority changed. Local verification passed 402 shared-core tests, 1,540 Android tests, and 79 Python/catalog tests with zero failures or skips; browser verification passed 30 tests with Firefox lint clean. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `3864a339ded3c975f909789add947397e27dbc3b` (`Clarify fictional Shop Again disclosure`). Candidate workflow **33984116186** passed for the exact SHA, and milestone provenance workflow **33984866308** verified the promoted ref and exact release artifact lineage. Home's repeat-list action now says it reuses offline fictional demo data and is not a live retailer plan, so its standalone accessibility description cannot imply current retailer evidence. This presentation-only correction adds no planner, product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,538 Android tests, and 79 Python/catalog tests with zero failures or skips. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code slice is `445af5f80d5624e35af2c4c27cbd2b04516bcb18` (`Clarify projected savings as potential`). Candidate workflow **33981634428** passed for the exact SHA, and milestone provenance workflow **33982413282** verified the promoted ref at docs head `2a11bdde5863f1e565726b954f68b7ca34be0cb1` and release artifact lineage. The optional second-stop alternative now says `Could save ...` so a projected, evidence-backed scenario cannot be mistaken for money already saved; exact savings math and eligibility remain owned by the existing planner/projector. This presentation-only correction adds no product, offer, store, availability, evidence, ranking, persistence, networking, account, tracking, demo-data or provider-economics authority. Local verification passed 402 shared-core tests, 1,537 Android tests, and 79 Python/catalog tests with zero failures or skips. Physical-device ergonomics/accessibility and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code milestone is `195aa654b05bea040a245ce454a06fa367deee76` (`Allow removing one private history observation`). Candidate workflow **33980306927** passed for the exact SHA, and milestone provenance workflow **33980621340** verified the promoted ref and release artifact lineage. Home now exposes `Forget one observation` only for readable, nonempty device-only comparison memory. A deterministic picker shows at most the 32 newest exact observations and carries each SHA-256 observation fingerprint—not a display name or list position—through explicit confirmation. The app re-reads the atomic local store immediately before removal; changed, erased, corrupt, oversized, missing or failed writes leave data untouched and report an honest recovery state. This gives the shopper individual deletion in addition to existing review, clear-all and export controls, without turning history into live offers, store facts or planner input. No planner, projector, product, offer, store, availability, evidence, ranking, networking, account, tracking, demo-data or provider-economics authority changed. Candidate Linux verification passed 402 shared-core tests and 1,533 Android tests with zero failures or skips, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device removal/share, camera/OCR ergonomics and lawful production Home activation remain open; provider feeds remain blocked pending explicit rights.

The latest promoted code milestone is `b94508453b4d51fbf192a9216d919b3b5818de9e` (`Add explicit private history export from Home`). Candidate workflow **33978834368** passed for the exact SHA, and milestone provenance workflow **33979149725** verified the promoted ref and release artifact lineage. Home now exposes `Export private history` only when bounded device-only comparison memory is readable and nonempty. The action creates a deterministic, newest-first, human-readable copy with exact price, package quantity, unit rate, price basis, promotion terms, observation time and source labels; it explicitly says the copy is personal historical context, not live retailer pricing, inventory, availability or a guarantee, and includes no store claim because private memory does not establish one. Home shows a five-row preview and an explicit privacy warning, then re-reads the atomic local store immediately before invoking Android's explicit text-share chooser; erased, changed, corrupt, empty or oversized histories fail closed without sharing or changing data. The exporter is bounded at 256 KiB and never truncates a user's copy. No planner, projector, product, offer, store, availability, evidence, ranking, networking, account, tracking, demo-data or provider-economics authority changed; existing review and clear-all controls remain the only memory-management routes besides this explicit export. Candidate Linux verification passed 402 shared-core tests and 1,533 Android tests with zero failures or skips, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device export/share, camera/OCR ergonomics and lawful production Home activation remain open. Provider feeds remain blocked pending explicit rights; see `DEVICE_VALIDATION.md` for the unrun device checklist.

The latest promoted code milestone is `2bdf246c5a5843ff9f7384f4743c472e2b71dfda` (`Focus first OCR addition in Compare Here`). Candidate workflow **33976684845** passed for the exact SHA, and milestone provenance workflow **33977035497** verified the promoted ref and release artifact lineage. Scan & Compare now returns the deterministic index of the first OCR suggestion explicitly added to the bounded editor and focuses that block after the review dialog is dismissed. The preceding barcode identity handoff also focuses the exact block populated by the selected identity, while Good Price focuses its manual product field after barcode identity selection. These are navigation-only affordances: barcode/OCR values remain untrusted names or raw suggestions, exact package quantity/currency/price and like-for-like review remain required, and no product, offer, store, availability, evidence, planner, ranking, persistence, networking or provider-economics authority moved into the Activities. Candidate Linux verification passed 402 shared-core tests and 1,529 Android tests with zero failures or skips, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics, accessibility and lawful production Home activation remain open. See `DEVICE_VALIDATION.md` for the unrun device checklist.

The latest promoted code milestone is `06c37ae4e3c2834bb01dc73d7b85f7e6815c9cd0` (`Preserve Home refinements when shopping again`). Candidate workflow **33974689463** passed for the exact SHA, and milestone provenance workflow **33975020123** verified the promoted ref and release artifact lineage. Home's `Shop again` action now replays the already-completed session model through the existing deterministic reducer, preserving explicit refinements such as a selected chicken cut instead of routing the unchanged query through a fresh draft and asking the same question again. Typed request details remain reconciled against the replayed stable item keys. This is a focused session/consumer-flow correction: it adds no planner, projector, price, evidence, offer, store, availability, ranking, persistence, networking or provider-economics authority and keeps the fictional sample disclosure unchanged. Candidate Linux verification passed 402 shared-core tests and 1,526 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `cfd4ba6663978dcc2f73447f218b0b4df643c568` (`Prefill complete OCR comparison drafts`). Candidate workflow **33973764156** passed for the exact SHA, and milestone provenance workflow **33974081700** verified the promoted ref and release artifact lineage. Scan & Compare's photo-review dialog now offers an explicit optional `Add with detected details` action when a bounded OCR row has one concrete ISO-currency price, an exact non-derived package quantity, a usable name and a promotion shape the exact route can replay. It copies only ordinary editable text into the draft; ambiguous or multiple prices, unclear currency, estimated/derived quantities and unsupported promotions stay on the raw-OCR path. The dialog and resulting status continue to mark these as unconfirmed suggestions, and the existing exact quantity/currency/price, promotion and like-for-like gates remain authoritative. The same slice corrects the displayed skipped-snippet count so it is not double-counted. This adds no OCR, product, offer, store, availability, planner, ranking, persistence, networking or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,525 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `55b1ac9c411444b5285fdcf6dc9740f3f8848cce` (`Explain OCR signals during photo review`). Candidate workflow **33972120095** passed for the exact SHA, and milestone provenance workflow **33972485006** verified the promoted ref and release artifact lineage. Scan & Compare's bounded photo-review rows now show deterministic parser signals for a possible product name, price text and package size, while every row is explicitly labelled `Review only`. Multiple price signals, ambiguous currency and estimated or missing package quantity are called out as review requirements. The selected value remains the original untrusted OCR text and still passes through the existing editable-entry, exact quantity/currency/price and like-for-like gates before comparison or private-memory capture. This presentation-only friction reduction adds no OCR authority, product, offer, store, availability, planner, ranking, persistence, networking or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,522 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `8499e8ad2b566947d6bd6af70d8ab24b47cbf5c2` (`Open Home catalog identities in Compare Here`). Candidate workflow **33970594096** passed for the exact SHA, and milestone provenance workflow **33970918134** verified the promoted ref and release artifact lineage. When Home's signed offline identity lookup returns a selected identity for an unknown list word, the result dialog now offers `Use in Scan & compare` alongside `Replace list word`. The handoff sends only bounded, explicitly untrusted display text through the existing `openComparisonWithSharedText` route; the shopper still enters and reviews exact package quantity, currency, observed price and like-for-like basis before any comparison or private-memory write. The action fails closed for invalid, oversized or control-character names and stale/closed dialog generations, while the exact product-confirmation path is unchanged. This adds no product, package, price, store, stock, availability, freshness, evidence, planner, ranking, persistence, networking or provider-economics authority; the visible fictional Home remains clearly demo-only. Candidate Linux verification passed 402 shared-core tests and 1,515 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `a231f0ecc962737f815dc2bfa9fe894037dd2bf9` (`Cancel stale Compare Here photo work`). Candidate workflow **33969229838** passed for the exact SHA, and milestone provenance workflow **33969555817** verified the promoted ref and release artifact lineage. Scan & Compare now exposes a bounded `Cancel photo reading` action while on-device OCR is active. Draft edits, clear actions and teardown invalidate the photo generation, reject stale callbacks, clean temporary camera files and leave the current comparison entries untouched; no photo suggestions are added after invalidation. This lifecycle/ergonomics correction adds no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,515 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `9318a70d5b6a9a31ba0d4377efdfebd676ccf749` (`Restore Good Price results after recreation`). Candidate workflow **33967685757** passed for the exact SHA, and milestone provenance workflow **33967990673** verified the promoted ref and release artifact lineage. Good Price now restores a previously evaluated answer and share card after activity recreation by replaying the exact bounded check without appending memory again; the matching observation fingerprint is excluded from its own private-history comparison. Any draft edit, clear action, or private-memory change clears the restoration marker, and unavailable memory fails closed to the idle/recovery presentation. This is a renderer/lifecycle correction with no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider, or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,510 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `8c0f4e843b0c1cce266f17fcceda19f278b5711a` (`Preserve Good Price capture draft safely`). Candidate workflow **33966599555** passed for the exact SHA, and milestone provenance workflow **33966897180** verified the promoted ref and release artifact lineage. Good Price now restores its bounded typed product draft across activity recreation without overriding a fresh intent prefill. Its existing barcode route prevents duplicate capture launches, keeps the button disabled while capture/lookup is active, and reports cancellation explicitly while preserving the typed price-check draft. This is a renderer/orchestration-only reliability correction; it adds no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider, or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,508 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `9432604ca89572233e64b8268a0673ed35c6c0ce` (`Announce scanner availability accessibly`). Candidate workflow **33964877138** passed for the exact SHA, and milestone provenance workflow **33965190171** verified the promoted ref and release artifact lineage. Compare Here’s existing optional scanner status now uses a polite accessibility live region, so availability changes observed when the screen resumes are announced to assistive technology. This is a renderer-only accessibility correction; it adds no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,505 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `60bd0358bc03a50c5180fea1e29a1ee3e65597cd` (`Invalidate stale private memory on resume`). Candidate workflow **33964012615** passed for the exact SHA, and milestone provenance workflow **33964271194** verified the promoted ref and release artifact lineage. Good Price now reloads device-only memory on resume and returns to its explicit idle presentation whenever the memory state or load issue changed, preventing a result or share card from retaining erased or changed personal context. Compare Here records the state behind its visible memory message and hides that message when a paused screen detects a changed or unreadable store. These are fail-closed lifecycle corrections; they add no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,505 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `d981fdcd81aa8101c3006f82b8b92744f3fd12ee` (`Announce private memory outcomes accessibly`). Candidate workflow **33963348099** passed for the exact SHA, and milestone provenance workflow **33963622041** verified the promoted ref and release artifact lineage. Good Price and Compare Here private-memory status changes now use polite accessibility live regions, so saved, unavailable and clear-failure outcomes are announced without moving any authority into Views or changing result semantics. Candidate Linux verification passed 402 shared-core tests and 1,504 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `d6a0b25ef3e20a7c533cb98fb083c910e734b641` (`Improve Good Price result accessibility`). Candidate workflow **33962568132** passed for the exact SHA, and milestone provenance workflow **33962826152** verified the promoted ref and release artifact lineage. Good Price’s projected answer card and optional private-history card now each expose one coherent accessibility summary while decorative child labels are hidden from assistive technology, so exact price, package quantity, unit rate, personal-history context and answer guidance are announced without duplication. This is renderer-only formatting; no parser, exact money/quantity, evaluator, private-memory, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority changed. Candidate Linux verification passed 402 shared-core tests and 1,503 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `44767d3a89c5fe79e3916c09ef91a589e42230dc` (`Invalidate Good Price result after memory clear`). Candidate workflow **33961384703** passed for the exact SHA, and milestone provenance workflow **33961699432** verified the promoted ref and release artifact lineage. Good Price now clears the rendered answer and share action after the existing private history store accepts deletion; failed deletion remains an explicit error. This prevents erased personal context from lingering on screen or being shared and changes no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,502 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `9c164ecd2566f26e9805cbcfce1356090cdf9ad6` (`Make photo retry outcomes explicit`). Candidate workflow **33960585727** passed for the exact SHA, and milestone provenance workflow **33960929862** verified the promoted ref and release artifact lineage. Scan & Compare now offers `Try another photo` only for recoverable OCR/no-match or picker/camera creation failures. Camera permission denial, cancellation and unavailable hardware stay explicit terminal states without a retry that would repeat the same impossible action. This presentation-only recovery correction preserves existing entries and adds no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,501 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `58c8732f1fe5dcb1a450fb9c0d28aa743f5dfb56` (`Add retry action for failed comparison photos`). Candidate workflow **33959540807** passed for the exact SHA, and milestone provenance workflow **33959812269** verified the promoted ref and release artifact lineage. Scan & Compare now offers a one-tap `Try another photo` action after a user-triggered camera/import failure or a no-match OCR result, repeating the same method the shopper chose. The retry is hidden while idle, successful review is pending, or another capture is running; existing entries remain unchanged on every failure/cancellation path. This presentation-only convenience reuses the bounded on-device OCR route and adds no parsing, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking authority. Candidate Linux verification passed 402 shared-core tests and 1,500 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `1db1b0d9edaa18e4b78ef11d08d317175d4e2902` (`Add repeat-list action to Practical Shopping Home`). Candidate workflow **33958293679** passed for the exact SHA, and milestone provenance workflow **33958570310** verified the promoted ref and release artifact lineage. Home now exposes an explicit `Shop again` action only for a completed nonempty result; it replays the same typed list through the existing Home session/controller and remains hidden for idle, draft, refinement and error states. The action is owner-driven, disabled until the surface is attached and rendered, and cleared with the Activity lifecycle. It changes no prices, evidence, stores, availability, ranking, persistence, networking or demo semantics, and does not duplicate the shared planner/projector. Candidate Linux verification passed 402 shared-core tests and 1,497 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `42f86e7dbd6a1e03a694c85032f30f21b1ab752e` (`Let saved products reopen Good Price`). Candidate workflow **33957008185** passed for the exact SHA, and milestone provenance workflow **33957294707** verified the promoted ref and release artifact lineage. Saved product rows now expose a typed `Check price` action that opens the existing Good Price route with only a bounded display-name prefill; exact package quantity, currency, observed price, selection and evidence checks remain required before any answer or private-memory write. Saved identity never becomes current price, stock, availability or store evidence, and busy Saved lifecycle states suppress the convenience action. No shared planner/projector, exact money/quantity, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority changed. Candidate Linux verification passed 402 shared-core tests and 1,494 Android tests with zero failures, lint/build, APK privacy/single-signer, browser/Firefox and release-bundle checks; local Python/catalog verification passed 79 tests. Physical-device ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `37cc19cb72cb231831dfecef8fd5eb0c0e5bca2b` (`Add review gate for OCR comparison suggestions`). Candidate workflow **33955298400** passed for that exact SHA, and milestone provenance workflow **33955607275** verified the promoted ref and release artifact lineage. Scan & Compare photo import/capture now keeps OCR output in a bounded, explicitly untrusted review dialog: suggestions are de-duplicated and capacity-limited before display, the shopper chooses which snippets to add, and only the existing editable exact route can parse, require like-for-like confirmation, compare, or remember prices. Cancel, dismiss, lifecycle changes, empty selection and malformed/duplicate/over-capacity snippets leave existing entries unchanged. No planner/projector, exact money/quantity, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority changed. Candidate Linux verification passed 402 shared-core tests and 1,490 Android tests with zero failures, lint/build, APK privacy and single-signer checks, browser/Firefox (30 tests), Python/catalog checks and the verified release bundle. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

The latest promoted code milestone is `0e1ffaaf24352ad9652631d84d081cd67db4699d` (`Clarify missing Home item price breakdowns`). Candidate workflow **33952630205** passed for that exact code SHA, and the resulting promoted tree was provenance-verified by milestone workflow **33953296196**. Home, Basket and the inactive production Home surface now distinguish an exact per-item breakdown from a covered subtotal whose optional line-item detail was not supplied: the former shows the exact included amount, while the latter says `Included in the basket total — exact item price not shown.` Missing, stale, conflicting and unknown price states remain separate and no price is invented; the planner and shared projector remain the only decision authorities. Basket’s check-off accessibility summary carries the same disclosure. The test-only source-boundary helper normalizes CRLF/CR to LF at the fixture edge, preserving complete assertions and making the full Windows run deterministic. Candidate Linux verification passed 402 shared-core tests and 1,485 Android tests with zero failures, lint/build, APK privacy and single-signer checks, browser/Firefox (30 tests) and the release bundle; physical-device ergonomics and lawful production Home activation remain open.

The latest candidate code milestone is `8025eb6e753d505dc75c19630683fd8f3afc2c29` (`Harden production Home UI handoff`), verified by candidate workflow **33933221103** for that exact SHA. The production Home host now retains its exact internal projection for generation identity but hands the renderer only a separate, demo-free `PracticalShoppingProductionHomeUiState`; a presentation projector maps the existing `PracticalShoppingUiState` into bounded per-item rows with exact included prices where supplied and an explicit unknown-price notice for uncovered items. Missing/unsafe display metadata and malformed assignment keys fail closed, while valid partial and no-coverage plans remain visible and honest. A new production Home surface is inactive by default and reuses the existing plan-card renderer; the visible Home remains the clearly labelled fictional controller until lawful current-offer/private-evidence inputs and a separate activation coordinator exist. Candidate Linux verification passed 402 shared-core tests and 1,482 Android tests with zero failures, browser/Firefox checks, lint/build, release-bundle, APK privacy and single-signer checks; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home activation remain open.

The latest promoted code milestone is `730794ed111658383ede5350fededfd57c98ece9` (`Add generation-safe production Home handoff`). Candidate workflow **33930515512** passed for that exact SHA, including the full shared-core/Android verification, browser/Firefox checks, release bundle, APK privacy and single-signer checks. Android now has a narrow production Home adapter that delegates every valid decision to the existing `PracticalShoppingUiProjector`, plus a bounded refresh host that re-evaluates raw orchestration inputs against the supplied lifecycle/disposition registries and rejects stale, duplicate or same-generation-conflicting updates. Structural production failures become an explicit unavailable state; valid no-coverage remains a normal truthful result. The renderer receives no raw production result, provider identifiers, blocker diagnostics or demo data. This prepares the production Home seam but does not activate live retailer networking or replace the clearly labelled fictional Home controller. Candidate Linux verification covered 402 shared-core tests and 1,473 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and full production Home activation remain open.

The latest promoted code milestone is `3efdb688d5ff981b4f421bf349d49ef1432679e1` (`Add privacy-safe Good Price result sharing`). Candidate workflow **33927693684** and milestone provenance workflow **33928182141** passed for that exact SHA. The first-class Good Price route now exposes an explicit `Share this price result` action only after an exact evaluated result. A review dialog precedes Android’s `text/plain` Sharesheet; the bounded generic card contains only the exact entered price, package quantity, selected price basis and exact unit rate, says it is based on a price entered rather than live store pricing, and omits the product name, private history, receipts, location, account and source identifiers. Non-exact, blocked, ambiguous, estimated or unsafe display states never receive a share card, and control-character facts fail closed. No planner/projector, money/quantity, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority changed; the APK permission boundary remains CAMERA plus the app’s non-exported receiver permission. Candidate Linux verification passed 402 shared-core tests and 1,465 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The latest promoted code milestone is `d53b4fca70c3e84b32fab49c7b8aad789e4e92e3` (`Let Home save confirmed offline product identities`). Candidate workflow **33923409718** and milestone provenance workflow **33923881317** passed for that exact code SHA. Home’s resolved list rows now offer an explicit `Choose exact product` action backed by the existing signed offline identity catalog. After the shopper selects a candidate and confirms it, the existing source-revalidated exact-choice projector and asynchronous private Saved transaction persist only the product identity preference. No package quantity, price, currency, store, stock, availability or freshness is inferred; unknown items remain query-only, and the fictional sample planner remains clearly separate and unchanged. This is a consumer-facing identity-selection improvement only: no shared planner/projector duplication, Android networking, new persistence authority, ranking influence or provider economics were added. Candidate Linux verification passed 402 shared-core tests, 1,457 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `80b9efd5e33839e3f0dfd1b10e273c1a2547cfaf` (`Show private Home price observation date`). Candidate workflow **33919462842** and milestone provenance workflow **33920091991** passed for this exact SHA. Home’s exact name/package private-history context now includes the latest observation date in stable UTC form, so a remembered price cannot look like a current retailer offer; a missing/zero timestamp stays explicitly `date not recorded`, and mixed package, currency, basis or promotion histories retain the conservative name-only notice. The shared formatter is reused by the private-history review. This remains read-only device context: the fictional planner result, exact money/quantity, evidence, offers, store/availability, ranking, persistence, networking and provider-economics boundaries are unchanged. Candidate Linux verification passed 402 shared-core tests, 1,452 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `8fc5629e99cca13ff2aca2b462ddaf5ee1bc1dbf` (`Show comparable personal price context on Home`). Candidate workflow **33917203335** and milestone provenance workflow **33917694467** passed for this exact SHA. Home now forwards the existing sample fixture’s normalized package quantity into its device-only private-history presentation. When a normalized display name and package quantity have one comparable currency, unit basis, price-selection and promotion shape, the row shows the last recorded personal price and deterministic remembered unit-rate range; mixed package, basis, currency or promotion history falls back to the prior name-only notice. Copy explicitly says product identity, brand and store may differ and that this is not live store pricing. The existing planner result, exact money/quantity authority, evidence, offers, ranking, store/availability, persistence, networking and provider economics are unchanged. Candidate Linux verification passed 402 shared-core tests, 1,451 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `f3afb2b274ad16dbc84454cc615277c2084ff124` (`Show exact item prices in Home plan`). Candidate workflow **33914080876** and milestone provenance workflow **33914604138** passed for this exact SHA. Home and Basket now show an optional renderer-only `Included in plan: X CAD` line for each covered item when the existing planner candidate carries an exact line-item breakdown. The breakdown is validated with exact Money arithmetic, must match the covered item set and authoritative subtotal, and is ignored by ranking; production evidence bridges and the fictional demo adapters populate it while older candidates remain supported with an unknown breakdown. Missing items remain visibly unknown, and Basket’s local check-off scope changes when a projected included price changes. No product identity, offer, availability, evidence, planner, ranking, network, persistence or provider-economics authority changed. Candidate Linux verification passed the 1,448-test Android job, lint/build/privacy/single-signer/release bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `d206331e2d3927e3b084b0fed1d92b40f16cdc68` (`Show Home personal history coverage`). Candidate workflow **33900346836** and milestone provenance workflow **33900879487** passed for this exact SHA. Home’s existing immutable projection now adds a clearly bounded `Name-matched personal history: X of N list items.` line when the current list has names and readable device-only comparison memory. The line is explicitly labelled as **not current-price coverage**: matching is only normalized display-label context, not exact identity, package, quantity, price, freshness, stock, availability or a live offer. Empty/blank list names retain the prior generic observation summary; unreadable memory remains suppressed and the fictional planner remains separate. No planner/projector, money, evidence, persistence, Android networking, provider-economics or ranking authority changed. Candidate Linux verification passed the Android test/lint/build/privacy/single-signer/release bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `41a0fc28466cd0ac5b794bcedfaf49ff7d15173a` (`Expose offline product identity search`). Candidate workflow **33898457412** and milestone provenance workflow **33898919053** passed for this exact SHA. Search now offers an explicit, user-triggered lookup against the separately signed 30,000-record offline Canada identity catalog. Results are limited to identity names, brands and deterministic match labels; they never claim package quantity, price, stock, store availability, freshness or ranking. A selected name is handed to the existing untrusted `text/plain` Scan & compare route, where the shopper must still provide exact quantity, currency and observed price before comparison. Query edits, sample actions, route changes, cancellation and activity teardown dismiss the dialog and invalidate stale work. The fictional sample value search remains clearly separate. No Android networking, new persistence authority or shared planner/projector duplication was added. Candidate Linux verification passed the Android tests/lint/build/privacy/single-signer/release bundle and browser/Python gates; the Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `713e4396c6643f3b6aa4b06af3a90cea0921416a` (`Add text share handoff to Compare Here`). Candidate workflow **33895387933** and milestone provenance workflow **33895985864** passed for this exact SHA. ValuePilot is now an explicit `text/plain` share target: it bounds and previews intentionally shared content as untrusted text, requires the shopper to open Scan & compare, and inserts it only into the earliest empty editor slot while preserving existing entries. Blank, oversized and full-draft cases are rejected without overwrite and remain visibly recoverable. The share path performs no parsing, product/GTIN matching, package, price, store, availability, evidence, planner or ranking work; the existing editable Compare Here route remains authoritative and any draft retention stays within its existing private local lifecycle. No Android networking or new persistence authority was added, and the APK still contains no INTERNET or ACCESS_NETWORK_STATE permission. Candidate Linux verification covered all 1,432 Android tests plus lint/build/privacy/single-signer/release, browser (30 tests/Firefox lint), and the 79-test Python/catalog suite. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `45f52cdaa5d3933a30b14f0386c9d51310d9a0f3` (`Make Home private history deletion explicit`). Candidate workflow **33892635254** and milestone provenance workflow **33893217763** passed for this exact SHA. Home’s existing bounded private-history review now offers an explicit `Clear private history` action. The action requires confirmation, deletes only the device-local comparison observations through the existing atomic store, resets Home’s in-memory summary only after the store accepts the deletion, and shows a failure state without claiming success. The confirmation explicitly says the Home list, identity catalog, store locations, live offers and recommendations are unaffected. Empty/unavailable memory keeps the prior recovery route; no store or live offer is inferred. This is a reversible local-consumer control: no planner/projector, money arithmetic, offer/store/availability, network, persistence authority beyond the existing private store, or provider-economics authority changed. Candidate Linux verification covered all 1,419 Android tests plus lint/build/privacy/single-signer/release, browser (30 tests/Firefox lint), and the 79-test Python/catalog suite. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `7c7be8d5215d2f6261049398700486252dda407c` (`Carry no-coverage summary into Basket`). Candidate workflow **33887664746** and milestone provenance workflow **33888215450** passed for this exact SHA. Basket now forwards Home’s existing renderer-owned `0 of N items priced yet.` summary when a projected result has no primary plan, so switching tabs does not hide the coverage state. The summary remains absent for empty/refinement states and for any projected primary plan that already carries exact coverage text. This is presentation/accessibility-only: no planner/projector, money arithmetic, price/offer/store/availability evidence, network, persistence or provider-economics authority changed. Local Python/browser/Android focused and build/privacy/signing checks passed; the Windows run retains the known 18 line-ending-sensitive boundary failures and physical-device ergonomics remain open.

The promoted milestone is now `1be6628b3fb48b67fa7ee272a8ed3a3a35984529` (`Clarify Home no-coverage summary`). Candidate workflow **33885576989** and milestone provenance workflow **33886216516** passed for this exact SHA. When Home’s existing projected result has no primary plan, the renderer now adds a compact `0 of N items priced yet.` aggregate alongside the per-item unknown-price notices. It is hidden for empty, refinement and any result with an existing primary plan. This is presentation-only: the shared planner/projector, money arithmetic, price/offer/store/availability evidence, network, persistence and provider-economics boundaries are unchanged. Focused tests, candidate Android/browser/privacy/signing/release checks and milestone provenance passed; the Windows run retains the known 18 line-ending-sensitive boundary failures and physical-device ergonomics remain open.

The promoted milestone is now `ec7ed377c27e7e6b4ece9030bedc5e7cbf997188` (`Add signed GTA GVA store directory snapshot`). Candidate workflow **33883155939** and milestone provenance workflow **33883690098** passed for this exact SHA. The APK now bundles a separately signed, source-attributed OpenStreetMap location directory with 6,093 source-listed rows (4,311 GTA and 1,782 Metro Vancouver). The directory loader verifies manifest/source hashes, detached RSA signature, rights gates, launch geography and freshness before exposing rows; blocked or stale artifacts expose no locations. Every row is explicitly `LOCATION_ONLY`, and no row can become a product, package, price, offer, stock, availability or ranking fact. Data Status now reports those counts and the observed date while preserving the shared 30,000-record identity rail, `0` authorized current offers, private-memory boundaries, flyer absence, offline connectivity and fictional Home/Search sample disclosure. Candidate Linux verification, Android tests/lint/debug build, privacy, signing, browser, Python snapshot and release-bundle checks are green. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics remain open.

The promoted milestone is now `16e1e68af516df2f4c4ac27b7f9105581a18a150` (`Add barcode identity handoff to Compare Here`). Candidate workflow **33876560165** and milestone provenance workflow **33877137830** passed for this exact SHA. Scan & Compare now offers the same user-triggered, cache-scoped barcode photo/import route already used by Good Price. After one validated barcode, the signed offline GTA/Metro Vancouver identity rail is searched off the UI thread and a reviewable name suggestion can fill only the first empty comparison entry; existing entries are never replaced. Package quantity, observed price, currency, like-for-like confirmation and the existing exact comparison/private-memory gates remain required. The identity rail still provides no package, price, store, stock, availability, freshness, ranking or live-offer authority. Barcode lookup cancellation, stale edits, full drafts, oversized names and lifecycle teardown fail closed; capture remains non-exported/local, CAMERA optional, and the APK has no INTERNET or ACCESS_NETWORK_STATE permission. Deterministic insertion, Android boundary, candidate Linux verification, lint/build, privacy, signing, browser and release-bundle checks are green. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device barcode ergonomics remain open.

The promoted milestone is now `1f3f8763b525d8b6533b9b012ef5a0adc2476946` (`Add offline barcode identity handoff to Good Price`). Candidate workflow **33874530113** and milestone provenance workflow **33875052835** passed for this exact SHA. The dedicated Good Price route now offers a user-triggered barcode photo/import flow using the bundled on-device ML Kit barcode model. A bounded resolver accepts exactly one distinct checksum-valid GTIN, ignores unrelated/invalid codes, preserves equivalent UPC/EAN representations deterministically, and fails closed when multiple product GTINs are present. Good Price performs the signed offline GTA/Metro Vancouver identity lookup off the UI thread and presents an explicit name suggestion; using it only fills the editable product-name draft. The identity snapshot contributes no package quantity, price, store, stock, availability, freshness, ranking or live-offer authority, and the shopper still enters and reviews their own observed quantity/price before the existing exact check/private-memory path. Temporary camera files are cache-scoped and removed on every terminal/lifecycle path; the new activity is non-exported, CAMERA remains optional and the APK still has no INTERNET or ACCESS_NETWORK_STATE permission. Deterministic resolver, presentation, Android boundary and Good Price tests cover this handoff. The full Windows Android test task retains the repository's known 18 line-ending-sensitive boundary failures; Linux candidate verification, lint/build, privacy, signing, release bundle, browser checks and Python/catalog tests are green. Physical-device barcode ergonomics still require device validation.

The promoted milestone is now `1b47547ddb04cb06afc5ed8b6400d8429705a5f9` (`Clarify pending extra-stop evaluation on Home`). Candidate workflow **33871829169** and milestone provenance workflow **33872307030** passed for this exact SHA. When a Home result has a missing usable price, the collapsed extra-stop disclosure now says `Not evaluated yet` in its renderer-projected summary, matching the existing expanded notice that another stop is not evaluated until every requested item has a usable price. Complete plans retain the original threshold summary. This is a reversible presentation refinement only: it adds no price, offer, store, availability, planner, ranking, network, provider-economics or persistence authority. The full Windows Android test task retains the repository's known line-ending-sensitive source-boundary failures; Linux candidate Android verification, lint/build, privacy, signing, release bundle, browser checks and Python/catalog tests are green.

The promoted milestone was `8f16e3fd6d6edf53a202c267fee87ba9e940bf99` (`Add consumer source and licence disclosure`). Candidate workflow **33870199022** and milestone provenance workflow **33870665728** passed for this exact SHA. The shell now exposes a clearly labelled `Sources & licences` action that explains the attributed Open Food Facts ODbL-1.0 identity snapshot, its offline-search-only limits, private observations, fictional Home/Search demo data, and the absence of a store directory, authorized current-offer feed or copied flyer content. The dialog is presentation-only and adds no price, offer, store, availability, planner, ranking, network, provider-economics or persistence authority. The full Windows Android test task retains the repository's known line-ending-sensitive source-boundary failures; Linux candidate Android verification, lint/build, privacy, signing, release bundle, browser checks and Python/catalog tests are green.

The promoted milestone is now `6743181977f1500772444599a667284b8d7ea2e3` (`Add actionable Home private history review route`). Candidate workflow **33867138431** and milestone provenance workflow **33867613305** passed for this exact SHA. When Home has readable nonempty private comparison history, its renderer-owned `Review private price history` action opens the existing Scan & compare screen; empty history stays quiet and unavailable history stays on its recovery notice. This is a small reversible navigation refinement: it adds no price, offer, store, stock, availability, planner, ranking, network, provider-economics or persistence authority, and the existing non-live-price and package/promotion caveats remain explicit.

## Previous verified engineering head (superseded)

The promoted milestone was `22e45377d40511e5a42aa786264cb562f15a1336` (`Point Home memory summary to review route`). Candidate workflow **33864992864** and milestone provenance workflow **33865459380** passed for this exact SHA. When Home had nonempty private comparison history but no current list label matched, its renderer-owned summary told the shopper to open Scan & compare prices to review those observations. This was a small route-discovery refinement with no action, storage, price, offer, store, stock, availability, planner, ranking, network, provider-economics or persistence authority.

## Previous verified engineering head (superseded)

The promoted milestone was `08657541629f5e7beb44c046573a8ae5d79be22a` (`Clarify offline catalog list replacement action`). Candidate workflow **33864027611** and milestone provenance workflow **33864356368** passed for this exact SHA. Home’s offline identity-match action now says `Replace list word` and exposes a matching assistive-technology description that explicitly says the action only edits the unresolved Home query and does not confirm an exact product, price or availability. The existing bounded, source-labelled identity lookup, review-before-Plan flow, fictional planner disclosure and query-only replacement remain unchanged. This is a presentation/accessibility refinement with no price, offer, store, stock, availability, planner, ranking, network, provider-economics or persistence authority added.

## Previous verified engineering head (superseded)

The promoted milestone is now `e02e358eb29ea28e4287ebef717d2d800d2b0720` (`Record Home private memory summary milestone`). Candidate workflow **33862278396** and milestone provenance workflow **33862848704** passed for this exact SHA. Scan & Compare offers a user-triggered `Take a price photo` action alongside the existing photo import. It requests CAMERA only after that tap, writes the captured image to a cache-scoped FileProvider URI with explicit temporary read/write grants, and sends the bounded image through the existing on-device OCR suggestion/review route. Import and capture actions share one in-flight gate, stale/lifecycle callbacks are ignored, temporary files are deleted on success, failure, cancellation or teardown, and camera-unavailable/permission-denied/error states leave existing entries unchanged. OCR status is an accessible polite live region. Home now also exposes only a bounded count of nonempty private comparison history when no current item name matches, while retaining the explicit name/package/promotion mismatch and non-live-price boundary. The existing Compare Here history, dedicated good-price check, fictional sample disclosures, 30,000-record shared Canada identity catalog and atomic catalog-release pointer remain intact. No camera permission is requested at launch; no network, live-price, stock, availability, planner or ranking authority was added.

Home still carries an explicit typed availability status for its device-only comparison history: an unreadable or invalid store is disclosed in an accessible recovery notice, and history-derived row context is suppressed until the store is readable again. Empty history remains distinct from unavailable history, while the fictional planner result, exact totals and unknown states pass through unchanged. The dedicated “Is this a good price?” path still accepts one manually entered product, reuses exact parser/comparison projection and gives only private-history context. Exact currency, package quantity, selected current/member price basis and promotion terms remain required; missing member prices, estimated quantities, ambiguous currencies and malformed input stay visibly blocked. A successful check remembers the typed exact observation locally with a source label distinct from Compare Here; it never becomes a store, stock, availability, public-offer, planner or ranking fact.

The same verified release carries the catalog milestone `a00f948995300374f953053a53c298546b530a52`: a signed 30,000-record shared Canada identity source is bundled for both GTA and Metro Vancouver, with no prices/offers/stock/availability. Offline catalog workflow **33840204939** and full candidate build **33840205073** passed, followed by milestone catalog workflow **33840982043**. Catalog promotion now writes one immutable generation and atomically replaces one root `active-generation.json` pointer; incomplete releases and orphan generations are ignored, and damaged active generations recover through the embedded last-known-good release. Shared identity records are counted once across regional references.

Home now also makes nonempty device-only comparison memory discoverable when no current list item has a matching display label. Its immutable renderer exposes only a bounded observation count and explains that Home context is name-based, package and promotion details may differ, and the memory is not live store pricing. Empty memory stays quiet; unreadable memory keeps the existing recovery notice and suppresses history-derived context. Deterministic Home history, renderer and View-boundary tests cover the summary. No price, offer, store, availability, planner, ranking, network or provider authority moved into Home.

This adds no Android networking, account, tracking, current-price authority, planner/ranking authority, provider economics, or persistent overlay foundation. Physical-device testing is still required before declaring camera/OCR ergonomics or launch readiness complete. The prior camera milestone is superseded by this Home history discoverability refinement.

## Previous verified engineering head (superseded)

The promoted milestone was `a5cfa0e609d18f1e3730f934788a72ade88ba37a` (`Rollback coverage report write failures`), verified by candidate build workflow **33829634963** and milestone provenance workflow **33830070695**. The weekly multi-metro refresh kept pointer promotion and diagnostic coverage reporting in one rollback boundary: if any region promotion or the final report write failed, every captured `current.json` and `last-known-good.json` pointer was restored byte-for-byte and the prior coverage report remained unchanged. Deterministic tests covered both a later-region failure and a report-write failure. This added no price, offer, availability, planner/ranking, network, or identity authority. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; offline snapshot tests passed in workflow **33829634993**.

## Previous verified engineering head (superseded)

The promoted milestone is `b462f01250d0987adfb16fcca7b908bc7bce9bd1` (`Rollback multi-region snapshot promotion failures`), verified by candidate build workflow **33829078507** and milestone provenance workflow **33829440551**. The weekly multi-metro refresh now captures each requested region’s `current.json` and `last-known-good.json` bytes before promotion and restores every pointer byte-for-byte if any later region promotion fails; a deterministic test covers a simulated second-region failure and preserves the prior coverage report. This closes the cross-region partial-promotion gap without adding price, offer, availability, planner/ranking, network, or identity authority. The existing signed 5,000-per-metro identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, rights gates, and Home stale-selection guidance remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the offline-catalog workflow **33829078425** also passed.

## Previous verified engineering head (superseded)

The promoted milestone is `c05ea098f4f8a6d375817b551befa890b3d250e4` (`Explain stale offline match application failure`), verified by candidate build workflow **33827937894** and milestone provenance workflow **33828361403**. Home’s offline identity-match dialog still names the exact unresolved word being searched, and now explains when a selected identity can no longer be applied because the Home list changed while the bounded lookup was open; the stale action is disabled so the shopper can review and retry. This is presentation/lifecycle-only and adds no price, offer, availability, planner/ranking, network, or identity authority. The existing 5,000-per-metro signed identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, deterministic refresh quality gates, and no-price/no-availability disclosures remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the unchanged snapshot inputs remain covered by offline-catalog workflows **33826706509** and **33827098104**.

## Previous verified engineering head (superseded)

The promoted milestone is `ee38c00792aaa2374e470fbd54bee30bfd3beb4e` (`Fail closed catalog quality regressions`), verified by candidate build workflow **33826706479** and milestone provenance workflow **33827098123**. The offline-match dialog still identifies the exact unresolved Home word it will search/replace in its title across loading, empty and selectable-result states, improving clarity and screen-reader context when multiple list words need clarification. The bundled Greater Toronto Area and Metro Vancouver identity snapshots contain 5,000 records each and 4,199 unique canonical identity names within the 1,500–5,000 target; 4,960 records receive a grocery hint and 41 receive a household hint under the deterministic 10% reserve. Current offers remain `0` / `NOT_INCLUDED`. Home’s offline catalog result explains that selecting a bundled identity name only edits the Home list and that the fictional sample planner may still leave that inserted name unresolved. Empty results explicitly say that a miss in this snapshot does not mean the product is unavailable. The existing no-price/no-availability disclosure remains, and catalog choices stay query-only until the separate exact-identity confirmation boundary is deliberately wired. The weekly refresh coordinator now fails closed before import/build/promotion when the selector reports an unsatisfied household reserve or identity-name variety outside the 1,500–5,000 launch target; rejected regressions preserve prior reports and pointers. These do not establish demand, retailer availability, stock, price, package quantity, freshness, or organic-ranking authority. The merchant-feed qualifier still reports identity coverage separately from structural current-offer and unit-value candidates; those counts remain `STRUCTURAL_ONLY` with authority `NONE`. The Android build workflow includes the qualifier paths in its push/PR filters. This adds no prices, offers, availability, planner/ranking authority, Android networking, or new consumer claim. Signed manifests and promotion pointers remain authoritative. Clean-source verification passed 400 shared-core tests and 1,337 Android app tests with zero failures, all 58 Android tasks, 65 Python tests, 30 browser tests, Firefox lint with no findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification, exact-SHA release-bundle provenance, and exact-SHA offline-catalog workflows **33826706509** (candidate) and **33827098104** (milestone). Historical superseded entries follow.

The previous promoted milestone was `b6018ab9d08f22558f01c0fe36609dad832b22f5` (`Search offline identity snapshots across supported metros`), verified by candidate workflow **33809477765** and milestone provenance workflow **33809973694**. The offline catalog had an explicit-input, deterministic, offline coordinator for the supported Greater Toronto Area and Metro Vancouver identity snapshots, and Home searched both independently region-bound snapshots through a bounded shared discovery index. Identical identities were deduplicated, conflicting record ids were omitted, and the combined lookup failed closed if either bundled region was not admitted. It required source/rights/timestamp/signing inputs, kept identity data separate from current offers, verified every regional candidate before promotion, and preserved existing pointers on coverage regression; it did not add networking or alter planner/ranking authority. Clean-source verification passed 397 shared-core tests and 1,333 Android app tests with zero failures, all 58 Android tasks, 61 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification and exact-SHA release-bundle provenance.

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

`6ac3ae2cbc6a6a4a855017911d060c458b795408` (`Fail closed Basket navigation on owner changes`) was the promoted milestone, verified by candidate workflow **33739991354** and milestone provenance workflow **33740563023**. Basket’s Open Home navigation control now updates its enabled state immediately when the typed owner callback is installed or cleared, and remains inert before the first immutable render; a detached Basket surface cannot look actionable. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

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

Latest promoted engineering head: `0cf9b253c7f6f28835cb4f2fd0019099b66ce783` (`Acknowledge Watch setup handoff`), verified by candidate workflow **33674718972** and promoted workflow **33675293225**. The milestone includes the immutable app-level `PracticalShoppingRequestDetailsSession` owner on top of the shared request-details core, plus a typed `PracticalShoppingHomeSession.State` that keeps explicit quantity/package/brand intent separate from the existing sample plan. Home presents progressive per-item details and a bounded editor for explicit package count, brand text and exact-product preference. Local-only cross-session retention remembers the bounded Home list, chicken choice, extra-stop preference and opaque request-details bytes; it restores only the exact established request, drops oversized/corrupt values safely, and clears stale detail bytes while a draft is being edited. Watch setup exposes an immutable selection-progress summary (selected staple count against the existing two-item minimum and usual-store selection) directly on the Saved-backed setup surface, so incomplete selections explain why continuation is unavailable without putting readiness logic in the View. When a configured foreground fact-check handoff is explicitly requested, the setup surface now acknowledges the accepted selection and states that no switch decision exists until current prices, route details and evidence checks are supplied; this feedback clears when the immutable selection projection changes. The Saved Watch entry point now also explains, in renderer-ready copy, that the shopper is choosing recurring saved items and a usual store to check whether a future switch is worth the trip; it does not imply live prices or automatic alerts. Details, Watch identity setup and handoff acknowledgement never feed basket arithmetic, planner, ranking, pricing, evidence, clock, or network authority; persistence remains a local UX concern, and the Home dialog explicitly says the fictional sample total remains unchanged until a verified planner mapping exists. The latest shared result-card refinement maps the projector's existing missing-price marker to an amber caution treatment for known subtotals while retaining the green treatment for complete baskets; it changes no projected copy or decision. Basket check-off now has a local-only Clear check-off control that appears only when marks exist, preserving the immutable plan and eligible item keys while resetting foreground progress. The provenance workflow recognizes the repository's historical `work/valuepilot-*` candidate branches while explicitly excluding the milestone branch itself. The earlier Basket surface remains intact: it presents the recognized list, unresolved tokens, exact already-projected one-store/optional-second-stop result, and selected exact extra-stop rule. Empty and attention-required states return the shopper to Home without inventing a result. Home and Basket share one physical result-card renderer, while presentation boundaries perform no planning, ranking, money formatting, or missing-price inference. Incomplete results remain explicitly labelled known subtotals and the fictional/offline disclosure is repeated prominently. Clean-source verification passed 1,625 JVM tests (375 shared-core + 1,250 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK permission inspection, and one-signer APK verification.

The preceding verified Home/Compare slice remains `c6c36416ab136473d7ddc22eef232de6ed090e55` (`Bound Practical Shopping Home query input`). Home users can remove resolved items or unresolved tokens through typed controller actions, natural `and`/`&` list syntax is supported, and result cards label freshness explicitly. The advanced extra-stop preference no longer competes with the untouched Home screen: immutable presentation keeps it hidden until an actual plan result exists, then exposes the shopper's persisted exact threshold for immediate deterministic replanning. The Home list editor shows its 240-character limit and physically retains at most the existing one-character error sentinel; immutable presentation carries the limit and the View only binds the counter/filter. Compare Here supports removing extra product entries while preserving its minimum two-slot shape and exposes an explicit Current shelf prices / Member prices choice. Its primary action is disabled until two product slots contain text and the user explicitly confirms like-for-like substitutability; pure bounded presentation state owns that readiness, while the existing exact route still owns all parsing, price/quantity validation and ranking. Each editor block is capped at the adapter's existing 4,096-character evidence limit. Oversized legacy/restored content is cleared with an explicit error instead of being silently truncated into partial facts. Changing the price basis invalidates a stale result, the choice survives draft restoration, and member mode never substitutes a current price when member evidence is missing. Manual-entry guidance shows the exact `Current price` and optional `Member price` labels accepted by the parser. If the selected basis lacks enough evidence, the result guidance names that basis; member mode explicitly says current prices are not substitutes. The Android privacy boundary is unchanged.

## Product direction now

ValuePilot remains provider-neutral shopping intelligence, but the startup launch strategy changed on 2026-08-29 after provider/rights/cost research.

Provider-by-provider approval is **no longer the primary launch dependency**. Rakuten/Jamieson/GS1/Walmart and future commercial feeds remain supplementary high-authority rails when they become available, but ValuePilot must provide compelling consumer utility without waiting for hundreds of companies.

The product is NOT primarily "5 million live-priced products." Millions of open/free product identities may support recognition, but the consumer promise is:

> **Given what I actually need, where I am, and how much inconvenience I will tolerate, what is the cheapest sensible way to shop?**

The default is **ONE STORE FIRST**.

A second store is optional and should only be recommended when its incremental savings clearly justify parking, getting out of the car, walking, finding products, checkout/loading and route detour. Initial product design may use a visible/user-controlled minimum extra-stop savings threshold around $15, to be validated empirically. Do not hide a made-up human-friction value inside an opaque score.

A third store is not a normal recommendation and should only be considered in an explicitly aggressive savings mode.

Unknown prices remain UNKNOWN. Never fabricate a complete basket by estimating missing prices as facts.

## UX / performance requirement

The app must be extremely polished, fast, simple, obvious and non-confusing. Treat this as an architectural/product requirement, not final cosmetic work.

Primary consumer experiences:

1. **Plan My Shop** — Home/hero flow. One simple shopping-list request; default result is one best-store recommendation card. Advanced controls stay behind progressive disclosure.
2. **Compare Here** — user is already in a store; barcode/camera/OCR compares visible products/package sizes/promotions with exact unit-value math and immediate benefit even when cross-store coverage is incomplete.
3. **Watch My Staples** — notify only when switching stores is practically worthwhile for recurring items/baskets; no penny-saving spam.
4. **Receipt/import** — optional user-benefit feature for list reconstruction/spending/history/comparison. Receipt crowdsourcing is NOT the core data model.

For every UI change: one obvious primary action; strong defaults; minimal fields/cards/buttons; explicit loading/empty/error/unknown states; no UI jumping; bounded work; smooth realistic item counts; no claim stronger than evidence.

## Permanent architecture

ValuePilot is provider-neutral shopping intelligence. Accessibility, OCR, overlays, browser capture, camera capture and retailer-specific extraction are optional adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic decision/ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; they never overwrite one shared truth row.
- Acceptance != factual conflict resolution.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks authoritative comparison.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain/suggest candidates but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect organic ranking.
- Feed/technical access != publisher authorization != offer geography != production-use rights.
- Dataset recency != per-offer freshness; currency != geography.
- Dataset namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- Shared core owns no hidden clock/network/UI/provider credentials.
- Android/UI renders immutable state and owns no provider/ranking business logic.
- Bound/coalesce/cancel/measure expensive work; no unbounded scans, queues, caches, requests or rendered rows.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow, not a primary tab. Built-in Search remains fictional/sample evidence and must never be represented as live merchant price, inventory, promotion or availability.

## Free/open data direction

There is no known legitimate free complete dataset of fresh store-specific prices across all Canadian grocery retailers. Do not pretend otherwise and do not build the commercial foundation on unauthorized scraping/private endpoints/anti-bot circumvention.

Potential source-isolated rails where licences/terms permit:

- Open Food Facts — broad packaged-product recognition/metadata; prefer bulk import for scale.
- USDA FoodData Central — public-domain/CC0 branded-food enrichment; never treat US market-country data as proof of Canadian availability.
- validated produce/PLU source — loose-produce identity/category where licensing permits.
- Open Prices — supplemental proof-backed observed/historical prices, not nationwide current-price coverage.
- OpenStreetMap — store/location/routing foundation where appropriate.
- user evidence adapters — barcode, shelf label, camera, product-page share, digital receipt/order evidence; immediate user utility first, contribution second.
- merchant self-service later — stores submit CSV/feed/API because inclusion can send customers.
- authorized commercial/provider/affiliate feeds — higher-authority accelerators only when rights/geography/freshness pass gates.

Keep ODbL/share-alike sources in source-isolated namespaces and do not casually merge them into an incompatible proprietary master database. Resolve evidence through the existing provenance architecture.

Launch depth before breadth: one dense metro area and roughly 1,500–5,000 high-frequency grocery/household concepts for excellent practical coverage, while millions of open identities remain backend recognition rather than a vanity KPI.

## Verified production chain — keep it

Core provider/evidence boundaries:

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral current/reference price relationship.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separated from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed geography; CAD is not Canada proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate.
- `e546822a448e150674a2769d9899a856124b50fb` — exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time raw-evidence production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product keys.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — current-price factual-conflict eligibility.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — production price + conflict-resolved package quantity -> exact unit value.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — immutable point-in-time production presentation.

Android/application boundaries:

- `617dc128c9df31bbbd2b1835ac243be93e511d97` — exact production Search projector.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic raw evidence -> presentation -> Android projection regression.
- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` — internal scope keys removed from UI-ready text.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` — raw provider URLs removed from catalog-only UI-ready state.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` — blocker enum codes removed from consumer blocked UI state.
- `7046b725291cc061f1e153e05c4a25539838a28e` — generation-based stale/out-of-order refresh protection.
- `3cba4d96eb91e961772dee3c60d86371e61b1137` — independent 128-candidate UI projection ceiling.
- `8520af97695fb346710649a80c6d95d422da3ee8` — production Search host/renderer separation.
- `e2767b31512d8d5b6b9cd7555d452ae3ec0017e2` — inactive-by-default production renderer.
- `c4661993f9c476e3a44c7d1dc504948e50767d48` — hidden physical production Search view in `activity_shell.xml`.
- `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` — display submission re-runs production evaluation from raw inputs/current lifecycle instead of trusting a detached snapshot.

Minimum known durable checkpoint before the 2026-08-29 strategy documentation commits: `deaa478c9b679f8820b47a80d7f43c5b18787677`, whose parent is verified code commit `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f`. Always inspect the current branch tip before new work.

Recent exact workflows passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

A temporary empty `__noop__` connector artifact created during the hidden-layout update was removed immediately; do not resurrect it.

## Production Search boundary now

Production arithmetic remains `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never convert verified production evidence back into legacy `Double` ranking.

`ProductionBestValueRankingEvaluator` is bounded to 128 price requests, 128 quantity candidates and 128 ranking candidates. Exact same `(currencyCode, RateUnit)` only; exact ties co-rank; singleton groups do not claim Best Value; provider/affiliate economics absent.

`ProductionBestValuePresentationEvaluator` re-runs ranking from raw evidence at the supplied instant. Presentation snapshots are point-in-time and not durable authorization tokens.

`ProductionSearchUiProjector` formats exact UI-ready values and independently caps rendered ranked+blocked candidates at 128. Consumer UI excludes internal merchant/location/channel keys, raw provider URLs and blocker enum codes.

`ProductionSearchRefreshGate` uses caller-supplied generations and owns no clock/I/O.

`ProductionSearchSurfaceHost.evaluateAndApply(...)` re-runs production presentation from raw bounded inputs/current registries before rendering.

`ProductionSearchSurfaceView` is physically present but starts `GONE`, saves no state, has no link/click/provider behavior and renders sanitized state only.

**MainActivity still does not wire or drive the separate production Best Value
surface described above.** The current visible Search is the M12.4B live,
identity-only Argentina Search, not the old fictional/sample path. Keep its
identity results separate from current-price/Best Value authority; do not route
them through `UniversalSearchController.receive()`, `DeterministicProductParser`,
`ValueEngine.analyze()`, `RankingModePolicy`, `DeterministicRankingEngine` or
`ValueEngine.rank()`.

## Rakuten / Jamieson — updated 2026-08-29

Rakuten Product Catalog technical access is enabled. Jamieson partnership, separate advertiser Product Feed approval and actual complete catalog-file access are proven. Proprietary feed data stays outside source control.

Sanitized feed checkpoint: 273 rows; 273 unique SKUs/Product IDs; GTIN present 271/273 and all supplied GTINs checksum-valid; 273 CAD; 273 in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all; Attribute 1 opaque.

Generic Rakuten semantics remain resolved: Sale Price reflects discounts and Retail Price does not. Sale>Retail is a semantic conflict; never swap/repair/infer promotion from those rows.

On 2026-08-29 Rakuten Publisher Support answered the production-use clarification materially:

- Product Catalog files may be downloaded with any FTP client.
- Once downloaded, product data/links may be used in the publisher's system for comparison, but application use requires confirmation from the relevant advertiser.
- Comparison use likewise requires advertiser confirmation.
- If partnership ends, product links become inaccessible while downloaded feed files remain saved in the publisher's system. Physical retention does not itself prove continuing display/use rights.
- Android affiliate-link use requires advertiser permission/terms.

Therefore Rakuten-side storage/comparison ambiguity is substantially reduced. Advertiser-specific Android/application display/comparison permission is the primary rights gate; post-partnership file retention must remain distinct from continued display/use authorization.

A Jamieson permission email has already been sent asking for Android display, search/comparison, cache/index/storage, affiliate-link use, retention/deletion requirements and confirmation that the approved feed represents Canadian consumer products/offers. **Do not resend while waiting unless the reply is incomplete or creates a new question.**

Rakuten Product Catalog file/update timestamps remain dataset/source recency, not trustworthy per-product current-price observation timestamps.

Jamieson is useful supplemental evidence but is no longer a launch dependency under the practical-shopping strategy.

## Walmart Canada

Rakuten Walmart Canada MID 36751 currently shows an advertiser-supplied eligibility pre-filter that prevents application. Do not falsify ValuePilot/account/channel details to bypass it. If Walmart is pursued later, ask Rakuten which exact eligibility condition fails. Walmart is not a core launch blocker.

## Package quantity / GS1 / open data

Valid normalized Jamieson × Open Food Facts result remains: 273 products; 271 valid GTINs; 102 matches; 169 unmatched; 12 exact supplement-count candidates; 2 structured mass/volume-only; 0 quantity conflicts; 88 matched without usable quantity. OFF is supplemental; never infer package count from Rakuten title/description/opaque attributes/images.

Health Canada LNHPD does not solve GTIN-level package count.

GS1 Canada ECCnet inquiry was sent and acknowledged 2026-08-28; no substantive eligibility/rights response yet unless newer evidence exists. GS1 is a potential high-quality package-content accelerator, not a launch dependency.

## Android privacy boundary

The paragraph below records the pre-M12.3 offline checkpoint. M12.3 is the
separately authorized exception recorded above: Android now intentionally has
`INTERNET` for a bounded, App-Check-protected public edge route, while
`ACCESS_NETWORK_STATE` remains absent. M12.4B Search is explicitly
user-triggered and identity-only; Home and the separate production Best Value
surface remain offline/inactive. There is no background sync, account,
telemetry, remote AI dependency, private-backend access, or server credential.

Do not silently expand this boundary. Any future live-surface or server change
requires an explicit milestone with privacy, security, offline and failure-mode
design.

## Immediate next work

The promoted Home first-value action placement slice now makes Scan & compare and Good Price discoverable directly beneath Plan my shop; physical-device validation should confirm that order, focus and accessibility behavior.

Do not keep adding speculative Android production/provider plumbing.

The Practical Shopping MVP fixture/controller, one-store planner/projector, Home/Basket/Saved surfaces, Watch setup/policy presentation, and required consumer correction flows are implemented at the promoted head. Home's latest convenience slice adds clearly labelled fictional offline `Try a sample item` chips for bounded text-only list entry; the existing missing-price handoff still opens Good Price with a bounded untrusted name prefill, while exact package quantity and observed price remain required and private.

The currently identified unblocked Home presentation gaps are implemented through the promoted result-reveal and quick-add slices. The next meaningful gate is physical-device validation of Home quick-add/scroll/keyboard, camera/OCR and accessibility behavior; after that, only another direct local consumer benefit should be added. Keep any production assembly behind explicitly selected identities, signed location-directory rows, private observed-price evidence and travel facts; do not infer live offers from identity or location data, and do not activate production Home or add provider plumbing without explicit lawful rights. Keep the following acceptance constraints active:

## Argentina consumer input intelligence V1 checkpoint (2026-09-08)

The bounded provider-neutral consumer input layer is implemented on the
candidate code commit `9515fb4d2186f637e8dbfe8bdd16747ef08d5e2f`. It adds
deterministic Spanish/English normalization, quantities, aliases, brands,
bounded typo correction, product-form safety and clarification gates above the
existing M6/M7 engine. The backend exposes `/v1/interpret` and `/v1/shop-text`
without changing Android networking or shipping provider data to the APK.

The 3,000-case frozen mutation fixture, high-risk substitution checks,
catalog/search benchmark and exact backend contracts passed. Warm 5,000-record
fixture timings were 5.027 ms for one line and 148.482 ms for ten lines. The
completed M5/M6/M7 verification evidence and 128 logical / 32 physical mobile
winner were reused; no national ZIP verifier or source-wide diagnostics rerun
was performed.

Candidate CI is green: backend run `34248134778`; Android/browser/release,
privacy and signer run `34248134741`. The next action is only fast-forwarding
this already-proven code to `work/valuepilot-android-milestone`, verifying the
promoted exact-head workflow, and then stopping. No further milestone is
authorized by this checkpoint.

1. Inspect current Home/Search/Basket/Saved shell and immutable UI-state boundaries before changing them.
2. Reuse existing exact money/quantity/value components; never duplicate arithmetic.
3. Preserve one-store-first and explicit second-stop savings/travel rules; never hide inconvenience in a score.
4. Keep fictional/sample data visibly offline and unknown prices unknown.
5. Keep Views as immutable renderers with typed actions; no Android networking, persistent overlay foundation, or provider economics in organic ranking.
6. Add deterministic failure/unknown/stale/conflict/bounds tests with each relevant slice, then run the full Android/browser/privacy/signature/release verification.

No user/device action is required for this documentation checkpoint.
