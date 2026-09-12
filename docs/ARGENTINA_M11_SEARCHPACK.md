# Argentina M11 SearchPack v2

SearchPack v2 is a derived, immutable search read-model for an already
qualified Argentina routing release. It does not replace routing,
micro-partition, or offer-evidence objects and it does not change the Android
application. The builder reads only each regional `search-index.jsonl.gz`
object from the existing content-addressed workspace; it never opens the
official SEPA ZIP.

The release-specific files are:

- a block-addressed lexical FST and exact product-key FST;
- ordinary bounded feature postings for the conservative input horizon;
- a fielded positional ranking index (name positions, aliases, brands and
  context exclusions) that preserves the authoritative Python scorer's
  semantics without fetching candidate records;
- independently compressed docstore blocks with exact hashes; and
- self-describing metadata carrying the source object hash, vocabulary/policy
  version, stable document ordering and every block digest.

Python remains the authority for Spanish normalization, aliases, typo policy,
quantity/form safety, context exclusions and ranking semantics. SearchPack
ranking reads only the term/posting ranges needed for a query and materializes
at most five full records per unique query (plus the deterministic union bound)
from the docstore. The old candidate-sized `lookup_features` path is retained
for input interpretation: if any feature document frequency exceeds its
explicit horizon, it returns `NEEDS_CLARIFICATION` before reading postings or
the docstore. Unknown and saturated input never becomes an arbitrary product.

All regions of a manager share one byte-bounded cache (64 MiB by default).
The cache key includes the immutable descriptor hash, so objects from separate
regions or releases cannot collide. A manifest that declares SearchPack but
has missing, incompatible or corrupt v2 objects fails closed and never
silently falls back to a corpus scan. v1 artifacts are not read by the v2
manager; they require an explicit older runtime.

## Local derivation

```text
python -m tools.build_argentina_searchpack \
  --source-workspace F:\\ValuePilot-M11-routing-derived-YYYYMMDD\\workspace \
  --output-workspace F:\\ValuePilot-M11-searchpack-derived-YYYYMMDD\\workspace \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1-search-v1
```

The output is a new workspace with no active pointer. Existing source objects
are hard-linked and remain immutable. The generated workspace and any full
regional packs are local qualification inputs and must not be committed.

## Runtime diagnostics

```text
python -m tools.measure_argentina_searchpack \
  --workspace F:\\ValuePilot-M11-searchpack-derived-YYYYMMDD\\workspace \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1-search-v1-search-v2
```

The diagnostic records index-native cold/warm top-k queries, candidate IDs
considered, full records materialized, posting/docstore bytes, remote-shaped
HEAD/range counts, the `arroz` metadata-only saturation path, and 5/10-line
internal exact-key request timings. `corpusScanned` is always zero for a
SearchPack-enabled release. Resident-memory fields are reported as unavailable
when the local `psutil` probe is not installed. This is engineering evidence,
not a launch claim about inventory or universal product coverage.
