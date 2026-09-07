# Argentina SEPA / Precios Claros qualification

Status: `CONDITIONAL_GO_OFFLINE_ADAPTER_ONLY`

Recommendation: **CONDITIONAL_GO_OFFLINE_ADAPTER_ONLY**

This report qualifies one exact official release for offline/provider-edge normalization only. It does not add Android networking or authorize production display/ranking.

## Exact source

- Release date: **2026-09-06**
- File: **sepa_domingo(1).zip**
- Bytes: **325,522,188**
- SHA-256: `e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305`
- Dataset: [https://datos.produccion.gob.ar/dataset/sepa-precios](https://datos.produccion.gob.ar/dataset/sepa-precios)
- Licence recorded from the national catalog: **Creative Commons Attribution 4.0**
- Attribution: **Precios Claros - Base SEPA; source: datos.produccion.gob.ar**

## Actual structure

The outer ZIP contained **15** nested retailer packages (1 zero-byte). Each valid package was required to contain `comercio.csv`, `sucursales.csv`, and a streamed `productos.csv`.
Expanded nested member sizes total **1,605,466,611 bytes**; the national product table was never loaded into memory.

## Preliminary cross-check

The independent preliminary figures reconcile to this run where the definitions are identical. The remaining differences are intentional and documented:

- Store rows: preliminary **2,620** vs strict full-field rows **2,595**; 25 short eight-field continuation rows in commerce 6 (among 120 malformed/footer rows) are not treated as stores.
- Unknown-store product rows: preliminary **55,989** vs **73,964**; the additional 17,975 are the product references from that same malformed commerce-6 store table and are explicitly quarantined.
- Quantity: preliminary positive numeric quantity+unit rows **14,311,497** vs **14,311,497**; canonical mass/volume/count rows are **13,915,328**, with **396,169** noncanonical units and **1,752** invalid/non-positive values kept UNKNOWN.
- Stale age: the stale package is **451.00** days using its explicit UTC-normalized timestamp; the preliminary **~452** was an approximate local-date value.

## Scale

- Product-price rows scanned: **14,313,249**
- Distinct provider product IDs: **78,671**
- Commerce IDs / banner keys: **14 / 26**
- Store keys / localities / provinces: **2,595 / 493 / 25**
- Store rows with valid coordinates: **2,594**; incomplete: **0**; invalid/out-of-bounds: **1**

## Identity and quantity readiness

- Checksum-valid GTIN rows / unique GTINs: **14,272,803 / 78,184** (99.72%)
- Valid GTINs appearing across at least two commerce IDs: **20,399**
- Retailer-specific-only rows: **40,446**; fuzzy matching: **NOT_USED**
- Conflicting product-ID / GTIN identity scopes: **20,308 / 20,308**
- Explicit quantity/unit rows: **14,313,249**; canonical mass/volume/count rows: **13,915,328** (97.22%); unknown quantity: **397,921**
- Unit-value-ready rows: **13,812,758**; quantity is never inferred from title text.

## Price, promotions, and freshness

- Positive list-price rows: **14,313,193**; invalid required price: **56**
- Plausibility signals preserved/quarantined: under ARS 10 = **8,024**, over ARS 10,000,000 = **9**
- Promotion-bearing rows: **1,640,544**; promotion eligibility remains **UNKNOWN** unless separately established.
- Package freshness policy: **2 days**, using explicit release date and package-level latest timestamp; stale packages: **1**.
- Row-level observation timestamp: **not provided**; package update coverage: **26 commerce metadata rows with parseable update timestamps; 0 without**.

## Geography

- Province count: **25**; locality count: **493**.
- Coverage beyond Buenos Aires is evidenced by the represented provinces: AR-A, AR-B, AR-C, AR-D, AR-E, AR-F, AR-G, AR-H, AR-J, AR-K, AR-L, AR-M, AR-N, AR-P, AR-Q, AR-R, AR-S, AR-T, AR-U, AR-V, AR-W, AR-X, AR-Y, AR-Z, Buenos Aires.
- Missing coordinates remain geo-incomplete; no coordinates were invented or geocoded.

## Deterministic sample audit

- Status: **MEASURED**, sample size **211** (minimum 100).
- Sample spans **25** provinces, **97** localities, and **14** commerce IDs.
- Failure counts: `{"coordinates": 6, "freshness": 4, "store_linkage": 6}`.

## Basket feasibility

The 25-scenario semantic basket test is **NOT_YET_QUALIFIED**. SEPA has no structured category field, and keyword-only matches previously produced false positives. No basket launch claim is made here.

## Storage and delivery boundary

- Raw outer ZIP: **325,522,188 bytes**; nested uncompressed members: **1,605,466,611 bytes**.
- Normalized output is local/provider-edge only: **22,181,289,398 uncompressed bytes** before gzip.
- The full national raw dataset must not ship in Android assets; regional immutable snapshots remain a later delivery decision.
- Delivery/pickup fields are **IN_STORE_ONLY** only: orderability, pickup, delivery, fees, ETA, slots, and fulfilment hours are NOT PROVIDED.

## Rights and recommendation

The source licence is recorded as **Creative Commons Attribution 4.0** with attribution and source link. Retailer marks, product images, and trademarks are separate/unaddressed and are not ingested. This is not a legal opinion.

Conditional GO is limited to offline/provider-edge normalization and a future separate adapter review. Production integration still requires the documented freshness, rights, semantic basket, and evidence gates; availability remains UNKNOWN.

### Explicit blockers

- No row-level product observation timestamp is present; freshness is package-level provenance only.
- Availability is UNKNOWN; SEPA price publication does not prove stock, pickup, or delivery.
- Semantic 25-basket coverage is not qualified because the schema has no category field and keyword-only matching is unsafe.
- Retailer marks, logos, images, and any rights beyond the catalog licence remain outside this ingestion and require separate confirmation.

Next milestone: After rights/attribution confirmation and a small manually reviewed semantic basket set, build a separate provider-edge Argentina adapter; keep Android offline and keep delivery/pickup as later adapters.
