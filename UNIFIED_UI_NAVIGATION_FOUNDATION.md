# Unified UI navigation foundation

Milestone: navigation architecture, shared design tokens and reusable
interaction foundations only.

## Current route map

The Android shell has four primary destinations, represented by
`AppPrimaryTab` and rendered through `AppShellReducer`:

| Primary destination | Current route | Role |
| --- | --- | --- |
| Home | `HOME` | private list plus the clearly-labelled sample planning surface |
| Search | `SEARCH` | user-triggered Argentina identity Search and exact-product context actions |
| Basket | `BASKET` | the active private shopping mission and collection state |
| You / Saved | `SAVED` | Saved identities and private-history entry points |

These are the only permanent bottom-navigation destinations. Compare is a
standalone workflow. Confirmed Purchases, Price Watch, observed-price entry,
and Staple Watch are Saved-owned contextual routes. Trip Planner is Basket-owned.
Product → Nearby Store Map is a product-context dialog reached from an exact
Search identity; it is not a fifth tab.

## State contract

- The shell reducer owns only typed route/tab transitions; business and
  evidence rules remain outside views.
- Search query, submitted state, loaded-page state and scroll position remain
  in the existing Search host/activity state. Switching tabs hides the surface
  without invalidating it; returning restores the saved scroll position.
- Home, Basket, Saved, purchases, watches and Trip Planner remain backed by
  their existing durable stores. The foundation does not duplicate those
  models.
- Product Map and Compare retain opaque product identity through their existing
  callbacks and return to the originating primary context.
- Back closes a contextual dialog first, then reduces a child route to its
  owner (Trip Planner → Basket; Saved-owned routes → Saved; Compare → origin).

## Foundation added in this milestone

- `ValuePilotDesignTokens` centralizes presentation spacing, shape, typography,
  touch-target, motion and warm-palette tokens.
- `ValuePilotProductContextMenu` provides bounded progressive disclosure for
  secondary Photo, Watch and Map actions. It emits typed action values and does
  not parse, rank, persist or navigate.
- Search now keeps the private Home action obvious and reveals secondary product
  actions only when requested. Existing callbacks and identity/evidence
  boundaries remain unchanged.

## Deliberately not migrated yet

Home, Basket, Saved, History, Price Watch, Trip Planner, Map, Compare, Good
Price and the standalone capture surfaces still use their existing renderers.
They remain functional and are intentionally deferred to later bounded
migration stages. No new catalog, provider, network, ranking or business logic
was introduced here.
