# ValuePilot final consumer redesign audit

Date: 2026-10-04
Source branch: `work/valuepilot-confirmed-purchases`
Source checkpoint: `cf95e1f588d230d2157ed9438ff983f38beddc04`
Candidate prepared by this audit: versionCode `10125`, versionName `101.1.24`

## Scope

This was a cohesion and consumer-copy pass over the existing Home, Search,
Basket, You, Map, Trip, Compare, Good Price, Settings, About and Licences
surfaces. It does not add a new product capability, backend, retailer feed,
image source, routing service, account, cloud sync or Android networking path.
The shared planner, exact-money rules, private-data boundaries and demo/live
separation were left intact.

## Consumer journeys reviewed

The following paths were read end-to-end through the shell routes, reducers,
renderers, callbacks, and state-restoration code:

| Journey | Static review | Result |
| --- | --- | --- |
| Home → Search → product → Add → Basket | PASS | Search keeps its query/session and Add is the single product-primary action. |
| Home → Basket → collect → purchase → You → Bought → Buy again | PASS | Collection and purchase remain private local actions; Bought is a secondary You destination. |
| Search → product More → Watch → You → Watching | PASS | Watch is progressive disclosure and uses the same exact identity key across surfaces. |
| Search → Nearby → store → Trip cost → Back → Map → Back → Search | PASS (static) | Map and Trip are secondary routes with explicit parent restoration. Physical route timing remains to be checked. |
| Product → Compare; product/history → Good Price | PASS | Compare and Good Price remain separate, user-entered evidence flows. |
| You → Settings → Data sources → About → Licences → Back | PASS | Settings is reached from You and source/legal detail is kept out of the primary shopping surfaces. |

## Cohesion changes made

- Replaced Home's prototype planning labels with shopper language: “Your shop
  plan is ready,” “Ready to shop,” and simple item counts.
- Replaced the Basket's emoji store marker with the same restrained text/card
  treatment used by Map and Trip (`Store:` / `Stores:`).
- Replaced Search's provider-heavy headline, badge, dialog and loading copy
  with “Product Search,” “Public Argentina catalog,” and short identity-only
  explanations. The copy still states that price, quantity, stock and
  availability are unknown where applicable.
- Made Compare the visible destination name instead of the older “Scan &
  compare” prototype label across Search, Home, sharing handoffs and history.
- Replaced “Quick answers” with “Shortcuts” and “remembered/private comparison
  history” labels with saved-price language in normal Home copy.
- Kept source/provider, release, evidence, freshness and licensing wording in
  Data sources, Licences, Technical details, contracts, diagnostics and tests
  where it is necessary for honesty and auditability.
- Added `FinalConsumerCopyAuditTest` to prevent the retired primary-flow labels
  from returning and to pin the Search/Basket vocabulary.

## Visual language and hierarchy

The existing shared design tokens remain the authority: warm neutral
background, surface/subtle/active cards, 18dp product cards, 48dp minimum
touch targets, restrained elevation and a 180ms disclosure motion. Home,
Search, Basket, You, Map, Trip, Compare, Good Price and Settings use the same
programmatic button and card primitives. Filled buttons remain for the current
screen's primary action; outlined buttons are used for secondary actions and
progressive disclosure. No fifth primary tab was introduced.

The first viewport on each primary surface still answers its local question:
what to enter (Home/Search), what is in the Basket, what is saved (You), where
the store is (Map), whether an extra stop is worthwhile (Trip), whether a
comparison is possible (Compare), or what the price context means (Good Price).

## Navigation, Back and state

- Primary navigation remains Home, Search, Basket and You.
- The shell's single secondary-route Back control is visible only when the
  route can return to a parent; Android system Back uses the same reducer path.
- Search results are intentionally revalidated after activity recreation while
  the query and scroll context are retained.
- Settings, About, Licences, Map and Trip preserve their parent route and do
  not create duplicate Back controls.
- Saved, Watching, Bought, Compare and Good Price remain private/device-only
  state boundaries; no visual copy upgrades unknown facts into live offers.

## Motion, accessibility and performance review

Existing reveal/disclosure motion is short and state-driven; no decorative or
bouncy animation was added. Buttons use shared touch-target sizing and content
descriptions, product cards expose a single primary action plus progressive
context, and important totals use the existing larger type levels. The code
paths remain bounded for the existing 100–500 product UI budget.

The following still require a physical device judgment rather than a source
claim: TalkBack traversal in every route, large-font clipping, rotation while a
secondary route is open, frame pacing on a real Motorola, and exact thumb
reach/pressed-state feel.

## Deliberate limitations

- This pass does not repair the stale-release/backend qualification state.
- Search's public catalog remains identity-only; it is not a current-price or
  inventory guarantee.
- Google Images remains explicitly unverified and is not product proof.
- No new store directory, flyer ingestion, image coverage or routing work was
  started.
- No signed artifact was produced, no Play upload was attempted, and no device
  data was changed.

## Verification target

The release candidate must pass shared-core tests, Android JVM tests,
redesigned regression tests, debug/release lint, debug/release builds, release
bundle build, privacy/manifest checks and `git diff --check`. The resulting
release AAB is intentionally unsigned and suitable only for local inspection;
the next physical-validation step must use the established protected signing
process after the source checkpoint is accepted.

## Local verification result

Completed on this checkpoint:

- shared-core: **411 tests, 0 failures**;
- Android JVM: **1,716 tests, 0 failures** (including the new consumer-copy
  audit and the updated Home/Basket/history regressions);
- `lintDebug`: **PASS**;
- `lintRelease`: **PASS**;
- `assembleDebug`: **PASS**;
- `assembleRelease`: **PASS**;
- `bundleRelease`: **PASS**;
- `git diff --check`: **PASS**.

The unsigned release artifacts are:

- APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk`
  (package `com.valuepilot.app`, versionCode `10125`, versionName `101.1.24`,
  SHA-256 `4A9755F6D85975B2C3CE0FC9288BAF40D3B01F915D14198D5466F1497C26D290`);
- AAB: `android/app/build/outputs/bundle/release/app-release.aab`
  (SHA-256 `F4F5FCE1C841206C6F25D8997FE80E7B57DE53E6E20FC837312843D61B42393D`).

APK manifest inspection found only the existing `INTERNET`, `CAMERA`,
`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` and app-owned receiver
permission; `ACCESS_NETWORK_STATE` is not packaged. The AAB contains no
signing certificate entries, so it has not been signed or made installable as
a Play update.
