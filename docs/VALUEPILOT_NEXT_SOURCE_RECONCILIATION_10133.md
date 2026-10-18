# ValuePilot Next — Source Reconciliation and Stage 1 Checkpoint

Updated: 2026-10-08

## A. Verified repository state

- The exact 10133 source is available at `C:\Users\iamatopstrongman\Documents\Codex\vp-search-design`.
- Its clean source branch is `work/valuepilot-search-design`, HEAD `9e24da055a3f79a897440bec1c6a028d12113d6b` (`Fix Search result card action layout`).
- Android configuration reports application ID `com.valuepilot.app`, version code `10133`, version name `101.1.32`, min SDK 24, compile SDK 36, and target SDK 36.
- The full source tree is available: 1,001 tracked files across Android, shared core, backend, consumer edge, browser extension, tools, and supporting data.
- This is a local, unpushed, untagged development branch. The authenticated GitHub remote advertises `main` at version code 10130 and `candidate/valuepilot-10131` at 10131; it does not advertise `work/valuepilot-search-design` or a 10133 branch. The 10133 branch contains four commits beyond remote `main` and is a sibling of the 10131 candidate-preparation commit.
- The user-specified path `I:\AI Software\Projects\food saving cost\ValuePilot` is absent. The older checkout found at `I:\AI Software\Projects\ValuePilot\ValuePilot` is version 10101.
- The earlier Compose checkpoint remains preserved as commit `ea41ab4e0e45a2fb48fe0a873a09d9018f360d7c` on `work/valuepilot-next-ui-foundation`. Its pre-existing untracked `tools/__pycache__/` folder was left untouched.
- The new 10133-based continuation branch is `work/valuepilot-next-ui-foundation-10133`, checked out separately from the clean `work/valuepilot-search-design` source worktree.

## B. 10101-to-10133 feature comparison

| Capability | 10101 checkout | 10133 source found |
| --- | --- | --- |
| Search and production prices | Local sample product provider; no `INTERNET` permission or production search transport. | Production-authorized Search is wired through the Argentina backend transport for the configured Buenos Aires launch area. The Android and backend unit suites pass, but no live service request was made. |
| Exact package and unit-price comparison | Existing on-device comparison and evidence-backed unit-value logic are present. | Retained and expanded with package-quantity fact resolution, production eligibility, and evidence-focused tests. Compose does not reimplement it. |
| Basket and shopping plan | The audited Basket route was a placeholder; local sample planning is not production shopping data. | Includes a Search-backed private basket plan and production shopping decision code. The Home planner still uses a separate, explicitly fictional local sample model. |
| Multi-store and trip costs | No production multi-store or trip-cost flow was found. | Shopping planning includes store/extra-stop decisions and a private trip-cost calculator for supported flows. This is not a separate screen named “Trip Planner.” |
| Store map | No store-map feature was found. | Provides evidence-backed store context and a handoff to an external map app; it is not an embedded map screen. |
| Watch | No Watch flow was found. | Saved includes `Staple Watch` setup and policy routes. The literal “Price Watch” label was not found. |
| Saved state and history | No durable Saved list or private price-history feature was found. | Has private saved identities, local preferences, and user-observed private price history. A distinct confirmed-purchase history screen was not found; private price observations are not purchase records. |

The 10133 Home sample planner remains distinct from production-authorized Search. Existing copy explicitly calls its sample plan fictional. The Compose preview keeps that boundary visible and contains no fabricated product or price rows.

## C. Architecture map and preservation boundary

- `MainActivity`, its View-based Home/Search/Basket/Saved surfaces, `AppShellReducer`, backend transport, repositories, and durable stores remain the production application path.
- `android/shared-core` continues to own evidence, product identity, package quantity, unit-value calculations, shopping planning, and their platform-neutral tests.
- `MainActivity` wires Search to `ArgentinaLiveSearchSurfaceHost.createProduction(...)`. It also retains the existing local sample Home model; Compose does not use that model as production offer data.
- Home preferences and session state use private local storage. The manifest keeps `allowBackup=false` and `usesCleartextTraffic=false`; `INTERNET` remains present and `ACCESS_NETWORK_STATE` remains removed.
- The new Compose Activity and UI files live in `src/debug`, and the Activity is registered only by the debug manifest. Compose UI dependencies are debug-only. Release compilation uses a compile-only Compose Runtime reference for the compiler plugin, and `releaseRuntimeClasspath` contains no Compose libraries.
- The existing `MainActivity` remains the launcher. Its preview action is visible only in a debuggable app. The Compose preview reuses `AppShellReducer`, supports Home/Search/Basket/Saved navigation, opens the existing comparison Activity, and does not read or write private shopping state.
- No backend, shared-core, pricing, persistence-schema, application-ID, version, network-permission, or signing changes were made.

## D. Stage 1 design and implementation

Three directions were evaluated:

| Direction | Strength | Main risk |
| --- | --- | --- |
| **A — Sunlit Intelligence**: warm ivory, charcoal, burnt orange, editorial display type, quiet surfaces. | Welcoming and distinctive while preserving clear price and quantity hierarchy. | Cream surfaces can lose contrast without strong text and semantic states. |
| **B — Precision Minimalism**: cool white, graphite, compact numeric hierarchy, minimal chrome. | Efficient for dense, fast unit-price comparison. | Can feel generic and make source/evidence distinctions too easy to compress. |
| **C — Immersive Commerce**: rich imagery, expanded product panels, expressive transitions. | Supports discovery when dependable product imagery exists. | 10133 does not provide an image-backed live catalog for every item; visual richness could imply unsupported offers. |

**Selected: Direction A, with B's disciplined information hierarchy.** The implementation defines shared light/dark semantic colors, typography, spacing, shapes, elevation, minimum touch sizing, status colors, reduced-motion behavior, and short transitions. Phone layouts use bottom navigation; wider layouts use a navigation rail. The Home shell contains no fabricated product rows or prices.

The current 10133 line retains compile SDK 36 and the previously compiled Compose BOM 2026.06.00. Android's current Compose setup documentation lists BOM 2026.09.00; Compose 1.12.0 requires compile SDK 37, which AGP 9.3 supports. Moving to that BOM therefore needs an intentional compile-SDK update; it was not bundled into this source reconciliation. See [Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) and [AGP 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes).

## E. Verification

- Baseline and post-port Android command: `:shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — passed; 2,139 JVM tests, 0 failures, 0 errors, 0 skipped.
- Backend command: `python -m unittest discover -s backend/tests` — passed; 121 tests.
- Release source check: `:app:compileReleaseKotlin :app:processReleaseManifest` — passed after adding the compile-only Compose Runtime. The release manifest excludes `ValuePilotNextActivity` and release runtime dependencies contain no Compose libraries.
- Debug APK metadata confirms application ID `com.valuepilot.app`, version code 10133, version name `101.1.32`, and min SDK 24. Its permissions include the existing `INTERNET` and `CAMERA` permissions plus AndroidX's generated receiver permission; `ACCESS_NETWORK_STATE` is absent.
- No Android device is connected to ADB. Device interaction, visual rendering, and accessibility behavior were not runtime-verified.
- Kotlin emitted existing deprecation and data-class visibility warnings; no tests or lint checks failed. No release APK was built or signed, and no secrets were inspected.

## F. Stage gate and next work

- Stage 1 is a compiled, debug-only Compose design and navigation shell on top of the actual 10133 source. The existing release UI remains the default.
- Stage 2 has not started. The verified source boundary is now clear: future Compose screens should adapt the existing production providers and durable state through presentation-facing interfaces, while leaving pricing, evidence, identity, ranking, basket calculations, and persistence in their existing owners.
- Keep the two history concepts distinct: 10133 has private user-observed price history; no separate purchase-history implementation was verified.
- Keep the map capability accurately described as external map handoff unless a real in-app map source is found.
- The source-of-record branch remains unpushed. The 10133-based Compose work is isolated on `work/valuepilot-next-ui-foundation-10133`; returning to commit `9e24da055a3f79a897440bec1c6a028d12113d6b` restores the recovered source baseline.
