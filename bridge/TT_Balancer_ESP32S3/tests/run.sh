#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
for verified in 0 1; do
  g++ -std=c++17 -Wall -Wextra -Werror \
    -fsanitize=address,undefined -fno-omit-frame-pointer \
    -DTELERC_HOST_TEST -DBALANCER_DIRECTIONS_VERIFIED="$verified" -Istubs \
    host_tests.cpp -o "$build_dir/tests-$verified"
  ASAN_OPTIONS=detect_leaks=0 "$build_dir/tests-$verified"
done
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -DTELERC_HOST_TEST -Istubs admin_tests.cpp -o "$build_dir/admin-tests"
ASAN_OPTIONS=detect_leaks=0 "$build_dir/admin-tests"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -DTELERC_HOST_TEST -Istubs serial_tests.cpp -o "$build_dir/serial-tests"
ASAN_OPTIONS=detect_leaks=0 "$build_dir/serial-tests"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -DTELERC_HOST_TEST -Istubs calibration_tests.cpp -o "$build_dir/calibration-tests"
ASAN_OPTIONS=detect_leaks=0 "$build_dir/calibration-tests"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -DTELERC_HOST_TEST -DBALANCER_IMU_MOUNT=1 \
  -Istubs mount_tests.cpp -o "$build_dir/mount-tests"
ASAN_OPTIONS=detect_leaks=0 "$build_dir/mount-tests"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -Istubs link_tests.cpp -o "$build_dir/link-tests"
ASAN_OPTIONS=detect_leaks=0 "$build_dir/link-tests"
g++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -fno-omit-frame-pointer -DCONFIG_IDF_TARGET_ESP32S3=1 -Istubs \
  network_tests.cpp -o "$build_dir/network-tests"
ASAN_OPTIONS=detect_leaks=0 "$build_dir/network-tests"
# Compile the production WiFi/task branches too, using fake platform APIs.
# This does NOT substitute for an actual ESP32 board build.
g++ -x c++ -std=c++17 -Wall -Wextra -Werror -fsyntax-only \
  -DCONFIG_IDF_TARGET_ESP32S3=1 -Istubs ../TT_Balancer_ESP32S3.ino
printf 'Production branches: host syntax check PASS\n'
