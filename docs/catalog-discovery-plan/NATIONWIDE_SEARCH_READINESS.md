# Nationwide search readiness — 2026-10-17

Status: **NOT LAUNCH READY**. The target is the complete catalog and all supported
Argentine regions, with real evidence and sorting over the entire matching set.
No single example query, literal-retrieval result, or populated table satisfies
that target. No production deployment or Play publication was performed here.

## What the complete product study establishes

`CATALOG_READINESS_STUDY.json` accounts for all **78,865 provider products** and
**134,337 exact retailer identities** in the frozen v40 candidate. The associated
compressed inventory has one record per provider product, including every source
name, identity key, assigned type/form, and unresolved status.

| Existing assignment coverage by provider product | Count |
| --- | ---: |
| All retailer identities have an assigned product type | 30,549 |
| Some retailer identities have an assigned product type | 8,016 |
| No retailer identity has an assigned product type | 40,300 |
| Total provider products inventoried | 78,865 |

An assignment is not independently established semantic truth. There are still
65,967 untyped identity records and zero completed independent semantic review
cases. Only 5,128 provider groups have form evidence. Frequency alone cannot
establish that a product is fruit, a drink, a toy, or a translated English term.

The unresolved inventory includes clothing abbreviations, televisions, books,
phones, household goods, and foods. This is a general retail catalog; treating
every unresolved item as a grocery would create incorrect clarification choices.
The report's observed prefixes are candidates for shared rules and review, not
newly accepted categories. The entire source remains accounted for and searchable
by literal identity while semantic gaps are repaired.

## Shared grouping decision

Use the existing typed concept graph and complete source postings. Do not build
78,865 individual dialogs or special cases for pineapple, eggs, or rice.

1. Establish types and relationships from source names and reliable supplier
   metadata. Keep direct type, ingredient, flavour, scent, and toy theme distinct.
2. Maintain bilingual aliases for accepted concepts, with evidence for meaning
   and translations. Keep exact product identity and package data unchanged.
3. For a query, derive choices from nonempty intersections between the complete
   eligible matching set and accepted type/form postings. The choices and counts
   precede product pagination. Multiple meaningful groups produce clarification;
   a unique exact product need not produce an unnecessary dialog.
4. Apply the chosen predicates strictly. Unknown meanings stay available through
   literal search without entering a confirmed type/form selection automatically.
5. Process every import through the same versioned rules and graph. New instances
   of known combinations create groups automatically. A genuinely unknown meaning
   needs evidence and review; it cannot safely become a new category solely from
   repeated words. Reprocess affected unresolved names when rules change.

This is the existing engineering specification's direction, applied to the whole
inventory. The new audit identifies the size and location of the remaining work;
it does not silently promote unreviewed labels or start another small rebuild.

## Price sorting requirement

**The customer's requirement is not yet implemented.** The current HTML sort
operates on a loaded page and now labels that scope explicitly. That must not be
reported as a global lowest-price result.

For a search with 300 matching egg products and 20 visible rows, the correct order
is: resolve the whole matching set, apply selected product type and geographic /
store filters, join authorized current offers by exact identity, calculate
comparable unit prices using the existing engine, sort all eligible rows, then
return the first 20. A cheap product originally at position 300 must reach page
one. Null prices remain unknown and must not become zero. Different currency or
unit dimensions must not be silently compared.

The cursor must bind release, identity index, query, accumulated selections,
geographic scope, store filter, sort direction, and unit basis. Later pages must
follow the same global order with deterministic identity/offer tie breakers.
Changing any filter starts a new result set. A store filter must cover all
eligible offers, not just stores in the loaded page.

The v40 sidecar and `/v2/search` currently contain **no prices or offers**. The
legacy `/v1/search` exposes a bounded nearest-store preview and caps discovery at
31 candidates, so it cannot supply a global sort across 300 products either.
A complete release-bound offer join and sorting projection remains necessary;
raising client row limits or sorting accumulated preview rows is insufficient.
Do not change the existing monetary calculations to implement this projection.

## Current national live evidence

Read-only Cloud Run and GCS checks on 2026-10-17 found:

- Active release: `argentina-sepa-2026-10-08-153e9d9f7ab4b87e-routing-v1-search-v2`.
- Manifest: 24 regions, 2,602 source-listed stores, 14,080,931 offers and 1,393,362
  regional product-evidence records. Regional record counts are not unique
  provider product counts and are not a claim to cover every Argentine store.
- The private backend's actual `/v1/status` returned `freshness: STALE`,
  `androidNetworkingAuthorized: false`, `productionAndroidUiAuthorized: false`,
  and `automatedOfficialAcquisitionProven: false`.
- The configured release is nine days old; the established freshness limit is
  seven days. Deployment authorization flags being configured true does not make
  stale data eligible.
- Both the official metadata endpoint and the official Saturday ZIP endpoint
  returned HTTP 403 on this machine. No fresh release was acquired or published.
- The deployed backend has no configured v2 sidecar activation. v40 remains a
  frozen, unpromoted local candidate; canonical v33 remains unchanged.
- Native preview search still uses the qualified CABA launch context. National
  launch needs an explicit user-selected place / location context and geographic
  scope passed consistently through clarification, counts, offers and cursors.

The public edge already allowlisted `/v2/search`, but its Cloud Run forwarding
adapter had omitted that route. This checkpoint repairs the adapter and verifies
App Check → OIDC forwarding, including rejection before forwarding when App Check
is absent. This code repair has **not been deployed**. It does not by itself
activate v40, refresh prices, or authorize a stale release.

Fresh authorized official source access is the external blocker to current live
prices. The offer join/global sort, national location UI, full semantic coverage,
and independent acceptance review are remaining engineering/review work. They
must not be described as an App Check issue or cleared by weakening freshness.

## Verified interaction work

- Actual-name engine audit: 247,384 bilingual-setting queries and 248,690 pages;
  all 134,337 exact identities retrieved, zero failures. These are literal source
  names in both settings, not proof of every English synonym.
- Discovery now keeps one bounded 50-row page with next and previous controls.
  A regression traverses 10,500 unique rows, then returns backward and forward.
- Reading more clarification choices preserves the current product page/cursor.
- Browser tests render actual read-only v40 responses through a test Android host
  adapter. They cover bilingual queries, strict rice selection, more than six
  observed choices, product pagination, exact detail/photo identity and return
  viewport. They do not certify Android Custom Tab expansion.
- The table no longer calls price-free identity rows "supported store offers".
- Android: 1,851 app and 405 shared-core tests passed; lint and debug build passed.
  Backend: 147 tests passed, including the real forwarding adapter boundary.
- ADB reported no connected device. Motorola interactions and screenshots remain
  unverified. Browser JPGs are clearly desktop evidence, not phone captures.

The frozen source/index hashes and compiler version remain unchanged. Do not
qualify this as full feature parity or a launch-ready national search release.
