"""Catalog-wide, identity-only discovery for ValuePilot.

The package is deliberately independent of prices, offers, stock and network
credentials.  It builds an immutable SQLite sidecar from an authorized identity
release and evaluates bounded, typed discovery queries against the full index.
"""

from .index import (
    BUILD_SCHEMA_VERSION,
    DiscoveryError,
    DiscoveryIndex,
    DiscoveryQuery,
    DiscoveryResponse,
    IndexBuildReport,
    build_discovery_index,
    refresh_discovery_index,
)

__all__ = [
    "BUILD_SCHEMA_VERSION",
    "DiscoveryError",
    "DiscoveryIndex",
    "DiscoveryQuery",
    "DiscoveryResponse",
    "IndexBuildReport",
    "build_discovery_index",
    "refresh_discovery_index",
]
