#!/usr/bin/env python3
"""Audit product identity coverage from an authorized Argentina SEPA ZIP.

The official SEPA source is an outer ZIP containing one retailer ZIP per
package.  This tool reads only ``productos.csv`` from those packages and
builds a bounded, identity-only view keyed by ``(id_comercio, id_producto)``.
It never reads or writes prices, store availability, credentials, release
pointers, or production objects.

The resulting identity file is deliberately a derived local artifact.  It is
safe to use as input to ``product_identity_lexicon.py`` while the provider
source itself remains outside Git.  Classification is conservative: reviewed
family rules are applied to observed source names, and every unclassified or
conflicting identity stays visible in the report for later review.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
import re
import sys
import zipfile
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

try:
    from tools.product_identity_lexicon import derive_identity_record
except ModuleNotFoundError:  # direct invocation from tools/
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from tools.product_identity_lexicon import derive_identity_record


SCHEMA_VERSION = "valuepilot-argentina-product-identity-coverage-v1"
POLICY_VERSION = "valuepilot-argentina-product-identity-coverage-policy-v1"
MAX_NAME_LENGTH = 512
MAX_VARIANT_SAMPLES = 8
MAX_ALIAS_RECORDS = 256
_SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")
_REQUIRED_COLUMNS = (
    "id_comercio",
    "id_producto",
    "productos_ean",
    "productos_descripcion",
    "productos_cantidad_presentacion",
    "productos_unidad_medida_presentacion",
    "productos_marca",
)


class ProductIdentityAuditError(RuntimeError):
    """Raised when the source is unavailable or its identity schema is unsafe."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _clean(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).replace("\ufeff", "").split())


def _identity_key(commerce_id: str, provider_product_id: str) -> str:
    # Match the stable identity namespace used by the regional SEPA builder,
    # without requiring a full qualification pass over price observations.
    return f"ar-sepa-product:{commerce_id}:{provider_product_id}"


def _open_product_rows(
    input_path: Path,
) -> Iterator[tuple[str, int, Mapping[str, str]]]:
    """Yield ``(package_name, line_number, row)`` from every products.csv."""

    try:
        outer = zipfile.ZipFile(input_path)
    except (OSError, zipfile.BadZipFile) as exc:
        raise ProductIdentityAuditError("official source ZIP is unreadable") from exc
    with outer:
        bad = outer.testzip()
        if bad is not None:
            raise ProductIdentityAuditError(f"outer ZIP CRC validation failed: {bad}")
        found_products = False
        for outer_info in outer.infolist():
            if outer_info.is_dir() or not outer_info.filename.lower().endswith(".zip"):
                continue
            if outer_info.file_size == 0:
                continue
            try:
                with outer.open(outer_info) as nested_stream, zipfile.ZipFile(nested_stream) as nested:
                    bad_inner = nested.testzip()
                    if bad_inner is not None:
                        raise ProductIdentityAuditError(
                            f"nested ZIP CRC validation failed: {outer_info.filename}:{bad_inner}"
                        )
                    names = [name for name in nested.namelist() if not name.endswith("/")]
                    product_name = next(
                        (name for name in names if name.casefold().endswith("productos.csv")),
                        None,
                    )
                    if product_name is None:
                        continue
                    found_products = True
                    with nested.open(product_name) as product_stream:
                        text_stream = io.TextIOWrapper(
                            product_stream, encoding="utf-8-sig", newline=""
                        )
                        try:
                            reader = csv.reader(text_stream, delimiter="|")
                            try:
                                headers = [_clean(value) for value in next(reader)]
                            except StopIteration as exc:
                                raise ProductIdentityAuditError(
                                    f"productos.csv has no header: {outer_info.filename}"
                                ) from exc
                            indexes = {header: index for index, header in enumerate(headers)}
                            missing = [
                                header for header in _REQUIRED_COLUMNS if header not in indexes
                            ]
                            if missing:
                                raise ProductIdentityAuditError(
                                    f"productos.csv is missing required identity columns: {','.join(missing)}"
                                )
                            for line_number, values in enumerate(reader, start=2):
                                if len(values) != len(headers):
                                    # The caller counts malformed rows from the
                                    # explicit sentinel rather than guessing a
                                    # product identity from shifted columns.
                                    yield outer_info.filename, line_number, {"__malformed__": "1"}
                                    continue
                                yield outer_info.filename, line_number, {
                                    header: _clean(values[index]) for header, index in indexes.items()
                                }
                        finally:
                            text_stream.detach()
            except zipfile.BadZipFile as exc:
                raise ProductIdentityAuditError(
                    f"nested ZIP is invalid: {outer_info.filename}"
                ) from exc
        if not found_products:
            raise ProductIdentityAuditError("outer ZIP contains no productos.csv files")


@dataclass
class _IdentityAggregate:
    key: str
    commerce_id: str
    provider_product_id: str
    canonical_name: str
    canonical_brand: str | None
    rows: int = 0
    name_variants: Counter[str] = field(default_factory=Counter)
    brand_variants: Counter[str] = field(default_factory=Counter)
    gtin_variants: set[str] = field(default_factory=set)
    quantity_variants: set[str] = field(default_factory=set)
    family_candidates: dict[str, dict[str, set[str]]] = field(default_factory=dict)
    aliases: dict[tuple[str, str], dict[str, str]] = field(default_factory=dict)
    names_seen_for_classification: set[str] = field(default_factory=set)

    def _merge_identity(self, identity: Mapping[str, Any]) -> None:
        for family in identity.get("familyCandidates", ()):
            if not isinstance(family, Mapping):
                continue
            family_key = str(family.get("family") or "")
            if not family_key:
                continue
            target = self.family_candidates.setdefault(
                family_key,
                {"roles": set(), "forms": set(), "matchedTerms": set()},
            )
            role = family.get("role")
            if isinstance(role, str):
                target["roles"].add(role)
            for field_name, target_name in (("observedForms", "forms"), ("matchedTerms", "matchedTerms")):
                values = family.get(field_name)
                if isinstance(values, Sequence) and not isinstance(values, (str, bytes)):
                    target[target_name].update(str(value) for value in values if str(value).strip())
        for alias in identity.get("aliases", ()):
            if not isinstance(alias, Mapping):
                continue
            value = alias.get("value")
            language = alias.get("language")
            if not isinstance(value, str) or not isinstance(language, str) or not value.strip():
                continue
            key = (language, value.casefold())
            if len(self.aliases) < MAX_ALIAS_RECORDS or key in self.aliases:
                self.aliases[key] = {
                    "value": value,
                    "language": language,
                    "provenance": str(alias.get("provenance") or "SOURCE_DERIVED"),
                    "confidence": str(alias.get("confidence") or "HIGH"),
                }

    def add_row(
        self,
        *,
        name: str,
        brand: str,
        gtin: str,
        quantity_value: str,
        quantity_unit: str,
    ) -> None:
        self.rows += 1
        if name:
            self.name_variants[name] += 1
            if name not in self.names_seen_for_classification:
                self.names_seen_for_classification.add(name)
                try:
                    self._merge_identity(
                        derive_identity_record(
                            {
                                "productEvidenceKey": self.key,
                                "name": name,
                                "brand": brand or None,
                                "gtin": gtin or None,
                                "quantity": {
                                    "value": quantity_value,
                                    "unit": quantity_unit,
                                }
                                if quantity_value or quantity_unit
                                else None,
                            }
                        )
                    )
                except (TypeError, ValueError):
                    # The report still retains the source identity and counts
                    # the row as unclassified rather than inventing a label.
                    pass
        if brand:
            self.brand_variants[brand] += 1
        if gtin:
            self.gtin_variants.add(gtin)
        if quantity_value or quantity_unit:
            self.quantity_variants.add(f"{quantity_value}|{quantity_unit}")

    @property
    def classification(self) -> str:
        if not self.family_candidates:
            return "UNCLASSIFIED"
        if len(self.family_candidates) > 1:
            return "MULTIPLE_FAMILIES"
        evidence = next(iter(self.family_candidates.values()))
        if "DIRECT" in evidence["roles"]:
            return "DIRECT"
        return "DERIVED"

    def as_identity(self) -> dict[str, Any]:
        family_values = []
        for family, evidence in sorted(self.family_candidates.items()):
            family_values.append(
                {
                    "family": family,
                    "role": "DIRECT" if "DIRECT" in evidence["roles"] else "DERIVED",
                    "matchedTerms": sorted(evidence["matchedTerms"]),
                    "observedForms": sorted(evidence["forms"]),
                }
            )
        aliases = sorted(
            self.aliases.values(),
            key=lambda item: (str(item.get("value", "")).casefold(), str(item.get("language", ""))),
        )
        name_samples = [
            value
            for value, _count in sorted(
                self.name_variants.items(), key=lambda item: (-item[1], item[0].casefold())
            )[:MAX_VARIANT_SAMPLES]
        ]
        brand_samples = [
            value
            for value, _count in sorted(
                self.brand_variants.items(), key=lambda item: (-item[1], item[0].casefold())
            )[:MAX_VARIANT_SAMPLES]
        ]
        return {
            "productEvidenceKey": self.key,
            "commerceId": self.commerce_id,
            "providerProductId": self.provider_product_id,
            "name": self.canonical_name,
            "brand": self.canonical_brand,
            "familyCandidates": family_values,
            "classification": self.classification,
            "aliases": aliases,
            "sourceEvidence": {
                "rows": self.rows,
                "nameVariantCount": len(self.name_variants),
                "nameSamples": name_samples,
                "brandVariantCount": len(self.brand_variants),
                "brandSamples": brand_samples,
                "gtinVariantCount": len(self.gtin_variants),
                "quantityVariantCount": len(self.quantity_variants),
            },
        }


def _canonical_json(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def audit_source(
    input_path: Path | str,
    *,
    expected_sha256: str,
    expected_bytes: int | None = None,
    release_date: str | None = None,
    identity_output: Path | str | None = None,
) -> dict[str, Any]:
    """Scan one trusted source ZIP and return a coverage report."""

    source = Path(input_path).resolve()
    if not source.is_file():
        raise ProductIdentityAuditError(f"source does not exist: {source}")
    if not _SHA256.fullmatch(expected_sha256 or ""):
        raise ProductIdentityAuditError("expected source SHA-256 is invalid")
    actual_sha256 = sha256_file(source)
    if actual_sha256.casefold() != expected_sha256.casefold():
        raise ProductIdentityAuditError("source SHA-256 does not match the authorized release")
    actual_bytes = source.stat().st_size
    if expected_bytes is not None and actual_bytes != expected_bytes:
        raise ProductIdentityAuditError("source byte count does not match the authorized release")

    aggregates: dict[str, _IdentityAggregate] = {}
    packages: Counter[str] = Counter()
    package_rows: Counter[str] = Counter()
    global_provider_product_ids: set[str] = set()
    commerce_ids: set[str] = set()
    canonical_names: set[str] = set()
    rows_scanned = 0
    malformed_rows = 0
    missing_identity_rows = 0
    invalid_name_rows = 0
    for package_name, _line_number, row in _open_product_rows(source):
        packages[package_name] += 1
        if row.get("__malformed__"):
            malformed_rows += 1
            continue
        package_rows[package_name] += 1
        rows_scanned += 1
        commerce_id = _clean(row.get("id_comercio"))
        provider_product_id = _clean(row.get("id_producto"))
        name = _clean(row.get("productos_descripcion"))
        if not commerce_id or not provider_product_id or not name:
            missing_identity_rows += 1
            continue
        global_provider_product_ids.add(provider_product_id)
        commerce_ids.add(commerce_id)
        canonical_names.add(name)
        if len(name) > MAX_NAME_LENGTH:
            invalid_name_rows += 1
            continue
        key = _identity_key(commerce_id, provider_product_id)
        aggregate = aggregates.get(key)
        if aggregate is None:
            aggregate = _IdentityAggregate(
                key=key,
                commerce_id=commerce_id,
                provider_product_id=provider_product_id,
                canonical_name=name,
                canonical_brand=_clean(row.get("productos_marca")) or None,
            )
            aggregates[key] = aggregate
        aggregate.add_row(
            name=name,
            brand=_clean(row.get("productos_marca")),
            gtin=_clean(row.get("productos_ean")),
            quantity_value=_clean(row.get("productos_cantidad_presentacion")),
            quantity_unit=_clean(row.get("productos_unidad_medida_presentacion")),
        )

    identities = [aggregates[key].as_identity() for key in sorted(aggregates)]
    classifications = Counter(identity["classification"] for identity in identities)
    family_counts: dict[str, Counter[str]] = {}
    form_counts: dict[str, Counter[str]] = {}
    alias_usage: Counter[tuple[str, str]] = Counter()
    ambiguous_aliases: set[tuple[str, str]] = set()
    for identity in identities:
        for family in identity["familyCandidates"]:
            family_key = str(family["family"])
            family_counts.setdefault(family_key, Counter())[str(family["role"])] += 1
            for form in family["observedForms"]:
                form_counts.setdefault(family_key, Counter())[str(form)] += 1
        for alias in identity["aliases"]:
            alias_key = (str(alias.get("language", "")), str(alias.get("value", "")).casefold())
            if alias_key in alias_usage and alias_usage[alias_key] > 0:
                ambiguous_aliases.add(alias_key)
            alias_usage[alias_key] += 1

    if identity_output is not None:
        output = Path(identity_output).resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        # Supplying a file object avoids embedding the caller's absolute path
        # in the gzip header, so identical source/rules produce identical
        # derived bytes in different workspaces.
        with output.open("wb") as raw_output:
            with gzip.GzipFile(fileobj=raw_output, mode="wb", mtime=0) as handle:
                for identity in identities:
                    handle.write((_canonical_json(identity) + "\n").encode("utf-8"))

    report = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "status": "COMPLETE",
        "source": {
            "path": str(source),
            "sha256": actual_sha256,
            "bytes": actual_bytes,
            "releaseDate": release_date,
            "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
            "attribution": "Precios Claros - Base SEPA; datos.gob.ar",
        },
        "scan": {
            "packagesWithProducts": len(packages),
            "packageRows": dict(sorted(package_rows.items())),
            "productRowsScanned": rows_scanned,
            "malformedRows": malformed_rows,
            "missingIdentityRows": missing_identity_rows,
            "invalidNameRows": invalid_name_rows,
            "distinctProductIdentities": len(identities),
            "distinctProviderProductIds": len(global_provider_product_ids),
            "distinctCommerceIds": len(commerce_ids),
            "distinctCanonicalNames": len(canonical_names),
        },
        "identityCounts": {
            "classifications": dict(sorted(classifications.items())),
            "nameVariantIdentities": sum(
                identity["sourceEvidence"]["nameVariantCount"] > 1 for identity in identities
            ),
            "brandVariantIdentities": sum(
                identity["sourceEvidence"]["brandVariantCount"] > 1 for identity in identities
            ),
            "gtinConflictIdentities": sum(
                identity["sourceEvidence"]["gtinVariantCount"] > 1 for identity in identities
            ),
            "quantityConflictIdentities": sum(
                identity["sourceEvidence"]["quantityVariantCount"] > 1 for identity in identities
            ),
        },
        "families": {
            family: {
                "directCount": counts.get("DIRECT", 0),
                "derivedCount": counts.get("DERIVED", 0),
                "observedForms": dict(sorted(form_counts.get(family, Counter()).items())),
            }
            for family, counts in sorted(family_counts.items())
        },
        "aliases": {
            "distinct": len(alias_usage),
            "identityCountWithAliases": sum(bool(identity["aliases"]) for identity in identities),
            "ambiguousAliasCount": len(ambiguous_aliases),
        },
        "safety": {
            "prices": False,
            "offers": False,
            "stock": False,
            "availability": False,
            "automaticProductResolution": False,
            "sourceRowsRemainProviderEvidence": True,
        },
    }
    return report


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--expected-bytes", type=int)
    parser.add_argument("--release-date")
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--identity-output", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        report = audit_source(
            args.input,
            expected_sha256=args.expected_sha256,
            expected_bytes=args.expected_bytes,
            release_date=args.release_date,
            identity_output=args.identity_output,
        )
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False, sort_keys=True))
        return 0
    except (OSError, ProductIdentityAuditError, zipfile.BadZipFile) as exc:
        print(f"product identity audit failed safely: {exc}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())


__all__ = ["SCHEMA_VERSION", "POLICY_VERSION", "ProductIdentityAuditError", "audit_source"]
