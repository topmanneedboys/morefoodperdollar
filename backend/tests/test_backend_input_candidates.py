from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from collections import OrderedDict
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from backend.reader import (
    ArgentinaBackendReader,
    MAX_INPUT_CANDIDATE_UNION,
)
import backend.reader as reader_module
from tools.consumer_input_intelligence import (
    CANDIDATE_HORIZON_REACHED,
    CatalogIndex,
    CatalogRecord,
    ConsumerInputInterpreter,
    NEEDS_CLARIFICATION,
    parse_intent,
)
import tools.argentina_sepa_query as sepa_query
from tools.tests.test_consumer_input_intelligence import fixture_records


class _FixtureRouter:
    def __init__(self, contract: object):
        self.contract_value = contract

    def contract(self, region_id: str) -> object:
        if region_id != "ar-caba":
            raise AssertionError(region_id)
        return self.contract_value


def _write_search_fixture(root: Path, records: list[dict[str, object]]) -> SimpleNamespace:
    payload = b"".join(
        (json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
        for record in records
    )
    path = root / "search-index.jsonl.gz"
    path.write_bytes(gzip.compress(payload, mtime=0))
    descriptor = {
        "path": path.name,
        "bytes": path.stat().st_size,
        "uncompressedBytes": len(payload),
        "uncompressedSha256": hashlib.sha256(payload).hexdigest(),
    }
    return SimpleNamespace(
        region=SimpleNamespace(region_id="ar-caba"),
        root=root,
        search_descriptor=descriptor,
        bootstrap={"regions": [{"regionId": "ar-caba"}]},
    )


def _reader_for_records(root: Path, records: list[dict[str, object]]) -> ArgentinaBackendReader:
    contract = _write_search_fixture(root, records)
    reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
    reader.release = SimpleNamespace(release_id="fixture-release")
    reader.artifacts = SimpleNamespace(remote=False)
    reader.router = _FixtureRouter(contract)
    reader._search_cache = OrderedDict()
    reader._input_candidate_cache = OrderedDict()
    return reader


class BackendInputCandidateTests(unittest.TestCase):
    def test_raw_mapping_gate_matches_shared_catalog_policy(self) -> None:
        records = fixture_records() + [
            {
                "productEvidenceKey": "accented",
                "name": "Puré de Tomate 500 g",
                "brand": "Miércoles",
                "canonicalSearchAliases": ["tomate triturado"],
            },
            {
                "productEvidenceKey": "punctuated",
                "name": "Coca-Cola Zero 2.25 L",
                "brand": "Coca-Cola",
                "canonicalSearchAliases": [],
            },
        ]
        catalog = CatalogIndex.from_records(records)
        queries = ("arroz 1kg", "aroz 1kg", "puré de tomate 500g", "coka cola 2.25l", "Miércoles")
        intents = tuple(parse_intent(query, data=catalog.data) for query in queries)
        features = tuple(catalog.intent_features(intent) for intent in intents)
        for raw in records:
            record = CatalogRecord.from_mapping(raw)
            expected = tuple(index for index, intent in enumerate(intents) if catalog.record_matches_intent(record, intent, intent_features=features[index]))
            actual = catalog.raw_record_matches_intents(raw, features)
            self.assertEqual(actual, expected, raw["productEvidenceKey"])

    def test_bounded_path_matches_full_interpreter_for_qualified_input_corpus(self) -> None:
        records = fixture_records()
        full = ConsumerInputInterpreter(CatalogIndex.from_records(records))
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-differential-") as directory:
            reader = _reader_for_records(Path(directory), records)
            cases = (
                ("arroz 1kg", True),
                ("rice 1kg", True),
                ("aroz 1kg", True),
                ("coka cola 2.25l", True),
                ("coca zero 2.25l", True),
                ("tomate pasta 500g", True),
                ("2 Sprite", True),
                ("1 kg Coca-Cola", True),
                ("apple juice 1l", True),
                ("arroz\nleche\nmanteca", True),
            )
            for text, require_quantities in cases:
                with self.subTest(text=text):
                    expected = full.interpret(text, require_quantities=require_quantities)
                    actual = reader.interpret_text(text, require_quantities=require_quantities, region_ids=("ar-caba",))
                    self.assertEqual(actual["lineCount"], expected["lineCount"])
                    self.assertEqual(actual["safeRequestReady"], expected["safeRequestReady"])
                    self.assertEqual(actual["structuredItems"], expected["structuredItems"])
                    self.assertEqual(actual["clarifications"], expected["clarifications"])
                    for expected_line, actual_line in zip(expected["lines"], actual["lines"]):
                        for key in ("resolution", "recognizedProduct", "correction", "clarification", "structuredLine", "suggestions"):
                            self.assertEqual(actual_line.get(key), expected_line.get(key), key)

    def test_candidate_horizon_saturation_fails_closed(self) -> None:
        records = [
            {
                "productEvidenceKey": f"arroz-{index:04d}",
                "name": f"Arroz Marca {index:04d} 1 kg",
                "brand": None,
                "canonicalSearchAliases": [],
            }
            for index in range(300)
        ]
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-saturation-") as directory:
            reader = _reader_for_records(Path(directory), records)
            result = reader.interpret_text("arroz", region_ids=("ar-caba",))
            self.assertEqual(result["diagnostics"]["candidateBound"], 256)
            self.assertTrue(result["diagnostics"]["candidateSaturated"])
            self.assertEqual(result["diagnostics"]["saturatedLineIds"], ["item-1"])
            self.assertEqual(result["lines"][0]["resolution"], NEEDS_CLARIFICATION)
            self.assertEqual(result["lines"][0]["clarification"]["code"], CANDIDATE_HORIZON_REACHED)
            self.assertFalse(result["safeRequestReady"])
            self.assertEqual(result["structuredItems"], [])
            preparation = reader._prepare_input_candidates("arroz", ("ar-caba",))
            self.assertLessEqual(preparation.retained_candidates, MAX_INPUT_CANDIDATE_UNION)
            self.assertLessEqual(len(preparation.catalog.records), 256)

    def test_large_irrelevant_growth_is_not_retained_in_catalog_index(self) -> None:
        records = [
            {
                "productEvidenceKey": f"unrelated-{index:05d}",
                "name": f"Producto de prueba {index:05d}",
                "brand": None,
                "canonicalSearchAliases": [],
            }
            for index in range(30_000)
        ]
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-large-") as directory:
            reader = _reader_for_records(Path(directory), records)
            captured: list[int] = []
            original = CatalogIndex.from_records

            def capture(values: object, **kwargs: object):
                materialized = list(values)  # type: ignore[arg-type]
                captured.append(len(materialized))
                return original(materialized, **kwargs)

            with patch("backend.reader.CatalogIndex.from_records", side_effect=capture), patch(
                "backend.reader.CatalogRecord.from_mapping", wraps=CatalogRecord.from_mapping
            ) as from_mapping:
                preparation = reader._prepare_input_candidates("arroz", ("ar-caba",))
            self.assertEqual(preparation.scanned_records, len(records))
            self.assertEqual(preparation.retained_candidates, 0)
            self.assertEqual(len(preparation.catalog.records), 0)
            self.assertEqual(captured, [0])
            self.assertEqual(from_mapping.call_count, 0)
            self.assertLessEqual(preparation.retained_candidates, MAX_INPUT_CANDIDATE_UNION)

    def test_multi_query_search_consumes_one_score_and_one_recovery_pass(self) -> None:
        records = fixture_records()
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-access-") as directory:
            reader = _reader_for_records(Path(directory), records)
            contract = reader.router.contract("ar-caba")
            calls = 0
            original = sepa_query._iter_gzip_records

            def counted(*args: object, **kwargs: object):
                nonlocal calls
                calls += 1
                return original(*args, **kwargs)

            queries = ("arroz", "leche", "manteca", "Coca-Cola", "Sprite") * 2
            with patch.object(sepa_query, "_iter_gzip_records", side_effect=counted):
                values = reader._search_many(contract, queries)
            self.assertEqual(len(values), len(queries))
            self.assertEqual(calls, 2)
            self.assertLessEqual(len(reader._search_cache), len(set(queries)))
            reader._search_cache.clear()
            single = reader._search(contract, "arroz")
            reader._search_cache.clear()
            multi = reader._search_many(contract, ("arroz",))[0]
            self.assertEqual(single, multi)

    def test_one_five_and_ten_line_inputs_keep_the_union_bounded(self) -> None:
        records = fixture_records()
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-lines-") as directory:
            reader = _reader_for_records(Path(directory), records)
            for count in (1, 5, 10):
                with self.subTest(count=count):
                    text = "\n".join(("arroz 1kg", "leche 1l", "manteca 500g")[:count] or ["arroz 1kg"])
                    # Repeat a deterministic valid line when the requested
                    # count exceeds the small fixture's distinct vocabulary.
                    if count > 3:
                        text = "\n".join(("arroz 1kg", "leche 1l", "manteca 500g")[index % 3] for index in range(count))
                    result = reader.interpret_text(text, require_quantities=True, region_ids=("ar-caba",))
                    self.assertEqual(result["lineCount"], count)
                    self.assertLessEqual(result["diagnostics"]["retainedCandidateCount"], MAX_INPUT_CANDIDATE_UNION)
                    self.assertFalse(hasattr(reader, "_input_catalog_cache"))

    def test_ten_line_candidate_generation_streams_each_region_once(self) -> None:
        records = fixture_records()
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-stream-") as directory:
            reader = _reader_for_records(Path(directory), records)
            calls = 0
            original = reader_module._iter_gzip_records

            def counted(*args: object, **kwargs: object):
                nonlocal calls
                calls += 1
                return original(*args, **kwargs)

            text = "\n".join(("arroz 1kg", "leche 1l", "manteca 500g")[index % 3] for index in range(10))
            with patch("backend.reader._iter_gzip_records", side_effect=counted):
                result = reader.interpret_text(text, require_quantities=True, region_ids=("ar-caba",))
            self.assertEqual(result["lineCount"], 10)
            self.assertEqual(calls, 1)

    def test_query_specific_candidate_cache_is_bounded_and_warm_reuses_it(self) -> None:
        records = fixture_records()
        with tempfile.TemporaryDirectory(prefix="valuepilot-input-cache-") as directory:
            reader = _reader_for_records(Path(directory), records)
            calls = 0
            original = reader_module._iter_gzip_records

            def counted(*args: object, **kwargs: object):
                nonlocal calls
                calls += 1
                return original(*args, **kwargs)

            with patch("backend.reader._iter_gzip_records", side_effect=counted):
                first = reader.interpret_text("arroz", region_ids=("ar-caba",))
                second = reader.interpret_text("arroz", region_ids=("ar-caba",))
            self.assertEqual(calls, 1)
            self.assertFalse(first["diagnostics"]["candidateCacheHit"])
            self.assertTrue(second["diagnostics"]["candidateCacheHit"])
            for index in range(20):
                reader.interpret_text(f"arroz {index}", region_ids=("ar-caba",))
            self.assertLessEqual(len(reader._input_candidate_cache), 8)
            self.assertTrue(all(len(item.catalog.records) <= MAX_INPUT_CANDIDATE_UNION for item in reader._input_candidate_cache.values()))


if __name__ == "__main__":
    unittest.main()
