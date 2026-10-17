from __future__ import annotations
import json
import os
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.argentina_archived_release_promotion import PromotionError, smoke_tokens
from tools.provision_argentina_release_permissions import apply_plan
from tools.verify_argentina_runtime_access import check_permissions

class PromotionAccessTests(unittest.TestCase):
    def test_target_tokens_stay_bound_to_each_service(self):
        urls = ["https://backend-us.a.run.app", "https://backend-sa.a.run.app"]
        with patch.dict(os.environ, {"VALUEPILOT_TOKEN_US": "us-token", "VALUEPILOT_TOKEN_SA": "sa-token"}):
            self.assertEqual(smoke_tokens(urls, ["VALUEPILOT_TOKEN_US", "VALUEPILOT_TOKEN_SA"]), dict(zip(urls, ["us-token", "sa-token"])))
            with self.assertRaises(PromotionError): smoke_tokens(urls, ["VALUEPILOT_TOKEN_US"])
            with self.assertRaises(PromotionError): smoke_tokens(["https://untrusted.example"], ["VALUEPILOT_TOKEN_US"])
            with self.assertRaises(PromotionError): smoke_tokens(urls, ["VALUEPILOT_MISSING", "VALUEPILOT_TOKEN_SA"])

    def test_permission_plan_limits_mutation_targets(self):
        plan = json.loads((Path(__file__).resolve().parents[2] / "docs/catalog-discovery-plan/AUTOMATION_PERMISSION_PLAN.json").read_text(encoding="utf-8"))
        calls = []
        def run(command, optional=False):
            calls.append(command)
            return None if optional else "{}"
        apply_plan(plan, run)
        pointers = [command for command in calls if "roles/storage.objectUser" in command]
        self.assertEqual(len(pointers), 2)
        for command in pointers:
            condition = command[command.index("--condition") + 1]
            self.assertIn("resource.name ==", condition)
            self.assertIn("control/active.json", condition)
        services = [command for command in calls if command[:3] == ["run", "services", "add-iam-policy-binding"]]
        self.assertEqual({command[3] for command in services}, {"valuepilot-argentina-us-east4", "valuepilot-argentina-sa-east1"})
        self.assertFalse(any("allUsers" in command or "roles/owner" in command or "roles/editor" in command for command in calls))
        mutation_permissions = {permission for role in plan["customRoles"] for permission in role["permissions"] if permission.endswith((".update", ".create", ".delete"))}
        self.assertEqual(mutation_permissions, {"run.services.update"})

    def test_live_permission_probe_is_read_only_and_fails_when_update_is_missing(self):
        calls = []
        def request(url, token, payload):
            calls.append((url, token, payload))
            return payload
        result = check_permissions("valuepilot-argentina", "not-a-real-token", request)
        self.assertEqual(len(result), 4)
        self.assertTrue(all(url.endswith(":testIamPermissions") for url, _, _ in calls))
        with self.assertRaises(PromotionError):
            check_permissions("valuepilot-argentina", "not-a-real-token", lambda *args: {"permissions": []})

    def test_workflow_uses_target_audiences_without_cli_token_arguments(self):
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/acquire-argentina-sepa-windscribe.yml").read_text(encoding="utf-8")
        self.assertIn("id_token_audience: ${{ env.US_BACKEND_URL }}", workflow)
        self.assertIn("id_token_audience: ${{ env.SA_BACKEND_URL }}", workflow)
        self.assertIn("--smoke-token-env VALUEPILOT_SMOKE_ID_TOKEN_US", workflow)
        self.assertIn("--smoke-token-env VALUEPILOT_SMOKE_ID_TOKEN_SA", workflow)
        self.assertNotIn("gcloud auth print-identity-token", workflow)
        self.assertIn("inputs.verify_promotion_access != true", workflow)

if __name__ == "__main__": unittest.main()
