#!/usr/bin/env bash
# Compile le pont PS3 d'Origami (native/ps3/foldplay_ps3.cpp) avec le NDK r29 et le range
# dans prebuilt/armsx3/libfoldplay_ps3.so. Quelques secondes ; le cœur se compile à part
# (native/build-armsx3.sh).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK:-$HOME/sdk/android-ndk-r29}"
CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang++"
mkdir -p "$HERE/prebuilt/armsx3"
"$CXX" -std=c++20 -O2 -fPIC -shared -static-libstdc++ -Wl,--no-undefined \
  -o "$HERE/prebuilt/armsx3/libfoldplay_ps3.so" "$HERE/native/ps3/foldplay_ps3.cpp" -ldl -llog
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$HERE/prebuilt/armsx3/libfoldplay_ps3.so"
ls -la "$HERE/prebuilt/armsx3/libfoldplay_ps3.so"
