# ValuePilot v101 / Android v101.1

ValuePilot is a private, local-first value ranker for groceries and restaurant menus. It ships as a Chromium extension, Firefox extension, and Android accessibility overlay.

## Android v101.1 milestone candidate

The existing Android app now has a search-scoped incremental pipeline, a virtualized consumer bottom sheet, clean offer/name parsing, and fail-closed exact-product reopening. It is designed and regression-tested for 20–500 collected products. Physical Motorola Edge 2025 validation is still required before the Android milestone is declared complete; see [`CURRENT_STATE.md`](CURRENT_STATE.md), [`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md), [`DEVICE_VALIDATION.md`](DEVICE_VALIDATION.md), and [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).

## What changed in v101

- A compact on-device semantic model classifies 19 food/menu categories without an account, API key, or network request.
- New visibly labeled **AI food/$** and **AI meat/$** fallback modes cover menus that omit weight and calories.
- Optional **Food only**, **No pork**, and maximum-spend filters persist locally.
- Promotion-aware budgets use the actual minimum checkout spend for BOGO-percent, buy-X-get-Y, and N-for-$X deals.
- Expanded mass, volume, count, currency, thousands/decimal, promotion, name, and Unicode handling.
- Safer browser DOM rendering and Android overlay/window isolation.
- One release workflow tests both browser targets and Android, lints them, verifies the APK has no network permissions, and emits checksums.

The model never invents grams or overrides known facts. Exact price, mass, volume, count, calories, pizza area, and promotion math remain authoritative; AI outputs are bounded relative estimates.

## Ranking concepts

- Smart Value
- Price per kg
- Price per litre
- Price per item
- Calories per dollar
- Pizza size per dollar (computed from circular area, not diameter)
- Food amount/$ · Estimate
- Meat value/$ · Estimate

Android shows only ranking choices relevant to the current query/results and clearly separates calculated measurements from estimates. The v101 browser packages retain their existing compact labels.

## Browser install

### Chromium (Chrome, Edge, Brave, Opera)

1. Unzip `ValuePilot-v101-chromium.zip`.
2. Open the browser's extensions page and enable Developer mode.
3. Choose **Load unpacked** and select the unzipped folder.
4. Open a shopping or menu page and use the floating **VP** button.

### Firefox 142+

The downloadable ZIP is a development package, not an AMO-signed store release. Load its `manifest.json` from `about:debugging` → **This Firefox** → **Load Temporary Add-on**. Temporary add-ons need to be reloaded after Firefox restarts.

## Android install

The provided APK is an installable debug build for Android 6.0+ (API 23). Screenshot OCR requires Android 11+.

1. Allow installation from the app you use to open the APK, then install it.
2. Open ValuePilot and read the accessibility disclosure.
3. Enable **ValuePilot screen value scanner** in Android Accessibility settings.
4. Open a store/menu app and search normally. ValuePilot automatically processes newly visible product cards.
5. Tap the single floating **VP** bubble to open the bottom sheet. Use **Rescan** when needed; filters, member-price settings, off-screen collection, and OCR are under **Filters**.
6. Tap a result to reopen that exact product when ValuePilot can reacquire it confidently. Ambiguous or stale matches are never clicked.

Android deliberately declares neither `android.permission.INTERNET` nor `android.permission.ACCESS_NETWORK_STATE`. The bundled ML Kit recognizer and compact classifier both run on the device.

## Development and verification

```bash
cd browser-extension
npm ci
npm run check
```

The browser check regenerates/verifies the model, runs engine and Shadow DOM integration tests, packages both targets, and runs Mozilla's extension validator with warnings treated as errors.

Android sources live in `android/`, target API 36, and include a Gradle 9.5 wrapper. Run:

```bash
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The GitHub workflow runs JVM tests, Android lint, builds the APK, inspects its final permissions, packages both browser targets, and creates `SHA256SUMS.txt`. Exact continuation commands and architectural invariants are in [`AGENTS.md`](AGENTS.md).

## Privacy and platform limits

- No account, remote AI service, API key, analytics, or backend.
- Browser code makes no outbound requests.
- Android has no INTERNET or ACCESS_NETWORK_STATE permission.
- Settings stay in browser storage or Android SharedPreferences.
- OCR and classification run locally.
- Accessibility collection is automatic for newly visible cards. Off-screen list traversal and OCR remain explicit advanced actions.

Third-party apps can still hide accessibility text, block screenshots with secure windows, render text only in canvas/WebGL, or change their markup. ValuePilot reports missing evidence instead of fabricating precise-looking values.
