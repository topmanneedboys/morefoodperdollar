# Argentina automated release source contract

Observed 2026-09-13 at the explicit qualification timestamp
`2026-09-13T12:00:00Z`.

The national catalog endpoint is reachable and identifies the expected
dataset:

- dataset: **Precios Claros - Base SEPA**
- dataset ID: `0f495d54-4a5d-5fa3-a323-f6e97ce434cf`
- catalog: `https://datos.gob.ar/api/3/action/package_show?id=precios-claros-base-sepa`
- response: HTTP 200, 15,728 bytes,
  `60d6401f530722cfe7aab45510a84d8d7c18b5c2eee4f01d4d3eaa644cdfede0`
- licence metadata: **Creative Commons Attribution 4.0**
- weekday resources: seven HTTPS ZIP candidates, including the catalog's
  `Martes` resource ID `7df65a95-1f1d-52a8-9a93-082e35998831`.

The catalog currently reports null resource sizes and July 2026
`last_modified` values. That metadata is stale relative to the already
verified local Tuesday release. It is retained as discovery evidence only;
the acquisition worker never turns `last_modified` or a filename into a
release identity.

The official production API, dataset page, and resource download probe were
HTTP 403 during this qualification. Therefore live automated download access
is **not proven**. The worker keeps the official production host allowlist,
HTTPS-only redirects, DNS/private-address checks, bounded streaming, and
fail-closed errors. It does not bypass the WAF, guess a URL, or substitute a
mirror.

`C:\Users\iamatopstrongman\Downloads\sepa_martes.zip` is the known local
baseline, not a new source: 325,118,492 bytes,
`aeef3399fa20e751e80cda46cbb3a38349e378b51832ecffe1f779b986479815`, internally
dated 2026-09-08. Its existing qualification is reused; no source-wide
requalification is needed for this metadata contract check.

Until a genuinely newer official ZIP is accessible and passes the existing
qualification, the safe rollout disposition is:

`SHADOW_ONLY_WAITING_FOR_ACCESSIBLE_NEWER_OFFICIAL_SOURCE`

This document records source facts and engineering evidence. It is not a
legal opinion or a grant of rights beyond the catalog licence metadata.
