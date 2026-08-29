# ValuePilot Practical Shopping Home Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified Practical Shopping Home consumer slice after explanation and interaction-boundedness hardening. Newer repository evidence overrides this file.

## Latest verified engineering head

`a93d02daa15cb689235fbc867dedf0ffe47e58b4` — `Bound Practical Shopping Home query state`

GitHub Actions workflow run **126** (`33262248609`) completed successfully.

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

The visible Home flow preserves this boundary:

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
7. The consumer-facing primary explanation is produced by `PracticalShoppingUiProjector` from the already-decided `PrimaryShoppingPlanKind`; Android renders the supplied `whyText` without reinterpretation.

## Verified Home UX

Home leads with the product question:

> **What do you need?**

The primary interaction is a plain-language list input plus one `Plan my shop` action.

The fictional proof flow demonstrates the intended low-friction behavior:

- `chicken eggs milk` keeps Eggs and Milk visible with clearly labeled sample defaults;
- bare `chicken` is not guessed and instead produces one tiny one-tap refinement: Breast / Thighs / Drumsticks / Whole chicken / Ground chicken;
- unknown groceries remain visibly unresolved instead of being silently dropped;
- a completed plan renders one primary practical-shopping recommendation;
- the primary card now includes a short auditable explanation of why the upstream planner selected that plan;
- a complete comparison explains that the selected option has the lowest known complete basket among the compared one-store options;
- an incomplete comparison explicitly says no complete basket is priced and that the selected option covers the most requested items;
- an optional second-stop card appears only when the upstream shared-core decision already recommends it;
- otherwise the upstream short not-worth-it message is rendered rather than another competing recommendation;
- the fictional-data notice stays unmistakable: no sample store/price/route is represented as a real merchant claim;
- direct product comparison remains available as a clearly secondary action;
- empty list/result hosts do not add unexplained blank vertical space before real content exists.

## Interaction and boundedness review

The Home proof now has explicit enforceable bounds rather than relying on UI goodwill:

- normal usable query length remains at most 240 characters;
- an over-limit error state retains at most 241 characters, just enough to prove that the limit was exceeded;
- a 100,000-character lifecycle restoration regression proves that oversized input is reduced to the bounded error-state envelope before it can remain in controller/snapshot state;
- exposed resolved + unknown shopping intents remain capped at 32 total;
- typing uses the controller's lightweight `QueryChanged` path and does not invoke the shopping planner;
- planning occurs only on explicit submit/chicken-choice actions and the fictional candidate set remains tiny and fixed;
- `syncQuery` does not call `setText` when the displayed query already equals immutable state, avoiding an unnecessary cursor reset during normal typing;
- Home owns no background queue, cache, polling loop, network request or unbounded rendered collection.

These are code/CI invariants. They are not a substitute for physical-device frame measurements. No claim is made here that Motorola/real-device frame timing has been measured for this Home slice yet.

## Verification trail

- `6947364f4f884e8ebd719d6b3c38aa9d7b8b1fa7` — add Home session boundary
- `1a00bd68d58fa32688da87d97019b251e9e9585c` — add Home session restoration regressions; workflow **120** (`33260649933`) passed
- `e994404fbbdd2462160f25bcdb36f44c86612a72` — add immutable Home renderer + tests; workflow **121** (`33260866925`) passed
- `37a0eb344d022b7f7e4c49d77771a5141068f7f5` — wire visible Practical Shopping Home experience; workflow **122** (`33261359768`) passed
- `7a9775a0f2d5e9a3442eab58df90a7482117d213` — remove empty Home host padding; workflow **123** (`33261460772`) passed
- `f9332a8e43a05601d65dfd4972ba06666c63c401` — derive auditable primary-plan explanation in the projector; workflow **124** (`33261712379`) passed
- `a627bc2fd459924b3869a65cc0721da2c80e3704` — render the already-projected explanation in Home; workflow **125** (`33261956215`) passed
- `a93d02daa15cb689235fbc867dedf0ffe47e58b4` — bound retained Home query/lifecycle state and add the adversarial restoration regression; workflow **126** (`33262248609`) passed

## Next engineering slice

Do not jump directly to real retailer networking or route provider data through the fictional controller.

The next safe production-facing step is to inspect and define the smallest deterministic **Practical Shopping evidence-to-candidate bridge** that can eventually consume source-isolated trusted evidence and explicit store/travel inputs, then emit bounded `SingleStorePlanCandidate` / `TwoStorePlanCandidate` values for the already-verified shared-core planner.

Requirements for that bridge:

1. reuse existing source-isolated evidence, authorization/freshness/conflict and exact-money boundaries rather than creating another truth database;
2. keep Product identity separate from retailer/location/channel Offer identity;
3. never fill an unknown price merely to complete a basket;
4. accept travel/location facts as explicit inputs rather than adding hidden routing/network work to shared-core;
5. remain bounded and deterministic;
6. preserve one-store-first policy entirely in `PracticalShoppingPlanner` rather than duplicating ranking in the bridge;
7. add regression coverage before any open-data or authorized-provider adapter is allowed to drive Home;
8. preserve the current Android no-network/no-account/no-telemetry boundary until a separate explicit networking milestone.
