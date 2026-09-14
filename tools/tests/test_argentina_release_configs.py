from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]


class ArgentinaReleaseConfigurationTests(unittest.TestCase):
    def test_source_contract_is_bounded_and_keeps_local_martes_separate(self) -> None:
        value = json.loads((ROOT / "ARGENTINA_AUTOMATED_RELEASE_SOURCE_CONTRACT.json").read_text(encoding="utf-8"))
        self.assertEqual(value["schemaVersion"], "valuepilot-argentina-source-contract-v1")
        self.assertEqual(value["dataset"]["metadataHttpStatus"], 200)
        self.assertEqual(value["downloadContract"]["officialHost"], "datos.produccion.gob.ar")
        self.assertFalse(value["downloadContract"]["resourceDownloadAccessProven"])
        self.assertFalse(value["localVerifiedBaseline"]["committed"])
        self.assertEqual(len(value["resources"]), 7)
        self.assertEqual(len({item["id"] for item in value["resources"]}), 7)
        self.assertTrue(all(item["url"].startswith("https://datos.produccion.gob.ar/") for item in value["resources"]))

    def test_cloud_build_and_workflow_are_shadow_safe_templates(self) -> None:
        cloudbuild = (ROOT / "cloudbuild" / "argentina-release-worker.yaml").read_text(encoding="utf-8")
        workflow = (ROOT / "workflows" / "argentina-release.yaml").read_text(encoding="utf-8")
        for text in (cloudbuild, workflow):
            self.assertIn("E2_STANDARD_2", text)
            self.assertIn("activePointerWrites", text)
            self.assertNotRegex(text, re.compile(r"(AKIA|BEGIN [A-Z ]*PRIVATE KEY|ghp_[A-Za-z0-9])"))
            self.assertIn("tools.argentina_release_handoff_worker", text)
        self.assertIn("VALUEPILOT_SOURCE_ARCHIVE_KEY", cloudbuild)
        self.assertIn("VALUEPILOT_SOURCE_ARCHIVE_SHA256", cloudbuild)
        self.assertIn("source_archive_key == \"\"", workflow)
        self.assertIn("CURRENT_TRUSTED_SOURCE_IDENTITY_REQUIRED", workflow)
        self.assertIn("SOURCE_ARCHIVE_KEY_AND_SHA_REQUIRED_TOGETHER", workflow)
        self.assertIn("VALUEPILOT_SOURCE_ARCHIVE_KEY=${source_archive_key}", workflow)
        self.assertIn("VALUEPILOT_SOURCE_ARCHIVE_SHA256=${source_archive_sha256}", workflow)
        self.assertIn("NO_CHANGE", workflow)
        self.assertIn("googleapis.cloudbuild.v1.projects.builds.create", workflow)
        self.assertIn("release_build.metadata.build.status", workflow)
        self.assertIn("connector_params", workflow)
        self.assertIn("timeout: 14400", workflow)
        self.assertIn("control/workflow-discovery-state.json", workflow)
        self.assertIn("activePointerWrites: 0", workflow)
        self.assertIn("state_error.code == 404", workflow)
        self.assertIn("CONTROL_STATE_UNAVAILABLE", workflow)
        self.assertNotIn("as: state_error\n          steps:\n            - no_previous_state", workflow)
        self.assertIn("--promotion-mode=${promotion_mode}", workflow)
        self.assertIn("read_worker_summary", workflow)
        self.assertIn("WORKER_SUMMARY_IDENTITY_MISMATCH", workflow)
        self.assertIn("PROMOTION_MODE", cloudbuild)
        self.assertTrue((ROOT / "tools" / "argentina_release_promoter.py").is_file())
        self.assertTrue((ROOT / "tools" / "argentina_release_handoff_worker.py").is_file())

    def test_scheduler_template_is_paused_and_argentina_localized(self) -> None:
        value = json.loads((ROOT / "scheduler" / "argentina-release-job.example.json").read_text(encoding="utf-8"))
        self.assertEqual(value["deploymentState"], "DISABLED_UNTIL_CONTROLLED_ACTIVATION")
        self.assertEqual(value["timeZone"], "America/Argentina/Buenos_Aires")
        self.assertEqual(value["schedule"], "30 13,15,17 * * *")
        self.assertIn("workflowexecutions.googleapis.com", value["httpTarget"]["uri"])
        serialized = json.dumps(value)
        self.assertIn("REPLACE_WITH_SCHEDULER_INVOKER_SERVICE_ACCOUNT", serialized)
        self.assertNotRegex(serialized, re.compile(r"(AKIA|BEGIN [A-Z ]*PRIVATE KEY|ghp_[A-Za-z0-9])"))


if __name__ == "__main__":
    unittest.main()
