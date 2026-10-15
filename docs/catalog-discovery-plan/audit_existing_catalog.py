"""Read every row of the pinned local artifacts; emit aggregate planning evidence.

This audits existing derived assignments, not their semantic correctness. It
does not classify products, change application data, or contact any service.
Usage: python docs/catalog-discovery-plan/audit_existing_catalog.py
"""

from __future__ import annotations

import gzip
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_HASH = "153e9d9f7ab4b87e72c16aebebc69c6f9f90fca681cd1075d625244e2e31bc2c"
MAX_ROWS = 500_000
MAX_DECOMPRESSED_BYTES = 512 * 1024 * 1024


def rows(path: Path):
    total_bytes = 0
    count = 0
    with gzip.open(path, "rb") as source:
        while line := source.readline(2 * 1024 * 1024 + 1):
            if len(line) > 2 * 1024 * 1024:
                raise ValueError("oversize source row")
            total_bytes += len(line)
            count += 1
            if total_bytes > MAX_DECOMPRESSED_BYTES or count > MAX_ROWS:
                raise ValueError("audit bound reached; increase explicitly, never truncate")
            value = json.loads(line.decode("utf-8"))
            if not isinstance(value, dict):
                raise ValueError("source row is not an object")
            yield value


def digest(path: Path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return {"path": path.relative_to(ROOT).as_posix(), "bytes": path.stat().st_size,
            "sha256": value.hexdigest()}


def contains_replacement(value):
    if isinstance(value, str):
        return "\ufffd" in value
    if isinstance(value, dict):
        return any(contains_replacement(item) for item in value.values())
    if isinstance(value, list):
        return any(contains_replacement(item) for item in value)
    return False


def main():
    identity_path = ROOT / "local-provider-data" / f"product-identities-{SOURCE_HASH}.jsonl.gz"
    taxonomy_path = ROOT / "local-provider-data" / f"product-taxonomy-{SOURCE_HASH}.jsonl.gz"
    identity_map = {}
    providers = defaultdict(lambda: {"identities": set(), "aliasLanguages": set(), "names": set()})
    identity_counts = Counter()
    for item in rows(identity_path):
        key = item["productEvidenceKey"]
        provider = item["providerProductId"]
        if key in identity_map:
            raise ValueError("duplicate source identity key")
        identity_map[key] = provider
        profile = providers[provider]
        profile["identities"].add(key)
        source_names = item.get("sourceEvidence", {}).get("nameSamples", [])
        profile["names"].update(source_names)
        profile["names"].add(item["name"])
        identity_counts["records"] += 1
        aliases = item.get("aliases", [])
        languages = {alias.get("language") for alias in aliases if alias.get("value")}
        profile["aliasLanguages"].update(languages)
        identity_counts["withEnglishAlias"] += "en" in languages
        identity_counts["withSpanishAlias"] += "es" in languages
        identity_counts["withoutAnyAlias"] += not aliases
        identity_counts["withFamilyAssignment"] += bool(item.get("familyCandidates"))
        identity_counts["withReplacementCharacterInSourceNames"] += contains_replacement(source_names)

    taxonomy_ids = set()
    taxonomy_identities = {}
    coverage = Counter()
    states = Counter()
    families = Counter()
    forms = Counter()
    departments = Counter()
    family_forms = defaultdict(Counter)
    names_vocabulary = set()
    for item in rows(taxonomy_path):
        provider = item["providerProductId"]
        if provider in taxonomy_ids:
            raise ValueError("duplicate taxonomy provider ID")
        if provider not in providers:
            raise ValueError("taxonomy provider is missing from the identity source")
        taxonomy_ids.add(provider)
        tax = item["taxonomy"]
        states[tax["coverageStatus"]] += 1
        fs = tax.get("familyCandidates", [])
        fm = tax.get("formCandidates", [])
        ds = tax.get("departmentCandidates", [])
        coverage["records"] += 1
        coverage["withoutFamily"] += not fs
        coverage["withoutForm"] += not fm
        coverage["withoutFamilyAndForm"] += not fs and not fm
        coverage["withMultipleFamilies"] += len(fs) > 1
        coverage["withMultipleDepartmentCandidates"] += len(ds) > 1
        coverage["withEnglishAliasOnAnyLinkedIdentity"] += "en" in providers[provider]["aliasLanguages"]
        coverage["withoutEnglishAliasOnAnyLinkedIdentity"] += "en" not in providers[provider]["aliasLanguages"]
        coverage["withMultipleObservedNames"] += len(providers[provider]["names"]) > 1
        coverage["withReplacementCharacterInTaxonomyLabels"] += contains_replacement(
            [{k: v for k, v in entry.items() if k in {"labelEs", "labelEn"}} for entry in ds + fm]
        )
        for entry in fs:
            families[entry["family"]] += 1
            family_forms[entry["family"]].update(set(entry.get("observedForms", [])))
        forms.update(entry["key"] for entry in fm)
        if tax.get("primaryDepartment"):
            departments[tax["primaryDepartment"]] += 1
        for key in item.get("identityKeys", []):
            if key in taxonomy_identities:
                raise ValueError("duplicate identity mapping in taxonomy")
            taxonomy_identities[key] = provider
        for name in item.get("nameSamples", []):
            names_vocabulary.update(re.findall(r"[^\W\d_]+", name.casefold()))

    unmapped = set(identity_map) - set(taxonomy_identities)
    orphan = set(taxonomy_identities) - set(identity_map)
    wrong_provider = sum(identity_map[key] != provider for key, provider in taxonomy_identities.items() if key in identity_map)
    result = {
        "auditDate": "2026-10-15",
        "baselineCommit": "4b3145f3",
        "scope": "Full structural census of the existing derived 2026-10-08 artifacts; not a new semantic classification or current offer inventory",
        "sourceZipSha256": SOURCE_HASH,
        "inputs": [digest(identity_path), digest(taxonomy_path)],
        "identityRows": dict(sorted(identity_counts.items())),
        "distinctProviderProductIds": len(providers),
        "taxonomyRows": dict(sorted(coverage.items())),
        "existingDepartmentCoverageStatus": dict(sorted(states.items())),
        "existingFamilies": dict(sorted(families.items())),
        "existingFamilyObservedForms": {key: dict(sorted(value.items())) for key, value in sorted(family_forms.items())},
        "existingForms": dict(sorted(forms.items())),
        "existingPrimaryDepartments": dict(sorted(departments.items())),
        "distinctAlphabeticTokensInTaxonomyNameSamples": len(names_vocabulary),
        "integrity": {
            "missingProviderTaxonomyRecords": len(set(providers) - taxonomy_ids),
            "unmappedIdentityKeys": len(unmapped),
            "orphanTaxonomyIdentityKeys": len(orphan),
            "providerMappingMismatches": wrong_provider,
            "allIdentityRowsMapped": not unmapped and not orphan and not wrong_provider,
        },
        "limitations": [
            "Counts describe existing rules, not independently verified semantic correctness.",
            "An English alias can be broad; its presence does not prove complete English search coverage.",
            "CLASSIFIED currently means primary department assigned, not complete type/role/relationship coverage.",
            "A provider ID is the current aggregation key, not proof of a distinct worldwide SKU or valid GTIN.",
            "Missing alias does not imply a source-name query cannot match.",
            "Raw products and sample names are deliberately not included in this report.",
        ],
    }
    target = Path(__file__).with_name("EXISTING_CATALOG_CENSUS.json")
    target.write_text(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(target), "providers": len(providers), "identityRows": identity_counts["records"],
                      "taxonomy": dict(coverage), "status": dict(states), "integrity": result["integrity"]}, indent=2))


if __name__ == "__main__":
    main()
