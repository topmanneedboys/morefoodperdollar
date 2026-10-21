"""Delete release objects that no live release references.

Run as a scheduled job (Cloud Run job + Cloud Scheduler) once a week.  The
active release and its previous (rollback) release are always kept, so serving
and rollback keep working while everything older is reclaimed.

Usage:
    python tools/prune_runtime_releases.py <bucket> [--dry-run]
"""
from __future__ import annotations

import argparse
import json

from google.cloud import storage

KEEP_KEYS = ("releaseId", "previousReleaseId", "lastKnownGoodReleaseId")


def referenced_shas(bucket: storage.Bucket, release_ids: list[str]) -> set[str]:
    referenced: set[str] = set()
    for release_id in release_ids:
        blob = bucket.blob(f"releases/{release_id}/manifest.json")
        if not blob.exists():
            continue
        manifest = json.loads(blob.download_as_bytes())
        for item in manifest.get("objects", []):
            sha = item.get("sha256")
            if sha:
                referenced.add(str(sha))
    return referenced


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("bucket")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    client = storage.Client()
    bucket = client.bucket(args.bucket)

    pointer = bucket.blob("control/active.json")
    if not pointer.exists():
        print(f"{args.bucket}: no active pointer; nothing to do")
        return 0
    active = json.loads(pointer.download_as_bytes())
    release_ids = sorted({str(active[key]) for key in KEEP_KEYS if active.get(key)})
    if not release_ids:
        print(f"{args.bucket}: active pointer has no release ids; refusing to prune")
        return 1

    keep = referenced_shas(bucket, release_ids)
    print(f"{args.bucket}: keeping {len(release_ids)} releases / {len(keep)} objects")

    removed = 0
    for blob in bucket.list_blobs(prefix="objects/sha256/"):
        if blob.name.rsplit("/", 1)[-1] in keep:
            continue
        removed += 1
        if not args.dry_run:
            blob.delete()

    verb = "would delete" if args.dry_run else "deleted"
    print(f"{args.bucket}: {verb} {removed} unreferenced objects")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
