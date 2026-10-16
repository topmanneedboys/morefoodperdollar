# Catalog discovery implementation progress

Updated: 2026-10-15

This ledger records the additive discovery work. It is not a claim that the
catalog has perfect semantic coverage. Existing `/v1` shopping, price, offer,
saved-data, release, and security paths remain unchanged.

## Checkpoint

- Branch: `work/valuepilot-stage2-premium-shopping`
- Starting handoff checkpoint: `6ce81d0d`
- Current verified checkpoint: code commit `32adbecf` (documentation checkpoint follows the verification update).
- Discovery rules: `valuepilot-catalog-discovery-rules-v4`
- Sidecar schema: `valuepilot-catalog-discovery-v2`
- Additive HTTP contract: `valuepilot-discovery-http-v2`
- Current state: deterministic identity index, strict query/facet engine, and
  capability-gated backend/Kotlin contract are implemented locally.

## Process accounting

The pinned source contains 134,337 retailer-scoped identity records and 78,865
distinct provider product IDs. The compiler visited source identities, preserves
their qualified keys and names, groups only by the existing provider ID, and
stores a coverage row and literal token posting for every identity. The full
release build is run as a local ignored artifact; its report is recorded after
the build completes.

Each identity receives one of these dispositions in the sidecar:

- accepted type/family assertions when an existing reviewed rule has direct or
  derived evidence;
- accepted form and department postings when the source rule supplies them;
- literal-only searchable coverage with explicit unresolved reason codes when
  no reviewed semantic rule applies.

Unknown meaning is not filled by a guessed translation. New source rows use the
same deterministic rules on the next rebuild. Rule changes require a new rules
version and replay of affected identities.

The current local release build reported:

- `providerProducts`: 78,865
- `identityRecords`: 134,337
- `indexedTokens`: 36,423
- `concepts`: 195
- `assertions`: 178,583
- `TYPE_RESOLVED`: 58,973
- `TYPE_UNRESOLVED`: 75,364
- `TYPE_OR_DEPARTMENT_RESOLVED`: 87,462
- `DEPARTMENT_RESOLVED`: 85,249
- `ROLE_RESOLVED`: 59,859
- `DERIVED_ONLY`: 886
- `NO_FORM`: 127,794
- English retrieval-path proxy: 87,530 identities
- Spanish retrieval-path proxy: 109,490 identities
- fully type-resolved provider groups: 25,576; partly resolved: 8,131; unresolved: 45,158
- fully type-or-department-resolved provider groups: 44,532; partly resolved: 8,697; unresolved: 25,636
- source SHA-256: `85033a863ea95fea8be7ea73c31577987f09d3533f4c5aab70321d7a945877a6`
- ignored sidecar SHA-256: `ac36b0efec197cbc33f63c15aafeeb1f2bf607f9cc060c58ef55480ecce0a41e`

The v4 replay combines the existing reviewed family dictionary with
`tools/data/discovery_family_rules.json`. It adds evidence-backed vocabulary for
common Argentina grocery, personal-care, household, apparel, electronics, pet,
and toy families while preserving the older high-risk family rules and their
derived-context safeguards. It also seeds identical ES/EN spellings in both
language paths and hides derived mentions when a bare type query has no safe
clarification facet. The bilingual counts above include accepted department
paths; they are retrieval-path proxies, not proof that every name has a reviewed
English translation.

The report and sidecar stay in `local-provider-data`, which is ignored by Git.
Those numbers are engineering coverage measurements. They do not mean that
the unresolved identities have known product meanings.

## Query gates implemented

- Full sidecar postings are evaluated before paging; the old 31-row v1 window
  is not used by the v2 identity rail.
- Stable predicate IDs are returned for clarification choices.
- ES and EN reviewed aliases resolve to the same predicate IDs.
- Choice counts are calculated from the complete candidate set and are bounded
  only for transport pagination.
- Explicit selections intersect postings strictly; an empty result stays empty.
- Product pages have signed query-bound cursors and support more than 31 rows.
- The v2 response contains identity evidence only; no prices, offers, stock,
  availability, or invented geography are added.

## Verification ledger

Completed locally:

- synthetic full-set, bilingual, strict-selection, literal-only, and pagination
  tests;
- unresolved mention versus explicit type-selection tests and typed context
  relation evidence tests;
- additive `/v2/search` backend route tests, including disabled capability and
  price/offer exclusion;
- Kotlin contract/bridge parser tests and the integrated Android unit-test task.

Completed in this checkpoint:

- full tools suite: 385 tests passed;
- backend suite: 135 tests passed;
- Android shared-core tests, app debug unit tests, lint, and debug APK build passed;
- debug APK SHA-256: `027736566b64f1a0e21e3fcb6f475b0a862633e06a88caa8a16d4c8166cd7a4c`.
- live sidecar spot checks passed: Spanish `whisky`, `vodka`, and `gin` resolve to exact type predicates; `rice` exposes grain/dessert/flour choices; derived feminine-hygiene mentions are suppressed from ready `toalla` results.

Pending after the local full build:

- incremental refresh parity against a complete rebuild;
- authorized Motorola validation;
- semantic review of unresolved records and source evidence gaps.

The current build has not been promoted to the backend or preview runtime. The
remaining 75,364 identity-level type-unresolved rows and 45,158 provider groups
without full type coverage are still open semantic work; literal reachability and
department coverage do not close that gate.

The semantic gate remains open until every unresolved record has reviewed
evidence or is explicitly documented as unresolved. Engineering completion and
device validation are separate gates.
