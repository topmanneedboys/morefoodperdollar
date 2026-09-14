from __future__ import annotations

import unittest

from tools.argentina_release_acquisition import AcquisitionCandidate, AcquisitionError, DATASET_ID, select_latest_candidate


WEEKDAYS = ("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")


def candidate(name: str, observed_at: str, *, hint: str = "2026-07-01", suffix: str = "x") -> AcquisitionCandidate:
    slug = name.lower().replace("á", "a").replace("é", "e")
    return AcquisitionCandidate(
        provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
        dataset_id=DATASET_ID,
        resource_id=f"resource-{slug}-{suffix}",
        resource_name=name,
        official_url=f"https://datos.produccion.gob.ar/download/sepa_{slug}.zip",
        observed_size=None,
        last_modified="2026-07-01T12:00:00",
        observed_at=observed_at,
        expected_source_host="datos.produccion.gob.ar",
        discovery_response_sha256="a" * 64,
        release_date_hint=hint,
    )


def full_week(observed_at: str) -> tuple[AcquisitionCandidate, ...]:
    # Give Monday the newest stale metadata hint on purpose.  The selector must
    # still prefer the Argentina-local weekday resource when it exists.
    values = []
    for index, name in enumerate(WEEKDAYS):
        hint = "2099-12-31" if name == "Lunes" else f"2026-07-0{index + 1}"
        values.append(candidate(name, observed_at, hint=hint, suffix=str(index)))
    return tuple(values)


class ArgentinaCandidateSelectionTests(unittest.TestCase):
    def test_utc_sunday_evening_selects_argentina_sunday_not_stale_monday_hint(self) -> None:
        selected = select_latest_candidate(full_week("2026-09-14T01:26:02Z"))
        self.assertEqual(selected.resource_name, "Domingo")

    def test_utc_monday_after_0300_selects_argentina_monday(self) -> None:
        selected = select_latest_candidate(full_week("2026-09-14T03:05:00Z"))
        self.assertEqual(selected.resource_name, "Lunes")

    def test_before_0300_utc_still_uses_previous_argentina_calendar_day(self) -> None:
        selected = select_latest_candidate(full_week("2026-09-14T02:59:59Z"))
        self.assertEqual(selected.resource_name, "Domingo")

    def test_mixed_observation_timestamps_fail_closed(self) -> None:
        candidates = (
            candidate("Domingo", "2026-09-14T01:26:02Z", suffix="a"),
            candidate("Lunes", "2026-09-14T03:05:00Z", suffix="b"),
        )
        with self.assertRaisesRegex(AcquisitionError, "observation timestamps differ") as raised:
            select_latest_candidate(candidates)
        self.assertEqual(raised.exception.code, "INVALID_METADATA")

    def test_duplicate_expected_weekday_fails_closed(self) -> None:
        candidates = (
            candidate("Domingo", "2026-09-14T01:26:02Z", suffix="a"),
            candidate("Domingo", "2026-09-14T01:26:02Z", suffix="b"),
        )
        with self.assertRaisesRegex(AcquisitionError, "multiple candidates") as raised:
            select_latest_candidate(candidates)
        self.assertEqual(raised.exception.code, "AMBIGUOUS_CANDIDATE")

    def test_missing_expected_weekday_keeps_deterministic_hint_fallback(self) -> None:
        candidates = (
            candidate("Martes", "2026-09-14T01:26:02Z", hint="2026-07-01", suffix="a"),
            candidate("Miércoles", "2026-09-14T01:26:02Z", hint="2026-07-02", suffix="b"),
        )
        selected = select_latest_candidate(candidates)
        self.assertEqual(selected.resource_name, "Miércoles")


if __name__ == "__main__":
    unittest.main()
