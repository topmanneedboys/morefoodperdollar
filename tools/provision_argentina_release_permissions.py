"""Operator-only installation of narrowly scoped national promotion permissions.

Default prints the exact plan. --apply installs it using the operator's current
Google CLI identity. It never obtains or prints credentials, changes active data,
or makes services public. Existing unrelated IAM bindings are preserved.
"""
from __future__ import annotations
import argparse
import json
import shutil
import subprocess
from pathlib import Path


def apply_plan(plan, run):
    project, member = plan["project"], plan["member"]
    for role in plan["customRoles"]:
        result = run(["iam", "roles", "describe", role["id"], "--project", project, "--format=json"], optional=True)
        if result is None:
            run(["iam", "roles", "create", role["id"], "--project", project, "--title", role["id"],
                 "--permissions", ",".join(role["permissions"]), "--stage", "GA"])
        elif set(json.loads(result).get("includedPermissions", [])) != set(role["permissions"]):
            raise RuntimeError("Existing custom role differs from the reviewed plan: " + role["id"])
    for target in plan["regionalBuckets"]:
        bucket = "gs://" + target["bucket"]
        for role, condition in (("roles/storage.objectViewer", None),
                                ("roles/storage.objectCreator", (target["immutableWriteCondition"], "valuepilot-immutable-publication")),
                                ("roles/storage.objectUser", (target["activePointerCondition"], "valuepilot-active-pointer-only"))):
            command = ["storage", "buckets", "add-iam-policy-binding", bucket, "--member", member, "--role", role]
            command += ["--condition", "expression=" + condition[0] + ",title=" + condition[1]] if condition else ["--condition=None"]
            run(command)
    for service in plan["services"]:
        for role in ("projects/" + project + "/roles/valuepilotReleaseServiceUpdater", "roles/run.invoker"):
            run(["run", "services", "add-iam-policy-binding", service["name"], "--region", service["region"],
                 "--project", project, "--member", member, "--role", role])
    run(["projects", "add-iam-policy-binding", project, "--member", member,
         "--role", "projects/" + project + "/roles/valuepilotReleaseOperationReader", "--condition=None"])
    run(["iam", "service-accounts", "add-iam-policy-binding", plan["runtimeServiceAccount"], "--project", project,
         "--member", member, "--role", "roles/iam.serviceAccountUser"])
    expression = "resource.name.startsWith('projects/_/buckets/" + plan["receiptBucket"] + "/objects/" + plan["receiptPrefix"] + "')"
    for role in ("roles/storage.objectViewer", "roles/storage.objectCreator"):
        run(["storage", "buckets", "add-iam-policy-binding", "gs://" + plan["receiptBucket"], "--member", member,
             "--role", role, "--condition", "expression=" + expression + ",title=valuepilot-promotion-receipts-only"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, default=Path(__file__).resolve().parents[1] / "docs/catalog-discovery-plan/AUTOMATION_PERMISSION_PLAN.json")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    if not args.apply:
        print(json.dumps(plan, indent=2))
        return 0
    cli = shutil.which("gcloud")
    if not cli:
        raise RuntimeError("Google Cloud CLI is unavailable")
    def run(command, optional=False):
        result = subprocess.run([cli, *command, "--quiet"], capture_output=True, text=True, encoding="utf-8")
        if result.returncode:
            if optional and ("NOT_FOUND" in result.stderr or "does not exist" in result.stderr):
                return None
            raise RuntimeError("Permission operation failed: " + command[0] + " " + command[1] + ": " + result.stderr[:800])
        print("Verified permission operation: " + " ".join(command[:3]), flush=True)
        return result.stdout
    apply_plan(plan, run)
    print("Promotion permission plan installed; automatic promotion still requires runtime verification.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
