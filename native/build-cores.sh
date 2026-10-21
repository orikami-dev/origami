#!/usr/bin/env bash
# Compile libfoldplay_cores.so (affinité des fils) dans prebuilt/bridge, sans NDK ni libc.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../prebuilt/bridge"
mkdir -p "$OUT"
clang-18 --target=aarch64-linux-android26 -fPIC -O2 -ffreestanding -nostdlib -fno-stack-protector -shared \
  -fuse-ld=lld -Wl,-soname,libfoldplay_cores.so -Wl,-z,max-page-size=16384 -Wl,--hash-style=both \
  -o "$OUT/libfoldplay_cores.so" "$HERE/cores_affinity.c"
echo "$OUT/libfoldplay_cores.so"
