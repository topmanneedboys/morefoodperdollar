from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "acquire-argentina-sepa-windscribe.yml"


class ArgentinaWindscribeAcquisitionWorkflowTests(unittest.TestCase):
    def test_workflow_is_oidc_only_weekday_scheduled_and_gated(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", text)
        for cron in ("30 16 * * 1-5", "30 18 * * 1-5", "30 20 * * 1-5"):
            self.assertIn(f"cron: '{cron}'", text)
        self.assertNotIn("30 16,18,20 * * *", text)
        self.assertIn("ARGENTINA_AUTOMATED_ACQUISITION_ENABLED", text)
        self.assertIn("id-token: write", text)
        self.assertGreaterEqual(text.count("google-github-actions/auth@v3"), 2)
        self.assertIn("workload_identity_provider", text)
        self.assertNotIn("credentials_json", text)
        self.assertNotIn("pull_request:", text)
        self.assertIn("cancel-in-progress: false", text)

    def test_metadata_preflight_deduplicates_before_opening_windscribe(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        preflight = text.index("Deduplicate today's acquisition before opening Argentina egress")
        retire = text.index("Retire Google credential material before VPN phase")
        acquire = text.index("Acquire and verify official SEPA through Windscribe Buenos Aires")
        self.assertLess(preflight, retire)
        self.assertLess(retire, acquire)
        self.assertIn("tools.argentina_acquisition_schedule_control", text)
        self.assertIn("steps.preflight.outputs.status == 'DOWNLOAD_REQUIRED'", text)
        self.assertIn("Reuse today's immutable acquisition evidence without VPN", text)
        self.assertIn("force_download", text)

    def test_wireguard_source_acquisition_is_full_tunnel_and_fail_closed(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("WINDSCRIBE_WIREGUARD_CONFIG_B64", text)
        self.assertIn("wireguard-tools", text)
        self.assertIn("0\\.0\\.0\\.0/0", text)
        self.assertIn("::/0", text)
        self.assertIn("has_v6_address", text)
        self.assertIn("has_v6_default", text)
        self.assertIn("IPv6 tunnel contract is incomplete", text)
        self.assertIn('if [[ "${has_v6_address}" == "0" ]]; then', text)
        self.assertIn("net.ipv6.conf.all.disable_ipv6=1", text)
        self.assertIn("latest-handshakes", text)
        self.assertIn("refusing direct-route fallback", text)
        self.assertIn("tools.argentina_release_acquisition_handoff", text)
        self.assertIn("datos.produccion.gob.ar", (ROOT / "tools" / "argentina_release_acquisition.py").read_text(encoding="utf-8"))

    def test_google_credentials_are_destroyed_for_vpn_phase_and_reissued_afterward(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        read_auth = text.index("Authenticate current-state and private-archive lookup with GitHub OIDC")
        retire = text.index("Retire Google credential material before VPN phase")
        acquire = text.index("Acquire and verify official SEPA through Windscribe Buenos Aires")
        reauth = text.index("Re-authenticate to Google Cloud after VPN teardown")
        publish = text.index("Publish exact verified source to private GCS archive")
        self.assertLess(read_auth, retire)
        self.assertLess(retire, acquire)
        self.assertLess(acquire, reauth)
        self.assertLess(reauth, publish)
        self.assertIn("shred -u", text)
        self.assertIn("GOOGLE_APPLICATION_CREDENTIALS=", text)
        self.assertIn("CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE=", text)

    def test_tunnel_is_down_before_durable_archive_publication(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        acquire = text.index("Acquire and verify official SEPA through Windscribe Buenos Aires")
        cleanup = text.index("cleanup_tunnel\n          tunnel_up=0", acquire)
        reauth = text.index("Re-authenticate to Google Cloud after VPN teardown")
        publish = text.index("Publish exact verified source to private GCS archive")
        self.assertLess(cleanup, reauth)
        self.assertLess(reauth, publish)
        self.assertIn("tools.publish_argentina_source_handoff", text)

    def test_heavy_build_is_reserved_once_and_google_workflow_is_async_shadow(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        reserve = text.index("Reserve at most one heavy build attempt per candidate per Argentina day")
        launch = text.index("Start one asynchronous Google SHADOW release workflow")
        self.assertLess(reserve, launch)
        self.assertIn("tools.argentina_acquisition_schedule_control reserve", text)
        self.assertIn("steps.reserve.outputs.status == 'RESERVED'", text)
        self.assertIn('"promotionMode": "SHADOW"', text)
        self.assertNotIn('"promotionMode": "AUTO_ACTIVATE"', text)
        self.assertNotIn('"promotionMode": "STAGE_CANARY"', text)
        self.assertIn("gcloud workflows execute", text)
        self.assertNotIn("gcloud workflows run", text)
        self.assertIn("The GitHub runner will not wait for the multi-hour Cloud Build", text)

    def test_runner_cleanup_removes_credentials_vpn_config_and_raw_source(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("Remove runner-local credential and raw-source material", text)
        self.assertIn("if: always()", text)
        self.assertIn('rm -rf "${LOCAL_ARCHIVE}"', text)
        self.assertIn('rm -f gha-creds-*.json', text)
        self.assertIn('"${WG_CONFIG}"', text)

    def test_supporting_tools_exist(self) -> None:
        self.assertTrue((ROOT / "tools" / "read_argentina_active_source_identity.py").is_file())
        self.assertTrue((ROOT / "tools" / "publish_argentina_source_handoff.py").is_file())
        self.assertTrue((ROOT / "tools" / "argentina_acquisition_schedule_control.py").is_file())


if __name__ == "__main__":
    unittest.main()
