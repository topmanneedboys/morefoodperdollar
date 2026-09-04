# Canada-first offline catalog refresh pipeline

`tools/refresh_offline_catalog_snapshots.py` is the repeatable weekly
coordination boundary for the identity-only launch catalog. It deliberately
does not download a provider export or read the wall clock. A scheduler or
operator supplies the export, rights evidence, timestamps, and signing keys;
the existing selector, importer, builder, verifier, and promotion code remain
the authorities for each decision.

The pipeline performs these bounded steps:

1. Selects only Canada-labelled Open Food Facts identities, with deterministic
   ordering and a caller-selected maximum (30,000 by default at the current
   launch gate).
2. Imports only identity/display fields into the `off-ca` source namespace.
   Price, promotion, package quantity, stock, availability, store and
   freshness fields are rejected or omitted.
3. Builds separate signed manifests for `ca-gta` and
   `ca-metro-vancouver` from the same explicitly supplied source snapshot.
4. Verifies every candidate's canonical JSON, source hash, rights gates,
   signature, identity-only role, and coverage before any pointer changes.
5. With `--promote`, writes one immutable release record under `generations/`
   and atomically replaces the root `active-generation.json` pointer only after
   every requested candidate passes preflight. The pointer embeds the prior
   complete release as `lastKnownGoodGeneration`; an interrupted run can leave
   an orphaned generation record, but it cannot expose a mixed regional release.

Example (all dates are explicit and must describe the supplied export):

```powershell
python tools/refresh_offline_catalog_snapshots.py `
  --input F:\exports\openfoodfacts-products.csv.gz `
  --output-root F:\valuepilot-catalog-state `
  --rights-manifest F:\valuepilot-rights\open-food-facts-ca.json `
  --private-key F:\valuepilot-secrets\catalog-signing-private.pem `
  --public-key android\app\src\main\assets\offline_catalog\public-key.pem `
  --generated-at 2026-09-03T12:00:00Z `
  --acquired-at 2026-09-03T11:00:00Z `
  --evaluated-at 2026-09-03T12:00:00Z `
  --maximum-age-millis 604800000 `
  --source-snapshot-id off-products-2026-09-03 `
  --source-published-at 2026-09-02T00:00:00Z `
  --promote
```

`--promote` is optional. Without it, the output root must be new or empty and
contains only verified candidates. With it, the output root is the promotion
state root and may already contain regional candidate directories and an
`active-generation.json` pointer. Existing candidate directories and release
records are never overwritten. The private signing key is read only for
detached RSA signing and must remain outside the repository. The older
single-region promotion helper remains available for compatibility, but the
multi-region refresh authority is the generation pointer.

The `catalogRecordCount` band (1,500–30,000 by default) measures identity
coverage only. `currentOfferRecordCount` remains zero and
`currentOfferCoverage` remains `NOT_INCLUDED`; the catalog therefore does not
claim a current price, local stock, store availability, package quantity or
freshness. Any future current-offer rail must be authorized and measured
separately before it can enter the existing production planner/ranking path.

Every successful refresh also writes a deterministic `coverage-report.json` at
the output root. It is a diagnostic summary for the weekly operator: the
`catalog` object reports the selected identity-record count and configured
bounds, while `currentOffers` reports `0` with `NOT_INCLUDED`. Per-region
manifest hashes and the same two measurements are included for audit. The
report is not a signed authority and must never replace verification of the
regional manifest or the active generation. It is a compatibility cache; the
generation record remains complete even if writing that cache fails. A
rejected/regressed refresh leaves the active generation unchanged.
