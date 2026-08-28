# ValuePilot agent rules

1. Read `CURRENT_STATE.md`, `CONTINUATION_CHECKPOINT.md`, `ARCHITECTURE.md`, and the relevant provider/status files and tests first. Repository evidence overrides chat summaries, and newer repository evidence overrides an older checkpoint.
2. Never restart blindly. Make one focused, reversible change at a time.
3. `android/shared-core` stays platform-neutral: no Android, UI, capture, OCR, retailer, filesystem, network, or hidden clock dependencies.
4. UI renders immutable state and emits typed actions; it owns no parsing, ranking, matching, session, promotion, or navigation rules.
5. Capture adapters emit observations and own no ranking logic. Accessibility/OCR/overlay are experimental and optional.
6. Money and quantity arithmetic stays explicit, deterministic, currency-aware, and tested. AI is always optional evidence.
7. Bound, coalesce, cancel, and measure expensive work. Never introduce unbounded scans, queues, caches, requests, or rendered rows.
8. Never weaken a regression test. Add cases to `shared-fixtures/` when clients share a deterministic rule.
9. Run tests before claiming completion; label anything not run as unverified. See the command below and inspect the APK permissions.
10. Do not start Universal Cart, Basket Optimizer, backend, cross-store, iOS, subscriptions, affiliates, or AI integration without an explicit milestone.

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
