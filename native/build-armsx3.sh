#!/usr/bin/env bash
# Compile le cœur PS3 d'ARMSX3 (RPCS3 pour Android, GPL-2.0-only) avec le NDK r29,
# puis range le .so strippé dans prebuilt/armsx3/. Mêmes options que android/configure.sh
# d'ARMSX3, variante « a13 » (API 33, armv8.2-a+dotprod+fp16 : Snapdragon 8 Gen 1 et plus).
# Durée : plusieurs heures sur 4 cœurs (LLVM est compilé depuis ses sources).
# Prérequis : git python3 ninja-build, cmake >= 3.30 (pip install cmake), ~25 Go libres.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
COMMIT="${ARMSX3_COMMIT:-103fc79e5c492e98956e5a6f8f7faa053ff8a1ed}"
NDK="${ANDROID_NDK:-$HOME/sdk/android-ndk-r29}"
WORK="${WORK:-$HOME/armsx3-build}"
SRC="$WORK/ARMSX3"
API="${ARMSX3_API:-33}"
MARCH="${ARMSX3_MARCH:-armv8.2-a+dotprod+fp16}"
JOBS="${JOBS:-$(nproc)}"
mkdir -p "$WORK"
if [ ! -d "$SRC/.git" ]; then
  git init -q "$SRC" && git -C "$SRC" remote add origin https://github.com/ARMSX2/ARMSX3
fi
git -C "$SRC" fetch -q --depth 1 origin "$COMMIT" && git -C "$SRC" checkout -q FETCH_HEAD
git -C "$SRC" submodule update --init --depth 1 --jobs 4
# En-têtes seuls : le cœur charge librashader.so à l'exécution s'il est présent (MPL, non lié).
[ -d "$SRC/3rdparty/librashader/include" ] || \
  git clone -q --depth 1 https://github.com/SnowflakePowered/librashader "$SRC/3rdparty/librashader"
for p in "$HERE"/native/armsx3-patches/*.patch; do [ -f "$p" ] && git -C "$SRC" apply "$p"; done

# ccache (0.66) : une recompilation après un petit correctif ne refait que les fichiers touchés.
# tools/native-cache.sh restore/save garde le cache entre deux conteneurs.
LAUNCH=()
if command -v ccache >/dev/null; then
  export CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}" CCACHE_MAXSIZE="${CCACHE_MAXSIZE:-12G}" CCACHE_SLOPPINESS=time_macros,include_file_mtime
  LAUNCH=(-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache)
fi
cmake -S "$SRC" -B "$WORK/core" -G Ninja \
  "${LAUNCH[@]}" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$API" -DCMAKE_BUILD_TYPE=Release \
  -DUSE_NATIVE_INSTRUCTIONS=OFF -DUSE_SDL=OFF -DUSE_SYSTEM_SDL=OFF -DUSE_GAMEMODE=OFF \
  -DUSE_SYSTEM_LIBUSB=OFF -DUSE_SYSTEM_CURL=OFF -DUSE_SYSTEM_OPENCV=OFF -DUSE_SYSTEM_FFMPEG=OFF \
  -DUSE_SYSTEM_ZLIB=ON -DUSE_DISCORD_RPC=OFF -DUSE_FAUDIO=OFF -DUSE_LIBEVDEV=OFF \
  -DWITH_LLVM=ON -DBUILD_LLVM=ON -DSTATIC_LINK_LLVM=ON -DUSE_LTO=OFF -DASMJIT_NO_SHM_OPEN=ON \
  -DARMSX3_ARM_MARCH="$MARCH"
ninja -C "$WORK/core" -j "$JOBS" android/libarmsx3-core.so

OUT="$HERE/prebuilt/armsx3"
mkdir -p "$OUT"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded \
  -o "$OUT/libarmsx3-core.so" "$WORK/core/android/libarmsx3-core.so"
echo "$COMMIT" > "$OUT/COMMIT"
ls -la "$OUT"
