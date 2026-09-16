# Argentina consumer edge (M12.2)

Status: `DEPLOYED_CANDIDATE_BLOCKED_PENDING_FIREBASE_APP_CHECK`

This checkpoint implements the server-side bridge from the public Argentina
consumer edge to the already-qualified private M11 Cloud Run service. It is a
candidate deployment and an auditable code/IAM checkpoint; it is not an
end-to-end launch approval until a real Firebase App Check project/app is
configured and exercised.

## What is included

The edge admits only the five existing consumer routes:

- `GET /v1/status`
- `POST /v1/search`
- `POST /v1/interpret`
- `POST /v1/shop-text`
- `POST /v1/shop`

`consumer_edge/cloud_run.py` is a provider-neutral Cloud Run transport
adapter. It accepts only an explicitly configured HTTPS `*.run.app` target
whose host is in an explicit allow-list. It obtains a short-lived Google OIDC
identity token through Application Default Credentials, using the target
service URL as the audience. It forwards only the fixed route/method set and a
small allow-list of headers; caller authorization and arbitrary headers are
never passed through. Response bytes are streamed and bounded, and malformed,
oversized, timeout and authentication failures remain generic edge errors.

No SEPA parsing, shopping logic, ranking, product/offer authority, source
acquisition or Android networking was added. M11 remains the business
authority. The Android APK still has neither `INTERNET` nor
`ACCESS_NETWORK_STATE` permission, and M12.3 has not started.

## Cloud deployment

Project: `valuepilot-argentina`

Public edge service (Cloud Run, `southamerica-east1`):

- service: `valuepilot-argentina-consumer-edge`
- URL: `https://valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app`
- revision: `valuepilot-argentina-consumer-edge-00001-lzn`
- image: `us-east4-docker.pkg.dev/valuepilot-argentina/valuepilot-backend/valuepilot-consumer-edge@sha256:274280c381b5c052a661b7606f1219ac71e29556669b36bee292d473b3290a7b`
- runtime service account: `vp-arg-consumer-edge@valuepilot-argentina.iam.gserviceaccount.com`
- ingress: `all`; minimum instances `0`; maximum instances `1`; concurrency
  `16`; request timeout `10s`; generation 2

The organization policy rejects an `allUsers` IAM member (`iam.allowedPolicyMemberDomains`).
The edge is therefore public through the Cloud Run `invoker-iam-disabled`
service setting, while every admitted request still requires Firebase App
Check in the application boundary. This setting was applied only to the edge;
no M11 service was made public.

Private M11 target:

- service: `valuepilot-argentina-sa-east1`
- URL/audience: `https://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app`
- revision: `valuepilot-argentina-sa-east1-00029-gef`
- image: `us-east4-docker.pkg.dev/valuepilot-argentina/valuepilot-backend/valuepilot-backend@sha256:fb36dcf9c40d9af15ea9b8019601c7b0eb585bce1c23587006559f77b95e6707`
- direct unauthenticated request: `403 Forbidden`

The dedicated edge service account has exactly one resource-scoped binding:

```text
roles/run.invoker on valuepilot-argentina-sa-east1
  member serviceAccount:vp-arg-consumer-edge@valuepilot-argentina.iam.gserviceaccount.com
```

It has no project-level role binding, no binding on the US M11 service, and no
static key or secret is present in the image or repository. The production
adapter uses the Cloud Run runtime's ADC identity; no reusable credential is
stored.

## Cloud smoke evidence

The deployed revision reached `Ready` and serves the immutable image digest.
The following checks were run over HTTPS:

| Check | Result |
| --- | --- |
| Edge `/v1/status` without App Check | `401 {"error":"UNAUTHENTICATED"}` |
| Edge `/v1/status` with an invalid App Check token | `401 {"error":"UNAUTHENTICATED"}` |
| Edge unknown path | `404 {"error":"NOT_FOUND"}` |
| Direct M11 `/v1/status` without identity | `403 Forbidden` |
| Edge revision health/configuration | `Ready`; explicit M11 target variables present |

The code and IAM shape prove the intended OIDC route, and the adapter has
deterministic fake-transport coverage. A live OIDC request through the edge
could not yet be exercised because the project is not registered as a
Firebase project and the current operator account is not permitted to mint an
impersonated test token. This is intentionally recorded as missing evidence,
not treated as success.

## Firebase boundary

`valuepilot-argentina` currently returns `404 Requested entity was not found`
from the Firebase Management API, so no Firebase App Check app/token can be
validly registered for this deployment yet. Adding Firebase to an existing
Google Cloud project is an irreversible project action and requires accepting
Firebase Terms in the Firebase Console. The one remaining manual task is:

> In Firebase Console, choose the existing Google Cloud project
> `valuepilot-argentina`, select **Add Firebase**, accept the Firebase Terms
> (Analytics is optional), and report when the project is added.

After that action, registering the Android app and Play Integrity App Check
provider is a separate, explicit activation step. Until a real token is
verified against the deployed edge and the edge's OIDC call is observed, this
candidate must remain blocked.

## Verification and provenance

The adapter and strict partial-configuration fail-closed check were committed
at code SHA `842f4b6b3825b98ea8599424206b3451561e6a83` and pushed to
`candidate/argentina-android-consumer-bridge-m12-v2`.

Local backend verification: 40 tests passed. The edge image was built by
Cloud Build build `232e7ce4-d53e-4a26-9b83-dd05495fc486` with the digest shown
above. Exact-head GitHub Actions backend run `35062107407` passed. Exact-head
Android workflow `35062107472` also passed: Android verification job
`104684419403`, browser verification job `104684419537`, and release-bundle
job `104686868032` all succeeded; the candidate-branch milestone-provenance
job was correctly skipped. The Android job completed the repository's
test/lint/build, privacy, single-signer and APK staging checks; the release
bundle's checksum validation passed and its short-lived artifact is not a
production approval.

The candidate contains no national data, provider ZIP, Android network client,
or release artifact. Cloud Run and Cloud Build remain free-first: request-based
scale-to-zero, one maximum instance, no VPC connector, and the existing small
e2-standard-2 build pool.

## First-party references

- [Firebase custom-backend App Check verification](https://firebase.google.com/docs/app-check/custom-resource-backend)
- [Firebase Android Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Cloud Run service-to-service authentication](https://docs.cloud.google.com/run/docs/authenticating/service-to-service)
- [Cloud Run HTTPS request behavior](https://docs.cloud.google.com/run/docs/triggering/https-request)
- [Cloud Run invoker IAM-check setting](https://cloud.google.com/run/docs/securing/managing-access#invoker_check)
