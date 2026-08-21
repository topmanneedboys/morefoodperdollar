# V2 permanent architecture decision

ValuePilot core is not the Android overlay. The target flow is provider adapters → immutable observations → canonical product/offer intelligence → repository/session → matching/ranking → stable application state/API → replaceable presentations.

## Classification

| Component | Decision | Reason |
|---|---|---|
| Deterministic `ValueEngine` calculations and golden fixtures | KEEP | Valuable offline behavior; continue separating model loading from calculation. |
| Canonical product, quantity, offer, membership, promotion models | KEEP | Permanent domain concepts. |
| Search relevance, `SearchSession`, incremental repository | KEEP / REFACTOR | Preserve invalidation and bounded incremental storage; remove remaining JVM/platform conveniences when migrating. |
| Ranking and item matching | KEEP | Deterministic business logic behind stable contracts. |
| `ValuePilotUiState`, typed `ValuePilotIntent`, projector | KEEP / REFACTOR | Correct presentation boundary; complete adoption by legacy overlay incrementally. |
| Provider/parser/repository/ranking/matching contracts | KEEP | These implementations genuinely vary across platforms/providers. |
| Accessibility capture and exact-node navigation | EXPERIMENTAL | Android Live adapter only; removable without changing the core. |
| OCR | EXPERIMENTAL | Optional observation provider, never a core dependency. |
| overlay and floating bubble | EXPERIMENTAL | Temporary presentation/entry method, not product foundation. |
| legacy overlay domain-state ownership | REFACTOR | Migrate it to immutable UI state and typed intents; do not rewrite deterministic core. |
| capture implementations for changed retailer UIs | REWRITE AS NEEDED | Adapter changes must not force core changes. |
| browser TypeScript engine/extensions | KEEP | Share schemas, fixtures, and deterministic expected outputs; no forced Kotlin rewrite. |
| final Android capture UX and native redesign | DEFER | Requires product/device evidence. |
| Universal Cart, Basket Optimizer, backend, iOS | DEFER | Explicitly outside Session 1. |

## Boundary established in Session 1

`CoreContracts.kt` defines replaceable observation, parser, repository, ranking, and matching ports without Android imports. `AndroidLiveConnector` contains the Accessibility-node dependency at the adapter edge. `ValuePilotUiState.kt` defines immutable presentation state, typed intents, pure projection, and opaque result IDs. `IncrementalProductStore` implements the repository contract. Domain code can therefore be tested without constructing an Activity, View, overlay, service, or Accessibility node.

The legacy overlay has not yet completed migration to this state API. It remains an experimental compatibility adapter, and that incomplete adoption is recorded rather than hidden.

## KMP decision

Do not mass-migrate in this checkpoint. First isolate a pure Kotlin core deliberately compatible with later KMP. Today `ValueEngine` still reaches `LocalFoodModel`, whose loader uses Android `Context` and `org.json`; other files use JVM time, locale, normalization, and Java collections. Moving all of this at once would destabilize a working Android build.

Session 2 established a small pure Kotlin/JVM `shared-core` because the unavailable pinned toolchain prevented safely proving a KMP plugin migration. It contains new exact foundational models/math, while contaminated legacy code remains in place. After compilation is restored, KMP conversion should be mechanical: replace the few JVM overflow helpers, apply the pinned KMP plugin, and run the same golden tests. Browser TypeScript remains independent while consuming the same versioned fixtures.

Session 3 increased KMP readiness but did not convert the module: semantic contracts, product identity/equivalence, observations, money, and quantities are portable in design. Remaining JVM-only implementation details are `Math.*` overflow helpers and the JVM build plugin. A KMP conversion is permitted only after the current JVM module and Android consumer compile successfully under the pinned toolchain.
