#!/usr/bin/env python3
"""Read the Argentina SEPA mobile shard through the existing search core.

The shard is an offline distribution artifact.  This adapter deliberately
projects only product-identity fields into :func:`search_products`; prices,
availability, and ranking economics remain owned by the shared core and the
offer tables.  A compressed shard is decompressed into a temporary file and
is never copied into Android assets by this tool.
"""

from __future__ import annotations

import gzip
import shutil
import sqlite3
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterable, Iterator

try:
    from tools.argentina_sepa_search import MAX_CANDIDATES, MAX_RESULTS, SearchResult, search_products
except ModuleNotFoundError:  # direct ``python tools/argentina_sepa_mobile_search.py`` invocation
    from argentina_sepa_search import MAX_CANDIDATES, MAX_RESULTS, SearchResult, search_products


class MobileSearchError(ValueError):
    """A mobile shard could not be read as the expected offline format."""


@contextmanager
def open_mobile_shard(path: Path) -> Iterator[sqlite3.Connection]:
    """Open one gzip-compressed SQLite shard in a bounded temporary file."""

    path = Path(path)
    if not path.is_file():
        raise MobileSearchError(f"missing mobile shard: {path}")
    directory = tempfile.TemporaryDirectory(prefix="argentina-mobile-search-")
    raw_path = Path(directory.name) / "shard.sqlite"
    try:
        with gzip.open(path, "rb") as source, raw_path.open("wb") as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
        connection = sqlite3.connect(str(raw_path))
        try:
            yield connection
        finally:
            connection.close()
    except (OSError, EOFError, sqlite3.Error) as exc:
        raise MobileSearchError(f"invalid mobile shard: {path}") from exc
    finally:
        directory.cleanup()


def iter_mobile_products(connection: sqlite3.Connection) -> Iterable[dict[str, Any]]:
    """Yield the provider-neutral product identity projection in stable order."""

    for commerce_id, provider_product_id, gtin, name, brand in connection.execute(
        "SELECT commerce_id,provider_product_id,gtin,name,brand FROM products ORDER BY product_id"
    ):
        if not isinstance(commerce_id, str) or not isinstance(provider_product_id, str):
            continue
        if not isinstance(name, str) or not name:
            continue
        yield {
            "productEvidenceKey": f"ar-sepa-product:{commerce_id}:{provider_product_id}",
            "commerceId": commerce_id,
            "providerProductId": provider_product_id,
            "name": name,
            "brand": brand if isinstance(brand, str) else None,
            "gtin": gtin if isinstance(gtin, str) else None,
        }


def search_mobile_shard(
    path: Path,
    query: str,
    *,
    limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
) -> list[SearchResult]:
    """Run the bounded shared search over one mobile shard."""

    with open_mobile_shard(path) as connection:
        return search_mobile_connection(connection, query, limit=limit, max_candidates=max_candidates)


def search_mobile_connection(
    connection: sqlite3.Connection,
    query: str,
    *,
    limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
) -> list[SearchResult]:
    """Run search against an already-open shard (amortizes decompression)."""

    if not isinstance(connection, sqlite3.Connection):
        raise MobileSearchError("connection must be a SQLite connection")
    return search_products(iter_mobile_products(connection), query, limit=limit, max_candidates=max_candidates)


__all__ = ["MobileSearchError", "iter_mobile_products", "open_mobile_shard", "search_mobile_connection", "search_mobile_shard"]
