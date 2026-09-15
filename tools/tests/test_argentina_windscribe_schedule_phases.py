from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "acquire-argentina-sepa-windscribe.yml"


class ArgentinaWindscribeSchedulePhaseTests(unittest.TestCase):
    def test_three_weekday_slots_map_to_bounded_phases(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("'30 16 * * 1-5') check_phase=\"PRIMARY\"", text)
        self.assertIn("'30 18 * * 1-5') check_phase=\"METADATA_ONLY\"", text)
        self.assertIn("'30 20 * * 1-5') check_phase=\"LATE_RECHECK\"", text)
        self.assertIn("SCHEDULE_CRON: ${{ github.event.schedule || '' }}", text)
        self.assertIn("EVENT_NAME: ${{ github.event_name }}", text)
        self.assertIn("--check-phase \"${check_phase}\"", text)

    def test_phased_control_owns_preflight_and_finalize_but_existing_reservation_gate_remains(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertGreaterEqual(text.count("python -m tools.argentina_acquisition_schedule_control_v2"), 2)
        self.assertIn("python -m tools.argentina_acquisition_schedule_control reserve", text)
        self.assertIn("promotionMode\": \"SHADOW\"", text)
        self.assertNotIn("promotionMode\": \"AUTO_ACTIVATE\"", text)
        self.assertNotIn("promotionMode\": \"STAGE_CANARY\"", text)

    def test_late_recheck_is_documented_as_one_bounded_extra_byte_check(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("one bounded late byte recheck only when the primary result was stale", text)
        self.assertIn("Check phase: ${{ steps.preflight.outputs.check_phase }}", text)
        self.assertIn("cancel-in-progress: false", text)
        self.assertIn("ARGENTINA_AUTOMATED_ACQUISITION_ENABLED", text)


if __name__ == "__main__":
    unittest.main()
