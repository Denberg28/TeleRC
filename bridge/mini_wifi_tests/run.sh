#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
flags=(-std=c++17 -Wall -Wextra -Werror -Istubs)
if [[ ${SANITIZE:-0} == 1 ]]; then flags+=(-g -fno-omit-frame-pointer -fsanitize=address,undefined); fi
c++ "${flags[@]}" test_mini_wifi.cpp -o "$out/mini"
"$out/mini" "$@"
