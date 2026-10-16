# Catalog discovery implementation progress

Updated: 2026-10-16

This ledger records the additive discovery work. It is not a claim that the
catalog has perfect semantic coverage. Existing `/v1` shopping, price, offer,
saved-data, release, and security paths remain unchanged.

## Checkpoint

- Branch: `work/valuepilot-stage2-premium-shopping`
- Starting handoff checkpoint: `6ce81d0d`
- Current verified checkpoint: code commit `1967c1f9` (documentation checkpoint follows the verification update).
- Discovery rules: `valuepilot-catalog-discovery-rules-v11`
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
- `concepts`: 215
- `assertions`: 187,548
- `TYPE_RESOLVED`: 65,491
- `TYPE_UNRESOLVED`: 68,846
- `TYPE_OR_DEPARTMENT_RESOLVED`: 90,890
- `DEPARTMENT_RESOLVED`: 85,249
- `ROLE_RESOLVED`: 66,639
- `DERIVED_ONLY`: 1,148
- `NO_FORM`: 127,233
- English retrieval-path proxy: 90,986 identities
- Spanish retrieval-path proxy: 110,930 identities
- fully type-resolved provider groups: 28,757; partly resolved: 7,840; unresolved: 42,268
- fully type-or-department-resolved provider groups: 46,047; partly resolved: 8,225; unresolved: 24,593
- source SHA-256: `85033a863ea95fea8be7ea73c31577987f09d3533f4c5aab70321d7a945877a6`
- ignored sidecar SHA-256: `98a4002855a917acdfe7318fd8c38d1ba1459eeb49a946a0c58662ae9eb6e26a`

The v11 replay combines the existing reviewed family dictionary with
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

## v10 verified checkpoint

- Code checkpoint: `f963a05d41b77fb9b9e5a4b812abfa251b33423c`.
- The v10 rule replay adds reviewed bilingual/Spanish forms and negative
  context for soft drinks, incense, blueberries, pajamas, leggings, underwear,
  swimwear, baby wipes, menstrual care, hair color and hair treatment. It also
  tokenizes punctuation in catalog names so abbreviations such as
  `FEM.SIEMPRE`, `HIG.` and `C/ALAS` participate in reviewed exclusions.
- The current-family evidence wins over stale direct source assertions when the
  reviewed context marks a name as a derived mention. This keeps envases and
  grocery `malla` products out of strict soft-drink/swimwear results.
- Focused identity/index tests: 36 passed. Full tools suite: 388 passed.
  Backend suite: 135 passed. Android shared-core tests, app debug unit tests,
  debug lint and debug APK build passed.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `9be32305b1b866b6807593f117c8f00959a5c0ebd5a956d927a126816235d020`.
- Representative strict results: `gaseo` -> soft drink (392 provider
  products), `sahumerios` -> incense (256), `pijama` -> pajamas (203),
  `traje de bano` -> swimwear (135), `toallitas humedas` -> baby wipes (65),
  `toalla femenina` -> menstrual care (123), and `issue kit` -> hair color
  (439). Known false-positive checks for swimwear, towel and soft-drink
  packaging returned zero.
- `rice` remains a deliberate clarification query with grain, dessert and flour
  choices. Broad ambiguous `short` remains a clothing clarification query.
- Motorola Edge 2025 runtime validation was not run in this checkpoint. The
  sidecar is local/ignored and has not been promoted to the backend or preview.

The v10 residual counts above are historical. Current next work is recorded in
the v11 checkpoint below; do not use this historical paragraph as a current
denominator.

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

- full tools suite: 391 tests passed;
- backend suite: 135 tests passed;
- Android source and APK unchanged; the prior shared-core tests, app debug unit tests, lint, and debug APK build remain the applicable evidence;
- debug APK SHA-256: `027736566b64f1a0e21e3fcb6f475b0a862633e06a88caa8a16d4c8166cd7a4c`.
- live sidecar spot checks passed: `gaseo`, `sahumerios`, `pijama`, `traje de bano`,
  `toallitas humedas`, `toalla femenina`, `issue kit`, `sal fina`, `salt fine`,
  `color perm`, `potato chips`, `deodorant`, `mouthwash` and `alimento perro`
  resolve to reviewed predicates; `rice`, `salt` and `spice` expose observed
  clarification choices; strict negative checks for the new families returned
  zero.

Pending after the local full build:

- incremental refresh parity against a complete rebuild;
- authorized Motorola validation;
- semantic review of unresolved records and source evidence gaps.

The current build has not been promoted to the backend or preview runtime. The
remaining 68,846 identity-level type-unresolved rows, 42,268 provider groups
without full type coverage and 24,593 groups without full type-or-department
coverage are still open semantic work; literal reachability and department
coverage do not close that gate.

The semantic gate remains open until every unresolved record has reviewed
evidence or is explicitly documented as unresolved. Engineering completion and
device validation are separate gates.

## v11 verified checkpoint

- Code checkpoint: `1967c1f936604440cc933975909a9d793843bb5e`.
- The v11 batch adds reviewed Argentina retail abbreviations and bilingual
  search paths for mouthwash, potato chips, salt, spice, air freshener, dog
  food, cat food, hair color, conditioner, shampoo, detergent, soap,
  sunscreen, toothpaste, toothbrush and deodorant.
- Generic JSON family forms now seed composed ES/EN aliases and labels. The
  full-sidecar query `sal fina` and `salt fine` both resolve to
  `form:salt:fine` (55 provider products); `color perm` resolves to
  `type:hair_color` (486); `potato chips` resolves to `type:potato_chips`
  (197); and `deodorant` resolves to `type:deodorant` (688).
- The ambiguous bare `chips` alias was removed from potato chips. Strict
  negative checks returned zero for potato-chip non-food rows, room
  deodorizer rows, mouthwash context, pet-food toy rows and conditioner rows
  under spice. Broad salt and spice queries still expose clarification choices
  because ingredient/flavor mentions are intentionally visible until a form
  is selected.
- Focused identity/index tests: 39 passed. Full tools suite: 391 passed.
  Backend suite: 135 passed. Android source and APK were unchanged; prior
  Android checks remain the applicable evidence for this sidecar-only commit.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `98a4002855a917acdfe7318fd8c38d1ba1459eeb49a946a0c58662ae9eb6e26a`.
- Motorola Edge 2025 runtime validation remains **NOT RUN** for this catalog
  checkpoint. The sidecar is local/ignored and has not been promoted to the
  backend or preview runtime.

Next concrete work is the residual inventory: 68,846 identity-level type rows,
42,268 provider groups without full type coverage, and 24,593 groups without
full type-or-department coverage. Continue evidence-backed batches and record
unresolved evidence gaps; do not treat literal reachability, department labels
or count increases as semantic completion.
