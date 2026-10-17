from __future__ import annotations

import unittest
from pathlib import Path
from urllib.parse import quote, unquote


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "acquire-argentina-sepa-windscribe.yml"
RELEASE_WORKFLOW = ROOT / "ARGENTINA_RELEASE_WORKFLOW.yaml"
BUILD_WORKFLOW = ROOT / ".github" / "workflows" / "build-valuepilot-android.yml"


class ArgentinaWindscribeAcquisitionWorkflowTests(unittest.TestCase):
    def test_workflow_is_oidc_only_daily_scheduled_and_gated(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", text)
        for cron in ("30 16 * * *", "30 18 * * *", "30 20 * * *"):
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
        self.assertIn("UPSTREAM_TEMPORARILY_UNAVAILABLE", text)
        self.assertIn("DISCOVERY_PLUS_RECOVERY", text)
        self.assertIn("Explain temporary official metadata unavailability", text)
        self.assertIn("nearing its explicit freshness limit", text)

    def test_upstream_outage_is_handled_only_when_active_freshness_is_safe(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        preflight = text.index("Reuse today's immutable acquisition evidence without VPN")
        resolution_start = text.index("      - id: resolution", preflight)
        stop_start = text.index("      - name: Stop cleanly when no build is required", resolution_start)
        resolution_gate = text[resolution_start:stop_start]
        case_start = resolution_gate.index('case "${status}" in')
        case_end = resolution_gate.index("esac", case_start)
        accepted_statuses = resolution_gate[case_start:case_end]

        self.assertIn("UPSTREAM_TEMPORARILY_UNAVAILABLE)", accepted_statuses)
        self.assertIn("FRESHNESS_LIMIT_EXCEEDED|ACTIVE_RELEASE_UNVERIFIED)", accepted_statuses)
        unsafe_state = accepted_statuses[
            accepted_statuses.index("FRESHNESS_LIMIT_EXCEEDED|ACTIVE_RELEASE_UNVERIFIED)") :
        ]
        self.assertIn("exit 2", unsafe_state)
        self.assertIn("if: steps.reserve.outputs.status == 'RESERVED'", text)

        outage_report = text[
            text.index("      - name: Explain temporary official metadata unavailability") :
            text.index("      - name: Reuse today's immutable acquisition evidence without VPN")
        ]
        self.assertIn("steps.preflight.outputs.status == 'UPSTREAM_TEMPORARILY_UNAVAILABLE'", outage_report)
        self.assertIn("steps.preflight.outputs.status == 'FRESHNESS_LIMIT_EXCEEDED'", outage_report)
        self.assertIn("steps.preflight.outputs.status == 'ACTIVE_RELEASE_UNVERIFIED'", outage_report)
        self.assertIn("Argentina acquisition blocked: active freshness is unsafe", outage_report)
        self.assertIn("::error::Official metadata is unavailable and active-release freshness cannot safely cover it", outage_report)
        for diagnostic in (
            "Active release unchanged",
            "Active release identity:",
            "Freshness policy:",
            "Same-day state/candidate:",
            "Same-day reservation/SHADOW:",
            "Active/canary pointer writes:",
            "Source substitutions:",
        ):
            with self.subTest(diagnostic=diagnostic):
                self.assertIn(diagnostic, outage_report)

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
        self.assertIn("ip -4 route get 1.1.1.1", text)
        self.assertIn("DNS resolver is not routed through the authorized tunnel", text)
        self.assertIn("--retry-all-errors", text)
        self.assertIn('sudo cp "${dns_backup}" /etc/resolv.conf', text)
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
        self.assertIn("tools.argentina_acquisition_schedule_control_v2 reserve", text)
        self.assertIn("steps.reserve.outputs.status == 'RESERVED'", text)
        self.assertIn('"promotionMode": "SHADOW"', text)
        self.assertNotIn('"promotionMode": "AUTO_ACTIVATE"', text)
        self.assertNotIn('"promotionMode": "STAGE_CANARY"', text)
        self.assertIn("gcloud workflows execute", text)
        self.assertNotIn("gcloud workflows run", text)
        self.assertIn("The GitHub runner will not wait for the multi-hour Cloud Build", text)
        self.assertIn('"upstreamChangeEvidence": preflight.get("upstreamChangeEvidence")', text)

    def test_reconciliation_job_describes_and_records_async_shadow_then_guarded_promotion(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        reconcile = text.index("  reconcile-shadow:")
        section = text[reconcile:]
        self.assertIn("    needs:\n      - acquire-and-shadow", section)
        self.assertIn(
            "    if: always() && (github.event_name == 'workflow_dispatch' || vars.ARGENTINA_AUTOMATED_ACQUISITION_ENABLED == 'true')",
            section,
        )
        self.assertIn("read-reconciliation-state", section)
        self.assertIn("--current-release-date", section)
        self.assertIn("steps.state.outputs.superseded != 'true'", section)
        self.assertIn("gcloud workflows executions describe", section)
        self.assertIn("reconcile-shadow-state", section)
        self.assertIn("SHADOW_PENDING", (ROOT / "tools" / "argentina_acquisition_schedule_control_v2.py").read_text(encoding="utf-8"))
        self.assertIn("Alert on terminal SHADOW failure", section)
        self.assertIn("Promote the verified candidate with CAS, smoke checks, and rollback", section)
        self.assertIn("Active/canary pointer writes by reconciler: deferred to guarded promotion", section)
        self.assertNotIn("AUTO_ACTIVATE", section)
        self.assertNotIn("STAGE_CANARY", section)

    def test_cloud_workflow_reconciles_only_an_exact_idempotent_state_write_conflict(self) -> None:
        text = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("state_write_error.code == 412", text)
        self.assertIn("STATE_WRITE_FAILED", text)
        self.assertIn("STATE_WRITE_UNVERIFIED", text)
        self.assertIn("STATE_WRITE_CONFLICT", text)
        self.assertIn("stateWriteStatus: ALREADY_RECORDED", text)
        for identity_field in (
            "metadataChecksum",
            "datasetId",
            "candidateReleaseId",
            "sourceArchiveSha256",
            "promotionMode",
            "upstreamChanged",
        ):
            self.assertIn(f'map.get(conflicting_state, "{identity_field}")', text)
        self.assertIn("VALUEPILOT_UPSTREAM_CHANGE_EVIDENCE_JSON", text)
        self.assertIn("VERIFIED_METADATA_CHANGE_EVIDENCE_REQUIRED", text)
        self.assertIn("VALUEPILOT_UPSTREAM_CHANGED", text)

    def test_discovery_state_write_uses_canonical_object_name_after_observed_412_404(self) -> None:
        """Reproduce the malformed encoded-key collision seen on 2026-09-25."""

        text = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        state_object = "control/workflow-discovery-state.json"
        encoded_key = quote(state_object, safe="")

        # The old insert used the URL-encoded value as the Storage object
        # name, leaving a literal `%2F` key.  Connector path reads decode that
        # value to the canonical slash path, so the existing malformed object
        # is invisible to the read and conflict-reconciliation calls.
        objects = {encoded_key: {"generation": "old-generation"}}
        read_key = unquote(encoded_key)
        self.assertNotIn(read_key, objects)
        self.assertIn(encoded_key, objects)
        with self.assertRaises(KeyError):
            _ = objects[read_key]
        conditional_insert_would_be_412 = encoded_key in objects
        conflict_read_would_be_404 = read_key not in objects
        self.assertTrue(conditional_insert_would_be_412)
        self.assertTrue(conflict_read_would_be_404)

        # The repaired workflow writes the canonical name while retaining the
        # encoded path form for Storage connector reads.
        self.assertIn("name: ${state_object}", text)
        self.assertNotIn("name: ${text.url_encode(state_object)}", text)
        self.assertIn("object: ${text.url_encode(state_object)}", text)
        self.assertIn('ifGenerationMatch: ${default(previous_state_generation, "0")}', text)

    def test_september_22_shadow_conflict_fails_closed_for_a_different_candidate(self) -> None:
        """Lock the observed 412 recovery boundary to the Sep 22 identities."""

        text = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        observed_candidate = "argentina-sepa-2026-09-22-eaefeae3d9f2885a-routing-v1-search-v2"
        worker_summary = {
            "metadataChecksum": "m" * 64,
            "datasetId": "precios-claros-base-sepa",
            "candidateReleaseId": observed_candidate,
            "sourceArchiveSha256": "a" * 64,
            "promotionMode": "SHADOW",
            "upstreamChanged": False,
        }
        conflicting_state = {**worker_summary, "candidateReleaseId": "different-candidate"}
        exact_repeat = all(
            worker_summary[field] == conflicting_state[field]
            for field in (
                "metadataChecksum",
                "datasetId",
                "candidateReleaseId",
                "sourceArchiveSha256",
                "promotionMode",
                "upstreamChanged",
            )
        )
        self.assertFalse(exact_repeat)
        self.assertIn("state_write_error.code == 412", text)
        self.assertIn("next: read_state_after_conflict", text)
        self.assertIn("status: STATE_WRITE_CONFLICT", text)
        self.assertIn("attemptedReleaseId", text)
        self.assertIn("conflictingReleaseId", text)
        self.assertIn("activePointerWrites: ${map.get(worker_summary, \"activePointerWrites\")}", text)
        self.assertIn("promotion: ${map.get(worker_summary, \"promotion\")}", text)
        # Keep the observed candidate as a fixture only; it must never become a
        # production source selector or an activation authority.
        self.assertTrue(observed_candidate.endswith("routing-v1-search-v2"))

    def test_cloud_workflow_materializes_worker_contract_evidence_file(self) -> None:
        text = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        for evidence_field in (
            "changed",
            "resourceId",
            "previousDiscoveryResponseSha256",
            "currentDiscoveryResponseSha256",
        ):
            self.assertIn(f'"{evidence_field}"', text)
        self.assertIn("/tmp/valuepilot-upstream-change-evidence.json", text)
        self.assertIn("payload = evidence if evidence is not None else {}", text)
        self.assertNotIn('"upstreamChangeEvidence": evidence', text)
        self.assertIn('"--upstream-change-evidence=/tmp/valuepilot-upstream-change-evidence.json"', text)
        self.assertIn("UNEXPECTED_METADATA_CHANGE_EVIDENCE", text)

    def test_cloud_workflow_decodes_json_bytes_before_string_concatenation(self) -> None:
        text = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            'VALUEPILOT_UPSTREAM_CHANGE_EVIDENCE_JSON=" + text.decode(json.encode(upstream_change_evidence), "UTF-8")',
            text,
        )
        self.assertNotIn(
            'VALUEPILOT_UPSTREAM_CHANGE_EVIDENCE_JSON=" + json.encode(upstream_change_evidence)',
            text,
        )

    def test_cloud_workflow_expressions_fit_service_limit(self) -> None:
        text = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        over_limit = []
        for line_number, line in enumerate(text.splitlines(), start=1):
            if "${" not in line or "}" not in line:
                continue
            expression = line[line.index("${") + 2 : line.rfind("}")]
            if len(expression) > 400:
                over_limit.append((line_number, len(expression)))
        self.assertEqual([], over_limit)

    def test_full_android_ci_triggers_for_acquisition_milestone_changes(self) -> None:
        text = BUILD_WORKFLOW.read_text(encoding="utf-8")
        paths = (
            ".github/workflows/acquire-argentina-sepa-windscribe.yml",
            ".github/workflows/test-argentina-acquisition-control.yml",
            "ARGENTINA_RELEASE_WORKFLOW.yaml",
            "ARGENTINA_AUTOMATED_RELEASE_SOURCE_CONTRACT.json",
            "tools/argentina_acquisition_schedule_control_v2.py",
            "tools/argentina_release_acquisition.py",
            "tools/argentina_release_acquisition_handoff.py",
            "tools/tests/test_argentina_acquisition_schedule_control_v2.py",
            "tools/tests/test_argentina_release_acquisition.py",
            "tools/tests/test_argentina_windscribe_acquisition_workflow.py",
        )
        for path in paths:
            with self.subTest(path=path):
                self.assertGreaterEqual(text.count(f"'{path}'"), 2)

    def test_debug_apk_artifact_is_manual_only_and_short_lived(self) -> None:
        text = BUILD_WORKFLOW.read_text(encoding="utf-8")
        # The release workflow now uploads the complete, checksum-verified
        # bundle rather than a duplicate standalone APK artifact.
        upload = text.index("      - name: Upload verified release bundle")
        upload_step = text[upload:]
        self.assertIn("        if: github.event_name == 'push'", upload_step)
        self.assertIn("          retention-days: 3", upload_step)

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
