"""Read the trusted active Argentina source identity from a runtime GCS store.

This helper is intentionally read-only. It verifies the active pointer and the
checksum-protected active release manifest through the existing acquisition
handoff logic, then emits only the active release ID, original official source
date, and source SHA-256 needed for the next acquisition decision.
"""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.object_store import GCSObjectStore
from tools.argentina_release_acquisition import canonical_bytes
from tools.argentina_release_acquisition_handoff import AcquisitionHandoffError, current_identity_from_store


SCHEMA_VERSION = "valuepilot-argentina-active-source-identity-v1"
MAX_REPORT_BYTES = 32 * 1024


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    raw = canonical_bytes(dict(value)) + b"\n"
    if len(raw) > MAX_REPORT_BYTES:
        raise AcquisitionHandoffError("active source identity report exceeds bound", code="REPORT_TOO_LARGE")
    target = Path(path).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=str(target.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--active-bucket", required=True)
    parser.add_argument("--active-project")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)

    try:
        identity = current_identity_from_store(
            GCSObjectStore(
                bucket=args.active_bucket,
                project=args.active_project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None,
            )
        )
        value = {"schemaVersion": SCHEMA_VERSION, "status": "PASS", **identity}
        _write_json_atomic(args.output, value)
        print(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except AcquisitionHandoffError as exc:
        value = {"schemaVersion": SCHEMA_VERSION, "status": "FAILED", "error": exc.code, "message": str(exc)}
        _write_json_atomic(args.output, value)
        print(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())
