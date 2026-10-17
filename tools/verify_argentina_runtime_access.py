"""Read-only verification using the real automation identity, without promotion."""
from __future__ import annotations
import argparse
import json
import re
import shutil
import subprocess
import urllib.request
from pathlib import Path
from tools.argentina_archived_release_promotion import PromotionError, run_production_smoke, smoke_tokens


def check_permissions(project, access_token, request):
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,61}[a-z0-9]", project):
        raise PromotionError("project is invalid")
    targets = [
        (f"https://run.googleapis.com/v2/projects/{project}/locations/us-east4/services/valuepilot-argentina-us-east4:testIamPermissions", ["run.services.get", "run.services.update"]),
        (f"https://run.googleapis.com/v2/projects/{project}/locations/southamerica-east1/services/valuepilot-argentina-sa-east1:testIamPermissions", ["run.services.get", "run.services.update"]),
        (f"https://cloudresourcemanager.googleapis.com/v3/projects/{project}:testIamPermissions", ["run.operations.get"]),
        (f"https://iam.googleapis.com/v1/projects/{project}/serviceAccounts/valuepilot-cloud-run-runtime@{project}.iam.gserviceaccount.com:testIamPermissions", ["iam.serviceAccounts.actAs"]),
    ]
    verified = []
    for url, required in targets:
        result = request(url, access_token, {"permissions": required})
        if not set(required).issubset(set(result.get("permissions", []))):
            raise PromotionError("automation permission check failed for " + url.split(":testIamPermissions")[0])
        verified.append({"resource": url.split(":testIamPermissions")[0], "permissions": required})
    return verified


def _request(url, token, payload):
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as response:
        raw = response.read(512 * 1024 + 1)
    if len(raw) > 512 * 1024:
        raise PromotionError("permission response exceeds bound")
    return json.loads(raw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--backend-url", action="append", required=True)
    parser.add_argument("--token-env", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    tokens = smoke_tokens(args.backend_url, args.token_env)
    cli = shutil.which("gcloud") or "gcloud"
    access = subprocess.run([cli, "auth", "print-access-token"], capture_output=True, text=True, check=True).stdout.strip()
    permissions = check_permissions(args.project, access, _request)
    smoke = {url: run_production_smoke(url, release_id=args.release_id, token=token) for url, token in tokens.items()}
    report = {"status": "PASS", "releaseId": args.release_id, "permissions": permissions,
              "smoke": smoke, "activePointerWrites": 0, "serviceUpdates": 0,
              "limitations": ["Runtime checks do not exercise object creation or active-pointer replacement.",
                              "This is private automation IAM verification, not a customer App Check/device test."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
