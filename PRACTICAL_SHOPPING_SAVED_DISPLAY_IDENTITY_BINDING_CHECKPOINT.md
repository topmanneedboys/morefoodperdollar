# Practical Shopping Saved Display Identity Binding Checkpoint

Status: verified code boundary

Latest verified code commit: `a7b1d58f223357708ade36aaa413171676a490cb`
Verified workflow: run 147 / `33272740661`

## What is now verified

Saved display metadata is presentation-only and remains separate from saved exact preference authority.

Every saved product display entry carries the exact `ProductionProductEvidenceKey` it was created for. Every saved store display entry carries the exact `PracticalShoppingStoreIdentityScope` it was created for.

Before presentation metadata is projected for the current saved state, the binder rechecks those exact bindings. A display label attached to the same logical `ShoppingItemKey` or `ShoppingStoreKey` cannot silently follow that key after the user changes the exact saved product or store.

Equivalent canonical GTIN representations remain the same exact production product identity, so their display metadata may remain usable. A genuinely different product key or store scope is stale and is withheld.

Source-derived names remain separately gated by the verified Open Food Facts / OpenStreetMap identity adapters and explicit confirmation boundary. User-provided names also require an already user-confirmed exact candidate.

The downstream Saved UI projector still applies its independent consumer-text leakage policy. Product/store/provider/source identifiers remain unavailable as fallback display strings.

## Not added by this slice

- No label persistence bytes or file I/O.
- No Saved renderer or MainActivity wiring.
- No networking or provider activation.
- No price, availability, promotion, travel, freshness, ranking, authorization, or commerce authority.
- No Android permission change.

## Verification

Workflow 147 completed successfully through browser tests/package, Android JVM tests, lint/APK, JVM summary, Android privacy verification, release/checksum assembly, artifact upload, and post-job cleanup.

## Next safe slice

Add a separate deterministic bounded schema-1 codec for identity-bound Saved display metadata. Keep it separate from the authoritative saved-exact-preference document and from AtomicFile I/O. Require deterministic ordering, strict byte/field/count bounds, malformed/truncated/unknown-version rejection, duplicate-key rejection, UTF-8 correctness, and no partial decode. Only after that codec is independently verified should an app-internal AtomicFile backend be introduced.
