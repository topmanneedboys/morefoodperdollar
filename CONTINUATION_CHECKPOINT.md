# ValuePilot Continuation Checkpoint

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Purpose: provide a compact, durable recovery point for a future ChatGPT/engineer when conversational context is truncated or unavailable. This file is a navigation/checkpoint document, not a replacement for repository evidence.

## Startup order for a new engineer

Read these before changing architecture or provider logic:

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `PROVIDER_ACCOUNT_STATUS.md`
4. `RAKUTEN_JAMIESON_VALIDATION.md`
5. `ARCHITECTURE.md`
6. `FUTURE_PRODUCT_VISION.md`
7. provider-specific validation files relevant to the current task

Repository evidence newer than this checkpoint overrides this checkpoint.

## Permanent product direction

ValuePilot is a provider-neutral shopping-intelligence platform. It is **not** founded on Android Accessibility, persistent overlays, OCR, scraping, one affiliate network, or one presentation method.

Permanent conceptual flow:

authorized evidence sources -> provider adapters -> provenance-preserving evidence -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI

Permanent rules:

- Product != Offer
- source claims stay provenance-separated
- stronger evidence may defeat weaker conflicting evidence; unresolved equal-strength conflicts block Best Value
- deterministic money/quantity/currency logic
- AI may classify/explain but must not invent authoritative price, quantity, ingredients, nutrition, availability or delivered totals
- ranking is independent of commission, EPC, payout, sponsorship or provider preference
- no unauthorized scraping or reverse engineering
- no production network adapter merely because technical access exists

## Current milestone

Milestone 5D — Authorized Real Shopping Data Provider Selection / validation.

The Android/shared-core foundation already has deterministic ShoppingEvidence, freshness/trust disposition, source isolation, GTIN validation, conflict resolution and evidence-backed unit-value gating. Built-in Android search evidence remains fictional/sample until deliberately replaced by production-authorized evidence.

Do not add Android `INTERNET` or `ACCESS_NETWORK_STATE` merely to accelerate provider experimentation.

## Latest major external milestone: Jamieson / Rakuten

Rakuten Product Catalog technical access is enabled.

Jamieson Vitamins advertiser partnership is active.

On 2026-08-28 Rakuten Customer Support explicitly confirmed:

- ValuePilot is approved for the Jamieson advertiser Product Feed.
- The Jamieson feed is already present in the authorized Product Catalog SFTP account.

The complete compressed TXT catalog was downloaded and inspected offline. The proprietary catalog file is **not** committed to the repository.

Empirical complete-feed checkpoint:

- 273 product records; trailer count matches
- all 273 rows have the documented 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs
- 273 unique source Product IDs
- UPC/GTIN present on 271/273; all 271 supplied values checksum-valid
- product URL valid on 273/273
- image URL valid on 273/273
- manufacturer present as Jamieson on all 273
- description present on 272/273
- Class ID blank on all 273 rows
- Sale Price < Retail Price on 48 rows
- Sale Price = Retail Price on 223 rows
- Sale Price > Retail Price on 2 rows

Important interpretation:

- advertiser feed approval and actual file availability are now proven
- production caching/indexing/display/mobile rights are **not** yet proven
- the two inverted Sale Price relationships prove price-field semantics need deterministic guards
- package quantity is not established by the generic feed schema and all Jamieson Class IDs are blank
- therefore: **273 structural offer candidates, 0 authoritative unit-value candidates until quantity/count is established by a validated source**
- do not guess package size/count from title, description, image filename, SKU, price or neighboring variants
- do not use supplement marketing claims as ValuePilot medical/efficacy/safety ranking evidence

See `RAKUTEN_JAMIESON_VALIDATION.md` for the full provider-specific record.

## Current provider/account state

Use `PROVIDER_ACCOUNT_STATUS.md` as the fast-changing authority.

As of this checkpoint:

- Rakuten/Jamieson: advertiser Product Feed approved and actual complete catalog available
- Well.ca: pending unless newer evidence
- Bath Depot: pending unless newer evidence
- Tru Earth: rejected; do not reapply now
- Giant Tiger: rejected; do not reapply now
- CJ: TSC, Brother Canada and DAVIDsTEA pending; AOSOM older pending unless newer evidence
- Awin: active; Skip CA rejected due publisher type; do not misrepresent publisher type
- impact.com Marketplace application declined; no duplicate/blind reapply
- Lowvyn: rights/technical inquiry sent; wait for written response before integration

For Lowvyn, if the initial request is approved, continue in the same email thread and ask about partner-level/full-catalog access, efficient sync/bulk mechanisms, production rate limits, caching/storage, consumer display, attribution and affiliate/commercial routing. Do not make Lowvyn the only provider dependency.

## Rakuten qualifier hardening completed in this session

The first real Jamieson feed exposed a concrete issue: positive `Sale Price` values are not always below `Retail Price`.

`tools/qualify_rakuten_product_catalog.py` has now been hardened so that it:

- preserves Sale Price and Retail Price as separate source fields/semantics
- reports `sale < retail`, `sale == retail`, and `sale > retail` separately
- does not infer a discount from the field names alone
- uses Retail Price first only for structural numeric qualification coverage, with Sale Price only as a fallback if Retail Price is not positive
- reports short/long-description coverage
- reports manufacturer-name coverage
- reports missing UPCs explicitly
- reports blank/present Class IDs explicitly
- emits a dedicated `price_semantics_gate`
- still reports `production_authorized = false`

Synthetic regression coverage was also added in `tools/tests/test_qualify_rakuten_product_catalog.py` for below/equal/above price relationships and the non-production price-semantics gate.

**Important verification note:** these source/test changes were committed through the connected GitHub repository, but the focused Python test suite was not executed in this chat environment because the repository runtime was not mounted. A future engineer must run the focused `tools/tests` suite before calling this code change fully verified.

Do not commit the proprietary Jamieson feed as a fixture. Use synthetic test rows.

## Next engineering sequence

Highest-value sequence now:

1. Run the focused Rakuten qualifier Python tests and fix any regression before claiming verification.
2. Run the hardened qualifier against the local authorized Jamieson `.txt.gz` feed and preserve only a sanitized qualification report, not the proprietary feed itself.
3. Define a provider-neutral offline import mapping that preserves both supplied price fields, source Product ID, SKU, GTIN, availability, product/image URLs and provenance without deciding retail-vs-sale semantics prematurely.
4. Establish package quantity/count from a validated source joined by strong identity, preferably checksum-valid GTIN where possible; preserve that source's separate provenance.
5. Only after rights are clear, decide whether/how Jamieson evidence may enter production search/display/ranking.
6. Do not automate SFTP credentials or add Android networking before those gates are deliberately cleared.

## Security checkpoint

Operational provider credentials have previously appeared in conversational material. Never repeat, commit, log, screenshot, embed or ask for them again. Repository documentation intentionally contains no file-transfer username/password or private account identifiers.

For future automation, secrets must live outside source control in an appropriate local/secret-management mechanism and should be rotated before production automation if needed.

## Tests / normal engineering discipline

For Android/shared-core changes, preserve the repo's standard validation sequence:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For Python feed-qualification changes, run the focused `tools/tests` suite relevant to the changed qualifier before claiming success.

Do not weaken tests to make a change pass.
