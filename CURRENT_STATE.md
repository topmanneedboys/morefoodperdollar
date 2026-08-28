# Current state

Updated: 2026-08-27

Branch: work/valuepilot-android-milestone

Android version: 101.1.0 (10101)

## Completed

### Permanent Android foundation

Completed milestones:

- 5B1 standalone comparison application layer
- 5B2 standalone comparison screen
- 5B2A real-device comparison hardening
- 5C1 immutable Android application shell state
- Parser regression fix preserving names beginning with "reg"
- 5C2 permanent Android app shell
- 5C3A Universal Search application foundation
- 5C3B first consumer Universal Search experience
- SMART ranking fix preferring explicit measurable evidence over heuristic portion fallback
- 5C4A permanent shopping evidence provenance contract
- 5C4B Universal Search migration to typed shopping evidence
- 5C4C deterministic evidence acceptance and freshness policy
- 5C4D Universal Search evidence-trust enforcement
- 5C4E promotion-provenance ranking hardening

Current primary navigation:

Home
Search
Basket
Saved

Compare remains a workflow, not a primary navigation tab.

## Universal Search status

Search is now a real application workflow.

Permanent flow:

Search UI

↓

immutable UniversalSearchState

↓

typed UniversalSearchIntent

↓

UniversalSearchController

↓

replaceable ProductSearchProvider

↓

ShoppingEvidence

↓

deterministic evidence acceptance

↓

deterministic parsing and relevance

↓

rankable versus reference-only separation

↓

deterministic ranking

↓

bounded presentation results


Current Search guarantees include:

- normalized and bounded human queries
- monotonically increasing request identities
- stale-result rejection
- stale-error rejection
- bounded provider observations
- bounded visible results
- provider-independent ranking
- mixed-currency ranking protection
- truthful no-results state
- no fabricated provider evidence
- typed provider and source provenance
- explicit sample versus real-world evidence
- explicit observation timestamps
- explicit availability evidence
- explicit promotion evidence
- explicit unknown and unverified states
- caller-supplied freshness evaluation time
- no hidden clock in deterministic core
- stale evidence cannot silently win Best Value
- future-dated invalid evidence can be rejected
- display-only evidence cannot influence Best Value
- unavailable evidence cannot influence Best Value
- weak inferred evidence cannot silently become trusted evidence
- parsed value-changing promotions require explicit promotion provenance before ranking
- unverified value-changing promotions remain reference-only

The first Android Search experience is physically verified on-device.

Current built-in Search data is explicitly fictional sample evidence.

It is not presented as live retailer pricing, inventory, promotions or availability.

Sample fixtures remain useful for deterministic offline development and regression testing.

## Real Shopping Evidence Contract

Milestone 5C4 is complete.

The permanent provider-neutral evidence envelope is ShoppingEvidence.

Shopping evidence can describe:

- provider identity
- source/store identity
- source product identity
- observation time
- sample, real-world or unknown environment
- acquisition channel
- observation claim type
- availability
- promotions
- freshness

Providers supply evidence.

Providers do not decide ValuePilot rank.

Evidence freshness is evaluated using caller-supplied time and explicit policy.

The shared core does not read a system clock.

Evidence acceptance produces one of three deterministic dispositions:

- RANKABLE
- DISPLAY_ONLY
- REJECTED

This allows ValuePilot to show useful but uncertain information without allowing it to silently influence a Best Value decision.

Current trust behavior includes:

- fresh trusted real-world evidence may rank
- aging evidence may rank according to explicit policy
- stale evidence is reference-only by default
- unknown-freshness real-world evidence is reference-only by default
- implausibly future-dated evidence is rejected
- unknown environment or channel cannot silently rank
- inferred or unknown observation claims cannot silently rank
- out-of-stock or unavailable evidence cannot rank
- low-stock evidence may rank with a warning
- unknown availability remains explicitly unknown
- expired promotions cannot influence Best Value
- inferred or unknown promotion claims cannot influence Best Value
- parsed BOGO or other value-changing promotion arithmetic cannot improve rank without explicit PromotionEvidence

The evidence hierarchy remains monotonic:

explicit source evidence

↓

deterministic parsed or derived evidence

↓

bounded heuristic evidence

↓

optional semantic or AI assistance

Later or weaker evidence must not overwrite stronger explicit money, quantity or provenance evidence.

## Multi-source evidence hardening

5D research now has a deterministic cross-source safety layer before any production provider integration.

Shared core includes:

- checksum-aware GTIN validation
- source-isolated evidence namespaces
- explicit storage-boundary metadata for open/share-alike, open-government, proprietary/restricted, user-controlled, and unknown datasets
- deterministic evidence conflict policy
- deterministic N-source fact resolution
- evidence-backed unit-value gating

Permanent rule:

**sources contribute claims; they do not overwrite one shared product row.**

Claims only compete when they describe the same factual domain and exact scope. A merchant web offer, a physical-store observation, a historical observed price, product metadata, and a market benchmark can coexist without being flattened into one value.

When a stronger claim deterministically defeats weaker conflicting claims, the stronger value may resolve. When equally credible claims disagree and no deterministic rule resolves them, the fact remains an unresolved conflict and blocks ranking rather than being averaged, voted, or guessed.

This is the permanent protection against later provider conflicts such as:

- current merchant price versus historical observed price
- online versus physical-store price
- same GTIN with conflicting package quantity
- proprietary merchant data versus open metadata
- stale observations versus current authoritative offers

## Open-data bootstrap status

Open Food Facts / Open Prices / open-government data remain useful evidence rails, but they are not treated as universal live Canadian merchant-price feeds.

Current architecture preserves source/licence isolation so open/share-alike datasets are not blindly flattened together with proprietary merchant feeds.

Open Prices remains suitable for proof-backed observed/historical prices and testing identity/freshness behavior. Open Food Facts remains suitable for product identity and metadata enrichment when supported by strong identifiers. Government datasets remain reference/regulatory/benchmark evidence rather than retailer-specific current prices.

A cross-source join cannot make stale price evidence current, cannot invent geography, and cannot upgrade weak metadata into authoritative merchant price evidence.

## Merchant feed qualification infrastructure

5D now includes offline feed-quality tooling before the first production provider adapter exists.

Generic qualifier:

`tools/qualify_merchant_feed.py`

Capabilities include:

- local CSV/TSV/delimited text/XML/gzip input
- conservative field mapping
- explicit failure on ambiguous auto-mapping
- explicit XML record tag requirement
- bounded row processing
- currency/price/identity/GTIN/quantity/availability/URL/timestamp coverage
- duplicate and conflicting identity-scope measurements
- structural current-offer and unit-value candidate counts
- explicit rights gate that the tool cannot approve

Rakuten-specific qualifier:

`tools/qualify_rakuten_product_catalog.py`

Capabilities include:

- pipe-delimited `.txt` / `.txt.gz` Product Catalog validation
- `HDR` record validation
- documented primary-field shape checks
- required-field coverage
- Retail Price / Sale Price qualification coverage
- CAD and mixed-currency measurement
- availability-value measurement
- UPC/GTIN checksum validation
- URL validation
- SKU/Product ID conflict measurements
- Class ID distribution
- `TRL` record and declared-product-count validation
- bounded/truncated-run behavior that does not pretend a trailer was checked

Important Rakuten trust rules are now encoded/documented:

- the file header timestamp is not promoted to per-product freshness
- Sale Price is only a qualification price candidate until real advertiser semantics are validated
- CAD does not alone prove Canadian geography
- the generic Rakuten primary schema does not provide a universal structured package quantity
- class-specific Size text is not automatically promoted to grams/millilitres/count
- feed access never equals caching/indexing/display/mobile rights

The qualification CI workflow compiles and tests the generic and Rakuten qualifiers independently of the Android/browser build.

Raw authorized provider feeds and generated reports are intentionally ignored under:

- `local-provider-data/`
- `local-feed-reports/`

They must not be committed to the public repository.

See `MERCHANT_FEED_QUALIFICATION.md` for the operational gate when the first real feed arrives.

## Deterministic value engine

The core remains responsible for:

- exact money handling
- quantity normalization
- promotions
- product identity
- comparison ranking
- value calculations

Explicit measurable evidence has priority over weaker heuristic evidence.

For example, when count evidence is available for comparable egg products,
SMART ranking uses unit value rather than interpreting words such as
"large" or "family" as stronger portion evidence.

AI or semantic enrichment cannot override explicit price or quantity evidence.

## Current architecture

Permanent product direction:

Authorized/open/user evidence providers

↓

source-isolated typed evidence claims

↓

deterministic conflict resolution and evidence trust boundary

↓

shared deterministic ValuePilot core

↓

application state and ranking

↓

presentation clients


ValuePilot does not depend on Accessibility, overlays, OCR,
a specific retailer, affiliate network, open dataset, or any single capture method.

Accessibility and OCR remain optional adapters.

The product must continue functioning if any one capture, data-provider, or presentation adapter is removed.

## Physical Android verification

Verified on physical device:

- Home
- Search
- Basket
- Saved
- standalone comparison
- comparison persistence
- portrait shell
- landscape shell
- consumer Search experience
- Search quick queries
- exact unit-value ranking
- exact volume-value ranking
- truthful no-results behavior
- bottom navigation and system-bar spacing

5C4C onward trust-layer changes do not require a new physical-device acceptance checkpoint before a deliberately selected real-world provider exists.

## Privacy boundary

Current Android build still has:

- no INTERNET permission
- no ACCESS_NETWORK_STATE permission
- no account requirement
- no telemetry
- no remote AI dependency
- no ValuePilot server dependency

The new provider/open-data qualification work is offline tooling and shared deterministic logic. It does not add production networking to Android.

A network permission must not be added merely because the architecture can support a remote provider.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection

Goal:

Select the first legally and commercially suitable source of real shopping evidence before adding a production network boundary.

This milestone is provider research, empirical feed qualification, and architectural selection first, not blind API implementation.

Evaluate candidate providers on:

- explicit authorization and permitted use
- Canadian coverage
- retailer and store coverage
- grocery and general-product breadth
- current-price quality
- package size and quantity quality
- promotion support
- availability or inventory support
- stable product identifiers
- store/source identifiers
- observation timestamps and freshness
- geographic precision
- search capability
- rate limits
- latency and reliability
- caching rules
- display and redistribution rights
- commercial-use rights
- attribution requirements
- pricing and expected operating cost
- scalability
- vendor lock-in risk
- long-term availability

The provider-selection milestone should produce:

1. a researched candidate comparison
2. a selected first provider or an explicit decision that no candidate is yet suitable
3. the exact evidence fields that provider can supply
4. the authorization and commercial constraints
5. an empirical qualification report from the actual authorized feed/API where possible
6. a bounded integration design
7. expected network permissions and privacy impact
8. expected cost and scale limits
9. failure and fallback behavior

Current highest-value next gate:

**obtain the first actual authorized merchant feed/API response and run it through the offline qualification layer.**

For a Rakuten Product Catalog text feed, use the complete `.txt.gz` file first. Do not begin with delta files as the first semantic/schema validation sample.

Only after a provider is deliberately selected and the real data/rights gates pass should ValuePilot implement the first production real-data adapter and add any required network permission.

5D does not authorize:

- unauthorized retailer scraping
- brittle private-endpoint reverse engineering
- checkout
- payment processing
- universal cart
- subscriptions
- affiliate influence on ranking
- remote AI
- telemetry

ValuePilot ranking remains independent of provider business incentives.

The deterministic ValuePilot engine remains responsible for comparison and value decisions.
