#!/usr/bin/env bash
set -euo pipefail
echo "Vai deployar, Lets gooo!!!"


ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_PATH="${ROOT_DIR}/hackaton-projeto-5"
PACKAGED_TEMPLATE="${ROOT_DIR}/template.packaged.yaml"

MODE="terraform"
DESTROY="false"
STACK_NAME="vehicle-platform"
REGION="us-east-1"
ENDPOINT="http://localhost:4566"
AWS_ENDPOINT_OVERRIDE="http://localhost.localstack.cloud:4566"
CLIENT_DATA_ENCRYPTION_KEY="${CLIENT_DATA_ENCRYPTION_KEY:-}"
ARTIFACT_BUCKET="sam-artifacts-local"
COMPOSE_FILE="${ROOT_DIR}/localstack/docker-compose.yaml"
TERRAFORM_PATH="${ROOT_DIR}/terraform"

log() {
  printf '%s\n' "$1"
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./deploy.sh [options]

Options:
  --mode <terraform|sam>          Deployment mode (default: terraform)
  --destroy                       Destroy infrastructure (Terraform mode only)
  --stack-name <name>             CloudFormation stack name (SAM mode)
  --region <aws-region>           AWS region (default: us-east-1)
  --endpoint <url>                LocalStack endpoint (default: http://localhost:4566)
  --aws-endpoint-override <url>   Endpoint used by Lambda SDK clients
  --client-data-encryption-key <k> Base64 AES-256 key for sensitive client data
  --artifact-bucket <name>        Artifact bucket (SAM mode)
  --compose-file <path>           docker-compose file path
  --terraform-path <path>         Terraform folder path
  -h, --help                      Show this help

Examples:
  ./deploy.sh
  ./deploy.sh --mode sam
  ./deploy.sh --destroy
EOF
}

resolve_bin() {
  local name="$1"
  if command -v "${name}" >/dev/null 2>&1; then
    command -v "${name}"
    return 0
  fi
  if command -v "${name}.exe" >/dev/null 2>&1; then
    command -v "${name}.exe"
    return 0
  fi
  return 1
}

to_bin_path() {
  local path="$1"
  local bin="$2"
  if [[ "${bin}" == *.exe ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath -w "${path}"
    return 0
  fi
  if [[ "${bin}" == *.exe ]] && [[ "${path}" =~ ^/mnt/([a-zA-Z])/(.*)$ ]]; then
    local drive="${BASH_REMATCH[1]^^}"
    local rest="${BASH_REMATCH[2]//\//\\}"
    printf '%s\n' "${drive}:\\${rest}"
    return 0
  fi
  printf '%s\n' "${path}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --destroy)
      DESTROY="true"
      shift
      ;;
    --stack-name)
      STACK_NAME="${2:-}"
      shift 2
      ;;
    --region)
      REGION="${2:-}"
      shift 2
      ;;
    --endpoint)
      ENDPOINT="${2:-}"
      shift 2
      ;;
    --aws-endpoint-override)
      AWS_ENDPOINT_OVERRIDE="${2:-}"
      shift 2
      ;;
    --client-data-encryption-key)
      CLIENT_DATA_ENCRYPTION_KEY="${2:-}"
      shift 2
      ;;
    --artifact-bucket)
      ARTIFACT_BUCKET="${2:-}"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="${2:-}"
      shift 2
      ;;
    --terraform-path)
      TERRAFORM_PATH="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

if [[ "${MODE}" != "terraform" && "${MODE}" != "sam" ]]; then
  fail "Invalid mode '${MODE}'. Use terraform or sam."
fi

if [[ "${DESTROY}" == "true" && "${MODE}" != "terraform" ]]; then
  fail "--destroy is only supported in terraform mode."
fi

DOCKER_BIN="$(resolve_bin docker)" || fail "docker is required."
CURL_BIN="$(resolve_bin curl)" || fail "curl is required."

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-${REGION}}"
export AWS_REGION="${AWS_REGION:-${REGION}}"
if [[ -z "${CLIENT_DATA_ENCRYPTION_KEY}" ]]; then
  if command -v openssl >/dev/null 2>&1; then
    CLIENT_DATA_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\r\n')"
  else
    CLIENT_DATA_ENCRYPTION_KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
  fi
fi

health_url="${ENDPOINT}/_localstack/health"

log "1/6 - Starting LocalStack"
if ! "${CURL_BIN}" -sS "${health_url}" | grep -q '"services"'; then
  "${DOCKER_BIN}" compose -f "${COMPOSE_FILE}" up -d --no-recreate || fail "Could not start LocalStack."
fi

log "2/6 - Waiting LocalStack health endpoint"
for _ in $(seq 1 45); do
  if "${CURL_BIN}" -sS "${health_url}" | grep -q '"services"'; then
    break
  fi
  sleep 2
done
"${CURL_BIN}" -sS "${health_url}" | grep -q '"services"' || fail "LocalStack did not become healthy in time."

log "3/6 - Building Java artifacts"
if command -v mvn >/dev/null 2>&1; then
  (
    cd "${PROJECT_PATH}"
    mvn clean package
  )
else
  "${DOCKER_BIN}" run --rm \
    -v "${PROJECT_PATH}:/app" \
    -v "${HOME}/.m2:/root/.m2" \
    -w /app \
    maven:3.9.9-eclipse-temurin-17 \
    mvn clean package
fi

if [[ "${MODE}" == "terraform" ]]; then
  TERRAFORM_BIN="$(resolve_bin terraform)" || fail "terraform is required in terraform mode."

  log "4/6 - Initializing Terraform"
  (
    cd "${TERRAFORM_PATH}"
    "${TERRAFORM_BIN}" init -input=false

    log "5/6 - Running Terraform"
    if [[ "${DESTROY}" == "true" ]]; then
      "${TERRAFORM_BIN}" destroy -auto-approve -input=false -var "client_data_encryption_key=${CLIENT_DATA_ENCRYPTION_KEY}"
    else
      "${TERRAFORM_BIN}" apply -auto-approve -input=false -var "client_data_encryption_key=${CLIENT_DATA_ENCRYPTION_KEY}"
    fi

    log "6/6 - Terraform outputs"
    "${TERRAFORM_BIN}" output
  )
  exit 0
fi

AWS_BIN="$(resolve_bin aws)" || fail "aws CLI is required for sam mode."
AWS_ARTIFACT_PATH="$(to_bin_path "${PROJECT_PATH}/target/function.jar" "${AWS_BIN}")"
AWS_PACKAGED_TEMPLATE_PATH="$(to_bin_path "${PACKAGED_TEMPLATE}" "${AWS_BIN}")"

log "4/6 - Ensuring LocalStack artifact bucket"
"${AWS_BIN}" --endpoint-url="${ENDPOINT}" --region "${REGION}" s3 mb "s3://${ARTIFACT_BUCKET}" >/dev/null 2>&1 || true

log "5/6 - Uploading Lambda artifact and generating packaged template"
"${AWS_BIN}" --endpoint-url="${ENDPOINT}" --region "${REGION}" \
  s3 cp "${AWS_ARTIFACT_PATH}" "s3://${ARTIFACT_BUCKET}/function.jar" >/dev/null

awk -v bucket="${ARTIFACT_BUCKET}" '
  /^[[:space:]]*CodeUri:[[:space:]]*hackaton-projeto-5\/target\/function\.jar[[:space:]]*$/ {
    match($0, /^[[:space:]]*/)
    indent = substr($0, RSTART, RLENGTH)
    print indent "CodeUri:"
    print indent "  Bucket: " bucket
    print indent "  Key: function.jar"
    next
  }
  { print }
' "${ROOT_DIR}/template.yaml" > "${PACKAGED_TEMPLATE}"

log "6/6 - Deploying CloudFormation stack"
"${AWS_BIN}" --endpoint-url="${ENDPOINT}" --region "${REGION}" cloudformation deploy \
  --template-file "${AWS_PACKAGED_TEMPLATE_PATH}" \
  --stack-name "${STACK_NAME}" \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides "AwsEndpointOverride=${AWS_ENDPOINT_OVERRIDE}" "ClientDataEncryptionKey=${CLIENT_DATA_ENCRYPTION_KEY}" >/dev/null

"${AWS_BIN}" --endpoint-url="${ENDPOINT}" --region "${REGION}" cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --query "Stacks[0].Outputs"

log "Deployment complete"
