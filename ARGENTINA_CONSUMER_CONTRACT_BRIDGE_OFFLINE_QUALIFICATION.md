# Argentina consumer contract bridge (M12.0)

Status: `CANDIDATE_PENDING_VERIFICATION`

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

Qualification is complete only after the repository's full Android/shared-core
tests, lint, debug/release builds, manifest/privacy/signer checks, Python
discovery tests, browser checks, and exact-head GitHub Actions verification are
green. M12.0 qualification does not mean live Android networking, cloud edge
qualification, or production launch readiness.
