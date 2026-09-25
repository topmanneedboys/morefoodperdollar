# ValuePilot Authorized Shopping Data Provider Strategy

Research date: 2026-08-22

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Public official-document research complete.
Account-level and contractual validation remains required.
No production provider is authorized yet.

## Executive decision

ValuePilot will not be built around one shopping-data company.

The permanent strategy is a multi-provider evidence architecture with
different provider classes serving different jobs.

Initial validation order:

1. Awin
2. impact.com
3. CJ Affiliate
4. Rakuten Advertising

Parallel strategic validation:

- Flipp for Canadian local/store promotions and pricing
- GS1 Canada ECCnet for Canadian product identity and trusted product content

Later channel-specific opportunities:

- Amazon Creators API
- Instacart
- Uber Eats
- DoorDash
- retailer-direct partnerships and APIs

Enterprise escalation candidates:

- DataWeave
- NielsenIQ

Currently unsuitable as a primary ValuePilot source:

- eBay Browse API without explicit permission for ValuePilot's independent ranking
- Walmart Marketplace seller APIs as a consumer catalog source
- unauthorized retailer scraping
- private retailer endpoints
- anti-bot circumvention
- browser automation as a permanent data foundation

No production adapter should be implemented merely because a provider has an API.

A candidate must pass authorization, data-quality, Canadian-coverage,
commercial-use and product-fit gates first.

## Permanent product-versus-offer decision

A product does not have one universal price.

The same underlying product may have multiple simultaneously valid offers.

Example:

30-count eggs

- Walmart physical store:
  CAD 8.00

- Walmart direct pickup:
  potentially a different price

- Walmart direct delivery:
  potentially a different price

- Walmart through Uber Eats:
  CAD 10.00

These are not automatically conflicting observations.

They may represent distinct consumer offers.

Therefore the long-term ValuePilot model is:

Product identity

↓

zero or more Offers

Each Offer may eventually carry:

- merchant identity
- physical store identity
- geographic scope
- commerce channel
- fulfillment method
- current item price
- regular price
- sale price
- promotion terms
- membership requirements
- item availability
- delivery or service fees when explicitly known
- basket minimums when explicitly known
- observation time
- source provenance
- freshness
- evidence quality

ValuePilot must never collapse distinct channels into one supposed
"true retailer price."

## EvidenceChannel is not CommerceChannel

The existing EvidenceChannel describes how ValuePilot acquired evidence.

Examples:

- AUTHORIZED_API
- FIRST_PARTY_FEED
- USER_PROVIDED
- DEVICE_OBSERVED

That is different from how the shopper can buy the product.

Future commerce-channel concepts may include:

- IN_STORE
- RETAILER_WEB
- RETAILER_APP
- MARKETPLACE
- UBER_EATS
- INSTACART
- DOORDASH
- AMAZON
- UNKNOWN

Future fulfillment concepts may include:

- CARRY_OUT
- PICKUP
- RETAILER_DELIVERY
- THIRD_PARTY_DELIVERY
- SHIPPING
- UNKNOWN

These enums are conceptual requirements only at this milestone.

5D does not authorize implementing them yet.

A later explicit offer-domain milestone should decide their final type names
and location in the architecture.

## Truthful channel-price language

If Walmart in-store evidence says CAD 8 and Uber Eats evidence says CAD 10,
ValuePilot may say:

"CAD 2 higher through Uber Eats"

or:

"Channel price difference: +CAD 2"

ValuePilot must not automatically claim:

"Uber marked this up by CAD 2"

unless explicit evidence establishes why the prices differ.

A price difference is evidence.

Its business cause is a separate claim.

## Future whole-cost distinction

Item price and total acquisition cost are different concepts.

A delivery offer may eventually include:

- higher or lower item price
- service fee
- delivery fee
- small-order fee
- bag fee
- regulatory fee
- membership benefit
- promotion
- tip

Only explicitly known components may be included in a calculated total.

Unknown fees must remain unknown.

ValuePilot must not fabricate a delivered total.

This will matter strongly for the future Basket Optimizer milestone but is
not implemented in 5D.

## Provider classes

The provider strategy is divided by what type of truth each source can provide.

### Class A — broad authorized catalog and offer rails

Primary candidates:

- Awin
- impact.com
- CJ Affiliate
- Rakuten Advertising

Strength:

Large authorized merchant catalogs and online/e-commerce offer evidence.

Weakness:

A merchant catalog price does not automatically mean a physical-store
shelf price.

These providers are the preferred first validation class because they offer
a scalable way to acquire many products through supported publisher
relationships without retailer scraping.

### Class B — Canadian local and store promotion evidence

Primary strategic target:

- Flipp

Strength:

Canadian local shopping relevance, local offers, flyers, store-aware
promotional content and strong grocery alignment.

Weakness:

No suitable self-service public read API for ValuePilot was verified during
the public-document research pass.

Required approach:

Commercial partnership inquiry.

Prohibited approach:

Scraping or reverse engineering Flipp.

### Class C — product identity and trusted content

Primary candidate:

- GS1 Canada ECCnet

Secondary future candidates may include commercial product-content networks.

Strength:

Authoritative Canadian GTIN-oriented product data, bilingual content,
dimensions, food/nutrition information and product identity.

Weakness:

Product master data is not a substitute for current consumer offer pricing.

This source class should strengthen product identity and normalization rather
than pretend to answer "what does this store charge right now?"

### Class D — commerce-channel-specific evidence

Candidates:

- Amazon Creators API
- Instacart
- Uber Eats
- DoorDash
- future delivery marketplaces

Strength:

Channel-specific offers, product availability or shopping/fulfillment context.

Weakness:

Access, intended use and API surface vary greatly.

These sources must never be treated as the retailer's universal in-store price.

### Class E — retailer-direct

Future examples:

- approved retailer APIs
- retailer-authorized product feeds
- retailer commercial data partnerships

Strength:

Potentially highest authority for retailer/store-specific evidence.

Weakness:

Requires individual business relationships and can create integration
fragmentation.

The provider-neutral ShoppingEvidence boundary exists specifically so
retailer-direct adapters do not contaminate the deterministic core.

### Class F — enterprise retail intelligence

Candidates:

- DataWeave
- NielsenIQ

Strength:

Large-scale pricing, retail measurement and competitive intelligence.

Weakness:

Likely enterprise pricing, licensing restrictions and use cases aimed at
retailers/manufacturers rather than a consumer comparison application.

They should be evaluated only if cheaper authorized publisher/catalog rails
cannot meet ValuePilot's requirements or if ValuePilot reaches a scale where
enterprise data becomes economically justified.

## Awin

### Why it ranks first for validation

Awin explicitly identifies price-comparison publishers as major users of its
product-feed platform.

Its publisher tooling provides access to very large product-feed datasets.

Available feed information can include:

- product identifiers
- titles
- descriptions
- product links
- images
- price
- sale price
- sale effective dates
- availability
- availability dates
- expiration dates
- GTIN
- brand
- multipack
- product weight and dimensions
- unit pricing measurement
- unit pricing base measurement
- shipping information when supplied

Awin exposes feed last-update information so ingestion systems can avoid
re-downloading unchanged feeds.

That fits ValuePilot's eventual incremental-ingestion architecture well.

### Cost/access advantage

Awin's current Canadian publisher documentation describes a very small
refundable publisher-verification deposit.

That makes account-level feasibility relatively inexpensive to test.

### Critical limitation

Joining Awin does not prove that ValuePilot can use every merchant.

ValuePilot must still validate actual advertiser programs.

Required account-level questions:

- Which Canadian advertisers are available?
- Which expose product feeds?
- Which allow a comparison-shopping application?
- Which allow mobile-app presentation?
- Which permit the required caching/indexing behavior?
- Which have useful grocery or general-shopping breadth?
- Which provide enough quantity information?
- Which prices are online/catalog prices versus store-specific prices?
- How frequently are feeds updated?

### Decision

PRIMARY ACCOUNT VALIDATION TARGET.

Do not build the production adapter until at least one useful Canadian
advertiser/feed passes the validation gate.

## impact.com

### Strengths

impact.com gives partners access to brand product catalogs.

Its catalog API can expose fields including:

- catalog item ID
- campaign/brand identity
- product name
- description
- current consumer price
- original price
- currency
- discount percentage
- stock availability
- GTIN
- category
- multipack
- weight
- promotion relationships
- catalog last-updated time
- service areas

It can search across catalogs available to the partner.

Catalogs can also be obtained via file download, FTP or API depending on
brand configuration.

Large feeds do not require a phone to ingest the entire catalog.

### Important authorization property

A partner must join a brand to see that brand's catalog.

Brands can restrict catalog access and can restrict download methods.

Therefore impact.com network membership alone does not authorize arbitrary
catalog use.

### Cost

Current public impact.com material says publishers/creators can join its
partner marketplace without the merchant-side SaaS subscription.

Merchant pricing shown on impact.com's website is not ValuePilot's presumed
publisher cost.

### Decision

SECOND ACCOUNT VALIDATION TARGET.

Impact may become co-primary with Awin if its Canadian brand catalog coverage
is materially better.

## CJ Affiliate

### Strengths

CJ's official product-feed format is particularly aligned with ValuePilot.

Useful fields can include:

- unique product ID
- target country
- currency
- availability
- price
- sale price
- sale-price effective dates
- product dimensions
- product weight
- shipping
- unit pricing measurement
- unit pricing base measurement
- per-unit count measurement
- product details

CJ also provides a Product Feed API for publishers to search product data at
lower volumes rather than requiring every use case to download the full
catalog.

Its current publisher signup is free.

### Important authorization property

Advertisers can restrict which joined publishers can export their full feeds.

Therefore CJ must pass an advertiser-level permission check exactly like
Awin and impact.com.

### Decision

THIRD ACCOUNT VALIDATION TARGET.

Its standardized unit-pricing fields make it especially attractive for
grocery and household-goods comparison if Canadian advertisers are present.

## Rakuten Advertising

### Strengths

Rakuten explicitly describes Product Catalog as appropriate for:

- shopping-comparison sites
- price-comparison sites
- product-search sites

Its Product Search API provides fields including:

- advertiser ID and name
- SKU
- product name
- retail price
- sale price
- UPC
- descriptions
- buy link
- image

Product Search has a documented bounded request rate.

For larger scale, Product Catalog provides SFTP feeds.

Rakuten supports:

- full catalog files
- delta files
- category-specific files
- multiple languages/currencies
- timestamps

Its documentation includes Canadian French / CAD global-feed naming.

Delta files are particularly attractive for a scalable ingestion system
because only new, changed and deleted products need to be processed between
full reconciliations.

### Authorization

Product Catalog requires:

1. technical approval
2. individual advertiser approval

### Decision

FOURTH ACCOUNT VALIDATION TARGET and strong long-term secondary rail.

## Flipp

### Why it matters

Flipp is more strategically important to ValuePilot than a normal affiliate
network because its public material describes:

- localized retailer pricing
- local offers
- SKU/store-level data
- grocery
- pharmacy
- general merchandise
- home and garden
- local store catalogs
- offers tied to individual store addresses
- consumer search by location

This is much closer to the local-shopping problem than a generic national
e-commerce product feed.

### Limitation

No suitable self-service public read API for ValuePilot was verified from
official public material.

Public material instead emphasizes:

- retailer partnerships
- brand partnerships
- media partnerships

### Decision

STRATEGIC CANADIAN PARTNERSHIP TARGET.

A future partnership inquiry should specifically ask about authorized access
to:

- retailer identity
- physical store identity
- item identity
- localized price
- regular price
- sale price
- effective dates
- in-store versus online scope
- local availability if supplied
- geographic scope
- permitted caching
- permitted consumer comparison display
- API/feed access
- attribution
- commercial terms

Do not scrape Flipp.

## GS1 Canada ECCnet

### Why it matters

ECCnet Registry is a fundamentally different type of asset.

It is Canada's national product registry and contains continuously updated
Canadian product information from manufacturers, brand owners, distributors
and suppliers.

GS1 Canada exposes content-access mechanisms including an ECCnet Content API.

Useful product-content categories include:

- grocery
- general merchandise
- pharmacy
- food
- frozen
- bakery
- beverages
- household
- personal care
- electronics
- hardware
- many others

Potential ValuePilot uses include:

- GTIN identity
- canonical product metadata
- bilingual English/French content
- dimensions
- weight
- images
- nutritional information
- ingredient information
- allergen information
- brand-owner-certified data

### Authorization

ECCnet data access is controlled by publication rights from data owners and
subscription by data users.

ValuePilot must obtain the appropriate commercial/data-recipient rights.

### Crucial limitation

ECCnet product content is not presumed to be live retailer/store pricing.

### Decision

PARALLEL PRODUCT-IDENTITY VALIDATION TARGET.

Do not mix the concepts:

GS1 can help answer:
"What exactly is this product?"

An offer provider should answer:
"Who sells it, through what channel, where, for how much, and when?"

## Amazon Creators API

### Strengths

Amazon's current Creators API supports the Canadian marketplace.

Canadian locale support includes:

- CAD
- English Canada
- French Canada
- Grocery & Gourmet Food search

SearchItems provides product discovery.

OffersV2 can expose:

- availability
- merchant
- buying price
- price per unit
- saving basis
- deal details

### Constraints

Creators API is tied to the Amazon Associates program.

Current official prerequisites include qualifying-sales requirements before
API eligibility.

SearchItems also returns a small bounded number of items per request.

Offer information has explicit cache limits.

Amazon also warns that API price can differ from what a particular customer
sees because of customer/address context.

### Decision

USEFUL LATER CHANNEL-SPECIFIC PROVIDER.

It is not the first general Canadian shopping-data foundation.

Amazon offer evidence must be labeled as Amazon-channel evidence.

## Instacart

### Public Developer Platform

The public Developer Platform supports:

- nearby retailer discovery in Canada
- shopping lists
- product matching
- shopping experiences
- retailer discovery

Instacart describes real-time inventory and pricing as part of these shopping
experiences.

However, ValuePilot must not assume every public endpoint gives unrestricted
raw price results suitable for independent aggregation.

### Retailer Catalog APIs

Instacart's separate retailer Catalog APIs model an important distinction:

Product:
shared across a retailer's stores.

Item:
store-specific properties such as price and availability.

This validates ValuePilot's permanent product-versus-offer distinction.

### Decision

LATER DEEP PARTNERSHIP / COMMERCE CHANNEL.

Potential future roles:

- retailer discovery
- store-specific authorized data
- fulfillment handoff
- cart handoff
- approved product/offer partnership

Do not make ValuePilot depend on Instacart fulfillment.

## Uber Eats

### Important architectural evidence

Uber Eats' authorized Menu API has an explicit price model containing:

- marketplace/order price
- in-store price
- in-store discounted price

That proves channel price and physical-store price can legitimately differ for
the same merchant item.

Uber also supports store-specific menus and fulfillment-specific menu
behavior.

### Access constraint

Production store/menu scopes require approval and merchant/store
authorization.

Uber's own documentation says uses outside normal order-fulfillment
integration should have an aligned business agreement.

### Decision

FUTURE CHANNEL-SPECIFIC PARTNERSHIP.

Do not treat Uber Eats as an open public retailer catalog.

Do not scrape it.

## DoorDash

DoorDash Marketplace APIs are currently limited-access rather than generally
available.

The APIs focus on merchant integrations for menus, stores and orders.

Production integrations require approval/certification.

### Decision

FUTURE CHANNEL-SPECIFIC PARTNERSHIP ONLY.

Do not build ValuePilot around undocumented DoorDash consumer endpoints.

## eBay

The eBay Browse API is technically capable of product/listing search including
keyword and GTIN discovery.

However, current Buy API requirements state that partners/users are not
permitted to independently sort items beyond eBay's supplied sorting.

Independent ValuePilot ranking is a core product requirement.

### Decision

HOLD / NOT A RANKING SOURCE.

Only revisit eBay if ValuePilot receives explicit contractual permission for
its comparison/ranking use case.

API availability is not enough when API terms conflict with product behavior.

## Walmart Canada Marketplace APIs

Walmart Canada has official Marketplace APIs for:

- item management
- seller prices
- inventory
- promotions

However, those APIs are designed for Marketplace sellers and approved
Solution Providers managing seller listings.

They are not documented as an open consumer catalog/pricing API for arbitrary
Walmart retail inventory.

### Decision

NOT A CONSUMER DATA SOURCE.

A future direct Walmart relationship could change this conclusion.

Do not misuse seller APIs as a retail price feed.

## DataWeave

DataWeave publicly markets enterprise retail pricing intelligence with:

- regional/store-level pricing
- assortment
- availability
- multiple commerce channels
- normalized units
- location intelligence
- APIs
- exports
- S3/Snowflake delivery

This is technically close to some long-term ValuePilot requirements.

However, its public material also describes collection from retailer sites,
apps and commerce surfaces.

Before ValuePilot could use it in a consumer-facing product, the contract
would need to establish:

- underlying data rights
- consumer display rights
- redistribution rights
- caching rights
- geographic/store detail rights
- permitted derived ranking
- commercial price

### Decision

ENTERPRISE ESCALATION CANDIDATE, NOT FIRST SOURCE.

## NielsenIQ

NielsenIQ offers large-scale retail measurement and point-of-sale intelligence.

Its public material describes:

- tens of millions of products
- hundreds of thousands of stores globally
- store-level information
- pricing
- distribution
- promotion
- cross-channel measurement
- transactional price analysis

This demonstrates that ValuePilot's intended scale is technically realistic.

However, NIQ products are primarily enterprise measurement/analytics products,
not a self-service consumer shopping API.

### Decision

STRATEGIC ENTERPRISE DATA / VALIDATION CANDIDATE.

Do not assume consumer-display rights without a negotiated agreement.

## Multi-provider long-term architecture

The target shape is:

Authorized feeds and APIs
        ↓
provider-specific ingestion adapters
        ↓
raw provider records
        ↓
provenance-preserving normalization
        ↓
product identity
        ↓
multiple channel/store Offers
        ↓
freshness and evidence acceptance
        ↓
geographic/search index
        ↓
bounded candidate retrieval
        ↓
deterministic ValuePilot ranking
        ↓
small result set
        ↓
Android presentation

This architecture is descriptive only during 5D.

It does not authorize a backend implementation yet.

## Scale rule

Whole catalogs must never be downloaded to the consumer phone.

At large scale, one catalog can contain millions of products.

The phone should receive only the bounded candidate set necessary for a
specific consumer decision.

Future ingestion should prefer:

- provider update timestamps
- delta feeds
- incremental processing
- compressed transfers
- bounded concurrent downloads
- retry/backoff
- source health monitoring
- full reconciliation at controlled intervals
- deduplication by strong identity evidence
- geographic partitioning
- bounded cache lifetimes

Provider credentials must never be shipped as recoverable secrets in the APK.

## Product identity versus offer identity

Identity must not be based solely on retailer title text.

Strong future product identity evidence may include:

- GTIN
- UPC
- EAN
- provider product identifier
- manufacturer part number
- brand
- exact package quantity
- exact variant

Offer identity additionally needs:

- provider
- merchant
- store/location when known
- commerce channel
- fulfillment mode
- offer identifier
- geographic scope

Two Offers may point to the same Product.

That is expected behavior.

## Provider disagreement

Two authorized providers may disagree.

ValuePilot must not silently choose whichever source has the lowest price.

Future conflict handling must consider:

- source authority
- price scope
- store/location
- commerce channel
- observation time
- freshness
- explicit promotion conditions
- availability
- identity confidence

If evidence genuinely conflicts at the same scope, the conflict should remain
explicit until a deterministic conflict-resolution policy authorizes one
source to rank.

## Ranking independence

Affiliate or commercial economics are never product-value evidence.

The following may not improve rank:

- commission rate
- referral payout
- sponsorship
- CPC payment
- advertiser relationship
- provider preference
- business-development priority

A higher-paying advertiser must not receive a better ValuePilot rank because
it pays more.

Commercial metadata belongs outside deterministic value ranking.

## Provider failure behavior

Every external provider must be replaceable.

If a provider fails:

- do not fabricate evidence
- do not mark old evidence fresh
- preserve timestamps
- apply normal freshness policy
- stale evidence may become reference-only
- rejected evidence must not rank
- another provider may continue serving independent evidence
- sample mode remains explicitly sample
- Search remains bounded
- core deterministic calculations remain available

No single provider outage should make the permanent ValuePilot core unusable.

## Cost architecture

ValuePilot should minimize repeated remote requests.

At scale, prefer:

1. incremental authorized feeds where economical
2. provider update timestamps
3. normalized server-side indexing in a later authorized milestone
4. bounded cache reuse within provider terms
5. small query responses to clients
6. expensive enterprise data only when its incremental value justifies cost

Do not optimize for the cheapest data source if its authorization,
reliability or coverage is poor.

Do not optimize for maximum data volume if most of the data does not improve
consumer decisions.

## Acquisition robustness

A company whose shopping intelligence depends on one third-party API has
concentrated platform risk.

ValuePilot should instead own:

- provider-neutral evidence contracts
- product identity logic
- offer identity
- evidence trust/freshness rules
- deterministic value math
- comparison logic
- future basket optimization
- provider quality history
- normalization
- consumer decision experience

Third-party providers supply evidence.

They should not own ValuePilot's intelligence.

## Account-level validation sequence

Public-document research cannot establish which actual Canadian advertiser
programs will approve ValuePilot.

Therefore production implementation is blocked on account-level validation.

### Gate A — Awin

Validate:

- publisher approval
- Canadian advertiser availability
- useful advertiser product feeds
- grocery/general retail breadth
- program terms
- comparison use
- mobile-app use
- caching/indexing rights
- current price
- quantity/size
- GTIN
- promotions
- availability
- update timestamps
- price scope

If useful Canadian coverage passes, Awin becomes the first implementation
candidate.

If not, do not force an Awin integration.

### Gate B — impact.com

Validate the same requirements against joined Canadian brands and available
catalogs/API access.

### Gate C — CJ Affiliate

Validate Canadian advertiser/feed availability and publisher export/API
rights.

### Gate D — Rakuten Advertising

Validate Canadian advertiser programs, Product Search/Product Catalog access
and advertiser-specific approval.

### Parallel Gate — Flipp

Contact Flipp regarding an authorized media/data partnership for local
shopping intelligence.

### Parallel Gate — GS1 Canada

Determine whether ValuePilot can qualify as a data recipient for ECCnet
product-content/API access and what consumer-facing use is permitted.

## Provider scorecard dimensions

Every candidate must be scored on:

### Authorization

- documented API/feed
- comparison use permitted
- mobile-app use permitted
- consumer display permitted
- derived ranking permitted
- caching permitted
- redistribution restrictions known

### Geography

- Canada
- province
- postal code
- physical store
- delivery zone

### Price scope

- national catalog
- retailer online
- marketplace
- physical store
- fulfillment-specific

### Product evidence

- stable provider ID
- GTIN/UPC/EAN
- brand
- package quantity
- mass
- volume
- count
- variant
- images

### Offer evidence

- current price
- regular price
- sale price
- sale dates
- promotion terms
- membership terms
- availability
- fees
- source timestamp

### Operations

- feed/API style
- delta/update support
- rate limits
- latency
- reliability
- maximum catalog size
- compression
- batching
- retry behavior

### Economics

- signup cost
- recurring data cost
- usage cost
- enterprise minimums
- referral opportunity
- cost at scale

### Strategic risk

- provider lock-in
- single-source dependency
- terms-change risk
- advertiser churn
- geographic gaps
- data staleness
- business model conflict

## Current scorecard conclusion

Awin:
Best first low-cost broad-feed validation candidate.

impact.com:
Very strong second candidate and possible co-primary depending on actual
Canadian brand availability.

CJ Affiliate:
Very strong standardized product/unit-data candidate.

Rakuten Advertising:
Strong comparison-oriented feed rail with particularly attractive delta-feed
support.

Flipp:
Best strategic Canadian local/store promotion candidate, but partnership
access required.

GS1 Canada ECCnet:
Best identified Canadian product-identity/content candidate; not live offer
pricing.

Amazon Creators API:
Useful later Amazon-specific offer channel with strict affiliate eligibility
and cache rules.

Instacart:
Strong future grocery commerce/fulfillment partner; deeper raw data access
requires the correct partnership model.

Uber Eats:
Strong future channel-specific price source under authorized merchant/business
relationships; important proof that in-store and marketplace prices differ.

DoorDash:
Future limited-access commerce partner.

DataWeave:
Potential powerful enterprise hyperlocal pricing source, subject to rights and
economics.

NielsenIQ:
Enterprise market/POS intelligence source, not first consumer real-time API.

eBay:
Hold because current Buy API ranking/sorting requirements conflict with
ValuePilot's independent ranking unless special permission is obtained.

Walmart Marketplace:
Seller integration API, not a general Walmart Canada consumer-price feed.

## 5D decision

No single permanent provider is selected.

The provider architecture is selected.

FIRST VALIDATION RAIL:
Awin

SECOND:
impact.com

THIRD:
CJ Affiliate

FOURTH:
Rakuten Advertising

CANADIAN LOCAL-PRICE PARTNERSHIP:
Flipp

CANADIAN PRODUCT IDENTITY:
GS1 Canada ECCnet

SUPPLEMENTARY CHANNEL SOURCES:
Amazon Creators API and future approved commerce-platform integrations

ENTERPRISE ESCALATION:
DataWeave / NielsenIQ / retailer-direct commercial data

PROHIBITED FOUNDATION:
Unauthorized scraping and reverse-engineered private retailer endpoints

## Implementation authorization status

NOT AUTHORIZED YET.

5D remains a research and provider-validation milestone.

The Android application must retain:

- no INTERNET permission
- no ACCESS_NETWORK_STATE permission
- no provider credentials
- no remote AI
- no telemetry
- no ValuePilot backend dependency

After at least one provider passes its account/contract/data-quality gate,
a new explicit implementation milestone must be opened.

That future milestone will decide:

- whether a backend is justified
- network permissions
- secret management
- ingestion cadence
- normalized offer-domain types
- storage/indexing
- privacy boundary
- provider adapter
- failure handling
- physical-device acceptance testing

## Official public sources reviewed

Research used current official public documentation available on 2026-08-22
from:

- Awin publisher and product-feed documentation
- impact.com partner/catalog documentation and API reference
- CJ Affiliate Developer Portal and publisher documentation
- Rakuten Advertising Developer and Publisher Help documentation
- Flipp corporate and Canadian consumer documentation
- GS1 Canada ECCnet documentation
- Amazon Creators API documentation
- Instacart Developer Platform and Catalog documentation
- Uber Eats Marketplace API documentation
- DoorDash Marketplace Developer documentation
- eBay Buy/Browse API documentation
- Walmart Canada Marketplace Developer documentation
- DataWeave retail pricing-intelligence documentation
- NielsenIQ retail-measurement and pricing documentation

Public documentation cannot establish private advertiser approvals,
negotiated terms or commercial access.

Those facts must be verified during account-level validation before any
production integration.

Provider capabilities and terms can change.

They must be rechecked at implementation time.
