# Argentina consumer input intelligence and search safety V1

Status: `QUALIFIED_FOR_PROVIDER_NEUTRAL_BACKEND_INPUT_ONLY`

This bounded milestone adds a safe text-to-shopping-request layer above the
qualified provider evidence and below the existing M6 shopping engine. It
does not change the Android application, add Android networking, reparse the
national ZIP, or replace the existing M6/M7 planner and reader.

## What was built

Human text now follows one deterministic path:

`raw text -> normalization -> bounded list segmentation -> exact quantity and
intent -> catalog aliases and brands -> bounded typo candidates -> product-form
and dimension safety -> ambiguity gate -> existing M6 request`

The layer supports Spanish/Argentine and English vocabulary, accents and
punctuation normalization, decimal comma/point input, Unicode fractions,
package expressions, kg/g/l/ml/cc/count/docena/media docena, exact pound-to-gram
conversion, Coca-Cola/Sprite variants, and explicit resolution states. It never
turns a price publication into stock, and it never invents a product identity.

The catalog index is built only from source-labelled qualified identity records.
Exact token/prefix/trigram retrieval is followed by a bounded stdlib edit score;
there is no million-row blind fuzzy scan and no downloaded semantic model.
Vocabulary and product-form rules live in
`tools/data/argentina_input_aliases.json` and are versioned by policy.

## Safety gates

Bare tomato selects only an audited fresh form. Tomato paste, puree, sauce,
crushed tomato and juice require explicit wording. High-risk traps (fruit versus
juice, orange versus soda, chicken versus broth/flavour/pet food, milk versus
cosmetics, coconut versus shampoo, rice versus seasoning, egg/pasta/chocolate,
cheese/filled pasta, cereal/yogurt and sugar-free soda) never become an
automatic structured request in the fixture qualification. Unknown quantity,
package size, wrong dimension and multiple plausible products return explicit
clarification codes; M6 is skipped until the request is complete.

The public backend adds `POST /v1/interpret` and `POST /v1/shop-text`. The
existing `/v1/search` contract remains compatible while exposing the original
query, interpreted product, resolution and correction. `shop-text` delegates to
the existing reader and M6 engine only after all lines are safe and complete.

## Deterministic qualification

- 3,000 mutation cases use a frozen development/holdout split (2,100/900) with
  SHA-256 `ee98484fc9714023854f9e68148dff5189601926139ee6dfc9159effe33e1d1d`.
- The audited typo set resolved 5/5 as safe corrections. Every holdout case
  remained non-exact; this is a deterministic fixture evaluation, not an
  independent human-label claim.
- The 25-query fixture search check has canonical, expanded and original-query
  precision-at-5 of 1.0.
- Clarification-code accuracy is 3/3 and unsafe automatic substitutions are 0
  across 11 high-risk trap cases.
- A 5,000-record bounded fixture measured warm mean latency of 4.288 ms for a
  one-line request and 132.145 ms for ten lines (p95 132.578 ms); both targets
  (50 ms and 250 ms) passed. Peak index-build allocation was 31,448,915 bytes.
- The SQLite FTS5 trigram capability is probed, but runtime behavior remains the
  provider-neutral deterministic index and does not depend on the optional
  capability.

## Preserved boundaries and evidence

The mobile 128-logical/32-physical winner and the backend 1024/32 server
profile remain unchanged. The completed M5 equivalence, all alternative
verification, and M7 evidence are reused. Per-request national range/packing
diagnostics were not rerun merely to improve reporting, as directed. The
national raw ZIP and expanded data remain local/ignored and are not Android
assets or Git inputs.

The requested starting code ancestor was `72b75d14306a880de9077aea4684b493a9eef4be`.
The candidate checkout already included the later promoted M7 documentation-only
finalization at `1e55bbd66b01ffdb6182320b726374bb171ae26e`; it was preserved
without reset or overwrite. The machine-readable report records this explicitly.

## Verification and promotion

The report is finalized only after the focused interpreter/backend tests, the
existing Python suite, Android tests/lint/build/privacy/signer checks, browser
checks, exact candidate-head CI and promoted-head provenance CI are green. No
national source-wide verifier is part of this milestone.

The final candidate and promoted SHAs and CI run IDs are recorded in the JSON
report and in the completion checkpoint once exact-head verification finishes.
