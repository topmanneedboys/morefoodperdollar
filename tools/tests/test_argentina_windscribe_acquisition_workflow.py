from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "acquire-argentina-sepa-windscribe.yml"


class ArgentinaWindscribeAcquisitionWorkflowTests(unittest.TestCase):
    def test_workflow_is_oidc_only_and_schedule_is_gated(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", text)
        self.assertIn("cron: '30 16,18,20 * * *'", text)
        self.assertIn("ARGENTINA_AUTOMATED_ACQUISITION_ENABLED", text)
        self.assertIn("id-token: write", text)
        self.assertGreaterEqual(text.count("google-github-actions/auth@v3"), 2)
        self.assertIn("workload_identity_provider", text)
        self.assertNotIn("credentials_json", text)
        self.assertNotIn("pull_request:", text)

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
        read_auth = text.index("Authenticate read-only current-state lookup with GitHub OIDC")
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
        cleanup = text.index("Bring the Argentina route down before any durable cloud publication")
        publish = text.index("Publish exact verified source to private GCS archive")
        workflow = text.index("Execute existing Google release workflow in SHADOW")
        self.assertLess(cleanup, publish)
        self.assertLess(publish, workflow)
        self.assertIn("tools.publish_argentina_source_handoff", text)

    def test_promotion_is_pinned_to_shadow(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn('"promotionMode": "SHADOW"', text)
        self.assertIn("Assert no production activation", text)
        self.assertIn("activePointerWrites", text)
        self.assertNotIn('"promotionMode": "AUTO_ACTIVATE"', text)
        self.assertNotIn('"promotionMode": "STAGE_CANARY"', text)

    def test_supporting_tools_exist(self) -> None:
        self.assertTrue((ROOT / "tools" / "read_argentina_active_source_identity.py").is_file())
        self.assertTrue((ROOT / "tools" / "publish_argentina_source_handoff.py").is_file())


if __name__ == "__main__":
    unittest.main()
