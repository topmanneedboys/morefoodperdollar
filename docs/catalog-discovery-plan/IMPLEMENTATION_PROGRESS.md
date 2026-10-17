# Catalog discovery implementation progress

Updated: 2026-10-17

This ledger records the additive discovery work. It is not a claim that the
catalog has perfect semantic coverage. Existing `/v1` shopping, price, offer,
saved-data, release, and security paths remain unchanged.

## Latest verified continuation checkpoint — 2026-10-17 (department facet selection correctness)

- Code checkpoint: `7c134b65` on `work/valuepilot-stage2-premium-shopping`.
- Fixed a real query bug in which a broad department search such as `baby` or
  `bebida` could present a valid child type choice but clear the table after
  that choice was selected. Accepted child predicates now retain their strict
  candidate intersection and return the matching products.
- Added a regression test for a broad `baby` query selecting `type:diaper`.
  Focused lexicon/index tests pass 66; the full tools suite passes 419 and the
  backend suite passes 145.
- An independent audit of the promoted real sidecar walked all 274 concepts in
  English and Spanish: 548 language queries, 144 facet pages, 2,678 stable
  choices, and 2,678 strict-selection count checks. It found zero pagination,
  duplicate-ID, or advertised-count failures. This proves query behavior and
  accounting, not human semantic truth for every label; the saved artifact is
  `local-provider-data/product-discovery-v2-concept-audit-v2.json`.
- This was a Python-only fix, so the last verified Android artifact remains
  the APK built from `47f17198` at
  `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256
  `734F052227C7AEC8A6C412B395B44EA286017CC8291CDC8773FC7515E88C1B50`.
  Motorola runtime, remote live authorization, full synthetic compiler scale
  evaluation and held-out semantic evaluation remain pending.

## Latest verified continuation checkpoint — 2026-10-17 (clarification facet paging)

- Code checkpoint: `47f17198` on `work/valuepilot-stage2-premium-shopping`.
- The debug HTML discovery rail now exposes a native-owned “more product
  forms” action. The Kotlin coordinator sends the opaque `facetCursor` back to
  `/v2/search`, appends the new product page safely, and keeps stable predicate
  IDs for each choice. The cursor is never accepted from or exposed to HTML.
- Continuation pages may contain one final observed choice; the strict parser,
  native presentation and HTML renderer all keep that last choice reachable.
- Direct local sidecar paging for Spanish `bebida` returned 3 pages of
  30/30/16 choices with 76 unique predicate IDs and no duplicates. This is
  local catalog evidence, not remote authorization or phone-runtime evidence.
- Focused tests cover cursor transport, the final single-choice page, the
  HTML command boundary and the localized action. The full Android command
  passed and produced the verified debug APK at
  `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256
  `734F052227C7AEC8A6C412B395B44EA286017CC8291CDC8773FC7515E88C1B50`.
- Motorola runtime, remote live authorization, full synthetic compiler scale
  evaluation and held-out semantic evaluation remain pending. This remains a
  recoverable development checkpoint, not a completion claim.

## Historical HTML discovery integration checkpoint — 2026-10-17

- Code checkpoint: `2d28644f` on `work/valuepilot-stage2-premium-shopping`.
- The active debug HTML preview now uses the typed `/v2/search` discovery rail.
  It sends the selected language and carries stable observed predicate IDs
  through clarification and cursor requests, so a selected meaning is not
  rewritten into an unqualified free-text search.
- Production `MainActivity` remains on the existing `/v1/search` path. Android
  authorization and App Check requirements remain enforced; this change does
  not bypass credentials or security gates.
- Local checks against the promoted real sidecar returned `rice`/`arroz` as
  `NEEDS_CLARIFICATION` with 119 products, 247 source identities and 3 choices,
  and `eggs` with 222 products, 288 source identities and 7 choices. These are
  local route checks, not remote live authorization or phone-runtime evidence.
- Full shared-core tests, debug unit tests, debug lint and debug APK assembly
  passed. The verified debug APK is
  `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256
  `D626222EBA592DB6A52E2ECA5064BDDD321196EB8B4E8CA81493640801BDA971`, built
  from `2d28644f`.
- Full tools tests remain 418 passed; focused lexicon/index tests 65 passed;
  backend tests 145 passed. Motorola runtime, remote live authorization,
  full synthetic compiler scale evaluation and held-out semantic evaluation
  remain pending. This is a recoverable development checkpoint, not a
  completion claim.

## Historical scalability checkpoint — 2026-10-17

- Code checkpoint: `1ca32a12` on `work/valuepilot-stage2-premium-shopping`.
- The query engine now prefers a specific reviewed type/form alias over a
  broad department alias when the same phrase appears in the other language.
  This fixes the real-sidecar collision where Spanish-mode `rice` returned no
  products; `rice` and `arroz` now agree on `type:rice` and the same catalog
  membership.
- The bounded posting caches introduced in `acd5c31d` preserve membership and
  clarification output while reducing representative real-sidecar warm query
  p95 to approximately 34–86 ms. The caches are local to an opened sidecar
  instance and do not change source accounting or production price data.
- Candidate sets larger than SQLite's bind-variable ceiling now use a private,
  indexed temporary candidate table. First-page reads, distinct product counts,
  and repeated cursor pages are bounded and cached per query fingerprint, with
  a JSON1 path for smaller sets and a chunked fallback when JSON1 is absent.
  This keeps broad searches from failing after reporting a large row count.
- Direct query-path stress against the promoted sidecar passed for 100,000 and
  250,000 candidate IDs: warm first-page p95 was 0.731 ms and 0.734 ms,
  respectively. These are candidate-set stress results; they do not replace a
  full synthetic compiler semantic evaluation.
- The real sidecar was rebuilt in 781.254 seconds with the identity-first alias
  index and name caches. Its structural counts match v32, and 18 bilingual
  queries with full pagination had zero parity differences against the prior
  qualified artifact. The promoted sidecar SHA-256 is
  `a7d9a061e8bf18cbbd5f588bc608369d7e52ce0dffb7646a5061e5288ae5965b`; the
  previous `0b1358…5677a` file is preserved locally as a backup.
- Full tools tests: 418 passed. Focused lexicon/index tests: 65 passed.
  Backend tests remain 145 passed. Android checks and the debug APK were
  already verified at the stable-selection client checkpoint `6764d77f`.
- Motorola runtime validation, authorized live-service evidence, synthetic
  100k/250k scale output, and held-out semantic evaluation are still pending.
  The temporary synthetic compiler check was stopped after its 100k phase
  exceeded the useful verification window; no result is counted as a pass.
  This is a recoverable development checkpoint, not a completion claim.

## Checkpoint

- Branch: `work/valuepilot-stage2-premium-shopping`
- Starting handoff checkpoint: `6ce81d0d`
- Current verified checkpoint: code commit `7c134b65` (the preceding sections record the latest Python and facet-paging checkpoints).
- Discovery rules: `valuepilot-catalog-discovery-rules-v32`
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
- `concepts`: 274
- `assertions`: 194,661
- `TYPE_RESOLVED`: 68,425
- `TYPE_UNRESOLVED`: 65,912
- `TYPE_OR_DEPARTMENT_RESOLVED`: 91,158
- `DEPARTMENT_RESOLVED`: 85,249
- `ROLE_RESOLVED`: 70,004
- `DERIVED_ONLY`: 1,579
- `NO_FORM`: 125,168
- English retrieval-path proxy: 91,295 identities
- Spanish retrieval-path proxy: 111,070 identities
- fully type-resolved provider groups: 30,589; partly resolved: 8,004; unresolved: 40,272
- fully type-or-department-resolved provider groups: 46,236; the report also keeps the overlapping partial and unresolved dispositions separately.
- source SHA-256: `85033a863ea95fea8be7ea73c31577987f09d3533f4c5aab70321d7a945877a6`
- ignored sidecar SHA-256: `a7d9a061e8bf18cbbd5f588bc608369d7e52ce0dffb7646a5061e5288ae5965b`

The following rule-history paragraph documents the v25 replay and is retained
for historical context; the current v32 totals are listed above. The v25 replay
combines the existing reviewed family dictionary with
`tools/data/discovery_family_rules.json`. It retains all earlier reviewed rules
and adds observed bilingual household-cleaning families for broom
(escoba/escobillon/escobillón), mop (mopa) and duster (plumero). The observed
KIT GANCHOS ESCOBA accessory and GUANTE NOVICA PLUMERO glove context remain
literal/derived and are excluded from strict family postings.
Chewing-gum forms are limited to observed sugar-free and filled names. Negative
context is conservative for scent, conditioner, shampoo, dental and beverage
mentions; facial-cream context excludes observed depilatory and sunscreen
names. The index uses the canonical product tokenizer for punctuation-separated
retail abbreviations.

The v23 full-catalog strict audit returned zero forbidden-context direct matches
for all seven new appliance families, bowl and the four v21 families. Strict
queries return coffee maker 63/64, air fryer 77/88, toaster 41/41, sandwich
maker 25/26, juicer 22/23, blender 75/78 and microwave 80/98 provider/source
counts in both languages. Existing strict query checks return wafer/oblea 156
provider products from 303 identities, chewing gum/chicle 69 products from 185
strict identities, sugar-free gum 10/12, filled gum 5/5, raticide 8/8 and
facial cream 76/136. Broad gum queries retain contextual mentions for the
clarification surface; explicit type/form selections use accepted postings.
These are observed identity matches, not price or availability evidence.

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

- full tools suite: 405 tests passed;
- backend suite: 138 tests passed;
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
remaining 67,274 identity-level type-unresolved rows, 41,408 provider groups
without full type coverage and 24,511 groups without full type-or-department
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

Next concrete work is the residual inventory: 66,680 identity-level type rows,
40,854 provider groups without full type coverage, and 24,421 groups without
full type-or-department coverage. Continue evidence-backed batches and record
unresolved evidence gaps; do not treat literal reachability, department labels
or count increases as semantic completion.

## v25 verified checkpoint

- Code checkpoint: `9d3edf42`.
- Rules: `valuepilot-catalog-discovery-rules-v25`; sidecar schema remains
  `valuepilot-catalog-discovery-v2`.
- The v25 batch adds three observed bilingual household-cleaning families:
  broom (`escoba`, `escobillon`, `escobillón`), mop (`mopa`) and duster
  (`plumero`). The strict English aliases are `broom`/`floor broom`, `mop`
  and `duster`/`feather duster`.
- Both language paths resolve to the same strict predicates. Full pagination
  audits retrieved every identity without duplicates: broom 167/283, mop
  89/141 and duster 24/35 provider/source counts. Every strict posting was
  independently re-derived as `DIRECT`; no non-direct family postings remain.
- The broom rule excludes the observed `KIT GANCHOS ESCOBA` accessory and the
  duster rule excludes the observed `GUANTE NOVICA PLUMERO` glove context. Each
  remains literal-searchable as a derived mention.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `a4f148aea8e9199d9b92e0d5d93acb25bfe805ce045f8f025ae33fee1c2c692b`.
- Coverage: 68,335 type-resolved; 66,002 type-unresolved; 91,161
  type-or-department resolved; 85,249 department-resolved; 69,578
  role-resolved; 1,243 derived-only; 126,069 without a form.
- Provider groups: 30,482 fully type-resolved, 8,002 partial and 40,381
  unresolved; 51,477 fully type-or-department-resolved, 2,967 partial and
  24,421 unresolved.
- Focused lexicon/backend checks passed. Full tools suite: 410 tests passed.
  Backend suite: 142 tests passed. Android source and APK were unchanged;
  prior Android checks remain applicable.
- Motorola Edge 2025 runtime validation remains **NOT RUN** for this
  catalog-only checkpoint. The sidecar is local/ignored and has not been
  promoted to the backend or preview.

The v25 residual gate remains open. Continue with evidence-backed batches and
record unresolved evidence gaps; do not treat literal reachability, department
labels or count increases as semantic completion.

## v24 verified checkpoint

- Code checkpoint: `778bf028635daf6804a772c921eb0d460014bce9`.
- Rules: `valuepilot-catalog-discovery-rules-v24`; sidecar schema remains
  `valuepilot-catalog-discovery-v2`.
- The v24 batch adds four observed bilingual household-appliance families:
  heater/calefactor, water heater/termotanque, fan/ventilador and vacuum
  cleaner/aspiradora. The English `fan` alias was checked against every
  posting; five observed football/fut/lata/perfume contexts remain derived
  mentions and are excluded from strict fan results.
- Both language paths resolve to the same strict predicates. Full pagination
  audits retrieved every identity without duplicates: heater 98/118, water
  heater 42/48, fan 20/20 and vacuum cleaner 50/50 provider/source counts.
  Every strict posting was independently re-derived as `DIRECT`; no non-direct
  family postings remain.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `69510b48892953339d07bfbe972c0284e5435e6ab7f02d51947f428d18d394a3`.
- Coverage: 67,884 type-resolved; 66,453 type-unresolved; 91,161
  type-or-department resolved; 85,249 department-resolved; 69,125
  role-resolved; 1,241 derived-only; 126,069 without a form.
- Provider groups: 30,221 fully type-resolved, 7,992 partial and 40,652
  unresolved; 46,230 fully type-or-department-resolved, 8,214 partial and
  24,421 unresolved.
- Full tools suite: 409 tests passed. Backend suite: 141 tests passed. Android
  source and APK were unchanged; prior Android checks remain applicable.
- Motorola Edge 2025 runtime validation remains **NOT RUN** for this
  catalog-only checkpoint. The sidecar is local/ignored and has not been
  promoted to the backend or preview.

The v24 residual gate remains open. Continue with evidence-backed batches and
record unresolved evidence gaps; do not treat literal reachability, department
labels or count increases as semantic completion.

## v23 verified checkpoint

- Code checkpoint: `bab93bc1910023d3ef366cfa2139d58ec9496990`.
- Rules: `valuepilot-catalog-discovery-rules-v23`; sidecar schema remains
  `valuepilot-catalog-discovery-v2`.
- The v23 batch adds observed bilingual strict families for coffee maker/cafetera,
  air fryer/airfryer, toaster/tostadora, sandwich maker/sandwichera,
  juicer/exprimidor, blender/licuadora and microwave/microondas. Accessory,
  food, wax and rack contexts are kept out of strict rows using observed names.
- Full-catalog strict direct-context audits returned zero forbidden matches for
  all seven families. Both language paths resolve to the same type predicates:
  coffee maker 63/64, air fryer 77/88, toaster 41/41, sandwich maker 25/26,
  juicer 22/23, blender 75/78 and microwave 80/98 provider/source counts.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `f8d6a59c772ecf8b965a792062e26e682d14a429ef50d71e0f889afc790b9a69`.
- Coverage: 67,657 type-resolved; 66,680 type-unresolved; 91,161
  type-or-department resolved; 85,249 department-resolved; 68,895
  role-resolved; 1,238 derived-only; 126,069 without a form.
- Provider groups: 30,065 fully type-resolved, 7,946 partial and 40,854
  unresolved; 46,230 fully type-or-department-resolved, 8,214 partial and
  24,421 unresolved.
- Full tools suite: 408 tests passed. Backend suite: 140 tests passed. Android
  source and APK were unchanged; prior Android checks remain applicable.
- Motorola Edge 2025 runtime validation remains **NOT RUN** for this
  catalog-only checkpoint. The sidecar is local/ignored and has not been
  promoted to the backend or preview.

The v23 residual gate remains open. Continue with evidence-backed batches and
record unresolved evidence gaps; do not treat literal reachability, department
labels or count increases as semantic completion.

## v22 verified checkpoint

- Code checkpoint: `38ef3db2e2b514de87435cf3f62618499950e5e3`.
- Rules: `valuepilot-catalog-discovery-rules-v22`; sidecar schema remains
  `valuepilot-catalog-discovery-v2`.
- The v22 batch adds the observed `bowl` family in Spanish/English using the
  catalog's own term. Two stand mixers and one chopper that mention a bowl are
  retained as contextual `MENTIONS` and excluded from strict `type:bowl` rows.
- The full-catalog strict direct-context audit returned zero forbidden bowl
  matches. `bowl` resolves to 189 distinct provider products from 198 source
  identities in both languages.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `522738d20bf9542ea0d812bccbd975478a4e04a2792d542d7dff34f737def418`.
- Coverage: 67,260 type-resolved; 67,077 type-unresolved; 91,063
  type-or-department resolved; 85,249 department-resolved; 68,479
  role-resolved; 1,219 derived-only; 126,069 without a form.
- Provider groups: 29,764 fully type-resolved, 7,881 partial and 41,220
  unresolved; 46,141 fully type-or-department-resolved, 8,213 partial and
  24,511 unresolved.
- Full tools suite: 407 tests passed. Backend suite: 139 tests passed. Android
  source and APK were unchanged; prior Android checks remain applicable.
- Motorola Edge 2025 runtime validation remains **NOT RUN** for this
  catalog-only checkpoint. The sidecar is local/ignored and has not been
  promoted to the backend or preview.

The v22 residual gate remains open. Continue with evidence-backed batches and
record unresolved evidence gaps; do not treat literal reachability, department
labels or count increases as semantic completion.

## v21 verified checkpoint

- Code checkpoint: `7b79ac963b000619f1143f73b9c0346cdaf4717d`.
- Rules: `valuepilot-catalog-discovery-rules-v21`; sidecar schema remains
  `valuepilot-catalog-discovery-v2`.
- The v21 batch adds observed bilingual strict families for wafer/oblea,
  chewing gum/chicle, raticide/rodenticide and facial cream. Chewing-gum
  clarification exposes only observed sugar-free and filled choices.
- Full-catalog strict negative audit returned zero forbidden-context direct
  matches for all four new families. Broad contextual mentions remain available
  for clarification; explicit type/form selections are strict.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `99e8cd39e798437721c5716d62e030a84ba5ea2025f5673065c7dbdfd17b498b`.
- Coverage: 67,063 type-resolved; 67,274 type-unresolved; 91,063
  type-or-department resolved; 85,249 department-resolved; 68,279
  role-resolved; 1,216 derived-only; 126,069 without a form.
- Provider groups: 29,579 fully type-resolved, 7,878 partial and 41,408
  unresolved; 46,141 fully type-or-department-resolved, 8,213 partial and
  24,511 unresolved.
- Full tools suite: 405 tests passed. Backend suite: 138 tests passed. Android
  source and APK were unchanged; prior Android checks remain applicable.
- Motorola Edge 2025 runtime validation remains **NOT RUN** for this
  catalog-only checkpoint. The sidecar is local/ignored and has not been
  promoted to the backend or preview.

The v21 residual gate remains open. Continue with evidence-backed batches and
record unresolved evidence gaps; do not treat literal reachability, department
labels or count increases as semantic completion.

## v19 verified checkpoint

- Code checkpoint: `f1e03771b36afbf93476e2f566a09f4efba111ee`.
- Rules: `valuepilot-catalog-discovery-rules-v19`; sidecar schema remains
  `valuepilot-catalog-discovery-v2`.
- The v19 batch adds strict bilingual meat-family discovery for pork/cerdo,
  lamb/cordero, turkey/pavo and beef/vacuno/ternera. The merge path now
  suppresses stale direct source assertions whenever current reviewed negative
  context matches, using the canonical tokenizer for punctuation and abbreviations.
- Full-catalog strict direct-posting audit: pork 334 direct / 0 forbidden-like,
  lamb 39 / 0, turkey 8 / 0, beef 367 / 0.
- Verified query results: cerdo/pork 317 provider products and 334 source
  identities; cordero/lamb 39/39; pavo/turkey 6/8; vacuno/beef 248/367.
- Coverage: 66,729 type-resolved; 67,608 type-unresolved; 90,923
  type-or-department resolved; 67,953 role-resolved; 1,224 derived-only;
  126,086 without a form.
- Provider groups: 29,464 fully type-resolved, 7,866 partial and 41,535
  unresolved; 46,078 fully type-or-department-resolved, 8,232 partial and
  24,555 unresolved.
- Sidecar: `local-provider-data/product-discovery-v2.sqlite`; SHA-256
  `8893365ece314585a9a0e88ff228b8e1fb83b67bb051be96a439fd85243013d4`.
- Focused identity/index tests passed before the release build. Full tools suite: 403
  tests passed. Backend suite: 137 tests passed. Android
  source and APK remain unchanged; Motorola Edge 2025 runtime validation was
  not run, and the sidecar remains local/ignored and unpromoted.

This checkpoint is meaningful semantic expansion, not catalog completion.
Residual identities and provider groups still require evidence-backed review; no
guessed translations, prices, offers, stock, availability, credentials,
production activation or Play publication were introduced.

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
