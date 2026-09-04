#!/usr/bin/env python3
"""Build and optionally promote the Canada-first offline catalog snapshots.

This coordinator is deliberately offline.  A caller supplies an Open Food
Facts export, an explicit rights manifest, explicit acquisition/generation
timestamps, and a signing key.  The existing selector, builder, verifier and
promotion boundary remain the authorities; this module only wires them into a
repeatable weekly run for the supported Greater Toronto Area and Metro
Vancouver identity snapshots.

No network request, price, package quantity, availability, store claim or
hidden wall clock is introduced here.  Without ``--promote`` the run only
creates verified candidates in a new empty output directory.  With
``--promote`` that output directory is also the per-region promotion state
root, and each region's pointers are changed only after every requested
candidate has been built and signature-verified.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from tools.build_offline_catalog_snapshot import (
    NAMESPACE_RE,
    NO_CURRENT_OFFERS_STATUS,
    SnapshotBuildError,
    _canonical_json,
    _parse_timestamp,
    build_snapshot,
)
from tools.promote_offline_catalog_snapshot import (
    MAX_CATALOG_RECORDS,
    MIN_CATALOG_RECORDS,
    SUPPORTED_METRO_REGIONS,
    SnapshotPromotionError,
    promote_snapshot,
)
from tools.import_open_food_facts_catalog import import_catalog
from tools.select_open_food_facts_launch_catalog import select_catalog
from tools.verify_offline_catalog_snapshot import verify_snapshot


DEFAULT_REGIONS = tuple(sorted(SUPPORTED_METRO_REGIONS))
DEFAULT_MAX_RECORDS = 5_000
COVERAGE_REPORT_NAME = "coverage-report.json"


class OfflineCatalogRefreshError(ValueError):
    """A refresh run cannot proceed without an explicit safe input."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise OfflineCatalogRefreshError(message)


def _iso_utc(epoch_millis: int) -> str:
    """Return one canonical ISO-8601 representation for generated configs."""

    return (
        (datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(milliseconds=epoch_millis))
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def _regions(values: Iterable[str] | None) -> tuple[str, ...]:
    selected = tuple(sorted(set(values or DEFAULT_REGIONS)))
    _require(selected, "At least one metro region is required")
    _require(
        all(region in SUPPORTED_METRO_REGIONS for region in selected),
        "Only the supported Canada-first metro regions may be refreshed",
    )
    return selected


def _safe_snapshot_component(value: str, field: str) -> str:
    value = value.strip()
    _require(bool(value) and NAMESPACE_RE.fullmatch(value) is not None, f"{field} has invalid form")
    return value


def _new_output_root(output_root: Path, *, promote: bool) -> Path:
    resolved = output_root.resolve()
    if resolved.exists():
        _require(resolved.is_dir(), f"Output root must be a directory: {resolved}")
        if not promote:
            _require(
                not any(resolved.iterdir()),
                f"Non-promoting refresh output must be missing or empty: {resolved}",
            )
    else:
        resolved.mkdir(parents=True, exist_ok=True)
    return resolved


def _write_config(
    path: Path,
    *,
    region_id: str,
    snapshot_id: str,
    generated_at: str,
    rights_manifest: Path,
    catalog: Path,
    source_snapshot_id: str,
    acquired_at: str,
    source_published_at: str | None,
) -> None:
    source: dict[str, Any] = {
        "rights_manifest": str(rights_manifest.resolve()),
        "catalog": str(catalog.resolve()),
        "snapshot_id": source_snapshot_id,
        "acquired_at": acquired_at,
    }
    if source_published_at is not None:
        source["source_published_at"] = source_published_at
    config = {
        "region_id": region_id,
        "snapshot_id": snapshot_id,
        "generated_at": generated_at,
        "sources": [source],
    }
    path.write_bytes(_canonical_json(config))


def _write_json_atomically(path: Path, payload: Mapping[str, Any]) -> None:
    """Write a diagnostic report without exposing a partially written file."""

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            handle.write(_canonical_json(payload))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def _coverage_report(
    *,
    generated_epoch: int,
    evaluated_epoch: int,
    source_snapshot_id: str,
    selected_count: int,
    minimum_catalog_records: int,
    maximum_catalog_records: int,
    selection_coverage: Mapping[str, Any],
    regions: Sequence[Mapping[str, Any]],
    promoted: bool,
) -> dict[str, Any]:
    """Build a non-authoritative, deterministic coverage summary.

    The signed regional manifests remain the authority.  This report exists so
    a weekly operator run can inspect identity coverage and current-offer
    coverage as two separate measurements without reading or inferring facts
    from display data.
    """

    return {
        "schemaVersion": 1,
        "reportType": "OFFLINE_CATALOG_COVERAGE",
        "generatedAtEpochMillis": generated_epoch,
        "evaluatedAtEpochMillis": evaluated_epoch,
        "sourceSnapshotId": source_snapshot_id,
        "catalog": {
            "coverageStatus": "MEASURED",
            "recordCount": selected_count,
            "minimumRecordCount": minimum_catalog_records,
            "maximumRecordCount": maximum_catalog_records,
            "selection": {
                "selectedGroceryHintRecords": selection_coverage["selectedGroceryHintRecords"],
                "selectedHouseholdHintRecords": selection_coverage["selectedHouseholdHintRecords"],
                "householdReserveTarget": selection_coverage["householdReserveTarget"],
                "householdReserveSatisfied": selection_coverage["householdReserveSatisfied"],
                "uniqueCanonicalIdentityNames": selection_coverage["uniqueCanonicalIdentityNames"],
                "identityNameVarietyStatus": selection_coverage["identityNameVarietyStatus"],
                "identityNameVarietyTarget": selection_coverage["identityNameVarietyTarget"],
                "authority": "NONE",
                "note": (
                    "Selection hints and canonical-name variety are bounded identity measurements, "
                    "not demand, retailer availability, stock, price, package quantity, freshness, "
                    "or ranking authority."
                ),
            },
        },
        "currentOffers": {
            "coverageStatus": NO_CURRENT_OFFERS_STATUS,
            "recordCount": 0,
            "authority": "NONE",
        },
        "regions": [
            {
                "regionId": item["regionId"],
                "snapshotId": item["snapshotId"],
                "manifestSha256": item["manifestSha256"],
                "catalogRecordCount": item["catalogRecordCount"],
                "currentOfferRecordCount": item["currentOfferRecordCount"],
                "currentOfferCoverage": item["currentOfferCoverage"],
            }
            for item in sorted(regions, key=lambda value: str(value["regionId"]))
        ],
        "promoted": promoted,
        "authorityNote": (
            "Diagnostic summary only; signed regional manifest verification and "
            "promotion pointers remain authoritative. Identity coverage does not "
            "claim price, package quantity, stock, availability, store scope or freshness."
        ),
    }


def refresh_snapshots(
    input_path: Path,
    output_root: Path,
    rights_manifest: Path,
    private_key: Path,
    public_key: Path,
    *,
    generated_at: str,
    acquired_at: str,
    evaluated_at: str,
    maximum_snapshot_age_millis: int,
    source_snapshot_id: str,
    source_published_at: str | None = None,
    regions: Sequence[str] | None = None,
    max_records: int = DEFAULT_MAX_RECORDS,
    minimum_catalog_records: int = MIN_CATALOG_RECORDS,
    maximum_catalog_records: int = MAX_CATALOG_RECORDS,
    promote: bool = False,
) -> dict[str, Any]:
    """Run one deterministic multi-region selection/build/verify/promotion.

    All timestamps are caller-owned.  The function never reads the current
    time and refuses to overwrite a non-promoting output directory or an
    existing candidate snapshot directory.
    """

    input_path = input_path.resolve()
    rights_manifest = rights_manifest.resolve()
    private_key = private_key.resolve()
    public_key = public_key.resolve()
    _require(input_path.is_file(), f"Missing catalog export: {input_path}")
    _require(rights_manifest.is_file(), f"Missing rights manifest: {rights_manifest}")
    _require(private_key.is_file(), f"Missing signing private key: {private_key}")
    _require(public_key.is_file(), f"Missing signature public key: {public_key}")
    _require(maximum_snapshot_age_millis > 0, "maximum_snapshot_age_millis must be positive")
    _require(1 <= max_records <= maximum_catalog_records, "max_records is outside the catalog bound")
    _require(
        1 <= minimum_catalog_records <= maximum_catalog_records,
        "Catalog coverage bounds are invalid",
    )
    source_snapshot_id = _safe_snapshot_component(source_snapshot_id, "source_snapshot_id")
    region_ids = _regions(regions)

    try:
        generated_epoch = _parse_timestamp(generated_at, "generated_at")
        acquired_epoch = _parse_timestamp(acquired_at, "acquired_at")
        evaluated_epoch = _parse_timestamp(evaluated_at, "evaluated_at")
        published_epoch = (
            _parse_timestamp(source_published_at, "source_published_at")
            if source_published_at is not None
            else None
        )
    except SnapshotBuildError as exc:
        raise OfflineCatalogRefreshError(str(exc)) from exc

    _require(acquired_epoch <= generated_epoch, "Source acquisition must not be after generation")
    if published_epoch is not None:
        _require(published_epoch <= acquired_epoch, "Source publication must not be after acquisition")
    _require(evaluated_epoch >= generated_epoch, "Evaluation must not be before generation")
    _require(
        evaluated_epoch - generated_epoch <= maximum_snapshot_age_millis,
        "Generated snapshot is outside the configured freshness window",
    )

    generated_iso = _iso_utc(generated_epoch)
    acquired_iso = _iso_utc(acquired_epoch)
    published_iso = _iso_utc(published_epoch) if published_epoch is not None else None
    output_root = _new_output_root(output_root, promote=promote)
    run_component = str(generated_epoch)
    selected_count: int | None = None
    verified: list[dict[str, Any]] = []

    # The stage directory is removed after the run.  Only the signed candidate
    # directories and optional promotion pointers remain durable.
    with tempfile.TemporaryDirectory(prefix=".offline-catalog-refresh-", dir=output_root) as stage_name:
        stage = Path(stage_name)
        selected_raw_catalog = stage / "selected-raw.jsonl"
        selected_catalog = stage / "selected.jsonl"
        selection_report_path = stage / "selection-report.json"
        try:
            selection_report = select_catalog(
                input_path,
                selected_raw_catalog,
                selection_report_path,
                max_records=max_records,
            )
        except (OSError, ValueError) as exc:
            raise OfflineCatalogRefreshError(f"Catalog selection failed: {exc}") from exc

        metrics = selection_report.get("metrics")
        _require(isinstance(metrics, Mapping), "Selection report metrics are missing")
        selection_coverage = selection_report.get("coverage")
        _require(isinstance(selection_coverage, Mapping), "Selection report coverage is missing")
        for field in (
            "selectedGroceryHintRecords",
            "selectedHouseholdHintRecords",
            "householdReserveTarget",
            "uniqueCanonicalIdentityNames",
        ):
            _require(isinstance(selection_coverage.get(field), int), f"Selection report {field} is invalid")
        _require(
            isinstance(selection_coverage.get("householdReserveSatisfied"), bool),
            "Selection report householdReserveSatisfied is invalid",
        )
        _require(
            selection_coverage.get("identityNameVarietyStatus") in {"WITHIN_TARGET", "OUTSIDE_TARGET"},
            "Selection report identityNameVarietyStatus is invalid",
        )
        target = selection_coverage.get("identityNameVarietyTarget")
        _require(isinstance(target, Mapping), "Selection report identityNameVarietyTarget is invalid")
        _require(
            isinstance(target.get("minimum"), int) and isinstance(target.get("maximum"), int),
            "Selection report identityNameVarietyTarget bounds are invalid",
        )
        selected_value = metrics.get("records_selected")
        _require(isinstance(selected_value, int), "Selection report selected count is invalid")
        selected_count = selected_value
        _require(
            minimum_catalog_records <= selected_count <= maximum_catalog_records,
            f"Selected catalog coverage must be between {minimum_catalog_records} and {maximum_catalog_records} records",
        )
        _require(
            selection_coverage.get("householdReserveSatisfied") is True,
            "Selection report household reserve requirement is not satisfied",
        )
        _require(
            selection_coverage.get("identityNameVarietyStatus") == "WITHIN_TARGET",
            "Selection report identity-name variety is outside the launch target",
        )
        try:
            imported = import_catalog(
                selected_raw_catalog,
                selected_catalog,
                dataset_namespace_id="off-ca",
                max_records=max_records,
            )
        except (OSError, ValueError) as exc:
            raise OfflineCatalogRefreshError(f"Catalog identity import failed: {exc}") from exc
        _require(
            imported.get("records") == selected_count,
            "Imported catalog count does not match deterministic selection",
        )

        for region_id in region_ids:
            snapshot_id = f"{region_id}-off-{run_component}"
            candidate_dir = output_root / region_id / "candidates" / snapshot_id
            _require(
                not candidate_dir.exists(),
                f"Refusing to overwrite existing candidate snapshot: {candidate_dir}",
            )
            staged_candidate_dir = stage / "candidates" / region_id / snapshot_id
            staged_candidate_dir.parent.mkdir(parents=True, exist_ok=True)
            config_path = stage / f"{region_id}.json"
            _write_config(
                config_path,
                region_id=region_id,
                snapshot_id=snapshot_id,
                generated_at=generated_iso,
                rights_manifest=rights_manifest,
                catalog=selected_catalog,
                source_snapshot_id=source_snapshot_id,
                acquired_at=acquired_iso,
                source_published_at=published_iso,
            )
            try:
                manifest = build_snapshot(
                    config_path,
                    staged_candidate_dir,
                    private_key=private_key,
                    require_signature=True,
                )
                verification = verify_snapshot(
                    staged_candidate_dir,
                    public_key=public_key,
                    require_signature=True,
                )
            except (SnapshotBuildError, OSError) as exc:
                raise OfflineCatalogRefreshError(
                    f"Candidate verification failed for {region_id}: {exc}"
                ) from exc
            _require(
                manifest.get("coverage", {}).get("catalogRecordCount") == selected_count,
                f"Candidate coverage mismatch for {region_id}",
            )
            candidate_coverage = manifest.get("coverage")
            _require(isinstance(candidate_coverage, Mapping), f"Candidate coverage metadata missing for {region_id}")
            current_offer_count = candidate_coverage.get("currentOfferRecordCount")
            current_offer_coverage = candidate_coverage.get("currentOfferCoverage")
            _require(
                current_offer_count == 0 and current_offer_coverage == NO_CURRENT_OFFERS_STATUS,
                f"Identity-only candidate current-offer coverage is invalid for {region_id}",
            )
            verified.append(
                {
                    "regionId": region_id,
                    "snapshotId": snapshot_id,
                    "catalogRecordCount": selected_count,
                    "currentOfferRecordCount": current_offer_count,
                    "currentOfferCoverage": current_offer_coverage,
                    "manifestSha256": verification["manifestSha256"],
                    "candidatePath": candidate_dir.as_posix(),
                    "_stagedCandidatePath": staged_candidate_dir.as_posix(),
                }
            )

        # Move only fully built and signature-verified candidates into the
        # durable root.  A malformed source therefore cannot leave a partially
        # written candidate that looks ready for a later promotion attempt.
        for item in verified:
            staged = Path(item.pop("_stagedCandidatePath"))
            durable = Path(item["candidatePath"])
            try:
                durable.parent.mkdir(parents=True, exist_ok=True)
                os.replace(staged, durable)
            except OSError as exc:
                raise OfflineCatalogRefreshError(
                    f"Unable to stage verified candidate for {item['regionId']}: {exc}"
                ) from exc

        if promote:
            for item in verified:
                region_id = item["regionId"]
                try:
                    promotion = promote_snapshot(
                        output_root / region_id / "candidates" / item["snapshotId"],
                        output_root / region_id,
                        public_key,
                        evaluated_at_epoch_millis=evaluated_epoch,
                        maximum_snapshot_age_millis=maximum_snapshot_age_millis,
                        expected_region_id=region_id,
                        minimum_catalog_records=minimum_catalog_records,
                        maximum_catalog_records=maximum_catalog_records,
                    )
                except (SnapshotPromotionError, SnapshotBuildError, OSError) as exc:
                    raise OfflineCatalogRefreshError(
                        f"Snapshot promotion failed for {region_id}: {exc}"
                    ) from exc
                item["promotion"] = promotion

        _write_json_atomically(
            output_root / COVERAGE_REPORT_NAME,
            _coverage_report(
                generated_epoch=generated_epoch,
                evaluated_epoch=evaluated_epoch,
                source_snapshot_id=source_snapshot_id,
                selected_count=selected_count,
                minimum_catalog_records=minimum_catalog_records,
                maximum_catalog_records=maximum_catalog_records,
                selection_coverage=selection_coverage,
                regions=verified,
                promoted=promote,
            ),
        )

    return {
        "generatedAtEpochMillis": generated_epoch,
        "evaluatedAtEpochMillis": evaluated_epoch,
        "catalogRecordCount": selected_count,
        "coverageReportPath": (output_root / COVERAGE_REPORT_NAME).as_posix(),
        "coverage": {
            "catalogRecordCount": selected_count,
            "currentOfferRecordCount": 0,
            "currentOfferCoverage": NO_CURRENT_OFFERS_STATUS,
        },
        "regions": verified,
        "promoted": promote,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--rights-manifest", required=True, type=Path)
    parser.add_argument("--private-key", required=True, type=Path)
    parser.add_argument("--public-key", required=True, type=Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--acquired-at", required=True)
    parser.add_argument("--evaluated-at", required=True)
    parser.add_argument("--maximum-age-millis", required=True, type=int)
    parser.add_argument("--source-snapshot-id", required=True)
    parser.add_argument("--source-published-at")
    parser.add_argument("--region", action="append", dest="regions")
    parser.add_argument("--max-records", type=int, default=DEFAULT_MAX_RECORDS)
    parser.add_argument("--minimum-records", type=int, default=MIN_CATALOG_RECORDS)
    parser.add_argument("--maximum-records", type=int, default=MAX_CATALOG_RECORDS)
    parser.add_argument(
        "--promote",
        action="store_true",
        help="After all regions verify, update each region's current and last-known-good pointers",
    )
    args = parser.parse_args(argv)
    try:
        result = refresh_snapshots(
            args.input,
            args.output_root,
            args.rights_manifest,
            args.private_key,
            args.public_key,
            generated_at=args.generated_at,
            acquired_at=args.acquired_at,
            evaluated_at=args.evaluated_at,
            maximum_snapshot_age_millis=args.maximum_age_millis,
            source_snapshot_id=args.source_snapshot_id,
            source_published_at=args.source_published_at,
            regions=args.regions,
            max_records=args.max_records,
            minimum_catalog_records=args.minimum_records,
            maximum_catalog_records=args.maximum_records,
            promote=args.promote,
        )
    except (OfflineCatalogRefreshError, OSError) as exc:
        print(f"offline catalog refresh failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
