from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_release_acquisition import (
    CATALOG_ENDPOINT,
    DATASET_ID,
    AcquisitionCandidate,
    AcquisitionError,
    PrivateSourceArchive,
    classify_source,
    discover_metadata,
    download_candidate,
    select_latest_candidate,
    validate_official_url,
)


FIXTURE = Path(__file__).parents[1] / "fixtures" / "argentina_release_acquisition" / "package_show.json"
SAFE_RESOLVER = lambda host, port: ["93.184.216.34"]
PRIVATE_RESOLVER = lambda host, port: ["10.0.0.8"]


class FakeResponse:
    def __init__(self, payload: bytes, *, url: str, status: int = 200, headers: dict[str, str] | None = None):
        self.payload = payload
        self.url = url
        self.status = status
        self.code = status
        self.headers = headers or {"Content-Length": str(len(payload))}
        self.offset = 0
        self.closed = False

    def geturl(self) -> str:
        return self.url

    def read(self, amount: int = -1) -> bytes:
        if amount < 0:
            amount = len(self.payload) - self.offset
        value = self.payload[self.offset : self.offset + amount]
        self.offset += len(value)
        return value

    def close(self) -> None:
        self.closed = True


class SequenceOpener:
    def __init__(self, *outcomes):
        self.outcomes = list(outcomes)
        self.calls = 0

    def __call__(self, url: str, **kwargs):
        self.calls += 1
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


class ArgentinaReleaseAcquisitionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.metadata = FIXTURE.read_bytes()
        self.candidate = AcquisitionCandidate(
            provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
            dataset_id=DATASET_ID,
            resource_id="resource-martes-fixture",
            resource_name="Martes",
            official_url="https://datos.produccion.gob.ar/download/sepa_martes.zip",
            observed_size=None,
            last_modified="2026-06-30T16:19:00.091864",
            observed_at="2026-09-13T12:00:00Z",
            expected_source_host="datos.produccion.gob.ar",
            discovery_response_sha256="a" * 64,
            release_date_hint="2026-06-30",
        )

    def test_discover_metadata_parses_official_contract_and_accented_weekday(self) -> None:
        result = discover_metadata(
            observed_at="2026-09-13T12:00:00-07:00",
            endpoint=CATALOG_ENDPOINT,
            fetcher=lambda _: self.metadata,
            resolver=SAFE_RESOLVER,
        )
        self.assertEqual(result.dataset_id, DATASET_ID)
        self.assertEqual(result.dataset_title, "Precios Claros - Base SEPA")
        self.assertEqual(result.license_title, "Creative Commons Attribution 4.0")
        self.assertEqual([item.resource_name for item in result.candidates], ["Martes", "Miércoles"])
        self.assertEqual(result.candidates[1].release_date_hint, "2026-07-01")
        self.assertEqual(result.candidates[0].last_modified, "2026-06-30T16:19:00.091864")
        self.assertEqual(result.observed_at, "2026-09-13T19:00:00Z")
        self.assertEqual(len(result.response_sha256), 64)
        self.assertEqual(result.attempts, 1)
        self.assertEqual(select_latest_candidate(result.candidates).resource_name, "Miércoles")

    def test_metadata_retries_502_then_succeeds_once_or_twice_with_fixed_backoff(self) -> None:
        for failures in (1, 2):
            with self.subTest(transient_failures=failures):
                outcomes = [
                    AcquisitionError(
                        "temporary gateway failure",
                        code="UPSTREAM_TRANSIENT_HTTP",
                        http_status=502,
                    )
                    for _ in range(failures)
                ] + [self.metadata]
                calls: list[str] = []
                delays: list[float] = []

                def fetcher(endpoint: str) -> bytes:
                    calls.append(endpoint)
                    outcome = outcomes.pop(0)
                    if isinstance(outcome, BaseException):
                        raise outcome
                    return outcome

                result = discover_metadata(
                    observed_at="2026-09-13T12:00:00Z",
                    fetcher=fetcher,
                    resolver=SAFE_RESOLVER,
                    sleep_fn=delays.append,
                )
                self.assertEqual(result.attempts, failures + 1)
                self.assertEqual(len(calls), failures + 1)
                self.assertEqual(delays, [0.5, 1.0][:failures])

    def test_metadata_three_502_failures_become_explicit_upstream_outage(self) -> None:
        calls: list[str] = []
        delays: list[float] = []

        def fetcher(endpoint: str) -> bytes:
            calls.append(endpoint)
            raise AcquisitionError(
                "temporary gateway failure",
                code="UPSTREAM_TRANSIENT_HTTP",
                http_status=502,
            )

        with self.assertRaises(AcquisitionError) as failure:
            discover_metadata(
                observed_at="2026-09-13T12:00:00Z",
                fetcher=fetcher,
                resolver=SAFE_RESOLVER,
                sleep_fn=delays.append,
            )
        self.assertEqual(failure.exception.code, "UPSTREAM_TEMPORARILY_UNAVAILABLE")
        self.assertEqual(failure.exception.http_status, 502)
        self.assertEqual(failure.exception.attempts, 3)
        self.assertEqual(failure.exception.cause_code, "UPSTREAM_TRANSIENT_HTTP")
        self.assertEqual(len(calls), 3)
        self.assertEqual(delays, [0.5, 1.0])

    def test_metadata_429_and_timeout_recover_with_bounded_retries(self) -> None:
        for first_error in (
            AcquisitionError("rate limited", code="UPSTREAM_TRANSIENT_HTTP", http_status=429),
            TimeoutError("temporary timeout"),
        ):
            with self.subTest(error=type(first_error).__name__):
                outcomes = [first_error, self.metadata]
                delays: list[float] = []

                def fetcher(_: str) -> bytes:
                    outcome = outcomes.pop(0)
                    if isinstance(outcome, BaseException):
                        raise outcome
                    return outcome

                result = discover_metadata(
                    observed_at="2026-09-13T12:00:00Z",
                    fetcher=fetcher,
                    resolver=SAFE_RESOLVER,
                    sleep_fn=delays.append,
                )
                self.assertEqual(result.attempts, 2)
                self.assertEqual(delays, [0.5])

    def test_permanent_metadata_4xx_is_not_retried(self) -> None:
        calls = 0
        delays: list[float] = []

        def fetcher(_: str) -> bytes:
            nonlocal calls
            calls += 1
            raise AcquisitionError("not found", code="HTTP_ERROR", http_status=404)

        with self.assertRaises(AcquisitionError) as failure:
            discover_metadata(
                observed_at="2026-09-13T12:00:00Z",
                fetcher=fetcher,
                resolver=SAFE_RESOLVER,
                sleep_fn=delays.append,
            )
        self.assertEqual(failure.exception.code, "HTTP_ERROR")
        self.assertEqual(calls, 1)
        self.assertEqual(delays, [])

    def test_discovery_rejects_malformed_json_and_contract_changes(self) -> None:
        with self.assertRaisesRegex(AcquisitionError, "valid UTF-8 JSON"):
            discover_metadata(observed_at="2026-09-13T12:00:00Z", fetcher=lambda _: b"not-json", resolver=SAFE_RESOLVER)
        value = json.loads(self.metadata)
        value["result"]["id"] = "wrong"
        with self.assertRaisesRegex(AcquisitionError, "official Base SEPA"):
            discover_metadata(observed_at="2026-09-13T12:00:00Z", fetcher=lambda _: json.dumps(value).encode(), resolver=SAFE_RESOLVER)
        value = json.loads(self.metadata)
        value["result"]["license_title"] = "Unknown"
        with self.assertRaisesRegex(AcquisitionError, "licence changed"):
            discover_metadata(observed_at="2026-09-13T12:00:00Z", fetcher=lambda _: json.dumps(value).encode(), resolver=SAFE_RESOLVER)

    def test_url_policy_rejects_credentials_private_schemes_hosts_and_dns(self) -> None:
        bad = (
            "http://datos.produccion.gob.ar/file.zip",
            "ftp://datos.produccion.gob.ar/file.zip",
            "https://user:pass@datos.produccion.gob.ar/file.zip",
            "https://localhost/file.zip",
            "https://127.0.0.1/file.zip",
            "https://evil.example/file.zip",
        )
        for url in bad:
            with self.subTest(url=url), self.assertRaises(AcquisitionError):
                validate_official_url(url, expected_host="datos.produccion.gob.ar", resolver=SAFE_RESOLVER)
        with self.assertRaisesRegex(AcquisitionError, "private"):
            validate_official_url("https://datos.produccion.gob.ar/file.zip", expected_host="datos.produccion.gob.ar", resolver=PRIVATE_RESOLVER)

    def test_discovery_rejects_unsafe_resource_url_before_download(self) -> None:
        value = json.loads(self.metadata)
        value["result"]["resources"][0]["url"] = "https://169.254.169.254/latest/meta-data"
        with self.assertRaises(AcquisitionError):
            discover_metadata(observed_at="2026-09-13T12:00:00Z", fetcher=lambda _: json.dumps(value).encode(), resolver=SAFE_RESOLVER)

    def test_download_streams_hashes_and_handles_windows_path(self) -> None:
        payload = b"PK\x03\x04" + (b"release" * 1000)
        opener = SequenceOpener(FakeResponse(payload, url=self.candidate.official_url))
        with tempfile.TemporaryDirectory(prefix="valuepilot-acq-") as directory:
            destination = Path(directory) / "nested" / "sepa_martes.zip"
            result = download_candidate(self.candidate, destination, opener=opener, resolver=SAFE_RESOLVER)
            self.assertEqual(result.bytes, len(payload))
            self.assertEqual(result.sha256, hashlib.sha256(payload).hexdigest())
            self.assertEqual(result.attempts, 1)
            self.assertEqual(destination.read_bytes(), payload)
            self.assertFalse(any(destination.parent.glob("*.part")))

    def test_download_retries_transient_failure_with_bounded_jitter(self) -> None:
        payload = b"zip-bytes"
        opener = SequenceOpener(TimeoutError("temporary"), FakeResponse(payload, url=self.candidate.official_url))
        delays: list[float] = []
        with tempfile.TemporaryDirectory(prefix="valuepilot-acq-") as directory:
            result = download_candidate(self.candidate, Path(directory) / "source.zip", opener=opener, resolver=SAFE_RESOLVER, sleep_fn=delays.append, random_fn=lambda: 0.0)
        self.assertEqual(result.attempts, 2)
        self.assertEqual(opener.calls, 2)
        self.assertEqual(delays, [0.5])

    def test_download_rejects_oversized_truncated_and_unsafe_redirect(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-acq-") as directory:
            with self.assertRaisesRegex(AcquisitionError, "exceeds"):
                download_candidate(self.candidate, Path(directory) / "too-big.zip", opener=SequenceOpener(FakeResponse(b"x", url=self.candidate.official_url, headers={"Content-Length": "9"})), resolver=SAFE_RESOLVER, max_bytes=8)
            sized = AcquisitionCandidate(**{**self.candidate.__dict__, "observed_size": 10})
            with self.assertRaisesRegex(AcquisitionError, "differs"):
                download_candidate(sized, Path(directory) / "short.zip", opener=SequenceOpener(FakeResponse(b"short", url=sized.official_url)), resolver=SAFE_RESOLVER, sleep_fn=lambda _: None)
            with self.assertRaises(AcquisitionError):
                download_candidate(self.candidate, Path(directory) / "redirect.zip", opener=SequenceOpener(FakeResponse(b"x", url="https://evil.example/file.zip")), resolver=SAFE_RESOLVER)

    def test_source_classification_covers_newer_older_same_and_revision(self) -> None:
        digest = "b" * 64
        self.assertEqual(classify_source(release_date="2026-09-09", source_sha256=digest, current_release_date="2026-09-08", current_source_sha256="a" * 64).disposition, "NEWER_RELEASE")
        self.assertEqual(classify_source(release_date="2026-09-07", source_sha256=digest, current_release_date="2026-09-08", current_source_sha256="a" * 64).disposition, "OLDER_REJECTED")
        self.assertEqual(classify_source(release_date="2026-09-08", source_sha256="a" * 64, current_release_date="2026-09-08", current_source_sha256="a" * 64).disposition, "NO_CHANGE")
        self.assertEqual(classify_source(release_date="2026-09-08", source_sha256=digest, current_release_date="2026-09-08", current_source_sha256="a" * 64).disposition, "REVIEW_REQUIRED")
        self.assertEqual(classify_source(release_date="2026-09-08", source_sha256=digest, current_release_date="2026-09-08", current_source_sha256="a" * 64, upstream_changed=True).disposition, "SAME_DAY_REVISION")

    def test_private_archive_is_content_addressed_and_deduplicates(self) -> None:
        payload = b"official-source-fixture"
        with tempfile.TemporaryDirectory(prefix="valuepilot-acq-") as directory:
            source = Path(directory) / "sepa_martes.zip"
            source.write_bytes(payload)
            store = LocalFilesystemObjectStore(Path(directory) / "archive-store")
            archive = PrivateSourceArchive(store)
            first = archive.archive_file(source, candidate=self.candidate, acquired_at="2026-09-13T12:00:00Z", release_date="2026-09-08")
            second = archive.archive_file(source, candidate=self.candidate, acquired_at="2026-09-13T12:01:00Z", release_date="2026-09-08")
            self.assertFalse(first.reused)
            self.assertTrue(second.reused)
            self.assertTrue(first.key.startswith("source-archive/sha256/"))
            self.assertTrue(store.exists(first.key))
            self.assertTrue(store.exists(first.manifest_key))
            manifest = json.loads(store.get(first.manifest_key))
            self.assertFalse(manifest["rawProviderDataCommitted"])
            self.assertFalse(manifest["runtimePublicationAllowed"])
            self.assertEqual(manifest["candidate"]["resourceId"], self.candidate.resource_id)
            self.assertEqual(manifest["discoveryResponseSha256"], self.candidate.discovery_response_sha256)
            # A later metadata poll has a new observation time/hash but the
            # exact source bytes remain the same archive identity.
            later_candidate = AcquisitionCandidate(**{**self.candidate.__dict__, "observed_at": "2026-09-13T13:00:00Z", "discovery_response_sha256": "b" * 64, "last_modified": "2026-09-13T12:59:00Z"})
            later = archive.archive_file(source, candidate=later_candidate, acquired_at="2026-09-13T13:01:00Z", release_date="2026-09-08")
            self.assertTrue(later.reused)
            restored = archive.restore_file(first.key, Path(directory) / "restored" / "source.zip", expected_sha256=first.sha256)
            self.assertEqual(restored.read_bytes(), payload)
            with self.assertRaises(AcquisitionError):
                archive.restore_file("source-archive/sha256/" + ("b" * 64) + ".zip", Path(directory) / "bad.zip", expected_sha256=first.sha256)

    def test_archive_conflict_fails_closed_and_no_candidate_is_not_selectable(self) -> None:
        with self.assertRaises(AcquisitionError):
            select_latest_candidate(())
        with tempfile.TemporaryDirectory(prefix="valuepilot-acq-") as directory:
            source = Path(directory) / "source.zip"
            source.write_bytes(b"one")
            store = LocalFilesystemObjectStore(Path(directory) / "store")
            archive = PrivateSourceArchive(store)
            first = archive.archive_file(source, candidate=self.candidate, acquired_at="2026-09-13T12:00:00Z", release_date="2026-09-08")
            store._path(first.key).write_bytes(b"tampered")
            with self.assertRaises(AcquisitionError):
                archive.archive_file(source, candidate=self.candidate, acquired_at="2026-09-13T12:00:00Z", release_date="2026-09-08")

    def test_committed_source_contract_keeps_martes_local_baseline_separate(self) -> None:
        contract_path = Path(__file__).parents[2] / "ARGENTINA_AUTOMATED_RELEASE_SOURCE_CONTRACT.json"
        contract = json.loads(contract_path.read_text(encoding="utf-8"))
        self.assertEqual(contract["dataset"]["title"], "Precios Claros - Base SEPA")
        martes = next(item for item in contract["resources"] if item["name"] == "Martes")
        self.assertEqual(martes["id"], "7df65a95-1f1d-52a8-9a93-082e35998831")
        self.assertIsNone(martes["size"])
        self.assertFalse(contract["downloadContract"]["resourceDownloadAccessProven"])
        self.assertEqual(contract["localVerifiedBaseline"]["sha256"], "aeef3399fa20e751e80cda46cbb3a38349e378b51832ecffe1f779b986479815")
        self.assertFalse(contract["localVerifiedBaseline"]["committed"])


if __name__ == "__main__":
    unittest.main()
