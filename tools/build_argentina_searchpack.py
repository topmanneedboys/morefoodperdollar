#!/usr/bin/env python3
"""Build immutable SearchPack v1 objects from qualified regional indexes.

The input is an existing content-addressed routing workspace.  Only the
already-qualified ``search-index.jsonl.gz`` objects are read; the official
SEPA ZIP is never opened or reparsed.  The output is a new derived workspace
whose unchanged objects are hard-linked and whose SearchPack files are added
under content-addressed paths.  No active pointer is written.
"""

from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import struct
import tempfile
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

from tools.argentina_searchpack import (
    DOCSTORE_BLOCK_RECORDS,
    LEXICON_BLOCK_ENTRIES,
    POSTINGS_BLOCK_MAX_IDS,
    SEARCHPACK_FORMAT_VERSION,
    SEARCHPACK_SCHEMA_VERSION,
    SearchPackError,
    _canonical_json,
    _compress,
    _encode_postings,
    build_fst,
    record_for_doc,
)
from tools.consumer_input_intelligence import CatalogRecord, LEXICAL_FEATURE_POLICY_VERSION, CatalogIndex


SEARCHPACK_RELEASE_SUFFIX = "-search-v1"
SEARCHPACK_ROOT = "micro-1024/searchpack-v1"
_TERM_STRUCT = struct.Struct("<IIII")
_CHUNK_STRUCT = struct.Struct("<IQIII")
_U32_MAX = 0xFFFFFFFF
_HEX64 = set("0123456789abcdef")


class SearchPackBuildError(ValueError):
    """A source release cannot produce a safe deterministic SearchPack."""


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_json(path: Path, label: str) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, ValueError) as exc:
        raise SearchPackBuildError(f"{label} is invalid") from exc
    if not isinstance(value, dict):
        raise SearchPackBuildError(f"{label} is invalid")
    return value


def _release_manifest(workspace: Path, release_id: str) -> dict[str, Any]:
    manifest = _read_json(workspace / "releases" / release_id / "manifest.json", f"{release_id} manifest")
    if manifest.get("completionState") != "COMPLETE" or manifest.get("releaseId") != release_id:
        raise SearchPackBuildError(f"{release_id} is not a complete release")
    return manifest


def _object_descriptor_map(manifest: Mapping[str, Any]) -> dict[str, Mapping[str, Any]]:
    values = manifest.get("objects")
    if not isinstance(values, list):
        raise SearchPackBuildError("release object descriptors are missing")
    result: dict[str, Mapping[str, Any]] = {}
    for item in values:
        if not isinstance(item, Mapping) or not isinstance(item.get("path"), str) or not isinstance(item.get("sha256"), str):
            raise SearchPackBuildError("release object descriptor is invalid")
        path = str(item["path"])
        if path in result:
            raise SearchPackBuildError("release object descriptors contain a duplicate path")
        result[path] = item
    return result


def _verify_source_object(workspace: Path, descriptor: Mapping[str, Any], logical_path: str) -> Path:
    digest = descriptor.get("sha256")
    size = descriptor.get("bytes")
    if not isinstance(digest, str) or len(digest) != 64 or any(value not in _HEX64 for value in digest) or not isinstance(size, int) or size < 0:
        raise SearchPackBuildError(f"{logical_path} source descriptor is invalid")
    object_path = workspace / "objects" / "sha256" / digest
    if not object_path.is_file():
        # Tiny local fixtures may use the direct legacy layout.
        object_path = workspace / Path(*logical_path.split("/"))
    try:
        if object_path.stat().st_size != size or _sha256_file(object_path) != digest:
            raise SearchPackBuildError(f"{logical_path} source object failed immutable verification")
    except OSError as exc:
        raise SearchPackBuildError(f"{logical_path} source object is unavailable") from exc
    return object_path


def _regional_search_descriptors(manifest: Mapping[str, Any]) -> dict[str, Mapping[str, Any]]:
    values = _object_descriptor_map(manifest)
    result: dict[str, Mapping[str, Any]] = {}
    for path, descriptor in values.items():
        parts = path.split("/")
        if len(parts) == 4 and parts[0] == "micro-1024" and parts[1] == "regions" and parts[3] == "search-index.jsonl.gz":
            result[parts[2]] = descriptor
    if not result:
        raise SearchPackBuildError("release has no regional search indexes")
    if any(not region for region in result):
        raise SearchPackBuildError("release regional search index identity is invalid")
    return dict(sorted(result.items()))


def _complete_search_descriptor(source_workspace: Path, manifest: Mapping[str, Any], region_id: str, descriptor: Mapping[str, Any]) -> dict[str, Any]:
    """Augment object metadata with the regional manifest's gzip digest."""

    if isinstance(descriptor.get("uncompressedSha256"), str):
        return dict(descriptor)
    logical = f"micro-1024/regions/{region_id}/manifest.json"
    candidate = _object_descriptor_map(manifest).get(logical)
    if candidate is None:
        raise SearchPackBuildError(f"{region_id} regional manifest is missing")
    path = _verify_source_object(source_workspace, candidate, logical)
    regional = _read_json(path, logical)
    files = regional.get("files")
    search = files.get("searchIndex") if isinstance(files, Mapping) else None
    if not isinstance(search, Mapping) or not isinstance(search.get("uncompressedSha256"), str):
        raise SearchPackBuildError(f"{region_id} regional search descriptor lacks uncompressed hash")
    completed = dict(descriptor)
    for key in ("uncompressedSha256", "uncompressedBytes"):
        if key in search:
            completed[key] = search[key]
    return completed


def _iter_source_records(path: Path, descriptor: Mapping[str, Any], region_id: str) -> Iterator[dict[str, Any]]:
    expected_uncompressed = descriptor.get("uncompressedSha256")
    expected_bytes = descriptor.get("uncompressedBytes")
    if not isinstance(expected_uncompressed, str) or len(expected_uncompressed) != 64 or not isinstance(expected_bytes, int) or expected_bytes < 0:
        raise SearchPackBuildError(f"{region_id} search index descriptor lacks uncompressed evidence")
    digest = hashlib.sha256()
    count = 0
    try:
        with gzip.open(path, "rb") as handle:
            for line_number, line in enumerate(handle, 1):
                digest.update(line)
                count += len(line)
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, ValueError) as exc:
                    raise SearchPackBuildError(f"{region_id} search index line {line_number} is invalid") from exc
                if not isinstance(value, Mapping):
                    raise SearchPackBuildError(f"{region_id} search index line {line_number} is not an object")
                yield dict(value)
    except (OSError, EOFError) as exc:
        raise SearchPackBuildError(f"{region_id} search index gzip stream is invalid") from exc
    if count != expected_bytes or digest.hexdigest() != expected_uncompressed:
        raise SearchPackBuildError(f"{region_id} search index uncompressed evidence mismatch")


def _file_descriptor(path: Path, logical_path: str) -> dict[str, Any]:
    return {"path": logical_path, "bytes": path.stat().st_size, "sha256": _sha256_file(path)}


def _block_descriptor(path: Path, offset: int, length: int, **extra: Any) -> dict[str, Any]:
    with path.open("rb") as handle:
        handle.seek(offset)
        data = handle.read(length)
    if len(data) != length:
        raise SearchPackBuildError("SearchPack block could not be read after writing")
    return {"offset": offset, "length": length, "bytes": length, "sha256": hashlib.sha256(data).hexdigest(), **extra}


def _build_fst_blocks(path: Path, entries: Iterable[tuple[str, int]], *, block_entries: int = LEXICON_BLOCK_ENTRIES) -> tuple[dict[str, Any], ...]:
    blocks: list[dict[str, Any]] = []
    batch: list[tuple[str, int]] = []
    offset = 0
    with path.open("wb") as handle:
        for entry in entries:
            batch.append(entry)
            if len(batch) < block_entries:
                continue
            payload = build_fst(batch)
            handle.write(payload)
            handle.flush()
            blocks.append(_block_descriptor(path, offset, len(payload), firstKey=batch[0][0], lastKey=batch[-1][0], entryCount=len(batch)))
            offset += len(payload)
            batch = []
        if batch:
            payload = build_fst(batch)
            handle.write(payload)
            handle.flush()
            blocks.append(_block_descriptor(path, offset, len(payload), firstKey=batch[0][0], lastKey=batch[-1][0], entryCount=len(batch)))
    if not blocks:
        raise SearchPackBuildError("SearchPack cannot build an empty FST")
    return tuple(blocks)


class _FstBlockWriter:
    """Append sorted FST blocks without retaining the feature universe."""

    def __init__(self, path: Path, *, block_entries: int = LEXICON_BLOCK_ENTRIES):
        self.path = path
        self.block_entries = block_entries
        self.handle = path.open("wb")
        self.offset = 0
        self.batch: list[tuple[str, int]] = []
        self.blocks: list[dict[str, Any]] = []

    def add(self, key: str, value: int) -> None:
        self.batch.append((key, value))
        if len(self.batch) >= self.block_entries:
            self.flush()

    def flush(self) -> None:
        if not self.batch:
            return
        payload = build_fst(self.batch)
        self.handle.write(payload)
        self.handle.flush()
        self.blocks.append(_block_descriptor(self.path, self.offset, len(payload), firstKey=self.batch[0][0], lastKey=self.batch[-1][0], entryCount=len(self.batch)))
        self.offset += len(payload)
        self.batch = []

    def finish(self) -> tuple[dict[str, Any], ...]:
        self.flush()
        self.handle.flush()
        self.handle.close()
        if not self.blocks:
            raise SearchPackBuildError("SearchPack cannot build an empty FST")
        return tuple(self.blocks)

    def close(self) -> None:
        if not self.handle.closed:
            self.handle.close()


def _build_region(source_workspace: Path, manifest: Mapping[str, Any], release_id: str, region_id: str, descriptor: Mapping[str, Any], staging: Path, vocabulary_sha256: str) -> dict[str, Any]:
    """Build one region using SQLite only as a bounded disk spool."""

    source_path = _verify_source_object(source_workspace, descriptor, f"micro-1024/regions/{region_id}/search-index.jsonl.gz")
    staging.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f"searchpack-{region_id}-", dir=str(staging.parent)) as temp_name:
        db_path = Path(temp_name) / "records.sqlite3"
        connection = sqlite3.connect(str(db_path))
        try:
            connection.execute("PRAGMA journal_mode=OFF")
            connection.execute("PRAGMA synchronous=OFF")
            connection.execute("PRAGMA temp_store=FILE")
            connection.execute("CREATE TABLE documents(product_key TEXT PRIMARY KEY, raw_json BLOB NOT NULL, doc_id INTEGER)")
            connection.execute("CREATE TABLE feature_docs(term TEXT NOT NULL, product_key TEXT NOT NULL, PRIMARY KEY(term, product_key)) WITHOUT ROWID")
            total_records = 0
            feature_rows = 0
            for raw in _iter_source_records(source_path, descriptor, region_id):
                try:
                    record = CatalogRecord.from_mapping(raw)
                except Exception as exc:  # noqa: BLE001 - normalize provider-row validation
                    raise SearchPackBuildError(f"{region_id} has an invalid product identity") from exc
                key = record.product_evidence_key
                canonical = _canonical_json(raw)
                try:
                    connection.execute("INSERT INTO documents(product_key, raw_json) VALUES (?, ?)", (key, canonical))
                except sqlite3.IntegrityError as exc:
                    raise SearchPackBuildError(f"{region_id} contains duplicate productEvidenceKey {key}") from exc
                features = CatalogIndex.record_feature_keys(record)
                connection.executemany("INSERT OR IGNORE INTO feature_docs(term, product_key) VALUES (?, ?)", ((term, key) for term in features))
                total_records += 1
                feature_rows += len(features)
                if total_records % 10_000 == 0:
                    connection.commit()
            connection.commit()
            if total_records == 0:
                raise SearchPackBuildError(f"{region_id} search index is empty")

            docstore_path = staging / "docstore.bin"
            key_lexicon_path = staging / "key-lexicon.bin"
            docstore_blocks: list[dict[str, Any]] = []
            cursor = connection.execute("SELECT product_key, raw_json FROM documents ORDER BY product_key")
            doc_id = 0
            block_lines: list[bytes] = []
            block_first = 0
            with docstore_path.open("wb") as docstore:
                for product_key, raw_json in cursor:
                    connection.execute("UPDATE documents SET doc_id=? WHERE product_key=?", (doc_id, product_key))
                    raw = json.loads(raw_json)
                    line = _canonical_json(record_for_doc(doc_id, raw))
                    if not block_lines:
                        block_first = doc_id
                    block_lines.append(line)
                    doc_id += 1
                    if len(block_lines) < DOCSTORE_BLOCK_RECORDS:
                        continue
                    payload = b"".join(block_lines)
                    compressed, compression = _compress(payload)
                    offset = docstore.tell()
                    docstore.write(compressed)
                    docstore_blocks.append({"offset": offset, "length": len(compressed), "bytes": len(compressed), "sha256": hashlib.sha256(compressed).hexdigest(), "uncompressedBytes": len(payload), "uncompressedSha256": hashlib.sha256(payload).hexdigest(), "firstDocId": block_first, "recordCount": len(block_lines)})
                    block_lines = []
                if block_lines:
                    payload = b"".join(block_lines)
                    compressed, compression = _compress(payload)
                    offset = docstore.tell()
                    docstore.write(compressed)
                    docstore_blocks.append({"offset": offset, "length": len(compressed), "bytes": len(compressed), "sha256": hashlib.sha256(compressed).hexdigest(), "uncompressedBytes": len(payload), "uncompressedSha256": hashlib.sha256(payload).hexdigest(), "firstDocId": block_first, "recordCount": len(block_lines)})
            connection.commit()

            key_lexicon_blocks = _build_fst_blocks(
                key_lexicon_path,
                ((str(product_key), int(index)) for index, (product_key, _raw_json) in enumerate(connection.execute("SELECT product_key, raw_json FROM documents ORDER BY product_key"))),
            )

            term_directory_path = staging / "term-directory.bin"
            chunk_directory_path = staging / "chunk-directory.bin"
            postings_path = staging / "postings.bin"
            lexicon_path = staging / "lexicon.bin"
            term_directory = term_directory_path.open("w+b")
            chunk_directory = chunk_directory_path.open("w+b")
            postings = postings_path.open("wb")
            try:
                term_cursor = connection.execute("SELECT term FROM feature_docs GROUP BY term ORDER BY term")
                term_ordinal = 0
                for (term,) in term_cursor:
                    term_pos = term_directory.tell()
                    term_directory.write(_TERM_STRUCT.pack(0, 0, 0, 0))
                    first_chunk = _U32_MAX
                    chunk_count = 0
                    df = 0
                    values: list[int] = []
                    ids_cursor = connection.execute("SELECT d.doc_id FROM feature_docs f JOIN documents d ON d.product_key=f.product_key WHERE f.term=? ORDER BY d.doc_id", (term,))

                    def flush_chunk(chunk_values: Sequence[int]) -> None:
                        nonlocal first_chunk, chunk_count, df
                        if not chunk_values:
                            return
                        encoded = _encode_postings(chunk_values)
                        chunk_index = chunk_directory.tell() // _CHUNK_STRUCT.size
                        if chunk_index > _U32_MAX:
                            raise SearchPackBuildError("SearchPack chunk directory exceeds u32")
                        if first_chunk == _U32_MAX:
                            first_chunk = chunk_index
                        chunk_directory.write(_CHUNK_STRUCT.pack(_U32_MAX, postings.tell(), len(encoded), len(chunk_values), 0))
                        postings.write(encoded)
                        chunk_count += 1
                        df += len(chunk_values)

                    for row in ids_cursor:
                        value = row[0]
                        if not isinstance(value, int) or value < 0 or value > _U32_MAX:
                            raise SearchPackBuildError("SearchPack document ID is invalid")
                        values.append(value)
                        if len(values) >= POSTINGS_BLOCK_MAX_IDS:
                            flush_chunk(values)
                            values = []
                    flush_chunk(values)
                    if df <= 0 or first_chunk == _U32_MAX:
                        raise SearchPackBuildError("SearchPack term has no postings")
                    # Patch the exact df into all chunks for this term, then
                    # patch the term directory entry.  Only chunk_count IDs
                    # and one bounded chunk are ever retained in memory.
                    for index in range(first_chunk, first_chunk + chunk_count):
                        position = index * _CHUNK_STRUCT.size
                        chunk_directory.seek(position)
                        next_chunk, posting_offset, posting_length, count, _ = _CHUNK_STRUCT.unpack(chunk_directory.read(_CHUNK_STRUCT.size))
                        next_value = index + 1 if index + 1 < first_chunk + chunk_count else _U32_MAX
                        chunk_directory.seek(position)
                        chunk_directory.write(_CHUNK_STRUCT.pack(next_value, posting_offset, posting_length, count, df))
                    term_directory.seek(term_pos)
                    term_directory.write(_TERM_STRUCT.pack(first_chunk, chunk_count, df, 0))
                    term_directory.seek(0, os.SEEK_END)
                    term_ordinal += 1
                term_directory.flush()
                chunk_directory.flush()
                postings.flush()
            finally:
                term_directory.close()
                chunk_directory.close()
                postings.close()
            lexicon_blocks = _build_fst_blocks(
                lexicon_path,
                ((str(term), int(ordinal)) for ordinal, (term,) in enumerate(connection.execute("SELECT term FROM feature_docs GROUP BY term ORDER BY term"))),
            )
            # Compression is constant for all blocks in this build; obtain it
            # from the last block's closure without retaining payloads.
            compression = compression if "compression" in locals() else "zlib"
        finally:
            connection.close()

    files = {
        "lexicon": _file_descriptor(lexicon_path, f"{SEARCHPACK_ROOT}/regions/{region_id}/lexicon.bin"),
        "termDirectory": _file_descriptor(term_directory_path, f"{SEARCHPACK_ROOT}/regions/{region_id}/term-directory.bin"),
        "chunkDirectory": _file_descriptor(chunk_directory_path, f"{SEARCHPACK_ROOT}/regions/{region_id}/chunk-directory.bin"),
        "postings": _file_descriptor(postings_path, f"{SEARCHPACK_ROOT}/regions/{region_id}/postings.bin"),
        "docstore": _file_descriptor(docstore_path, f"{SEARCHPACK_ROOT}/regions/{region_id}/docstore.bin"),
        "keyLexicon": _file_descriptor(key_lexicon_path, f"{SEARCHPACK_ROOT}/regions/{region_id}/key-lexicon.bin"),
    }
    metadata = {
        "schemaVersion": SEARCHPACK_SCHEMA_VERSION,
        "formatVersion": SEARCHPACK_FORMAT_VERSION,
        "policyVersion": LEXICAL_FEATURE_POLICY_VERSION,
        "regionId": region_id,
        "sourceReleaseId": release_id,
        "sourceSearchIndexPath": f"micro-1024/regions/{region_id}/search-index.jsonl.gz",
        "sourceSearchIndexSha256": descriptor["sha256"],
        "sourceSearchIndexBytes": descriptor["bytes"],
        "sourceSearchIndexUncompressedSha256": descriptor["uncompressedSha256"],
        "sourceSearchIndexUncompressedBytes": descriptor["uncompressedBytes"],
        "vocabularySha256": vocabulary_sha256,
        "attribution": {"dataset": "Precios Claros - Base SEPA", "license": "Creative Commons Attribution 4.0", "catalog": "https://datos.gob.ar"},
        "documentCount": total_records,
        "termCount": term_ordinal,
        "featureRowCount": feature_rows,
        "docstoreCompression": compression,
        "docstoreBlockRecords": DOCSTORE_BLOCK_RECORDS,
        "postingsChunkMaxIds": POSTINGS_BLOCK_MAX_IDS,
        "files": files,
        "lexiconBlocks": list(lexicon_blocks),
        "keyLexiconBlocks": list(key_lexicon_blocks),
        "docstoreBlocks": docstore_blocks,
    }
    metadata_path = staging / "metadata.json"
    metadata_path.write_bytes(_canonical_json(metadata))
    return {"metadata": metadata, "metadataPath": metadata_path, "files": files, "paths": tuple(value["path"] for value in files.values())}


def _hard_link_objects(source_workspace: Path, output_workspace: Path, manifest: Mapping[str, Any]) -> None:
    descriptors = manifest.get("objects")
    if not isinstance(descriptors, list):
        raise SearchPackBuildError("release object descriptors are missing")
    for descriptor in descriptors:
        if not isinstance(descriptor, Mapping):
            raise SearchPackBuildError("release object descriptor is invalid")
        digest = descriptor.get("sha256")
        if not isinstance(digest, str) or len(digest) != 64:
            raise SearchPackBuildError("release object hash is invalid")
        source = source_workspace / "objects" / "sha256" / digest
        target = output_workspace / "objects" / "sha256" / digest
        if not source.is_file():
            raise SearchPackBuildError(f"source immutable object is missing: {digest}")
        if source.stat().st_size != descriptor.get("bytes") or _sha256_file(source) != digest:
            raise SearchPackBuildError(f"source immutable object failed verification: {descriptor.get('path')}")
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if target.stat().st_size != source.stat().st_size or _sha256_file(target) != digest:
                raise SearchPackBuildError(f"reused immutable object differs: {digest}")
        else:
            try:
                os.link(source, target)
            except OSError as exc:
                raise SearchPackBuildError("reused immutable object could not be hard-linked") from exc


def _publish_object(staging: Path, output_workspace: Path, logical_path: str) -> dict[str, Any]:
    source = staging / Path(*logical_path.split("/"))
    if not source.is_file():
        raise SearchPackBuildError(f"generated SearchPack file is missing: {logical_path}")
    digest = _sha256_file(source)
    target = output_workspace / "objects" / "sha256" / digest
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if target.stat().st_size != source.stat().st_size or _sha256_file(target) != digest:
            raise SearchPackBuildError(f"generated immutable object differs: {logical_path}")
    else:
        try:
            os.link(source, target)
        except OSError:
            shutil.copyfile(source, target)
    return {"path": logical_path, "bytes": source.stat().st_size, "sha256": digest}


def _vocabulary_sha256() -> str:
    path = Path(__file__).with_name("data") / "argentina_input_aliases.json"
    return hashlib.sha256(path.read_bytes()).hexdigest()


def derive_searchpack_workspace(source_workspace: Path | str, output_workspace: Path | str, release_ids: Iterable[str]) -> dict[str, Any]:
    source = Path(source_workspace).resolve()
    output = Path(output_workspace).resolve()
    if source == output or output.exists():
        raise SearchPackBuildError("derived workspace must be a new path")
    ids = tuple(release_ids)
    if not ids:
        raise SearchPackBuildError("at least one release is required")
    partial = output.parent / f".{output.name}.partial"
    if partial.exists():
        raise SearchPackBuildError("partial derived workspace already exists")
    partial.mkdir(parents=True)
    (partial / "objects" / "sha256").mkdir(parents=True)
    (partial / "releases").mkdir(parents=True)
    vocabulary_sha = _vocabulary_sha256()
    derived: list[dict[str, Any]] = []
    try:
        for release_id in ids:
            base = _release_manifest(source, release_id)
            regional = _regional_search_descriptors(base)
            _hard_link_objects(source, partial, base)
            release_staging = partial / ".searchpack" / release_id
            region_metadata_paths: dict[str, str] = {}
            region_metadata_hashes: dict[str, str] = {}
            region_source_hashes: dict[str, str] = {}
            generated_objects: list[dict[str, Any]] = []
            for region_id, descriptor in regional.items():
                complete_descriptor = _complete_search_descriptor(source, base, region_id, descriptor)
                result = _build_region(source, base, release_id, region_id, complete_descriptor, release_staging / "regions" / region_id, vocabulary_sha)
                metadata_logical = f"{SEARCHPACK_ROOT}/regions/{region_id}/metadata.json"
                # Files are staged under the same logical path, then published
                # as content-addressed objects.  Moving the generated tree to
                # its final logical staging path avoids a second full copy.
                generated_dir = partial / Path(*SEARCHPACK_ROOT.split("/")) / "regions" / region_id
                generated_dir.mkdir(parents=True, exist_ok=True)
                for file_name in ("metadata.json", "lexicon.bin", "term-directory.bin", "chunk-directory.bin", "postings.bin", "docstore.bin", "key-lexicon.bin"):
                    source_file = result["metadataPath"].parent / file_name
                    target_file = generated_dir / file_name
                    if source_file != target_file:
                        os.replace(source_file, target_file)
                metadata_object = _publish_object(partial / "", partial, metadata_logical)
                # The object is now in the content-addressed pool; the logical
                # file remains available for local direct-file qualification.
                generated_objects.append(metadata_object)
                for name, descriptor_value in result["files"].items():
                    logical = str(descriptor_value["path"])
                    generated_objects.append(_publish_object(partial / "", partial, logical))
                region_metadata_paths[region_id] = metadata_logical
                region_metadata_hashes[region_id] = metadata_object["sha256"]
                region_source_hashes[region_id] = str(complete_descriptor["sha256"])
            derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
            release_dir = partial / "releases" / derived_id
            release_dir.mkdir(parents=True)
            manifest = copy.deepcopy(base)
            manifest["releaseId"] = derived_id
            manifest["searchPackArtifact"] = {
                "schemaVersion": SEARCHPACK_SCHEMA_VERSION,
                "formatVersion": SEARCHPACK_FORMAT_VERSION,
                "policyVersion": LEXICAL_FEATURE_POLICY_VERSION,
                "sourceReleaseId": release_id,
                "attribution": {"dataset": "Precios Claros - Base SEPA", "license": "Creative Commons Attribution 4.0", "catalog": "https://datos.gob.ar"},
                "regions": sorted(regional),
                "metadataPaths": dict(sorted(region_metadata_paths.items())),
                "metadataSha256": dict(sorted(region_metadata_hashes.items())),
                "sourceSearchIndexSha256": dict(sorted(region_source_hashes.items())),
            }
            objects = [dict(value) for value in manifest["objects"]]
            objects.extend(generated_objects)
            deduped: dict[str, dict[str, Any]] = {}
            for value in objects:
                path = value.get("path")
                if not isinstance(path, str):
                    raise SearchPackBuildError("generated object path is invalid")
                if path in deduped and deduped[path] != value:
                    raise SearchPackBuildError(f"duplicate generated object path: {path}")
                deduped[path] = value
            manifest["objects"] = [deduped[path] for path in sorted(deduped)]
            raw = _canonical_json(manifest)
            (release_dir / "manifest.json").write_bytes(raw)
            (release_dir / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
            derived.append({"sourceReleaseId": release_id, "derivedReleaseId": derived_id, "regionCount": len(regional), "documentCounts": {region: _read_json(partial / Path(*region_metadata_paths[region].split("/")), region).get("documentCount") for region in sorted(regional)}})
        plan = {"schemaVersion": "valuepilot-derived-searchpack-plan-v1", "sourceWorkspace": str(source), "derivedWorkspace": str(output), "oldReleasesUnchanged": True, "releases": derived, "publicationOrder": ["immutable_objects", "verify_objects", "manifests", "verify_manifests"]}
        (partial / "SEARCHPACK_RELEASE_PLAN.json").write_bytes(_canonical_json(plan))
        os.replace(partial, output)
        return plan
    except Exception:
        shutil.rmtree(partial, ignore_errors=True)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-workspace", required=True, type=Path)
    parser.add_argument("--output-workspace", required=True, type=Path)
    parser.add_argument("--release-id", action="append", dest="release_ids")
    args = parser.parse_args(argv)
    try:
        ids = tuple(args.release_ids or sorted(path.name for path in (args.source_workspace / "releases").iterdir() if path.is_dir()))
        plan = derive_searchpack_workspace(args.source_workspace, args.output_workspace, ids)
    except (SearchPackBuildError, SearchPackError, OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"SearchPack derivation failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(plan, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


__all__ = ["SEARCHPACK_RELEASE_SUFFIX", "SearchPackBuildError", "derive_searchpack_workspace"]
