# ValuePilot v101 / Android v101.1 architecture

## Permanent product boundary

ValuePilot core is independent of capture and presentation:

`provider adapters → immutable observations → canonical product/offer intelligence → repository/session → matching/ranking → stable application state/API → replaceable presentations`

`CoreContracts.kt` contains platform-neutral provider, parser, repository, ranking, and matching ports. `ValuePilotUiState.kt` contains immutable presentation state and typed intents. Android Accessibility/OCR/navigation live outside that boundary; `AndroidLiveConnector` is explicitly experimental and removable. The existing overlay remains a compatibility presentation while it is migrated to consume only application state. See `V2_ARCHITECTURE_DECISION.md` for classification and the incremental KMP decision.

## Evidence hierarchy

ValuePilot is a deterministic measurement engine with a bounded semantic fallback:

1. Current price and explicit promotion terms
2. Explicit mass, volume, count, calories, or pizza diameter
3. Explicit size/count words such as six wings, double, or family size
4. Compact local-model category and relative food/meat signals

Later evidence cannot replace earlier evidence. The model can select a category or seed relative portion points, but it cannot claim a physical weight or alter the price/unit conversion.

## Shared local model

`training/local_ai_corpus.json` is converted by `tools/train-local-ai.mjs` into:

- `browser-extension/local-ai-model.js`
- `android/app/src/main/assets/local_ai_model.json`

The runtime is a deterministic multinomial Naive Bayes classifier over word unigrams and bigrams. It has 19 categories and 642 selected features. This gives both platforms the same small, instant, offline model instead of bundling a multi-gigabyte LLM or depending on hardware-specific browser AI APIs.

Confidence is calibrated by recognized-evidence count. Food, pork, and meat-ratio signals are bounded and only used above explicit thresholds. Unknown inputs remain unknown.

## Exact normalization

Mass becomes grams, volume becomes millilitres, and multipacks become totals. Supported families include metric, imperial, cups/pints/quarts/gallons, centilitres/decilitres, dozens, and common spelling variants.

Pizza uses `π × (diameter / 2)²`, so area—not linear diameter—is compared.

Promotions record both a received-quantity multiplier and the actual minimum spend. This prevents a two-item 50%-off deal from appearing affordable under a budget that only covers one item.

## Browser extraction

The content script combines semantic/card candidates, price-text ancestor scoring, Product/MenuItem/Offer JSON-LD, mutation rescans, and an explicit controlled-scroll collector. A Shadow DOM isolates the overlay. Results are rendered with DOM nodes and `textContent`, so scraped strings are never interpreted as markup.

## Android incremental pipeline

`ValueAccessibilityService` tracks the last external app window and rejects all events from ValuePilot's own package. A bounded recent-signature gate coalesces event storms into at most one pending scan and one follow-up flag, so rapid callbacks cannot create polling-loop fan-out or indefinitely postpone a scan.

`NodeScanner` performs one breadth-first capture, capped at 5,000 nodes and a 30 ms elapsed deadline after the first 256 nodes. It records lightweight node facts, computes subtree price/node/character counts bottom-up, selects distinct cards, gathers each selected subtree once, and drops node references. Every card snapshot carries stable content/card fingerprints and a semantic locator seed.

Only new or changed card fingerprints enter `IncrementalProductStore`. Parsing runs on a single lower-priority background executor. Latest-only ranking requests are coalesced; filtering/ranking also runs off main. The UI receives immutable ranked lists through `ListAdapter`, where DiffUtil and stable IDs update a RecyclerView without recreating the result tree.

`SearchContext` owns platform/package/store/query/page fingerprints and a generated session ID. A strong context transition clears all product/card state before new results are accepted. Query relevance is a second boundary against unrelated results.

## Android product reopening

Each parsed Accessibility item stores a locator containing package, session, canonical name, current/member price, quantity, card fingerprint, view ID, and original bounds. A row tap hides the sheet and reacquires current cards. Matching requires the same active package/session/card fingerprint plus compatible canonical name, exact prices, exact known quantity, and a clickable semantic path. Close duplicates are refused. If not visible, navigation searches downward and then upward within fixed bounds. There is no coordinate-click fallback.

## Experimental Android Live consumer surface

The current live adapter uses one movable VP bubble and a bottom sheet. This is temporary presentation/capture behavior, not a permanent architectural requirement.

If accessibility text is incomplete, the user can request a screenshot from advanced controls. The overlay is hidden, ML Kit recognizes text locally, and the same engine analyzes it. Secure windows can refuse capture; this is reported rather than bypassed.

## Privacy boundaries

The browser has no network code. Android's merged manifest removes INTERNET and ACCESS_NETWORK_STATE. There is no account, API key, telemetry SDK, server, remote model, or cross-store backend. Settings are stored locally; only newly visible cards scan automatically, while off-screen traversal and OCR are user initiated.
