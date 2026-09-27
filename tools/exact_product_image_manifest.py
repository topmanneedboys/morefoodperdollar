#!/usr/bin/env python3
"""Emit a bounded, rights-aware exact-product image evidence manifest.

This is an offline provider-edge tool.  It reuses the existing Argentina image
audit's exact GTIN/name/brand/quantity boundary, but searches every documented
Open Food Facts selected-image role exposed by tabular exports instead of only
``image_front_url``.  It emits evidence rows only when an image URL and an
explicit compatible open licence are both present.  Image bytes are never
downloaded, and no country or language filter is applied: those are evidence
fields, not identity shortcuts.

The output is JSONL so a national export can be processed with bounded memory.
The first line is a deterministic manifest header; subsequent lines are
accepted evidence records carrying attribution/share-alike obligations.
"""

from __future__ import annotations

import argparse
import json
import re
import tempfile
from collections import Counter
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence
from urllib.parse import urlparse

from tools.audit_argentina_product_images import (
    ProductImageAuditError,
    _clean,
    _iter_off_rows,
    _load_catalog,
    _strict_match,
    _sha256,
)
from tools.open_facts_barcode import canonical_open_facts_gtin


SCHEMA_VERSION = "valuepilot-exact-product-image-manifest-v1"
POLICY_VERSION = "exact-gtin-name-brand-quantity-rights-roles-v1"
OFF_IMAGE_HOSTS = frozenset(("images.openfoodfacts.org", "static.openfoodfacts.org"))
MAX_OUTPUT_RECORD_BYTES = 32 * 1024

# The API documents these selected roles.  Small/thumb variants are included
# as fallback evidence, but the engine keeps the source field and URL so a
# later renderer can choose the smallest permitted representation.
IMAGE_FIELDS: tuple[tuple[str, str], ...] = (
    ("front", "image_front_url"),
    ("front", "image_url"),
    ("ingredients", "image_ingredients_url"),
    ("nutrition", "image_nutrition_url"),
    ("packaging", "image_packaging_url"),
    ("front", "image_front_small_url"),
    ("front", "image_front_thumb_url"),
    ("ingredients", "image_ingredients_small_url"),
    ("ingredients", "image_ingredients_thumb_url"),
    ("nutrition", "image_nutrition_small_url"),
    ("nutrition", "image_nutrition_thumb_url"),
    ("packaging", "image_packaging_small_url"),
    ("packaging", "image_packaging_thumb_url"),
)
_SELECTED_IMAGE_RE = re.compile(
    r"/(?P<role>front|ingredients|nutrition|packaging)(?:_(?P<language>[a-z]{2}))?\."
    r"(?P<revision>[0-9]+)\.(?P<resolution>100|200|400|full)\.jpe?g$",
    re.IGNORECASE,
)


class ImageManifestError(ProductImageAuditError):
    """The supplied image-evidence inputs are unsafe or malformed."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ImageManifestError(message)


def _valid_image_url(value: Any) -> bool:
    raw = _clean(value)
    if not raw:
        return False
    parsed = urlparse(raw)
    return (
        parsed.scheme == "https"
        and parsed.hostname in OFF_IMAGE_HOSTS
        and bool(parsed.path)
        and "/invalid/" not in parsed.path
    )


def _variant_metadata(url: str, role: str) -> dict[str, str]:
    match = _SELECTED_IMAGE_RE.search(urlparse(url).path)
    if match is None:
        return {"role": role, "language": "UNKNOWN", "resolution": "UNKNOWN"}
    return {
        "role": match.group("role").lower(),
        "language": (match.group("language") or "UNKNOWN").lower(),
        "resolution": match.group("resolution").lower(),
    }


def _license_value(row: Mapping[str, Any], field: str, role: str) -> str:
    role_field = f"image_{role}_licenses"
    values = (
        row.get(f"{field}_licenses"),
        row.get(role_field),
        row.get("image_licenses"),
        row.get("image_license"),
    )
    for value in values:
        cleaned = _clean(value)
        if cleaned:
            return cleaned
    return ""


def _compatible_license(value: str) -> tuple[str, bool, bool] | None:
    """Return the small set of licenses safe for the display pipeline.

    License labels are provider data, not trusted enum values.  Matching a
    broad substring such as ``"cc by"`` would incorrectly accept restricted
    variants like CC BY-NC or CC BY-ND.  Normalize punctuation into tokens,
    reject explicit non-commercial/no-derivatives markers first, and only
    then accept the exact commercial-compatible families we document.
    """

    normalized = value.casefold().replace("–", "-").replace("—", "-")
    tokens = re.sub(r"[^a-z0-9]+", "-", normalized).strip("-")
    token_set = set(tokens.split("-")) if tokens else set()
    if {"nc", "nd", "noncommercial", "noderivatives"} & token_set:
        return None
    if re.search(r"non[- ]?commercial|no[- ]?derivatives", normalized):
        return None
    if "public-domain" in tokens or "cc0" in token_set or "creativecommons.org/publicdomain/zero/" in normalized:
        return ("CC0_OR_PUBLIC_DOMAIN", False, False)
    if "creativecommons.org/licenses/by-sa/" in normalized or re.search(r"(?:^|-)cc-by-sa(?:-|$)", tokens):
        return ("CC-BY-SA", True, True)
    if "creativecommons.org/licenses/by/" in normalized or re.search(r"(?:^|-)cc-by(?:-|$)", tokens):
        return ("CC-BY", True, False)
    return None


def _iter_image_candidates(row: Mapping[str, Any]) -> Iterable[dict[str, str]]:
    seen: set[tuple[str, str]] = set()
    for role, field in IMAGE_FIELDS:
        raw = _clean(row.get(field))
        if not raw:
            continue
        key = (field, raw)
        if key in seen:
            continue
        seen.add(key)
        metadata = _variant_metadata(raw, role)
        yield {
            "field": field,
            "url": raw,
            "role": metadata["role"],
            "language": metadata["language"],
            "resolution": metadata["resolution"],
        }


def _canonical_json(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def build_manifest(catalog_path: Path, off_path: Path, output_path: Path) -> dict[str, Any]:
    """Build a deterministic accepted-evidence JSONL manifest.

    The catalog is bounded by the existing audit loader.  The Open Food Facts
    export and the accepted output are streamed; no national image or row list
    is retained in memory.
    """

    _require(output_path != catalog_path and output_path != off_path, "output must differ from inputs")
    _require(not output_path.exists(), f"Refusing to overwrite output: {output_path}")
    catalog, catalog_meta = _load_catalog(catalog_path)
    yerba_keys: set[str] = set(catalog_meta["yerba_keys"])
    counters: Counter[str] = Counter()
    rejection_reasons: Counter[str] = Counter()
    accepted_licenses: Counter[str] = Counter()
    roles: Counter[str] = Counter()
    languages: Counter[str] = Counter()
    strict_identity_keys: set[str] = set()
    accepted_identity_keys: set[str] = set()
    accepted_yerba_identity_keys: set[str] = set()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    header = {
        "recordType": "manifestHeader",
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "source": {
            "provider": "open-food-facts",
            "catalogSha256": _sha256(catalog_path),
            "openFoodFactsExportSha256": _sha256(off_path),
            "countryFilter": "NONE",
            "gtinNormalization": "OPEN_FOOD_FACTS_DOCUMENTED_LEADING_ZERO_RULES",
            "imageHosts": sorted(OFF_IMAGE_HOSTS),
            "imageBytesDownloaded": False,
        },
        "rights": {
            "acceptedLicences": ["CC-BY-SA", "CC-BY", "CC0_OR_PUBLIC_DOMAIN"],
            "attributionRequired": True,
            "shareAlikeMayBeRequired": True,
        },
    }

    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", newline="\n", dir=output_path.parent,
        prefix=f".{output_path.name}.", suffix=".tmp", delete=False,
    ) as handle:
        temporary = Path(handle.name)
        handle.write(_canonical_json(header) + "\n")
        for row_number, row in enumerate(_iter_off_rows(off_path), start=2):
            counters["off_rows_seen"] += 1
            canonical = canonical_open_facts_gtin(_clean(row.get("code")))
            if canonical is None:
                rejection_reasons["INVALID_GTIN"] += 1
                continue
            identities = catalog.get(canonical)
            if identities is None:
                continue
            counters["off_rows_matching_catalog_gtin"] += 1
            for identity_key, record in identities:
                counters["catalog_identity_matches_evaluated"] += 1
                mismatch = _strict_match(record, row)
                if mismatch is not None:
                    rejection_reasons[mismatch.upper()] += 1
                    continue
                counters["strict_identity_rows"] += 1
                strict_identity_keys.add(identity_key)
                row_candidates: set[tuple[str, str]] = set()
                for candidate in _iter_image_candidates(row):
                    counters["image_fields_seen"] += 1
                    dedupe_key = (candidate["role"], candidate["url"])
                    if dedupe_key in row_candidates:
                        rejection_reasons["DUPLICATE_IMAGE_VARIANT"] += 1
                        continue
                    row_candidates.add(dedupe_key)
                    if not _valid_image_url(candidate["url"]):
                        rejection_reasons["IMAGE_URL_INVALID_OR_UNSUPPORTED_HOST"] += 1
                        continue
                    counters["valid_image_candidates"] += 1
                    license_text = _license_value(row, candidate["field"], candidate["role"])
                    if not license_text:
                        rejection_reasons["RIGHTS_EVIDENCE_MISSING"] += 1
                        continue
                    license_info = _compatible_license(license_text)
                    if license_info is None:
                        rejection_reasons["RIGHTS_LICENSE_INCOMPATIBLE"] += 1
                        continue
                    license_id, attribution_required, share_alike_required = license_info
                    accepted_licenses[license_id] += 1
                    roles[candidate["role"]] += 1
                    languages[candidate["language"]] += 1
                    accepted_identity_keys.add(identity_key)
                    if identity_key in yerba_keys:
                        accepted_yerba_identity_keys.add(identity_key)
                    evidence = {
                        "recordType": "acceptedImageEvidence",
                        "schemaVersion": SCHEMA_VERSION,
                        "policyVersion": POLICY_VERSION,
                        "productEvidenceKey": identity_key,
                        "gtin": canonical,
                        "source": "open-food-facts",
                        "sourceRow": row_number,
                        "imageField": candidate["field"],
                        "imageRole": candidate["role"],
                        "imageLanguage": candidate["language"],
                        "imageResolution": candidate["resolution"],
                        "imageUrl": candidate["url"],
                        "licenseEvidence": license_text,
                        "licenseId": license_id,
                        "attributionRequired": attribution_required,
                        "shareAlikeRequired": share_alike_required,
                        "exactIdentityBasis": "VALID_GTIN_NAME_BRAND_EXPLICIT_QUANTITY",
                        "priceOrAvailabilityClaim": False,
                    }
                    encoded = _canonical_json(evidence)
                    _require(len(encoded.encode("utf-8")) <= MAX_OUTPUT_RECORD_BYTES, "manifest evidence row exceeds bound")
                    handle.write(encoded + "\n")
                    counters["accepted_evidence_rows"] += 1
        handle.flush()
    temporary.replace(output_path)

    catalog_valid_gtin_identities = int(
        catalog_meta["catalog_counters"].get("valid_gtin_records", 0)
    )

    def _percent(numerator: int, denominator: int) -> float:
        return round((numerator * 100 / denominator), 6) if denominator else 0.0

    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "catalog": catalog_meta["catalog_counters"],
        "source": {
            "provider": "open-food-facts",
            "catalogSha256": _sha256(catalog_path),
            "openFoodFactsExportSha256": _sha256(off_path),
            "countryFilter": "NONE",
            "imageBytesDownloaded": False,
        },
        "counts": dict(sorted(counters.items())),
        "acceptedLicences": dict(sorted(accepted_licenses.items())),
        "acceptedRoles": dict(sorted(roles.items())),
        "acceptedLanguages": dict(sorted(languages.items())),
        "rejections": dict(sorted(rejection_reasons.items())),
        "coverage": {
            "catalogValidGtinIdentities": catalog_valid_gtin_identities,
            "catalogUniqueGtins": int(
                catalog_meta["catalog_counters"].get("unique_gtin_values", 0)
            ),
            "strictExactIdentityCount": len(strict_identity_keys),
            "rightsQualifiedImageEvidenceIdentityCount": len(accepted_identity_keys),
            "rightsQualifiedImageEvidencePercentOfCatalogValidGtinIdentities": _percent(
                len(accepted_identity_keys), catalog_valid_gtin_identities
            ),
            "rightsQualifiedImageEvidencePercentOfStrictExactIdentities": _percent(
                len(accepted_identity_keys), len(strict_identity_keys)
            ),
            "yerbaCatalogIdentities": len(yerba_keys),
            "yerbaRightsQualifiedImageEvidenceIdentityCount": len(
                accepted_yerba_identity_keys
            ),
            "yerbaRightsQualifiedImageEvidencePercent": _percent(
                len(accepted_yerba_identity_keys), len(yerba_keys)
            ),
            "displayEligibleIdentityCount": 0,
            "displayEligibility": "NOT_CLAIMED_IMAGE_BYTES_NOT_DOWNLOADED",
        },
        "displayEligibility": "EVIDENCE_ONLY_IMAGE_BYTES_NOT_DOWNLOADED",
        "obligations": ["preserve attribution", "honour share-alike when required", "retain source provenance"],
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--open-food-facts", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        report = build_manifest(args.catalog, args.open_food_facts, args.output)
    except (ImageManifestError, OSError) as exc:
        print(f"Exact product image manifest failed: {exc}")
        return 2
    print(_canonical_json(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
