"""Bounded verified evidence cache with single-flight loading."""

from __future__ import annotations

import hashlib
import threading
from collections import OrderedDict
from dataclasses import dataclass
from typing import Callable, Generic, TypeVar

T = TypeVar("T")


class CacheError(RuntimeError):
    pass


@dataclass(frozen=True)
class CacheStats:
    hits: int
    misses: int
    evictions: int
    bytes: int
    entries: int
    inflight: int


class VerifiedEvidenceCache(Generic[T]):
    def __init__(self, max_bytes: int = 128 * 1024 * 1024):
        if max_bytes <= 0:
            raise ValueError("max_bytes must be positive")
        self.max_bytes = max_bytes
        self._items: OrderedDict[str, tuple[bytes, T]] = OrderedDict()
        self._inflight: dict[str, threading.Event] = {}
        self._errors: dict[str, BaseException] = {}
        self._bytes = 0
        self._hits = self._misses = self._evictions = 0
        self._condition = threading.Condition()

    @staticmethod
    def verify(data: bytes, *, expected_sha256: str, expected_bytes: int) -> None:
        if len(data) != expected_bytes:
            raise CacheError("cached evidence length mismatch")
        if hashlib.sha256(data).hexdigest() != expected_sha256:
            raise CacheError("cached evidence hash mismatch")

    def get_or_load(self, key: str, loader: Callable[[], tuple[bytes, T]], *, expected_sha256: str, expected_bytes: int) -> tuple[T, bool]:
        if not key or len(key) > 1024:
            raise CacheError("cache key is invalid")
        while True:
            with self._condition:
                current = self._items.get(key)
                if current is not None:
                    self._items.move_to_end(key)
                    self._hits += 1
                    return current[1], True
                event = self._inflight.get(key)
                if event is None:
                    event = threading.Event()
                    self._inflight[key] = event
                    self._misses += 1
                    owner = True
                else:
                    owner = False
            if owner:
                try:
                    data, value = loader()
                    self.verify(data, expected_sha256=expected_sha256, expected_bytes=expected_bytes)
                    if len(data) > self.max_bytes:
                        raise CacheError("evidence member exceeds cache bound")
                    with self._condition:
                        while self._bytes + len(data) > self.max_bytes and self._items:
                            _, (old_data, _) = self._items.popitem(last=False)
                            self._bytes -= len(old_data)
                            self._evictions += 1
                        self._items[key] = (data, value)
                        self._bytes += len(data)
                        self._inflight.pop(key, None)
                        event.set()
                        self._condition.notify_all()
                    return value, False
                except BaseException as exc:
                    with self._condition:
                        self._inflight.pop(key, None)
                        self._errors[key] = exc
                        event.set()
                        self._condition.notify_all()
                    raise
            event.wait()
            with self._condition:
                if key in self._errors:
                    error = self._errors.pop(key)
                    raise CacheError("single-flight evidence load failed") from error

    def stats(self) -> CacheStats:
        with self._condition:
            return CacheStats(self._hits, self._misses, self._evictions, self._bytes, len(self._items), len(self._inflight))

    def clear(self) -> None:
        with self._condition:
            self._items.clear()
            self._bytes = 0


__all__ = ["CacheError", "CacheStats", "VerifiedEvidenceCache"]
