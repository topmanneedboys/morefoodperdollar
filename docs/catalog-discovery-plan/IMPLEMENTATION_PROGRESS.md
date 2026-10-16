# Catalog discovery implementation progress

Updated: 2026-10-16

This ledger records the additive discovery work. It is not a claim that the
catalog has perfect semantic coverage. Existing `/v1` shopping, price, offer,
saved-data, release, and security paths remain unchanged.

## Checkpoint

- Branch: `work/valuepilot-stage2-premium-shopping`
- Starting handoff checkpoint: `6ce81d0d`
- Current verified checkpoint: code commit `3a67385b` (documentation checkpoint follows the verification update).
- Discovery rules: `valuepilot-catalog-discovery-rules-v14`
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
- `concepts`: 235
- `assertions`: 191,069
- `TYPE_RESOLVED`: 66,584
- `TYPE_UNRESOLVED`: 67,753
- `TYPE_OR_DEPARTMENT_RESOLVED`: 90,913
- `DEPARTMENT_RESOLVED`: 85,249
- `ROLE_RESOLVED`: 67,782
- `DERIVED_ONLY`: 1,198
- `NO_FORM`: 126,086
- English retrieval-path proxy: 91,008 identities
- Spanish retrieval-path proxy: 110,939 identities
- fully type-resolved provider groups: 29,254; partly resolved: 7,832; unresolved: 41,779
- fully type-or-department-resolved provider groups: 45,963; partly resolved: 8,321; unresolved: 24,581
- source SHA-256: `85033a863ea95fea8be7ea73c31577987f09d3533f4c5aab70321d7a945877a6`
- ignored sidecar SHA-256: `817a78c886b93eb81b13df3e7c4861612a20875d1946059da8a5975f2603d7b2`

The v14 replay combines the existing reviewed family dictionary with
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
the v14 checkpoint below; do not use this historical paragraph as a current
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

- full tools suite: 397 tests passed;
- backend suite: 136 tests passed;
- Android source and APK unchanged; the prior shared-core tests, app debug unit tests, lint, and debug APK build remain the applicable evidence;
- debug APK SHA-256: `027736566b64f1a0e21e3fcb6f475b0a862633e06a88caa8a16d4c8166cd7a4c`.
- live sidecar spot checks passed: the prior v11 queries plus `alfajor`, `alfajor chocolate`,
  `flour`, `flour wheat`, `mani`, `mani tostado`, and `peanut butter` resolve to
  reviewed predicates; bare alfajor, flour and peanut queries expose observed
  clarification choices; explicit form queries return `READY`; strict full-sidecar
  negative checks for flour and peanut contexts returned zero.

Pending after the local full build:

- incremental refresh parity against a complete rebuild;
- authorized Motorola validation;
- semantic review of unresolved records and source evidence gaps.

The current build has not been promoted to the backend or preview runtime. The
remaining 67,753 identity-level type-unresolved rows, 41,779 provider groups
without full type coverage and 24,581 groups without full type-or-department
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

Next concrete work is the residual inventory: 67,753 identity-level type rows,
41,779 provider groups without full type coverage, and 24,581 groups without
full type-or-department coverage. Continue evidence-backed batches and record
unresolved evidence gaps; do not treat literal reachability, department labels
or count increases as semantic completion.

## v14 verified checkpoint

- Code checkpoint: `3a67385b0ead741574eaa5b2eec423cafcfc6d3a`.
- The v14 batch adds reviewed Argentine food families for alfajores, flour and
  peanuts. Each family has bilingual observed forms, explicit product-level
  clarification labels, and negative contexts for ingredient and derived
  mentions such as peanut paste, oil, chocolate, bars, flour of bone,
  fertilizer and baked goods.
- Form-qualified selection now prefers the family supplying the form, so
  `alfajor chocolate`, `flour wheat`, `mani tostado` and `peanut butter`
  resolve to the intended form predicate and return `READY` without reopening
  sibling facets.
- Full-catalog strict checks returned zero direct peanut marker leaks and zero
  direct postings for all reviewed flour/peanut negative contexts.
- Focused identity/index/backend integration tests: 81 passed. Full tools suite:
  397 passed. Backend suite: 136 passed.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `817a78c886b93eb81b13df3e7c4861612a20875d1946059da8a5975f2603d7b2`.
- Coverage: 66,584 type-resolved; 67,753 type-unresolved; 90,913 type-or-department
  resolved; 67,782 role-resolved; 1,198 derived-only; 126,086 without a form.
- Provider groups: 29,254 fully type-resolved, 7,832 partial and 41,779
  unresolved; 45,963 fully type-or-department-resolved, 8,321 partial and
  24,581 unresolved.
- Android source and APK are unchanged; the prior APK evidence remains valid.
  Motorola Edge 2025 runtime validation was not run for this catalog-only
  checkpoint, and the sidecar remains local/ignored and unpromoted.

This checkpoint is meaningful semantic expansion, not catalog completion. The
remaining unresolved identities and groups still require evidence-backed review;
no guessed translations, prices, offers, stock, availability, credentials,
production activation or Play publication were introduced.
