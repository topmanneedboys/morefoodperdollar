from __future__ import annotations

import asyncio
import json
import unittest

from fastapi.testclient import TestClient

from backend.consumer_edge import (
    BoundedRateLimiter,
    EdgeConfig,
    EdgeUpstreamResponse,
    FirebaseAdminAppCheckVerifier,
    create_consumer_edge_app,
    parse_bounded_json,
)


class FakeVerifier:
    def __init__(self, claims=None):
        self.claims = {"sub": "app-test"} if claims is None else claims
        self.tokens: list[str] = []

    def verify(self, token: str):
        self.tokens.append(token)
        if token != "valid-token":
            raise ValueError("invalid token")
        return self.claims


class FakeUpstream:
    def __init__(self, response=None):
        self.response = response or EdgeUpstreamResponse(200, b'{"ok":true}')
        self.calls: list[dict[str, object]] = []

    async def forward(self, **kwargs):
        self.calls.append(kwargs)
        return self.response


class SlowUpstream(FakeUpstream):
    async def forward(self, **kwargs):
        self.calls.append(kwargs)
        await asyncio.sleep(0.2)
        return self.response


class ConsumerEdgeTests(unittest.TestCase):
    def setUp(self):
        self.verifier = FakeVerifier()
        self.upstream = FakeUpstream()
        self.client = TestClient(
            create_consumer_edge_app(verifier=self.verifier, upstream=self.upstream),
            base_url="https://testserver",
        )
        self.auth = {"X-Firebase-AppCheck": "valid-token"}

    def test_default_timeout_budget_is_finite_and_cold_start_tolerant(self):
        config = EdgeConfig()
        self.assertEqual(config.upstream_timeout_seconds, 30.0)
        self.assertGreater(config.upstream_timeout_seconds, config.queue_timeout_seconds)
        self.assertLessEqual(config.upstream_timeout_seconds, 30.0)

    def test_plain_http_is_rejected_before_authentication(self):
        verifier = FakeVerifier()
        upstream = FakeUpstream()
        client = TestClient(create_consumer_edge_app(verifier=verifier, upstream=upstream))
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json(), {"error": "HTTPS_REQUIRED"})
        self.assertEqual(verifier.tokens, [])
        self.assertEqual(upstream.calls, [])

    def test_cloud_run_forwarded_https_scheme_is_accepted(self):
        response = TestClient(
            create_consumer_edge_app(verifier=self.verifier, upstream=self.upstream)
        ).get("/v1/status", headers={**self.auth, "X-Forwarded-Proto": "https"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(self.upstream.calls), 1)

    def test_valid_status_is_forwarded_without_app_check_or_admin_routes(self):
        response = self.client.get("/v1/status", headers={**self.auth, "X-Request-ID": "status_1"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"ok": True})
        self.assertEqual(len(self.upstream.calls), 1)
        call = self.upstream.calls[0]
        self.assertEqual(call["method"], "GET")
        self.assertEqual(call["path"], "/v1/status")
        self.assertEqual(call["body"], b"")
        self.assertEqual(call["headers"], {"accept": "application/json", "x-request-id": "status_1"})
        self.assertNotIn("valid-token", str(call["headers"]))
        self.assertEqual(response.headers["x-request-id"], "status_1")
        self.assertEqual(self.client.get("/healthz").status_code, 404)
        self.assertEqual(self.client.get("/admin/active-release").status_code, 404)

    def test_valid_post_is_forwarded_as_json_with_query_rejected(self):
        payload = {"text": "arroz 1 kg"}
        response = self.client.post("/v1/interpret", json=payload, headers=self.auth)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.upstream.calls[0]["method"], "POST")
        self.assertEqual(self.upstream.calls[0]["path"], "/v1/interpret")
        self.assertEqual(json.loads(self.upstream.calls[0]["body"]), payload)
        self.assertEqual(self.upstream.calls[0]["headers"]["content-type"], "application/json")
        self.assertEqual(self.client.post("/v1/interpret?text=secret", json=payload, headers=self.auth).status_code, 400)

    def test_missing_invalid_and_unidentified_app_check_fail_closed(self):
        self.assertEqual(self.client.get("/v1/status").status_code, 401)
        self.assertEqual(self.client.get("/v1/status", headers={"X-Firebase-AppCheck": "bad"}).status_code, 401)
        self.verifier.claims = {}
        self.assertEqual(self.client.get("/v1/status", headers=self.auth).status_code, 401)
        self.assertEqual(self.upstream.calls, [])

    def test_unknown_method_and_content_type_are_rejected_before_upstream(self):
        response = self.client.post("/v1/status", headers=self.auth, content=b"{}")
        self.assertEqual(response.status_code, 405)
        self.assertEqual(response.headers["allow"], "GET")
        response = self.client.post("/v1/shop", headers=self.auth, content=b"{}")
        self.assertEqual(response.status_code, 415)
        response = self.client.post("/v1/shop", headers={**self.auth, "Content-Type": "text/plain"}, content=b"{}")
        self.assertEqual(response.status_code, 415)
        self.assertEqual(self.upstream.calls, [])

    def test_get_body_is_rejected(self):
        response = self.client.request("GET", "/v1/status", headers={**self.auth, "Content-Type": "application/json"}, content=b"{}")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json(), {"error": "GET_BODY_NOT_ALLOWED"})
        self.assertEqual(self.upstream.calls, [])

    def test_pathological_content_length_is_rejected_without_edge_failure(self):
        response = self.client.post(
            "/v1/shop",
            headers={**self.auth, "Content-Type": "application/json", "Content-Length": "9" * 5000},
            content=b"{}",
        )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json(), {"error": "INVALID_REQUEST"})
        self.assertEqual(self.upstream.calls, [])

    def test_malformed_duplicate_and_non_object_json_fail_closed(self):
        for body in (b"{", b'{"a":1,"a":2}', b"[]"):
            response = self.client.post("/v1/shop", headers={**self.auth, "Content-Type": "application/json"}, content=body)
            self.assertEqual(response.status_code, 400)
            self.assertEqual(response.json(), {"error": "INVALID_JSON"})
        self.assertEqual(self.upstream.calls, [])

    def test_request_and_json_structure_bounds_are_enforced(self):
        config = EdgeConfig(max_request_bytes=32, max_json_array_items=2, max_json_string_bytes=4)
        verifier = FakeVerifier()
        upstream = FakeUpstream()
        client = TestClient(
            create_consumer_edge_app(verifier=verifier, upstream=upstream, config=config),
            base_url="https://testserver",
        )
        oversized = client.post("/v1/shop", headers={**self.auth, "Content-Type": "application/json"}, content=b'{"value":"123456789"}')
        self.assertEqual(oversized.status_code, 400)
        self.assertEqual(oversized.json(), {"error": "INVALID_JSON"})
        too_many = client.post("/v1/shop", headers={**self.auth, "Content-Type": "application/json"}, content=b'{"items":[1,2,3]}')
        self.assertEqual(too_many.status_code, 400)
        too_large = client.post("/v1/shop", headers={**self.auth, "Content-Type": "application/json"}, content=b"x" * 33)
        self.assertEqual(too_large.status_code, 413)
        self.assertEqual(upstream.calls, [])

    def test_upstream_success_is_bounded_and_invalid_payload_is_sanitized(self):
        invalid = FakeUpstream(EdgeUpstreamResponse(200, b"not-json"))
        client = TestClient(
            create_consumer_edge_app(verifier=self.verifier, upstream=invalid),
            base_url="https://testserver",
        )
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json(), {"error": "UPSTREAM_INVALID_RESPONSE"})
        huge = FakeUpstream(EdgeUpstreamResponse(200, b"{}"))
        client = TestClient(
            create_consumer_edge_app(
                verifier=self.verifier,
                upstream=huge,
                config=EdgeConfig(max_response_bytes=1),
            ),
            base_url="https://testserver",
        )
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json(), {"error": "UPSTREAM_RESPONSE_TOO_LARGE"})

        array_limited = FakeUpstream(EdgeUpstreamResponse(200, b'{"items":[1,2,3]}'))
        client = TestClient(
            create_consumer_edge_app(
                verifier=self.verifier,
                upstream=array_limited,
                config=EdgeConfig(max_json_array_items=2),
            ),
            base_url="https://testserver",
        )
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json(), {"error": "UPSTREAM_INVALID_RESPONSE"})

    def test_upstream_errors_do_not_leak_body_or_headers(self):
        upstream = FakeUpstream(
            EdgeUpstreamResponse(
                500,
                b"private stack trace",
                headers={"X-Internal-Secret": "do-not-forward"},
            )
        )
        client = TestClient(
            create_consumer_edge_app(verifier=self.verifier, upstream=upstream),
            base_url="https://testserver",
        )
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json(), {"error": "UPSTREAM_UNAVAILABLE"})
        self.assertNotIn("private", response.text)
        self.assertNotIn("X-Internal-Secret", response.headers)

    def test_upstream_timeout_is_bounded(self):
        client = TestClient(
            create_consumer_edge_app(
                verifier=self.verifier,
                upstream=SlowUpstream(),
                config=EdgeConfig(upstream_timeout_seconds=0.01, queue_timeout_seconds=0.01),
            ),
            base_url="https://testserver",
        )
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 504)
        self.assertEqual(response.json(), {"error": "UPSTREAM_TIMEOUT"})

    def test_rate_limiter_is_bounded_and_resets_deterministically(self):
        limiter = BoundedRateLimiter(limit=1, window_seconds=10, max_keys=2, clock=lambda: 0.0)
        self.assertTrue(limiter.allow("b", now=0))
        self.assertFalse(limiter.allow("b", now=1))
        self.assertTrue(limiter.allow("b", now=10))
        self.assertTrue(limiter.allow("a", now=10))
        self.assertTrue(limiter.allow("c", now=10))
        self.assertEqual(limiter.size, 2)

    def test_rate_limit_is_exposed_as_generic_429(self):
        config = EdgeConfig(rate_limit_requests=1, rate_limit_window_seconds=60)
        verifier = FakeVerifier()
        upstream = FakeUpstream()
        client = TestClient(
            create_consumer_edge_app(verifier=verifier, upstream=upstream, config=config),
            base_url="https://testserver",
        )
        self.assertEqual(client.get("/v1/status", headers=self.auth).status_code, 200)
        response = client.get("/v1/status", headers=self.auth)
        self.assertEqual(response.status_code, 429)
        self.assertEqual(response.json(), {"error": "RATE_LIMITED"})
        self.assertEqual(len(upstream.calls), 1)

    def test_bounded_json_parser_rejects_deep_values_and_non_finite_numbers(self):
        config = EdgeConfig(max_json_depth=2)
        with self.assertRaises(ValueError):
            parse_bounded_json(b'{"a":{"b":{"c":1}}}', config=config)
        with self.assertRaises(ValueError):
            parse_bounded_json(b'{"a":NaN}', config=config)

    def test_firebase_verifier_validates_project_and_allow_list_configuration(self):
        verifier = FirebaseAdminAppCheckVerifier("valuepilot-argentina", allowed_app_ids=frozenset({"app-1"}))
        self.assertEqual(verifier._project_id, "valuepilot-argentina")
        with self.assertRaises(ValueError):
            FirebaseAdminAppCheckVerifier("not valid")
        with self.assertRaises(ValueError):
            FirebaseAdminAppCheckVerifier("valuepilot-argentina", allowed_app_ids=frozenset({""}))


if __name__ == "__main__":
    unittest.main()

