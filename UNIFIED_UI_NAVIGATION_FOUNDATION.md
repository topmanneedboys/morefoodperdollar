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

The complete contextual route inventory is:

- `OBSERVED_PRICE_SAVED_SELECTION` → `OBSERVED_PRICE_CONFIRMATION_DRAFT`
  for selecting a Saved identity and entering a private observation.
- `STAPLE_WATCH_SETUP` → `STAPLE_WATCH_POLICY` for the private watch policy
  flow.
- `CONFIRMED_PURCHASE_HISTORY` and `PRICE_WATCHES` for private history views.
- `TRIP_PLANNER` for Basket-owned manual trip assumptions.
- `COMPARE` for the standalone comparison activity handoff, returning to its
  originating primary tab.

Other explicit activity/surface entry points—Good Price, barcode capture,
Share to ValuePilot, and the optional Accessibility/OCR tools—remain secondary
workflows rather than bottom-navigation destinations.

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

## Performance and accessibility contract

- Search remains bounded by its existing page/candidate limits and retains the
  existing rendered-match identity guard, so an unchanged result set is not
  rebuilt merely because another tab was selected.
- New product cards and context actions use stable opaque callbacks rather than
  reconstructing identity from display text; secondary actions are bounded to
  four and use minimum 48dp touch targets.
- The disclosure control and every secondary action have explicit labels for
  screen readers. The foundation introduces no mandatory animation and keeps
  existing large-text-compatible TextView layouts intact.
- A later list-performance migration can replace the current vertical surfaces
  with a bounded keyed list without moving business logic into views.

## Foundation added in this milestone

- `ValuePilotDesignTokens` centralizes presentation spacing, shape, typography,
  touch-target, motion and warm-palette tokens.
- `ValuePilotProductContextMenu` provides bounded progressive disclosure for
  secondary Photo, Watch and Map actions. It emits typed action values and does
  not parse, rank, persist or navigate.
- Search now keeps the private Home action obvious and reveals secondary product
  actions only when requested. Existing callbacks and identity/evidence
  boundaries remain unchanged.

## Stage 2 Home command center

Home now starts with a compact command-center card instead of repeating the
long route header above the shopping workspace. It is presentation-only and
is derived from the existing immutable `PracticalShoppingHomeRenderState`:

- planned-item count and plan/coverage status are shown as small visual pills;
- projected-plan copy remains evidence-aware and never claims live inventory;
- a repeat-purchase entry appears only when the bounded private confirmed
  purchase store has records, and opens the existing Saved-owned history route;
- saved identities and private price memory remain separate from the sample
  planning result.

The card uses `ValuePilotProductCard`, shared spacing/shape/color tokens and
the bounded `ValuePilotMotion` reveal helper. Motion is a 180 ms disclosure
only, resets immediately when Android disables animator motion, and has no
effect on state, persistence or planner authority.

## Stage 3 Search, product context and map

Search keeps one shared `ValuePilotProductCard` per exact identity. The name,
brand/variant, source-supplied package quantity and identity/evidence caveat
are visually grouped; saving to the private Home list remains the single
obvious action. Photo, Watch, Map and exact-match History are bounded typed
actions inside the same expandable context surface. History appears only when
the exact opaque identity key exists in the private purchase store.

The existing Product → Nearby Store Map now uses the same warm card/touch
tokens. It highlights the exact product, relative store positions, the
selected store, release/freshness evidence, approximate straight-line distance
and the existing Trip Planner handoff. It remains deliberately tile-free and
does not imply stock, driving distance, driving time or cheapest-store status.
Selection still passes the original `ProductStoreMapPin`/`storeKey` into the
existing Trip Planner state; no second store model was introduced.

Search query, loaded pages, rendered-match identity, scroll position, basket,
Saved, watches and history remain owned by their existing state boundaries.
Opening context or the map does not invalidate Search; returning through the
same primary route restores the saved scroll position. Map and context
disclosures use the shared motion helper and collapse after an action.

## Stage 4 Basket

Basket now presents the active shopping mission through the same warm
`ValuePilotProductCard` language as Home and Search. Its summary card keeps the
item count, collected progress, planned-stop context, optional trip-rule context
and the next Home/Trip action together without moving any decision authority
into the View. Unknown items remain counted and visibly unresolved.

Sample-planned rows are reconciled by stable `ShoppingItemKey` instances rather
than rebuilding the whole list after a check-off. The primary row action is
the local collected mark; the card's secondary purchase and Price Watch actions
are progressively disclosed. Collected is intentionally rendered as a local
shopping-session state and never as a confirmed purchase. Private Search
identities use the same card treatment, keep their exact-key Watch/photo/
purchase actions behind disclosure, and expose Watch status without showing a
full history inline.

Basket scroll position is saved independently from Search and restored when the
Basket route is reopened. Existing progress scopes, durable private identities,
purchase confirmation, Price Watch, Trip Planner and Decision projections remain
the authoritative boundaries; no new pricing, store, trip or purchase logic was
introduced.

## Stage 5 Personal shopping area

Saved identities, confirmed purchases and Price Watch now share the same calm
personal-card language. Each row reuses its stable identity key, keeps its
primary next step visible, and places correction, removal, target and history
details behind an explicit More action. This keeps the personal area useful at
a glance without hiding the exact private facts or turning them into live offers.

The Saved-owned routes retain their existing typed projectors, stores and
unknown-state copy. The app shell now preserves the personal scroll position
when Saved, purchase history or Price Watch refreshes, rotates, or returns from
another route. This is presentation state only; it does not change purchase,
observation, watch, price or ranking authority.

## Stage 6 Trip Planner decision presentation

Trip Planner now uses the same calm card hierarchy as the primary shopping
surfaces. The first screen shows the Basket coverage, the selected destination
and origin, the exact manual trip-cost estimate, and a plain-language
break-even statement. Assumptions remain visibly separate from evidence; no
route, inventory, cheapest-store or current-savings claim is added.

The decision projection is presented as a readable headline plus four compact
evidence chips: usable current lines, history/personal context, current
unknowns, and lines needing review. The existing decision status and blocker
facts remain authoritative. “Why this answer” progressively reveals the exact
assumptions and evidence text, while “More trip actions” keeps secondary store,
origin, directions, preferred-store and clear actions available without
cluttering the primary path. The View still emits only typed actions.

Trip Planner scroll position is saved across route changes and recreation. The
surface uses shared cards, spacing, touch targets, content descriptions and
bounded reveal motion; it does not add a new store, price, ranking, persistence,
network or location authority.

## Stage 7 Compare, Good Price and capture consolidation

Compare now uses the shared warm card language for its projected status and
exact product rows. Comparable products keep their exact price, package quantity
and unit-rate facts visible; a winner is shown only when the existing evaluator
has a valid comparable result. Blocked products and incomplete names move behind
an explicit details action instead of competing with the main comparison.

Good Price now makes its evidence basis visible in the answer itself: “this exact
price only” when no matching history exists, or “your private history” when the
existing exact-memory assessment supplies context. The longer disclosure is
progressive, and the answer remains explicitly personal context rather than a
live market or store claim.

Photo, barcode and shared-content adapters remain user-triggered and local. The
manual Compare flow is primary; photo/barcode helpers are grouped in a secondary
capture card below the comparison result. Barcode and share review surfaces use
the same warm card/action treatment. No Accessibility overlay, OCR, barcode,
share, price, ranking, provider or network authority moved into presentation
code.

## Deliberately not migrated yet

No major consumer-facing shopping surface remains on the old visual language.
The optional Accessibility settings/scanner internals remain deliberately
adapter/diagnostic surfaces, secondary to Home and the shopping workspace.
Search was not redesigned in Stage 2. No new catalog, provider, network,
ranking or business logic was introduced here.
