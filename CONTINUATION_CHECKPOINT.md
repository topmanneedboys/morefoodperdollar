# ValuePilot Continuation Checkpoint

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Purpose: compact durable recovery point. Newer repository/account evidence overrides this file.

## Startup order

Read before changing architecture/provider logic:

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `CONTINUATION_CHECKPOINT.md`
4. `PROVIDER_ACCOUNT_STATUS.md`
5. `RAKUTEN_PRICE_AND_ANDROID_RIGHTS_GATE.md`
6. `RAKUTEN_JAMIESON_FEED_AUDIT_2026-08-28.md`
7. `RAKUTEN_OFF_QUANTITY_COVERAGE.md`
8. `OPEN_DATA_INTEGRATION_STATUS.md`
9. `ARCHITECTURE.md`
10. `FUTURE_PRODUCT_VISION.md`

## Permanent architecture

ValuePilot is a provider-neutral shopping-intelligence platform, not an Accessibility/overlay/OCR product.

Permanent flow:

authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI

Permanent rules:

- Product != Offer.
- Sources contribute claims; they never overwrite one shared product row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflicts block Best Value.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but must not invent authoritative facts.
- Commission, EPC, payout, sponsorship and provider preference never affect ranking.
- No unauthorized scraping/private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- No Android `INTERNET` / `ACCESS_NETWORK_STATE` merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains explicitly fictional/sample evidence until a production provider path passes every machine-enforced activation/evidence gate.

## Verified provider-neutral hardening

### Price-pair semantics

Commit `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` adds structural discounted/reference price-pair assessment.

It never selects current price or grants rankability. Missing/malformed/non-positive/incomparable values fail closed; discounted > reference becomes an explicit semantic conflict.

### Dataset recency

Commit `a8e98b8ce333a612538841566972d6cab58dde88` adds dataset/file recency classification separate from per-offer freshness.

Permanent rule: **a recent dataset is not a fresh offer.**

### Production authorization gate

Commit `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` adds the fail-closed provider production activation boundary.

For the consumer mobile offer-catalog profile, every required gate must be explicitly `SATISFIED`:

- data access
- consumer display
- caching
- indexing
- mobile app
- retention/deletion policy
- offer geography
- price semantics
- dataset recency policy
- offer freshness policy

Missing, pending, denied, unknown, or incorrectly-not-required required gates block activation.

When affiliate/network links are enabled, the stronger profile also requires affiliate-link permission, installed-software/network approval, advertiser distribution approval and tracking/privacy readiness.

A feed-access approval alone can never authorize production.

Full ValuePilot Android workflow passed for the exact commit, including privacy-boundary verification.

### Offer-country gate

Commit `7606ea941f80e3dc6b2ea362bc688c7434215195` adds fail-closed provider dataset offer-country validation.

Strong bases:

- explicit offer country
- explicit dataset country
- documented dataset market

Weak/unresolved bases:

- currency only
- advertiser context only
- inferred
- unknown

Permanent rule: **CAD != Canadian offer scope.**

A strong Canada match can satisfy `OFFER_GEOGRAPHY_VALIDATED`; a strong different-country declaration denies it; weak/contextual evidence stays unknown and blocks activation.

Full ValuePilot Android workflow passed for the exact commit, including privacy-boundary verification.

### Staged production offer candidate

Commit `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` adds the provider-neutral staged-production candidate boundary.

A row cannot become `StagedProductionOfferCandidate` unless:

- the activation profile contains at least the complete consumer-mobile-catalog requirements;
- authorization belongs to the same provider + dataset namespace;
- geography belongs to the same provider + dataset and is independently re-evaluated;
- the effective authorization decision is fully authorized;
- evidence is real-world, from a known non-fixture channel, and not inferred/unknown;
- a safe validated source identity exists;
- the adapter explicitly declares the current-price field and optional reference field;
- price values are positive exact compatible money;
- any declared current<=reference rule passes;
- a real `priceObservedAtEpochMillis` is present;
- caller-supplied per-offer freshness is `FRESH` or `AGING`.

It fails closed on missing authorization, scope mismatch, weak geography, explicit geography mismatch, missing/malformed/incompatible/inverted price, missing per-offer timestamp, unknown/future/stale freshness, weak claim, or invalid-only identity.

Permanent rules:

- dataset timestamp cannot substitute for offer freshness;
- currency/context cannot substitute for country;
- authorization cannot be reused across a different provider/feed;
- a weak custom activation profile cannot bypass the base profile;
- staged candidate != canonical `Offer`;
- staged candidate has no rankability permission;
- quantity/unit value/Best Value remain separately gated.

Full ValuePilot Android workflow passed for exact commit `f58b400533bdf9a0705fb8e88680e4b56ce9d94e`, including privacy-boundary verification and release artifact upload.

### Source withdrawal already exists

`SourceIsolatedEvidenceIndex.removeNamespace(namespaceId)` already removes exactly one dataset namespace and its claims without touching other providers. Do not duplicate this mechanism. Future activation/revocation should connect to this existing source-isolation boundary.

## Jamieson / Rakuten

Rakuten technical Product Catalog access is enabled. Jamieson partnership + separate Product Feed approval + actual complete file availability are proven.

Sanitized feed facts:

- 273 product rows
- 273/273 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs and Product IDs
- GTIN present 271/273; all supplied GTINs checksum-valid
- Sale < Retail: 48
- Sale = Retail: 223
- Sale > Retail: 2
- Class ID blank all rows; opaque Attribute 1 must not be reverse-engineered

Rakuten generic schema semantics are resolved:

- Sale Price = discounted price field
- Retail Price = non-discounted/reference field

Therefore the 2 Sale > Retail rows are semantic-invalid price evidence and must fail closed; never swap or auto-correct them.

Still blocked for production:

- cache/index/search/display/mobile rights
- retention/deletion obligations
- Android installed-software/DSA approvals where applicable
- per-offer/current-price freshness
- Canadian offer geography beyond CAD/context
- broad package quantity

The Rakuten Android/feed-use/retention/DSA clarification request was already sent on 2026-08-28. Do not send a duplicate unless their response creates a new gap.

Under the machine gates, Jamieson is **NOT production-authorized today**. `OFFER_GEOGRAPHY_VALIDATED` remains unknown because CAD/context does not prove Canada scope, and the feed does not provide a trustworthy per-offer price observation timestamp for the staged candidate boundary.

## Jamieson × Open Food Facts

Historical 0/271 raw-code result is invalid coverage evidence.

Correct normalized authoritative result:

- 273 product records
- 271 valid GTINs
- 102 normalized OFF matches
- 169 unmatched
- 12 exact supplement-count candidates
- 2 structured mass/volume-only
- 0 quantity conflicts
- 88 matched but no usable quantity
- 1 search-fallback batch / 20 direct reads

Open Food Facts is supplemental only; 12/271 valid-GTIN identities are exact-count-ready under strict rules. Never relax the parser or infer counts from Rakuten text.

## Package-content source

Health Canada LNHPD does not supply GTIN-level package net count.

GS1 Canada ECCnet is the strategic next package-content candidate. The Data Recipient eligibility/rights inquiry was already sent on 2026-08-28.

Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing external status.

Key state:

- Rakuten/Jamieson: feed approved + actual catalog available; production gates still blocked
- GS1 Canada ECCnet: inquiry sent; await response
- Well.ca / Bath Depot: pending unless newer evidence
- Tru Earth / Giant Tiger: rejected; no reapply now
- CJ: TSC, Brother Canada, DAVIDsTEA pending; AOSOM older pending unless newer evidence
- Awin active; Skip CA rejected due publisher type; do not misrepresent publisher type
- impact.com Marketplace declined; no blind duplicate
- Lowvyn inquiry sent; await response

## Immediate next work

Do not resend Rakuten or GS1 inquiries.

Continue only bounded provider-neutral/offline engineering while responses are pending.

Next safe target:

**Add an activation/revocation lifecycle around accepted staged candidates so a provider dataset can become enabled only from a currently authorized snapshot and can be disabled/withdrawn by dataset namespace using the existing source-isolation mechanism.**

Do not create canonical Offers or enable ranking merely because a staged candidate exists. Keep package quantity separately attributed and unit-value gated by exact compatible quantity evidence.

Do not add Android networking, production provider credentials, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI or telemetry yet.

## Security

Operational provider credentials must never be repeated, committed, logged, screenshotted, embedded or requested. Production secrets stay outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Never weaken tests to make a change pass.
