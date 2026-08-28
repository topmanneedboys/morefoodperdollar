# Current state

Updated: 2026-08-28

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

Current built-in Search data is explicitly fictional sample evidence. It is not presented as live retailer pricing, inventory, promotions or availability. Sample fixtures remain useful for deterministic offline development and regression testing.

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

Providers supply evidence. Providers do not decide ValuePilot rank.

Evidence freshness is evaluated using caller-supplied time and explicit policy. The shared core does not read a system clock.

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

5D research now has deterministic cross-source and provider-import safety layers before any production provider integration.

Shared core includes:

- checksum-aware GTIN validation
- source-isolated evidence namespaces
- explicit storage-boundary metadata for open/share-alike, open-government, proprietary/restricted, user-controlled, and unknown datasets
- deterministic evidence conflict policy
- deterministic N-source fact resolution
- evidence-backed unit-value gating
- a provider-neutral staged offer-import contract that preserves raw provider price fields without prematurely selecting a canonical current price

Permanent rule:

**sources contribute claims; they do not overwrite one shared product row.**

Claims only compete when they describe the same factual domain and exact scope. A merchant web offer, a physical-store observation, a historical observed price, product metadata, and a market benchmark can coexist without being flattened into one value.

When a stronger claim deterministically defeats weaker conflicting claims, the stronger value may resolve. When equally credible claims disagree and no deterministic rule resolves them, the fact remains an unresolved conflict and blocks ranking rather than being averaged, voted, or guessed.

This protects against conflicts such as:

- current merchant price versus historical observed price
- online versus physical-store price
- same GTIN with conflicting package quantity
- proprietary merchant data versus open metadata
- stale observations versus current authoritative offers

## Source-isolated evidence index

`SourceIsolatedEvidenceIndex.kt` now provides the bounded platform-neutral in-memory repository prototype.

It:

- preserves dataset namespaces and storage-boundary metadata;
- stores source claims without flattening provenance;
- supports bounded lookup by stable product key;
- delegates fact resolution to `EvidenceFactResolver`;
- rejects same-namespace claim-ID collisions;
- can remove one dataset namespace without mutating another provider.

This is an engineering/source-isolation mechanism, not a legal conclusion about any dataset licence.

## Open-data bootstrap status

Open Food Facts / Open Prices / open-government data remain useful evidence rails, but they are not treated as universal live Canadian merchant-price feeds.

Open Prices remains suitable for proof-backed observed/historical prices and testing identity/freshness behavior. Open Food Facts remains suitable for product identity/package metadata enrichment when supported by stable identifiers. Government datasets remain reference/regulatory/benchmark evidence rather than retailer-specific current prices.

A cross-source join cannot make stale price evidence current, cannot invent geography, and cannot upgrade weak metadata into authoritative merchant price evidence.

### Open Food Facts quantity support

The network-free mapper now supports two explicit quantity bases:

- structured positive whole-product `g` / `ml` metadata; and
- exact displayed supplement package counts such as `100 tablets`, `60 capsules`, `90 gummies` and a narrow equivalent French vocabulary.

Supplement count is accepted only when the **entire source quantity field** matches a strict integer + allow-listed dose-form syntax.

It deliberately does not parse product names/descriptions, dosage strength, ranges, multipliers or mixed strings such as `60 capsules x 500 mg` into authoritative count.

Open Food Facts quantity remains separately attributed `SOURCE_ASSERTED_METADATA` and cannot overwrite stronger merchant-authoritative quantity.

The Android build containing this count support passed on commit `450d615c7143dee12e3f5e62b2db03b5effba9db`.

## Merchant feed qualification infrastructure

5D includes offline feed-quality tooling before the first production provider adapter exists.

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
- separate Retail Price / Sale Price qualification and relationship measurement
- CAD and mixed-currency measurement
- availability-value measurement
- UPC/GTIN checksum validation
- URL validation
- SKU/Product ID conflict measurements
- Class ID distribution
- `TRL` record and declared-product-count validation
- bounded/truncated-run behavior that does not pretend a trailer was checked

Important Rakuten trust rules are encoded/documented:

- the file header timestamp is not promoted to per-product freshness
- Retail Price and Sale Price remain separate source semantics until explicitly resolved
- CAD does not alone prove Canadian geography
- the generic Rakuten primary schema does not provide universal structured package quantity
- class-specific Size text is not automatically promoted to grams/millilitres/count
- feed access never equals caching/indexing/display/mobile rights

The qualification CI workflow compiles and tests the generic/Rakuten qualification harnesses independently of the Android/browser build.

Raw authorized provider feeds and generated reports are intentionally ignored under:

- `local-provider-data/`
- `local-feed-reports/`

They must not be committed to the public repository.

## First authorized real merchant feed: Jamieson Vitamins / Rakuten

Rakuten technical Product Catalog access is enabled and Jamieson advertiser Product Feed approval/file availability are proven.

The complete authorized Jamieson compressed TXT feed was downloaded and audited offline. The proprietary catalog file is not committed.

Sanitized empirical checkpoint:

- 273 product rows; trailer count matches
- all 273 rows have the documented 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs
- 273 unique source Product IDs
- UPC/GTIN present on 271/273; all 271 supplied values checksum-valid
- product URL valid on 273/273
- image URL valid on 273/273
- manufacturer present as Jamieson on all 273
- description present on 272/273
- Class ID blank on all 273 rows
- Sale Price < Retail Price on 48 rows
- Sale Price = Retail Price on 223 rows
- Sale Price > Retail Price on 2 rows

Conclusions:

- actual advertiser feed authorization/file availability are proven;
- two inverted Sale-vs-Retail relationships prove price-field names cannot be treated as discount semantics blindly;
- package count is not established by the generic Rakuten feed;
- there are 273 structural offer candidates but still 0 authoritative unit-value candidates until quantity/count is separately established;
- production caching/indexing/display/mobile rights remain unresolved;
- supplement marketing claims are not efficacy/safety/treatment ranking evidence.

See `RAKUTEN_JAMIESON_FEED_AUDIT_2026-08-28.md`.

## Provider-neutral staged offer import

`ProviderOfferImport.kt` preserves the fields needed from provider rows without committing ValuePilot to Rakuten or prematurely constructing a canonical Offer.

It preserves:

- provider item ID
- SKU
- supplied GTIN plus validated stable identity when checksum-valid
- source URL/image URL fields
- availability text
- separate Retail Price and Sale Price raw values and optional parsed Money
- dataset/file-generation metadata separate from per-offer observation time

Its price semantics remain explicitly unresolved until an advertiser/feed-specific semantic resolver is justified.

The full Android/browser build for commit `4b423af63a64abd403ef01baf3821e65e112bd8b` passed, including privacy-boundary checks.

## Rakuten × Open Food Facts quantity-coverage research tool

`tools/measure_rakuten_off_quantity_coverage.py` now provides a privacy-safe bounded way to measure whether the 271 valid Jamieson GTINs have useful independent package metadata.

It:

- reads the local authorized Rakuten feed;
- validates the complete trailer/product count;
- holds GTINs in memory only;
- queries a narrow Open Food Facts structured-search field projection in controlled batches;
- uses exact displayed supplement counts first, otherwise structured `g`/`ml` metadata;
- fails closed on conflicting duplicate quantity candidates;
- emits aggregate JSON/Markdown only;
- never emits GTINs, catalog rows, product URLs, provider credentials or account identifiers;
- keeps `production_authorized = false`.

Synthetic regression tests explicitly verify that the serialized aggregate report contains neither tested GTINs nor source product text.

The `Test merchant feed qualification` workflow compiled the tool and passed the test suite on commit `3c4dfa8cfe2f8fd6de4c3a503983de52c26bed7a`.

The real Jamieson feed **has not yet been run through this new quantity-coverage tool**, so no real exact-count coverage percentage is proven yet.

See `RAKUTEN_OFF_QUANTITY_COVERAGE.md`.

## Deterministic value engine

The core remains responsible for:

- exact money handling
- quantity normalization
- promotions
- product identity
- comparison ranking
- value calculations

Explicit measurable evidence has priority over weaker heuristic evidence.

For example, when count evidence is available for comparable egg products, SMART ranking uses unit value rather than interpreting words such as "large" or "family" as stronger portion evidence.

AI or semantic enrichment cannot override explicit price or quantity evidence.

## Current architecture

Permanent product direction:

Authorized/open/user evidence providers

↓

source-isolated typed evidence claims/import records

↓

deterministic conflict resolution and evidence trust boundary

↓

shared deterministic ValuePilot core

↓

application state and ranking

↓

presentation clients

ValuePilot does not depend on Accessibility, overlays, OCR, a specific retailer, affiliate network, open dataset, or any single capture method.

Accessibility and OCR remain optional adapters. The product must continue functioning if any one capture, data-provider, or presentation adapter is removed.

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

5C4C onward trust/provider-core changes do not require a new physical-device acceptance checkpoint before a deliberately selected production real-world provider exists.

## Privacy boundary

Current Android build still has:

- no INTERNET permission
- no ACCESS_NETWORK_STATE permission
- no account requirement
- no telemetry
- no remote AI dependency
- no ValuePilot server dependency

Provider/open-data qualification work remains offline/research tooling plus shared deterministic logic. It does not add production networking to Android.

A network permission must not be added merely because the architecture can support a remote provider.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection

The milestone has now progressed beyond merely obtaining a first feed: Jamieson/Rakuten is the first actual authorized merchant-feed validation case.

The remaining gates are still intentionally separate:

- exact provider field semantics
- package quantity/count quality
- cross-source identity/conflict behavior
- caching/persistence/indexing rights
- consumer display/mobile rights
- freshness/update model
- production networking/privacy impact
- provider resilience beyond one advertiser/network

Current highest-value next gate:

**run the privacy-safe Rakuten × Open Food Facts quantity-coverage tool locally against the existing authorized complete Jamieson `.txt.gz` feed and retain only the aggregate report.**

That measurement should establish:

- how many valid Jamieson GTINs match Open Food Facts;
- how many have an exact accepted tablet/capsule/gummy/etc. count;
- how many only have mass/volume metadata;
- how many have conflicting quantity metadata;
- how many remain unresolved.

Only after useful count coverage is proven should those separate quantity claims be assembled through the existing source-isolated index, conflict resolver and evidence-backed unit-value gate.

Only after provider semantics **and** rights gates pass should ValuePilot implement a first production real-data adapter or add required network permissions.

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

ValuePilot ranking remains independent of provider business incentives. The deterministic ValuePilot engine remains responsible for comparison and value decisions.
