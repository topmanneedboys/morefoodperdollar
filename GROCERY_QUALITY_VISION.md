# ValuePilot Grocery Quality & Ingredient Intelligence Vision

Updated: 2026-08-23

Status:
Future product direction and architecture guidance. This document does not authorize production provider integration, scraping, remote AI, medical claims, or changing the current 5D provider-selection gate.

## Decision

Grocery quality intelligence should become a first-class ValuePilot capability and should be designed before restaurant intelligence, because grocery products usually provide stronger structured evidence for ingredients, nutrition, package quantity, certifications, allergens and comparable variants.

The goal is not to replace objective price/unit-value ranking with a vague "healthy" score. ValuePilot should preserve separate evidence-backed dimensions and allow the user to understand trade-offs.

Core question:

> Among genuinely comparable grocery products, which option gives the best value for this user's priorities, considering price, quantity and explicit quality evidence without hiding uncertainty?

## Keep objective value separate from quality

Default ValuePilot should retain an objective value layer:

- price
- quantity / weight / volume / count
- price per normalized unit
- promotions with provenance
- availability
- freshness of evidence

Quality should be a separate evidence layer rather than silently changing `Best Value`.

Possible user-visible outputs:

- Best unit value
- Best ingredient profile
- Best nutrition profile
- Best quality-for-price
- Lowest processing / simplest ingredient list when the evidence supports that interpretation
- Best match for explicit user preferences

If a combined recommendation is later shown, the weighting must be visible, explainable and user-controllable.

## Grocery evidence dimensions

### Ingredients

When source evidence exists, preserve:

- complete ingredient list
- ingredient order
- sub-ingredients where supplied
- ingredient-list source and observation time
- exact product/variant identity
- whether the list is complete or partial

Never invent missing ingredients or treat absence from an incomplete list as verified absence.

### Nutrition

Potential structured evidence:

- serving size
- calories
- protein
- carbohydrates
- fibre
- sugars
- sodium
- fat / saturated fat
- other label nutrients when explicitly supplied

Nutrition comparisons should normalize serving sizes where possible and clearly distinguish manufacturer serving size from normalized 100 g / 100 mL or per-item comparisons.

### Allergens and dietary attributes

Only when explicitly sourced:

- contains / may contain allergen statements
- vegetarian / vegan
- gluten-free
- kosher / halal
- organic or other certifications

Do not infer an allergen-free or dietary status merely because a keyword is absent.

### Processing / formulation evidence

ValuePilot may expose descriptive signals such as:

- ingredient count
- presence of added sugars
- whole-food versus refined ingredient composition where deterministically identifiable
- additives, colours, flavours, sweeteners or preservatives when explicitly named
- percentage ingredients when labels provide them

These signals must not be presented as medical conclusions. If a future processing score is introduced, its rules must be published and deterministic.

### Product-quality evidence

Depending on category and provider evidence, useful attributes can include:

- protein/fat ratio
- fruit/vegetable/nut percentage
- meat/fish percentage
- concentration or active-content percentage
- grade / cut / roast / origin
- organic/fair-trade/certification evidence
- material or formulation differences for non-food grocery/household goods

Category-specific quality dimensions should be modeled explicitly rather than forcing one universal score across milk, cereal, detergent and pet food.

## Quality-for-price

A later ValuePilot capability should answer questions such as:

- Product A costs 18% less per 100 g, but Product B has materially more protein and less added sugar.
- Product B costs $0.40 more per serving but uses a higher documented percentage of the primary ingredient.
- Product A is the cheapest objective unit value; Product C is the strongest match for the user's explicit ingredient preferences.

A combined quality-for-price result must expose its components so users can see why it differs from pure unit value.

## Ratings and reviews

Ratings may be useful but should remain independent evidence.

If legally and technically available, preserve:

- rating value
- rating scale
- review count
- source
- observation time

Never let a 5.0 rating from a tiny sample silently outrank a highly established product. Use review count/confidence and show uncertainty.

Do not fabricate review summaries or treat AI-generated sentiment as stronger than source ratings.

## Personalized grocery preferences

Future users may explicitly choose priorities such as:

- lowest cost
- highest protein
- lower sodium
- lower added sugar
- simpler ingredient list
- vegan / vegetarian / allergen constraints
- organic / certification preferences
- brand avoidance or preference

These preferences belong in a transparent personalization layer. They must not alter the underlying factual evidence.

The same product may therefore have:

- objective unit-value rank
- evidence-backed quality dimensions
- personalized suitability rank

Those are different outputs and should not be conflated.

## Comparability rules

Quality comparisons only make sense between meaningfully comparable products.

Examples:

- compare similar cereal types before declaring a quality winner
- compare peanut butter with peanut butter, not peanut-flavoured spread without warning
- distinguish concentrated detergent from non-concentrated detergent
- distinguish ready-to-eat meals from raw ingredients
- preserve package/variant/flavour differences

AI may help classify product families, but explicit product, quantity, ingredient and nutrition evidence remains authoritative.

## Evidence and trust rules

1. Every ingredient, nutrition, certification, rating or quality claim needs provenance.
2. Missing evidence remains unknown.
3. Supplier claims and independent certifications should remain distinguishable.
4. Stale label/ingredient evidence should be flagged because formulations can change.
5. Variant identity must be strong enough before copying nutrition or ingredients from one SKU to another.
6. User-entered or OCR-extracted labels can be useful evidence but should carry lower or explicit confidence until verified.
7. Affiliate commission, sponsorship, provider payout and CPC must never influence quality or value ranking.
8. Do not make diagnostic, disease-treatment or other unsupported health claims.
9. The engine should explain which evidence caused a result.
10. A quality score must never hide the raw facts behind it.

## Relationship to restaurant intelligence

The same general framework can later support restaurant foods:

- ingredients
- nutrition
- portion quantity
- rating evidence
- price
- quality/preferences

Grocery should establish the quality-evidence model first because packaged products often have clearer labels and stronger identity. Restaurant intelligence can then reuse the proven evidence concepts while adding meal-specific comparability and portion uncertainty.

## Implementation order

Do not implement this during the current external provider waiting gate unless real provider evidence makes a small provider-neutral change clearly necessary.

Preferred progression:

1. complete first authorized real shopping-provider validation
2. inspect which ingredient/nutrition/identifier fields real feeds actually supply
3. define provider-neutral ingredient and nutrition evidence types from real data
4. implement deterministic normalization and provenance tests
5. expose separate ingredient/nutrition/quality dimensions in UI
6. add optional transparent personalization
7. only later consider a combined quality-for-price recommendation
8. reuse the resulting framework for restaurant intelligence

## Product principle

ValuePilot should not tell users that the cheapest item is automatically the best, nor that a subjective "health score" is automatically superior to price.

The defensible product is one that says:

> Here is the cheapest comparable option. Here is the documented ingredient/nutrition/quality difference. Here is what changes if you care more about one of those dimensions. Here is the evidence behind each conclusion.

That preserves trust while making ValuePilot substantially more useful than a conventional price-comparison app.
