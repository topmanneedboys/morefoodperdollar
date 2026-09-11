#!/usr/bin/env python3
"""Immutable, range-addressable SearchPack v1 runtime and format helpers.

SearchPack is deliberately a provider-neutral read model.  Python owns
normalization and feature derivation; this module stores the resulting opaque
feature keys in sorted dictionaries and postings.  The optional Rust module
(``searchpack_native``) supplies FST, delta-varint, zstd, and checksum
primitives.  Small deterministic Python fallbacks keep fixtures and offline
qualification runnable when the native wheel is not installed.

The runtime never scans a regional product corpus.  It reads one lexicon
block, the corresponding bounded posting chunks, and only the docstore blocks
containing the selected document IDs.
"""

from __future__ import annotations

import hashlib
import json
import struct
import tempfile
import zlib
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

try:  # pragma: no cover - the wheel is built in release/CI images
    import searchpack_native as _native
except ImportError:  # pragma: no cover - deterministic Python fallback
    _native = None

try:
    from tools.consumer_input_intelligence import (
        CatalogIndex,
        CatalogRecord,
        LEXICAL_FEATURE_POLICY_VERSION,
        canonical_feature_keys,
        parse_intent,
    )
except ModuleNotFoundError:  # pragma: no cover - direct tool invocation
    from consumer_input_intelligence import CatalogIndex, CatalogRecord, LEXICAL_FEATURE_POLICY_VERSION, canonical_feature_keys, parse_intent


SEARCHPACK_SCHEMA_VERSION = "valuepilot-argentina-searchpack-v1"
SEARCHPACK_FORMAT_VERSION = 1
POSTINGS_BLOCK_MAX_IDS = 65_536
SEARCHPACK_CANDIDATE_HORIZON = 256
SEARCHPACK_MAX_DOCS = 100_000
DOCSTORE_BLOCK_RECORDS = 128
LEXICON_BLOCK_ENTRIES = 4_096
MAX_METADATA_BYTES = 8 * 1024 * 1024
MAX_BLOCK_BYTES = 8 * 1024 * 1024
MAX_TERM_CACHE_ENTRIES = 8_192
_U32_MAX = 0xFFFFFFFF
_CHUNK_STRUCT = struct.Struct("<IQIII")
_TERM_STRUCT = struct.Struct("<IIII")
_FALLBACK_FST_MAGIC = b"VPFST1\0"


class SearchPackError(RuntimeError):
    """A SearchPack is missing, malformed, or failed immutable verification."""


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _fallback_build_fst(entries: Sequence[tuple[str, int]]) -> bytes:
    if not entries:
        raise SearchPackError("FST needs at least one entry")
    result = bytearray(_FALLBACK_FST_MAGIC)
    result.extend(struct.pack("<I", len(entries)))
    previous = ""
    for key, value in entries:
        if not isinstance(key, str) or not key or "\x00" in key or key <= previous:
            raise SearchPackError("FST keys must be strictly sorted")
        if not isinstance(value, int) or value < 0 or value > 0xFFFFFFFFFFFFFFFF:
            raise SearchPackError("FST value is invalid")
        encoded = key.encode("utf-8")
        if len(encoded) > 0xFFFF:
            raise SearchPackError("FST key is too long")
        result.extend(struct.pack("<H", len(encoded)))
        result.extend(encoded)
        result.extend(struct.pack("<Q", value))
        previous = key
    return bytes(result)


def build_fst(entries: Sequence[tuple[str, int]]) -> bytes:
    ordered = tuple(entries)
    if _native is not None:
        try:
            return bytes(_native.build_fst(list(ordered)))
        except Exception as exc:  # noqa: BLE001 - use the strict fallback only for unavailable native ABI
            if not isinstance(exc, (ImportError, ModuleNotFoundError)):
                raise SearchPackError(f"native FST build failed: {exc}") from exc
    return _fallback_build_fst(ordered)


def _fallback_fst_lookup(data: bytes, key: str) -> int | None:
    if not data.startswith(_FALLBACK_FST_MAGIC):
        raise SearchPackError("FST format is unavailable without the native kernel")
    cursor = len(_FALLBACK_FST_MAGIC)
    if len(data) < cursor + 4:
        raise SearchPackError("FST is truncated")
    count = struct.unpack_from("<I", data, cursor)[0]
    cursor += 4
    previous: str | None = None
    found: int | None = None
    for _ in range(count):
        if cursor + 2 > len(data):
            raise SearchPackError("FST key length is truncated")
        size = struct.unpack_from("<H", data, cursor)[0]
        cursor += 2
        end = cursor + size
        if end + 8 > len(data):
            raise SearchPackError("FST entry is truncated")
        candidate = data[cursor:end].decode("utf-8")
        cursor = end
        if previous is not None and candidate <= previous:
            raise SearchPackError("FST keys are not strictly sorted")
        value = struct.unpack_from("<Q", data, cursor)[0]
        cursor += 8
        if candidate == key:
            found = value
        previous = candidate
    if cursor != len(data):
        raise SearchPackError("FST has trailing bytes")
    return found


def fst_lookup(data: bytes, key: str) -> int | None:
    if _native is not None:
        try:
            value = _native.fst_lookup(data, key)
            return None if value is None else int(value)
        except Exception as exc:  # noqa: BLE001
            if not data.startswith(_FALLBACK_FST_MAGIC):
                raise SearchPackError(f"native FST lookup failed: {exc}") from exc
    return _fallback_fst_lookup(data, key)


def _encode_postings(ids: Sequence[int]) -> bytes:
    if not ids:
        raise SearchPackError("posting list is empty")
    if any(not isinstance(value, int) or value < 0 or value > 0xFFFFFFFF for value in ids):
        raise SearchPackError("posting ID is invalid")
    if any(left >= right for left, right in zip(ids, ids[1:])):
        raise SearchPackError("posting IDs must be strictly increasing")
    raw = b"".join(struct.pack("<I", value) for value in ids)
    if _native is not None:
        try:
            return bytes(_native.encode_postings_u32(raw))
        except Exception as exc:  # noqa: BLE001
            raise SearchPackError(f"native posting encode failed: {exc}") from exc
    result = bytearray()
    previous = 0
    for index, value in enumerate(ids):
        delta = value if index == 0 else value - previous
        while delta >= 0x80:
            result.append((delta & 0x7F) | 0x80)
            delta >>= 7
        result.append(delta)
        previous = value
    return bytes(result)


def _decode_postings(data: bytes, max_count: int) -> tuple[int, ...]:
    if not data or max_count <= 0:
        raise SearchPackError("posting payload is empty or unbounded")
    if _native is not None:
        try:
            return tuple(int(value) for value in _native.decode_postings(data, max_count))
        except Exception as exc:  # noqa: BLE001
            raise SearchPackError(f"posting decode failed: {exc}") from exc
    values: list[int] = []
    cursor = 0
    previous = 0
    while cursor < len(data):
        if len(values) >= max_count:
            raise SearchPackError("posting count exceeds bound")
        shift = 0
        delta = 0
        while True:
            if cursor >= len(data) or shift > 28:
                raise SearchPackError("posting varint is truncated or overlong")
            byte = data[cursor]
            cursor += 1
            delta |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
        value = delta if not values else previous + delta
        if value > 0xFFFFFFFF or (values and value <= previous):
            raise SearchPackError("posting IDs are invalid")
        values.append(value)
        previous = value
    return tuple(values)


def _compress(data: bytes) -> tuple[bytes, str]:
    if _native is not None:
        try:
            return bytes(_native.zstd_compress(data, 6)), "zstd"
        except Exception as exc:  # noqa: BLE001
            raise SearchPackError(f"native zstd compression failed: {exc}") from exc
    try:  # zstandard is optional for local fixtures
        import zstandard

        return bytes(zstandard.ZstdCompressor(level=6).compress(data)), "zstd"
    except ImportError:
        return zlib.compress(data, level=6), "zlib"


def _decompress(data: bytes, algorithm: str, max_bytes: int) -> bytes:
    if max_bytes <= 0 or max_bytes > MAX_BLOCK_BYTES:
        raise SearchPackError("decompression bound is invalid")
    if algorithm == "zstd":
        if _native is not None:
            try:
                return bytes(_native.zstd_decompress(data, max_bytes))
            except Exception as exc:  # noqa: BLE001
                raise SearchPackError(f"zstd decompression failed: {exc}") from exc
        try:
            import zstandard

            result = zstandard.ZstdDecompressor().decompress(data, max_output_size=max_bytes)
        except ImportError as exc:  # pragma: no cover - only a corrupt/mismatched fixture reaches this
            raise SearchPackError("zstd runtime is unavailable") from exc
        except Exception as exc:  # noqa: BLE001
            raise SearchPackError(f"zstd decompression failed: {exc}") from exc
    elif algorithm == "zlib":
        try:
            result = zlib.decompress(data)
        except zlib.error as exc:
            raise SearchPackError("zlib decompression failed") from exc
    else:
        raise SearchPackError("unsupported docstore compression")
    if len(result) > max_bytes:
        raise SearchPackError("decompressed docstore block exceeds bound")
    return result


def _verify_block(data: bytes, descriptor: Mapping[str, Any], label: str) -> bytes:
    expected_bytes = descriptor.get("bytes")
    expected_hash = descriptor.get("sha256")
    if not isinstance(expected_bytes, int) or expected_bytes < 0 or not isinstance(expected_hash, str) or len(expected_hash) != 64:
        raise SearchPackError(f"{label} descriptor is invalid")
    if len(data) != expected_bytes or _sha256(data) != expected_hash:
        raise SearchPackError(f"{label} hash or byte count mismatch")
    return data


@dataclass
class SearchPackStats:
    feature_lookups: int = 0
    lexicon_reads: int = 0
    term_directory_reads: int = 0
    posting_reads: int = 0
    docstore_reads: int = 0
    key_lookups: int = 0
    bytes_read: int = 0
    posting_bytes: int = 0
    docstore_bytes: int = 0
    records_returned: int = 0
    saturated_features: int = 0
    cache_hits: int = 0
    cache_misses: int = 0


@dataclass(frozen=True)
class SearchPackLookup:
    doc_ids: tuple[int, ...]
    saturated: bool
    saturated_features: tuple[str, ...] = ()
    matched_features: tuple[str, ...] = ()


class _ByteLRU:
    def __init__(self, max_bytes: int):
        if max_bytes <= 0:
            raise ValueError("cache bound must be positive")
        self.max_bytes = max_bytes
        self._items: OrderedDict[str, bytes] = OrderedDict()
        self._bytes = 0

    def get(self, key: str) -> bytes | None:
        value = self._items.get(key)
        if value is not None:
            self._items.move_to_end(key)
        return value

    def put(self, key: str, value: bytes) -> None:
        if len(value) > self.max_bytes:
            return
        previous = self._items.pop(key, None)
        if previous is not None:
            self._bytes -= len(previous)
        while self._bytes + len(value) > self.max_bytes and self._items:
            _, old = self._items.popitem(last=False)
            self._bytes -= len(old)
        self._items[key] = value
        self._bytes += len(value)

    @property
    def bytes(self) -> int:
        return self._bytes

    @property
    def entries(self) -> int:
        return len(self._items)


class SearchPackRegion:
    """Read one immutable regional SearchPack without corpus scans."""

    def __init__(self, artifacts: Any, metadata: Mapping[str, Any], *, cache_bytes: int = 8 * 1024 * 1024):
        self.artifacts = artifacts
        self.metadata = dict(metadata)
        self.region_id = self._require_str(self.metadata, "regionId")
        if self.metadata.get("schemaVersion") != SEARCHPACK_SCHEMA_VERSION:
            raise SearchPackError("SearchPack schema version is unsupported")
        if self.metadata.get("formatVersion") != SEARCHPACK_FORMAT_VERSION:
            raise SearchPackError("SearchPack format version is unsupported")
        if self.metadata.get("policyVersion") != LEXICAL_FEATURE_POLICY_VERSION:
            raise SearchPackError("SearchPack lexical policy does not match runtime")
        doc_count = self.metadata.get("documentCount")
        if not isinstance(doc_count, int) or doc_count < 0 or doc_count > 0xFFFFFFFF:
            raise SearchPackError("SearchPack document count is invalid")
        self.document_count = doc_count
        self.compression = self.metadata.get("docstoreCompression")
        if self.compression not in {"zstd", "zlib"}:
            raise SearchPackError("SearchPack compression is unsupported")
        files = self.metadata.get("files")
        if not isinstance(files, Mapping):
            raise SearchPackError("SearchPack files are missing")
        self.files = {str(key): self._descriptor(value, str(key)) for key, value in files.items()}
        required = {"lexicon", "termDirectory", "chunkDirectory", "postings", "docstore", "keyLexicon"}
        if set(self.files) != required:
            raise SearchPackError("SearchPack file set is invalid")
        self.lexicon_blocks = self._blocks(self.metadata.get("lexiconBlocks"), "lexiconBlocks")
        self.key_lexicon_blocks = self._blocks(self.metadata.get("keyLexiconBlocks"), "keyLexiconBlocks")
        self.docstore_blocks = self._blocks(self.metadata.get("docstoreBlocks"), "docstoreBlocks")
        self._validate_index_blocks(self.lexicon_blocks, "lexicon", key_blocks=True, expected_entries=None)
        self._validate_index_blocks(self.key_lexicon_blocks, "keyLexicon", key_blocks=True, expected_entries=doc_count)
        self._validate_index_blocks(self.docstore_blocks, "docstore", key_blocks=False, expected_entries=doc_count)
        term_count = self.metadata.get("termCount")
        if not isinstance(term_count, int) or term_count < 0:
            raise SearchPackError("SearchPack term count is invalid")
        self.term_count = term_count
        if sum(int(block.get("entryCount", 0)) for block in self.lexicon_blocks) != term_count:
            raise SearchPackError("SearchPack lexicon entry coverage is invalid")
        if int(self.files["termDirectory"]["bytes"]) != term_count * _TERM_STRUCT.size:
            raise SearchPackError("SearchPack term directory length is invalid")
        if self.document_count and sum(int(block.get("recordCount", 0)) for block in self.docstore_blocks) != self.document_count:
            raise SearchPackError("SearchPack docstore coverage is invalid")
        self._cache = _ByteLRU(cache_bytes)
        self._term_cache: dict[int, tuple[int, int, int, int]] = {}

    @staticmethod
    def _require_str(value: Mapping[str, Any], key: str) -> str:
        result = value.get(key)
        if not isinstance(result, str) or not result:
            raise SearchPackError(f"SearchPack {key} is invalid")
        return result

    @staticmethod
    def _descriptor(value: Any, label: str) -> dict[str, Any]:
        if not isinstance(value, Mapping):
            raise SearchPackError(f"SearchPack {label} descriptor is invalid")
        path = value.get("path")
        size = value.get("bytes")
        digest = value.get("sha256")
        if not isinstance(path, str) or not path or not isinstance(size, int) or size < 0 or not isinstance(digest, str) or len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest):
            raise SearchPackError(f"SearchPack {label} descriptor is invalid")
        return dict(value)

    @classmethod
    def _blocks(cls, value: Any, label: str) -> tuple[dict[str, Any], ...]:
        if not isinstance(value, list) or not value:
            raise SearchPackError(f"SearchPack {label} are invalid")
        blocks: list[dict[str, Any]] = []
        previous = -1
        for item in value:
            if not isinstance(item, Mapping):
                raise SearchPackError(f"SearchPack {label} entry is invalid")
            offset = item.get("offset")
            length = item.get("length")
            if not isinstance(offset, int) or offset < 0 or not isinstance(length, int) or length <= 0 or offset < previous:
                raise SearchPackError(f"SearchPack {label} range is invalid")
            blocks.append(dict(item))
            previous = offset
        return tuple(blocks)

    def _validate_index_blocks(self, blocks: Sequence[Mapping[str, Any]], name: str, *, key_blocks: bool, expected_entries: int | None) -> None:
        """Validate sorted, non-overlapping ranges before any lookup occurs."""

        file_size = int(self.files[name]["bytes"])
        previous_end = 0
        previous_last: str | None = None
        total_entries = 0
        expected_doc_id = 0
        for block in blocks:
            offset = block.get("offset")
            length = block.get("length")
            digest = block.get("sha256")
            if not isinstance(offset, int) or not isinstance(length, int) or offset != previous_end or length <= 0 or offset + length > file_size or not isinstance(digest, str) or len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest):
                raise SearchPackError(f"SearchPack {name} block range is invalid")
            previous_end = offset + length
            if key_blocks:
                first = block.get("firstKey")
                last = block.get("lastKey")
                entry_count = block.get("entryCount")
                if not isinstance(first, str) or not isinstance(last, str) or not first or first > last or not isinstance(entry_count, int) or entry_count <= 0 or (previous_last is not None and first <= previous_last):
                    raise SearchPackError(f"SearchPack {name} block keys are invalid")
                previous_last = last
                total_entries += entry_count
            else:
                first_doc = block.get("firstDocId")
                record_count = block.get("recordCount")
                uncompressed = block.get("uncompressedBytes")
                if not isinstance(first_doc, int) or first_doc != expected_doc_id or not isinstance(record_count, int) or record_count <= 0 or not isinstance(uncompressed, int) or uncompressed <= 0 or uncompressed > MAX_BLOCK_BYTES:
                    raise SearchPackError("SearchPack docstore block metadata is invalid")
                expected_doc_id += record_count
        if previous_end != file_size:
            raise SearchPackError(f"SearchPack {name} blocks do not cover the file")
        covered_entries = total_entries if key_blocks else expected_doc_id
        if expected_entries is not None and covered_entries != expected_entries:
            raise SearchPackError(f"SearchPack {name} entry coverage is invalid")

    def _read(self, name: str, offset: int, length: int, stats: SearchPackStats, *, label: str) -> bytes:
        descriptor = self.files[name]
        total = int(descriptor["bytes"])
        if offset < 0 or length < 0 or offset + length > total or length > MAX_BLOCK_BYTES:
            raise SearchPackError(f"{label} range is invalid")
        key = f"{name}:{offset}:{length}:{descriptor['sha256']}"
        cached = self._cache.get(key)
        if cached is not None:
            stats.cache_hits += 1
            return cached
        stats.cache_misses += 1
        try:
            data = self.artifacts.read_range(str(descriptor["path"]), offset, length)
        except Exception as exc:  # noqa: BLE001 - normalize store-specific failures
            raise SearchPackError(f"{label} range read failed") from exc
        if not isinstance(data, bytes) or len(data) != length:
            raise SearchPackError(f"{label} range is truncated")
        stats.bytes_read += len(data)
        self._cache.put(key, data)
        return data

    @staticmethod
    def _block_descriptor(blocks: Sequence[Mapping[str, Any]], ordinal: int, label: str) -> Mapping[str, Any]:
        if ordinal < 0 or ordinal >= len(blocks):
            raise SearchPackError(f"{label} ordinal is invalid")
        return blocks[ordinal]

    @staticmethod
    def _find_block(blocks: Sequence[Mapping[str, Any]], key: str, label: str) -> int | None:
        low = 0
        high = len(blocks)
        while low < high:
            mid = (low + high) // 2
            block = blocks[mid]
            first = block.get("firstKey")
            last = block.get("lastKey")
            if not isinstance(first, str) or not isinstance(last, str):
                raise SearchPackError(f"{label} key range is invalid")
            if key < first:
                high = mid
            elif key > last:
                low = mid + 1
            else:
                return mid
        return None

    def _load_block(self, name: str, block: Mapping[str, Any], stats: SearchPackStats, *, label: str) -> bytes:
        offset = block.get("offset")
        length = block.get("length")
        if not isinstance(offset, int) or not isinstance(length, int):
            raise SearchPackError(f"{label} range is invalid")
        data = self._read(name, offset, length, stats, label=label)
        return _verify_block(data, block, label)

    def _lookup_ordinal(self, key: str, blocks: Sequence[Mapping[str, Any]], name: str, stats: SearchPackStats, *, label: str) -> int | None:
        index = self._find_block(blocks, key, label)
        if index is None:
            return None
        data = self._load_block(name, blocks[index], stats, label=label)
        if name == "lexicon":
            stats.lexicon_reads += 1
        return fst_lookup(data, key)

    def _term(self, ordinal: int, stats: SearchPackStats) -> tuple[int, int, int, int]:
        cached = self._term_cache.get(ordinal)
        if cached is not None:
            return cached
        descriptor = self.files["termDirectory"]
        offset = ordinal * _TERM_STRUCT.size
        if offset + _TERM_STRUCT.size > int(descriptor["bytes"]):
            raise SearchPackError("term ordinal exceeds directory")
        data = self._read("termDirectory", offset, _TERM_STRUCT.size, stats, label="term directory")
        stats.term_directory_reads += 1
        value = _TERM_STRUCT.unpack(data)
        if len(self._term_cache) >= MAX_TERM_CACHE_ENTRIES:
            self._term_cache.pop(next(iter(self._term_cache)))
        self._term_cache[ordinal] = value
        return value

    def lookup_features(self, feature_keys: Iterable[str], *, candidate_bound: int | None = SEARCHPACK_CANDIDATE_HORIZON, stats: SearchPackStats | None = None) -> SearchPackLookup:
        stats = stats or SearchPackStats()
        keys = tuple(sorted({value for value in feature_keys if isinstance(value, str) and value}))
        if not keys:
            return SearchPackLookup((), False, (), ())
        if candidate_bound is None:
            candidate_bound = SEARCHPACK_MAX_DOCS
        if candidate_bound <= 0 or candidate_bound > SEARCHPACK_MAX_DOCS:
            raise SearchPackError("SearchPack candidate bound is invalid")
        doc_ids: set[int] = set()
        saturated: list[str] = []
        matched: list[str] = []
        for key in keys:
            stats.feature_lookups += 1
            ordinal = self._lookup_ordinal(key, self.lexicon_blocks, "lexicon", stats, label="lexicon")
            if ordinal is None:
                continue
            matched.append(key)
            first_chunk, chunk_count, df, _flags = self._term(int(ordinal), stats)
            if df <= 0 or chunk_count <= 0 or df > 0xFFFFFFFF:
                raise SearchPackError("term directory entry is invalid")
            if df > candidate_bound:
                saturated.append(key)
                stats.saturated_features += 1
                continue
            chunk = first_chunk
            seen_chunks: set[int] = set()
            collected = 0
            while chunk != _U32_MAX:
                if chunk in seen_chunks or len(seen_chunks) >= (chunk_count + 1):
                    raise SearchPackError("posting chunk chain is invalid")
                seen_chunks.add(chunk)
                chunk_offset = chunk * _CHUNK_STRUCT.size
                chunk_directory = self.files["chunkDirectory"]
                if chunk_offset + _CHUNK_STRUCT.size > int(chunk_directory["bytes"]):
                    raise SearchPackError("posting chunk ordinal exceeds directory")
                descriptor_data = self._read("chunkDirectory", chunk_offset, _CHUNK_STRUCT.size, stats, label="chunk directory")
                next_chunk, posting_offset, posting_length, count, chunk_df = _CHUNK_STRUCT.unpack(descriptor_data)
                if count == 0 or chunk_df != df or posting_length == 0:
                    raise SearchPackError("posting chunk metadata is invalid")
                posting = self._read("postings", posting_offset, posting_length, stats, label="posting")
                stats.posting_reads += 1
                stats.posting_bytes += len(posting)
                values = _decode_postings(posting, count)
                if len(values) != count:
                    raise SearchPackError("posting count mismatch")
                doc_ids.update(values)
                collected += len(values)
                if len(doc_ids) > candidate_bound:
                    saturated.append(key)
                    stats.saturated_features += 1
                    break
                if collected > df:
                    raise SearchPackError("posting chain exceeds declared df")
                chunk = next_chunk
            if collected != df and key not in saturated:
                raise SearchPackError("posting chain does not match declared df")
            if saturated:
                # The result is intentionally unusable for a bounded semantic
                # decision.  Do not return an arbitrary first product.
                break
        if saturated:
            return SearchPackLookup((), True, tuple(sorted(set(saturated))), tuple(matched))
        ordered = tuple(sorted(doc_ids))
        if len(ordered) > candidate_bound:
            return SearchPackLookup((), True, tuple(keys), tuple(matched))
        return SearchPackLookup(ordered, False, (), tuple(matched))

    def _doc_block_for_id(self, doc_id: int) -> int:
        low = 0
        high = len(self.docstore_blocks)
        while low < high:
            mid = (low + high) // 2
            block = self.docstore_blocks[mid]
            first = block.get("firstDocId")
            count = block.get("recordCount")
            if not isinstance(first, int) or not isinstance(count, int):
                raise SearchPackError("docstore block metadata is invalid")
            if doc_id < first:
                high = mid
            elif doc_id >= first + count:
                low = mid + 1
            else:
                return mid
        raise SearchPackError("doc ID is not in docstore")

    def get_records(self, doc_ids: Iterable[int], *, stats: SearchPackStats | None = None, max_records: int = SEARCHPACK_MAX_DOCS) -> dict[int, Mapping[str, Any]]:
        stats = stats or SearchPackStats()
        values = tuple(sorted({int(value) for value in doc_ids}))
        if len(values) > max_records or any(value < 0 or value >= self.document_count for value in values):
            raise SearchPackError("doc ID request exceeds bound")
        grouped: dict[int, list[int]] = {}
        for value in values:
            grouped.setdefault(self._doc_block_for_id(value), []).append(value)
        result: dict[int, Mapping[str, Any]] = {}
        for block_index in sorted(grouped):
            block = self.docstore_blocks[block_index]
            compressed = self._load_block("docstore", block, stats, label="docstore")
            stats.docstore_reads += 1
            uncompressed_bytes = block.get("uncompressedBytes")
            if not isinstance(uncompressed_bytes, int) or uncompressed_bytes <= 0 or uncompressed_bytes > MAX_BLOCK_BYTES:
                raise SearchPackError("docstore uncompressed bound is invalid")
            decoded = _decompress(compressed, self.compression, uncompressed_bytes)
            expected_uncompressed_hash = block.get("uncompressedSha256")
            if not isinstance(expected_uncompressed_hash, str) or _sha256(decoded) != expected_uncompressed_hash:
                raise SearchPackError("docstore uncompressed hash mismatch")
            stats.docstore_bytes += len(compressed)
            wanted = set(grouped[block_index])
            lines = decoded.splitlines()
            first_doc = int(block["firstDocId"])
            for offset, line in enumerate(lines):
                doc_id = first_doc + offset
                if doc_id not in wanted:
                    continue
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, ValueError) as exc:
                    raise SearchPackError("docstore record is invalid") from exc
                if not isinstance(value, Mapping) or value.get("docId") != doc_id or not isinstance(value.get("record"), Mapping):
                    raise SearchPackError("docstore record identity is invalid")
                result[doc_id] = value["record"]
        if set(result) != set(values):
            raise SearchPackError("docstore omitted requested record")
        stats.records_returned += len(result)
        return result

    def lookup_product_key(self, product_key: str, *, stats: SearchPackStats | None = None) -> Mapping[str, Any] | None:
        stats = stats or SearchPackStats()
        if not isinstance(product_key, str) or not product_key:
            raise SearchPackError("product key is invalid")
        stats.key_lookups += 1
        doc_id = self._lookup_ordinal(product_key, self.key_lexicon_blocks, "keyLexicon", stats, label="key lexicon")
        if doc_id is None:
            return None
        return self.get_records((int(doc_id),), stats=stats, max_records=1).get(int(doc_id))

    def search_many(self, queries: Sequence[str], *, product_limit: int = 5, candidate_bound: int = SEARCHPACK_MAX_DOCS, stats: SearchPackStats | None = None) -> tuple[tuple[Mapping[str, Any], ...], ...]:
        if not isinstance(product_limit, int) or not 1 <= product_limit <= 5:
            raise SearchPackError("product limit is invalid")
        stats = stats or SearchPackStats()
        scorer = CatalogIndex(())
        requested = tuple(dict.fromkeys(queries))
        intents = tuple(parse_intent(query, data=scorer.data) for query in requested)
        lookups = [self.lookup_features(scorer.intent_feature_keys(intent), candidate_bound=candidate_bound, stats=stats) for intent in intents]
        if any(item.saturated for item in lookups):
            raise SearchPackError("SearchPack candidate horizon is saturated")
        all_ids = tuple(sorted({doc_id for item in lookups for doc_id in item.doc_ids}))
        records = self.get_records(all_ids, stats=stats, max_records=candidate_bound) if all_ids else {}
        from tools.argentina_sepa_search import search_products

        values: list[tuple[Mapping[str, Any], ...]] = []
        for lookup in lookups:
            scoped = (records[doc_id] for doc_id in lookup.doc_ids if doc_id in records)
            values.append(tuple(item.as_dict() for item in search_products(scoped, requested[len(values)], limit=product_limit, max_candidates=candidate_bound)))
        return tuple(values)


class SearchPackManager:
    """Validate and expose the per-region packs pinned by a release manifest."""

    def __init__(self, artifacts: Any, manifest: Mapping[str, Any], *, cache_bytes: int = 8 * 1024 * 1024):
        self.artifacts = artifacts
        descriptor = manifest.get("searchPackArtifact") if isinstance(manifest, Mapping) else None
        if not isinstance(descriptor, Mapping):
            raise SearchPackError("release does not declare SearchPack v1")
        if descriptor.get("schemaVersion") != SEARCHPACK_SCHEMA_VERSION or descriptor.get("formatVersion") != SEARCHPACK_FORMAT_VERSION or descriptor.get("policyVersion") != LEXICAL_FEATURE_POLICY_VERSION:
            raise SearchPackError("release SearchPack declaration is incompatible")
        regions = descriptor.get("regions")
        if not isinstance(regions, list) or not regions or any(not isinstance(value, str) or not value for value in regions) or len(set(regions)) != len(regions):
            raise SearchPackError("release SearchPack regions are invalid")
        if regions != sorted(regions):
            raise SearchPackError("release SearchPack regions are not stable")
        metadata_paths = descriptor.get("metadataPaths")
        metadata_hashes = descriptor.get("metadataSha256")
        if not isinstance(metadata_paths, Mapping) or set(metadata_paths) != set(regions) or not isinstance(metadata_hashes, Mapping) or set(metadata_hashes) != set(regions):
            raise SearchPackError("release SearchPack metadata index is invalid")
        if not isinstance(descriptor.get("sourceReleaseId"), str) or not descriptor["sourceReleaseId"]:
            raise SearchPackError("release SearchPack source release is invalid")
        self.descriptor = dict(descriptor)
        self.region_ids = tuple(sorted(regions))
        self.cache_bytes = cache_bytes
        self._regions: dict[str, SearchPackRegion] = {}
        self._metadata_cache: dict[str, Mapping[str, Any]] = {}

    def _metadata_path(self, region_id: str) -> str:
        paths = self.descriptor.get("metadataPaths")
        path = paths.get(region_id) if isinstance(paths, Mapping) else None
        if not isinstance(path, str) or not path:
            raise SearchPackError("SearchPack region metadata path is missing")
        return path

    def region(self, region_id: str) -> SearchPackRegion:
        if region_id not in self.region_ids:
            raise SearchPackError("SearchPack region is not declared")
        current = self._regions.get(region_id)
        if current is not None:
            return current
        path = self._metadata_path(region_id)
        try:
            raw = self.artifacts.read(path)
        except Exception as exc:  # noqa: BLE001 - normalize immutable-store failures
            raise SearchPackError("SearchPack metadata object is unavailable") from exc
        if len(raw) > MAX_METADATA_BYTES:
            raise SearchPackError("SearchPack metadata is too large")
        digest = _sha256(raw)
        expected = self.descriptor.get("metadataSha256", {}).get(region_id) if isinstance(self.descriptor.get("metadataSha256"), Mapping) else None
        if not isinstance(expected, str) or digest != expected:
            raise SearchPackError("SearchPack metadata hash mismatch")
        try:
            metadata = json.loads(raw)
        except (UnicodeDecodeError, ValueError) as exc:
            raise SearchPackError("SearchPack metadata is invalid") from exc
        if not isinstance(metadata, Mapping) or metadata.get("regionId") != region_id:
            raise SearchPackError("SearchPack metadata region is invalid")
        expected_source = self.descriptor.get("sourceReleaseId")
        if isinstance(expected_source, str) and metadata.get("sourceReleaseId") != expected_source:
            raise SearchPackError("SearchPack metadata source release is invalid")
        source_hashes = self.descriptor.get("sourceSearchIndexSha256")
        expected_source_hash = source_hashes.get(region_id) if isinstance(source_hashes, Mapping) else None
        if isinstance(expected_source_hash, str) and metadata.get("sourceSearchIndexSha256") != expected_source_hash:
            raise SearchPackError("SearchPack metadata source index is invalid")
        region = SearchPackRegion(self.artifacts, metadata, cache_bytes=self.cache_bytes)
        self._metadata_cache[region_id] = metadata
        self._regions[region_id] = region
        return region

    def regions(self, region_ids: Sequence[str] | None = None) -> tuple[SearchPackRegion, ...]:
        selected = self.region_ids if region_ids is None else tuple(sorted(set(region_ids)))
        if any(region_id not in self.region_ids for region_id in selected):
            raise SearchPackError("SearchPack region selection is invalid")
        return tuple(self.region(region_id) for region_id in selected)


def record_for_doc(doc_id: int, record: Mapping[str, Any]) -> dict[str, Any]:
    """Canonical docstore envelope used by builders and tiny fixtures."""

    if not isinstance(doc_id, int) or doc_id < 0 or not isinstance(record, Mapping):
        raise SearchPackError("docstore record is invalid")
    return {"docId": doc_id, "record": dict(record)}


__all__ = [
    "DOCSTORE_BLOCK_RECORDS",
    "LEXICON_BLOCK_ENTRIES",
    "LEXICAL_FEATURE_POLICY_VERSION",
    "POSTINGS_BLOCK_MAX_IDS",
    "SEARCHPACK_CANDIDATE_HORIZON",
    "SEARCHPACK_FORMAT_VERSION",
    "SEARCHPACK_MAX_DOCS",
    "SEARCHPACK_SCHEMA_VERSION",
    "SearchPackError",
    "SearchPackLookup",
    "SearchPackManager",
    "SearchPackRegion",
    "SearchPackStats",
    "build_fst",
    "canonical_feature_keys",
    "fst_lookup",
    "record_for_doc",
]
