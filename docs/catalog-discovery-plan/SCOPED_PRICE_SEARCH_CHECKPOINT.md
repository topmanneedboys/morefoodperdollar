# Scoped full-result price search — development checkpoint

Status: backend implementation verified with automated fixtures; **not deployed or launch ready**.

## Implemented

- Additive `POST /v2/search` accepts a complete latitude/longitude/radius scope, `sort` (`name`, `pack`, `unit`, `distance`), optional exact `storeKey` and `unitBasis`.
- Existing identity-only requests and `/v1/search` behavior remain supported.
- The catalog resolves the entire strict selected identity set before any product page is read. No lexical top-31 limit is reused for this path.
- Exact SearchPack keys are read in batches. Docstore blocks and shared offer partitions are coalesced; no separate offer request is made for each product.
- Prices come from the existing verified offer reader and exact quantity/money engine. Stale, conflicting, out-of-radius, wrong-currency, invalid-price and duplicate evidence is excluded.
- Full matching identities are ordered by their best eligible offer before pagination. Unit-price ordering uses exact rational arithmetic over structured package quantities, independently of the rounded display value. Unknown rates follow known rates. Incomparable unit bases require a selection.
- Store choices cover the complete joined offer set, ordered by distance, rather than stores in the loaded page.
- Product cursors bind query, language, selections, source/index, release, coordinates, radius, sort, store, unit basis and page size. Invalid cursors fail before offer reads.
- Current-release authorization and exact catalog/price-release equality are required. The frozen October 8 sidecar cannot inherit October 14 prices.

## Evidence

- Full backend suite: 157 tests passed.
- Catalog/SearchPack suites: 45 tests passed.
- Final focused scoped-search suite after additional cursor/price guards: 10 tests passed.
- Regression: all 300 matching identities considered, cheapest originally last appears first, complete unique global order across all pages, and a store filter beyond the first page returns its exact product.
- Reader regression: 300 exact identities share one offer-partition read; stale date, mismatched quantity, far-away store, wrong currency, negative price and duplicate offer are excluded.
- SearchPack regression: batched exact keys decode a shared docstore block once; wrong-key records are rejected.
- No new APK built or installed. ADB currently lists no connected device.

## Remaining requirements

1. Build and qualify a discovery artifact from the current active source release; do not relabel frozen v40. Integrate release-bound artifact creation into future official imports.
2. Benchmark real cold/warm full-result offer joining against public-edge deadlines. Each request currently recomputes the complete ordering; a qualified immutable price projection/cache is still needed if measurements exceed budgets.
3. Resource limits fail the entire request (`DISCOVERY_SCOPE_TOO_LARGE`) instead of falsely claiming complete results. The current implementation allows at most 100,000 eligible offers and limits per-region partition reads to 128 MiB compressed/512 MiB expanded. Broad-query support beyond these limits needs an indexed projection, not raised arbitrary limits.
4. Clarification counts still describe catalog membership before geography/store filtering and are explicitly marked as such. Integrate geographic eligibility into facets if the customer expects local offer counts.
5. Connect Android/HTML location selection, sort/filter requests and qualified offer parsing. The current preview still uses its fixed CABA context and page-local sorting.
6. The response includes up to eight ordered offers per identity plus a complete eligible count; it is not a fully paginated flat table of every retailer offer. Table-level offer pagination remains necessary for full offer-row parity.
7. Deploy only after qualification through existing App Check/IAM boundaries, then verify actual Motorola interactions. Backend fixture tests alone are insufficient.
8. Restore authorized acquisition access. A payment notice located during account recovery identifies a different operational account from the two free accounts checked. User sign-in/billing recovery is pending; no credentials, financial links, or payment details are committed here.

The active national price release remains October 14 across 24 reporting regions. This checkpoint changes development code only and does not establish unattended acquisition success, complete semantic understanding, full UI integration or national launch readiness.
