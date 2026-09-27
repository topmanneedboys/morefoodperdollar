# Argentina product-image discovery audit — M12.6 continuation

## Why the original 21 is not a coverage ceiling

`ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.json` is a valid historical audit, not
the output of a complete image discovery engine. Its denominator is 66,806
catalog identities, of which 66,716 have valid GTIN checksums; the `yerba`
name/brand subset is 389 identities. The old implementation selected only
`image_front_url` (falling back to `image_url`) from the tabular export and
therefore measured **strict identities with one front-image URL**, not every
Open Food Facts image variant. Its 21 candidates and zero display-eligible
records remain the correct result for that old policy, but must not be read as
“only 21 photographs exist.”

The old audit does **not** filter Open Food Facts rows to Argentina, does **not**
require one language, and already applies the documented Open Food Facts
leading-zero GTIN normalization. The actual discovery shortfall was the
single-field/front-image selection and the absence of explicit per-image rights
evidence in the bulk export.

## Documented source findings

The official Open Food Facts documentation says that `selected_images` exposes
front, ingredients, nutrition and packaging images, with language-specific
variants and 100/200/400/full resolutions. It also documents the worldwide AWS
images dataset, which is synchronized monthly and can lag recent uploads:

- [How to download product images](https://openfoodfacts.github.io/openfoodfacts-server/api/how-to-download-images/)
- [Open Food Facts image schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_images/)
- [AWS images dataset](https://openfoodfacts.github.io/openfoodfacts-server/api/aws-images-dataset/)
- [Barcode normalization](https://openfoodfacts.github.io/openfoodfacts-server/api/ref-barcode-normalization/)
- [Licence guidance](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/)

The licence guidance says product photos are CC BY-SA by default but can contain
third-party elements; attribution and any share-alike obligations must remain
with the reuse. A URL alone is not independent commercial-rights evidence.

## Inexpensive sample assessment

On 2026-09-27, five Argentina-looking GTINs from the existing Search fixture
were queried through the documented product API with only metadata/image fields
(no image bytes): `7790742192103`, `7790742192202`, `7790060023684`,
`7790070012050`, and `7790070104304`.

- Image URL candidates observed: **5 products / 5 selected front URLs**.
- Languages observed in the selected URLs: Spanish and English; country tags
  were mixed, confirming that a country tag is not a safe identity gate.
- Exact SEPA identity matches: **0 accepted** in this sample. The local
  full-export join was not remeasured because the large export is not present
  in this checkout; this is not evidence that the catalog has no exact matches.
- Independently verified reuse rights: **0**. The sampled API responses did not
  expose a per-image licence assertion that can be carried into a commercial
  ValuePilot manifest.

This sample is a discovery diagnostic only. It is not a catalog coverage
estimate and does not authorize downloading, caching or displaying these URLs.

## Bounded rights-aware engine

`tools/exact_product_image_manifest.py` is the next safe implementation step.
It streams the OFF export, preserves the existing exact GTIN/name/brand/
explicit-quantity boundary, examines all documented role/variant URL fields,
accepts both documented OFF image hosts, records language/role/resolution, and
emits JSONL evidence only when an explicit compatible open licence is present.
Accepted rows retain the source row, canonical GTIN, URL, licence evidence,
attribution/share-alike obligations and a permanent “no price or availability”
boundary. Invalid hosts, missing rights, malformed GTINs and semantic mismatches
are counted with explicit rejection reasons. No image bytes are downloaded and
no Android asset or networking path is changed.

The engine is proven on a tiny deterministic fixture; it has **not** been run
against the national export in this checkout. Verified displayable photo
coverage therefore remains **unmeasured for the broadened policy**, and the
90% ambition remains open rather than claimed. The next evidence-bearing step
is a bounded sample from the real local export, manual review of accepted and
near-match/yerba rejections, and an attribution/rights decision before any
larger batch or Android integration.
