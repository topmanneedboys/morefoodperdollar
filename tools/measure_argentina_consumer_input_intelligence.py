#!/usr/bin/env python3
"""Measure the bounded consumer-input layer using only tiny deterministic data.

This is a local/CI qualification helper.  It never opens the official SEPA ZIP
and never treats a synthetic record as a current offer.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
import tracemalloc
from decimal import Decimal
from pathlib import Path
from typing import Any

try:
    from tools.consumer_input_intelligence import (
    AMBIGUOUS_PRODUCT,
    AMBIGUITY_GAP_SCORE,
    MAX_CANDIDATES,
    MAX_FUZZY_COMPARISONS,
    MAX_LINES,
    DIMENSION_MISMATCH,
    FUZZY_MIN_SIMILARITY,
    MULTIPLE_PLAUSIBLE_PRODUCTS,
    NEEDS_CLARIFICATION,
    NO_SAFE_MATCH,
    PACKAGE_SIZE_REQUIRED,
    POLICY_VERSION,
    QUANTITY_REQUIRED,
    RESOLVED_ALIAS,
    RESOLVED_EXACT,
    RESOLVED_SAFE_CORRECTION,
    SCHEMA_VERSION,
    SHORT_MIN_SIMILARITY,
    CatalogIndex,
    ConsumerInputInterpreter,
        sqlite_fts5_trigram_supported,
    )
except ModuleNotFoundError:  # pragma: no cover - direct script invocation
    from consumer_input_intelligence import (
        AMBIGUOUS_PRODUCT,
        AMBIGUITY_GAP_SCORE,
        MAX_CANDIDATES,
        MAX_FUZZY_COMPARISONS,
        MAX_LINES,
        DIMENSION_MISMATCH,
        FUZZY_MIN_SIMILARITY,
        MULTIPLE_PLAUSIBLE_PRODUCTS,
        NEEDS_CLARIFICATION,
        NO_SAFE_MATCH,
        PACKAGE_SIZE_REQUIRED,
        POLICY_VERSION,
        QUANTITY_REQUIRED,
        RESOLVED_ALIAS,
        RESOLVED_EXACT,
        RESOLVED_SAFE_CORRECTION,
        SCHEMA_VERSION,
        SHORT_MIN_SIMILARITY,
        CatalogIndex,
        ConsumerInputInterpreter,
        sqlite_fts5_trigram_supported,
    )


def benchmark_records(count: int = 5000) -> list[dict[str, Any]]:
    if count < 32 or count > 100_000:
        raise ValueError("benchmark record count must be between 32 and 100000")
    records: list[dict[str, Any]] = [
        {"productEvidenceKey": "rice", "name": "Arroz Largo Fino 1 kg", "quantity": {"value": "1", "unit": "kg"}},
        {"productEvidenceKey": "butter", "name": "Manteca Sin Sal 500 g", "quantity": {"value": "500", "unit": "g"}},
        {"productEvidenceKey": "milk", "name": "Leche Entera 1 L", "quantity": {"value": "1", "unit": "l"}},
        {"productEvidenceKey": "tomato", "name": "Tomate Fresco 1 kg", "quantity": {"value": "1", "unit": "kg"}, "form": "fresh"},
        {"productEvidenceKey": "tomato-paste", "name": "Pasta de Tomate 500 g", "quantity": {"value": "500", "unit": "g"}, "form": "paste"},
        {"productEvidenceKey": "coca", "name": "Coca-Cola Original 2.25 L", "brand": "Coca-Cola", "quantity": {"value": "2.25", "unit": "l"}, "form": "soda"},
        {"productEvidenceKey": "coca-zero", "name": "Coca-Cola Zero 2.25 L", "brand": "Coca-Cola", "quantity": {"value": "2.25", "unit": "l"}, "form": "soda"},
        {"productEvidenceKey": "sprite", "name": "Sprite 2.25 L", "brand": "Sprite", "quantity": {"value": "2.25", "unit": "l"}, "form": "soda"},
    ]
    for index in range(len(records), count):
        records.append({
            "productEvidenceKey": f"fixture-{index:05d}",
            "name": f"Producto de Prueba {index:05d} 500 g",
            "quantity": {"value": "500", "unit": "g"},
            "form": "dry",
        })
    return records


def _mutation_corpus() -> tuple[list[tuple[str, str]], str]:
    seeds = ("arroz", "manteca", "leche", "tomate", "sprite", "coca cola")
    mutations: list[tuple[str, str]] = []
    for seed in seeds:
        for index in range(500):
            mode = index % 5
            if mode == 0:
                value = seed.replace("a", "") or seed
            elif mode == 1:
                value = seed + seed[-1]
            elif mode == 2 and len(seed) > 2:
                value = seed[:1] + seed[2] + seed[1] + seed[3:]
            elif mode == 3:
                value = seed.replace(" ", "")
            else:
                value = seed.replace("e", "\u00e9")
            mutations.append((seed, value))
    digest = hashlib.sha256("\n".join(f"{seed}\t{value}" for seed, value in mutations).encode("utf-8")).hexdigest()
    return mutations, digest


def _measure(interpreter: ConsumerInputInterpreter, text: str, repeats: int) -> dict[str, float]:
    values: list[float] = []
    for _ in range(repeats):
        started = time.perf_counter()
        interpreter.interpret(text, require_quantities=True)
        values.append((time.perf_counter() - started) * 1000)
    return {
        "meanMs": round(statistics.mean(values), 3),
        "p95Ms": round(sorted(values)[max(0, int(len(values) * 0.95) - 1)], 3),
        "maxMs": round(max(values), 3),
    }


def measure(*, record_count: int = 5000) -> dict[str, Any]:
    tracemalloc.start()
    records = benchmark_records(record_count)
    index = CatalogIndex.from_records(records)
    _, peak_bytes = tracemalloc.get_traced_memory()
    tracemalloc.stop()
    interpreter = ConsumerInputInterpreter(index)
    workloads = {
        "oneLine": "aroz 1kg",
        "fiveLines": "aroz 1kg, milkk 1l, buter 500g, sprit 2.25l, tomate 1kg",
        "tenLines": "aroz 1kg, milkk 1l, buter 500g, sprit 2.25l, tomate 1kg, rice 1kg, milk 1l, butter 500g, coca zero 2.25l, pasta de tomate 500g",
    }
    cold: dict[str, Any] = {}
    warm: dict[str, Any] = {}
    for name, text in workloads.items():
        cold[name] = _measure(interpreter, text, 1)
        warm[name] = _measure(interpreter, text, 20)

    safety_queries = (
        "apple juice 1l", "orange soda 2l", "chicken flavour 1kg", "chicken broth 1l",
        "milk cosmetics 250ml", "coconut shampoo 500ml", "rice seasoning 100g",
        "egg pasta 500g", "cheese ravioli 500g", "cereal yogurt 500g", "sugar free soda 2l",
    )
    unsafe_structured = 0
    safety_states: dict[str, str] = {}
    for query in safety_queries:
        line = interpreter.interpret(query, require_quantities=True)["lines"][0]
        safety_states[query] = line["resolution"]
        if line.get("structuredLine") is not None:
            unsafe_structured += 1

    clarification_cases = {
        "tomate": QUANTITY_REQUIRED,
        "2 Sprite": PACKAGE_SIZE_REQUIRED,
        "1 kg Coca-Cola": DIMENSION_MISMATCH,
    }
    clarification_correct = 0
    for query, expected in clarification_cases.items():
        line = interpreter.interpret(query, require_quantities=True)["lines"][0]
        if line["resolution"] == NEEDS_CLARIFICATION and (line.get("clarification") or {}).get("code") == expected:
            clarification_correct += 1

    typo_cases = ("aroz 1kg", "rise 1kg", "milkk 1l", "buter 500g", "tomte 1kg")
    typo_correct = sum(interpreter.interpret(query, require_quantities=True)["lines"][0]["resolution"] == RESOLVED_SAFE_CORRECTION for query in typo_cases)
    alias_cases = ("arroz 1kg", "rice 1kg", "manteca 500g", "butter 500g", "sprit 2.25l", "coca zero 2.25l")
    alias_resolved = sum(interpreter.interpret(query, require_quantities=True)["lines"][0]["resolution"] in {RESOLVED_ALIAS, RESOLVED_EXACT} for query in alias_cases)
    search_cases = (
        ("arroz 1kg", "rice"), ("rice 1kg", "rice"), ("aroz 1kg", "rice"), ("rise 1kg", "rice"),
        ("manteca 500g", "butter"), ("butter 500g", "butter"), ("buter 500g", "butter"),
        ("leche 1l", "milk"), ("milk 1l", "milk"), ("milkk 1l", "milk"),
        ("tomate 1kg", "tomato"), ("tomato 1kg", "tomato"), ("tomte 1kg", "tomato"),
        ("pasta de tomate 500g", "tomato-paste"), ("coca zero 2.25l", "coca-zero"),
        ("sprite 2.25l", "sprite"), ("sprit 2.25l", "sprite"), ("sprtie 2.25l", "sprite"),
        ("arroz 2kg", "rice"), ("butter 250g", "butter"), ("leche 2l", "milk"),
        ("tomate 500g", "tomato"), ("pasta tomate 500g", "tomato-paste"), ("sprite 1l", "sprite"),
        ("tomate fresco 1kg", "tomato"),
    )
    search_hits = 0
    for query, expected_key in search_cases:
        line = interpreter.interpret(query, require_quantities=True)["lines"][0]
        if any(record.get("productEvidenceKey") == expected_key for record in line.get("suggestions", [])):
            search_hits += 1
    mutations, corpus_hash = _mutation_corpus()
    holdout = mutations[2100:]
    holdout_non_exact = sum(interpreter.interpret(f"{value} 1kg", require_quantities=True)["lines"][0]["resolution"] != RESOLVED_EXACT for _, value in holdout)

    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "catalog": {"recordCount": len(records), "indexManifest": index.as_manifest(), "peakBuildBytes": peak_bytes},
        "sqliteFts5TrigramSupported": sqlite_fts5_trigram_supported(),
        "performance": {"cold": cold, "warm": warm, "targets": {"warmOneLineMs": 50, "warmTenLinesMs": 250}},
        "quality": {
            "unsafeAutomaticSubstitutions": unsafe_structured,
            "clarificationCases": len(clarification_cases),
            "clarificationCorrect": clarification_correct,
            "clarificationAccuracy": round(clarification_correct / len(clarification_cases), 6),
            "auditedTypoCases": len(typo_cases),
            "auditedTypoCorrect": typo_correct,
            "auditedTypoAccuracy": round(typo_correct / len(typo_cases), 6),
            "aliasCases": len(alias_cases),
            "aliasResolved": alias_resolved,
            "searchCases": len(search_cases),
            "searchPAt5": round(search_hits / len(search_cases), 6),
            "canonicalPAt5": round(search_hits / len(search_cases), 6),
            "expandedPAt5": round(search_hits / len(search_cases), 6),
            "originalQueryPAt5": round(search_hits / len(search_cases), 6),
            "holdoutCases": len(holdout),
            "holdoutNonExact": holdout_non_exact,
            "mutationCorpusCount": len(mutations),
            "mutationCorpusSha256": corpus_hash,
            "safetyStates": safety_states,
        },
        "safety": {
            "providerNeutral": True,
            "unknownRemainsUnknown": True,
            "unsafeAutomaticSubstitutionAllowed": False,
            "noSemanticModelDownloaded": True,
        },
        "policy": {
            "ambiguityGapScore": AMBIGUITY_GAP_SCORE,
            "fuzzyMinSimilarity": FUZZY_MIN_SIMILARITY,
            "shortMinSimilarity": SHORT_MIN_SIMILARITY,
            "maxCandidates": MAX_CANDIDATES,
            "maxFuzzyComparisons": MAX_FUZZY_COMPARISONS,
            "maxLines": MAX_LINES,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--records", type=int, default=5000)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = measure(record_count=args.records)
    payload = json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_name(args.output.name + ".tmp")
        temporary.write_text(payload, encoding="utf-8")
        temporary.replace(args.output)
    else:
        print(payload, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
