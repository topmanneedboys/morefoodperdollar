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

### 2. Jamieson Vitamins — PRE-SCREEN PASSED / APPLY

Why:

- Canadian packaged goods with many count/strength/format variants
- useful later for the grocery-quality/ingredient evidence layer
- creates strong tests for exact variant identity because similar products differ by strength, dosage form, count and demographic target

Authenticated Rakuten evidence reviewed on 2026-08-26:

- active public baseline Offer with generic approval guidance and no ValuePilot-specific eligibility block observed
- Product Catalog
- Cross-Device Tracking
- Deep Links
- ITP v2.2
- DSA policy flag: `Allows downloadable software applications`
- real-time tracking
- coupon support through the publisher channel
- advertiser agreement contains no blanket anti-comparison or mobile/software prohibition that conflicts with the profile DSA flag
- advertiser agreement recognizes digital properties providing a user-requested benefit
- AI/automated promotional content is contemplated if accurate, reviewed and compliant
- automatic Offer assignment exists, with a seven-day review period before deemed acceptance
- privacy/cookie/tracking obligations are substantial
- general content/IP license remains narrow and referral-oriented and does not establish blanket catalog persistence/indexing/display/redistribution rights

Special guardrail:

The authenticated policy explicitly prohibits wording that suggests health claims. ValuePilot must not turn supplement catalog evidence into medical efficacy, safety, treatment or disease-prevention claims. Initial use, if later authorized, should be limited to sourced label/product facts such as price, count, dosage form, printed strength, ingredients/label attributes, package quantity and deterministic unit-value comparisons. Any health or efficacy interpretation remains out of scope unless a future deliberately designed evidence/policy layer supports it.

Important unresolved point:

`Product Catalog` is visible, but actual datafeed access, field quality, feed-specific rights and mobile-app catalog-use rights remain unproven until advertiser approval and authorized catalog inspection.

Decision:

**APPLY TO JAMIESON VITAMINS FOR CONTROLLED 5D VALIDATION.**

### 3. Newegg Canada — PRE-SCREEN FAILED / DO NOT APPLY NOW

Why it was attractive:

- useful non-grocery stress test for SKU/model/variant/product-vs-offer identity
- large electronics catalog could test sale-price semantics and general-shopping architecture
- authenticated profile shows Product Catalog, Deep Links and a DSA flag allowing downloadable software applications

Material conflict found in advertiser-specific Special Terms reviewed on 2026-08-26:

- the Special Terms restrict promotion, absent prior written approval, to displaying a link to Newegg on the publisher's website
- that restriction is materially narrower than the dashboard DSA flag and creates unresolved ambiguity for ValuePilot's intended mobile-app/catalog presentation model
- the Special Terms also impose legacy state-tax-nexus representations/administrative obligations that add unnecessary operational complexity for an early validation candidate
- the terms are advertiser-specific and therefore cannot be overridden merely by relying on generic dashboard feature metadata

Decision:

**DO NOT APPLY TO NEWEGG CANADA NOW.**

Do not accept the Special Terms merely to obtain relationship status. Reconsider only if Newegg provides explicit written approval for ValuePilot's intended application/presentation model or newer advertiser terms remove the website-only restriction. The Product Catalog and DSA profile flags do not cure this conflict by themselves.

### 4. Bath Depot / Bain Dépôt — NEXT SCREEN

Why:

- Canadian-focused retailer from the filtered Rakuten candidate set
- useful for dimensions, variants, finish/color/model identity, bundle/accessory distinctions and higher-ticket price comparison
- likely a more focused catalog than multinational electronics/appliance brands

Pre-screen before any application. Do not assume Product Catalog or DSA rights from discovery presence alone.

### 5. Greenworks Tools Canada — optional next screen

Why:

- useful for tool/battery/voltage/bundle variant identity and durable-goods comparisons
- Canadian-relevant advertiser discovered in the filtered catalog pass
- provides another focused e-commerce catalog rather than another giant marketplace

Use after Bath Depot, or sooner if Bath Depot fails pre-screen.

## Candidates intentionally deprioritized

- CanadaPetCare: deprioritized because its public catalog includes flea/tick/heartworm and other pet-treatment products, creating unnecessary medication/regulatory complexity for an initial provider-validation rail.
- Bass Pro Shops & Cabela's Canada: deprioritized because the catalog can contain weapons/regulated-goods categories that are irrelevant to the first ValuePilot data milestone and add avoidable policy/domain complexity.
- LG Canada and Miele Canada: useful future durable-goods sources but large-brand approval friction and narrow appliance-centric coverage make them weaker immediate validation candidates than the selected batch.
- FortNine: valid future niche candidate, but powersports is less representative of ValuePilot's initial everyday-shopping thesis.
- Walmart Canada: currently blocked/ineligible and has unresolved advertiser-specific download/software ambiguity.
- Giant Tiger: denied for the current pass; do not reapply now.
- Newegg Canada: current advertiser-specific Special Terms create a website-only promotion conflict absent prior written approval; do not apply now.

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

Pre-screen outcomes:

- Tru Earth: PASS / APPLY
- Jamieson Vitamins: PASS / APPLY
- Newegg Canada: FAIL FOR CURRENT PASS / DO NOT APPLY

Next screening order:

1. Bath Depot / Bain Dépôt
2. Greenworks Tools Canada

If both pass, it is acceptable to apply to them without waiting serially for Well.ca, Tru Earth or Jamieson, while keeping the controlled concurrent portfolio bounded.

Do not expand to ten random applications merely to increase nominal approval probability.
