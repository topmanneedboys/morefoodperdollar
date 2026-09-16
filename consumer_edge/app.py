"""Production composition for the M12.1 consumer edge.

The edge is intentionally not wired to M11 here.  M12.2 will add a separate
server-side identity/upstream adapter after its IAM and private-ingress gates
are verified.  Until then, an unconfigured edge fails closed with a generic
upstream-unavailable response.
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from typing import Any

from backend.consumer_edge import (
    EdgeConfig,
    EdgeUpstream,
    EdgeUpstreamResponse,
    FirebaseAdminAppCheckVerifier,
    create_consumer_edge_app,
)
from .cloud_run import CloudRunConfigurationError, create_cloud_run_upstream_from_env


class ConfigurationRequiredUpstream:
    """Fail-closed placeholder until the separately qualified M12.2 adapter."""

    async def forward(
        self,
        *,
        method: str,
        path: str,
        body: bytes,
        headers: Mapping[str, str],
        request_id: str,
    ) -> EdgeUpstreamResponse:
        raise RuntimeError("private M11 upstream is not configured")


def _app_ids() -> frozenset[str]:
    raw = os.environ.get("VALUEPILOT_FIREBASE_APP_IDS", "")
    values = frozenset(part.strip() for part in raw.split(",") if part.strip())
    if any(len(value) > 256 for value in values):
        raise RuntimeError("VALUEPILOT_FIREBASE_APP_IDS contains an invalid value")
    return values


def create_app(
    *,
    verifier: Any | None = None,
    upstream: EdgeUpstream | None = None,
    config: EdgeConfig = EdgeConfig(),
):
    """Compose the edge, requiring explicit production configuration."""

    if verifier is None:
        project_id = os.environ.get("VALUEPILOT_FIREBASE_PROJECT_ID", "")
        if not project_id:
            raise RuntimeError("VALUEPILOT_FIREBASE_PROJECT_ID is required")
        verifier = FirebaseAdminAppCheckVerifier(project_id, allowed_app_ids=_app_ids())
    if upstream is None:
        if all(os.environ.get(name, "") for name in (
            "VALUEPILOT_M11_BASE_URL",
            "VALUEPILOT_M11_AUDIENCE",
            "VALUEPILOT_M11_ALLOWED_HOSTS",
        )):
            try:
                upstream = create_cloud_run_upstream_from_env(
                    max_response_bytes=config.max_response_bytes,
                    timeout_seconds=config.upstream_timeout_seconds,
                )
            except CloudRunConfigurationError:
                # A malformed target must stop startup rather than become an
                # arbitrary outbound proxy.  A completely absent target keeps
                # the local/default composition's existing fail-closed seam.
                raise
        else:
            upstream = ConfigurationRequiredUpstream()
    return create_consumer_edge_app(verifier=verifier, upstream=upstream, config=config)


app = None
if os.environ.get("VALUEPILOT_EDGE_IMPORT_PRODUCTION", "") == "1":
    app = create_app()


__all__ = ["ConfigurationRequiredUpstream", "app", "create_app"]
