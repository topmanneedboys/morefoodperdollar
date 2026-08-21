# Platform dependency map

Audit date: 2026-08-21. The decision is the next safe action, not a claim that every item has moved.

| Concept/source | Contamination | Decision |
|---|---|---|
| `shared-core/CanonicalModels.kt` | Kotlin/JVM standard library and exact `Long` arithmetic only | KEEP platform-neutral; later KMP move is mechanical after replacing JVM overflow helpers if required |
| `ValueEngine` models/parser/ranking | legacy measurement/score and money `Double`; display formatting | Global model access REMOVED; canonicalization isolated; continue exact-money migration incrementally |
| `LocalFoodModel` | Android `Context`, assets, `org.json`, JVM normalization/locale | MOVE loading/JSON to Android adapter; keep pure optional inference behind a port |
| `SearchContext`/session | JVM canonicalizer adapter; app-local context detector | Hidden clock REMOVED; supplied observation time is deterministic; move pure identity after compilation |
| `SearchRelevance` | app-local aliases and JVM canonicalizer adapter | Locale dependence REMOVED; migrate only when normalization port is proven |
| `IncrementalProductStore` | app `ValueItem` and capture-card bookkeeping | Locale formatting REMOVED; stable scaled `ProductIdentityKey` now used |
| `ItemMatcher`/locator | Android capture paths/bounds/fingerprints | Core `ProductMatching` separated; KEEP navigation reacquisition platform-specific |
| `CoreContracts` | no Android imports but references app-local parsed types | Semantic port added; REFACTOR remaining types toward shared-core models |
| `ValuePilotUiState` | no Android imports; JVM formatting and app-local ranked models | ABSTRACT display formatting; keep immutable application contracts outside UI |
| `NodeScanner`, `AndroidLiveConnector` | Accessibility nodes, Android clocks/rectangles | KEEP PLATFORM-SPECIFIC experimental capture adapter |
| `ValueAccessibilityService` | lifecycle, Handler/Looper, Accessibility, screenshot, Toast, executors | KEEP PLATFORM-SPECIFIC experimental orchestration |
| `OcrScanner` | Bitmap and ML Kit | KEEP PLATFORM-SPECIFIC optional provider |
| `OverlayController`, `ResultAdapter`, `MainActivity` | Views, WindowManager, resources/lifecycle | KEEP PLATFORM-SPECIFIC presentation; migrate toward immutable state consumption only |

No shared-core source may depend back on the app, overlay, capture, OCR, or a retailer implementation.

## `Double` classification

| Use | Classification | Session 3 action |
|---|---|---|
| `PriceOffer`, promotion minimum spend/effective price, budget comparison | MONEY | Remains legacy; exact `Money` is the target and conversion must preserve golden behavior |
| repository/dedupe price identity | MONEY | Converted once at the boundary to exact minor units |
| mass, volume, dimensions, calories | MEASUREMENT | Retain `Double` until an accuracy/range-driven replacement is proven |
| rank/relevance/confidence | SCORE/CONFIDENCE | `Double` is appropriate; never use as a monetary total |
| portion/meat/category signals | ESTIMATE | `Double` is appropriate and explicitly optional |

This prevents a blind numeric rewrite: monetary totals migrate first, while physical measurements and probabilistic scores keep suitable precision.
