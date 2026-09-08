from __future__ import annotations

import unittest

from fastapi.testclient import TestClient

from backend.app import create_app
from backend.service import MAX_REQUEST_BYTES


class FakeService:
    def health(self):
        return {"status": "ok"}

    def ready(self):
        return True, {"service": "ready", "releaseId": "fixture"}

    def status(self):
        return {"service": "ready", "backendCodeQualified": True, "androidNetworkingAuthorized": False}

    def search(self, payload, *, correlation_id=None):
        return {"query": payload["query"], "requestId": correlation_id or "fixture"}

    def shop(self, payload, *, correlation_id=None):
        return {"requestId": correlation_id or "fixture", "result": {"decision": {"bestSensibleChoice": {"selectedPlan": None}}}}


class BackendHttpTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(create_app(FakeService()))

    def test_health_ready_and_status_are_small(self):
        self.assertEqual(self.client.get("/healthz").status_code, 200)
        self.assertEqual(self.client.get("/readyz").status_code, 200)
        self.assertEqual(self.client.get("/v1/status").status_code, 200)

    def test_unknown_fields_and_oversized_body_are_rejected(self):
        payload = {"latitude": "-34", "longitude": "-58", "radiusKm": "5", "query": "leche", "unexpected": 1}
        self.assertEqual(self.client.post("/v1/search", json=payload).status_code, 422)
        huge = b"{" + b"\"x\":" + (b"\"a\"" * MAX_REQUEST_BYTES) + b"}"
        self.assertEqual(self.client.post("/v1/search", content=huge, headers={"content-type": "application/json"}).status_code, 413)

    def test_search_contract_rejects_non_string_decimal_inputs(self):
        payload = {"latitude": -34, "longitude": "-58", "radiusKm": "5", "query": "leche"}
        self.assertEqual(self.client.post("/v1/search", json=payload).status_code, 422)
        payload["latitude"] = "-34"
        response = self.client.post("/v1/search", json=payload, headers={"x-request-id": "test-request"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["requestId"], "test-request")


if __name__ == "__main__":
    unittest.main()
