# ValuePilot Argentina consumer edge (M12.2)

This package is the public consumer-edge boundary, not a second shopping
backend. It requires HTTPS (or a single trusted `X-Forwarded-Proto: https`
value from the Cloud Run proxy), authenticates Firebase App Check tokens,
permits only the five consumer routes, applies bounded
request/response/concurrency/rate policies, and forwards through an injected
upstream interface. The deployed edge uses a short-lived Google OIDC token
from Cloud Run Application Default Credentials to invoke one explicitly
allow-listed private M11 service. The process must stay behind Cloud Run or
another proxy that overwrites `X-Forwarded-Proto`; it must not be exposed
directly on a plaintext socket.

The default JSON-array bound is 4,096. This is a transport safety limit, not
a promise that the backend will produce that many records; M11 remains the
authority for route-specific candidate, offer, store, and basket limits.

An entirely unconfigured local composition retains the default
`ConfigurationRequiredUpstream` fail-closed seam. Production configuration
requires all three M11 target variables together; partial or malformed target
configuration fails startup. The deployed candidate is documented in
`ARGENTINA_CONSUMER_EDGE_M12_2_QUALIFICATION.md`. No Android networking is
part of this package.

Production configuration uses the Firebase Admin SDK for App Check and Google
Application Default Credentials for the short-lived Cloud Run OIDC token;
service-account keys and other reusable credentials must never be copied into
the image or repository. Set
`VALUEPILOT_FIREBASE_PROJECT_ID` and, preferably, the comma-separated
`VALUEPILOT_FIREBASE_APP_IDS` allow-list. The deployed M12.2 edge is pinned to
the registered `ValuePilot Android` App ID and its Firebase verifier was
exercised with a temporary debug attestation. The App Check verifier validates
the token with Firebase's current Admin SDK before any request reaches the
upstream seam. Real-device Play Integrity and Android client networking remain
outside this offline milestone and are not implied by the server smoke.
