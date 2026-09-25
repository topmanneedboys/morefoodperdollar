# Canonical Project Specification

Updated: 2026-08-20

## Current binding milestone

Turn the existing Android ValuePilot into a fast, search-aware, clean, tappable consumer experience that remains smooth with 100–500 collected products.

## Scope constraints

- Modify the existing app; do not replace it.
- Do not start iOS, Supabase, a cross-store backend, Universal Cart, or Basket Optimizer implementation.
- Browser code is consulted only for shared behavior when necessary.
- Preserve local-only parsing, ranking, OCR, and model inference.

## Acceptance requirements

- Filter/coalesce Accessibility events, ignore the overlay, snapshot a bounded tree pass, fingerprint cards, parse only changes in the background, maintain a session-scoped incremental store, coalesce ranking work, and render with virtualized stable-ID diffs.
- A new query/store/platform/strong page session must not reuse stale products. `bananas` → `eggs` excludes bananas; `milk` excludes banana.
- Canonical names exclude member/previous-price UI phrases. Current/member/previous prices are separate fields.
- Rows wrap, remain readable, and are fully tappable.
- Exact visible/off-screen product reopening is supported when it can be reacquired confidently. Uncertain, ambiguous, stale, or changed items are never clicked.
- Minimized state is one VP bubble. Open state hides it and uses a draggable bottom sheet that leaves meaningful app content visible.
- Header, consumer ranking labels, Filters, Rescan, advanced scan/OCR controls, methodology info, subtle loading/motion/haptics, and reduce-motion respect are required.
- Regression fixtures cover stale sessions, relevance, price/name parsing, same-name/different-size identity, safe locator behavior, and 20/60/100/160/250/500 products.
- Unit tests, lint, and Android assembly must pass; an installable APK and before/after performance evidence are required.
- The milestone is complete only after physical-device responsiveness and navigation validation. Only then may the next proposed implementation milestone be Universal Cart + Basket Optimizer domain model, followed by cross-store infrastructure.
