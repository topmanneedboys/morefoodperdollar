# ValuePilot Next: finish accurate bilingual Search and clarification

Prepared 2026-10-16 for Luna. This is an implementation goal to use when the user activates or resumes it. Preparing this document does not resume the paused goal.

## Continuation checkpoint (2026-10-16)

The goal is active again from the existing `work/valuepilot-stage2-premium-shopping`
branch. The v32 candidate has now been built and semantically qualified from the
pinned 78,865-product source: 134,337 identity records, rules
`valuepilot-catalog-discovery-rules-v32`, sidecar SHA-256
`0b135858dfccb8b41d815b3f5c4ca71fc9718bda3a90526d5734f59c4785677a`. The
tracked release report has been refreshed to this candidate. The verified audit
now covers the earlier cloth, strawberry, mango, egg, rice, and fruit failure
seeds, terminal form selections, bilingual aliases, forbidden-context scans,
and duplicate-free representative pagination. Full tools/backend suites,
relevant Android checks, active HTML runtime evidence, and the Motorola device
review remain gates before this goal can be called complete.

## Continuation checkpoint (2026-10-17)

The verified implementation checkpoint is now `47c71853`. Clarification choices
use stable `family:form` predicate IDs from the HTML preview through the Android
request/coordinator and backend catalog filter, so selecting “whole egg”, “rice
grain”, or another observed form no longer becomes a free-text guess. The
previous hidden six-choice limit is replaced with a bounded 30-choice contract;
the current catalog families expose every observed choice within that bound.
Focused Python and Android checks pass, and the required shared-core, Android
unit, lint, and debug-APK build command passes. The rebuilt APK is recorded in
`RELEASE_BUILD_REPORT.json`. Motorola runtime screenshots, live authorized
service evidence, and the remaining performance/held-out evaluation gates are
still unrun; this is a verified development checkpoint, not goal completion.

## Continuation checkpoint (2026-10-17, query correctness and warm-cache pass)

The current code checkpoint is `199f5bd0`. A language collision found during
the real-sidecar check is fixed: when the selected UI language has only a broad
department alias for a phrase, a reviewed type/form alias in the other language
now takes precedence. English `rice`, Spanish `rice`, and Spanish `arroz` now
resolve to the same `type:rice` set (119 provider products, 247 source
identities, three observed choices). The regression is covered by the catalog
index tests.

The bounded posting caches from `acd5c31d` preserve those results and bring the
real-sidecar warm query p95 to roughly 34–86 ms for the representative rice,
arroz, eggs, fruit, frutilla, and strawberry checks. The full tools suite now
passes 417 tests; the focused lexicon/index set passes 64. The Android APK
remains the verified artifact built from the stable-selection client checkpoint
`6764d77f`; the Python-only query changes do not alter that APK. Synthetic
100k/250k scale output, Motorola runtime evidence, authorized live-service
evidence, and held-out semantic evaluation remain open gates. A temporary
100k/250k synthetic build was started but stopped after the 100k compiler phase
exceeded the useful verification window; it is recorded as unrun rather than
claimed as a pass.

## Goal and authority

Repository: C:/Users/iamatopstrongman/Documents/Codex/2026-10-08/valuepilot-next-complete-premium-ui-ux/work/valuepilot-10133-source-audit.

Complete the existing ValuePilot Next HTML search experience so an English or Spanish query leads to appropriate, evidence-supported clarification choices and then the correct product table, using the entire authorized catalog. Make the process reusable for future catalog releases. Preserve the approved interface and the existing shopping engine.

Read this brief fully, then the relevant sections of ENGINEERING_SPEC.md and the current IMPLEMENTATION_PROGRESS.md in this directory. Inspect applicable repository instructions and actual code. The previous LUNA_COVERAGE_COMPLETION_PROMPT.md is supporting history. This brief supersedes its execution order, especially repeated small vocabulary batches before core correctness and runtime integration.

Continue from the actual latest legitimate Git HEAD and preserve existing work. Do not reset to an old commit, restart completed Stage 0/1/2 audits, or rewrite the application. Check each inherited defect once against current evidence; do not redo a fix that is already proven.

The finish line is working customer search, measured semantic quality, and a verified preview. Scanning all records, assigning department labels, passing unit tests, or creating an unused endpoint does not alone meet it.

## Starting evidence: verify once, then work

At authoring time:

- Branch: work/valuepilot-stage2-premium-shopping.
- Latest Git HEAD: 1835223b, documenting v25; latest committed implementation: 9d3edf42.
- Seven tracked files contain uncommitted cleaning-cloth and form-filter changes. Preserve and review them; do not blindly commit or discard them.
- The on-disk product-discovery-v2.sqlite and its local report identify v26. The tracked release report still identifies v25. A v27 rebuild launched before the pause was still running at the last observation. Recheck its status and output; never assume it completed or launch a duplicate.
- Baseline: 78,865 provider product IDs and 134,337 retailer-scoped identity records. These are different counting units, not two competing product totals.
- Pinned source: local-provider-data/product-identities-153e9d9f7ab4b87e72c16aebebc69c6f9f90fca681cd1075d625244e2e31bc2c.jsonl.gz.
- Source SHA-256: 85033a863ea95fea8be7ea73c31577987f09d3533f4c5aab70321d7a945877a6.
- v25 report: 68,335 type-assigned identity records; 30,482 fully type-assigned provider groups, 8,002 partial, 40,381 with none. These assignments still require semantic validation.
- A report discrepancy needs reconciliation: the tracked report says 51,477 fully type-or-department-covered groups. Recalculating v25 assignments by excluding the new cloth type from the v26 database produced 46,230 full, 8,214 partial, and 24,421 unresolved groups, while reproducing the v25 identity totals. Verify the group calculation using per-identity logical OR, without double-counting overlapping assignments.
- The local v26 query reproduced these failures:
  - strawberry/frutilla returns hair conditioner as a direct strawberry product, with no clarification.
  - fruit followed by type:mango includes can openers, razors, and scent products.
  - eggs followed by form:egg:whole_egg includes egg bagels and prepared egg bites.
  - pano de cocina includes curtain and kitchen-paper names. The pending v27 work attempts to address direct-form leakage; verify it and its effects on every affected family.
- app.js nativeClarification truncates choices with slice(0,6) and sends rewritten queries. The active coordinator uses the older searchPresentation path. A v2 endpoint, Kotlin parser, and bridge method exist, but their existence is not runtime integration.
- The compiler's per-identity language lookup scans identity_alias without an index beginning with identity_key. This is a measured query-plan concern, not yet proof of the whole build's bottleneck. Profile before fixing.

These examples are regression seeds, not the catalog scope. Find and repair the same classes of error across all observed families.

## Work in this order

### 1. Recover a consistent, truthful checkpoint

Inspect HEAD, diff, running jobs, source/index metadata, report hashes, and current activation state. If an earlier build is live, keep one owner and observe that process; do useful independent work while it runs. Do not restart because a tool observation timed out.

Reconcile source code, compiled artifact, report, and tests. A completed build is only a candidate until semantic and structural qualification passes. Preserve the last actually verified artifact and manifest before any replacement. Do not claim an old sidecar remains recoverable unless its file and hash have been checked.

Separate artifact states explicitly: committed code, candidate local index, qualified local index, local API, preview UI, and deployed service. Save a recoverable checkpoint after meaningful verification.

### 2. Repair meaning and clarification before expanding vocabulary

Implement reusable, evidence-based distinctions between:

- sellable product type;
- ingredient or base;
- flavor, scent, material, intended use, or depicted theme;
- accessories, containers, molds, bundles, and components;
- ambiguous word senses, including mango fruit versus a tool handle.

Use source context and reviewed rules. Token presence is not proof of product type. Preserve original names, qualified identity keys, source spans, and provenance. Unknown meanings remain literal-searchable with actionable internal reasons. Missing optional attributes must not invalidate an otherwise supported type.

Fresh strawberry can be fruit; strawberry conditioner belongs to hair care; a mango handle belongs to its tool. A chicken-egg selection must exclude prepared foods, confectionery, toys, and containers. Derived products remain reachable through appropriate, clearly labeled choices where the source supports them.

Fix shared mechanisms, not only these words. Review overlapping families, stale source assertions, exclusions, type hierarchies, form constraints, mixed products, and negative phrases. Define direct-form restrictions per supported family/meaning where necessary; a global form word must not accidentally suppress valid presentations in another family.

Make specific queries preserve their qualifiers. Rice cakes must not mean rice OR cakes. Support reviewed Spanish/English equivalents, Argentine vocabulary, accents, plurals, abbreviations, mixed language, brands and package qualifiers without flattening distinct senses or blindly translating brand names.

Generate useful choices from actual catalog membership and the current trusted scope before result pagination. A choice must have a stable predicate ID and a nonempty evidence-backed membership. Keep a direct-product choice accessible when derived contexts exist. Permit further refinement where useful; one relevant clarification must not disappear merely because other choices are absent.

A selection must narrow the intended set, remain idempotent, and never restore unrelated rows after an empty result. Explicit equivalent queries and selecting their choice must agree. Handle overlapping dimensions without pretending their counts add up to a unique total.

Add independent regression expectations before changing the relevant rule. Re-running the same classifier on its own output is a consistency check, not semantic proof.

### 3. Make the existing HTML experience use the corrected engine

Trace and implement the complete active path:

    qualified discovery index -> backend -> Kotlin contract/bridge/coordinator -> existing HTML -> selections/paging -> existing offer engine

Relevant locations include tools/catalog_discovery, backend/service.py, ArgentinaBackendConsumerBridge.kt, ArgentinaBackendContract.kt, ArgentinaLiveSearchCoordinator.kt, and android/app/src/debug/assets/valuepilot_next_html.

Reuse the existing table, frozen product/brand column, actions under product names, column order, smooth scrolling, themes and Google Images flow. Do not redesign Search or start a new Compose migration.

Carry stable predicate IDs, language, scope, result cursor, facet cursor, and request generation through the whole path. Do not turn a selected meaning back into a free-text guess. Make all choices reachable with compact progressive disclosure or paging; remove the silent six-choice limit.

Advertise the new capability only for a qualified, matching index. Verify actual call sites consume it. A fallback must disclose unavailability and must not silently drop active selections. Keep empty results, missing offer evidence, authorization failure, stale index and temporary failure distinct. Prevent old responses from overwriting a newer query or selection.

Respect geography, store, and release scope using trusted existing data before presenting scoped counts. A catalog identity is not proof of an offer in Buenos Aires. Join prices and offers only through the existing qualified identity/package rules. Do not manufacture prices, stock, unit quantities, distances, or equivalence between different products.

Report distinct products and store offers with accurate labels. Duplicate retailer descriptions must not inflate the displayed product count. Retain differing packages and genuine branch offers. Equal verified store prices are valid; invented savings are not.

Verify a complete working path early, before another long run of coverage batches. Use an authorized local/development service and clearly isolated test fixtures if necessary. Runtime configuration gaps do not excuse leaving the client integration unwritten.

### 4. Make builds efficient and expand the whole catalog

Profile the compiler and query engine on the real baseline. Investigate repeated table scans, tokenization, rule evaluation and alias lookups. Optimize measured bottlenecks while proving logical output equivalence; do not remove validation or impose hidden catalog limits for speed.

Maintain a resumable queue covering every provider ID and linked identity. Include unresolved types, wrong assignments, modifier conflicts, language gaps, ambiguous senses, singleton names, and nonfood departments. Record source references, rule dependencies, review method and next action. A list of unknowns alone is not completion.

Process substantial batches of related source patterns. Prioritize essential shopping categories and high-impact defects, then cover all remaining departments and the long tail. Inspect distinct contexts and exceptions across all affected records before accepting a rule. Do not fill percentages by assigning generic food/other labels or fabricating translations.

Use a reusable bilingual concept dictionary, type hierarchy and role/composition rules. Do not enumerate every product against every other product or create thousands of redundant synonyms per SKU. New combinations of known types and modifiers should work through composition.

Future additions, edits and deletions must use the same deterministic pipeline. Changed rules must revisit affected existing and previously unresolved identities. Unknown new senses enter a persistent review queue; frequency alone must not auto-approve them.

A measured full offline refresh is acceptable initially. Describe it accurately. Add incremental processing only if it is needed and prove equivalence to a full rebuild at the logical record, assertion, membership, facet and alias levels.

Use versioned candidate artifacts with source/rule/schema/compiler identifiers, logical digests, integrity checks, recoverable prior versions and atomic activation after qualification. Do not replace a working artifact before its candidate passes the required checks.

### 5. Prove behavior, coverage, performance and phone integration

Keep four evidence categories separate: structural accounting, semantic correctness, language retrieval, and actual runtime behavior.

Structural checks must cover the entire corpus: input accounting, qualified keys, literal/ID reachability, evidence provenance, conflicting rows, acyclic hierarchy, orphan references, source/index compatibility, every predicate's pages, counts, and strict selection. Test contradictory and invalid selections, final singleton choice pages, more than six choices, more than 31 products, stale cursors, cancellation, request races and out-of-scope records.

Create a versioned semantic evaluation set independently of the classifier. Record how expected memberships were reviewed. Cover every rule family and risk pattern, retailer variations, ordinary and rare products, and all observed departments. Include a held-out evaluation of at least 1,000 distinct provider groups, with related retailer copies kept in the same split. Do not tune on the held-out set and then report it as untouched. Newly discovered errors become regressions; maintain fresh independent evaluation evidence.

Measure precision and recall only where reference membership is established. A handpicked list of positives cannot establish whole-catalog recall. Run semantic tests for both languages, compounds, senses, negation and selections. Inspect false positives and missing expected products; fix known reproducible errors before qualifying the affected feature.

Target at least 95% useful type coverage and 95% verified useful bilingual retrieval coverage, separately at identity and fully covered provider-group levels. These are acceptance targets, not achieved claims. Department membership or an alias flag cannot substitute. Report exact numerators, denominators, partial groups and conflicts. Every residual must have a concrete disposition; unfinished rules must not be mislabeled as missing source evidence. Continue examining residuals even after reaching the thresholds.

Apply the engineering specification's performance targets and scale checks: real baseline plus 100k/250k synthetic identities; warm index query/count p95 <=100 ms and warm backend search p95 <=500 ms excluding external network. Record hardware, workload, memory, build duration, cold/warm results, and deviations. No catalog classification on the phone or per keystroke. Preserve completeness while bounding requests, rendering and caches.

Run focused tests during meaningful changes and the required full suites at integrated checkpoints:

    python -m unittest discover -s tools/tests -p 'test_*.py'
    python -m unittest discover -s backend/tests -p 'test_*.py'

When Android/client code changes, run relevant HTML/contract tests and, from android:

    .\gradlew.bat --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

Install only the isolated preview alongside the original Play-installed application. On the authorized Motorola Edge 2025, verify English/Spanish queries, clarification, further refinement, all result/choice pages, store/sort changes, counts, prices from the existing engine, and fast successive queries. Check the known failures above and independently selected categories.

Also verify Search -> product -> Google Images -> expand -> close -> exact same product, query, selections, tab and scroll position. Preserve saved/basket actions. Capture actual light/dark JPG screenshots and keep an evidence checklist tying the installed APK and runtime index to the tested code. Do not substitute simulated screens for device evidence.

If the phone or live development service is unavailable, finish independent code and contract work, report the exact remaining runtime gate, and leave it unverified. Do not label the entire search goal complete.

## Execution discipline and boundaries

- Work from the current state; keep a short gate ledger with PASS/FAIL/NOT RUN, evidence, next action and checkpoint. Read relevant updates, not the whole accumulated history each turn.
- Correct known semantic defects and establish the active HTML path before spending the whole run adding tiny families. Batch related vocabulary work and rebuild at useful integration boundaries.
- Keep one full build owner. Retain process/session handles and exit status. Add progress instrumentation when useful; do not repeatedly launch the same tests or builds after losing a handle.
- Do not spend long periods narrating unchanged CPU activity. Use brief meaningful updates and perform independent work while a process runs.
- Fix failed checks before advancing dependent work. Do not weaken expected results, hide failures, change denominators, or treat a larger count as evidence of correctness.
- Preserve private saved data, the shopping/value engine, backend security, Firebase/App Check, production credentials, signing, original package and Play-installed app.
- Do not publish to Play, promote production, bypass authorization, change credentials, add paid services, upload the catalog externally, or add mandatory remote AI. Do not restart an emulator setup without a material environment change.
- Keep restricted catalog/review records local and ignored. Commit reusable rules, code, safe fixtures, aggregate reports and verified development checkpoints.
- Keep the selected model and existing task. Do not create other chats or delegate agents without user authorization.
- Continue authorized implementation without routine permission questions. Respect pauses and budget limits. If genuinely blocked, state the exact missing evidence/access and completed independent work. Do not loop over an unchanged blocker.

## Completion contract and final deliverables

Mark this Search goal complete only when:

1. All valid source identities are accounted for, reachable and correctly associated with their original evidence; malformed/conflicting records have explicit handling.
2. Known reproduced semantic defects are fixed, independent evaluation is recorded, coverage targets above are demonstrated, and unresolved evidence limitations are precisely documented.
3. The actual HTML preview uses the qualified discovery flow, preserves selected meanings and navigation, and reaches every supported choice and result without hidden truncation.
4. Existing qualified prices, package calculations, stores, offers, saved data and security retain their authority.
5. Refresh behavior, artifact integrity, tests, performance and Motorola interaction checks have passed against identified code/index/APK versions.
6. Changes are saved on a recoverable development branch with the exact tested commit, artifact hashes and a clean tracked working tree after preserving any unrelated user changes.

If any required gate is unmet, report the milestone as incomplete or blocked with the specific gap. Do not promise perfect interpretation of arbitrary future descriptions. Do not mark the entire ValuePilot redesign complete.

Deliver a concise plain-language explanation of corrected searches; reproducible coverage and independent accuracy results; failures or unrun checks; actual activation state; Git checkpoint; APK location and SHA-256 when built; and actual Motorola JPG screenshots.

Begin with recovery and the independent failing semantic cases. Then implement the shared corrections and the functioning HTML path. Carry the work through the remaining gates when the goal is active.

---

Authoring reference: the goal's explicit outcome, evidence, constraints, iteration policy and stop conditions follow official OpenAI guidance at https://developers.openai.com/cookbook/examples/codex/using_goals_in_codex. Catalog facts and defects above come from this repository's local review; the external guidance does not establish their correctness or promise completion in one run.

