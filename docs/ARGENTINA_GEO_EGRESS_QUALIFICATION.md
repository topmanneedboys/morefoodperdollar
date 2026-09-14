# Argentina source egress qualification

Status: **QUALIFICATION HARNESS ONLY — NO PRODUCTION CHANGE**

This bounded harness determines whether the existing official SEPA acquisition path behaves differently over two operator-selected network routes. It does not configure a VPN, use a proxy URL, use an alternate mirror, write an archive, build a release, stage a canary, or change `control/active.json`.

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

## Windows / PowerShell qualification

Run from the repository root. Reports should be written outside the repository.

### 1. Baseline route

Disconnect the Argentina VPN first, then run:

```powershell
$Observed = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$Baseline = Join-Path $env:TEMP 'valuepilot-sepa-baseline.json'
python tools\qualify_argentina_geo_egress.py run `
  --observed-at $Observed `
  --egress-label canada-direct `
  --output $Baseline
```

A current HTTP 403 is an expected bounded failure and should produce a report with:

- `status: FAILED`
- `error: HTTP_ERROR`
- a message containing `403`
- `activePointerWrites: 0`
- `archiveWrites: 0`
- `buildStarted: false`
- `productionStateChanged: false`

### 2. Candidate route

Connect the already-approved Argentina VPN/network route. Do not change ValuePilot URLs or source-host validation. Then run:

```powershell
$Observed = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$Candidate = Join-Path $env:TEMP 'valuepilot-sepa-argentina-route.json'
python tools\qualify_argentina_geo_egress.py run `
  --observed-at $Observed `
  --egress-label argentina-vpn `
  --output $Candidate
```

This performs a complete bounded source download (currently roughly 325 MB), verifies its SHA-256 and official ZIP identity, and removes the temporary source after the qualification process exits.

A success report must have:

- `status: PASS`
- `stage: VERIFIED_OFFICIAL_SOURCE`
- final host `datos.produccion.gob.ar`
- an internally proven release date
- exact byte count and SHA-256
- `activePointerWrites: 0`
- `archiveWrites: 0`
- `buildStarted: false`
- `productionStateChanged: false`

### 3. Compare

```powershell
$Comparison = Join-Path $env:TEMP 'valuepilot-sepa-route-comparison.json'
python tools\qualify_argentina_geo_egress.py compare `
  --baseline $Baseline `
  --candidate $Candidate `
  --output $Comparison
Get-Content $Comparison
```

The strongest expected conclusion is:

`CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403`

That proves route-dependent access using the exact ValuePilot acquisition path. It deliberately does **not** claim that ValuePilot independently geolocated the exit IP.

## Automation boundary after qualification

Do not add a generic HTTP proxy parameter to `argentina_release_acquisition.py`. The durable route should stay below the application networking layer so ValuePilot still connects directly to and validates the official source host.

Only after a successful comparison should an unattended egress adapter be selected. Prefer, in order:

1. an operator-approved VPN client/protocol that can run unattended on a dedicated worker;
2. a dedicated runner with already-established Argentina egress;
3. a small Argentina-hosted acquisition worker if no safe unattended VPN mode exists.

The acquisition runner may pass a verified ZIP into the existing automated release worker, but the existing source/date/hash, archive, release, canary, smoke, compare-and-swap, and rollback gates remain authoritative.

Do not place VPN credentials in Git, command output, qualification JSON, release manifests, or routine logs. If a future GitHub Actions runner needs Google Cloud access, use GitHub OIDC / Google Workload Identity Federation rather than a long-lived service-account key.

## CI boundary

CI tests this harness with deterministic fake discovery/download/inspection functions. CI does not contact the government source and does not need VPN credentials. A real geo-egress result is operator/network evidence and must never be fabricated from a passing fixture test.
