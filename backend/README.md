# ValuePilot Argentina backend foundation

This is a portable, read-only provider-edge service. It consumes an already
qualified immutable ValuePilot release and delegates money, quantity, and
shopping decisions to the existing Milestone 6 engine.

## Local run

Install the pinned dependencies and point the process at a local qualified
root (the 1024/32 root is the backend profile used for qualification):

```text
python -m pip install -r backend/requirements.txt
$env:VALUEPILOT_RELEASE_ROOT = 'F:\valuepilot-m5-alternatives\micro-1024'
python -m backend
```

The service has no acquisition job. It never contacts `datos.produccion.gob.ar`
and does not bypass WAFs, use proxies, or infer missing source facts.

## Free-first deployment blueprint

The documented first deployment candidate is Cloudflare R2 Standard through the
S3-compatible object-store adapter and a small request-based Cloud Run service.
No cloud resource, account, credential, billing configuration, or cost claim
is created by this repository. Start with zero minimum instances and a small
maximum; choose a region only after measuring Argentina→compute and
compute→object-storage latency. Runtime credentials should be read-only and
publication credentials separate.

`control/active.json` points at an immutable `releases/<id>/` generation. A
request resolves and pins one generation before routing. Publication uploads
and verifies the generation before changing the pointer; the local publisher
uses a single-writer lock where a portable object-store compare-and-swap is not
available.

Availability is always `UNKNOWN` for SEPA prices. A stale release fails closed
with `CURRENT_PRICE_EVIDENCE_UNAVAILABLE`; it is never presented as today's
price. The existing 128/32 mobile contract remains intact for a future optional
offline feature and is not replaced by the server profile.
