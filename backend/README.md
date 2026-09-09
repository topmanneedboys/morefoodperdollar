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

The container can instead use the provider-neutral S3-compatible release
store.  Set `VALUEPILOT_RELEASE_STORE=s3`, `VALUEPILOT_RELEASE_BUCKET`, and
optionally `VALUEPILOT_RELEASE_ENDPOINT_URL` and
`VALUEPILOT_RELEASE_REGION`.  boto3's standard credential chain supplies the
runtime credential; the process only calls HEAD/GET/range GET.  Do not set the
publisher credential in the runtime.

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

An operator can dry-run or explicitly apply a qualified local M9/M10
content-addressed workspace with `tools/argentina_object_store_publisher.py`.
The publisher uses `VALUEPILOT_PUBLISH_BUCKET`, optional
`VALUEPILOT_PUBLISH_ENDPOINT_URL`/`VALUEPILOT_PUBLISH_REGION`, and the
operator's separate write credential. It never runs in the backend container;
`--apply` is required for writes and the active pointer is written last.

File-backed immutable uploads use boto3's managed `upload_file` transfer. Files
at or above the bounded 16 MiB threshold use 16 MiB multipart parts with at
most four concurrent transfers; the client uses standard botocore retries with
five total attempts and the adapter permits at most three bounded
connection-reset retries. Each retry first verifies whether the complete
immutable object became visible, so an ambiguous success is not overwritten.
Existing objects are skipped only after exact size/SHA-256 verification;
mismatches fail closed. No delete-on-failure behavior is used. Incomplete
publication leaves manifests, control metadata and `control/active.json`
untouched, so a later run can safely resume already verified objects before
publishing the pointer last.

Availability is always `UNKNOWN` for SEPA prices. A stale release fails closed
with `CURRENT_PRICE_EVIDENCE_UNAVAILABLE`; it is never presented as today's
price. The existing 128/32 mobile contract remains intact for a future optional
offline feature and is not replaced by the server profile.
