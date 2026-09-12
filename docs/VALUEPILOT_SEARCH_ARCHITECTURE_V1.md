# ValuePilot Search Architecture v1

This document is the contract for product retrieval in the Argentina backend.
It is deliberately separate from the Android application, shopping planner,
price evidence and inventory systems.

## Permanent invariants

- SearchPack v2 is the retrieval architecture.
- Normal requests never scan a regional catalog; the bounded pre-SearchPack
  path exists only for compatibility with explicitly legacy releases.
- `DISCOVERY` search and `EXACT` identity resolution are separate operations.
- Broad discovery may have thousands or millions of matching identities.
- Discovery returns a bounded, deterministically ranked top-K result.
- Exact resolution never guesses an identity; ambiguity stays explicit.
- Once an identity is safely known, its `productEvidenceKey` is used directly.
- Location routing is separate from product retrieval.
- Location narrows regions and stores before price evaluation.
- Product search does not claim inventory availability.
- Price evidence is evaluated only after product and store narrowing.
- R2 request count is bounded by query complexity and top-K, not corpus size.
- SearchPack uses one global byte-bounded cache across regions.
- Immutable releases are content-addressed and independently verifiable.
- `ACTIVE` is only a release pointer; it is not product or price authority.
- Corrupt or missing evidence fails closed.

The invariants may change only after measured evidence demonstrates an
architectural defect and a separately authorized migration preserves the
deterministic and fail-closed boundaries.

## Operation boundaries

`POST /v1/search` is **DISCOVERY**. It validates the query and location,
routes the location, searches only the selected SearchPack regions, merges
and deduplicates ranked identity candidates, and returns a bounded top-K list.
It does not ask the exact-identity interpreter to choose one SKU and does not
claim price, stock or availability.

`POST /v1/interpret` is **EXACT IDENTITY INTERPRETATION**. It retains the
candidate horizon, aliases, safe corrections, package and dimension checks,
ambiguity protection and fail-closed behavior. A broad term such as `arroz`
may therefore remain `NEEDS_CLARIFICATION` here.

`POST /v1/shop-text` is **SAFE INTERPRETATION + SHOPPING**. It runs exact
interpretation first and passes a qualified `productEvidenceKey` through the
private backend boundary when available. The public structured `/v1/shop`
mapping cannot inject that key.

Search results are identity discovery only. Current price, promotion
eligibility, store evidence and inventory availability remain independent
facts and are evaluated by the existing evidence and shopping layers.
