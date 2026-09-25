# Argentina storage-retention fixture

This directory contains only tiny deterministic metadata used by the V1
retention tests. It is not an Argentina SEPA export and contains no provider
data. The tests create content-addressed objects and release manifests in a
temporary workspace, then exercise mark/plan/verify/sweep, grace, pins,
rollback, partial deletion recovery, and budget fail-closed behavior.

The fixture deliberately models four complete releases with one shared object
and one per-release object. It is safe for CI and must never be replaced with
the national SEPA ZIP.
