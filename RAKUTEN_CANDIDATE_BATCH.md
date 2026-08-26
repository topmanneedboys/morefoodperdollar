# ValuePilot Rakuten Controlled Backup Candidate Batch

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Purpose:
Reduce calendar delay from strictly serial advertiser applications without turning the Rakuten account into a random affiliate-program collection.

This note does not authorize production integration, scraping, advertiser-content reuse, or ranking based on affiliate economics. Every advertiser must still pass profile, Offer, DSA/software, datafeed/Product Catalog, and advertiser-term review before application.

## Why the strategy changes now

The first Rakuten validation attempt (Giant Tiger) was denied without a specific advertiser reason. Well.ca has been fully pre-screened, applied to, and is now pending advertiser approval. impact.com separately declined ValuePilot at the media-partner/Marketplace level, while Awin/Skip and selected CJ advertiser decisions are still pending.

Strictly waiting for one Rakuten advertiser decision before even screening the next candidate now creates unnecessary calendar risk. The better approach is a small controlled portfolio of high-information candidates.

Target:

- maintain approximately five concurrent high-quality Rakuten validation candidates at most, including Well.ca
- pre-screen each candidate before application
- stop once enough approved/data-access paths exist to perform real feed validation
- do not mass-apply to ten or hundreds of advertisers

## Selected next candidates

### 1. Tru Earth — PRE-SCREEN PASSED / APPLY

Why:

- Canadian household consumables directly fit ValuePilot's unit-value problem
- products naturally expose count/load/package comparisons
- household cleaning products can later exercise ingredient/formulation evidence without restaurant-style portion uncertainty
- smaller focused catalog may be operationally easier to validate than a giant marketplace

Authenticated Rakuten evidence reviewed on 2026-08-26:

- MID 54255
- active baseline Offer with generic approval guidance and no ValuePilot-specific eligibility block observed
- Product Catalog
- Cross-Device Tracking
- Deep Links
- ITP v2.2
- DSA policy flag: `Allows downloadable software applications`
- real-time tracking
- coupon support through the publisher channel
- Offer permits product/search/storefront-style link formats subject to advertiser authorization requirements
- advertiser terms contain no blanket anti-comparison or mobile/software prohibition that conflicts with the profile DSA flag
- advertiser agreement expressly recognizes digital properties providing a user-requested benefit
- AI/automated promotional content is contemplated if accurate, reviewed and compliant
- automatic Offer assignment exists, with a seven-day review period before deemed acceptance; notices therefore require monitoring
- privacy/cookie/tracking obligations are substantial and must be implemented before any production tracking use
- the general IP/content license is narrow and referral-oriented; it does not establish blanket rights to cache, index, republish or redistribute a full product catalog
- confidentiality, audit, security and breach-notification obligations apply

Important unresolved point:

The authenticated Features page shows `Product Catalog`, but the supplied baseline Offer evidence did not independently state `Datafeed Availability`. Product Catalog visibility is sufficient to justify a controlled application because actual catalog/feed access and feed-specific rights can only be validated after advertiser acceptance. It is **not** evidence that ValuePilot currently has feed access or mobile-app catalog-use rights.

Decision:

**APPLY TO TRU EARTH FOR CONTROLLED 5D VALIDATION.**

After application, record the exact resulting partnership status. If approved later, inspect the actual Product Catalog/feed access mechanism and any feed-specific terms before downloading, caching, indexing, displaying or integrating anything.

### 2. Jamieson Vitamins — strong packaged-product/ingredient evidence candidate

Why:

- Canadian packaged goods with many count/strength/format variants
- useful later for the grocery-quality/ingredient evidence layer
- creates strong tests for exact variant identity because similar products differ by strength, dosage form, count and demographic target

Special guardrail:

ValuePilot must not convert supplement marketing into medical efficacy claims. If this advertiser is ever used, ranking should remain price/quantity/evidence based unless a clearly sourced, non-medical user preference dimension is explicitly selected. Health claims are not inferred.

Pre-application checks are the same as Tru Earth.

### 3. Newegg Canada — cross-category identity/variant stress-test candidate

Why:

- provides a non-grocery contrast to prevent ValuePilot's provider model from overfitting consumables
- useful for SKU/model/variant/product-vs-offer identity, sale-price semantics and large-catalog retrieval
- Canadian serviceability was already observed in the Rakuten discovery pass

This is not as strategically important as consumables for first consumer value differentiation, but it is high-information for the permanent general-shopping architecture.

### 4. Bath Depot / Bain Dépôt — Canadian durable-goods backup candidate

Why:

- Canadian-focused retailer from the filtered Rakuten candidate set
- useful for dimensions, variants, finish/color/model identity, bundle/accessory distinctions and higher-ticket price comparison
- likely a more focused catalog than multinational electronics/appliance brands

Pre-screen before any application. Do not assume Product Catalog or DSA rights from discovery presence alone.

### 5. Greenworks Tools Canada — optional fifth screen if one of the above fails pre-screen

Why:

- useful for tool/battery/voltage/bundle variant identity and durable-goods comparisons
- Canadian-relevant advertiser discovered in the filtered catalog pass
- provides another focused e-commerce catalog rather than another giant marketplace

Use as the fifth candidate only after its actual Rakuten profile/Offer/terms are checked.

## Candidates intentionally deprioritized

- CanadaPetCare: deprioritized because its public catalog includes flea/tick/heartworm and other pet-treatment products, creating unnecessary medication/regulatory complexity for an initial provider-validation rail.
- Bass Pro Shops & Cabela's Canada: deprioritized because the catalog can contain weapons/regulated-goods categories that are irrelevant to the first ValuePilot data milestone and add avoidable policy/domain complexity.
- LG Canada and Miele Canada: useful future durable-goods sources but large-brand approval friction and narrow appliance-centric coverage make them weaker immediate validation candidates than the selected batch.
- FortNine: valid future niche candidate, but powersports is less representative of ValuePilot's initial everyday-shopping thesis.
- Walmart Canada: currently blocked/ineligible and has unresolved advertiser-specific download/software ambiguity.
- Giant Tiger: denied for the current pass; do not reapply now.

## Fastest safe operating procedure

For each selected candidate, open the authenticated Rakuten advertiser profile and capture only the minimum evidence needed to decide:

1. About/profile page showing partnership state and Canadian availability
2. Features & Services showing Product Catalog, Deep Links and DSA/software policy
3. Offers page showing datafeed availability if stated and approval guidance
4. full advertiser Terms & Conditions (copy/paste is fine)

These can be collected for several candidates in parallel and reviewed as one batch. Do not click Apply until the candidate passes this screen.

A candidate fails the pre-screen if it has any material conflict such as:

- no Product Catalog/datafeed path when catalog evidence is the reason for applying
- explicit prohibition on ValuePilot's mobile/software model
- anti-comparison restriction
- terms that make the intended evidence display/normalization clearly incompatible
- mandatory attribution behavior inconsistent with genuine user-initiated clicks
- geography/currency incompatible with Canadian launch validation

Unknown feed caching/indexing/display rights do not automatically fail the application if the relationship is needed to inspect feed-specific terms after approval; those rights must remain unproven until actual authorization evidence exists.

## Current batch decision

Well.ca is already applied and pending.

Tru Earth has now passed pre-screen and should be applied to next.

Remaining screening order:

1. Jamieson Vitamins
2. Newegg Canada
3. Bath Depot / Bain Dépôt
4. Greenworks Tools Canada if a slot remains

If the remaining candidates pass pre-screen, it is acceptable to apply to them without waiting serially for Well.ca or Tru Earth, creating a controlled portfolio of up to five concurrent Rakuten advertiser validations.

This batch size is deliberately bounded. Do not expand to ten random applications merely to increase nominal approval probability.
