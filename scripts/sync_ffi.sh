#!/usr/bin/env bash
set -euo pipefail

ABI="${ABI:-arm64-v8a}"
LIB_NAME="libflare_im_core_sdk_ffi.so"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CLIENT_ROOT="$(cd "${EXAMPLE_ROOT}/../.." && pwd)"
REPO_ROOT="$(cd "${CLIENT_ROOT}/.." && pwd)"
CORE_ROOT="${REPO_ROOT}/flare-im-core-sdk"
APP_JNI_DIR="${EXAMPLE_ROOT}/app/src/main/jniLibs/${ABI}"
SDK_JNI_DIR="${CLIENT_ROOT}/packages/flare-core-android-sdk/src/main/jniLibs/${ABI}"

SOURCES=(
  "${EXAMPLE_ROOT}/native/artifacts/android/${ABI}/${LIB_NAME}"
  "${CLIENT_ROOT}/native/artifacts/android/${ABI}/${LIB_NAME}"
  "${REPO_ROOT}/target/aarch64-linux-android/release/${LIB_NAME}"
  "${CORE_ROOT}/target/aarch64-linux-android/release/${LIB_NAME}"
  "${CLIENT_ROOT}/examples/flare-core-flutter-app/android/app/src/main/jniLibs/${ABI}/${LIB_NAME}"
)

SOURCE=""
for candidate in "${SOURCES[@]}"; do
  if [[ -f "${candidate}" ]]; then
    SOURCE="${candidate}"
    break
  fi
done

if [[ -z "${SOURCE}" ]]; then
  cat >&2 <<EOF
Missing ${LIB_NAME} for ${ABI}.

Build it first:
  cd "${CORE_ROOT}"
  ANDROID_NDK_ROOT=/path/to/android/ndk cargo xtask build android

Then rerun:
  ${0}
EOF
  exit 1
fi

DESTINATIONS=("${APP_JNI_DIR}")
if [[ -d "${CLIENT_ROOT}/packages/flare-core-android-sdk" ]]; then
  DESTINATIONS+=("${SDK_JNI_DIR}")
fi

for destination in "${DESTINATIONS[@]}"; do
  mkdir -p "${destination}"
  cp -f "${SOURCE}" "${destination}/${LIB_NAME}"
  echo "Synced ${SOURCE}"
  echo "  -> ${destination}/${LIB_NAME}"
done
