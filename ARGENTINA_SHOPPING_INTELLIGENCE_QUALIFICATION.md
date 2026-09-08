# Argentina shopping intelligence V1

Status: `BACKEND_TOOLING_QUALIFIED; ANDROID_NOT_AUTHORIZED`

This checkpoint adds a provider-neutral, exact shopping decision engine over
the already-qualified Argentina SEPA micro-partition evidence. It does not
repeat the Milestone 5 national verifier and it does not add Android
networking, Android data assets, live inventory, or UI integration.

## Starting point and proven data design

- Starting repository checkpoint: `bc5aa94c9d4715b8bd65be7df756d8f34baaa053`.
- Provider: `ARGENTINA_SEPA_PRECIOS_CLAROS` / Precios Claros - Base SEPA.
- Release: `2026-09-06`; currency: `ARS`.
- The first completed Milestone 5 benchmark verified all four alternatives,
  exact equivalence, and every payload gate. The selected existing root is the
  smallest passing design: **128 logical partitions and 32 physical packs**.
- The official ZIP, expanded national data, and generated roots are local
  ignored inputs. None is shipped to Android or committed to Git.
- This report reuses that completed evidence. Verification timing is
  `NOT_REMEASURED_PER_USER_DIRECTION`; only the bounded per-request diagnostics
  below were collected from the existing completed root.

## Architecture

```text
existing verified micro root
        -> Argentina provider adapter
        -> provider-neutral ShoppingRequest / OfferEvidence
        -> exact Shopping Intelligence Engine V1
        -> deterministic plans and safety-labelled diagnostics
```

The engine has no SEPA names, filesystem access, network access, Android
dependencies, clock reads, routing, or natural-language intent parsing. The
adapter owns only translation of qualified provider evidence. No business
authority is placed in a View.

## Request and evidence contract

- Requests are bounded to 1–10 explicit lines. Each line has text, an exact
  decimal amount, and an explicit `MASS`, `VOLUME`, or `COUNT` unit.
- Supported unit conversions are exact (`kg`/`g`, `l`/`ml`, and count
  aliases). Binary floating-point is never used for money or quantities.
- A package plan uses integer ceiling package count and reports requested,
  supplied, excess, package price, and exact line total. Unknown or
  incompatible package quantity stays unsupported; titles are never parsed to
  invent a quantity.
- One `productEvidenceKey` is selected per line. Brands, package identities,
  and unrelated products are never mixed. Only provider-valid GTINs are
  exposed as GTIN evidence.
- Only explicit fresh positive ARS list-price evidence is eligible for a
  current plan. Promotions remain attached evidence and are excluded from the
  base total unless eligibility is known. Availability remains `UNKNOWN`.
- A complete price plan is not a stock, pickup, delivery, ETA, fee, or route
  claim. Distance is straight-line Haversine only.

## Decision outputs

For each bounded request the engine deterministically returns:

- cheapest complete single-store plan;
- closest complete price-evidence store;
- cheapest two-store combination, with exact line assignment and savings
  context available for comparison to the cheapest single store;
- cheapest per-line lower bound, explicitly labelled as an unbounded reference;
- a Pareto frontier over exact total, store count, and maximum straight-line
  distance;
- diagnostics for nearby stores, candidates, package plans, single-store
  plans, pair combinations, incompatible/unknown quantities, and bounds.

Without an explicit policy, `bestSensibleChoice` is deliberately unresolved.
With a caller-supplied policy, the result names the exact rule and comparison;
there is no hidden money-per-kilometre or provider-weighted score.

## Existing-root diagnostics

The 12 scenarios below use deterministic count requests at a 2 km radius. Count
was chosen for real SEPA coverage; mass/volume conversion and package rounding
are covered by the provider-neutral deterministic tests. The regional provider
preparation time is shared by the three cached request sizes; engine time and
peak allocation are per request.

| Region | Lines | Nearby stores | Candidates | Accepted offers | Package plans | Complete single stores | Pair combinations | Two-store plans | Engine ms | Peak bytes | Ranges | Packs | Payload bytes | Cheapest complete total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| CABA | 1 | 9 | 5 | 30 | 30 | 9 | 36 | 0 | 5 | 104,634 | 5 | 5 | 6,533,545 | ARS 1,940.00 |
| CABA | 5 | 9 | 25 | 87 | 76 | 0 | 36 | 0 | 9 | 145,642 | 24 | 17 | 19,949,005 | unresolved |
| CABA | 10 | 9 | 50 | 114 | 100 | 0 | 36 | 0 | 13 | 190,074 | 38 | 21 | 29,730,911 | unresolved |
| Buenos Aires | 1 | 1 | 5 | 4 | 4 | 1 | 0 | 0 | 1 | 22,399 | 5 | 5 | 8,888,916 | ARS 1,940.00 |
| Buenos Aires | 5 | 1 | 25 | 16 | 13 | 0 | 0 | 0 | 2 | 25,476 | 23 | 19 | 25,882,118 | unresolved |
| Buenos Aires | 10 | 2 | 50 | 35 | 25 | 0 | 0 | 0 | 4 | 53,612 | 40 | 24 | 42,464,132 | unresolved |
| Córdoba | 1 | 1 | 5 | 5 | 5 | 1 | 0 | 0 | 1 | 24,573 | 5 | 5 | 3,702,541 | ARS 2,079.00 |
| Córdoba | 5 | 1 | 25 | 21 | 19 | 1 | 0 | 0 | 3 | 86,146 | 24 | 17 | 6,121,102 | ARS 16,666.00 |
| Córdoba | 10 | 2 | 50 | 43 | 34 | 1 | 0 | 0 | 7 | 173,246 | 40 | 23 | 8,203,007 | ARS 61,459.00 |
| Jujuy | 1 | 2 | 5 | 5 | 0 | 0 | 0 | 0 | 1 | 8,334 | 5 | 5 | 1,151,790 | unresolved |
| Jujuy | 5 | 2 | 25 | 19 | 6 | 0 | 1 | 0 | 3 | 34,290 | 24 | 17 | 1,439,785 | unresolved |
| Jujuy | 10 | 2 | 50 | 43 | 15 | 0 | 1 | 0 | 4 | 68,218 | 43 | 27 | 1,729,735 | unresolved |

“Unresolved” means the evidence did not prove a complete current-price plan
within the explicit radius; it is not a guessed total. The CABA and Buenos
Aires multi-line cases visibly demonstrate why partial store subtotals are not
ranked as complete baskets. Jujuy also demonstrates incompatible package
quantities remaining excluded.

## Search and safety boundaries

- The existing CABA 25-query search audit remains `GO` with precision@5
  `1.0000`; no recall claim is made.
- Existing Milestone 5 exact-equivalence and 128/32 payload-gate evidence is
  reused, not rerun.
- Promotions retain unknown shopper eligibility; availability is `UNKNOWN`.
- No fuzzy matching, GTIN repair, title-derived quantity, live stock claim,
  routing, or provider-economics ranking was introduced.
- The engine and adapter are backend/tooling-only. Android networking remains
  unauthorized and the production UI remains unauthorized for this data.

## Verification performed for this checkpoint

- Provider-neutral engine tests: exact money/quantity conversion, package
  ceiling, identity isolation, unknown quantity, promotions, availability,
  single/two-store plans, Pareto frontier, policy behavior, bounds, malformed
  input, repeat determinism, and Windows-shaped paths.
- Argentina adapter tests: fresh/stale evidence, explicit provider quantity,
  non-ARS rejection, unknown quantity, promotion retention, and no-inference
  safety flags.
- The full Python discovery suite and Android tests/lint/debug build/privacy/
  signer checks remain promotion gates. CI must run at the exact candidate
  commit before the already-proven 128/32 design is promoted.

## Limitations and next boundary

This milestone ends at the offline/backend shopping-intelligence contract. It
does not authorize Android integration, networking, national raw-data assets,
natural-language input, live inventory, flyers, images, community sharing, or
monetization. Semantic category/basket quality is not claimed by this report;
the existing finite search precision audit remains the only search claim.
