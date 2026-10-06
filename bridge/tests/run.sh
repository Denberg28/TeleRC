#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
build_dir=$(mktemp -d)
trap 'rm -rf "$build_dir"' EXIT
checks=()
if [[ "${SANITIZE:-0}" == 1 ]]; then
  checks+=(-fsanitize=address,undefined -fno-omit-frame-pointer -fno-pie -no-pie)
fi
for core in 2 3; do
  for transport in WIFI UART; do
    options=()
    if [[ "$transport" == UART ]]; then options+=(-DTEST_UART_MOTOR); fi
    g++ -std=c++17 -Wall -Wextra -Werror "${checks[@]}" -DESP_ARDUINO_VERSION_MAJOR="$core" "${options[@]}" \
      -Ibridge/tests/stubs -Ibridge/libraries/TeleRCLink/src bridge/tests/hybrid_test.cpp -o "$build_dir/hybrid"
    "$build_dir/hybrid"
  done
done
g++ -std=c++17 -Wall -Wextra -Werror "${checks[@]}" -Ibridge/tests/stubs -Ibridge/libraries/TeleRCLink/src \
  bridge/tests/lora_protocol_test.cpp -lcrypto -o "$build_dir/lora"
"$build_dir/lora"
for role in TEST_BASE_GATEWAY TEST_ROVER_GATEWAY; do
  for variant in 1262 1276; do
    for wifi in 0 1; do
      g++ -std=c++17 -Wall -Wextra -Werror "${checks[@]}" -D"$role" -DTELERC_RADIO_VARIANT="$variant" -DTELERC_BASE_WIFI="$wifi" \
        -Ibridge/tests/stubs -Ibridge/libraries/TeleRCLink/src bridge/tests/lora_gateway_test.cpp -lcrypto -o "$build_dir/gateway"
      "$build_dir/gateway"
    done
  done
done
python -m unittest discover -s bridge/tests -p 'test_*.py'

for test in lora_setup wifi_gateway; do
  g++ -std=c++17 -Wall -Wextra -Werror "${checks[@]}" -Ibridge/tests/stubs -Ibridge/libraries/TeleRCLink/src "bridge/tests/${test}_test.cpp" -lcrypto -o "$build_dir/$test"
  "$build_dir/$test"
done
