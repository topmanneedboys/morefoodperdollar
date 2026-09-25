# Argentina consumer edge (M12.1)

Status: `QUALIFIED_FOR_LOCAL_EDGE_BOUNDARY_ONLY`

This checkpoint implements and tests the small public-edge boundary described
by the M12 plan. It is a transport/security boundary only. It is not a live
consumer deployment and it does not change the Android application.

## Included boundary

Only these routes are admitted:

- `GET /v1/status`
- `POST /v1/search`
- `POST /v1/interpret`
- `POST /v1/shop-text`
- `POST /v1/shop`

All other paths, including health, admin, acquisition, release, storage, and
deployment operations, return a generic not-found response. POST requests
must use UTF-8 `application/json`; GET requests cannot carry a body or query
parameters. The edge rejects plaintext requests. It accepts a direct HTTPS
ASGI request or exactly `X-Forwarded-Proto: https`, which is the scheme signal
available after Cloud Run terminates TLS. Production must keep the process
behind Cloud Run or another proxy that overwrites that header; the container
must not be exposed directly on a plaintext socket.

Every admitted request requires a valid Firebase App Check token in
`X-Firebase-AppCheck`. The Firebase Admin SDK performs signature, issuer,
audience, and expiry validation; the edge additionally requires the verified
App ID subject and can enforce an explicit App ID allow-list. Token material
is never forwarded or logged. A valid production token and Firebase verifier
IAM have not been exercised in this local checkpoint.

## Explicit transport policy

The defaults are versioned in `backend.consumer_edge.EdgeConfig`:

| Boundary | Limit |
| --- | ---: |
| Request body | 16 KiB |
| App Check token | 4 KiB |
| JSON depth | 24 |
| JSON array items | 4,096 |
| JSON object keys | 64 |
| JSON string | 4,096 UTF-8 bytes |
| Response body | 512 KiB |
| Concurrent requests | 16 |
| Queue wait | 50 ms |
| Upstream wait | 5 s |
| Rate window | 60 requests per minute per bounded App ID/client key |
| Rate keys | 1,024 per edge instance |

The response byte bound remains authoritative; the array bound prevents an
otherwise small malicious JSON response from allocating an unbounded number
of values. M11 remains authoritative for route-specific basket, candidate,
offer, store, and business validation. The edge contains no shopping,
ranking, provider, release, or source-acquisition logic.

Timeout, queue, rate, malformed-payload, invalid-response, and upstream
failure paths return bounded generic error codes. Responses include a safe
correlation ID and defensive cache/content/security headers. Upstream headers,
bodies, stack traces, and App Check tokens are not exposed.

## Deliberate non-scope

- No M11 upstream adapter is configured. The default upstream fails closed.
- No Cloud Run deployment, IAM mutation, public ingress change, or Firebase
  project change was made.
- M12.2 workload identity/OIDC and private M11 invocation remain pending.
- No Android HTTP client, Firebase Android SDK, Play Integrity configuration,
  `INTERNET` permission, or `ACCESS_NETWORK_STATE` permission was added.
- No provider data, credentials, keys, release archives, or national source
  files are included in the edge image or repository.

The current cloud M11 services remain private/read-only from this milestone's
perspective. The previously verified OIDC capability probe still lacks the
read permissions needed to independently qualify the next cloud step, so it
is not silently treated as M12.2 evidence.

## Verification

The deterministic local edge suite covers HTTPS/proxy-scheme handling,
route/method/content-type allow-lists, App Check rejection and claim checks,
query/body/JSON bounds, duplicate/non-finite JSON, response bounds and JSON
validation, sanitised upstream errors, timeout, bounded rate eviction, and
correlation/security headers. It uses in-memory fakes only and requires no
network, credential, Firebase project, Cloud Run service, or provider data.

The repository backend workflow also compiles the edge package, runs the
backend/tooling regression suites, and builds `consumer_edge/Dockerfile`.
The Android workflow is path-triggered for this package but Android remains
offline and permission checks remain unchanged.

The implementation was committed at code SHA
`5e829251cc43653e42a8583c2acd2cb2e9c18fb6`. Exact-head backend workflow
**35058092106** passed its compile, 27 backend tests, 197 tooling tests, both
container builds, and health smoke. Exact-head Android workflow
**35058092151** passed its browser verification (**104672396676**), Android
test/lint/build, privacy/signing verification (**104672396323**), and
release-bundle assembly (**104674454452**); its milestone provenance proof was
correctly skipped for this candidate branch.

This is an implementation checkpoint, not a production-launch approval. The
next authorized step is M12.2 only after a separately verified least-
privilege service identity can invoke private M11 through the intended Cloud
Run boundary.

## First-party design references

- [Firebase custom-backend App Check verification](https://firebase.google.com/docs/app-check/custom-resource-backend)
- [Firebase Android Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Cloud Run service-to-service authentication](https://docs.cloud.google.com/run/docs/authenticating/service-to-service)
- [Cloud Run HTTPS request behavior](https://docs.cloud.google.com/run/docs/triggering/https-request)
