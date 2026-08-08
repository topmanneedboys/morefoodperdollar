# ValuePilot v101

ValuePilot is a private, local-first value ranker for groceries and restaurant menus. It ships as a Chromium extension, Firefox extension, and Android accessibility overlay.

## What changed in v101

- A compact on-device semantic model classifies 19 food/menu categories without an account, API key, or network request.
- New visibly labeled **AI food/$** and **AI meat/$** fallback modes cover menus that omit weight and calories.
- Optional **Food only**, **No pork**, and maximum-spend filters persist locally.
- Promotion-aware budgets use the actual minimum checkout spend for BOGO-percent, buy-X-get-Y, and N-for-$X deals.
- Expanded mass, volume, count, currency, thousands/decimal, promotion, name, and Unicode handling.
- Safer browser DOM rendering and Android overlay/window isolation.
- One release workflow tests both browser targets and Android, lints them, verifies the APK has no INTERNET permission, and emits checksums.

The model never invents grams or overrides known facts. Exact price, mass, volume, count, calories, pizza area, and promotion math remain authoritative; AI outputs are bounded relative estimates.

## Ranking modes

- `Smart`
- `$/kg`
- `$/L`
- `$/unit`
- `Calories/$`
- `Pizza area/$` (computed from circular area, not diameter)
- `AI food/$`
- `AI meat/$`

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
4. Open a store/menu app and tap the floating **VP** bubble.
5. Use **Loaded** for visible content, **Scan all** for explicitly requested lazy-list traversal, or **OCR** for on-device screenshot text recognition.

Android deliberately declares no `android.permission.INTERNET`. The bundled ML Kit recognizer and compact classifier both run on the device.

## Development and verification

```bash
cd browser-extension
npm ci
npm run check
```

The browser check regenerates/verifies the model, runs engine and Shadow DOM integration tests, packages both targets, and runs Mozilla's extension validator with warnings treated as errors.

Android sources live in `android/` and target API 36. The GitHub workflow runs JVM tests, Android lint, builds the APK, inspects its final permissions, packages both browser targets, and creates `SHA256SUMS.txt`.

## Privacy and platform limits

- No account, remote AI service, API key, analytics, or backend.
- Browser code makes no outbound requests.
- Android has no INTERNET permission.
- Settings stay in browser storage or Android SharedPreferences.
- OCR and classification run locally.
- Scan all acts only after the user requests it and returns toward the starting position.

Third-party apps can still hide accessibility text, block screenshots with secure windows, render text only in canvas/WebGL, or change their markup. ValuePilot reports missing evidence instead of fabricating precise-looking values.
