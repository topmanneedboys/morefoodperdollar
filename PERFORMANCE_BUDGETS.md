# Performance Budgets

Updated: 2026-08-20

These budgets protect the Android consumer experience. JVM measurements are regression signals, not substitutes for frame timing on a physical device.

## Runtime budgets

| Area | Budget | Enforcement |
|---|---:|---|
| Accessibility tree capture | One full pass; no ancestor subtree rescans | `ScanMetrics.fullTreePasses == 1`, architecture fixture |
| Ancestor subtree traversal | 0 repeated traversals | `ScanMetrics.ancestorSubtreeTraversals == 0` |
| Main-thread capture | Stop after 5,000 nodes or a 30 ms elapsed deadline after the first 256 nodes | `NodeScanner` hard bound |
| Duplicate content event | Ignore identical empty-text content signatures for 900 ms | Service event gate |
| Pending scans | At most one scheduled scan plus one follow-up flag | Service coalescer |
| Product capacity | 1,000 stored items; milestone fixtures through 500 | Incremental store |
| Unchanged 500-card batch | Less than 5 ms on the JVM fixture | Unit performance test |
| Rank/filter 500 items | Less than 50 ms on the JVM fixture; hard test ceiling 1,500 ms | Unit performance test |
| Full one-time parse of 500 synthetic cards | Less than 2,000 ms on the JVM fixture; hard test ceiling 8,000 ms | Unit performance test |
| Normal UI update | No row mass redraw; only visible RecyclerView holders bind | Code invariant + lint/build |
| Physical-device frame health | No sustained shopping-app lag; target less than 5% slow/frozen frames while collecting 100–500 products | Pending Motorola/physical-device benchmark |

## Before/after scanner work evidence

The regression harness mirrors the v101 algorithm (one BFS plus up to seven capped ancestor-subtree walks per price node) and the v101.1 algorithm (one tree snapshot plus one gather per distinct card).

| Products | v101 synthetic node visits | v101.1 snapshot visits | Work reduction |
|---:|---:|---:|---:|
| 20 | 3,422 | 162 | 21.12× |
| 60 | 12,482 | 482 | 25.90× |
| 100 | 20,802 | 802 | 25.94× |
| 160 | 33,282 | 1,282 | 25.96× |
| 250 | 52,002 | 2,002 | 25.98× |
| 500 | 104,002 | 4,002 | 25.99× |

The old UI also destroyed and recreated up to 60 rows on every update. v101.1 submits immutable ranked lists to `ListAdapter`; DiffUtil computes changes and RecyclerView creates only visible holders.

## Latest JVM fixture

Measured during the clean 2026-08-20 build on the provided Linux runner:

| Products | Parse changed cards | Apply to store | Filter + rank | Reject unchanged batch |
|---:|---:|---:|---:|---:|
| 20 | 49.228 ms | 4.890 ms | 14.789 ms | 0.124 ms |
| 60 | 105.569 ms | 15.522 ms | 19.983 ms | 0.145 ms |
| 100 | 85.949 ms | 7.830 ms | 8.761 ms | 0.105 ms |
| 160 | 137.433 ms | 12.559 ms | 12.520 ms | 0.219 ms |
| 250 | 186.507 ms | 9.708 ms | 11.189 ms | 0.200 ms |
| 500 | 221.386 ms | 17.102 ms | 20.427 ms | 0.378 ms |

Parsing and ranking run off the main thread. Timing varies with JIT, hardware, and local-model initialization; compare trends and budgets, not isolated sub-millisecond differences.

## Physical-device protocol

1. Install the current testing APK on the Motorola Edge 2025.
2. Enable ValuePilot and Android Developer Options → Profile HWUI rendering or collect a Perfetto/System Trace.
3. Exercise Walmart and Uber Eats at approximately 20, 60, 100, 160, 250, and 500 collected products.
4. Record capture time from advanced status, slow/frozen frames, CPU, memory, event rate, collected count, and interaction latency.
5. Change `bananas` → `eggs` and `milk` searches while the list is populated; confirm immediate invalidation.
6. Open visible and off-screen exact rows; confirm ambiguous/stale cases do not click.
7. Add the measured physical-device evidence here before declaring the milestone complete.
