# ValuePilot v101 verification

Date: 2026-08-08

## Browser suite

The release check currently passes 27 tests covering:

- mass, volume, multipack, dozen, currency, decimal, and thousands normalization
- BOGO, partial BOGO, buy-X-get-Y, bundles, displayed percent-off, and minimum-spend budgets
- calorie, pizza-area, unit, portion, meat, and Smart rankings
- current-price selection, safe name extraction, Unicode dedupe, and non-food/pork filters
- deterministic local-model food, non-food, and meat classification
- Shadow DOM mounting, scanning, storage, budget, and dietary-filter integration

Mozilla `web-ext lint --warnings-as-errors` reports 0 errors, 0 notices, and 0 warnings for the packaged Firefox directory.

## Release workflow gates

Before files are distributed, CI must also pass:

- generated-model drift check
- Android JVM tests for engine/model parity
- Android lint
- debug APK assembly against API 36
- final APK permission inspection proving `android.permission.INTERNET` is absent
- target-specific Chromium and Firefox packaging
- SHA-256 generation for all three downloads

## Honest limits

Automated tests cannot guarantee every future third-party app/site layout. Real apps may expose incomplete accessibility data, secure windows may reject screenshots, and websites may render text outside the DOM. The app uses layered extraction and surfaces uncertainty, but compatibility still needs real-device/site testing as those services change.
