# ValuePilot M12.4–M12.9 work ledger

This is the single bounded ledger for the continuation goal. It distinguishes
implemented code from independently verified live behaviour. It is intentionally
short and is updated only with a substantial engineering checkpoint.

Latest checkpoint: 2026-09-27 UTC, merged main `7d94438246dabd7611afb62234930164949f70e5`.
The live Cloud Workflow execution `c46e8991-4927-4737-b6ae-5af7a2818029`
reached its candidate build but returned `STATE_WRITE_UNVERIFIED` because a
legacy discovery-state object used the literal encoded key
`control%2Fworkflow-discovery-state.json`. Its schema and bytes were verified;
the exact record was copied to the canonical `control/workflow-discovery-state.json`
key, and only the malformed duplicate was removed with its generation
precondition. The control bucket's seven-day soft-delete policy remains in
effect. Runtime `control/active.json` was not changed and still points to the
qualified 2026-09-25 release. A future scheduled run is still required to prove
healthy end-to-end SHADOW reconciliation.

### Preserved Motorola audit evidence reviewed — 2026-09-27

The preserved, untracked `device-audit/` directory was inspected read-only. Its
September 24 trace is from the pre-continuation Android candidate: the XML
hierarchies identify `com.valuepilot.app`, the qualified Buenos Aires (CABA)
Search area, and source-attributed Argentina SEPA identity cards. The milk
trace contains five distinct identity cards across the captured scroll states;
the yerba trace likewise contains five distinct identity cards. No captured
hierarchy contains the later `searchLoadMoreButton`, `hasMore`, or bounded-limit
announcement, so this evidence must not be used to claim Search beyond five
results. The current source-level continuation implementation and its fixture
tests remain the authoritative pagination evidence until a current signed
build is exercised with valid App Check.

The same audit visibly exercised the offline/private product flows: Data Status
reported 30,000 identities, 6,093 source-listed stores, zero authorized current
offers and no copied flyers; Home and Basket kept the fictional sample plan
labelled; Saved showed local-only empty state; Compare required like-for-like
confirmation and explicit price basis; Good Price rejected an unclear entry;
and large-font/rotation captures showed the main layout remained readable.
These are physical presentation observations only. They do not prove current
offers, stock, App Check, both-region routing, a release signature, or Play
installation of the current 10108 candidate. The audit files remain preserved
and untracked.

| Milestone | Implemented | Independently verified | Still missing | Can be completed now | Requires user or external action |
|---|---|---|---|---|---|
| M12.4 Search | Bounded five-result pages, Load More accumulation capped at 30, deterministic identity deduplication, stale-generation and App Check/backend failure handling, and visible result-count disclosure. | Local coordinator/surface tests cover more than five fixture results, stable ordering, deduplication, both `ar-b`/`ar-caba` diagnostics, and fail-closed authorization. The preserved Motorola trace proves the older CABA identity-card flow but shows only five results and no Load More control. | A current signed/Play-installed request returning more than five results with valid App Check in both regions; physical pagination/accessibility/latency evidence. | Keep the trace gate ready and re-run cheap automated checks after substantive changes. | Unlocked Motorola with the current candidate and a valid App Check path.
| M12.5 acquisition | Scheduled OIDC acquisition, explicit freshness/outage policy, asynchronous SHADOW reconciliation, terminal alerts, idempotent receipts, and guarded write-free success checks. | Both runtime buckets currently point to the same complete qualified 2026-09-25 release and source hash. The latest execution reached Cloud Build and candidate creation, then failed closed at `STATE_WRITE_UNVERIFIED`; the malformed encoded-key state was repaired without pointer writes. | A normal scheduled end-to-end acquisition after the repair that reaches verified candidate/SHADOW success; unattended promotion remains disabled. | Continue read-only freshness/pointer observation; preserve promotion controls. | A future healthy scheduled source run or explicit production-promotion approval; no manual release promotion.
| M12.6 product pictures | Legacy offline audit measured 66,806 identities / 66,716 valid GTIN records / 389 yerba identities and found 21 legacy strict front-image candidates, zero rights-qualified display records. The manifest engine now checks exact identity, all documented image roles/variants, normalized GTINs, explicit licenses, provenance and obligations without downloading bytes; the conservative Open Food Facts rights policy keeps display disabled pending rights review. | Tooling unit tests prove deterministic bounded JSONL output, alternate roles/languages, static/image hosts, and rights rejection. A bounded real sample of 20 catalog identities found 9 API records but 0 strict exact matches and 0 display-eligible images. Official OFF documentation was reviewed. | Full-export remeasurement with the broadened engine, manual representative review, and verified commercial reuse rights/attribution bundle. No 90% claim is justified. | Run the new engine on an exact local export when available; expand only after sample review and rights evidence. | A permitted local OFF export and any necessary rights/attribution decision for app distribution.
| M12.7 details/screens | Identity cards, source attribution, explicit package-size-only-when-supplied disclosure, neutral missing-image state, and Search-to-Home identity handoff exist. | Android unit/source-boundary tests and local lint/build are green. The preserved audit shows readable identity cards, disclosure copy, Data Status, Home/Basket/Saved/Compare states, large-font and rotation layouts. | Current-build accessibility/responsiveness and real image rendering remain unverified; live price/stock detail remains intentionally absent. | Fix only evidence-backed defects found by tests/device review. | Motorola access for current-build physical UX evidence.
| M12.8 basket/Saved/Compare | Existing bounded planner, private observed-price/history flows, Saved/Compare limits and honest unknown/stale/conflict states remain intact; fictional sample planner is disclosed. | Shared-core and Android tests cover deterministic limits and private-memory boundaries. The preserved audit exercises offline Basket, Saved, Compare, barcode and Good Price paths without turning identity data into offers. | A real current-offer basket total and live store availability are not qualified; no live offer feed is authorized. Current-build physical validation remains open. | Improve integration only when exact evidence is available; never turn identity data into price or stock. | Lawful authorized offer source and/or user-confirmed private observations; no provider scraping.
| M12.9 Android candidate | VersionCode 10108 / versionName 101.1.7 unsigned release candidate builds locally with privacy boundary intact. | Local full debug/release test, lint, build and manifest inspection passed; PR #32 exact-head CI passed before merge. | Protected signing, Play Internal Testing upload and physical Motorola validation. | Keep candidate ready and run final checks after code is final. | Protected signing path/Play access and connected unlocked Motorola; no public release without approval.

Current merged main baseline: `7d94438246dabd7611afb62234930164949f70e5`.
The untracked `device-audit/` directory is intentionally preserved.
