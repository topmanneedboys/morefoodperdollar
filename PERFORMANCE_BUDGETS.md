# Performance Budgets

## Shared-core boundary

- Calls remain ordinary in-process functions; do not serialize internal observations/products between layers.
- Exact money/quantity values are compact `Long`-backed data classes. Avoid reflection, IPC, platform callbacks, and defensive copying of whole product collections.
- Providers supply explicit timestamps and immutable observations. Shared core performs no I/O, network, logging, model loading, or hidden background work.

Updated: 2026-08-29

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
| Practical Shopping raw production price re-evaluation | At most one raw acceptance/claim evaluation per supplied current-price request per decision invocation; max 128 | `ProductionCurrentPriceEligibilityEvaluator.evaluateAll`, semantic-equivalence regression |
| Practical Shopping raw production requests | At most 128 per decision invocation | Shared-core hard bound |
| Practical Shopping price bindings | At most 128 per decision invocation | Shared-core hard bound |
| Practical Shopping stores | At most 64 per decision invocation | Shared-core hard bound |
| Practical Shopping ordered store pairs | At most 128 per decision invocation | Shared-core hard bound |
| Practical Shopping Android threading | Production evidence-to-decision evaluation must not execute on the Android main/UI thread | Required future Android orchestration invariant; not wired to Home yet |
| Physical-device frame health | No sustained shopping-app lag; target less than 5% slow/frozen frames while collecting 100–500 products | Pending Motorola/physical-device benchmark |

## Practical Shopping production work boundary

The first production Practical Shopping bridge originally re-ran the complete current-price eligibility set for every explicit item/store binding. With the hard bounds of 128 bindings and 128 raw requests, that permitted up to **16,384 raw production request evaluations** inside one shopping decision call.

Commit `2e6a71180738bb1d19be64c1eb850d6730bb139e` changes only the execution shape. `ProductionCurrentPriceEligibilityEvaluator.evaluateAll` now re-runs each raw request once at the supplied decision instant, using the current lifecycle and namespace-disposition registries, then derives every candidate-specific conflict/eligibility result from that same immutable in-call evaluation set.

Therefore:

- before batching: up to `bindings × requests` raw production evaluations = `128 × 128 = 16,384`;
- after batching: at most `requests` raw production evaluations = `128`;
- candidate-specific conflict resolution still occurs independently for each candidate and retains the same authority/freshness/scope semantics;
- the batch result is internal to one invocation and is never a durable authorization token;
- a later decision invocation re-runs raw requests again against the then-current lifecycle/disposition state and supplied evaluation time;
- there is no cross-call cache, stale eligibility reuse, hidden background refresh, network work or clock ownership in shared-core.

`ProductionCurrentPriceEligibilityBatchTest` compares batched candidate results with the original one-candidate evaluator on the same raw same-scope conflicting price set. Blockers, factual resolution, acceptance decision, selected evidence claim and final current-price eligibility must remain equal.

This removes the identified quadratic raw-evaluation path without weakening the trust boundary. It does **not** prove the whole production planning path is cheap enough for Android's UI thread. The eventual Android coordinator must invoke production planning off the main thread and publish an immutable completed render state back to the UI.

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
