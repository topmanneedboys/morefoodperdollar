# ValuePilot catalog-wide discovery specification

Version: 1.0. Planning date: 2026-10-15. Baseline: `4b3145f3`.
Status: implementation contract, not implemented functionality.

## 1. Scope and non-negotiable acceptance meaning

Every provider record in the pinned baseline of 78,865 must be examined. Every
one of its 134,337 retailer identity records must stay accounted for. Do not
hard-code either number as a future catalog limit. Include food and nonfood
departments, obscure products, singleton groups, long-tail vocabulary and new
arrivals. No family is exempt because it was not named in the conversation.

Build an identity discovery layer that answers three questions:

1. What can the shopper mean by this English or Spanish query?
2. Which of those meanings actually have products in the selected catalog scope?
3. Which exact source identities satisfy the meaning they chose?

This layer may select and order identity candidates. It cannot change an offer,
price calculation, unit comparison, saved identity, stock status, security gate,
or existing shopping decision. Semantic connections do not make products valid
price substitutes. Preserve the exact identity used to join each store offer.

Implement locally and in the isolated Next application. Existing `/v1` clients
keep their contract and behavior. Production deployment, release promotion,
credentials and Play publication are separate actions; this handoff does not
authorize them. No restart of prior Stage 0/1/2 audits or UI redesign.

## 2. The model: a typed catalog, not a list of special searches

Use a small concept graph plus sparse relationships from real product records.
Do not compare every product to every other product. Do not create one dictionary
or clarification dialog per product. Do not generate all possible combinations.

The concept dictionary is expandable from the entire corpus. For each concept,
store a stable ID, a precise meaning, ES-AR/EN labels and aliases, an appropriate
parent relationship, and allowed roles. For example, an edible fruit concept
and a tool-handle concept may share the Spanish word `mango`; they have different
IDs. Aliases are a many-to-many mapping to senses, never a global replacement.

Keep these axes independent:

| Axis or relation | Meaning | Required distinction |
|---|---|---|
| `IS_A` | What the sellable item is | biscuit, juice, shampoo, toy vehicle |
| `BASE` | Named food/base material | rice in a rice cake |
| `DECLARED_INGREDIENT` | Explicit ingredient assertion | source states an ingredient; no invented proportion |
| `FLAVOR` | Named flavor | strawberry flavor does not prove strawberry content |
| `SCENT` | Named fragrance | fruit scent does not make a cleaning product edible |
| `MATERIAL` | Construction material | wood, plastic, steel |
| `THEME` | Depicted object/character | toy egg or fruit-shaped toy |
| `INTENDED_USE` | Explicit intended use | dog food, cake mold, handle for a tool |
| `FORM` | Physical form | powder, liquid, grain, whole, sliced |
| `STATE` | Explicit condition | frozen, dried; never assume fresh from silence |
| `VARIANT` | Supported distinguishing property | unsweetened, wholegrain; not an inferred health claim |
| `BRAND` | Exact source brand identity | never infer a flavor or type just from a brand |
| `MENTIONS` | Literal but unresolved occurrence | searchable evidence, not an accepted semantic relation |

`IS_A` parent edges form a validated acyclic hierarchy. Other relations are not
transitive. A drink flavored with mango does not inherit `IS_A fruit`. A cake
mold is not a cake. A rice-branded toy is not rice food. Do not model every noun
in a name as the identity of the item.

An assertion must carry its source identity, original field and text span,
normalization version, rule or override ID/version, and review state. Keep
assertions scoped to the product component/sense where necessary. For example,
"wheat cakes with strawberry filling" cannot create a strawberry-cake-base
assertion merely because both tokens occur. A food/cosmetic mention whose exact
role is unclear can support `MENTIONS` and the proven head type without asserting
an ingredient or fragrance. Never manufacture evidence because a group is useful.

### Synthetic illustration of composition

These examples describe behavior, not products claimed to exist in the catalog.

- `GALLETAS DE ARROZ`: `IS_A rice_cake`, with parent `crisp_cake`, `BASE rice`.
- `BEBIDA EN POLVO SABOR MANGO`: `IS_A drink_mix`, `FORM powder`, `FLAVOR mango`.
- `MANGO PARA ESCOBA`: `IS_A broom_handle`, `INTENDED_USE broom`; no fruit edge.
- `AUTO DE JUGUETE`: `IS_A toy_vehicle`, `THEME car`; no transport offer.
- `HUEVO DE CHOCOLATE`: `IS_A chocolate_confection`, `THEME egg_shape`;
  never a chicken egg, and never assumed to contain egg as an ingredient.

Every rule needs observed-source support and counterexamples before acceptance.
Do not collapse all Spanish `galletas`, English `cakes`, and `rice cakes` into
one sense. Define phrase-level senses; a baked cake, a biscuit and a puffed rice
cake can differ. English and Argentine Spanish labels describe those senses.

## 3. Identity and provenance before grouping

The existing 78,865 count is distinct `providerProductId`, not proof of 78,865
globally unique SKUs. Keep that baseline accounting key, but use qualified
`productEvidenceKey` as the canonical source identity. Preserve retailer and
source namespace. Never normalize a numeric ID by dropping leading zeros.

Do not merge records merely because names, provider IDs or prices match. A
cross-retailer product group is allowed only under the existing identity rules
or separately validated equivalence with compatible GTIN/package evidence.
Conflicting names, GTINs, quantities or departments require separate assertions
and a conflict record. Never union facts from contradictory retailer variants
into a synthetic product. Aggregate evidence only after those checks.

Use all available source names, including retailer variants. The old artifacts
limit name samples and identity mappings; record those limits and verify whether
source variants were omitted. If a limit was hit, inspect the authorized local
source or carry an explicit incompleteness reason. Increasing a bound without
measuring it does not prove coverage. Raw provider records remain ignored by Git.

## 4. Deterministic build pipeline for the entire catalog

Create versioned data definitions, not giant hand-written `if query == ...`
chains. Recommended new files (names can be adjusted to local conventions):

```text
tools/catalog_discovery/concepts.json
tools/catalog_discovery/aliases.json
tools/catalog_discovery/patterns.json
tools/catalog_discovery/overrides.jsonl
tools/catalog_discovery/normalize.py
tools/catalog_discovery/classify.py
tools/catalog_discovery/build.py
tools/catalog_discovery/query.py
tools/catalog_discovery/qualify.py
tools/tests/test_catalog_discovery_*.py
shared-fixtures/catalog-discovery-v2/
```

### 4.1 Inventory and normalization

1. Pin source release and exact artifact hashes. Census every input row and
   unique source identity; reject duplicate identity keys with conflicting data.
2. Preserve original text. Normalize Unicode and whitespace, retain a mapping
   back to source spans, and build a separate accent-tolerant lookup form.
   Accent folding must preserve ambiguity (`papa` and `papá`, `ñ` and `n`);
   the raw form and sense context remain available. Never translate brand names.
3. Extract words and phrases using boundaries. Keep word order. A maximum phrase
   length bounds rule matching, not record reachability: index the full literal
   name and report longer unmatched phrases. Retain raw ID/name search for all.
4. Keep package and currency parsing under their existing authority. Packaging
   tokens may be removed from a discovery signature, never from source evidence.

### 4.2 Build the initial dictionary from all products

Scan all names into token/phrase inventories and repeatable name-pattern buckets.
Enumerate observed head types, modifiers, abbreviations, brands and ambiguous
senses. Discovery candidates are suggestions for engineering review, not accepted
categories. Frequency helps prioritize work; it must never determine truth or
exclude rare products.

The initial coding agent reviews these patterns across the full dataset, adds
definitions and bilingual aliases, and replays them against every affected record.
Batch by stable product ID (default 500 product records) with a persistent ledger;
each record has its own outcome even when many share a pattern. Process all
batches, then individually examine residual ambiguous records. Check all matched
names for rule conflicts; sample review alone cannot satisfy the full-coverage
claim. Use licensed/authorized supplier metadata where it is already available.

Make semantic decisions once per reusable sense/pattern where justified, rather
than paying for 78,865 separate AI prompts. Save exceptions for a genuine specific
source anomaly; an override must include a source fingerprint and evidence.

### 4.3 Assert relations conservatively

Use longest accepted phrases, contextual head/modifier patterns, exclusions and
explicit negation. Precedence must be specified in the data and tested:

1. Applicable reviewed source-specific override with matching fingerprint.
2. Trusted, explicitly mapped supplier type/attribute evidence.
3. Reviewed exact phrase and role-specific composition rules.
4. Reviewed abbreviations with sufficient context.
5. Literal searchable mentions with unresolved semantic status.

Conflicting evidence creates a conflict; precedence must not blindly erase
incompatible evidence. `sin azúcar` must not become `CONTAINS sugar`. A product
with sugar in its brand must not become sugar. Detect language mixing, compounds,
abbreviations and terms such as `para`, `con`, `sabor`, `sin`, `tipo`, `juguete`.
Multi-item kits need component-level relations; they are not each component's
standalone product. Product-name descriptions do not prove nutrition/allergens.
Plain `huevos` supports edible eggs only when context establishes that meaning;
it does not prove chicken species, free-range status or freshness. Never use the
most common assumption to fill a missing product attribute.

Existing `product_identity_lexicon.py` and `product_taxonomy.py` are seeds only.
Their old `CLASSIFIED`, family roles and department priority are not gold labels.

### 4.4 Compile a small local artifact

Prefer Python standard-library SQLite for this catalog size. No graph server,
embedding service, vector database, remote LLM or new paid dependency is required.
Build once outside the phone; open read-only on the backend. The phone receives
bounded choices and result pages. Add another storage format only if measurement
shows a specific need.

Minimum logical tables:

```text
build(release_id, source_hash, dictionary_hash, rules_hash, schema_version, count)
identity(identity_key, provider_key, retailer_key, source_fingerprint, display_ref)
product_group(group_id, equivalence_basis) + group_member(group_id, identity_key)
concept(concept_id, kind, label_es_ar, label_en, sense_definition)
parent(child_concept, parent_concept) + ancestor(descendant, ancestor)
alias(normalized_phrase, language, concept_id, context_rule, alias_kind, evidence_id)
assertion(assertion_id, identity_key, component_id, relation, concept_id,
          evidence_id, rule_id, state)
evidence(evidence_id, source_identity, source_field, original_span, source_hash)
posting(posting_key, identity_key)   -- indexed both ways
scope_membership(scope_key, identity_key, source_membership_ref)
coverage(identity_key, type_status, role_status, bilingual_status, reason_codes)
build_impact(record_fingerprint, rule_dependencies, assertion_digest)
```

Posting keys include role and context, e.g. `IS_A:shampoo`,
`FLAVOR:mango`, or `BASE:rice@component:crisp_cake`. Keep single-role postings
and selectively materialize frequently used conjunctions. Do not precompute
the Cartesian product of all attributes, stores or product pairs. Geography
filters use existing qualified source membership/offer partitions; classification
must not add invented location data.

Index every literal product name even if semantic classification is incomplete.
Derived semantic assertions enter strict facet postings only when accepted.
Use integer identity row IDs internally with stable external identity keys.
Sorted postings or bounded bitsets can accelerate intersection if measured useful.
For scale intuition, a bitset over 78,865 entries takes about 9.9 KB before overhead;
this does not make storing every possible intersection sensible.

Build time grows with input text, rule matches and indexing, not N squared.
The same release, rules and source must produce the same logical records and
counts regardless of input order or batch size. SQLite file bytes need not be
identical across SQLite versions; compute a canonical logical-content digest and
also checksum each actual produced file for deployment integrity.

## 5. Query interpretation and the clarification formula

Resolve the full query to one or more sense-aware predicates. Prefer an accepted
specific phrase to its independent words. A specific query keeps all constraints:
`rice cakes` does not become `rice OR cakes`. Use synonyms bidirectionally only
inside their reviewed senses. EN `strawberry` and ES `frutilla`/`fresa` can share
a concept; `mango` fruit, handle and brand senses cannot be flattened together.

Brand, package and other explicit qualifiers remain active. Multiple concepts
within one query use a compositional parse, not OR over every token. Unsupported
or ambiguous parses retain a literal path and may offer sense clarification.
Spelling corrections are suggestions; never silently change a query into an
unrelated product. Do not promise every word or every synonym in either language.
Require all observed product vocabulary and all reviewed supported alias senses.

Let:

- `U_R` be all source identities in the pinned release.
- `S` be identities within the current source/geography/store selection.
- `M(q)` be the complete identity set for the resolved query sense, before paging.
- `F` be the predicate set from previous clarification selections.
- `P(p)` be the accepted posting set for predicate `p`.

Then:

```text
Candidates(q, F, S) = U_R ∩ S ∩ M(q) ∩ intersection(P(f) for f in F)
Members(choice c)  = Candidates(q, F, S) ∩ P(c)
Count(c)           = distinct validated product groups in Members(c)
                    (or explicitly named source-identity count if not deduplicated)
Show(c)            = Count(c) > 0 and c is an applicable, nonredundant refinement
SelectedResults   = Members(selected choice), ranked and paged AFTER intersection
```

Counts come from the entire matching index, never the first 5, 31 or 100 results.
Calculate counts before materializing a result page. Exact count is required for
this bounded baseline; if a future cap is reached, return explicit partial status
instead of a fabricated total or missing groups. The catalog may be larger than
the selected CABA area. Scope counts accordingly; a nationwide source product
does not prove it is sold nearby or in stock.

For a bare subject query such as `rice`, `M(q)` may include separately typed
relations involving rice (the food itself, named base, flavor, themed goods).
Those are discovery alternatives, not synonym matches or equivalent products.
For `fruit`, interpret `IS_A` descendants of fruit; do not include every
fruit-flavored item. Show observed fruit types as the next dimension. If the word
itself has unresolved senses, clarify the sense first.

### 5.1 Choose useful dimensions, preserve every option

Choose one next dimension at a time with deterministic rules:

1. Unresolved word sense or food/nonfood ambiguity.
2. Actual product type for a bare subject/material/flavor query.
3. Specific child type for a broad type query (`fruit` -> observed fruit types).
4. Remaining useful base/flavor/form/state differences inside the chosen type.

A dimension is useful when it produces at least two different nonempty subsets,
including a genuinely unresolved subset where relevant. Avoid asking a question
whose only answer is identical to the current result set. Use a stable priority
table for ties. Do not blend type, flavor and form into an arbitrary flat list.

Display up to six choices initially, then `Ver más / More options` with bounded
pagination or filtering inside that same box. Every supported nonempty choice
must be reachable, including a group containing only one product. Child groups
are derived from actual postings. Definitions may exist without products, but
must not produce empty customer choices. Do not use a minimum-frequency cutoff
to decide whether a genuine category exists.

Within a dimension, choose the most specific compatible child type and keep
broader branches for navigation. If groups overlap, count distinct products in
each and do not sum those counts as the total. Multi-flavor items may belong to
multiple flavor choices. Conflicting primary types require resolution, not
double assignment to unrelated types.

### 5.2 Exact behavior of selection and related searches

Each choice sends a stable predicate ID, never translated display text or a
rewritten free-text query. A selection narrows the current set. Applying it is
idempotent. Reapplying the same choice cannot broaden results. An empty filtered
set stays empty; never fall back to the unfiltered candidates.

`rice cakes` already specifies a type and base. Keep that scope. If the UI exposes
other cake bases, that is an explicit replace/remove-filter action: show the
current rice selection and let the shopper choose another observed base. Do not
insert wheat cakes into rice-cake results. Parent navigation is allowed through
an explicit `All [parent type] / Todos [tipo]` action which removes the appropriate
constraint. This implements useful connections without violating the query.

If the initial query has materially different meanings, show the clarification
box before populating a mixed table. A single unambiguous meaning can open the
table directly. Keep any current selection visible. An optional `All matching
types / Todos los tipos encontrados` action must be explicit and must not imply
those products are comparable. Do not silently select whole rice and conceal
rice cakes, or silently select sweets for eggs.

### 5.3 Unknowns remain available without contaminating selections

Maintain a literal-match set separately from accepted semantic predicates.
When relevant, expose a query-specific residual choice such as `Other matches
for “…” / Otros resultados de “…”`, backed by real source records. Do not expose
internal confidence scores or `REVIEW_REQUIRED`; do not add a global miscellaneous
toggle. Do not include unresolved products in a selected precise type unless
that type has accepted evidence. A resolved type with an unresolved modifier can
still appear under that proven type. This is field-level uncertainty, not a
reason to discard the product.

## 6. Bilingual coverage without per-product synonym explosions

Maintain aliases at concept and phrase level, with language, sense and evidence.
Reuse them across all products assigned to those concepts. Generate allowable
query compositions from accepted relations; do not store every possible sentence
for every product. Keep a reviewed list of safe inflections and Argentine
abbreviations. Do not blindly stem all words or translate brands.

Every customer-facing concept and choice requires reviewed ES-AR and EN labels.
Each accepted relation pattern needs both query-language forms when applicable.
Resolve normalized EN and ES equivalents to the same predicate IDs and product
sets. Mixed-language names remain literal-searchable. The full-corpus ledger
must report missing English concept labels and aliases separately from missing
Spanish ones. A generic English alias such as `food` cannot satisfy that gate.

Candidate dictionary expansion can use pattern counts and available supplier
categories, but an unknown word's English meaning cannot be proven from frequency
alone. Never invent an English translation to clear the completion report.

## 7. New releases: automatic processing without another AI project

For every authorized catalog refresh:

1. Compute fingerprints of all relevant source fields; diff added, changed,
   unchanged and removed identities. Process additions with the existing parser.
2. Reuse assertions only when source fingerprint and semantic dependency versions
   match. Re-evaluate changed records. Remove deleted identities from postings.
3. Add newly populated groups automatically from accepted existing concepts and
   composition rules. A new combination of a known base and known type does not
   require a hand-written product-specific rule or an AI call.
4. New unsupported head terms/senses enter a deterministic proposal/review queue.
   Keep literal search and any independently proven facets. Group proposals by
   context for review, but never auto-approve a semantic sense from co-occurrence.
5. A rule/dictionary change reprocesses all affected records using dependencies.
   Adding a previously unknown term must consult the full lexical inventory,
   including unresolved records. Initially prefer a full offline rebuild at this
   catalog size; introduce incremental optimization only with full-rebuild parity.
6. Recompute counts for changed scopes; empty groups disappear. Qualify a new
   immutable artifact. Atomically activate it with its matching source release
   only in an authorized environment. Keep the prior pair recoverable.

No LLM call is part of build or search. Existing vocabulary and composition rules
cover known types and combinations automatically. A genuinely new meaning or
unusable source description may need a small dictionary/metadata correction.
No formula can guarantee correct meanings for arbitrary future products from
missing data. That exception must be explicit, not hidden as "automatic success."

## 8. Runtime and wire contract

Introduce an additive, capability-gated discovery v2 path. Keep `/v1/search`
unchanged for older clients. Recommended endpoints: authenticated `POST /v2/search`
and `POST /v2/search/facets`; use the existing origin, App Check, authorization,
rate limits and evidence gates. Add their exact paths to the edge allow-list,
with bounded request validation. Do not disable validation or security to make
the endpoint work. Deployment is separate from implementing this code.

The status capability advertises v2 only if the current immutable index is
qualified and matches the pinned release. Old clients ignore the new optional
capability. Next uses v2 only when advertised. If unavailable, retain the supported
v1 path with its honest limitations; never label it full-catalog discovery.

Example request shape (contract example; identifiers are illustrative):

```json
{
  "contract": "valuepilot-discovery-v2",
  "query": "rice",
  "language": "en",
  "context": {"latitude": "-34.6037", "longitude": "-58.3816", "radiusKm": "5"},
  "storeKeys": [],
  "selection": [{"dimensionId": "product_type", "predicateId": "type:rice_cake"}],
  "releaseId": "pinned-release",
  "indexVersion": "qualified-logical-digest",
  "cursor": null,
  "pageSize": 20
}
```

Initial requests may omit release/index; the response pins both. Subsequent
requests and cursors must match. Context uses an existing explicit user or
qualified launch area; this example is not a new default location.

Response must include validated contract/release/index versions, a canonical
query/scope/selection fingerprint, resolved sense/predicates, scope description,
clarification dimension, bilingual choices, per-choice distinct counts, total
distinct products and source identities with declared count units, residual
count, page information, and the same qualified identity/offer evidence authority.
Use explicit status such as `READY`, `NEEDS_CLARIFICATION`, `NO_MATCHES`,
`INDEX_UNAVAILABLE`, or `RELEASE_CHANGED`; preserve transport/auth errors separately.

Each choice contains `id`, `predicateId`, `labelEs`, `labelEn`, `productCount`,
`countUnit`, and `hasChildren`. Labels are never queries. `facets` pagination must
remain stable independently of product pagination. The UI renders server-owned
predicates and sends typed actions; it does not classify products itself.

Start with pageSize 20, maximum 50 products; initial six choices and a bounded
choice page up to 30; maximum eight active dimensions and 256 Unicode query
characters (measure encoded byte bounds separately). These are page/request
bounds, not total catalog/result limits. Extend only after measurement. An exact
long source name must remain accessible via an identity route or explicit query
validation; never silently truncate it into a different query.

Use stable keyset pagination over relevance plus immutable identity/group key,
with an opaque cursor tied to release/index, normalized query, filters, scope,
sort mode and last sort key. Validate it and reapply authorization/scope every
time; a cursor never grants access to product IDs or stores. Reject malformed,
oversize or mismatched cursors. After a release change, return a typed restart
state; do not mix pages from different releases. Do not retain the v1 30-product
overall ceiling in v2 or implement v2 by repeatedly querying the same 31 candidates.

Hydrate only a result page from existing qualified identity/offer readers. If
deduplicating validated cross-retailer entities, keep their child source identities
and offer links intact. Missing offers never turn a valid product into a fake
price. Product count and displayed store-offer row count are distinct labels.
Store filter eligibility is source-listed evidence, never an in-stock claim.

## 9. App integration and user experience

The current HTML interface is the first customer surface to update. Preserve its
approved layout, product/brand area, action placement, pricing columns, free table
scrolling, Google Images action, language/theme settings and saved interactions.
Extend the existing clarification box; do not redesign Search.

New Kotlin contracts and coordinator state should carry predicates, cursor,
release/index, query fingerprint, choice pagination and request generation.
Keep v1 model bounds intact. Add a v2 adapter rather than weakening existing
validation. Reuse validated identity/offer parsing where possible. Code locations:

| Concern | Existing integration points |
|---|---|
| Source interpretation seeds | `tools/product_identity_lexicon.py`, `tools/product_taxonomy.py` |
| Discovery index infrastructure | `tools/argentina_searchpack.py`, `tools/consumer_input_intelligence.py` |
| Build/release qualification | `tools/build_argentina_daily_release.py`, release manifests/qualification tests |
| Backend API and scope | `backend/app.py`, `backend/service.py`, `backend/reader.py` |
| Edge security/path bounds | `backend/consumer_edge.py` |
| Android transport/contracts | `ArgentinaBackendConsumerBridge.kt`, `ArgentinaBackendContract.kt`, `ArgentinaConsumerEdgeTransport.kt` |
| Search state/lifecycle | `ArgentinaLiveSearchCoordinator.kt`, `ArgentinaLiveSearchSurface.kt`, Next journey owner |
| HTML bridge | `ValuePilotNextHtmlNativeSearchBridge.kt`, HTML Activity callbacks |
| Customer HTML | `android/app/src/debug/assets/valuepilot_next_html/app.js` and its existing stylesheet |
| Compose adapter | `ValuePilotNextDiscoveryScreens.kt` and existing journey state |

Every request has a generation; ignore stale responses after a query, scope,
selection or language-context change. Cache keys include source/index, normalized
query, selected senses/predicates, scope, sort and cursor; labels may be localized
without changing membership. Coalesce/cancel requests with bounded queues.

Selecting a different group intentionally resets the product table to the first
result. Opening a product or Google Images and returning must restore that query,
selection, loaded pages, selected product, table x/y scroll, active tab and focus
appropriately. Store state outside transient browser handoff. Switching EN/ES must
preserve meaning and selection. Back removes a refinement or returns to the
previous screen predictably, never silently erases the search.

Screen-reader labels include full choice text and count meaning. Controls meet
existing touch-target standards, work in both themes and large text, and announce
selection/results changes without repeated modal interruptions. Error messages
are localized and useful; no prototype text or internal taxonomy statuses.

## 10. Verification that cannot be replaced by a few example searches

### 10.1 Coverage gates

Write a machine-readable ledger for every source identity and every baseline
provider group, containing: type disposition, applicable roles, rule/evidence
links, bilingual query paths, literal reachability, conflict state and reason.
Record positive and explicit not-applicable outcomes; do not require ingredients
for a hammer. Calculate separate measures:

```text
record_accounting = visited_unique_source_identities / expected_source_identities
literal_reachability = exact_identity/name_retrievable / expected_source_identities
semantic_resolution = records_with_supported_type_and_resolved_applicable_roles / total
bilingual_coverage = resolved_records_with_valid_ES_and_EN_query_paths / total
facet_reachability = accepted_group_members_retrievable_via_group / accepted_group_members
selection_purity = returned_identities_satisfying_selected_predicate / returned_identities
```

The structural gates must be 100%. Semantic and bilingual gates must report their
true numerator/denominator; unresolved records do not count as passed. A single
shared broad word or `Other` assignment cannot pass them. `CLASSIFIED` by department
does not pass them. One successful row does not verify all products in that group.

Maintain three distinct milestones:

- **Engineering complete:** pipeline, contracts, strict filtering, UI and tests
  work; every record is accounted for, but semantic exceptions may remain.
- **Catalog semantic coverage complete:** every record's applicable meanings and
  bilingual paths have evidence-backed review, with no unresolved semantic gaps.
  If source data makes this impossible, report exact blocked records/reasons;
  do not claim this milestone or keep generating speculative labels.
- **Device/live verified:** updated preview is tested on the Motorola against
  an explicitly named release/index with authorized live service. Local fixtures
  and automated results cannot substitute for that.

Even passing these gates is scoped to the reviewed source and vocabulary; it is
not a proof of perfection for every possible future phrase or product.

### 10.2 Full-corpus invariants and independent semantic checks

Use tests that verify behavior and evidence, not just re-call the classifier as
the expected answer. Seed independent reviewed cases from every observed type,
role pattern and ambiguity, plus all previously reported regressions. Keep real
restricted source records local; commit synthetic equivalents where necessary.

Required automatic properties:

1. Every input record has a ledger entry and a literal retrieval path; no hidden
   dropping at a name, alias, record, family or pagination bound.
2. Every accepted assertion has a valid source span/evidence and existing concept;
   the type hierarchy has no cycles and every customer label has both languages.
3. For every accepted predicate, exhaust all pages and compare the identity set
   to an independent membership oracle. No duplicates, gaps or unrelated rows.
4. A choice with count N retrieves exactly N distinct counted entities under the
   same scope/release. A choice containing one item remains reachable.
5. Changing product page size or order does not change choices, counts or membership.
6. Selecting a predicate narrows; repeating it is idempotent. Zero strict matches
   remain zero. Unresolved records never leak into a resolved selected category.
7. All reviewed EN/ES alias pairs yield the same predicates and membership; test
   ambiguity, negation, brands and compounds independently of template generation.
8. Product/group/offer counts are distinct. Seven store offers never create seven
   product types or seven identical products unless source identity requires it.
9. Every scope filter is applied before counts; filtered counts cannot reveal
   out-of-scope membership. Price/stock evidence stays under existing gates.
10. Input shuffle, different batch sizes and repeated builds yield identical logical
    digests. Incremental add/change/delete results equal a full rebuild.
11. Rule changes reprocess affected unknown terms as well as existing assignments.
    Removed products remove their last group; new known combinations create groups.
12. Malformed index, wrong source hash, unknown predicates, stale cursor, oversized
    request and authorization failure have typed outcomes with no fallback leaks.
13. Shared wire fixtures travel through backend -> Kotlin parser -> HTML bridge ->
    rendered choices, and Compose if used. Catch the old label/code mismatch.
14. Out-of-order requests, rapid choices, language changes, rotation/process recreation
    and browser return preserve the current search state and correct row identity.

Essential independent counterexamples cover plain foods versus flavor/ingredient,
cosmetics versus vegetables, edible eggs versus confectionery/toys, tools versus
fruit senses, packaging/molds versus food, drink mixes versus fruit powders,
multi-item bundles, negative claims, and unseen source names. Expand this matrix
from all observed types; the examples above are the minimum, not the scope.

### 10.3 Bounded performance and release checks

Benchmark the full baseline plus 100k/250k synthetic identity load, not fabricated
production offers. Measure build time, memory, index size, cold/warm lookup,
complete facet counting, page hydration and cancellation. Initial investigation
budgets: warm index-only query/count p95 <=100 ms; warm backend search p95 <=500 ms
excluding external network; report real results and the test environment. These
are targets, not achieved claims. No full catalog scan or classification on the
phone or per keystroke. Bound queries and caches; no silent result truncation.

Run relevant Python unittest modules and new contract/property checks first.
Existing tests use `unittest`; do not declare Python blocked just because pytest
is unavailable. Run Android tests/lint/build once per meaningful integrated
milestone per repository rules. Do not repeatedly rebuild for documentation or
small unverified visual tweaks. No emulator setup. After a device build, install
only the separate preview package, preserve private data, and inspect APK identity
and permissions. Do not publish to Play.

Motorola verification: use several independently reviewed families from different
departments, a broad type query, a nonfood ambiguity, a long-tail singleton, more
than six choices, more than 31 products, and both languages. For each: select,
page, change store/scope, open a product, open/expand/close real Google Images,
and verify exact return state. Capture light/dark JPG screenshots and record
release/index/commit/APK hash. If the device or authorization is unavailable,
finish independent work and record the precise verification blocker once.

## 11. Implementation stages for Luna

Do not attempt an unreviewable rewrite. Keep one recoverable branch and a concise
progress ledger. Advance automatically through stages when their gates pass.

| Stage | Deliverable | Exit gate |
|---|---|---|
| A | Reproduce baseline census; inspect source completeness and existing integration seams | All input identities counted; hashes and missing-field/variant limits recorded |
| B | Generic concept/alias/rule schemas, evidence parser, ledger, index and query set operations | Synthetic role/ambiguity/property tests pass; no example-specific endpoint logic |
| C | Full-corpus pattern inventory, reviewed vocabulary and all-record classification | Every batch visited; type/role/language gaps enumerated; semantic coverage reported honestly |
| D | Immutable release-bound index and full-set facet/cursor engine | Every accepted predicate verified across all pages; rebuild/incremental parity |
| E | Additive authenticated v2 backend and Kotlin contracts, shared wire fixtures | Old clients unaffected; source/index/scope/security/price gates verified |
| F | Existing HTML clarification box, typed selections/state and matching Compose adapter | Every option reachable, strict membership, stable browsing return, bilingual themes |
| G | Relevant complete tests/build, authorized preview device checks and evidence | Verified commit and clear distinction between implemented, semantic-complete and live-tested |

Stage C may need more than one work session. Persist the next batch key, unresolved
record counts and reasons, rule versions, last verified command and next concrete
step. Reprocess impacted prior batches after later discoveries. Do not say "all
done" at the first generic framework commit or after adding only a few fruit rules.

If a semantic blocker cannot be resolved from source information, continue the
other records and independent engineering. Stop at a clean checkpoint with exact
counts/evidence needs once further progress needs new information. Never buy
services, call another model, weaken security, or fabricate classification to
reach an attractive percentage.
