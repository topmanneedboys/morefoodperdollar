# National promotion automation access recovery

The October 17 acquisition succeeded and Cloud Build `8c24715e-788a-4f51-a24b-6a997b35e9b3` is building the candidate. The previous runtime release is October 14. The automation account could read only the US runtime bucket, could not read/write the South America runtime bucket, and had no runtime-service update/invocation or receipt-publication access. Operator activation of October 14 did not establish that the GitHub identity could promote a later release.

## Changes applied by the operator

The existing `valuepilot-github-release` service account received the exact bindings in `AUTOMATION_PERMISSION_PLAN.json`:

- Read immutable data in the two national runtime buckets.
- Create content-addressed objects and release manifests under their existing immutable prefixes.
- Replace only each bucket's `control/active.json`, retaining the existing generation/CAS safeguards.
- Update/read the two existing regional runtime services through a minimal custom role and invoke their private smoke-check endpoints.
- Read long-running operation status, and use only the existing runtime service identity when updating those services.
- Create/read immutable receipts under the existing promotion-receipt prefix.

The installer defaults to displaying the plan; `--apply` is an operator action. Existing unrelated IAM bindings are preserved. It does not publish an APK, change active data, disable App Check, or grant public invocation.

## Authentication correction

The prior workflow used one unscoped `gcloud auth print-identity-token` value for both regional services. Machine identity verification requires an audience matching each receiving service. The workflow now obtains separate short-lived US and South America ID tokens through its existing GitHub-to-Google federation. Tokens are passed through masked environment variables, with target/environment-name alignment validated before any promotion writes.

References: [Google authentication action](https://github.com/google-github-actions/auth), [Cloud Run service authentication](https://docs.cloud.google.com/run/docs/authenticating/service-to-service).

## Read-only machine verification

`verify_promotion_access=true` skips national acquisition and prevents activation. It obtains real machine tokens, checks runtime update/operation/service-identity permissions, and performs private readiness/status/search smoke checks against both current services. This verifies the actual runner identity rather than relying on the operator's broader account. It does not exercise active-pointer replacement or publish data. The October 17 automatic activation must still be observed before claiming that full unattended promotion has been verified.

Focused promotion, permissions and workflow regression tests passed (27). Actual runner access verification is pending deployment of this checkpoint.
