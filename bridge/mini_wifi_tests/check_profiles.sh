#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
for api in 2 3; do
 for count in 2 4; do
  for driver in 1 2 3 4 5; do
   supply=5000
   [[ $driver == 4 ]] && supply=6000
   c++ -std=c++17 -Wall -Wextra -Werror -Istubs -DESP_ARDUINO_VERSION_MAJOR="$api" \
     -DTELERC_DRIVER="$driver" -DTELERC_MOTOR_COUNT="$count" -DTELERC_SUPPLY_MAX_MV="$supply" \
     -DTELERC_BTS_LOGIC_VERIFIED=1 test_profiles.cpp -o "$out/profile"
   "$out/profile"
  done
 done
done
printf '20 driver/count/API host combinations passed\n'
