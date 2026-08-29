# Practical Shopping Saved Display Metadata Codec Checkpoint

Status: verified code boundary

Latest verified code commit: `b3408100ecc3adb343903f9ac6c1119555d6157a`
Verified workflow: run 148 / `33273410051`

## What is now verified

Identity-bound Saved display metadata has its own deterministic schema-1 codec, separate from the authoritative saved-exact-preference document.

The codec persists only:
- stable shopping item/store keys;
- the exact `ProductionProductEvidenceKey` or exact store scope the label was created for;
- the human-facing display name;
- presentation-only metadata basis.

It does not persist price, availability, promotion, travel, freshness, ranking, current-price authorization, provider networking state, or source records.

Encoding is deterministic by stable key. Text is UTF-8 encoded and hex-wrapped in an ASCII representation so record delimiters cannot collide with field content.

Decoding is bounded before parsing and fails closed for oversized input, malformed/truncated fields, invalid headers, unsupported schema versions, invalid enum values, duplicate stable keys, and invalid reconstructed domain objects. No partial snapshot is returned on failure.

Decoded display metadata is still detached presentation data. It must be passed through `PracticalShoppingSavedExactPreferenceDisplayMetadataBinder` against the current exact saved state before projection; the codec does not decide stale-vs-current identity binding.

## Verification

Workflow 148 completed successfully through browser tests/package, Android JVM tests, lint/APK, JVM summary, Android privacy verification, release/checksum assembly, artifact upload, and post-job cleanup.

## Next safe slice

Introduce a separate app-internal AtomicFile store for this display-metadata codec. Keep the display file independent from the saved-exact-preference file. Bound raw reads before decode, preserve the previous generation on failed replacement, distinguish missing/corrupt/oversized/I/O states, block selective mutation of corrupt metadata, and keep clear-all as a safe recovery path. Do not claim cross-file atomicity: stale/orphan display metadata must remain harmless because the binder revalidates exact identity before presentation.
