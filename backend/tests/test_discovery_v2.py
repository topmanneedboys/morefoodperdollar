from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from backend.app import create_app
from backend.service import BackendService
from tools.catalog_discovery import build_discovery_index


def _row(number: int, name: str) -> dict[str, object]:
    return {
        "productEvidenceKey": f"ar-sepa-product:v2:{number}",
        "providerProductId": f"v2-{number}",
        "name": name,
        "brand": "Marca",
        "familyCandidates": [{
            "family": "rice",
            "matchedTerms": ["arroz"],
            "observedForms": ["grain"],
            "role": "DIRECT",
        }],
        "aliases": [],
    }


class DiscoveryV2BackendTests(unittest.TestCase):
    def test_additive_route_is_disabled_without_explicit_gate(self) -> None:
        class FakeService:
            def health(self): return {"status": "ok"}
            def ready(self): return True, {"service": "ready"}
            def status(self): return {"service": "ready"}
            def discovery_search(self, payload, *, correlation_id=None): raise AssertionError("should not be called")

        # The HTTP boundary delegates only after pydantic validation.  A
        # service's explicit unavailable result remains a 503, never a v1
        # fallback.
        class DisabledService(FakeService):
            def discovery_search(self, payload, *, correlation_id=None):
                from backend.reader import BackendQueryError
                raise BackendQueryError("DISCOVERY_V2_UNAVAILABLE")

        with patch.dict(os.environ, {"VALUEPILOT_DISCOVERY_V2_ENABLED": "false"}, clear=False):
            client = TestClient(create_app(DisabledService()))
            response = client.post("/v2/search", json={"query": "rice"})
        self.assertEqual(response.status_code, 503)
        self.assertIn("DISCOVERY_V2_UNAVAILABLE", response.text)

    def test_route_returns_full_catalog_contract_when_sidecar_is_enabled(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "identities.jsonl"
            with source.open("w", encoding="utf-8", newline="\n") as handle:
                for number in range(3):
                    handle.write(json.dumps(_row(number, f"ARROZ LARGO {number} KG"), ensure_ascii=False) + "\n")
            index = root / "discovery.sqlite"
            build_discovery_index(source, index, release_id="v2-test")
            service = BackendService("", discovery_index_path=index, discovery_release_id="v2-test")
            try:
                with patch.dict(os.environ, {"VALUEPILOT_DISCOVERY_V2_ENABLED": "true"}, clear=False):
                    client = TestClient(create_app(service))
                    response = client.post("/v2/search", json={"query": "rice", "language": "en", "pageSize": 2})
                self.assertEqual(response.status_code, 200)
                body = response.json()
                self.assertEqual(body["apiContractVersion"], "valuepilot-discovery-http-v2")
                self.assertEqual(body["releaseId"], "v2-test")
                self.assertEqual(body["result"]["total"]["productCount"], 3)
                self.assertTrue(body["result"]["pagination"]["hasMore"])
                self.assertEqual(body["evidenceSemantics"]["prices"], "NOT_INCLUDED")
            finally:
                if service._discovery_index is not None:
                    service._discovery_index.close()


if __name__ == "__main__":
    unittest.main()
