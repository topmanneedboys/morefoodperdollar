# Argentina M11 SearchPack v1

SearchPack v1 is a derived, immutable search read-model for the already
qualified Argentina routing releases. It does not replace the routing,
micro-partition, or offer evidence objects and it does not change the Android
application. The builder reads only each regional `search-index.jsonl.gz`
object from the existing content-addressed workspace; it never opens the
official SEPA ZIP.

The release-specific files are:

- a block-addressed feature lexicon and exact product-key lexicon;
- sorted delta-varint postings with a bounded chunk chain;
- independently compressed docstore blocks with exact hashes;
- self-describing metadata carrying the source object hash, vocabulary/policy
  version, stable document ordering, and every block digest.

Python remains the authority for Spanish normalization, aliases, typo policy,
quantity/form safety, and the existing scorer. The optional Rust extension
only implements the immutable FST, postings, zstd, and checksum primitives.
When a posting's document frequency exceeds the 256-item semantic horizon,
the input interpreter returns `NEEDS_CLARIFICATION` with no arbitrary product.
The old bounded gzip scan remains available only for manifests that predate
SearchPack. A manifest that declares SearchPack but has missing or corrupt
objects fails closed and never silently falls back to scanning.

## Local derivation

```text
python -m tools.build_argentina_searchpack \
  --source-workspace F:\\ValuePilot-M11-routing-derived-YYYYMMDD\\workspace \
  --output-workspace F:\\ValuePilot-M11-searchpack-derived-YYYYMMDD\\workspace \
  --release-id argentina-sepa-2026-09-06-e6c08be6a36e5e5b9-routing-v1 \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1
```

The output is a new workspace with no active pointer. Existing source objects
are hard-linked and remain immutable. The generated workspace and any full
regional packs are local qualification inputs and must not be committed.

## Runtime diagnostics

The bounded local diagnostic measures cold/warm feature lookups, one selective
full-name query, and 1/5/10-line reader requests without a source-wide
verification pass:

```text
python -m tools.measure_argentina_searchpack \
  --workspace F:\\ValuePilot-M11-searchpack-derived-YYYYMMDD\\workspace \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1-search-v1
```

Its `corpusScanned`/`searchPackCorpusScanned` fields are always zero for a
SearchPack-enabled release.  Optional resident-memory fields are reported as
unavailable when the local `psutil` probe is not installed; that does not turn
into a hidden remeasurement or a source verification run.
The script is evidence for local engineering decisions, not a launch claim
about inventory or universal product coverage.
