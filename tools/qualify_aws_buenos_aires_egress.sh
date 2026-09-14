#!/usr/bin/env bash
set -euo pipefail

# Disposable AWS Buenos Aires Local Zone probe for the official Argentina SEPA source.
#
# Intended to be run from AWS CloudShell in us-east-1. It creates no inbound
# access, launches one short-lived Amazon Linux instance in us-east-1-bue-1a,
# assigns an Elastic IP from the Buenos Aires network border group, runs the
# existing read-only ValuePilot geo-egress qualifier, retrieves the JSON result
# through the EC2 serial console, and tears the disposable resources down.
#
# The Local Zone opt-in itself is account-level and persists after this script;
# AWS currently requires Support to opt a Local Zone group back out.

REGION="us-east-1"
ZONE_GROUP="us-east-1-bue-1"
ZONE="us-east-1-bue-1a"
NETWORK_BORDER_GROUP="us-east-1-bue-1"
REPO_URL="https://github.com/topmanneedboys/morefoodperdollar.git"
REPO_BRANCH="candidate/argentina-geo-egress-qualification-v1"
TAG_PREFIX="valuepilot-bue-egress-probe"
MAX_WAIT_SECONDS=1200
POLL_SECONDS=10

INSTANCE_ID=""
ALLOCATION_ID=""
ASSOCIATION_ID=""
SUBNET_ID=""
SECURITY_GROUP_ID=""

cleanup() {
  set +e
  if [[ -n "${ASSOCIATION_ID}" ]]; then
    aws ec2 disassociate-address --region "${REGION}" --association-id "${ASSOCIATION_ID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${INSTANCE_ID}" ]]; then
    aws ec2 terminate-instances --region "${REGION}" --instance-ids "${INSTANCE_ID}" >/dev/null 2>&1 || true
    aws ec2 wait instance-terminated --region "${REGION}" --instance-ids "${INSTANCE_ID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${ALLOCATION_ID}" ]]; then
    aws ec2 release-address --region "${REGION}" --allocation-id "${ALLOCATION_ID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${SECURITY_GROUP_ID}" ]]; then
    aws ec2 delete-security-group --region "${REGION}" --group-id "${SECURITY_GROUP_ID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${SUBNET_ID}" ]]; then
    aws ec2 delete-subnet --region "${REGION}" --subnet-id "${SUBNET_ID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: required command '$1' is unavailable" >&2
    exit 2
  }
}
need aws
need python3
need git

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
if [[ -z "${ACCOUNT_ID}" || "${ACCOUNT_ID}" == "None" ]]; then
  echo "ERROR: AWS identity is unavailable" >&2
  exit 2
fi

echo "AWS account: ${ACCOUNT_ID}"
echo "Enabling Buenos Aires Local Zone group ${ZONE_GROUP} if needed..."
aws ec2 modify-availability-zone-group \
  --region "${REGION}" \
  --group-name "${ZONE_GROUP}" \
  --opt-in-status opted-in >/dev/null

for _ in $(seq 1 30); do
  OPT_IN_STATUS="$(aws ec2 describe-availability-zones \
    --region "${REGION}" \
    --zone-names "${ZONE}" \
    --all-availability-zones \
    --query 'AvailabilityZones[0].OptInStatus' \
    --output text 2>/dev/null || true)"
  if [[ "${OPT_IN_STATUS}" == "opted-in" ]]; then
    break
  fi
  sleep 2
done
if [[ "${OPT_IN_STATUS:-}" != "opted-in" ]]; then
  echo "ERROR: Buenos Aires Local Zone did not become opted-in" >&2
  exit 2
fi

VPC_ID="$(aws ec2 describe-vpcs \
  --region "${REGION}" \
  --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' \
  --output text)"
if [[ -z "${VPC_ID}" || "${VPC_ID}" == "None" ]]; then
  echo "ERROR: this AWS account has no default VPC in ${REGION}; no resources were created" >&2
  exit 2
fi

VPC_CIDR="$(aws ec2 describe-vpcs --region "${REGION}" --vpc-ids "${VPC_ID}" --query 'Vpcs[0].CidrBlock' --output text)"
EXISTING_SUBNETS="$(aws ec2 describe-subnets \
  --region "${REGION}" \
  --filters Name=vpc-id,Values="${VPC_ID}" \
  --query 'Subnets[].CidrBlock' \
  --output text)"

FREE_CIDR="$(VPC_CIDR="${VPC_CIDR}" EXISTING_SUBNETS="${EXISTING_SUBNETS}" python3 - <<'PY'
import ipaddress
import os

vpc = ipaddress.ip_network(os.environ['VPC_CIDR'])
used = [ipaddress.ip_network(x) for x in os.environ.get('EXISTING_SUBNETS', '').split() if x]
for candidate in vpc.subnets(new_prefix=max(vpc.prefixlen, 28)):
    if all(not candidate.overlaps(existing) for existing in used):
        print(candidate)
        break
else:
    raise SystemExit('no free /28 subnet remains in the default VPC')
PY
)"

echo "Creating disposable subnet ${FREE_CIDR} in ${ZONE}..."
SUBNET_ID="$(aws ec2 create-subnet \
  --region "${REGION}" \
  --vpc-id "${VPC_ID}" \
  --availability-zone "${ZONE}" \
  --cidr-block "${FREE_CIDR}" \
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=${TAG_PREFIX}}]" \
  --query 'Subnet.SubnetId' \
  --output text)"
aws ec2 wait subnet-available --region "${REGION}" --subnet-ids "${SUBNET_ID}"

MAIN_ROUTE_TABLE_ID="$(aws ec2 describe-route-tables \
  --region "${REGION}" \
  --filters Name=vpc-id,Values="${VPC_ID}" Name=association.main,Values=true \
  --query 'RouteTables[0].RouteTableId' \
  --output text)"
INTERNET_GATEWAY_ROUTE="$(aws ec2 describe-route-tables \
  --region "${REGION}" \
  --route-table-ids "${MAIN_ROUTE_TABLE_ID}" \
  --query "RouteTables[0].Routes[?DestinationCidrBlock=='0.0.0.0/0'].GatewayId | [0]" \
  --output text)"
if [[ -z "${INTERNET_GATEWAY_ROUTE}" || "${INTERNET_GATEWAY_ROUTE}" == "None" || "${INTERNET_GATEWAY_ROUTE}" != igw-* ]]; then
  echo "ERROR: default VPC main route table has no IPv4 internet-gateway route" >&2
  exit 2
fi

SECURITY_GROUP_ID="$(aws ec2 create-security-group \
  --region "${REGION}" \
  --vpc-id "${VPC_ID}" \
  --group-name "${TAG_PREFIX}-$(date +%s)" \
  --description 'ValuePilot disposable Buenos Aires egress qualification; no inbound rules' \
  --query GroupId \
  --output text)"
aws ec2 create-tags --region "${REGION}" --resources "${SECURITY_GROUP_ID}" --tags Key=Name,Value="${TAG_PREFIX}" >/dev/null

OFFERINGS="$(aws ec2 describe-instance-type-offerings \
  --region "${REGION}" \
  --location-type availability-zone \
  --filters Name=location,Values="${ZONE}" \
  --query 'InstanceTypeOfferings[].InstanceType' \
  --output text)"
INSTANCE_TYPE=""
for candidate in t3.micro t3.small t3.medium t3a.micro t3a.small t3a.medium; do
  if grep -qw "${candidate}" <<<"${OFFERINGS}"; then
    INSTANCE_TYPE="${candidate}"
    break
  fi
done
if [[ -z "${INSTANCE_TYPE}" ]]; then
  echo "ERROR: none of the bounded ValuePilot probe instance types is currently offered in ${ZONE}" >&2
  echo "Available offerings: ${OFFERINGS}" >&2
  exit 2
fi

echo "Using instance type ${INSTANCE_TYPE}."
AMI_ID="$(aws ssm get-parameter \
  --region "${REGION}" \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameter.Value' \
  --output text)"

REPO_SHA="$(git ls-remote "${REPO_URL}" "refs/heads/${REPO_BRANCH}" | awk '{print $1}')"
if [[ ! "${REPO_SHA}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "ERROR: could not pin the current ValuePilot candidate branch" >&2
  exit 2
fi

echo "Pinned ValuePilot candidate: ${REPO_SHA}"

USER_DATA="$(cat <<EOF
#!/bin/bash
set -u
exec > >(tee -a /var/log/valuepilot-bue-probe.log) 2>&1

emit() {
  printf '%s\\n' "\$1" >/dev/ttyS0 2>/dev/null || true
}

# EIP association is intentionally performed by the outer CloudShell script
# after instance creation. Wait for that bounded network path before installing.
network_ok=0
for attempt in \$(seq 1 60); do
  if curl -fsS --max-time 5 https://github.com/ >/dev/null 2>&1; then
    network_ok=1
    break
  fi
  sleep 5
done
if [[ "\${network_ok}" != "1" ]]; then
  emit 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=NETWORK_UNAVAILABLE'
  exit 2
fi

if ! dnf install -y git python3-pip >/dev/null 2>&1; then
  emit 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=PACKAGE_INSTALL'
  exit 2
fi
rm -rf /tmp/valuepilot
if ! git clone --quiet --branch '${REPO_BRANCH}' --single-branch '${REPO_URL}' /tmp/valuepilot; then
  emit 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=GIT_CLONE'
  exit 2
fi
cd /tmp/valuepilot || exit 2
if ! git checkout --quiet '${REPO_SHA}'; then
  emit 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=PINNED_CHECKOUT'
  exit 2
fi
if ! python3 -m pip install --user --disable-pip-version-check -r backend/requirements.txt >/dev/null 2>&1; then
  emit 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=PYTHON_DEPENDENCIES'
  exit 2
fi

export PYTHONPATH=/tmp/valuepilot
observed=\$(date -u +%Y-%m-%dT%H:%M:%SZ)
set +e
python3 -m tools.qualify_argentina_geo_egress run \
  --observed-at "\${observed}" \
  --egress-label aws-buenos-aires-local-zone \
  --output /tmp/valuepilot-aws-bue-result.json >/tmp/valuepilot-qualifier.stdout 2>/tmp/valuepilot-qualifier.stderr
rc=\$?
set -e

if [[ -s /tmp/valuepilot-aws-bue-result.json ]]; then
  result=\$(cat /tmp/valuepilot-aws-bue-result.json)
  emit "VALUEPILOT_AWS_BUE_RESULT=\${result}"
  emit "VALUEPILOT_AWS_BUE_EXIT=\${rc}"
else
  emit 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=QUALIFIER_NO_RESULT'
  sed -n '1,20p' /tmp/valuepilot-qualifier.stderr >/dev/ttyS0 2>/dev/null || true
fi
EOF
)"

ALLOCATION_OUTPUT="$(aws ec2 allocate-address \
  --region "${REGION}" \
  --domain vpc \
  --network-border-group "${NETWORK_BORDER_GROUP}" \
  --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=${TAG_PREFIX}}]")"
ALLOCATION_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["AllocationId"])' <<<"${ALLOCATION_OUTPUT}")"
PUBLIC_IP="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["PublicIp"])' <<<"${ALLOCATION_OUTPUT}")"

echo "Allocated Buenos Aires network-border-group public IPv4: ${PUBLIC_IP}"

RUN_OUTPUT="$(aws ec2 run-instances \
  --region "${REGION}" \
  --image-id "${AMI_ID}" \
  --instance-type "${INSTANCE_TYPE}" \
  --subnet-id "${SUBNET_ID}" \
  --security-group-ids "${SECURITY_GROUP_ID}" \
  --metadata-options HttpTokens=required,HttpEndpoint=enabled \
  --user-data "${USER_DATA}" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${TAG_PREFIX}}]")"
INSTANCE_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["Instances"][0]["InstanceId"])' <<<"${RUN_OUTPUT}")"

echo "Instance ${INSTANCE_ID} launched; attaching Buenos Aires EIP..."
ASSOCIATION_ID="$(aws ec2 associate-address \
  --region "${REGION}" \
  --instance-id "${INSTANCE_ID}" \
  --allocation-id "${ALLOCATION_ID}" \
  --query AssociationId \
  --output text)"

aws ec2 wait instance-running --region "${REGION}" --instance-ids "${INSTANCE_ID}"

START_EPOCH="$(date +%s)"
FOUND="0"
RESULT_LINE=""
BOOTSTRAP_FAILURE=""
while (( $(date +%s) - START_EPOCH < MAX_WAIT_SECONDS )); do
  CONSOLE="$(aws ec2 get-console-output \
    --region "${REGION}" \
    --instance-id "${INSTANCE_ID}" \
    --latest \
    --query Output \
    --output text 2>/dev/null || true)"
  RESULT_LINE="$(grep -F 'VALUEPILOT_AWS_BUE_RESULT=' <<<"${CONSOLE}" | tail -n 1 || true)"
  BOOTSTRAP_FAILURE="$(grep -F 'VALUEPILOT_AWS_BUE_BOOTSTRAP_FAILED=' <<<"${CONSOLE}" | tail -n 1 || true)"
  if [[ -n "${RESULT_LINE}" || -n "${BOOTSTRAP_FAILURE}" ]]; then
    FOUND="1"
    break
  fi
  sleep "${POLL_SECONDS}"
done

if [[ "${FOUND}" != "1" ]]; then
  echo "ERROR: probe timed out after ${MAX_WAIT_SECONDS}s; resources will be cleaned up" >&2
  exit 2
fi

if [[ -n "${BOOTSTRAP_FAILURE}" ]]; then
  echo "${BOOTSTRAP_FAILURE}"
  echo "Probe bootstrap failed; resources will be cleaned up." >&2
  exit 2
fi

RESULT_JSON="${RESULT_LINE#VALUEPILOT_AWS_BUE_RESULT=}"
echo
echo "========== VALUEPILOT AWS BUENOS AIRES RESULT =========="
printf '%s\n' "${RESULT_JSON}" | python3 -m json.tool

echo
STATUS="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))' <<<"${RESULT_JSON}")"
ERROR_CODE="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("error", ""))' <<<"${RESULT_JSON}")"
if [[ "${STATUS}" == "PASS" ]]; then
  echo "AWS_BUENOS_AIRES_EGRESS=PASS"
  echo "The exact ValuePilot official-source path succeeded from AWS Buenos Aires."
  exit 0
fi
if [[ "${ERROR_CODE}" == "HTTP_ERROR" ]] && grep -q '403' <<<"${RESULT_JSON}"; then
  echo "AWS_BUENOS_AIRES_EGRESS=HTTP_403"
  echo "SEPA rejected the AWS Buenos Aires data-center route; use the VPN/headless fallback instead."
  exit 3
fi

echo "AWS_BUENOS_AIRES_EGRESS=NOT_PROVEN"
exit 2
