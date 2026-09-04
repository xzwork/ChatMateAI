#!/usr/bin/env bash

set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
user_home="${HOME:?无法确定用户目录}"
keystore_path="${CHATMATE_KEYSTORE_PATH:-${user_home}/.android/chatmate-ai-release.jks}"
key_alias="${CHATMATE_KEY_ALIAS:-chatmate}"

if [[ ! -f "${project_dir}/gradlew" ]]; then
    printf '错误：没有找到 gradlew。\n' >&2
    exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
    printf '错误：没有找到 keytool，请先安装 JDK 17。\n' >&2
    exit 1
fi

printf 'ChatMate AI Release 构建\n'
printf '密钥文件：%s\n' "${keystore_path}"
printf '密钥别名：%s\n\n' "${key_alias}"

if [[ ! -f "${keystore_path}" ]]; then
    printf '首次构建，将创建新的正式发布密钥。\n'
    printf '请务必备份该文件和密码；丢失后将无法升级已发布的 App。\n\n'

    read -r -s -p '设置发布密码（至少 6 位）：' store_password
    printf '\n'
    read -r -s -p '再次输入发布密码：' password_confirmation
    printf '\n'

    if [[ "${store_password}" != "${password_confirmation}" ]]; then
        printf '错误：两次输入的密码不一致。\n' >&2
        exit 1
    fi
    if (( ${#store_password} < 6 )); then
        printf '错误：密码至少需要 6 位。\n' >&2
        exit 1
    fi

    mkdir -p "$(dirname -- "${keystore_path}")"
    export CHATMATE_SIGNING_STORE_PASSWORD="${store_password}"
    export CHATMATE_SIGNING_KEY_PASSWORD="${store_password}"
    keytool -genkeypair \
        -keystore "${keystore_path}" \
        -storetype PKCS12 \
        -alias "${key_alias}" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=ChatMate AI, OU=Mobile, O=ChatMate AI, C=CN" \
        -storepass:env CHATMATE_SIGNING_STORE_PASSWORD \
        -keypass:env CHATMATE_SIGNING_KEY_PASSWORD
    unset password_confirmation
else
    read -r -s -p '输入发布密钥库密码：' store_password
    printf '\n'
    read -r -s -p '输入密钥密码（直接回车表示与密钥库相同）：' key_password
    printf '\n'
    if [[ -z "${key_password}" ]]; then
        key_password="${store_password}"
    fi
    export CHATMATE_SIGNING_STORE_PASSWORD="${store_password}"
    export CHATMATE_SIGNING_KEY_PASSWORD="${key_password}"
fi

export CHATMATE_SIGNING_STORE_FILE="${keystore_path}"
export CHATMATE_SIGNING_KEY_ALIAS="${key_alias}"
trap 'unset CHATMATE_SIGNING_STORE_PASSWORD CHATMATE_SIGNING_KEY_PASSWORD store_password key_password' EXIT

printf '\n正在构建 Release APK…\n'
(
    cd "${project_dir}"
    bash ./gradlew --no-daemon assembleRelease
)

apk_dir="${project_dir}/app/build/outputs/apk/release"
latest_apk=""
for candidate in "${apk_dir}"/*.apk; do
    [[ -e "${candidate}" ]] || continue
    if [[ -z "${latest_apk}" || "${candidate}" -nt "${latest_apk}" ]]; then
        latest_apk="${candidate}"
    fi
done

if [[ -z "${latest_apk}" ]]; then
    printf '错误：构建完成，但没有找到 Release APK。\n' >&2
    exit 1
fi

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${sdk_dir}" && -f "${project_dir}/local.properties" ]]; then
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "${project_dir}/local.properties" | head -n 1)"
fi

apksigner_path=""
if [[ -n "${sdk_dir}" && -d "${sdk_dir}/build-tools" ]]; then
    while IFS= read -r candidate; do
        apksigner_path="${candidate}"
    done < <(find "${sdk_dir}/build-tools" -type f -name apksigner | sort)
fi

printf '\n构建成功：\n%s\n' "${latest_apk}"
if [[ -n "${apksigner_path}" ]]; then
    printf '\n签名验证：\n'
    "${apksigner_path}" verify --verbose --print-certs "${latest_apk}"
else
    printf '\n提示：未找到 apksigner，已跳过签名验证。\n'
fi
