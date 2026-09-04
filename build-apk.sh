#!/usr/bin/env bash

set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mode="${1:-}"

if [[ -z "${mode}" ]]; then
    printf '请选择构建类型：\n'
    printf '  1) Debug\n'
    printf '  2) Release（正式签名）\n'
    printf '  3) Debug + Release\n'
    read -r -p '请输入 1、2 或 3：' selection
    case "${selection}" in
        1) mode="debug" ;;
        2) mode="release" ;;
        3) mode="all" ;;
        *) printf '错误：无效选项。\n' >&2; exit 1 ;;
    esac
fi

print_latest_apk() {
    local variant="$1"
    local apk_dir="${project_dir}/app/build/outputs/apk/${variant}"
    local latest_apk=""
    local candidate
    for candidate in "${apk_dir}"/*.apk; do
        [[ -e "${candidate}" ]] || continue
        if [[ -z "${latest_apk}" || "${candidate}" -nt "${latest_apk}" ]]; then
            latest_apk="${candidate}"
        fi
    done
    if [[ -n "${latest_apk}" ]]; then
        printf '%s APK：\n%s\n' "${variant}" "${latest_apk}"
    fi
}

build_debug() {
    printf '\n正在构建 Debug APK…\n'
    (
        cd "${project_dir}"
        bash ./gradlew --no-daemon assembleDebug
    )
    printf '\n'
    print_latest_apk debug
}

build_release() {
    "${project_dir}/build-release.sh"
}

case "${mode}" in
    debug)
        build_debug
        ;;
    release)
        build_release
        ;;
    all|both)
        build_debug
        printf '\n----------------------------------------\n\n'
        build_release
        ;;
    *)
        printf '用法：%s [debug|release|all]\n' "$0" >&2
        exit 1
        ;;
esac
