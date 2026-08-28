# ValuePilot Continuation Checkpoint

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Purpose: compact durable recovery point for a future ChatGPT/engineer if conversational context is truncated. This is a navigation/checkpoint document; newer repository evidence overrides it.

## Startup order

Read before changing architecture or provider logic:

1. `AGENTS.md`
2. `CONTINUATION_CHECKPOINT.md`
3. `CURRENT_STATE.md`
4. `PROVIDER_ACCOUNT_STATUS.md`
5. `RAKUTEN_JAMIESON_VALIDATION.md`
6. `RAKUTEN_JAMIESON_FEED_AUDIT_2026-08-28.md`
7. `OPEN_DATA_INTEGRATION_STATUS.md`
8. `ARCHITECTURE.md`
9. `FUTURE_PRODUCT_VISION.md`
10. provider-specific validation files relevant to the task

## Permanent product direction

ValuePilot is a provider-neutral shopping-intelligence platform. It is not founded on Android Accessibility, persistent overlays, OCR, scraping, one affiliate network, one data vendor, or one presentation method.

Permanent flow:

authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation and normalization -> Product identity + Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI

Permanent rules:

- Product != Offer.
- Sources contribute claims; they do not overwrite one shared product row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflicts block Best Value.
- Money, quantity and currency logic is exact and deterministic.
- AI may classify/explain but must not invent authoritative price, quantity, ingredients, nutrition, availability or delivered totals.
- Commission, EPC, payout, sponsorship and provider preference never influence ranking.
- No unauthorized scraping or reverse engineering.
- Technical/feed access never equals production authorization.
- Do not add Android `INTERNET` or `ACCESS_NETWORK_STATE` merely for provider experimentation.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

The shared deterministic foundation already includes ShoppingEvidence, evidence freshness/trust disposition, source-isolated namespaces, checksum-aware GTIN validation, conflict resolution, evidence-backed unit-value gating and a provider-neutral staged offer-import contract.

Built-in Android Search data remains explicitly fictional/sample evidence until a deliberately production-authorized provider path exists.

## Rakuten / Jamieson milestone

Rakuten Product Catalog technical access is enabled and Jamieson Vitamins is an active advertiser partner.

On 2026-08-28 Rakuten Customer Support explicitly confirmed that ValuePilot is approved for the Jamieson Product Feed and that the feed is present in the authorized Product Catalog SFTP account.

The complete compressed TXT feed was downloaded and audited offline. The proprietary catalog file is not committed.

Sanitized empirical checkpoint:

- 273 product rows and matching trailer count
- all 273 rows have the documented 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs and 273 unique source Product IDs
- UPC/GTIN present on 271/273; all 271 supplied values checksum-valid
- product and image URL syntax valid on 273/273
- manufacturer present as Jamieson on 273/273
- description present on 272/273
- Class ID blank on all 273
- Sale Price < Retail Price: 48
- Sale Price = Retail Price: 223
- Sale Price > Retail Price: 2

Interpretation:

- advertiser feed approval and actual file availability are proven;
- caching/persistence/indexing/display/mobile production rights are not yet proven;
- Sale Price cannot be blindly treated as a discount/current price;
- package quantity/count is not established by the Rakuten feed;
- current structural offer candidates = 273;
- current authoritative unit-value candidates = 0 until quantity is established by validated separately attributed evidence;
- never guess count/size from title, description, image filename, SKU, price or nearby variants;
- supplement marketing claims are not medical/efficacy/safety ranking evidence.

See `RAKUTEN_JAMIESON_VALIDATION.md` and `RAKUTEN_JAMIESON_FEED_AUDIT_2026-08-28.md`.

## Rakuten qualifier status

`tools/qualify_rakuten_product_catalog.py` is an offline research/validation tool, not a production adapter.

After the real Jamieson audit it was hardened to preserve Retail Price and Sale Price separately, report below/equal/above relationships, avoid inferring discounts, report identity/description/manufacturer/Class-ID coverage, keep file-generation time separate from product freshness, and retain `production_authorized = false`.

Synthetic regression tests cover the discovered price relationships. The `Test merchant feed qualification` GitHub Actions workflow passed after those changes.

## Provider-neutral staged offer import — completed and verified

`android/shared-core/src/main/kotlin/com/valuepilot/core/ProviderOfferImport.kt` now defines the provider-neutral boundary between parsed provider rows and canonical production offers.

It deliberately does **not** create an `Offer` or choose a current price.

Key invariants:

- provider item ID, SKU and supplied GTIN remain source-scoped;
- malformed GTIN is preserved for audit but is never promoted as validated cross-source identity;
- checksum-valid GTIN may be promoted into the existing `SourceProductIdentity` contract;
- source price fields remain distinct raw fields with optional parsed `Money`;
- price semantics remain `UNRESOLVED_SOURCE_FIELDS` until an explicit provider semantic resolver exists;
- malformed source price text may remain auditable without becoming money;
- dataset/file generation time remains separate from per-offer observation time;
- platform-neutral boundary: no Android, UI, filesystem, network, retailer-specific logic or hidden clock.

`ProviderOfferImportTest.kt` covers price-field preservation, inverted-price non-selection, valid/invalid GTIN promotion, malformed-price auditability, duplicate source-field rejection and freshness separation.

GitHub Actions build run for commit `4b423af63a64abd403ef01baf3821e65e112bd8b` completed successfully. Browser checks, shared-core/app tests, Android lint/assemble and the APK privacy-boundary check all passed; no Android network permission was introduced.

## Open Food Facts / quantity path

`OpenFoodFactsImportedMetadata.kt` already implements a strict network-free metadata mapping keyed by checksum-valid GTIN. Today it accepts structured whole-product quantity only when Open Food Facts supplies a positive normalized `product_quantity` with unit `g` or `ml`; simple raw mass/volume strings are only cross-checked and disagreements fail closed.

It emits PACKAGE_QUANTITY with `SOURCE_ASSERTED_METADATA` authority and cannot emit retailer price, stock, promotion or merchant identity.

Previous real Open Prices × Open Food Facts measurement found a useful GTIN quantity join for mass/volume products, but this does not prove count coverage for Jamieson tablets/capsules/gummies. Do not broaden to title/description guessing.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` as the fast-changing authority. At this checkpoint:

- Rakuten/Jamieson: advertiser feed approved and actual complete catalog available
- Well.ca: pending unless newer evidence
- Bath Depot: pending unless newer evidence
- Tru Earth: rejected; do not reapply now
- Giant Tiger: rejected; do not reapply now
- CJ: TSC, Brother Canada and DAVIDsTEA pending; AOSOM older pending unless newer evidence
- Awin active; Skip CA rejected due publisher type; do not misrepresent publisher type
- impact.com Marketplace application declined; no duplicate/blind reapply
- Lowvyn rights/technical inquiry sent; wait for written response before integration

If Lowvyn approves the initial request, reply in the same thread about partner/full-catalog access, efficient synchronization/bulk mechanisms, production rate limits, caching/storage, consumer display, attribution and affiliate/commercial routing. Do not make Lowvyn the only provider dependency.

## Next engineering sequence

1. Measure whether a bounded public metadata source can supply authoritative package **count** for the Jamieson GTINs without committing the proprietary feed or GTIN list.
2. Inspect/extend quantity modeling only if the source has a documented, deterministic count field; use synthetic tests and fail closed on ambiguity.
3. Keep quantity provenance separate from Jamieson merchant-price provenance and join only through strong stable identity, preferably checksum-valid GTIN.
4. Do not create canonical production Offers while Retail/Sale semantics and data-use rights remain unresolved.
5. Do not automate SFTP credentials or add Android networking yet.
6. Continue waiting on screened provider decisions and Lowvyn written response; do not mass-apply elsewhere.

## Security

Operational provider credentials have appeared in conversational material. Never repeat, commit, log, screenshot, embed, or ask for them again. Repository documentation intentionally contains no file-transfer credentials or private account identifiers.

Future production automation must keep secrets outside source control and use an appropriate local/secret-management boundary.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For Python feed-qualification changes, run the focused `tools/tests` suite relevant to the changed qualifier. Never weaken tests to make a change pass.
