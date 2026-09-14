"""Materialize a verified source-archive handoff before running the release worker.

The mature release worker has an intentional metadata-only NO_CHANGE shortcut
for its normal direct-acquisition path.  An explicit verified archive handoff
must not be suppressed by stale CKAN metadata, so this thin entrypoint restores
the content-addressed ZIP first and invokes the existing worker with
``--source``.  All qualification, build, promotion, and pointer rules remain
owned by ``argentina_release_worker``.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path
from typing import Callable, MutableMapping, Sequence

from backend.object_store import LocalFilesystemObjectStore, ObjectStore
from tools.argentina_release_acquisition import AcquisitionError, PrivateSourceArchive
from tools.argentina_release_worker import _main as release_worker_main


class HandoffWorkerError(RuntimeError):
    def __init__(self, message: str, *, code: str = "HANDOFF_WORKER_FAILED") -> None:
        super().__init__(message)
        self.code = code


def _strip_option(argv: Sequence[str], name: str) -> list[str]:
    result: list[str] = []
    index = 0
    prefix = f"{name}="
    while index < len(argv):
        value = str(argv[index])
        if value.startswith(prefix):
            index += 1
            continue
        if value == name:
            index += 2
            continue
        result.append(value)
        index += 1
    return result


def run_handoff_worker(
    argv: Sequence[str] | None = None,
    *,
    worker_main: Callable[[Sequence[str] | None], int] = release_worker_main,
    environ: MutableMapping[str, str] | None = None,
) -> int:
    values = list(argv if argv is not None else sys.argv[1:])
    environment = os.environ if environ is None else environ
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--archive-store", type=Path)
    parser.add_argument("--archive-bucket")
    parser.add_argument("--archive-project")
    parser.add_argument("--source", type=Path)
    parser.add_argument("--source-archive-key")
    parser.add_argument("--source-archive-sha256")
    known, _ = parser.parse_known_args(values)

    archive_key = known.source_archive_key or environment.get("VALUEPILOT_SOURCE_ARCHIVE_KEY") or None
    archive_sha256 = known.source_archive_sha256 or environment.get("VALUEPILOT_SOURCE_ARCHIVE_SHA256") or None
    if archive_key is None and archive_sha256 is None:
        return worker_main(values)
    if not archive_key or not archive_sha256:
        raise HandoffWorkerError("source archive key and hash are both required", code="INVALID_STATE")
    if known.source is not None:
        raise HandoffWorkerError("source and source archive restore are mutually exclusive", code="INVALID_STATE")
    if bool(known.archive_store) == bool(known.archive_bucket):
        raise HandoffWorkerError("exactly one archive store is required", code="INVALID_STATE")

    if known.archive_bucket:
        from backend.object_store import GCSObjectStore

        store: ObjectStore = GCSObjectStore(
            bucket=known.archive_bucket,
            project=known.archive_project or environment.get("GOOGLE_CLOUD_PROJECT") or None,
        )
    else:
        store = LocalFilesystemObjectStore(known.archive_store)

    transformed = _strip_option(values, "--source-archive-key")
    transformed = _strip_option(transformed, "--source-archive-sha256")
    previous_key = environment.pop("VALUEPILOT_SOURCE_ARCHIVE_KEY", None)
    previous_sha = environment.pop("VALUEPILOT_SOURCE_ARCHIVE_SHA256", None)
    try:
        with tempfile.TemporaryDirectory(prefix="valuepilot-sepa-handoff-worker-") as temporary:
            source = PrivateSourceArchive(store).restore_file(
                archive_key,
                Path(temporary) / "source.zip",
                expected_sha256=archive_sha256,
            )
            transformed.extend(["--source", str(source)])
            return worker_main(transformed)
    finally:
        if previous_key is not None:
            environment["VALUEPILOT_SOURCE_ARCHIVE_KEY"] = previous_key
        if previous_sha is not None:
            environment["VALUEPILOT_SOURCE_ARCHIVE_SHA256"] = previous_sha


def _main(argv: Sequence[str] | None = None) -> int:
    try:
        return run_handoff_worker(argv)
    except (AcquisitionError, HandoffWorkerError) as exc:
        print(
            json.dumps(
                {"error": getattr(exc, "code", "HANDOFF_WORKER_FAILED"), "message": str(exc)},
                ensure_ascii=False,
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["HandoffWorkerError", "run_handoff_worker"]
