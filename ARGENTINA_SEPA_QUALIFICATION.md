# Argentina SEPA / Precios Claros qualification

Status: `BLOCKED_OFFICIAL_SOURCE_UNRETRIEVABLE`

Evaluated at: `2026-09-07T02:34:00Z`

Baseline: `3c7b9cab9c2de693f983cf1c8e3365f386845e26`

Scope: qualify one current, official Argentine SEPA / Precios Claros retail
release for a possible future ValuePilot data adapter. This is a source
qualification checkpoint, not an Argentina integration and not a claim that
ValuePilot has current Argentine prices.

## Decision

**No production qualification is possible in this environment.** The official
dataset portal and its resource host returned a BunkerWeb HTTP 403 for the
dataset page, the API endpoint, and direct resource URLs. The same response
was reproduced in the in-app browser. Because the milestone requires the
actual current official release, this report intentionally does not substitute
a mirror, an unofficial export, a search-indexed historical resource, or a
reverse-engineered/bypassed access path.

Recommendation: `NO-GO_FOR_PRODUCTION_QUALIFICATION / BLOCKED_BY_ACCESS`.
This is an access block, not a finding that SEPA is inherently unsuitable.

## Official source chain reviewed

- [Argentina Precios SEPA landing page](https://www.argentina.gob.ar/economia/industria-y-comercio/defensadelconsumidor/precios-sepa)
  links the official open SEPA database and the retail/wholesale dataset.
- [Official production dataset page](https://datos.produccion.gob.ar/dataset/sepa-precios)
  is the required source for the current daily release.
- [Precios Claros official data site](https://www.preciosclaros.gob.ar/index.html)
  describes the public SEPA presentation and its data/rights terms.
- [Resolution 678/2020 Annex II technical specification](https://www.argentina.gob.ar/normativa/345335_res678-2_pdf/archivo)
  is the official technical-specification reference. It was not treated as a
  substitute for inspecting the current ZIP's actual schema.

The official page describes SEPA as daily, point-of-sale price information
supplied by large retail/wholesale merchants, covering more than 70,000
products. It describes a retail list price and up to two promotions, including
promotions that may be limited to a loyalty, bank, student, or senior group.
Those are page-level source facts only; they are not row-level evidence for a
ValuePilot offer.

Search-indexed portal metadata also describes weekday ZIP resources, daily
updates, approximately 12 million records, and a Creative Commons Attribution
4.0 dataset licence. The metadata page could not be retrieved directly in
this environment, so these indexed values are recorded only as context and
are **not** accepted as current-release qualification measurements.

## Retrieval evidence

No raw file was downloaded and no bytes were parsed.

| Attempt | Result | Evidence |
| --- | --- | --- |
| `https://datos.produccion.gob.ar/api/3/action/package_show?id=sepa-precios` | HTTP 403 | BunkerWeb response, request ID `4aa9a3137cd54a7e94dc3be3e193577b`, `2026-09-07 02:32:24 UTC` |
| `https://datos.produccion.gob.ar/dataset/sepa-precios` via direct HTTP | HTTP 403 | Same BunkerWeb barrier and client address; no HTML dataset payload |
| Direct resource download for indexed UUID `9dc06241-cc83-44f4-8e25-c9b1636b8bc8` | HTTP 403 | Tried `/download`, `/download/sepa.zip`, and `/archivo/<uuid>`; indexed UUID is not treated as current |
| Dataset page in the in-app browser | HTTP 403 | BunkerWeb response, request ID `e9ea8898870d53fba5b615f514840235`, `2026-09-07 02:33:39 UTC` |

The response body identified BunkerWeb as the access barrier and said access
was forbidden. No CAPTCHA, authentication, or security bypass was attempted.

## Qualification measurements

All measurements below are `NOT_MEASURED_OFFICIAL_DATA_UNAVAILABLE` by design:

- current release identity, resource hash, byte size, and schema;
- row count, merchant count, store count, product/identity count, and
  duplicate/conflict rates;
- barcode/GTIN coverage and deterministic identity joins;
- package quantity coverage, unit parsing, and quantity conflicts;
- positive price, currency, promotion-condition, and exact-money coverage;
- observation/freshness distribution and expiry behavior;
- province/city/store geography and store identity completeness;
- 100+ row human-reviewed sample audit;
- 25-basket feasibility scenarios and eligible single-store coverage;
- compressed/uncompressed storage estimates and lookup/load benchmarks;
- delivery, pickup, shipping, minimum-order, or store-fulfilment semantics.

The official website's general description cannot establish any of those
row-level or basket-level properties. In particular, the indexed “12 million
daily prices” statement must not be presented as ValuePilot coverage.

## Rights and attribution gate

The official dataset portal metadata is indexed as CC BY 4.0. Separately, the
official Precios Claros website terms state that government content is made
available under CC BY 2.5 Argentina, while product/store trademarks, images,
and logos remain the property of their respective holders; the site also says
the records are supplied by merchants and published as received. This licence
description discrepancy is unresolved because the authoritative dataset page
was inaccessible. Before any import, obtain and record the exact licence,
attribution, caching, retention, indexing, display, comparison, mobile,
geography, and commercial permissions for the specific release. No product
images, logos, or trademarks would be imported by this checkpoint.

## What was deliberately not done

- No mirror, GitHub copy, unofficial API, or search-result resource was used.
- No scraping, reverse engineering, rate-limit evasion, CAPTCHA handling, or
  BunkerWeb bypass was attempted.
- No Android networking, Argentina UI, localization, delivery logic, account,
  backend, or production adapter was added.
- No current-price, availability, stock, promotion, or ranking claim was
  created.
- No raw provider data, credentials, or restricted files were committed.

## Exact follow-up gate

When the official host is ordinarily reachable, or when the user supplies the
official ZIP and its release metadata directly, rerun this checkpoint against
that exact release. Preserve the raw file outside Git, hash it, inspect the
actual `comercio.csv`, `sucursales.csv`, and `productos.csv` (or the schema
specified by the current official release), then run deterministic identity,
quantity, price, freshness, geography, sample-audit, basket, storage, and
rights gates. Only a complete green result may recommend a separate future
integration milestone.
