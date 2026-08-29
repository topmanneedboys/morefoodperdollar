# ValuePilot Practical Shopping Saved Display Metadata Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified confirmation/source-bound human-label adapter for Saved exact preferences. Newer repository evidence overrides this file.

## Latest verified code head

`e420713de00b614ca58f032b4dbba617767678a1` — `Bind saved display names to confirmed evidence`

GitHub Actions workflow run **146** (`33272489075`) completed successfully.

All normal repository gates passed: browser checks/package, shared-core/app JVM tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload and post-job cleanup.

## Verified label sources

`PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter` is network-free and non-authoritative.

It can emit presentation metadata from only three bases:

- explicit user-provided label for an already `USER_CONFIRMED_EXACT_PRODUCT` candidate;
- explicit user-provided label for an already `USER_CONFIRMED_EXACT_STORE` candidate;
- Open Food Facts `productName` after re-running the verified OFF product-identity adapter and requiring the same confirmed production product key/provenance;
- OpenStreetMap place name after re-running the verified OSM store-suggestion adapter and requiring the same confirmed complete merchant/location/channel scope/provenance.

A source name does not create or change product/store identity. It grants no price, availability, package quantity, travel, routing, ranking, freshness or provider-production authority.

Open Food Facts package quantity may remain unavailable; that does not prevent a same-identity product name from being presentation metadata.

## Fail-closed behavior

The adapter fails for:

- product/store candidate not explicitly user-confirmed;
- source provenance mismatch;
- different exact product identity;
- different exact OSM-derived store scope;
- missing, blank, control-character or oversized display name.

The source identity adapters are re-run instead of duplicating their identity rules inside the display adapter.

The already verified Saved projector remains an independent final leakage guard. A source may legitimately contain a technically shaped `productName`, but raw GTIN/provider/merchant/location/channel identifiers are still rejected before consumer rendering.

## Important next hardening before persistence

The current display entry is created against confirmed evidence, but a durable label must not be keyed only by the logical `ShoppingItemKey` / `ShoppingStoreKey`.

If the same logical key is later re-confirmed to a different exact product or store scope, an old label must become stale rather than relabel the new choice.

Before persisting display metadata, bind every product label to the exact `ProductionProductEvidenceKey` it described and every store label to the exact `PracticalShoppingStoreIdentityScope` it described. Revalidate those bindings against the current saved-preference state before creating projector metadata.

Equivalent canonical GTIN representations may remain the same exact product binding; genuinely different product identities or store scopes must invalidate the old label.

## Still intentionally absent

No Saved physical renderer, MainActivity Saved wiring, durable label codec/store, live OFF/OSM client, Android networking permission, current-price integration, account, telemetry, remote AI or server was introduced.

No user/device action is required for this checkpoint.
