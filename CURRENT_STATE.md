# Current State

Updated: 2026-08-21

## Session 1 permanent-core checkpoint

The product foundation is now provider- and presentation-independent by policy. Persistent Accessibility capture, OCR, overlay, and bubble are experimental Android adapters, not ValuePilot core. Platform-neutral contracts and immutable application/UI state have been added; the legacy overlay has not yet completed adoption of that state boundary. See `V2_ARCHITECTURE_DECISION.md` and `CRASH_ROOT_CAUSE.md`.

The earlier passing verification below predates these Session 1 edits. Session 1 rerun was blocked before compilation because this fresh workspace lacks a writable cached Gradle 9.5 distribution, Android SDK/JDK compiler toolchain, and permitted network access to retrieve them. Browser deterministic tests passed 26 cases, but the integration test could not load absent `jsdom`; dependency installation was also network-blocked. Therefore this checkpoint is **not build-verified** and no new APK is claimed.

## Baseline inspected

- Repository: `topmanneedboys/morefoodperdollar`
- Starting commit: `ceed4d4dbd83f3636c5e9af858d8c4a42e98b2e5`
- Starting source branch: `agent/valuepilot-apk-ci`
- Local work branch: `work/valuepilot-android-milestone`
- Uploaded v101 APK SHA-256: `e3e35c4c89bb2b5ccc1d66300bfcdd26729c5691123b6dc5bbd83a33c65017e2`
- Uploaded APK and starting source both identified as package `com.valuepilot.app`, version code `10100`, version name `101.0.0`.
- Baseline Android unit tests, lint, and debug assembly passed before modification.

The Android project, build configuration, ValueEngine, NodeScanner, AccessibilityService, overlay controller, OCR scanner, filtering, local model, tests, workflow, and shared browser behavior were inspected from the current repository rather than inferred from old snippets.

## Implemented Android v101.1 state

- Version code `10101`; version name `101.1.0`.
- `SearchContext` tracks platform, package, store, normalized query, query/page fingerprints, session ID, and start time.
- Query/store/page transitions reset the incremental product store; query relevance is also applied before ranking.
- The scanner performs one bounded tree pass, computes subtree facts bottom-up, emits immutable card snapshots, maintains fingerprints, and reports scan metrics.
- Accessibility events are filtered, signature-gated, and coalesced into one pending scan. Unchanged cards are rejected before parsing.
- Changed cards parse on one low-priority background executor. Ranking requests are latest-only/coalesced and execute off the main thread.
- Product names remove member/previous/regular price phrases. `PriceOffer` keeps current, member, previous, regular, and sale values separate.
- Unit-rate labels such as `$0.39/item` are not mistaken for the total product price.
- Normal UI updates use `RecyclerView` + `ListAdapter` + `DiffUtil` + stable IDs.
- Minimized state has one VP bubble. Open state hides the bubble and shows a draggable bottom sheet at roughly 48% screen height.
- Header, contextual match count, consumer rank menu, Filters, Rescan, readable wrapping rows, loading state, motion-aware transitions, and optional haptics are implemented.
- Scan-all and OCR remain advanced mechanisms rather than primary controls.
- Result rows are fully tappable. Navigation reacquires visible or off-screen cards in both scroll directions and clicks only after strict session/package/card/name/price/member/quantity validation. Ambiguity fails closed.
- Android manifest removes both INTERNET and ACCESS_NETWORK_STATE permissions declared by dependencies.
- A Gradle 9.5 wrapper is present under `android/`.

## Verification status

The most recent complete automated run passed:

```bash
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- 36 JVM tests: 0 failures, 0 errors, 0 skipped.
- Android lint task: passed.
- Debug APK assembly: passed after a clean dependency/dex rebuild.
- Final inspected APK: package/signature/ZIP/permission gates passed, SHA-256 `e8463cb0d3fa1b7226d245dbcf39f7bf9e925c56f1c656739b121d76dd95e944`. Rerun after every future source change.
- Browser source remains unchanged; browser validation is required at the Session 1 checkpoint.

## Milestone gate

The implementation and automated evidence cover the requested defects and 20–500 product fixtures. The milestone is not yet declared fully complete because responsiveness and exact navigation have not been exercised on the reported Motorola Edge 2025 or another physical Android shopping-app session. No backend, cross-store, Universal Cart, Basket Optimizer, or iOS work has begun.
