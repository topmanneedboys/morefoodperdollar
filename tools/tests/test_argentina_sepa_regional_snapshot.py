from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.build_argentina_sepa_regional_snapshot import (
    QUALIFIED_POLICY_VERSION,
    QUALIFIED_SCHEMA_VERSION,
    RegionalSnapshotError,
    _canonical_json,
    build_regional_snapshot,
)
from tools.verify_argentina_sepa_regional_snapshot import verify_snapshot


OUTER_SHA = "a" * 64
OUTER_BYTES = 1234
RELEASE = "2026-09-06"
PACKAGE_ONE = "b" * 64
PACKAGE_TWO = "c" * 64


def row(*, commerce: str, banner: str, store_id: str, province: str = "AR-C", name: str = "LECHE ENTERA", product_id: str = "7790070318398", quantity: dict | None = None, quantity_status: str = "KNOWN", price: str = "1250.00", freshness: str = "FRESH", latitude: str | None = "-34.6037", longitude: str | None = "-58.3816", package: str = PACKAGE_ONE, promo: bool = False, locality: str = "Buenos Aires") -> dict:
    promotions = []
    if promo:
        promotions = [{"slot": 1, "price_ars": "999.00", "price_raw": "999.00", "condition": "Con tarjeta", "eligibility": "UNKNOWN"}]
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
            "gtin": product_id if product_id == "7790070318398" else None,
            "gtin_status": "VALID" if product_id == "7790070318398" else "INVALID_OR_NOT_GTIN",
            "name": name,
            "brand": "FIXTURE BRAND",
        },
        "offer": {
            "list_price": {"amount": price, "currency": "ARS", "field": "productos_precio_lista", "raw": price},
            "reference_price": None,
            "promotions": promotions,
            "availability": "UNKNOWN",
            "observation_time": None,
            "provider_update_time": "2026-09-06T12:00:00+00:00",
            "freshness_status": freshness,
        },
        "quantity": quantity,
        "quantity_status": quantity_status,
        "store": {
            "commerce_id": commerce,
            "banner_id": banner,
            "store_id": store_id,
            "name": "Fixture Store",
            "type": "Supermercado",
            "street": "Calle Uno",
            "number": "10",
            "locality": locality,
            "province": province,
            "postal_code": "1000",
            "latitude": latitude,
            "longitude": longitude,
            "geo_status": "VALID" if latitude is not None and longitude is not None else "GEO_INCOMPLETE",
        },
    }


def write_source(root: Path, rows: list[dict]) -> tuple[Path, Path]:
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
        "source": {"sha256": OUTER_SHA, "bytes": OUTER_BYTES, "license": "Creative Commons Attribution 4.0", "attribution": "Precios Claros - Base SEPA; source: fixture", "raw_data_committed": False},
    }
    manifest_path = root / "manifest.json"
    manifest_path.write_bytes(_canonical_json(manifest))
    return accepted, manifest_path


def read_gzip_jsonl(path: Path) -> list[dict]:
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        return [json.loads(line) for line in stream]


class ArgentinaCabaSnapshotTest(unittest.TestCase):
    def build(self, root: Path, rows: list[dict], name: str = "snapshot") -> tuple[Path, dict]:
        accepted, manifest = write_source(root, rows)
        output = root / name
        result = build_regional_snapshot(
            accepted,
            manifest,
            output,
            generated_at="2026-09-07T12:00:00Z",
            expected_outer_sha256=OUTER_SHA,
            expected_outer_bytes=OUTER_BYTES,
        )
        return output, result

    def test_exact_ar_c_selector_and_compact_tables(self):
        rows = [
            row(commerce="1", banner="1", store_id="1", quantity={"unit": "GRAM", "value": "1000"}, promo=True),
            row(commerce="2", banner="2", store_id="2", product_id="12345", quantity=None, quantity_status="UNKNOWN", latitude=None, longitude=None),
            row(commerce="9", banner="9", store_id="9", province="AR-B", locality="Ciudad Autónoma de Buenos Aires"),
        ]
        with tempfile.TemporaryDirectory() as temp:
            output, manifest = self.build(Path(temp), rows)
            verified = verify_snapshot(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)
            self.assertEqual(verified["offers"], 2)
            self.assertEqual(manifest["counts"]["selectedAcceptedObservations"], 2)
            stores = read_gzip_jsonl(output / "stores.jsonl.gz")
            products = read_gzip_jsonl(output / "products.jsonl.gz")
            offers = read_gzip_jsonl(output / "offers.jsonl.gz")
            promotions = read_gzip_jsonl(output / "promotions.jsonl.gz")
            self.assertEqual(len(stores), 2)
            self.assertEqual(len(products), 2)
            self.assertEqual(len(offers), 2)
            self.assertEqual(len(promotions), 1)
            by_id = {item["providerProductId"]: item for item in products}
            self.assertEqual(by_id["12345"]["gtinStatus"], "INVALID_OR_NOT_GTIN")
            self.assertEqual(by_id["7790070318398"]["quantityStatus"], "KNOWN")
            self.assertEqual(next(item for item in stores if item["storeId"] == "2")["geoStatus"], "GEO_INCOMPLETE")
            self.assertEqual(offers[0]["availability"], "UNKNOWN")
            self.assertEqual(promotions[0]["eligibility"], "UNKNOWN")

    def test_source_hash_and_stale_status_fail_closed(self):
        rows = [row(commerce="1", banner="1", store_id="1")]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            accepted, manifest = write_source(root, rows)
            with self.assertRaises(RegionalSnapshotError):
                build_regional_snapshot(accepted, manifest, root / "bad", generated_at="2026-09-07T12:00:00Z", expected_outer_sha256="d" * 64, expected_outer_bytes=OUTER_BYTES)
            stale = row(commerce="1", banner="1", store_id="1", freshness="STALE")
            accepted, manifest = write_source(root / "stale", [stale])
            with self.assertRaises(RegionalSnapshotError):
                build_regional_snapshot(accepted, manifest, root / "stale-out", generated_at="2026-09-07T12:00:00Z", expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

    def test_repeat_run_is_byte_deterministic_and_windows_paths_work(self):
        rows = [row(commerce="2", banner="2", store_id="2", product_id="12345"), row(commerce="1", banner="1", store_id="1")]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first, _ = self.build(root / "one", rows)
            second, _ = self.build(root / "two", rows)
            names = {"release.json", "manifest.json", "manifest.sha256", "integrity.json", "stores.jsonl.gz", "products.jsonl.gz", "offers.jsonl.gz", "promotions.jsonl.gz"}
            for name in names:
                self.assertEqual((first / name).read_bytes(), (second / name).read_bytes(), name)

    def test_tampered_and_partial_artifacts_rejected(self):
        rows = [row(commerce="1", banner="1", store_id="1")]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output, _ = self.build(root, rows)
            data = bytearray((output / "stores.jsonl.gz").read_bytes())
            data[-1] ^= 1
            (output / "stores.jsonl.gz").write_bytes(data)
            with self.assertRaises(RegionalSnapshotError):
                verify_snapshot(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)
            partial = root / "partial"
            partial.mkdir()
            (partial / "stores.jsonl.gz").write_bytes(b"partial")
            with self.assertRaises(RegionalSnapshotError):
                verify_snapshot(partial, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

    def test_missing_offer_reference_rejected_after_file_descriptor_refresh(self):
        rows = [row(commerce="1", banner="1", store_id="1")]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output, _ = self.build(root, rows)
            offers = read_gzip_jsonl(output / "offers.jsonl.gz")
            offers[0]["storeKey"] = "ar-sepa-store:missing:missing:missing"
            with (output / "offers.jsonl.gz").open("wb") as raw:
                with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as stream:
                    for value in offers:
                        stream.write(_canonical_json(value))
            manifest = json.loads((output / "manifest.json").read_bytes())
            path = output / "offers.jsonl.gz"
            uncompressed = b"".join(_canonical_json(value) for value in offers)
            manifest["files"]["offers.jsonl.gz"].update({"bytes": path.stat().st_size, "uncompressedBytes": len(uncompressed), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "uncompressedSha256": hashlib.sha256(uncompressed).hexdigest()})
            manifest_path = output / "manifest.json"
            manifest_path.write_bytes(_canonical_json(manifest))
            manifest_hash = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
            (output / "manifest.sha256").write_text(f"{manifest_hash}  manifest.json\n", encoding="ascii")
            integrity = json.loads((output / "integrity.json").read_bytes())
            integrity["manifestSha256"] = manifest_hash
            (output / "integrity.json").write_bytes(_canonical_json(integrity))
            with self.assertRaises(RegionalSnapshotError):
                verify_snapshot(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

    def test_missing_product_reference_rejected_after_file_descriptor_refresh(self):
        rows = [row(commerce="1", banner="1", store_id="1")]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output, _ = self.build(root, rows)
            offers = read_gzip_jsonl(output / "offers.jsonl.gz")
            offers[0]["productEvidenceKey"] = "ar-sepa-product:missing:missing"
            with (output / "offers.jsonl.gz").open("wb") as raw:
                with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as stream:
                    for value in offers:
                        stream.write(_canonical_json(value))
            manifest = json.loads((output / "manifest.json").read_bytes())
            path = output / "offers.jsonl.gz"
            uncompressed = b"".join(_canonical_json(value) for value in offers)
            manifest["files"]["offers.jsonl.gz"].update({"bytes": path.stat().st_size, "uncompressedBytes": len(uncompressed), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "uncompressedSha256": hashlib.sha256(uncompressed).hexdigest()})
            manifest_path = output / "manifest.json"
            manifest_path.write_bytes(_canonical_json(manifest))
            manifest_hash = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
            (output / "manifest.sha256").write_text(f"{manifest_hash}  manifest.json\n", encoding="ascii")
            integrity = json.loads((output / "integrity.json").read_bytes())
            integrity["manifestSha256"] = manifest_hash
            (output / "integrity.json").write_bytes(_canonical_json(integrity))
            with self.assertRaises(RegionalSnapshotError):
                verify_snapshot(output, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)

    def test_invalid_price_currency_and_coordinate_are_not_admitted(self):
        rows = [row(commerce="1", banner="1", store_id="1", price="0.00")]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            accepted, manifest = write_source(root, rows)
            with self.assertRaises(RegionalSnapshotError):
                build_regional_snapshot(accepted, manifest, root / "zero", generated_at="2026-09-07T12:00:00Z", expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)
            bad_currency = row(commerce="1", banner="1", store_id="1")
            bad_currency["offer"]["list_price"]["currency"] = "USD"
            accepted, manifest = write_source(root / "currency", [bad_currency])
            with self.assertRaises(RegionalSnapshotError):
                build_regional_snapshot(accepted, manifest, root / "currency-out", generated_at="2026-09-07T12:00:00Z", expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)
            bad_coordinate = row(commerce="1", banner="1", store_id="1", latitude="0.0", longitude="-58.3816")
            accepted, manifest = write_source(root / "coordinate", [bad_coordinate])
            with self.assertRaises(RegionalSnapshotError):
                build_regional_snapshot(accepted, manifest, root / "coordinate-out", generated_at="2026-09-07T12:00:00Z", expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)


if __name__ == "__main__":
    unittest.main()
