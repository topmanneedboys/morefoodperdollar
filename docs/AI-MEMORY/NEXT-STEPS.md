# Next Steps

Updated: 2026-08-20

1. Rerun the exact Android command in root `AGENTS.md` after any change.
2. Inspect the generated APK with `apksigner` and `aapt`; verify package `com.valuepilot.app`, version `101.1.0`, and absence of INTERNET/ACCESS_NETWORK_STATE.
3. Uninstall the differently signed v101 debug build, install v101.1 on the Motorola Edge 2025, re-enable the Accessibility service, and execute the physical-device protocol in `PERFORMANCE_BUDGETS.md` at 20/60/100/160/250/500 products.
4. Reproduce Uber Eats `bananas` → `eggs`, standalone `milk`, member-price labels, same-name/different-size rows, visible row open, off-screen row open, ambiguous duplicate refusal, and stale session refusal.
5. Record Perfetto/HWUI, memory, scan metrics, navigation outcomes, app/version, and fixtures in `TEST_REPORT.md` and update the milestone gate.
6. If and only if every Android stop condition is device-verified, propose—but do not silently begin—the Universal Cart + Basket Optimizer domain-model milestone. Cross-store infrastructure follows that model.

Files to inspect first: `CURRENT_STATE.md`, `KNOWN_ISSUES.md`, `PERFORMANCE_BUDGETS.md`, `ValueAccessibilityService.kt`, `NodeScanner.kt`, `SearchContext.kt`, `IncrementalProductStore.kt`, `ItemLocator.kt`, and the Android tests.
