# Argentina consumer contract bridge (M12.0)

Status: `QUALIFIED_FOR_OFFLINE_CONSUMER_CONTRACT_ONLY`

This bounded milestone adds the offline Android boundary for the already
qualified M11 Argentina backend contract. It does not activate Android
networking, add a Firebase dependency, contact Cloud Run, or authorize the
production Argentina UI.

## What is included

- A strict, bounded DTO parser for `/v1/status`, `/v1/search`,
  `/v1/interpret`, `/v1/shop-text`, and `/v1/shop`.
- A provider-neutral fake transport seam. The only implementation in this
  milestone is an in-memory response transport used by deterministic tests.
- Request-size, response-size, JSON-depth, text, array, candidate, plan,
  store, promotion, and diagnostic bounds.
- Fail-closed handling for unsupported versions, release mismatch, malformed
  money/currency, invalid GTIN checksum, stale/current-price-unavailable
  responses, illegal availability, conflicting offer evidence, and unsafe
  clarification state.
- Identity-only Search presentation with generation ordering. Search identities
  never imply price, stock, package quantity, store availability, or freshness.
- A display-only mapper into the existing demo-free production Home state. It
  preserves exact backend money text, release/source evidence, unknown
  availability, missing-price coverage, and straight-line-only distance
  wording. It does not invoke the shared planner a second time.

Two-store backend results remain explicitly unavailable until the existing
Home/Basket surfaces have a dedicated dual-stop presentation. They are not
collapsed into a misleading one-store result.

## Preserved boundaries

- Android remains offline and has no `INTERNET` or `ACCESS_NETWORK_STATE`
  permission.
- No HTTP client, URL, credential, API key, service account, or provider raw
  data is bundled.
- SEPA-specific parsing and backend authority stay outside `android/shared-core`.
- Availability remains `UNKNOWN`; a published price is never treated as shelf
  inventory. Promotions retain their eligibility and do not silently change
  the base total.
- The existing shared-core planner and production Home adapter remain the
  authority for their own evidence path. This bridge only parses and presents
  an already-computed backend decision.

## Deterministic test coverage

The focused Android suite covers valid fake status/search/interpret/shop
responses, one/five/ten-line baskets, clarification-required shop-text,
empty coverage, stale evidence, HTTP current-price unavailability, two-store
disclosure, unsupported version, malformed/missing fields, ARS enforcement,
UNKNOWN availability, promotion retention, invalid GTIN, conflicting duplicate
offer evidence, response bounds, release mismatch, and stale-generation
ordering. Fixtures are generated in memory and require no network, credentials,
or private release files.

Qualification completed at candidate HEAD
`48b1d747125d656b3cf0ec77603820c1390e7aae`. Local verification passed 405
shared-core tests, 1,630 Android unit tests, 11 backend tests, 197 tooling
tests, and 30 browser tests, with zero failures or skips; Android lint, debug
and release assembly, APK privacy, and one-signer verification also passed.
Exact-head GitHub Actions run [35053280023](https://github.com/topmanneedboys/morefoodperdollar/actions/runs/35053280023)
passed Android job 104658056547 (including privacy/signature), browser job
104658056705, and release-bundle job 104659354704. The initial run
35053203937 failed before tests because the hosted SDK rejected the retired
`tools` package; the workflow now requests only `platform-tools` and the fixed
run is the qualification evidence. M12.0 qualification does not mean live
Android networking, cloud edge qualification, or production launch readiness.
