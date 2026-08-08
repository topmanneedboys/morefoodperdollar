# ValuePilot v101 architecture

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

## Android extraction

The accessibility service reconstructs candidate card text from node subtrees, tracks the last external app window, ignores its own overlay events, and accumulates virtualized items during explicit forward scrolling. It then scrolls backward toward the start.

If accessibility text is incomplete, the user can request a screenshot. The overlay is hidden, ML Kit recognizes text locally, and the same engine analyzes it. Secure windows can refuse capture; this is reported rather than bypassed.

## Privacy boundaries

The browser has no network code. Android declares no INTERNET permission. There is no account, API key, telemetry SDK, server, remote model, or background scraping process. Settings are stored locally and Scan all is user initiated.
