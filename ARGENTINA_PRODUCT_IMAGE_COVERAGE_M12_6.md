# Argentina product-picture coverage — M12.6

This is a deterministic, offline audit of the 2026-09-25 Argentina identity
vocabulary against a locally downloaded Open Food Facts bulk export. The raw
exports remain outside Git and no image bytes were downloaded or added to the
Android application.

The audit joins only validated GTINs. A candidate must also have an exact
normalized product name, an exact known brand when the Argentina record has
one, and an explicit package quantity that agrees after only deterministic
unit conversion. Unknown or ambiguous quantity is rejected. Provider
identities sharing a GTIN are counted separately, while unique GTIN coverage
is reported separately.

Measured results:

- 66,806 catalog records were inspected; 66,716 had valid GTIN checksums,
  representing 44,683 unique GTIN values. The remaining 90 records were
  invalid, and 22,033 valid records repeated a GTIN across provider identities.
- 15,592 catalog identities had an exact GTIN match in 6,799 Open Food Facts
  rows. Strict name/brand/quantity agreement reduced this to 21 identities.
- 21 strict identities (0.031477% of valid GTIN catalog records) had an
  official Open Food Facts image URL candidate. The export supplied no
  per-image licence field, so **0 images are display-eligible** under the
  current rights gate. This is not a 90% qualification.
- The 389 catalog identities whose name/brand contains `yerba` had **0 strict
  image candidates (0%)**.

The source policy records Open Food Facts' documented CC BY-SA image boundary,
but keeps per-image and third-party-rights review separate. This audit therefore
does not authorize republishing, ranking, price claims, or Android networking.
The next safe picture milestone is a rights-qualified source/manifest and a
variant-aware picture field; until then the app must show its existing neutral
no-picture state.

The machine-readable evidence is in
[`ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.json`](ARGENTINA_PRODUCT_IMAGE_COVERAGE_M12_6.json).
