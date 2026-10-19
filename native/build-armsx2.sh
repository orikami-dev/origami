#!/usr/bin/env bash
# Compile le cœur libretro d'ARMSX2 (PS2, GPL-3.0) pour Android arm64 avec le NDK r29,
# puis range le .so et les ressources dans prebuilt/armsx2/, que build.sh met dans l'APK.
# Durée : ~1 h sur 4 cœurs. Prérequis : cmake ninja-build flex bison git python3.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
COMMIT="${ARMSX2_COMMIT:-25e7084fdefcf1e9069481a583c38c103b03e8f1}"
NDK="${ANDROID_NDK:-$HOME/sdk/android-ndk-r29}"
WORK="${WORK:-$HOME/armsx2-build}"
SRC="$WORK/ARMSX2"
mkdir -p "$WORK"
if [ ! -d "$SRC/.git" ]; then
  git init -q "$SRC" && git -C "$SRC" remote add origin https://github.com/ARMSX2/ARMSX2
fi
git -C "$SRC" fetch -q --depth 1 origin "$COMMIT" && git -C "$SRC" checkout -q FETCH_HEAD
# Correctifs FOLD/PLAY (publiés avec l'app, GPL oblige) : accéléré suivi par le limiteur de la VM.
for p in "$HERE"/native/armsx2-patches/*.patch; do git -C "$SRC" apply "$p"; done

[ -f "$WORK/deps/lib/libshaderc_combined.a" ] || (cd "$WORK" && ANDROID_NDK="$NDK" \
  bash "$SRC/.github/workflows/scripts/android/build-dependencies.sh" "$WORK/deps")

# Mêmes options que la recette libretro d'ARMSX2 (linux_build_libretro.yml), en version Android.
cmake -S "$SRC" -B "$WORK/core" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$WORK/deps" -DCMAKE_FIND_ROOT_PATH="$WORK/deps" \
  -DENABLE_QT_UI=OFF -DENABLE_SDL_FRONTEND=OFF -DENABLE_LIBRETRO=ON -DENABLE_GSRUNNER=OFF -DENABLE_TESTS=OFF \
  -DENABLE_RECOMPILER_TEST_HOOKS=OFF -DENABLE_SETCAP=OFF -DUSE_BACKTRACE=OFF -DX11_API=OFF -DWAYLAND_API=OFF \
  -DDISABLE_ADVANCE_SIMD=TRUE -DCMAKE_DISABLE_PRECOMPILE_HEADERS=ON
ninja -C "$WORK/core" pcsx2-libretro

OUT="$HERE/prebuilt/armsx2"
mkdir -p "$OUT"
SO="$(find "$WORK/core" -name armsx2_libretro.so | head -1)"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded -o "$OUT/libarmsx2_libretro.so" "$SO"
# Ressources comme l'APK officiel : bin/resources sans les shaders dx11, plus armsx2_overrides.yaml.
rm -f "$OUT/pcsx2-resources.zip"
(cd "$SRC/bin/resources" && zip -q -r -9 "$OUT/pcsx2-resources.zip" . -x 'shaders/dx11/*')
(cd "$SRC/bin/resources-overlay" && zip -q -9 "$OUT/pcsx2-resources.zip" armsx2_overrides.yaml)
echo "$COMMIT" > "$OUT/COMMIT"
ls -la "$OUT"
