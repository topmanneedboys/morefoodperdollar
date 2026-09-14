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
        self.assertIn('VALUEPILOT_SOURCE_ARCHIVE_KEY=\" + source_archive_key', workflow)
        self.assertIn('VALUEPILOT_SOURCE_ARCHIVE_SHA256=\" + source_archive_sha256', workflow)
        self.assertIn(" and ", workflow)
        self.assertIn(" or ", workflow)
        self.assertNotIn("&&", workflow)
        self.assertNotIn("||", workflow)
        self.assertIn("NO_CHANGE", workflow)
        self.assertIn("googleapis.cloudbuild.v1.projects.builds.create", workflow)
        self.assertIn("release_build.metadata.build.status", workflow)
        self.assertIn("control/workflow-discovery-state.json", workflow)
        self.assertIn("activePointerWrites: 0", workflow)
        self.assertIn("state_error.code == 404", workflow)
        self.assertIn("CONTROL_STATE_UNAVAILABLE", workflow)
        self.assertNotIn("as: state_error\n          steps:\n            - no_previous_state", workflow)
        self.assertIn('--promotion-mode=\" + promotion_mode', workflow)
        self.assertIn("read_worker_summary", workflow)
        self.assertIn("WORKER_SUMMARY_IDENTITY_MISMATCH", workflow)
        self.assertIn("PROMOTION_MODE", cloudbuild)
        self.assertTrue((ROOT / "tools" / "argentina_release_promoter.py").is_file())
        self.assertTrue((ROOT / "tools" / "argentina_release_handoff_worker.py").is_file())

    def test_google_workflow_does_not_use_inline_string_interpolation(self) -> None:
        workflow = (ROOT / "workflows" / "argentina-release.yaml").read_text(encoding="utf-8")
        # Google Workflows does not support strings such as "hello-${world}".
        self.assertNotRegex(workflow, re.compile(r"--[a-z0-9-]+=\$\{"))
        self.assertNotRegex(workflow, re.compile(r"(?:BUILDER|VALUEPILOT)_[A-Z_]+=\$\{"))
        self.assertIn('${"--observed-at=" + args.observedAt}', workflow)
        self.assertIn('${"BUILDER_IMAGE=" + args.workerImage}', workflow)

    def test_google_workflow_connector_params_are_inside_args(self) -> None:
        workflow = (ROOT / "workflows" / "argentina-release.yaml").read_text(encoding="utf-8")
        self.assertIn(
            "              serviceAccount: ${args.builderServiceAccount}\n"
            "            connector_params:\n"
            "              timeout: 14400\n",
            workflow,
        )
        self.assertNotIn(
            "              serviceAccount: ${args.builderServiceAccount}\n"
            "          connector_params:\n",
            workflow,
        )

    def test_google_workflow_serializes_discovery_state_without_inline_map_expression(self) -> None:
        workflow = (ROOT / "workflows" / "argentina-release.yaml").read_text(encoding="utf-8")
        self.assertIn("- prepare_discovery_state:", workflow)
        self.assertIn("discovery_state_bytes: ${json.encode(discovery_state)}", workflow)
        self.assertIn("body: ${discovery_state_bytes}", workflow)
        self.assertNotIn("body: '${json.encode({", workflow)

    def test_google_workflow_handles_missing_discovery_state_key_safely(self) -> None:
        workflow = (ROOT / "workflows" / "argentina-release.yaml").read_text(encoding="utf-8")
        self.assertIn('map.get(previous_state, "metadataChecksum")', workflow)
        self.assertNotIn("previous_state.metadataChecksum", workflow)

    def test_google_workflow_handles_omitted_optional_arguments_safely(self) -> None:
        workflow = (ROOT / "workflows" / "argentina-release.yaml").read_text(encoding="utf-8")
        self.assertNotIn("default(args.", workflow)
        for name in (
            "executionId",
            "currentReleaseDate",
            "currentSourceSha256",
            "sourceArchiveKey",
            "sourceArchiveSha256",
            "upstreamChanged",
            "promotionMode",
            "promotionDataCanaryPath",
            "expectedCurrentReleaseId",
            "promotionStoreRoot",
            "promotionUsBucket",
            "promotionSaBucket",
            "promotionR2Bucket",
            "promotionGcsProject",
            "promotionR2Endpoint",
            "promotionR2Region",
            "promotionUsSmokeUrl",
            "promotionSaSmokeUrl",
            "promotionR2SmokeUrl",
        ):
            self.assertIn(f'map.get(args, "{name}")', workflow)
        self.assertNotIn("default(worker_summary.", workflow)
        self.assertIn('map.get(worker_summary, "activePointerWrites")', workflow)
        self.assertIn('map.get(worker_summary, "promotion")', workflow)

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
