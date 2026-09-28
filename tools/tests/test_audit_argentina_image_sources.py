import gzip
import json
import tempfile
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from tools.audit_argentina_image_sources import (
    ImageSourceAuditError,
    _request_batch,
    audit_sample,
    select_sample,
)


class _FakeResponse:
    def __init__(self, payload: dict, status: int = 200) -> None:
        self.status = status
        self._body = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def read(self, limit: int = -1) -> bytes:
        return self._body if limit < 0 else self._body[:limit]


def _observation(gtin: str, name: str, brand: str = "Marca", *, yerba: bool = False) -> dict:
    actual_name = f"YERBA {name}" if yerba else name
    return {
        "product": {
            "brand": brand,
            "gtin": gtin,
            "gtin_status": "VALID",
            "name": actual_name,
            "provider_product_id": gtin,
        },
        "quantity": {"unit": "COUNT", "value": "1"},
        "source": {"commerce_id": "10"},
    }


class ArgentinaImageSourceAuditTests(unittest.TestCase):
    def test_sample_is_repeatable_and_reserves_yerba(self) -> None:
        gtins = [
            "7793670000670",
            "7790710334641",
            "7790710334573",
            "7790387013627",
            "7790070318398",
            "7791293047102",
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "accepted.ndjson.gz"
            with gzip.open(path, "wt", encoding="utf-8", newline="\n") as handle:
                for index, gtin in enumerate(gtins):
                    handle.write(
                        json.dumps(
                            _observation(gtin, f"Producto {index}", yerba=index < 4),
                            ensure_ascii=False,
                        )
                        + "\n"
                    )
            first, first_meta = select_sample(path, sample_size=5, yerba_minimum=3)
            second, second_meta = select_sample(path, sample_size=5, yerba_minimum=3)
            self.assertEqual(first, second)
            self.assertEqual(first_meta, second_meta)
            self.assertEqual(5, len(first))
            self.assertGreaterEqual(
                sum("yerba" in item["name"].casefold() for item in first), 3
            )

    def test_request_batch_is_bounded_and_uses_requested_codes(self) -> None:
        calls: list[str] = []

        def opener(request, timeout):
            calls.append(request.full_url)
            return _FakeResponse({"products": [{"code": "7793670000670"}]})

        payload = _request_batch(
            "https://world.openfoodfacts.net/api/v2/search",
            ["7793670000670"],
            user_agent="ValuePilot-test",
            timeout_seconds=2,
            opener=opener,
        )
        self.assertEqual(1, len(payload["products"]))
        query = parse_qs(urlparse(calls[0]).query)
        self.assertEqual(["7793670000670"], query["code"])
        self.assertIn("selected_images", query["fields"][0])

    def test_audit_reuses_exact_manifest_and_never_claims_display(self) -> None:
        gtins = [
            "7793670000670",
            "7790710334641",
            "7790710334573",
            "7790070318398",
        ]
        products = {
            gtin: {
                "code": gtin,
                "product_name": (f"YERBA Producto {index}" if index < 2 else f"Producto {index}"),
                "brands": "Marca",
                "quantity": "1 unit",
                "image_front_url": f"https://images.openfoodfacts.net/images/products/{gtin}/front_es.1.400.jpg",
                "selected_images": {},
            }
            for index, gtin in enumerate(gtins)
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            observations = root / "accepted.ndjson.gz"
            with gzip.open(observations, "wt", encoding="utf-8", newline="\n") as handle:
                for index, gtin in enumerate(gtins):
                    handle.write(
                        json.dumps(
                            _observation(gtin, f"Producto {index}", yerba=index < 2),
                            ensure_ascii=False,
                        )
                        + "\n"
                    )
            policy = Path(__file__).parents[1] / "data" / "open_food_facts_image_rights_policy.json"

            def opener(request, timeout):
                query = parse_qs(urlparse(request.full_url).query)
                requested = query["code"][0].split(",")
                return _FakeResponse({"products": [products[code] for code in requested]})

            report = audit_sample(
                observations,
                root / "output",
                sample_size=4,
                yerba_minimum=2,
                endpoint="https://world.openfoodfacts.net/api/v2/search",
                evaluated_at="2026-09-27T12:00:00Z",
                source_release="argentina-sepa-2026-09-06",
                source_sha256="a" * 64,
                coverage_reference=None,
                source_rights_policy=policy,
                batch_size=4,
                min_request_interval_seconds=0,
                user_agent="ValuePilot-test",
                timeout_seconds=2,
                opener=opener,
            )
            self.assertEqual(4, report["sample"]["sampleSizeSelected"])
            self.assertEqual(4, report["api"]["sampleProductsReturned"])
            self.assertEqual(4, report["manifest"]["coverage"]["strictExactIdentityCount"])
            self.assertEqual(4, report["manifest"]["coverage"]["rightsQualifiedImageEvidenceIdentityCount"])
            self.assertEqual(0, report["manifest"]["coverage"]["displayEligibleIdentityCount"])
            self.assertFalse(report["rights"]["commercialDisplayClaim"])

    def test_non_https_source_is_rejected(self) -> None:
        with self.assertRaises(ImageSourceAuditError):
            # The remaining arguments are intentionally omitted: endpoint
            # validation must fail before any local source is touched.
            audit_sample(
                Path("missing.ndjson.gz"),
                Path("output"),
                sample_size=1,
                yerba_minimum=0,
                endpoint="http://example.invalid/api",
                evaluated_at="2026-09-27T12:00:00Z",
                source_release="test",
                source_sha256=None,
                coverage_reference=None,
                source_rights_policy=None,
                batch_size=1,
                min_request_interval_seconds=0,
                user_agent="ValuePilot-test",
                timeout_seconds=2,
            )

    def test_unapproved_https_source_is_rejected(self) -> None:
        with self.assertRaises(ImageSourceAuditError):
            audit_sample(
                Path("missing.ndjson.gz"),
                Path("output"),
                sample_size=1,
                yerba_minimum=0,
                endpoint="https://example.invalid/api/v2/search",
                evaluated_at="2026-09-27T12:00:00Z",
                source_release="test",
                source_sha256=None,
                coverage_reference=None,
                source_rights_policy=None,
                batch_size=1,
                min_request_interval_seconds=0,
                user_agent="ValuePilot-test",
                timeout_seconds=2,
            )


if __name__ == "__main__":
    unittest.main()
