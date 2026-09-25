# Project Decisions

Updated: 2026-08-20

1. Search state is a first-class `SearchContext` keyed by platform/package/store/query/page fingerprints and a generated session ID. Product state never crosses session IDs.
2. The scanner trades completeness for bounded main-thread work: one pass, 5,000-node cap, 30 ms elapsed deadline after 256 nodes, immutable snapshots, and later background parsing.
3. Normal updates use RecyclerView/ListAdapter/DiffUtil stable IDs. View-tree mass redraw is not permitted for result updates.
4. Accessibility events use a recent-signature gate and a single pending scan. Ranking requests are latest-only/coalesced.
5. Navigation is fail-closed and semantic. No coordinate fallback is allowed; ambiguity or stale evidence produces a message and no click.
6. Current/member/previous/regular/sale price semantics are stored separately. Member pricing affects ranking/budget only when explicitly enabled.
7. Exact quantities/calories remain authoritative. Range and local-model-derived metrics are visibly marked Estimate.
8. The consumer surface is one persistent bubble plus a temporary draggable bottom sheet; technical scan/OCR controls live in advanced Filters.
9. Privacy remains local-only. INTERNET and ACCESS_NETWORK_STATE are removed from the merged Android manifest.
10. Version `101.1.0`/code `10101` identifies this Android milestone test build without changing the v101 browser packages.
