#!/usr/bin/env bash

set -euo pipefail

readonly WRAPPER_VERSION="8.11.1"
readonly WRAPPER_SHA256="2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
readonly WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${WRAPPER_VERSION}/gradle/wrapper/gradle-wrapper.jar"

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper_dir="${repo_root}/gradle/wrapper"
wrapper_jar="${wrapper_dir}/gradle-wrapper.jar"
temporary_jar=""

cleanup() {
  if [[ -n "${temporary_jar}" ]]; then
    rm -f -- "${temporary_jar}"
  fi
}
trap cleanup EXIT

mkdir -p -- "${wrapper_dir}"
temporary_jar="$(mktemp "${wrapper_dir}/gradle-wrapper.jar.XXXXXX")"

curl \
  --proto '=https' \
  --tlsv1.2 \
  --fail \
  --location \
  --silent \
  --show-error \
  --output "${temporary_jar}" \
  "${WRAPPER_URL}"

actual_sha256="$(sha256sum "${temporary_jar}" | awk '{print $1}')"
if [[ "${actual_sha256}" != "${WRAPPER_SHA256}" ]]; then
  printf 'Gradle Wrapper checksum mismatch: expected %s, got %s\n' \
    "${WRAPPER_SHA256}" "${actual_sha256}" >&2
  exit 1
fi

mv -- "${temporary_jar}" "${wrapper_jar}"
temporary_jar=""
printf 'Gradle Wrapper %s verified and installed.\n' "${WRAPPER_VERSION}"
