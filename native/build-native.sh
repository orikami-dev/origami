#!/usr/bin/env bash
# Compile le pont libretro (libfoldplay.so, arm64).
# Avec l'Android NDK (ANDROID_NDK, ou ~/sdk/android-ndk-*) : vrai sysroot, liens contre la vraie bionic.
# Sans NDK : repli sur clang + lld et des bibliothèques-souches (les vraies sont celles de l'appareil).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HOME/foldplay-native}"
mkdir -p "$OUT"
NDK="${ANDROID_NDK:-$(ls -d "$HOME"/sdk/android-ndk-* 2>/dev/null | sort -V | tail -1)}"
LDFLAGS=(-shared -Wl,-soname,libfoldplay.so -Wl,--hash-style=both -Wl,-z,max-page-size=16384
  -Wl,--no-undefined -Wl,--build-id)

if [ -n "$NDK" ] && [ -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]; then
  CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
  # -iquote : libretro.h vient d'include/, mais pas sa souche stdio.h (le NDK a la vraie).
  "$CC" -fPIC -O2 -fvisibility=hidden -Wall -Wextra -Wno-unused-parameter -iquote "$HERE/include" \
    -c "$HERE/foldplay_libretro.c" -o "$OUT/foldplay_libretro.o"
  "$CC" "${LDFLAGS[@]}" -o "$OUT/libfoldplay.so" "$OUT/foldplay_libretro.o" -ldl -llog
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$OUT/libfoldplay.so"
  echo "$OUT/libfoldplay.so (NDK $(basename "$NDK"))"
  exit 0
fi

CC="${CC:-clang-18}"
JDK_INC="${JDK_INC:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")/include}"
TARGET=aarch64-linux-android26
RES="$("$CC" -print-resource-dir)/include"
mkdir -p "$OUT/stubs"
FLAGS=(--target=$TARGET -fPIC -O2 -ffreestanding -nostdinc -fno-stack-protector -fvisibility=hidden
  -isystem "$RES" -isystem "$HERE/include" -isystem "$JDK_INC" -isystem "$JDK_INC/linux")
for lib in c dl log; do
  "$CC" --target=$TARGET -fPIC -fno-builtin -w -nostdlib -shared -fuse-ld=lld -Wl,-soname,lib$lib.so \
    -o "$OUT/stubs/lib$lib.so" "$HERE/stubs/lib$lib.c"
done
"$CC" "${FLAGS[@]}" -Wall -Wextra -Wno-unused-parameter -c "$HERE/foldplay_libretro.c" -o "$OUT/foldplay_libretro.o"
"$CC" --target=$TARGET -nostdlib -fuse-ld=lld "${LDFLAGS[@]}" \
  -o "$OUT/libfoldplay.so" "$OUT/foldplay_libretro.o" -L"$OUT/stubs" -lc -ldl -llog
echo "$OUT/libfoldplay.so (souches, sans NDK)"
