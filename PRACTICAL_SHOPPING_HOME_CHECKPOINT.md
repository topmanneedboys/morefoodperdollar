# ValuePilot Practical Shopping Home Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the first fully verified Practical Shopping Home consumer slice. Newer repository evidence overrides this file.

## Verified head

`7a9775a0f2d5e9a3442eab58df90a7482117d213` — `Remove empty Home surface padding`

GitHub Actions workflow run **123** (`33261460772`) completed successfully.

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

No Android networking, account, telemetry, remote AI, live-retailer integration or provider plumbing was added.

## Verified Home architecture

The visible Home flow now preserves this boundary:

`fictional controller -> PracticalShoppingHomeSession -> PracticalShoppingHomeRenderer -> PracticalShoppingHomeSurfaceView`

The underlying shopping decision still flows through the existing verified layers:

`resolved sample intents -> shared-core PracticalShoppingPlanner -> PracticalShoppingUiProjector -> controller UI state`

Important invariants:

1. `PracticalShoppingHomeSession` persists/restores only the user-level session inputs needed to reproduce state: query, whether it was submitted, and an explicit chicken choice when present.
2. Android lifecycle state does not persist a detached shopping decision, store ranking or basket result as authority.
3. `PracticalShoppingHomeRenderer` receives controller UI state and passes the already-projected `PracticalShoppingUiState` through unchanged.
4. The Home Android view receives only immutable render state and emits typed actions. It owns no shopping resolution, store ranking, basket arithmetic, second-stop threshold or provider logic.
5. Search remains a separate primary surface.
6. Compare remains a secondary workflow rather than competing with the Home primary action.

## Verified Home UX

Home now leads with the product question:

> **What do you need?**

The primary interaction is a plain-language list input plus one `Plan my shop` action.

The fictional proof flow demonstrates the intended low-friction behavior:

- `chicken eggs milk` keeps Eggs and Milk visible with clearly labeled sample defaults;
- bare `chicken` is not guessed and instead produces one tiny one-tap refinement: Breast / Thighs / Drumsticks / Whole chicken / Ground chicken;
- unknown groceries remain visibly unresolved instead of being silently dropped;
- a completed plan renders one primary practical-shopping recommendation;
- an optional second-stop card appears only when the upstream shared-core decision already recommends it;
- otherwise the upstream short not-worth-it message is rendered rather than another competing recommendation;
- the fictional-data notice stays unmistakable: no sample store/price/route is represented as a real merchant claim;
- direct product comparison remains available as a clearly secondary action;
- empty list/result hosts no longer add unexplained blank vertical space before real content exists.

## Verification trail

- `6947364f4f884e8ebd719d6b3c38aa9d7b8b1fa7` — add Home session boundary
- `1a00bd68d58fa32688da87d97019b251e9e9585c` — add Home session restoration regressions; workflow **120** (`33260649933`) passed
- `e994404fbbdd2462160f25bcdb36f44c86612a72` — add immutable Home renderer + tests; workflow **121** (`33260866925`) passed
- `37a0eb344d022b7f7e4c49d77771a5141068f7f5` — wire visible Practical Shopping Home experience; workflow **122** (`33261359768`) passed
- `7a9775a0f2d5e9a3442eab58df90a7482117d213` — remove empty Home host padding; workflow **123** (`33261460772`) passed

## Remaining Home trust gap

The durable product specification calls for a short consumer-facing **Why this store** explanation.

That explanation must not introduce a new score or re-run ranking in presentation. The safe next slice is to derive one concise explanation from the already-decided `PrimaryShoppingPlanKind` in `PracticalShoppingUiProjector`, for example:

- complete comparison: explain that this is the lowest known complete basket among eligible one-store candidates;
- incomplete comparison: explain that no complete basket is priced and this option has the best requested-item coverage under the planner's deterministic policy.

The explanation must be regression-tested against shared-core decision semantics and then rendered by Home without reinterpretation.

After that, review Home interaction/performance behavior and only then consider the next data-adapter phase. Do not jump directly to real retailer networking or provider plumbing.