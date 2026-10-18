from __future__ import annotations

import asyncio
import json
import os
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from backend.consumer_edge import EdgeUpstreamResponse, create_consumer_edge_app
from consumer_edge.app import create_app
from consumer_edge.cloud_run import (
    CloudRunAuthenticationError,
    CloudRunConfigurationError,
    CloudRunOidcUpstream,
    CloudRunResponseTooLarge,
    CloudRunTarget,
    GoogleApplicationDefaultTokenProvider,
    create_cloud_run_upstream_from_env,
)


class FakeTokenProvider:
    def __init__(self, value: str = "header.payload.signature") -> None:
        self.value = value
        self.audiences: list[str] = []

    def get_token(self, audience: str) -> str:
        self.audiences.append(audience)
        return self.value


class FailingTokenProvider:
    def get_token(self, audience: str) -> str:
        raise CloudRunAuthenticationError("no token")


class FakeResponse:
    def __init__(self, status_code: int, chunks: list[bytes], content_type: str = "application/json") -> None:
        self.status_code = status_code
        self.headers = {"content-type": content_type}
        self._chunks = chunks
        self.closed = False

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        self.closed = True
        return False

    async def aiter_bytes(self):
        for chunk in self._chunks:
            await asyncio.sleep(0)
            yield chunk


class FakeClient:
    def __init__(self, response: FakeResponse) -> None:
        self.response = response
        self.calls: list[dict[str, object]] = []

    def stream(self, method: str, url: str, *, headers: dict[str, str], content: bytes):
        self.calls.append({"method": method, "url": url, "headers": headers, "content": content})
        return self.response


def target(**overrides) -> CloudRunTarget:
    values = {
        "base_url": "https://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app",
        "audience": "https://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app",
        "allowed_hosts": frozenset({"valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app"}),
    }
    values.update(overrides)
    return CloudRunTarget(**values)


class CloudRunUpstreamTests(unittest.TestCase):
    def test_default_private_upstream_timeout_matches_edge_budget(self):
        self.assertEqual(target().timeout_seconds, 12.0)

    def test_target_normalizes_exact_https_run_app_destination(self):
        value = target(base_url="https://VALUEPILOT-ARGENTINA-SA-EAST1-M72BV57HRQ-RJ.A.RUN.APP/")
        self.assertEqual(value.base_url, "https://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app")
        self.assertEqual(value.audience, value.base_url)
        self.assertEqual(value.url_for("/v1/status"), f"{value.base_url}/v1/status")

    def test_target_rejects_untrusted_urls_and_audience_mismatch(self):
        cases = [
            {"base_url": "http://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app"},
            {"base_url": "https://example.com"},
            {"base_url": "https://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app/internal"},
            {"allowed_hosts": frozenset({"other.run.app"})},
            {"audience": "https://valuepilot-argentina-us-east4-m72bv57hrq-uk.a.run.app"},
        ]
        for changes in cases:
            with self.subTest(changes=changes), self.assertRaises(CloudRunConfigurationError):
                target(**changes)

    def test_target_rejects_nonpositive_or_unbounded_limits(self):
        with self.assertRaises(CloudRunConfigurationError):
            target(max_response_bytes=0)
        with self.assertRaises(CloudRunConfigurationError):
            target(max_response_bytes=4 * 1024 * 1024 + 1)
        with self.assertRaises(CloudRunConfigurationError):
            target(timeout_seconds=31)

    def test_forward_preserves_route_body_and_semantic_response_without_copying_headers(self):
        body = b'{"releaseId":"2026-09-15","availability":"UNKNOWN","price":"12.34"}'
        response = FakeResponse(200, [body])
        client = FakeClient(response)
        provider = FakeTokenProvider()
        upstream = CloudRunOidcUpstream(target(), token_provider=provider, client=client)
        result = asyncio.run(
            upstream.forward(
                method="POST",
                path="/v1/search",
                body=body,
                headers={"authorization": "attacker-token", "x-extra": "ignored"},
                request_id="req_123",
            )
        )
        self.assertEqual(result, EdgeUpstreamResponse(200, body, "application/json"))
        self.assertEqual(provider.audiences, [target().audience])
        self.assertEqual(len(client.calls), 1)
        call = client.calls[0]
        self.assertEqual(call["method"], "POST")
        self.assertEqual(call["url"], f"{target().base_url}/v1/search")
        self.assertEqual(call["content"], body)
        self.assertEqual(
            call["headers"],
            {
                "Authorization": "Bearer header.payload.signature",
                "Accept": "application/json",
                "Content-Type": "application/json",
                "X-Request-ID": "req_123",
            },
        )

    def test_all_consumer_routes_are_forwardable_and_admin_routes_are_blocked(self):
        for method, path in (
            ("GET", "/v1/status"),
            ("POST", "/v1/search"),
            ("POST", "/v2/search"),
            ("POST", "/v1/interpret"),
            ("POST", "/v1/shop-text"),
            ("POST", "/v1/shop"),
        ):
            with self.subTest(method=method, path=path):
                client = FakeClient(FakeResponse(200, [b"{}"]))
                upstream = CloudRunOidcUpstream(target(), token_provider=FakeTokenProvider(), client=client)
                result = asyncio.run(
                    upstream.forward(method=method, path=path, body=b"", headers={}, request_id="route")
                )
                self.assertEqual(result.status_code, 200)
                self.assertEqual(client.calls[0]["url"], f"{target().base_url}{path}")
        for method, path in (("GET", "/admin"), ("POST", "/v1/status"), ("GET", "/v2/search"), ("POST", "/v1/unknown")):
            with self.subTest(method=method, path=path), self.assertRaises(CloudRunConfigurationError):
                asyncio.run(
                    CloudRunOidcUpstream(
                        target(), token_provider=FakeTokenProvider(), client=FakeClient(FakeResponse(200, [b"{}"]))
                    ).forward(method=method, path=path, body=b"", headers={}, request_id="route")
                )

    def test_v2_search_travels_through_appcheck_and_oidc_without_forwarding_client_tokens(self):
        class Verifier:
            def verify(self, token):
                if token != "valid-appcheck":
                    raise ValueError("unauthorized")
                return {"sub": "preview-app"}

        client = FakeClient(FakeResponse(200, [b'{"result":{"matches":[]}}']))
        provider = FakeTokenProvider()
        upstream = CloudRunOidcUpstream(target(), token_provider=provider, client=client)
        edge = TestClient(create_consumer_edge_app(verifier=Verifier(), upstream=upstream), base_url="https://testserver")
        query = {"query": "eggs", "language": "en", "selections": ["form:egg:whole_egg"], "pageSize": 20}
        self.assertEqual(edge.post("/v2/search", json=query).status_code, 401)
        self.assertEqual(client.calls, [])
        response = edge.post("/v2/search", json=query, headers={"X-Firebase-AppCheck": "valid-appcheck", "Authorization": "attacker-token"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(client.calls), 1)
        call = client.calls[0]
        self.assertEqual(call['url'], f"{target().base_url}/v2/search")
        self.assertEqual(json.loads(call['content']), query)
        self.assertEqual(call['headers']['Authorization'], 'Bearer header.payload.signature')
        self.assertNotIn('valid-appcheck', str(call['headers']))
        self.assertNotIn('attacker-token', str(call['headers']))

    def test_sync_and_async_token_providers_are_supported(self):
        class AsyncProvider:
            async def get_token(self, audience: str) -> str:
                return "async.token.value"

        for provider in (FakeTokenProvider("sync.token.value"), AsyncProvider()):
            with self.subTest(provider=type(provider).__name__):
                client = FakeClient(FakeResponse(200, [b"{}"]))
                result = asyncio.run(
                    CloudRunOidcUpstream(target(), token_provider=provider, client=client).forward(
                        method="GET", path="/v1/status", body=b"", headers={}, request_id="sync_async"
                    )
                )
                self.assertEqual(result.body, b"{}")

    def test_token_failure_or_malformed_token_stops_before_network(self):
        for provider in (FailingTokenProvider(), FakeTokenProvider("has whitespace"), FakeTokenProvider("x" * 9000)):
            with self.subTest(provider=type(provider).__name__):
                client = FakeClient(FakeResponse(200, [b"{}"]))
                with self.assertRaises((CloudRunAuthenticationError, RuntimeError)):
                    asyncio.run(
                        CloudRunOidcUpstream(target(), token_provider=provider, client=client).forward(
                            method="GET", path="/v1/status", body=b"", headers={}, request_id="auth"
                        )
                    )
                self.assertEqual(client.calls, [])

    def test_oversized_private_response_is_streamed_and_rejected(self):
        response = FakeResponse(200, [b"1234", b"5678"])
        client = FakeClient(response)
        with self.assertRaises(CloudRunResponseTooLarge):
            asyncio.run(
                CloudRunOidcUpstream(
                    target(max_response_bytes=7), token_provider=FakeTokenProvider(), client=client
                ).forward(method="GET", path="/v1/status", body=b"", headers={}, request_id="bounded")
            )
        self.assertTrue(response.closed)

    def test_google_adc_provider_uses_audience_without_persisting_credentials(self):
        seen: list[str] = []

        def fetcher(audience: str) -> str:
            seen.append(audience)
            return "adc.token"

        token = asyncio.run(GoogleApplicationDefaultTokenProvider(fetcher=fetcher).get_token(target().audience))
        self.assertEqual(token, "adc.token")
        self.assertEqual(seen, [target().audience])

    def test_environment_builder_requires_explicit_destination_and_allowlist(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(CloudRunConfigurationError):
            create_cloud_run_upstream_from_env()
        host = "valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app"
        with patch.dict(
            os.environ,
            {
                "VALUEPILOT_M11_BASE_URL": f"https://{host}",
                "VALUEPILOT_M11_AUDIENCE": f"https://{host}",
                "VALUEPILOT_M11_ALLOWED_HOSTS": host,
            },
            clear=True,
        ):
            upstream = create_cloud_run_upstream_from_env()
            self.assertEqual(upstream.target.base_url, f"https://{host}")

    def test_edge_rejects_partial_private_target_configuration_at_startup(self):
        with patch.dict(
            os.environ,
            {
                "VALUEPILOT_M11_BASE_URL": "https://valuepilot-argentina-sa-east1-m72bv57hrq-rj.a.run.app",
            },
            clear=True,
        ), self.assertRaises(CloudRunConfigurationError):
            create_app(verifier=object())

    def test_edge_maps_oidc_failure_to_sanitized_unavailable_without_fallback(self):
        client = TestClient(
            create_consumer_edge_app(
                verifier=type("Verifier", (), {"verify": lambda self, token: {"sub": "app-test"}})(),
                upstream=CloudRunOidcUpstream(
                    target(), token_provider=FailingTokenProvider(), client=FakeClient(FakeResponse(200, [b"{}"]))
                ),
            ),
            base_url="https://testserver",
        )
        response = client.get("/v1/status", headers={"X-Firebase-AppCheck": "token"})
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json(), {"error": "UPSTREAM_UNAVAILABLE"})


if __name__ == "__main__":
    unittest.main()
