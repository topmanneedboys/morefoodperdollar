# Argentina source egress qualification

Status: **ROUTE-DEPENDENT OFFICIAL SOURCE ACCESS PROVEN — UNATTENDED EGRESS NOT YET DEPLOYED**

This bounded qualification determines whether the existing official SEPA acquisition path behaves differently over operator-selected network routes. It does not configure a VPN, use a proxy URL, use an alternate mirror, stage a canary, or change `control/active.json`.

The harness reuses ValuePilot's existing source controls:

- official CKAN metadata from `datos.gob.ar`;
- exact official source host `datos.produccion.gob.ar`;
- HTTPS-only URL and redirect validation;
- bounded streaming download;
- observed-size check when supplied by metadata;
- SHA-256 while downloading;
- outer ZIP CRC validation;
- internal release-date evidence from the official package paths;
- second source identity check during ZIP inspection.

A route label such as `argentina-vpn` is operator-supplied context only. ValuePilot does not call an IP-geolocation service and does not claim that the label independently proves the network exit country.

## Measured qualification — 2026-09-14 UTC

The exact ValuePilot acquisition path was executed twice from the same Windows host.

Baseline route:

- egress label: `canada-direct`;
- discovery/catalog: succeeded;
- official source host: `datos.produccion.gob.ar`;
- download result: `HTTP_ERROR`, HTTP 403;
- archive writes: `0`;
- build started: `false`;
- production state changed: `false`.

Operator-selected Argentina VPN route:

- egress label: `argentina-vpn`;
- status: `PASS`;
- stage: `VERIFIED_OFFICIAL_SOURCE`;
- final host: `datos.produccion.gob.ar`;
- downloaded bytes: `330069965`;
- SHA-256: `5c8912521d6fe2d64e2f096b962acd47c18c247cce7787576ae04880d028b962`;
- source kind: `OFFICIAL_SEPA_ZIP`;
- internally proven source date: `2026-09-07`;
- archive writes: `0`;
- build started: `false`;
- production state changed: `false`.

Comparison conclusion:

`CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403`

This proves route-dependent access using the exact ValuePilot acquisition path. It deliberately does **not** claim that ValuePilot independently geolocated the exit IP.

The successful qualification also exposed stale CKAN discovery hints: at observation time `2026-09-14T01:26:02Z`, the old selector chose `Lunes` and the ZIP internally proved `2026-09-07`, while the trusted active source was already `2026-09-08`. The candidate selector is therefore required to prefer the Argentina-local weekday endpoint before stale metadata hints. The ZIP's internally proven date/hash remains the only release/freshness authority, and older bytes remain fail-closed.

## Windows / PowerShell qualification procedure

Run from the repository root. Reports should be written outside the repository. For direct module execution on Windows, keep the repository root on `PYTHONPATH` and invoke the module with `python -m`.

### 1. Baseline route

Disconnect the Argentina VPN first, then run:

```powershell
$env:PYTHONPATH=(Get-Location).Path
$Observed = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$Baseline = Join-Path $env:TEMP 'valuepilot-sepa-baseline.json'
python -m tools.qualify_argentina_geo_egress run `
  --observed-at $Observed `
  --egress-label canada-direct `
  --output $Baseline
```

A current HTTP 403 should produce a bounded failure with no archive/build/production write.

### 2. Candidate route

Connect the already-approved Argentina VPN/network route. Do not change ValuePilot URLs or source-host validation. Then run:

```powershell
$Observed = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$Candidate = Join-Path $env:TEMP 'valuepilot-sepa-argentina-route.json'
python -m tools.qualify_argentina_geo_egress run `
  --observed-at $Observed `
  --egress-label argentina-vpn `
  --output $Candidate
```

This performs a complete bounded source download, verifies its SHA-256 and official ZIP identity, and removes the temporary source after the qualification process exits.

### 3. Compare

```powershell
$Comparison = Join-Path $env:TEMP 'valuepilot-sepa-route-comparison.json'
python -m tools.qualify_argentina_geo_egress compare `
  --baseline $Baseline `
  --candidate $Candidate `
  --output $Comparison
Get-Content $Comparison
```

## Automation boundary after qualification

Do not add a generic HTTP proxy parameter to `argentina_release_acquisition.py`. The durable route stays below the application networking layer so ValuePilot continues connecting directly to and validating the official source host.

The safe automation split is:

1. a small acquisition runner with already-established approved Argentina egress reads the checksum-verified active release identity;
2. it downloads the Argentina-local weekday ZIP through the normal official URL and verifies host/hash/ZIP/internal date;
3. `NO_CHANGE` and `OLDER_REJECTED` stop without a build;
4. a genuinely newer verified source is written to the existing content-addressed private source archive;
5. the handoff contains the exact archive key/SHA, source date, observation timestamp, and current trusted source identity;
6. Google Workflow/Cloud Build restores that exact archived ZIP through `argentina_release_handoff_worker` and runs the mature release worker;
7. staging/canary/activation rules remain unchanged and `SHADOW` remains the default.

The heavy release build must not be routed through the VPN. Only source acquisition needs the Argentina egress path.

Unattended egress remains an external deployment choice. Prefer, in order:

1. an operator-approved VPN client/protocol that can run unattended on a dedicated acquisition worker;
2. a dedicated runner with already-established Argentina egress;
3. a small Argentina-hosted acquisition worker if no safe unattended VPN mode exists.

Do not place VPN credentials in Git, command output, qualification JSON, release manifests, or routine logs. If a future GitHub Actions runner needs Google Cloud access, use GitHub OIDC / Google Workload Identity Federation rather than a long-lived service-account key.

## CI boundary

CI tests the selection, qualification, archive handoff, active-manifest identity lookup, archive restore entrypoint, and failure-closed behavior with deterministic fixtures. CI does not contact the government source and does not need VPN credentials. A real geo-egress result must never be fabricated from a passing fixture test.
