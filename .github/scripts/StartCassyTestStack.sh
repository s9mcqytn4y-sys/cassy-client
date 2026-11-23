#!/usr/bin/env bash
# Starts the Docker test stack (cassy-docker Core/DockerComposeTest.yml) for live e2e jobs.
# Checks out cassy-docker and cassy-backend at the branch with the same name as this one
# when it exists (coordinated multi-repo PRs), otherwise at main.
set -eu

BRANCH="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-main}}"
WORK="${RUNNER_TEMP:-/tmp}/cassy-stack"
mkdir -p "$WORK"

clone_repo() {
  repo="$1"
  dir="$WORK/$2"
  ref=main
  if git ls-remote --exit-code --heads "https://github.com/s9mcqytn4y-sys/$repo.git" "$BRANCH" >/dev/null 2>&1; then
    ref="$BRANCH"
  fi
  echo "$repo @ $ref"
  git clone --quiet --depth 1 --branch "$ref" "https://github.com/s9mcqytn4y-sys/$repo.git" "$dir"
}

clone_repo cassy-docker Docker
clone_repo cassy-backend CassyBackend

CASSY_BACKEND_DIR="$WORK/CassyBackend" docker compose -f "$WORK/Docker/Core/DockerComposeTest.yml" up -d --wait --wait-timeout 300
docker compose -f "$WORK/Docker/Core/DockerComposeTest.yml" ps
curl -fsS http://127.0.0.1:18080/api/v1/health
echo
curl -fsS http://127.0.0.1:9101/health
echo
