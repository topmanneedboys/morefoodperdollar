# ValuePilot compact consumer UI audit

Wave 2 was implemented from the clean `3c9e829988a22b9675494180d0ca814347a74df0` checkpoint. The earlier Basket redesign at `732cd26` remains intact; this pass makes the shared objects and primary shell more compact without changing product, price, evidence, purchase, or ranking authority.

## What changed

| Surface | Before | After |
| --- | --- | --- |
| Home | Hero card, flow tutorial, basket card, repeat card and planner chrome competing in the first viewport | Quiet ValuePilot header, compact search action, one compact basket/repeat context object, and one `DEMO DATA` status chip; advanced modules remain hidden until their existing workflow needs them |
| Search | Search field plus permanent large submit button and identity-only paragraph | IME-first search field; retry button appears only for revalidation/error states; `About results` opens a contextual sheet; results use compact product objects |
| Product result | Tall card with stacked metadata, full-width Add button, and visible More stack | Compact identity row with one Add/Added control, overflow, and product sheet; exact identity and unknown price remain explicit |
| Basket | Stacked product card, large collect button and permanent detail wording | Keyed compact product rows with a familiar `○/✓` collection control, body product sheet, overflow disclosure, graphical progress, and details below one disclosure |
| You | Three separate summary cards followed by the real collection surface | One compact privacy chip, Bought/Saved/Watching segmented selector, and only the selected summary/action |
| Map / Trip / Compare / Good Price / Settings | Contextual cards and stacked controls could dominate the route | Map gives the marker surface the largest viewport; Trip groups supporting metrics; Compare/Good Price use quieter compact result surfaces; Settings is a conventional tappable row list. All projectors and route authority remain unchanged |
| Saved / Bought history / Price Watch | Personal rows used a heavier card shell | Same projected rows with quieter tonal surfaces, tighter padding and progressive secondary actions |

## Foundation

`ValuePilotDesignTokens` now includes semantic screen, row, section, radius, icon, touch, typography, divider, and micro/route-motion tokens. `ValuePilotCompactComponents.kt` provides the reusable compact row, overflow action, status chip, and `BottomSheetDialog` pattern. Sheets have a drag handle, title, optional subtitle/body, bounded actions, accessible labels, and dismiss on action.

The app keeps the warm ivory/charcoal/burnt-tangerine palette. Ordinary surfaces use tonal contrast and dividers; stronger elevation remains reserved for meaningful state changes. No third-party artwork, product photos, analytics, telemetry, or new permission was added. Functional overflow uses a consistent text glyph with a screen-reader label; product imagery is still governed by the existing unverified Google Images boundary.

## Copy and disclosure audit

- “PUBLIC ARGENTINA CATALOG” and the long identity-only explanation are no longer default-visible; the complete truth is in `About results`.
- “ACTIVE SHOPPING MISSION”, repeated trip/context prose, and the old Search submit duplication are removed from the compact first viewport.
- `DEMO DATA` is shown once where fictional sample state is present; its explanation is available through a sheet or accessible description.
- Basket progress is graphical and announced as the complete “N of M basket products collected” meaning to assistive technology.
- Price, store, history, evidence and trip caveats stay behind product/Basket details rather than being deleted.

## Interaction and state

Product objects support body tap, one frequent trailing action, and bounded overflow/context actions. Basket identity-body taps open a sheet without leaving the Basket route. Collection still calls `PracticalShoppingBasketProgressSession.toggle`; it never creates a purchase. Purchase confirmation remains an explicit owner action.

Existing keyed row reconciliation, search/basket/personal scroll state, collection scope, shell Back behavior, and saved-state restoration remain in place. The new sheets are transient and do not mutate their parent projection.

## Accessibility and performance

- Interactive controls remain 48dp-class; compact visible glyphs retain full touch targets.
- Content descriptions state identity, action, and uncertainty; collection changes use the existing polite live regions.
- Segmented You controls expose counts and selected state through normal Material controls.
- Motion uses the shared 140/180/240ms targets and `ValueAnimator.areAnimatorsEnabled()` fallback; no per-row animation loop was added.
- Basket/Search rows retain stable keys and avoid unbounded derived work. No image pipeline or nested scrolling system was introduced.
- Map, Trip, Compare, Good Price and Settings now use the same tokenized radius, divider and compact-control language as the primary loop; map/store truth remains explicitly unknown where the projector says so.

## Verification and limitations

The local verification run covers shared-core tests, Android JVM tests, focused compact-boundary tests, debug/release lint, debug/release builds, release bundle generation, privacy permissions, and `git diff --check`. The generated release bundle is intentionally unsigned. No Play upload, device install, data clear, uninstall, or backend STALE_RELEASE repair was performed.

The compact architecture still needs a fresh Motorola comparison for large font scales, real sheet ergonomics, exact visual density, map marker readability, and performance on a large Search result set. Those are physical judgments, not claims made by JVM/build verification.

The current local version remains `10125 / 101.1.24`; any future Play candidate must use a versionCode greater than `10125` (preferred next code `10126`) and must be separately signed and validated.
