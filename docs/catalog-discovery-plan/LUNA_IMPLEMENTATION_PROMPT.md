# Copy this prompt into the Luna coding agent

> Superseded as the next-run prompt after foundation commit `4127bd22`.
> Use [LUNA_COVERAGE_COMPLETION_PROMPT.md](LUNA_COVERAGE_COMPLETION_PROMPT.md)
> for coverage expansion and remaining integration. This original brief is kept
> as history; do not follow its final instruction to restart from stage A.

Implement ValuePilot's catalog-wide bilingual product discovery system according
to the engineering specification already prepared in this repository.

Repository workspace:

```text
C:\Users\iamatopstrongman\Documents\Codex\2026-10-08\valuepilot-next-complete-premium-ui-ux\work\valuepilot-10133-source-audit
```

Start by reading these files once:

1. `AGENTS.md` and applicable current continuation notes, without restarting old audits.
2. `docs/catalog-discovery-plan/README.md`.
3. `docs/catalog-discovery-plan/ENGINEERING_SPEC.md` in full.
4. `docs/catalog-discovery-plan/EXISTING_CATALOG_CENSUS.json`.

Continue from the actual latest legitimate Git HEAD. The design baseline was
`4b3145f3`, and the handoff itself was committed later. Do not reset to that
baseline, overwrite uncommitted work, or undo subsequent legitimate changes.
Use the current recoverable development branch unless local instructions require
an isolated branch. Do not use an older Canada implementation as a new baseline.

## Product intent

The entire catalog must be organized through evidence-backed types and relations,
so English and Spanish queries produce meaningful clarification choices and an
exactly filtered product table. The pinned dataset contains 78,865 distinct
provider product IDs and 134,337 retailer identity records. Account for every
record. Future releases can contain different counts.

Rice, strawberry, fruit, mango, wheat, sugar and toys were illustrations only.
Do not implement just those examples or the existing 31 family rules and declare
completion. Discover and cover the full observed vocabulary and all applicable
product types/roles across the catalog, including uncommon nonfood products.

Differentiate what an item is from its base, declared ingredient, flavor, scent,
material, use or toy theme. A product can connect to several searches through
different relations; those relations must not make it interchangeable with the
food or object it mentions. Preserve compound meaning, ambiguity and negation.

Customers see a simple clarification box and the existing table. They do not see
engineering statuses, confidence scores, prototype language or the internal
classification ledger. Use the existing approved HTML layout first. Preserve
current table columns, actions, scrolling and Google Images behavior.

## What to implement

Follow the schemas, equations, API direction, UI rules and stages in
`ENGINEERING_SPEC.md`. That specification is the acceptance contract. Resolve
routine implementation details yourself and record any justified deviation.

Build a generic deterministic pipeline with an expandable bilingual concept
dictionary, context-aware aliases, reviewed phrase/role patterns, traceable
assertions, full-corpus coverage ledger, immutable release-bound index, and
incremental refresh that agrees with a complete rebuild. Use existing Python
infrastructure and standard-library SQLite unless measurement justifies more.
No mandatory remote AI, graph service, embedding service or paid dependency.

Every choice must be supported by at least one actual product in the relevant
source/geography/store scope. Compute facets and counts over the entire index,
not a first page or 31-candidate window. Apply strict semantic predicates before
ranking, pagination or offer hydration. Selecting a choice must never fall back
to unrelated rows when the subset is empty. Query rewrites are not facet IDs.

Build choices from applicable dimensions: ambiguous sense, product type, child
type, then remaining forms/bases/flavors when useful. Keep all nonempty options
reachable through bounded choice pagination; do not silently discard choices
after six or rare groups with one product. Specific queries keep their constraints;
broader or sibling searches require explicit remove/replace-filter actions.

Each accepted concept has ES-AR and EN labels and sense-scoped aliases. Equivalent
queries must select the same IDs and results regardless of display language.
Unknown terms retain literal retrieval and a query-specific residual path, without
leaking into a precise selected group. Never invent a meaning or translation.

Process the initial catalog in reproducible batches with a persistent ledger.
Use patterns to share justified decisions, but keep a disposition for every
source record. Revisit previously processed records when a new rule affects
them. Examine all residual ambiguous records using available evidence. A broad
department assignment or `REVIEW_REQUIRED` is not semantic completion.

Known types, terms and new combinations must classify automatically on refresh.
Truly unknown semantics enter a review queue. Do not promise that unspecified
future meanings can be known automatically without evidence. No AI invocation
belongs in the production query or refresh pipeline.

## Existing defects that the design must address

- `backend/service.py` currently derives clarification after bounded candidate
  discovery. `backend/reader.py` has a 31-candidate ceiling and v1 serves only
  bounded pages. Implement full-set v2 discovery; increasing that cap is not
  an adequate solution.
- The generic taxonomy response uses `PRODUCT_TAXONOMY_CLARIFICATION` and
  `labelEs`, while the current Kotlin model expects a different code, `label`
  and `evidence`. Add shared end-to-end wire fixtures so backend-only tests
  cannot miss this again. Do not merely weaken the parser.
- Generic form filtering can return all candidates when none match. Explicit
  filtering must be strict.
- Existing taxonomy unions attributes across retailer name variants. Preserve
  identity/component evidence and conflicts; do not create false combinations.
- The old all-products claim measured record accounting, not semantic coverage.
  The baseline census found 66,694 products without a family and 67,057 without
  any English alias on their linked identities. Those counts are evidence of
  missing structure, not a new correctness assessment.

## Boundaries

This task authorizes implementing and locally testing the additive discovery
feature, backend contracts and isolated preview integration described in the
specification. Preserve the existing `/v1` API for old clients and capability-gate
v2. Preserve existing price/quantity/value calculations, exact offer identity
joins, source/freshness/rights gates, Firebase/App Check, saved data, release
signing and the Play-installed application. Semantic discovery is not a new
source of prices, inventory or product equivalence.

Do not publish to Play, promote a production dataset, change production credentials,
disable security, create paid resources, delete provider data or reset history.
If live validation needs unavailable authorization or deployment, finish independent
work and report that precise gap. Never present fixtures as real production offers.
Do not repeat previously established access investigations without new evidence.

## Execution and verification

Proceed through stages A-G in the specification without repeatedly asking to
continue. Persist `docs/catalog-discovery-plan/IMPLEMENTATION_PROGRESS.md` with:

- actual branch/HEAD and source/index/dictionary versions;
- current stage and completed gates;
- every batch visited, next record key and reprocessing obligations;
- record, semantic, bilingual and facet coverage with real denominators;
- unresolved reasons and the exact evidence needed;
- last relevant test/build/device results and next concrete action.

Read that ledger on resumption instead of repeating the original full audit.
Make recoverable commits after meaningful verified stages. Keep restricted raw
catalogs and generated per-product outputs in the ignored local data directory;
commit rules, synthetic fixtures, code and aggregate reports only.

Run behaviorally meaningful Python tests, corpus-wide invariants, exact set/count
checks, bilingual equivalence, input-order invariance and full/incremental parity.
Use Python `unittest` where appropriate; pytest absence alone is not a blocker.
Test one independently reviewed positive and relevant negative for every type/rule
family, plus all per-record structural/reachability checks. Generated tests from
the same rule are insufficient evidence of semantic correctness.

Test backend -> Kotlin -> HTML contract and state restoration. Run relevant
Android tests/lint/build at integrated milestones; do not repeatedly rebuild for
minor changes. Validate complete result pagination beyond 31 and choice pagination
beyond six. Test exact product/offer identity and count units. An unimplemented
check must remain marked unverified.

Once the integrated preview is ready, use the authorized Motorola Edge 2025 if
connected: install the separate preview, exercise multiple food and nonfood
searches in both languages and themes, select/refine/page, then Product -> Google
Images -> expand -> close -> exact return. Preserve data and the Play app. Capture
JPG screenshots. Do not set up another emulator. If device access is missing,
report it distinctly from automated success.

## Completion rules

Do not finish after adding a few family examples, a generic framework, a broad
department classifier, or a ledger full of unresolved records. Complete all
independent engineering and process all product records. Do not claim all products
are semantically covered until the explicit semantic and bilingual gates pass.
If available evidence cannot resolve the remainder, finish the available work,
leave a clean verified checkpoint and identify exact remaining counts/reasons.
Do not continue indefinitely inventing labels to reach 100%.

Report plainly: what now works, what is still unknown, full-catalog coverage,
test/device evidence, commit, and whether deployment or source metadata is still
needed. Distinguish engineering completion, catalog semantic completion and live
device verification. No claim of a perfect dictionary or perfect future inference.

Begin with the baseline and stage A, then implement the specification.
