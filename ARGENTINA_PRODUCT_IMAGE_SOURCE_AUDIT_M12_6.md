# Argentina product-image source audit — M12.6 bulk-source recovery

This is a bounded, reproducible evidence run against the actual local
Argentina SEPA release. The raw SEPA observations, the Open Food Facts API
responses, and the generated evidence JSONL remain under the ignored
`local-provider-data/` tree; none are committed to Git or Android.

## What was measured

- The tool streamed all **14,209,151** accepted observations with bounded
  memory and selected **1,000 distinct checksum-valid GTINs** by a stable
  SHA-256 rank. It reserved **100 yerba-mate identities** so the sample was
  not allowed to omit that important category.
- The existing full-catalog denominator remains **66,716 valid-GTIN
  identities**, **44,683 unique GTIN values**, and **389 yerba identities**.
- Open Food Facts metadata was requested in ten batches of 100 through the
  caller-selected documented Product Opener endpoint
  `https://world.openfoodfacts.net/api/v2/search` (the documented `.net`
  staging/mirror path). Only **112** of the 1,000
  sampled codes returned a product row; **888** were absent from that API
  response at the evaluation time.
- Every returned row failed the existing exact product boundary with an
  explicit `NAME_MISMATCH`. Therefore the sample produced **0 strict exact
  image candidates**, **0 rights-qualified image identities**, and **0
  display-eligible identities**. The 100 sampled yerba identities also
  produced **0** exact candidates.

The documented worldwide Open Food Facts image bucket was also evaluated as a
possible bulk path. Its `data/data_keys.gz` object is an image-key inventory
that is synchronized monthly; it does not itself carry the exact product,
brand, package, or commercial-rights evidence required by ValuePilot. The
inventory therefore cannot be counted as product coverage, and no image bytes
were downloaded. The same exact-identity and rights gates would still be
required after joining it to a complete product export.

The `76,196` distinct-GTIN figure in the machine-readable report is a bounded
diagnostic count from the accepted-observation stream. It is not a replacement
for the independently generated catalog denominator (`66,716` valid-GTIN
identities) and is not a claim that every observed provider row is a distinct
consumer catalog identity.

The machine-readable result is
[`ARGENTINA_PRODUCT_IMAGE_SOURCE_AUDIT_M12_6.json`](ARGENTINA_PRODUCT_IMAGE_SOURCE_AUDIT_M12_6.json).

## Why no image was integrated

The existing gate requires a checksum-valid GTIN plus exact normalized
product name, brand (when supplied), and explicit package quantity. A same
GTIN with a generic or different variant name is not safe to show as the
requested product. The source-rights policy records Open Food Facts' CC
BY-SA image boundary, but it deliberately does not grant commercial display:
attribution/share-alike implementation, image-byte handling, and third-party
packaging rights remain separate gates. No image bytes were downloaded.

The bounded spot check included returned yerba-mate records. Several looked
like plausible product-family matches, but their names, brands, or package
variants were incomplete or different. They were correctly rejected rather
than turned into false pictures.

## Decision

This source path is useful as a future evidence adapter, but this measured
sample is **not productive enough to qualify for Android display** and does
not support a 90% coverage claim. The existing neutral “No verified product
picture available” state remains the correct consumer behavior. A future
bulk export or an explicitly authorized image source may be evaluated with
the same tool; it must pass the same exact-identity and rights gates before
any bytes or UI integration are considered.

Open Food Facts documents the bulk-code API, image roles, and the worldwide
S3 image-key inventory in its
[API introduction](https://openfoodfacts.github.io/openfoodfacts-server/api/),
[image-download guide](https://openfoodfacts.github.io/openfoodfacts-server/api/how-to-download-images/),
[AWS image dataset guide](https://openfoodfacts.github.io/openfoodfacts-server/api/aws-images-dataset/),
and [licence guidance](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/).
