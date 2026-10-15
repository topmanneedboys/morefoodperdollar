# Catalog discovery implementation progress

Updated: 2026-10-15

This ledger records the additive discovery work. It is not a claim that the
catalog has perfect semantic coverage. Existing `/v1` shopping, price, offer,
saved-data, release, and security paths remain unchanged.

## Checkpoint

- Branch: `work/valuepilot-stage2-premium-shopping`
- Starting handoff checkpoint: `6ce81d0d`
- Current verified checkpoint: this implementation commit (Git HEAD is reported with the completion record).
- Discovery rules: `valuepilot-catalog-discovery-rules-v1`
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

The completed local release build reported:

- `providerProducts`: 78,865
- `identityRecords`: 134,337
- `indexedTokens`: 36,386
- `concepts`: 80
- `assertions`: 131,164
- `TYPE_RESOLVED`: 23,966
- `TYPE_UNRESOLVED`: 110,371
- `NO_FORM`: 132,693
- English query path available: 25,123 identities
- Spanish query path available: 79,277 identities
- source SHA-256: `85033a863ea95fea8be7ea73c31577987f09d3533f4c5aab70321d7a945877a6`
- ignored sidecar SHA-256: `db54517da5fd74e681117906afa8369d1cffd7b8706dea700a8874b4ee84c95b`

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

Pending after the local full build:

- incremental refresh parity against a complete rebuild;
- integrated Android unit tests/lint and authorized Motorola validation;
- semantic review of unresolved records and source evidence gaps.

The semantic gate remains open until every unresolved record has reviewed
evidence or is explicitly documented as unresolved. Engineering completion and
device validation are separate gates.
