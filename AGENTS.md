# ValuePilot agent operating rules

## Persistent-memory protocol

1. Repository files are authoritative. Chat history and compaction summaries are advisory only.
2. Before changing implementation, inspect the relevant current source, tests, specifications, and `docs/AI-MEMORY/` files.
3. If durable memory conflicts with repository evidence, verify the canonical source and update the memory.
4. Never reconstruct or silently change an API, invariant, acceptance criterion, path, architectural decision, or user-approved requirement when a canonical repository source exists.
5. Never weaken, delete, or rewrite an acceptance test merely to make implementation pass.
6. Verify important claims with tools. Label facts as code-inspected, test/build-verified, device-verified, or not yet verified.
7. After meaningful work, update `CURRENT_STATE.md`, `KNOWN_ISSUES.md`, `PERFORMANCE_BUDGETS.md`, and `docs/AI-MEMORY/`.
8. Before declaring completion, reread the specification and state, run relevant verification, compare the implementation with acceptance criteria, and inspect the final artifact.
9. Do not modify outside this project unless separately authorized.

## Current scope

- Work only on the existing Android ValuePilot milestone unless the user explicitly changes scope.
- Do not begin iOS, Supabase, a cross-store backend, Universal Cart, or Basket Optimizer implementation yet.
- Browser code is shared reference only unless an Android change requires parity work.
- Preserve the local-only privacy boundary. The Android APK must not declare `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`.

## Permanent architecture invariant

- ValuePilot core is not an Android overlay.
- Keep capture/platform adapters, immutable observations, parsing/intelligence, repository/session, matching/ranking, application state, and presentations separable.
- UI components render immutable state and emit typed intents. Do not put scanning, parsing, matching, ranking, sessions, promotion arithmetic, AI, or navigation policy in a View, Activity, overlay, or adapter.
- Accessibility, OCR, bubble, overlay, and live navigation are experimental/optional adapters and must be removable without breaking core tests.
- Prefer a small pure-Kotlin boundary compatible with incremental KMP migration; do not perform a mass rewrite. Browser TypeScript may share schemas/fixtures without being replaced.

## Android invariants

- Accessibility callbacks must ignore ValuePilot's own package and coalesce event storms.
- Accessibility-tree capture is one bounded pass. Do not restore repeated price-node ancestor subtree scans.
- Parse changed immutable card snapshots off the main thread.
- Product storage is scoped to `SearchContext.sessionId`; a query, store, platform, or strong page transition must invalidate stale products.
- Normal result updates use `RecyclerView`, `ListAdapter`, stable IDs, and `DiffUtil`. Do not reintroduce `removeAllViews()` row redraws.
- Product navigation is fail-closed: verify package/session/card fingerprint/name/current price/member price/quantity, refuse ambiguous matches, and never use an uncertain coordinate click.
- Exact measurements and model-derived estimates must remain visibly distinct.
- Only one VP launcher is persistent. Opening the sheet hides it; minimizing the sheet restores it.

## Verification commands

From `android/`, with JDK 17 and Android SDK/API 36 installed:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Inspect the artifact:

```bash
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
```

The expected package is `com.valuepilot.app`, version code `10101`, version name `101.1.0`, minimum API 23, and target API 36. See `PERFORMANCE_BUDGETS.md` for performance gates and `KNOWN_ISSUES.md` for the remaining real-device gate.
