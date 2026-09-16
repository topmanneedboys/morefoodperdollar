# ValuePilot Argentina consumer edge (M12.1)

This package is the public consumer-edge boundary, not a second shopping
backend. It requires HTTPS (or a single trusted `X-Forwarded-Proto: https`
value from the Cloud Run proxy), authenticates Firebase App Check tokens,
permits only the five consumer routes, applies bounded
request/response/concurrency/rate policies, and forwards through an injected
upstream interface. The process must stay behind Cloud Run or another proxy
that overwrites `X-Forwarded-Proto`; it must not be exposed directly on a
plaintext socket.

The default JSON-array bound is 4,096. This is a transport safety limit, not
a promise that the backend will produce that many records; M11 remains the
authority for route-specific candidate, offer, store, and basket limits.

The current milestone intentionally has no M11 upstream implementation. The
default `ConfigurationRequiredUpstream` fails closed until M12.2 separately
qualifies server-side workload identity, private Cloud Run invocation, and
private-ingress behavior. No Android networking or cloud resource mutation is
part of this package yet.

Production configuration uses Application Default Credentials through the
Firebase Admin SDK; service-account keys and other reusable credentials must
never be copied into the image or repository. Set
`VALUEPILOT_FIREBASE_PROJECT_ID` and, when desired, the comma-separated
`VALUEPILOT_FIREBASE_APP_IDS` allow-list. The App Check verifier validates the
token with Firebase's current Admin SDK before any request reaches the
upstream seam.
