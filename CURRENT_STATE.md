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

Current primary navigation:

Home
Search
Basket
Saved

Compare remains a workflow, not a primary navigation tab.

## Current architecture

Permanent product direction:

Capture adapters
(OCR / Accessibility / future providers)

↓

ProductObservation

↓

Shared deterministic ValuePilot core

↓

Application state and ranking

↓

Presentation clients


The product does not depend on Accessibility, overlays, OCR, any retailer, or any single capture method.

## Deterministic value engine

The core remains responsible for:

- exact money handling
- quantity normalization
- promotions
- product identity
- comparison ranking
- value calculations

AI or semantic enrichment cannot override explicit price or quantity evidence.

## Android shell status

Verified on physical device:

- Home tab
- Search tab
- Basket tab
- Saved tab
- portrait layout
- landscape layout
- comparison workflow
- comparison persistence
- parser regression fix

## Privacy boundary

Current Android build has:

- no INTERNET permission
- no ACCESS_NETWORK_STATE permission
- no account requirement
- no telemetry
- no remote AI dependency
- no ValuePilot server dependency

## Next milestone

5C3 — Universal Search application foundation

Goal:

Turn Search from a placeholder destination into a real application workflow.

Architecture:

Search UI

↓

immutable search state

↓

search controller/reducer

↓

replaceable ProductSearchProvider

↓

normalized product evidence

↓

existing deterministic ranking

↓

search results presentation


5C3 will not begin:

- retailer scraping
- checkout integration
- universal cart
- subscriptions
- affiliate systems
- remote AI backend

Those require later milestones.
