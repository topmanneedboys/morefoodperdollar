# ValuePilot Future Product Vision

Updated: 2026-08-23

Status:
Future product direction only. This document intentionally records ideas that should survive chat/session boundaries without changing the currently authorized 5D implementation scope.

Current implementation priority remains provider selection and validation for shopping evidence. Nothing in this document authorizes premature restaurant integrations, scraping, production networking, remote AI, affiliate-driven ranking, nutrition/health claims, or a redesign of the provider-neutral core.

## Strategic direction

ValuePilot should be designed as a general **value-intelligence engine for things people buy and consume**, not merely as an affiliate product directory or a grocery price list.

The long-term question is:

> Given what a person wants to buy, eat, or order, what comparable option gives them the best defensible value, and why?

The existing permanent architecture remains useful for this direction:

capture/data providers

↓

typed evidence with provenance

↓

deterministic trust/acceptance

↓

normalized product/offer or meal/offer identity

↓

deterministic comparable-value analysis

↓

bounded presentation

Capture methods such as OCR, screenshots, accessibility, menu feeds, retailer feeds, restaurant APIs, user entry, or other authorized sources remain replaceable adapters rather than the product itself.

## Future restaurant-order intelligence

A future ValuePilot capability should compare restaurant food in the same spirit as grocery/general-product value comparison.

Example user intent:

- user is considering or has selected a KFC fried-chicken order
- ValuePilot identifies the actual order and quantity
- it finds meaningfully comparable fried-chicken alternatives from other restaurants
- it compares what the customer would actually receive rather than merely comparing menu-line prices

Potential comparison dimensions include:

- delivered/menu price, with unknown charges kept explicitly unknown rather than fabricated
- number of pieces/items
- estimated or explicit portion/weight when evidence exists
- meal components such as fries, drink, sides, sauces, or add-ons
- calories, protein, sodium, allergens or other nutrition facts when explicitly sourced
- ingredients when explicitly available
- restaurant/menu-item rating evidence when legally and technically available
- rating count/confidence so a 5.0 from two reviews does not silently outrank a 4.7 from thousands
- distance/service area when relevant
- delivery or pickup availability
- quoted delivery/pickup time when provided by an authorized source
- promotions with explicit provenance
- evidence freshness

The engine should answer questions such as:

- Which similar fried-chicken order gives the most food for the money?
- Which pizza gives the best comparable value after size/toppings/quantity normalization?
- Which burger combo is actually comparable to the user's current order?
- Is a larger order a better unit value or merely more expensive?
- Does a higher-rated alternative justify a modest price premium?

## Restaurant comparability is not simple price sorting

Restaurant items require a semantic/comparability layer because two menu items with similar names may not be equivalent.

Future comparison should distinguish dimensions such as:

- food family: pizza, burger, fried chicken, wings, fries, pasta, noodles, curry, sandwich, dessert, etc.
- portion/count/size
- included sides/drinks
- bone-in versus boneless
- individual versus combo/family bundle
- toppings/protein choices
- preparation style when it materially affects comparability

AI/semantic assistance may help classify or match items, but it must not overwrite explicit money, quantity, ingredients, nutrition, availability, or source evidence.

A future restaurant similarity score should be explainable and bounded. It should never pretend that two foods are equivalent when important evidence is missing.

## "What am I eating?" capture concept

The preferred progression is incremental and adapter-neutral.

### Phase 1 — explicit user intent

User searches or enters a restaurant/menu item directly, for example:

- `KFC 10-piece bucket`
- `best value chicken wings nearby`
- `compare this pizza with similar pizzas`

### Phase 2 — authorized menu/order evidence

Use authorized menu feeds/APIs, restaurant-direct sources, delivery-platform partnerships, structured menu data, or user-provided evidence where legally permitted.

### Phase 3 — screenshot/vision capture

A user can voluntarily share a screenshot/order page. An OCR/vision adapter extracts candidate restaurant, item, price, size/count, modifiers and other evidence.

The extracted information still passes through the normal provenance/trust boundary before it can influence ranking.

Continuous Android Accessibility capture is not required and should not become the product foundation again.

## Ingredients as a first-class evidence dimension

Ingredients should eventually be supported across both grocery products and restaurant/menu items whenever reliable source evidence exists.

Potential uses:

- display source-provided ingredient lists
- compare ingredient differences between otherwise similar products
- show allergens when explicitly supplied
- connect nutrition facts to the exact product/variant when identity is strong enough
- distinguish missing ingredients from verified absence
- preserve source and observation metadata

ValuePilot should not invent ingredient lists, infer allergens as facts, or present unsupported health claims.

Ingredient information should initially be descriptive evidence rather than a hidden ranking factor.

If ValuePilot later introduces user-selectable quality/ingredient preferences, those preferences should be explicit and separable from the default objective value ranking.

## Grocery + restaurant cross-comparison

A particularly valuable later capability is comparing restaurant consumption with realistic grocery/home-preparation alternatives.

Example:

Restaurant meal:
- chicken curry meal
- known menu price
- known or estimated serving quantity only when justified by evidence

Home-preparation alternative:
- required grocery ingredients
- package prices
- recipe quantity/servings
- estimated consumed ingredient cost

Possible result:

- restaurant price per serving
- home-prepared ingredient cost per serving
- absolute and percentage price difference
- important caveats such as preparation time, leftover ingredients, delivery fees, equipment, missing ingredients, or unknown portion equivalence

Do not pretend that restaurant labour, convenience, taste, preparation time, or experience has a universal monetary value. If convenience or subjective quality is incorporated later, it should be a user-controlled preference rather than a fabricated objective number.

## Quality, ratings and subjective value

ValuePilot's default `Best Value` should remain evidence-driven and not silently become `highest rating` or `cheapest`.

A future restaurant result can expose separate dimensions, for example:

- Quantity value
- Nutrition evidence
- Ingredient evidence
- Popularity/rating evidence
- Convenience
- Overall personalized recommendation

If an overall personalized recommendation is introduced, users should be able to understand why it differs from pure unit/quantity value.

Affiliate commission, sponsorship, restaurant payout, CPC or provider economics must never influence these rankings.

## Proposed future domain model

Do not force every restaurant concept into today's physical-product fields. Reuse the evidence principles while allowing domain-specific comparable attributes.

Conceptual model:

`ConsumableItem`

- canonical identity
- source identity
- category/food family
- variant/modifiers
- explicit quantity/count/weight/volume when known
- components/bundle contents
- ingredients evidence
- nutrition evidence

`ConsumableOffer`

- seller/restaurant/store
- price/currency
- promotion evidence
- availability
- fulfillment mode
- service geography
- observed time/freshness
- optional delivery/pickup terms when explicitly known

`ComparableValueResult`

- normalized comparable quantity
- price/value metrics supported by evidence
- confidence/explanation
- excluded or unknown dimensions
- independent ratings/quality evidence

This should remain compatible with the permanent Product != Offer principle: one food/item identity may have multiple offers, locations, fulfillment modes or prices.

## Data-source and authorization strategy

Restaurant intelligence should follow the same provider discipline as current 5D shopping work.

Preferred evidence order:

1. authorized restaurant/menu/provider APIs or feeds
2. restaurant-direct structured data with explicit permission
3. user-provided order/menu evidence
4. device-observed evidence when deliberately supported and legally appropriate
5. deterministic parsing/normalization
6. optional AI semantic assistance

Do not build the feature around unauthorized scraping of Uber Eats, DoorDash, restaurant sites or private endpoints.

Provider independence is mandatory because restaurant/menu coverage will likely require multiple sources.

## Important ranking guardrails

1. Never fabricate delivered totals, fees, taxes, tips or availability.
2. Do not compare fundamentally different food quantities as though equivalent.
3. If portion evidence is weak, label it and prevent it from silently controlling `Best Value`.
4. Ratings need source, count/confidence and freshness where available.
5. Ingredients/nutrition require explicit provenance.
6. Promotions require explicit provenance before they improve rank.
7. Affiliate/sponsorship economics must never influence ranking.
8. Restaurant/menu data rights must be validated separately from platform/account access.
9. AI may assist classification and matching but cannot create authoritative price, quantity, ingredient or nutrition evidence.
10. Missing evidence remains unknown.

## Why this should wait until after the current provider gate

Do not implement restaurant intelligence during the present provider-selection milestone simply because the idea is promising.

Reasons:

- current shopping evidence/provider work will reveal real-world field and rights problems that should inform the broader model
- premature restaurant abstractions risk speculative complexity
- authorized menu data is a separate commercial/data-access problem
- the existing shopping core should first prove itself against real authorized feeds

Once the first real shopping provider is validated and integrated cleanly, restaurant intelligence can become a deliberate later milestone rather than an ad-hoc feature.

## Product thesis

The long-term defensible product is not:

> a list of affiliate links sorted by price

It is:

> a provenance-aware engine that understands what the user is actually buying or consuming, normalizes meaningfully comparable alternatives, separates objective value from subjective preferences, and explains the best defensible option without allowing commercial incentives to influence the answer.

This vision includes physical products, groceries, restaurant meals, ingredients and other consumable purchases while preserving the same deterministic trust principles already established in ValuePilot.
