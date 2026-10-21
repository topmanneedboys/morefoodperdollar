# Search performance and reliability — current state

Last updated after the search-hardening pass on the `feat/argentina-search-area`
branch. Every claim below was measured on a Galaxy A15 against the deployed
backend unless marked otherwise.

## What customers experience today

| Scenario | Latency | Result |
| --- | --- | --- |
| Repeat of a query already served at the same coordinates | 1.3–1.5 s | cached response |
| Common grocery term (startup warm set) | ~1.5 s | cached response |
| Cold, uncommon term | ~10 s | computed, then cached |
| Deep page (page 2+) | ~10 s | computed |

No search errors were observed in the final audits. The 12-second edge timeout
that produced intermittent 504s was removed.

## Fixes in place

1. **Clarification labels.** Taxonomy choices emitted `labelEs` while the
   Android contract required `label`; every query that triggered a
   clarification failed the whole page. Both builders now emit `label`, and a
   malformed clarification degrades to "no clarification" instead of failing.
2. **Row isolation.** A malformed product row or qualified offer is dropped;
   the rest of the page still renders. A page where every row is malformed
   still fails closed.
3. **Depth.** 20 products per page, 24 pages, 500 maximum. The 31-row ceiling
   was raised in the SearchPack runtime, the reader, the shard query layer, the
   service paging and the Android contract.
4. **Self-healing.** Release drift (`RELEASE_MISMATCH`, `STALE_RELEASE`,
   `SEARCH_NOT_AUTHORIZED`, `UNAUTHORIZED`) and transient failures (`TIMEOUT`,
   `SERVICE_UNAVAILABLE`, `SERVER_ERROR`, `NETWORK_ERROR`) retry once, silently,
   before the shopper sees an error.
5. **Memory.** The service was OOM-killed at 4 GiB, which surfaced as 504s.
   It now runs with 8 GiB and 2 CPU.
6. **Pack cache.** Raised from 64 MB to 1 GiB, then 2 GiB. Container profiling
   showed 44.7 of 44.6 profiled seconds were GCS range reads.
7. **Region preload.** Each pack file is read once (~10 reads per region) and
   sliced into the block cache, on a background thread when the region opens.
8. **Response cache.** `BackendService` keeps the last 4096 responses, keyed on
   release, query, page, selection and exact coordinates, so distances stay
   correct. A test asserts that a repeated search performs zero reader calls.
9. **Edge timeout.** The consumer edge abandoned searches after 12 s; it now
   waits 30 s, with a 60 s Cloud Run timeout.
10. **English search.** 361 single-word labels, 190 phrase labels, ~120 common
    shopper words, plus singular/plural fallback (`peanut → mani`,
    `diapers → panales`).
11. **Scaling.** Concurrency 20, maximum 10 instances, minimum 1.
12. **Android navigation insets.** The system bar inset is bridged into CSS so
    the tab bar never hides under the gesture or three-button navigation.

## Known limitations

- The response cache is keyed on exact coordinates, so a shopper whose position
  differs from the warm-up point misses it. This is why cold terms are ~10 s.
- Cold, uncommon queries still cost about 10 s; the target is under 2 s.
- Approximately 10% of the catalogue is unclassified and has no English label.
- The classification (640+ families) is not yet surfaced as filters or
  clarifications in the UI.

## Recommended next steps, in order

1. **Cache the ranked product list per region + query** (not per coordinate) and
   compute distances per request. This makes every query fast for every
   shopper and removes the coordinate sensitivity.
2. **Precompute the top few thousand queries at each daily build** and serve
   them as a lookup.
3. **Surface the classification**: filter chips for varieties, and a question
   only for genuine two-meaning collisions (crema, agua, dulce, polvo).
4. **Cached fallback in the app**: if a live search is slow, show the last
   successful result for that query instead of an error.

## Verification commands

```bash
cd android && ./gradlew --no-daemon :app:testDebugUnitTest
cd .. && PYTHONPATH=. python -m unittest discover -s backend/tests -p 'test_*.py'
```
