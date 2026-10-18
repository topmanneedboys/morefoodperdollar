# Nationwide search readiness — 2026-10-17

Status: **NOT LAUNCH READY**. The target is the complete catalog and all supported
Argentine regions, with real evidence and sorting over the entire matching set.
No single example query, literal-retrieval result, or populated table satisfies
that target. The October 14 national price release has been activated in both runtime regions. No Play publication was performed.

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

**The app requirement is not yet complete.** A new additive backend path now joins and orders the full selected identity set before paging; see `SCOPED_PRICE_SEARCH_CHECKPOINT.md` for evidence and limits. It is not deployed or connected to the app. The current HTML sort
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

The v40 sidecar and identity-only `/v2/search` responses contain **no prices or offers**. Explicit scoped requests now support a separately authorized exact offer join, but require a catalog built for the same current release. The
legacy `/v1/search` exposes a bounded nearest-store preview and caps discovery at
31 candidates, so it cannot supply a global sort across 300 products either.
A complete release-bound offer join and sorting projection remains necessary;
raising client row limits or sorting accumulated preview rows is insufficient.
Do not change the existing monetary calculations to implement this projection.

## Current national live evidence

The October 14 official release is now ACTIVE in both US and South America runtime regions, replacing October 8. Its manifest contains 24 regions, 2,935 reporting stores, 14,429,267 offers, 1,411,208 regional product-evidence records and 1,242,658 promotions. These are reporting-source counts, not every Argentine store or unique product counts.

Both backends returned FRESH and authorized networking/UI status after immutable staging, pointer activation and IAM smoke checks. Real source-store coordinate requests passed fresh response/routing checks for all 24 regions. The first rice page contained listed prices in 22 regions; two bounded pages had no price evidence. Some responses included unexpected additional regions, so geographic accuracy is not fully certified. This operator test does not certify customer App Check or Motorola behavior.

### Root causes repaired

PR 52, merged as `db7604d6e660724022a289504995b40ff78e20d0`, repairs overnight execution recovery, terminal pending-pointer rotation, double-encoded Cloud Workflows results, activation rollback metadata and hourly reconciliation. Source checks now include weekends. The existing release service account received object-update permission restricted to the single archive `control/pending-workflow.json` object. No public access or App Check bypass was introduced.

The October 14 build had already succeeded but remained unactivated. Today's metadata masked its older journal, and an October 6 global reservation could not rotate. The reconciler now follows unfinished work across days rather than ignoring it.

### Download recovery verified; new release building

After operational account recovery, Windscribe shows an ACTIVE custom plan through November 17, 2026. The existing stored profile recovered without replacement. GitHub acquisition run `39070503910` passed the real WireGuard handshake, downloaded and verified the official October 17 ZIP (323,721,080 bytes, SHA-256 `9cc1987ea562484090b1d6d63eaddc93d185d26b70fa2b0f57730278a2396e82`), archived it and launched the national build.

The exact Cloud Workflow execution is `ae58706b-4069-46b2-bcd8-9da1b8703746` (ACTIVE), with Cloud Build `8c24715e-788a-4f51-a24b-6a997b35e9b3` (WORKING). Candidate: `argentina-sepa-2026-10-17-9cc1987ea5624840-routing-v1-search-v2`. It is not yet qualified/active. October 14 remains the last verified active release.

A configuration mismatch was also corrected: the repository automation variable was true while the production environment value was false. Both now explicitly read true, and the GitHub workflow is active. Daily checks include weekends; hourly reconciliation follows completed builds across day boundaries and uses existing promotion/smoke/rollback gates. A successful future scheduled activation must still be observed; enabled configuration is not proof of that future outcome. No duplicate cloud scheduler was introduced.

### Remaining engineering work

The deployed backend has no active v2 sidecar. The edge forwarding adapter repair from development commit `116939d4` is not deployed. The frozen October 8 v40 identity candidate must not be relabeled as October 14. Complete release-bound offer joining, full-result sorting, national location selection and semantic acceptance remain necessary. Backend freshness recovery alone does not make the complete preview launch ready.

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
