# Argentina product identity coverage qualification

## Verified source

This report was produced with read-only access to the existing
`valuepilot-argentina` project.  No production pointer, credential, backend
service, or Android asset was changed.

- Active release: `argentina-sepa-2026-10-08-153e9d9f7ab4b87e-routing-v1-search-v2`
- Provider source: `ARGENTINA_SEPA_PRECIOS_CLAROS`
- Source release date: `2026-10-08`
- Source SHA-256:
  `153e9d9f7ab4b87e72c16aebebc69c6f9f90fca681cd1075d625244e2e31bc2c`
- Source size: `322,801,717` bytes
- Official attribution: Precios Claros - Base SEPA; datos.gob.ar

The full source archive contains 15 non-empty retailer packages.  The scan
read `productos.csv` from every package and verified the outer and nested ZIP
checksums before deriving identity records.

## Coverage result

| Measure | Count |
| --- | ---: |
| Product rows scanned | 14,247,011 |
| Malformed rows quarantined from identity analysis | 30 |
| Rows missing a commerce, product id, or name | 0 |
| Retailer-scoped product identities (`commerce + id_producto`) | 134,337 |
| Distinct provider product ids across retailers | 78,865 |
| Distinct canonical names | 123,692 |
| Identities with a reviewed family classification | 23,333 (17.37%) |
| Identities still unclassified | 111,004 (82.63%) |

The 78,865 provider-id count is the answer to the “70,000-plus products”
question.  The larger 134,337 count is expected because the same provider id
can be represented separately for different commerce/retailer identities in
the source.

Current classification counts:

- Direct reviewed family match: `20,806`
- Derived product form: `1,144`
- Multiple reviewed families: `1,383`
- Unclassified: `111,004`

The source had no name, brand, or quantity conflicts for a retailer-scoped
identity.  Seven identities had more than one GTIN value and remain visible as
conflicts rather than being silently resolved.

## Reviewed clarification evidence

The existing clarification layer is evidence-driven.  It only presents a
choice when the bounded result set contains at least two observed forms.

- **Rice:** 1,147 identities; 1,008 direct and 139 derived.  Observed forms
  include 251 grain, 270 dessert, 9 flour, and 627 unspecified identities.
- **Carrot:** 98 identities; 89 direct and 9 derived.  Observed forms include
  8 vegetable, 4 drink, 2 dessert, 3 cosmetics, and 81 unspecified identities.
- **Yerba mate:** 1,106 direct identities, with observed loose-leaf, tea-bag,
  drink, and extract signals.  Forms may overlap because a source name can
  contain more than one reviewed signal.

The derived local identity file contains 134,337 identity-only records and
does not contain prices, offers, stock, availability, or store decisions.  It
is intentionally ignored by Git and can be regenerated from the pinned source
hash.

## What remains

The current reviewed vocabulary is not a claim that every product has been
translated or assigned a category.  Most source names are still unclassified.
Automatically inventing synonyms from a name would create false matches for
brands, package text, flavours, cosmetics, and mixed products.  The safe next
step is to review high-frequency unclassified families in bounded batches,
add bilingual rules with tests, rebuild the derived identity file, and only
then expose a clarification choice in Search.  New source releases can repeat
the same read-only audit before any rule is promoted.

## Reproduction

The scanner is `tools/audit_argentina_product_identity.py`.  It requires the
source SHA-256 and byte count, so an unrelated or stale provider file fails
closed.  Example:

```text
python tools/audit_argentina_product_identity.py \
  --input <authorized-source.zip> \
  --expected-sha256 153e9d9f7ab4b87e72c16aebebc69c6f9f90fca681cd1075d625244e2e31bc2c \
  --expected-bytes 322801717 \
  --release-date 2026-10-08 \
  --report <coverage.json> \
  --identity-output <identities.jsonl.gz>
```
