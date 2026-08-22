# Current state

Updated: 2026-08-22

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

ProductObservation

↓

deterministic parsing and relevance

↓

deterministic ranking

↓

bounded presentation results


Current guarantees include:

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

The first Android Search experience is physically verified on-device.

Current built-in Search data is explicitly fictional sample evidence.
It is not presented as live retailer pricing, inventory, promotions or availability.

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

Capture/data providers

↓

normalized product evidence

↓

shared deterministic ValuePilot core

↓

application state and ranking

↓

presentation clients


ValuePilot does not depend on Accessibility, overlays, OCR,
a specific retailer, or any single capture method.

Accessibility and OCR remain optional adapters.

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

## Privacy boundary

Current Android build still has:

- no INTERNET permission
- no ACCESS_NETWORK_STATE permission
- no account requirement
- no telemetry
- no remote AI dependency
- no ValuePilot server dependency

## Next milestone

5C4 — Real Shopping Evidence Contract

Goal:

Prepare Universal Search to consume trustworthy real-world shopping data
without tying ValuePilot to any retailer or capture method.

The next layer must make the origin and quality of shopping evidence explicit.

Required concepts include:

- provider identity
- source/store identity
- observation time
- live versus sample evidence
- freshness
- availability evidence
- promotion evidence
- stable product identity
- clear unknown/unverified states

Provider data remains evidence.

Providers do not rank products.

The deterministic ValuePilot engine remains responsible for comparison
and value decisions.

5C4 will remain provider-independent and will not yet add:

- unauthorized retailer scraping
- checkout
- payment processing
- universal cart
- subscriptions
- affiliate ranking influence
- remote AI
- telemetry

Internet permission will not be added merely to prepare the architecture.

A network boundary should be introduced only when an authorized,
useful real-data provider is deliberately selected.
