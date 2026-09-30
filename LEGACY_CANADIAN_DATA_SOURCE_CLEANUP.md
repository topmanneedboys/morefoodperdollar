# Legacy Canadian data-source cleanup

This checkpoint removes the obsolete Canadian catalog and location-directory
rails before the next unified UI migration. It does not change the
provider-neutral shopping core, generic CAD arithmetic, private history, or the
Argentina Search identity rail.

## Removed from the shipped app

- The signed offline product catalog assets (`offline_catalog/`) were removed
  from the APK source tree. The previous Git tree contained 10,067,567 bytes
  (about 9.60 MiB), including the 10,060,353-byte Canadian Open Food Facts
  source JSONL and its GTA/Metro Vancouver manifests.
- The signed store-directory assets (`store_directory/`) were removed from the
  APK source tree. The previous Git tree contained 3,111,204 bytes (about
  2.97 MiB), including the 3,107,184-byte OpenStreetMap places source JSONL.
- Home no longer searches the bundled Canadian catalog or offers exact-product
  selection from it. Unknown Home items remain unknown and can be removed; the
  Argentina identity rail remains user-triggered and source-attributed.
- Good Price and Compare barcode capture now fills the entered identifier for
  manual review. It does not silently resolve a deleted Canadian catalog row.
- Trip planning accepts a private destination store name and manual route
  assumptions; no bundled Canadian directory is used.

## Removed tooling and source-specific rails

The obsolete offline-catalog/store-directory builders, verifiers, promotion
tools, asset tests, Open Prices Canada measurement tools/workflows, and the
Jamieson/Rakuten Android adapter and qualification tooling were removed. CI
path filters were updated so no workflow points at those deleted files.

## Intentionally retained

- `Money` and quantity primitives continue to support explicit CAD and other
  currencies exactly; no currency capability was removed.
- Argentina SEPA Search and its bounded backend/tooling remain the active
  identity rail. It still does not establish price, stock, availability, or
  ranking authority.
- Open Food Facts image-rights policy and barcode normalization remain only
  where the active Argentina image-audit milestone still needs them. They are
  not a bundled Canadian catalog and are not a current-offer source.
- Provider-neutral saved/history compatibility readers remain so existing
  private records can be displayed safely. They do not reactivate a source,
  download data, or create an offer.
- Generic merchant-feed qualification remains available for a future
  rights-qualified provider; no Canadian feed is active.

## Historical records and legal context

Older qualification reports and continuation checkpoints mentioning Canada,
Open Food Facts, Open Prices, OpenStreetMap, CJ, or Rakuten remain in the
repository as historical engineering/provenance records. They are not shipped
assets, runtime adapters, or current availability claims. Their source-specific
licence and attribution facts are not expanded into permission for a new feed.
The cleanup is an engineering boundary, not a legal opinion.

## Regression boundary

`LegacyCanadianSourceFootprintTest` verifies that the obsolete asset roots and
runtime source markers stay absent while exact CAD support remains available.
