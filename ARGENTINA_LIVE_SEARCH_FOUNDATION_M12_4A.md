# ValuePilot Argentina M12.4A — live Search foundation

Updated: 2026-09-19

## Qualification label

`ARGENTINA_LIVE_SEARCH_FOUNDATION_M12_4A_READY_NOT_UI_QUALIFIED`

This is a bounded foundation checkpoint on branch
`candidate/argentina-live-search-m12-4`. It adds a user-triggered,
identity-only Search coordinator around the already-qualified M12 bridge and
hardens the existing public edge timeout budget. It deliberately does not
wire a visible Search/Home surface, upload a new Android bundle, or start
M12.4B.

The branch started at the qualified M12.3 head
`93f25a072881cb25db6bfc38517d4efc87523af9`.

## What changed

### Android coordinator

`ArgentinaLiveSearchCoordinator` is an application-edge orchestration seam,
not a View or shared-core authority. It:

- accepts only an explicit `ArgentinaSearchContext`; there is no default,
  invented, or silently acquired device location;
- requires one nonblank bounded query and one explicit user-triggered submit;
- performs one status handshake when no release is pinned, requiring a strict
  release id/date, `FRESH` status, qualified backend code, and explicit
  `androidNetworkingAuthorized` permission;
- uses the returned release as the `ExpectedRelease` for `/v1/search`, so the
  rolling active release is learned from the handshake rather than hardcoded
  in the APK;
- clears the pin and fails closed on a release mismatch; it never retries a
  mismatched or stale request automatically;
- projects only existing identity presentation. Prices, package quantities,
  stock, availability, ranking, and provider economics cannot enter the
  result state;
- rejects missing geography as the typed
  `SEARCH_GEOGRAPHY_UNAVAILABLE` state;
- rejects a backend that has not explicitly authorized Android live Search as
  the typed `SEARCH_NOT_AUTHORIZED` state, before `/v1/search` is called;
- cancels superseded generations, rejects stale callbacks, clears the pin on
  route changes, and tears down owned work on close;
- uses a one-worker, one-pending-slot latest-only executor. A cancelled slow
  request cannot create an unbounded queue or request storm.

The existing bridge, strict contract parser, App Check transport, identity
mapper, and refresh gate remain the authorities. No Search UI consumes this
seam yet.

### Timeout budget

The M12.3 device qualification observed an edge cold-start request at
8.578306877 seconds (504) while the private M11 request completed at
4.898859329 seconds. The finite M12.4A hierarchy is now:

| Boundary | Budget |
| --- | ---: |
| App Check token acquisition | 5 s (existing SDK bound) |
| Android connect | 5 s |
| Android read | 15 s |
| Android hard total | 20 s |
| Edge queue | 0.05 s |
| Edge → private M11 | 12 s |
| Cloud Run service request | 20 s |
| Private OIDC token acquisition | 4 s (existing bound) |

The edge and Cloud Run budgets are finite and preserve cancellation,
scale-to-zero, bounded responses, and no-retry behavior. The 12-second
upstream allowance is intentionally below the 20-second public service
deadline, leaving room for token and edge overhead.

## Live deployment verification

The existing service was updated in place; no new Android artifact was
uploaded.

- Project: `valuepilot-argentina`
- Service: `valuepilot-argentina-consumer-edge`
- Region: `southamerica-east1`
- Revision: `valuepilot-argentina-consumer-edge-00004-5lg`
- Image digest:
  `sha256:315e37021aa7b1adf2f77eba88bc85f09984025ebac98e89612ff40168238581`
- Cloud Run timeout: `20s`
- Runtime identity: `vp-arg-consumer-edge@valuepilot-argentina.iam.gserviceaccount.com`
- Scale policy: minimum `0`, maximum `20`, generation 2, startup CPU boost
- Firebase project/App Check allow-list and private M11 OIDC destination:
  unchanged from M12.3/M12.2 qualified configuration

Post-deployment checks:

- no App Check: `401`;
- invalid App Check: `401`;
- unknown route: `404`;
- real Play-installed `com.valuepilot.app` release probe: `M12_3_STATUS=200`
  on three process restarts, with host relaunch-to-safe-log times of about
  1.1–1.6 seconds. These are end-to-end host timings, not claimed HTTP-only
  latency.

The `200` proves the existing Play Integrity/App Check → public edge → OIDC
private M11 path remains live after the revision update. No token, response
body, credential, or authorization header was logged.

The prior 8.578-second cold-start observation remains the authoritative cold
reference. A new forced scale-to-zero measurement was not claimed because no
safe deterministic way to force that external state was available during this
checkpoint. Live `/v1/search` timing was not remeasured: visible Search is
intentionally out of scope and the installed qualified build has no Search
submit path. The coordinator's deterministic fake transport covers the
request/release/cancellation path instead.

## Verification

The new offline tests cover:

- explicit-geography gating and blank/over-limit queries;
- status pinning, non-hardcoded rolling release ids/dates, and route reset;
- identity-only success, empty results, and the 24-match bound;
- stale, malformed, unauthorized, and release-safe failure states;
- generation supersession, cancellation, query replacement, and teardown;
- bounded timeout defaults for Android, edge, and Cloud Run.

The final local verification completed successfully:

- 405 shared-core tests and 1,661 Android unit tests, with zero failures or
  skips;
- debug and release lint plus debug and release assembly;
- APK privacy and single-signer checks (intentional `INTERNET`, no
  `ACCESS_NETWORK_STATE`);
- 42 backend tests, 197 tooling tests, Python compilation, and 30 browser/
  Firefox checks;
- `git diff --check` and the final working-tree review.

The repository's exact-head GitHub workflows remain the final remote CI gate;
this candidate does not alter the qualified M12.3 branch or the milestone
provenance workflow.

## Remaining boundary

This checkpoint is not user-facing qualification. M12.4B remains blocked
until a later separately authorized milestone supplies an authoritative
geography and a visible Search owner, then requalifies the coordinator on a
new Android artifact. The existing M12.3 qualified branch and evidence remain
unchanged.
