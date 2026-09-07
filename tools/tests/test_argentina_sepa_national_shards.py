from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.argentina_sepa_mobile_search import search_mobile_shard
from tools.build_argentina_sepa_national_shards import (
    ARGENTINA_REGIONS,
    MOBILE_SHARD_FILE,
    MobileShardWriter,
    NationalShardError,
    _canonical_json,
    build_national_shards,
)
from tools.build_argentina_sepa_regional_snapshot import (
    QUALIFIED_POLICY_VERSION,
    QUALIFIED_SCHEMA_VERSION,
)
from tools.verify_argentina_sepa_national_shards import verify_national_shards


OUTER_SHA = "a" * 64
OUTER_BYTES = 1234
RELEASE = "2026-09-06"
PACKAGE_ONE = "b" * 64
PACKAGE_TWO = "c" * 64
VALID_GTIN = "7790070318398"


def _row(
    *,
    commerce: str,
    banner: str,
    store_id: str,
    province: str | None = "AR-C",
    product_id: str = VALID_GTIN,
    quantity: dict | None = None,
    quantity_status: str = "KNOWN",
    price: str = "1250.00",
    latitude: str | None = "-34.6037",
    longitude: str | None = "-58.3816",
    package: str = PACKAGE_ONE,
    promo: bool = False,
    reference: dict | None = None,
) -> dict:
    promotions = []
    if promo:
        promotions = [
            {
                "slot": 1,
                "price_ars": "999.00",
                "price_raw": "999.00",
                "condition": "Con tarjeta",
                "eligibility": "UNKNOWN",
            }
        ]
    product_name = "LECHE ENTERA" if product_id == VALID_GTIN else "PRODUCTO SIN GTIN"
    store = {
        "commerce_id": commerce,
        "banner_id": banner,
        "store_id": store_id,
        "name": "Fixture Store",
        "type": "Supermercado",
        "street": "Calle Uno",
        "number": "10",
        "locality": "Buenos Aires",
        "province": province,
        "postal_code": "1000",
        "latitude": latitude,
        "longitude": longitude,
        "geo_status": "VALID" if latitude is not None and longitude is not None else "GEO_INCOMPLETE",
    }
    return {
        "schema_version": QUALIFIED_SCHEMA_VERSION,
        "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
        "source": {
            "release_date": RELEASE,
            "outer_sha256": OUTER_SHA,
            "nested_package_name": "fixture-retailer.zip",
            "nested_package_sha256": package,
            "commerce_id": commerce,
            "banner_id": banner,
            "store_id": store_id,
            "product_row": int(store_id) + 1,
        },
        "product": {
            "provider_product_id": product_id,
            "provider_product_id_raw": product_id,
            "gtin": product_id if product_id == VALID_GTIN else None,
            "gtin_status": "VALID" if product_id == VALID_GTIN else "INVALID_OR_NOT_GTIN",
            "name": product_name,
            "brand": "FIXTURE BRAND",
        },
        "offer": {
            "list_price": {"amount": price, "currency": "ARS", "field": "productos_precio_lista", "raw": price},
            "reference_price": reference,
            "promotions": promotions,
            "availability": "UNKNOWN",
            "observation_time": None,
            "provider_update_time": "2026-09-06T12:00:00+00:00",
            "freshness_status": "FRESH",
        },
        "quantity": quantity,
        "quantity_status": quantity_status,
        "store": store,
    }


def _write_source(root: Path, rows: list[dict]) -> tuple[Path, Path]:
    root.mkdir(parents=True, exist_ok=True)
    accepted = root / "accepted-observations.ndjson.gz"
    with accepted.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as stream:
            for value in rows:
                stream.write(_canonical_json(value))
    accepted_hash = hashlib.sha256(accepted.read_bytes()).hexdigest()
    manifest = {
        "atomic_completion": True,
        "counts": {"accepted_rows": len(rows)},
        "files": {"accepted-observations.ndjson.gz": {"bytes": accepted.stat().st_size, "sha256": accepted_hash}},
        "manifest_schema_version": "argentina-sepa-manifest-v1",
        "policy_version": QUALIFIED_POLICY_VERSION,
        "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
        "release_date": RELEASE,
        "schema_version": QUALIFIED_SCHEMA_VERSION,
        "source": {
            "sha256": OUTER_SHA,
            "bytes": OUTER_BYTES,
            "license": "Creative Commons Attribution 4.0",
            "attribution": "Precios Claros - Base SEPA; source: fixture",
            "raw_data_committed": False,
        },
    }
    manifest_path = root / "manifest.json"
    manifest_path.write_bytes(_canonical_json(manifest))
    return accepted, manifest_path


def _build(root: Path, rows: list[dict], name: str = "national") -> tuple[Path, dict]:
    accepted, manifest = _write_source(root / "source", rows)
    output = root / name
    result = build_national_shards(
        accepted,
        manifest,
        output,
        generated_at="2026-09-07T12:00:00Z",
        expected_outer_sha256=OUTER_SHA,
        expected_outer_bytes=OUTER_BYTES,
    )
    return output, result


class ArgentinaSepaNationalShardsTest(unittest.TestCase):
    def _rows(self) -> list[dict]:
        return [
            _row(
                commerce="caba-commerce",
                banner="caba-banner",
                store_id="1",
                quantity={"unit": "GRAM", "value": "1000", "raw_value": "1", "raw_unit": "KG"},
                promo=True,
                reference={
                    "amount_ars": "1500.00",
                    "raw": "1500.00",
                    "quantity_raw": "1000",
                    "unit_raw": "GRAM",
                    "semantic_role": "reference_price_not_current_offer",
                },
            ),
            _row(
                commerce="caba-commerce",
                banner="caba-banner",
                store_id="2",
                product_id="12345",
                quantity=None,
                quantity_status="UNKNOWN",
                latitude=None,
                longitude=None,
                package=PACKAGE_TWO,
            ),
            _row(commerce="ba-commerce", banner="ba-banner", store_id="3", province="AR-B", quantity={"unit": "MILLILITRE", "value": "1000"}),
            _row(commerce="salta-commerce", banner="salta-banner", store_id="4", province="AR-A", price="7.50", quantity={"unit": "COUNT", "value": "1"}),
            _row(commerce="unknown-commerce", banner="unknown-banner", store_id="5", province=None),
            # Locality/name text must never silently turn this into AR-B.
            _row(commerce="text-commerce", banner="text-banner", store_id="6", province="Buenos Aires"),
        ]

    def test_all_regions_are_indexed_and_unknown_provinces_are_unpublished(self):
        with tempfile.TemporaryDirectory() as temp:
            output, index = _build(Path(temp), self._rows())
            result = verify_national_shards(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)
            self.assertEqual(len(index["regions"]), len(ARGENTINA_REGIONS))
            self.assertEqual(result["regions"], 24)
            self.assertEqual(index["totals"]["inputAcceptedObservations"], 6)
            self.assertEqual(index["totals"]["selectedAcceptedObservations"], 4)
            self.assertEqual(index["totals"]["unpublishedProvinceRows"], 2)
            self.assertEqual(index["totals"]["exactCrossRetailerGtins"], 1)
            self.assertEqual(
                [(item["classification"], item["value"], item["rows"]) for item in index["unpublishedProvinceEvidence"]],
                [("NONSTANDARD", "Buenos Aires", 1), ("UNKNOWN", None, 1)],
            )
            self.assertEqual(index["regions"][0]["provinceCode"], "AR-A")
            caba = next(item for item in index["regions"] if item["provinceCode"] == "AR-C")
            ba = next(item for item in index["regions"] if item["provinceCode"] == "AR-B")
            self.assertEqual(caba["counts"]["offers"], 2)
            self.assertEqual(ba["counts"]["offers"], 1)
            self.assertNotEqual(caba["shard"]["path"], ba["shard"]["path"])

    def test_mobile_tables_preserve_exact_evidence_and_search_projection(self):
        with tempfile.TemporaryDirectory() as temp:
            output, _ = _build(Path(temp), self._rows())
            caba_path = output / "regions" / "ar-caba" / MOBILE_SHARD_FILE
            search = search_mobile_shard(caba_path, "leche")
            self.assertEqual([item.product_evidence_key for item in search], [f"ar-sepa-product:caba-commerce:{VALID_GTIN}"])
            # Inspect through the adapter's temporary SQLite connection to
            # ensure prices, quantities, promotions and UNKNOWN boundaries are
            # not altered by integer/dictionary compaction.
            import tools.argentina_sepa_mobile_search as mobile

            with mobile.open_mobile_shard(caba_path) as connection:
                offer = connection.execute(
                    "SELECT m.amount,p.quantity_json,o.availability,o.release_date,r.semantic_role "
                    "FROM offers o JOIN money m ON m.money_id=o.list_price_id "
                    "JOIN products p ON p.product_id=o.product_id "
                    "LEFT JOIN reference_prices r ON r.reference_id=o.reference_price_id "
                    "ORDER BY o.offer_id LIMIT 1"
                ).fetchone()
                self.assertEqual(offer[0], "1250.00")
                self.assertEqual(json.loads(offer[1])["unit"], "GRAM")
                self.assertEqual(json.loads(offer[1])["value"], "1000")
                self.assertEqual(offer[2:], ("UNKNOWN", RELEASE, "reference_price_not_current_offer"))
                self.assertEqual(connection.execute("SELECT COUNT(*) FROM promotions").fetchone()[0], 1)
                invalid = connection.execute("SELECT gtin,gtin_status,quantity_status FROM products WHERE provider_product_id='12345'").fetchone()
                self.assertEqual(invalid, (None, "INVALID_OR_NOT_GTIN", "UNKNOWN"))

    def test_repeat_runs_are_byte_identical(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first, _ = _build(root / "one", self._rows())
            second, _ = _build(root / "two", self._rows())
            first_files = sorted(path.relative_to(first) for path in first.rglob("*") if path.is_file())
            second_files = sorted(path.relative_to(second) for path in second.rglob("*") if path.is_file())
            self.assertEqual(first_files, second_files)
            for relative in first_files:
                self.assertEqual((first / relative).read_bytes(), (second / relative).read_bytes(), str(relative))

    def test_tampered_missing_incomplete_and_wrong_source_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            output, _ = _build(Path(temp), self._rows())
            shard = output / "regions" / "ar-caba" / MOBILE_SHARD_FILE
            tampered = bytearray(shard.read_bytes())
            tampered[-1] ^= 1
            shard.write_bytes(tampered)
            with self.assertRaises(NationalShardError):
                verify_national_shards(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

            output, _ = _build(Path(temp), self._rows(), name="missing")
            (output / "regions" / "ar-b" / MOBILE_SHARD_FILE).unlink()
            with self.assertRaises(NationalShardError):
                verify_national_shards(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

            output, _ = _build(Path(temp), self._rows(), name="incomplete")
            index_path = output / "index.json"
            index = json.loads(index_path.read_bytes())
            index["completionState"] = "PARTIAL"
            index_path.write_bytes(_canonical_json(index))
            with self.assertRaises(NationalShardError):
                verify_national_shards(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

            output, _ = _build(Path(temp), self._rows(), name="wrong-source")
            with self.assertRaises(NationalShardError):
                verify_national_shards(output, expected_outer_sha256="d" * 64, expected_outer_bytes=OUTER_BYTES)

    def test_cross_region_selector_cannot_be_relabelled(self):
        with tempfile.TemporaryDirectory() as temp:
            output, _ = _build(Path(temp), self._rows())
            index_path = output / "index.json"
            index = json.loads(index_path.read_bytes())
            caba = next(item for item in index["regions"] if item["provinceCode"] == "AR-C")
            caba["selector"]["value"] = "AR-B"
            index_path.write_bytes(_canonical_json(index))
            with self.assertRaises(NationalShardError):
                verify_national_shards(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

    def test_failed_generation_removes_unexposed_partial_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            accepted, manifest = _write_source(root / "source", self._rows())
            output = root / "out"
            original_add = MobileShardWriter.add

            def fail_after_first_row(self, raw, *, source):
                original_add(self, raw, source=source)
                raise NationalShardError("fixture interruption")

            with patch.object(MobileShardWriter, "add", fail_after_first_row):
                with self.assertRaises(NationalShardError):
                    build_national_shards(
                        accepted,
                        manifest,
                        output,
                        generated_at="2026-09-07T12:00:00Z",
                        expected_outer_sha256=OUTER_SHA,
                        expected_outer_bytes=OUTER_BYTES,
                    )
            self.assertFalse(output.exists())
            self.assertEqual(list(root.glob(f".{output.name}.partial-*")), [])


if __name__ == "__main__":
    unittest.main()
