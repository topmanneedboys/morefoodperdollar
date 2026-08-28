# ValuePilot Product Design and Scale Principles

Updated: 2026-08-27

Purpose:
Capture useful product-design and architecture ideas from design/engineering material reviewed during ValuePilot development. These are **inputs and heuristics, not automatically trusted facts or implementation requirements**. Any numerical psychology/conversion claim from source material should be verified independently before being treated as evidence.

## Product UX principles worth keeping

### 1. Reduce decision work

Every important screen should make the next useful action obvious and easy to evaluate.

Prefer:
- a small number of meaningful choices
- clear labels and defaults
- deterministic comparisons that remove mental arithmetic
- progressive disclosure instead of dumping every field/filter at once

Do not remove information merely to make a decision look easier. ValuePilot should reduce cognitive work by doing valid normalization and comparison for the user, not by hiding uncertainty.

### 2. Transparency beats sales pressure

Trust is a product feature.

ValuePilot should proactively show material information that affects a decision:
- source/provider
- current price and currency
- quantity/pack size
- unit value
- availability when known
- price/reference-price semantics when known
- freshness / observation time
- important exclusions or unknowns

If something is unknown, say it is unknown rather than filling the gap with an estimate that looks authoritative.

### 3. Specificity is useful when it is real

Specific numbers can reduce uncertainty, but only when supported by evidence.

Examples:
- show an actual result count only when it is the real bounded result count
- show exact unit-price math when inputs are exact
- show exact savings only against a valid sourced comparison/reference price
- show an exact total only when the total is actually known

Never fabricate specificity to increase conversion.

### 4. Give useful value before requiring an account

Core shopping intelligence should work before sign-up wherever practical.

A user should be able to search, inspect evidence, and compare useful results without first surrendering an email address or creating an account.

If accounts are introduced later, they should unlock persistence/sync/convenience rather than hold basic results hostage.

### 5. Smart defaults, but only when justified

Defaults can reduce friction when ValuePilot has a defensible basis for them.

Good examples:
- current country/currency when reliably known and user-editable
- a previously chosen unit/display preference
- a previously selected sort mode
- a common category-specific normalization that is clearly labeled

Bad examples:
- pretending to know a preference that has never been expressed
- silently choosing a retailer/product because it monetizes better
- defaults influenced by affiliate commission

All important defaults should remain easy to change.

### 6. Progress should be real, not fake

If onboarding or a multi-step workflow later exists, count legitimate completed work and make remaining work understandable.

Do not use fake percentages, artificial completed steps, or misleading "head starts" merely to manipulate completion.

### 7. Create ownership without lock-in

Let users build useful state before an account exists where feasible:
- shortlist products
- compare offers
- set preferences
- save a local search/session

Later account creation may offer cross-device persistence or backup, but ValuePilot should not manufacture loss or hold work hostage to force signup.

### 8. Known price beats ambiguous price presentation

For ValuePilot, the correct rule is not "always replace ranges with one number." The rule is:

- if an exact authorized current price is known, show it clearly
- if only a legitimate range is known, show the range and why
- if the price is uncertain, stale, variant-dependent, location-dependent, or incomplete, expose that uncertainty
- never invent a single number to make comparison easier

### 9. Relative-value cues must be provable

Badges such as `Cheaper`, `Best value`, `Save 31%`, or crossed-out reference prices can strongly influence decisions, so they require deterministic evidence.

A ValuePilot badge must answer:
- cheaper than what?
- using which exact quantity/unit normalization?
- at what observed time?
- from which source?

No vague "smart choice" badge without an auditable rule.

### 10. Show the thing being evaluated

Prefer real, authorized product imagery and meaningful product attributes over decorative artwork when imagery is available and permitted.

The interface should help the user understand the actual product/offer, not merely look attractive.

## Anti-dark-pattern guardrails

Do not adopt source examples that depend on coercion or misleading psychology.

ValuePilot should not use:
- fake urgency or scarcity
- shame-based opt-outs
- threatening copy such as "I'll risk it"
- false progress
- invented crossed-out prices
- unsupported discount percentages
- hidden fees/totals
- forced signup before basic value is shown
- deliberately ambiguous "cheaper" claims
- affiliate-economics-driven ranking or defaults

Product psychology is useful when it reduces uncertainty and helps users make an informed choice. It is not a license to manipulate users into actions that are against their interests.

## ValuePilot-specific UX translation

### Search
- allow useful search before account creation
- make the main query/action obvious
- use defaults only when grounded and editable
- show real result counts only when known
- keep filters progressive rather than overwhelming

### Results
- lead with comparable facts: current price, quantity, normalized unit value, availability/freshness where known
- use concise deterministic badges only when their rule is provable
- do not let decorative content outrank evidence

### Compare
- remove mental arithmetic for the user
- explain why one offer ranks above another
- expose unknown fields instead of hiding them
- distinguish Product identity from retailer Offer identity

### Saved / preferences
- local persistence can create useful ownership before an account exists
- account/sync can be an optional later convenience

### Future monetization/paywall
If ValuePilot eventually introduces paid features:
- explain billing/trial timing plainly
- disclose renewal and cancellation behavior before purchase
- avoid pressure language
- remind users before a trial charge when technically/product-policy feasible
- make cancellation straightforward

## Architecture lessons worth keeping

The reviewed architecture material correctly emphasizes that architecture is about components, boundaries, relationships, deployment, and scaling. However, "microservices solve the problems of monoliths" is too simplistic to adopt as a rule.

### Current ValuePilot decision

Keep the present architecture direction: a **modular, strongly bounded application/shared core** rather than prematurely splitting ValuePilot into network microservices.

The existing boundaries remain appropriate:
- provider/capture adapters
- provenance-preserving evidence
- deterministic shared core
- application/orchestration
- replaceable presentations

Do not introduce a backend, network dependency, or microservice topology merely because it may be needed at large scale later.

### When a backend eventually exists

Split deployment units only when a concrete scaling, ownership, reliability, security, or release-cadence need justifies it.

Potential future independent workloads include:
- authorized provider/feed ingestion
- feed validation and normalization
- product/offer identity resolution
- search/index generation
- freshness/update processing
- account/saved-preference sync
- notification jobs

These are **possible service boundaries, not commitments**.

### Event-driven processing

Event-driven jobs may be a good fit for future feed updates:

`provider file/update -> validate -> normalize -> identity/offer update -> index refresh -> downstream notification/cache invalidation`

Each stage should be idempotent, retryable, observable, and provenance-preserving. A failed stage must not silently publish partially trusted evidence.

### Serverless

Serverless functions may be useful later for small bursty/event-driven jobs, webhooks, or scheduled processing. They are not automatically the best home for every ValuePilot workload. Long-running ingestion, large feed processing, stateful indexing, and predictable high-throughput work may fit persistent workers/services better.

### Scaling rule

Prefer the simplest deployable architecture that preserves clean domain boundaries. Scale a component independently only after measurement shows that independent scaling or fault isolation is valuable.

## Permanent compatibility with existing engineering rules

These notes do not replace ValuePilot's existing architecture rules:
- shared-core remains platform-neutral
- money/quantity logic stays explicit and deterministic
- AI remains optional evidence
- expensive work is bounded
- UI renders immutable state and emits typed actions
- adapters do not own ranking
- no production backend/network/affiliate integration is introduced without the appropriate milestone

## Source-quality note

The reviewed material contains useful product heuristics but also presents several psychology/conversion statistics and causal claims without the underlying studies in the supplied text. Treat those numerical claims as unverified until independently checked. The durable ValuePilot principles above intentionally retain the useful design ideas while rejecting manipulative or unsupported implementations.
