# Platform dependency map

Audit date: 2026-08-21. The decision is the next safe action, not a claim that every item has moved.

| Concept/source | Contamination | Decision |
|---|---|---|
| `shared-core/CanonicalModels.kt` | Kotlin/JVM standard library and exact `Long` arithmetic only | KEEP platform-neutral; later KMP move is mechanical after replacing JVM overflow helpers if required |
| `ValueEngine` models/parser/ranking | `java.text.Normalizer`, `Locale`, `Double`, global `LocalFoodModel` | ABSTRACT normalization/model inference; adopt exact core money incrementally |
| `LocalFoodModel` | Android `Context`, assets, `org.json`, JVM normalization/locale | MOVE loading/JSON to Android adapter; keep pure optional inference behind a port |
| `SearchContext`/session | hidden `System.currentTimeMillis`, JVM normalization/locale | REMOVE hidden time defaults; supply times/IDs; ABSTRACT normalization |
| `SearchRelevance` | JVM normalization/locale | ABSTRACT one shared normalizer, then move |
| `IncrementalProductStore` | JVM `Locale` formatting; app `ValueItem` | REMOVE formatting-based identity; use exact canonical keys, then move |
| `ItemMatcher`/locator | matching is pure; locator includes capture paths/bounds/fingerprints | MOVE locator construction to adapter; retain canonical match evidence/decision in core |
| `CoreContracts` | no Android imports but references app-local types | REFACTOR types toward shared-core models before moving |
| `ValuePilotUiState` | no Android imports; JVM formatting and app-local ranked models | ABSTRACT display formatting; keep immutable application contracts outside UI |
| `NodeScanner`, `AndroidLiveConnector` | Accessibility nodes, Android clocks/rectangles | KEEP PLATFORM-SPECIFIC experimental capture adapter |
| `ValueAccessibilityService` | lifecycle, Handler/Looper, Accessibility, screenshot, Toast, executors | KEEP PLATFORM-SPECIFIC experimental orchestration |
| `OcrScanner` | Bitmap and ML Kit | KEEP PLATFORM-SPECIFIC optional provider |
| `OverlayController`, `ResultAdapter`, `MainActivity` | Views, WindowManager, resources/lifecycle | KEEP PLATFORM-SPECIFIC presentation; migrate toward immutable state consumption only |

No shared-core source may depend back on the app, overlay, capture, OCR, or a retailer implementation.
