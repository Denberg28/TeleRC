#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
flags=(-std=c++17 -Wall -Wextra -Werror -DTELERC_HOST_TEST=1 -Ibridge/mini_wifi_tests/stubs)
if [[ "${SANITIZE:-0}" == 1 ]]; then flags+=(-fsanitize=address,undefined -fno-omit-frame-pointer); fi
out="$(mktemp /tmp/go-firmware-test.XXXXXX)"
trap 'rm -f "$out"' EXIT
g++ "${flags[@]}" -DTELERC_GO_ELRS=1 firmware/go/tests/test_go.cpp -o "$out"
"$out"
g++ "${flags[@]}" -DTELERC_GO_ELRS=0 firmware/go/tests/test_wifi.cpp -o "$out"
"$out"
g++ "${flags[@]}" -DTELERC_GO_ELRS=1 -DTELERC_DRIVER=4 -DTELERC_SUPPLY_MAX_MV=12000 -DTELERC_MOTOR_RATED_MV=12000 -DTELERC_BTS_LOGIC_VERIFIED=1 firmware/go/tests/test_go.cpp -o "$out"
"$out"
