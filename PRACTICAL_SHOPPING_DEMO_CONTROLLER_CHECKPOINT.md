# ValuePilot Practical Shopping Demo Controller Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the first fully verified application-level Practical Shopping demo controller. Newer repository evidence overrides this file.

## Verified head

`0a9bde0bf31585ba03d691aba2035c9d0b6ef950` — `Keep practical shopping demo compatible with API 23`

GitHub Actions workflow run **118** (`33260325622`) completed successfully.

Verified gates all passed:

- browser model/engine/UI/Firefox checks
- browser extension packaging
- shared-core tests
- Android app JVM tests
- Android lint
- Android APK build
- JVM test summary
- Android privacy-boundary verification
- release files/checksums
- verified release artifact upload

No Android networking, account, telemetry or live-retailer integration was added.

## What is now verified

The offline fictional controller lives at:

`android/app/src/main/java/com/valuepilot/app/LocalSamplePracticalShoppingDemo.kt`

Its regression suite lives at:

`android/app/src/test/java/com/valuepilot/app/LocalSamplePracticalShoppingDemoTest.kt`

The controller is intentionally a tiny application fixture, not a production retailer/product resolver. All stores, prices and route values are explicitly fictional sample data.

The controller preserves the permanent architecture boundary:

1. It resolves only a small bounded sample vocabulary.
2. It sends resolved item identities to the real shared-core `PracticalShoppingPlanner`.
3. It sends the already-decided planner result to the verified `PracticalShoppingUiProjector`.
4. It does not duplicate store ranking, second-stop savings logic or UI price arithmetic.
5. Unknown items remain visible and block a false complete result.
6. Bare `chicken` remains ambiguous and requires one tiny refinement: Breast / Thighs / Drumsticks / Whole chicken / Ground chicken.
7. Eggs and milk may use visible fictional sample defaults.
8. Duplicate item words collapse deterministically before the shopping request is created.
9. A complete store beats an incomplete store with a suspiciously cheap known subtotal.
10. Penny-level split-store savings do not create an extra-stop recommendation under the sample `$15.00 CAD` threshold.
11. Query length is bounded at 240 characters.
12. Exposed resolved + unknown intents are bounded to 32 total.
13. The implementation remains compatible with Android minSdk 23.

## Regression cases now covered

- natural input `chicken eggs milk`
- explicit chicken choice after refinement
- explicit phrase `chicken breast eggs milk`
- duplicate groceries
- unknown groceries remaining visible
- complete versus incomplete store coverage
- below-threshold second-stop savings
- overlong query fail-closed behavior
- over-32 distinct-intent fail-closed behavior

The shared-core suite separately retains exact boundary tests proving `$14.00` second-stop savings is rejected while `$15.00` is accepted when route constraints are met.

## Defects found during verification

Verification was deliberately allowed to fail rather than weakening assertions.

### Combined intent-bound inconsistency

The first controller implementation checked a 32-intent ceiling but could expose up to 32 resolved rows plus 32 unknown rows in the error state. The UI-state invariant and error-state allocation were tightened so resolved + unknown rows cannot exceed 32 total.

### Test input hit the wrong guard

The first adversarial >32-intent test used long synthetic token names and exceeded the 240-character query limit first. The fixture was corrected to short tokens so the test reaches the distinct-intent guard it claims to exercise.

### Android API compatibility

Android lint correctly caught `Map.putIfAbsent` calls requiring API 24 while ValuePilot supports API 23. They were replaced with explicit deterministic contains/assignment logic. The minSdk was not raised and lint was not suppressed.

## Commit trail

- `05d888a1b14e64e49d3e7c9939f6b16b1ada8b1` — initial unverified fictional controller
- `463983f8ed982c2bb483267f52fd74c3933d8dd6` — controller regression suite
- `338d41855283bc46c993d5a053d222be24410cd9` — enforce combined 32-intent bound
- `8f0e7d44fd66c86f45a02842c8ef55cbd68542ad` — make the bound test exercise the intended guard
- `0a9bde0bf31585ba03d691aba2035c9d0b6ef950` — preserve Android API-23 compatibility; full workflow 118 passes

## Next engineering slice

The controller boundary is complete. The next layer may now begin: wire the polished Home Practical Shopping experience around the verified immutable state.

The Home slice must preserve these rules:

- make `What do you need?` the obvious primary action;
- keep the first-session flow fast and simple;
- render controller/projector state rather than recalculate decisions in Android views;
- keep chicken refinement tiny and one-tap;
- keep unknown items visible;
- show one primary practical recommendation rather than a dashboard;
- show a second-stop card only when the shared-core decision already recommends it;
- keep the fictional-data notice unmistakable during this proof stage;
- preserve Search as a separate surface;
- preserve Compare Here as a secondary workflow rather than letting it compete with the Home primary action;
- preserve no-network/no-account/no-telemetry Android boundaries;
- add regression coverage for any new pure presentation/coordinator logic and run the full CI gate again before calling Home verified.

Do not connect real retailers or add networking in this slice.
