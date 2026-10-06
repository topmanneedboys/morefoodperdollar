from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from backend.service import (
    ANDROID_NETWORKING_AUTHORIZED_ENV,
    ANDROID_NETWORKING_RELEASE_DATE_ENV,
    ANDROID_NETWORKING_RELEASE_ID_ENV,
    ANDROID_NETWORKING_SCOPE_ENV,
    PRODUCTION_ANDROID_UI_AUTHORIZED_ENV,
    PRODUCTION_ANDROID_UI_SCOPE,
    PRODUCTION_ANDROID_UI_SCOPE_ENV,
    BackendService,
)


class FakeReleaseManager:
    def __init__(self, **overrides):
        self.value = {
            "service": "ready",
            "releaseId": "sepa-2026-09-06",
            "releaseDate": "2026-09-06",
            "freshness": "FRESH",
            "qualifiedOfferEvidenceAvailable": False,
            **overrides,
        }

    def status(self):
        return dict(self.value)


class AndroidNetworkingAuthorizationTests(unittest.TestCase):
    def service(self, manager=None):
        return BackendService("unused", release_manager=manager or FakeReleaseManager())

    def test_missing_configuration_is_false(self):
        with patch.dict(os.environ, {}, clear=True):
            value = self.service().status()
        self.assertFalse(value["androidNetworkingAuthorized"])
        self.assertFalse(value["productionAndroidUiAuthorized"])

    def test_exact_deployment_configuration_authorizes_identity_search_only(self):
        environment = {
            ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
            ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
            ANDROID_NETWORKING_RELEASE_ID_ENV: "sepa-2026-09-06",
            ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-06",
        }
        with patch.dict(os.environ, environment, clear=True):
            value = self.service().status()
        self.assertTrue(value["androidNetworkingAuthorized"])
        self.assertFalse(value["productionAndroidUiAuthorized"])

    def test_qualified_offer_ui_requires_a_separate_release_scoped_gate(self):
        environment = {
            ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
            ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
            ANDROID_NETWORKING_RELEASE_ID_ENV: "sepa-2026-09-06",
            ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-06",
            PRODUCTION_ANDROID_UI_AUTHORIZED_ENV: "true",
            PRODUCTION_ANDROID_UI_SCOPE_ENV: PRODUCTION_ANDROID_UI_SCOPE,
        }
        with patch.dict(os.environ, environment, clear=True):
            value = self.service(FakeReleaseManager(qualifiedOfferEvidenceAvailable=True)).status()
        self.assertTrue(value["androidNetworkingAuthorized"])
        self.assertTrue(value["productionAndroidUiAuthorized"])

    def test_qualified_offer_ui_gate_fails_closed_on_scope_or_release_mismatch(self):
        base = {
            ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
            ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
            ANDROID_NETWORKING_RELEASE_ID_ENV: "sepa-2026-09-06",
            ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-06",
            PRODUCTION_ANDROID_UI_AUTHORIZED_ENV: "true",
            PRODUCTION_ANDROID_UI_SCOPE_ENV: PRODUCTION_ANDROID_UI_SCOPE,
        }
        for overrides in (
            {PRODUCTION_ANDROID_UI_SCOPE_ENV: "identity-search"},
            {ANDROID_NETWORKING_RELEASE_ID_ENV: "other-release"},
            {PRODUCTION_ANDROID_UI_AUTHORIZED_ENV: "false"},
        ):
            environment = {**base, **overrides}
            with self.subTest(overrides=overrides), patch.dict(os.environ, environment, clear=True):
                value = self.service().status()
                self.assertFalse(value["productionAndroidUiAuthorized"])

    def test_malformed_or_mismatched_configuration_fails_closed(self):
        cases = [
            {ANDROID_NETWORKING_AUTHORIZED_ENV: "yes"},
            {
                ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
                ANDROID_NETWORKING_SCOPE_ENV: "shop",
                ANDROID_NETWORKING_RELEASE_ID_ENV: "sepa-2026-09-06",
                ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-06",
            },
            {
                ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
                ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
                ANDROID_NETWORKING_RELEASE_ID_ENV: "wrong-release",
                ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-06",
            },
            {
                ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
                ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
                ANDROID_NETWORKING_RELEASE_ID_ENV: "sepa-2026-09-06",
                ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-07",
            },
        ]
        for environment in cases:
            with self.subTest(environment=environment), patch.dict(os.environ, environment, clear=True):
                self.assertFalse(self.service().status()["androidNetworkingAuthorized"])

    def test_unready_or_unqualified_release_cannot_authorize(self):
        environment = {
            ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
            ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
            ANDROID_NETWORKING_RELEASE_ID_ENV: "sepa-2026-09-06",
            ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-09-06",
        }
        for overrides in (
            {"service": "not_ready"},
            {"freshness": "STALE"},
        ):
            manager = FakeReleaseManager(**overrides)
            with self.subTest(overrides=overrides), patch.dict(os.environ, environment, clear=True):
                self.assertFalse(self.service(manager).status()["androidNetworkingAuthorized"])


if __name__ == "__main__":
    unittest.main()
