#!/usr/bin/env bash
# Compile le cœur libretro de Dolphin (GameCube/Wii, GPL-2.0+) pour Android arm64 avec le NDK r29,
# puis range le .so et le dossier Sys dans prebuilt/dolphin/, que build.sh met dans l'APK.
# Durée : ~15 min sur 4 cœurs. Prérequis : cmake ninja-build git.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
COMMIT="${DOLPHIN_COMMIT:-5466da350f2fd143ee3e7b921e8c33aee64f7065}"
NDK="${ANDROID_NDK:-$HOME/sdk/android-ndk-r29}"
WORK="${WORK:-$HOME/dolphin-build}"
SRC="$WORK/dolphin"
mkdir -p "$WORK"
if [ ! -d "$SRC/.git" ]; then
  git init -q "$SRC" && git -C "$SRC" remote add origin https://github.com/libretro/dolphin
fi
git -C "$SRC" fetch -q --depth 1 origin "$COMMIT" && git -C "$SRC" checkout -q FETCH_HEAD
# Sous-modules utiles seulement (pas Qt, FFmpeg ni les tests).
subs=$(git -C "$SRC" config -f .gitmodules --get-regexp path | awk '{print $2}' | grep -v -E "Externals/Qt|FFmpeg-bin|gtest")
git -C "$SRC" submodule update --init --depth 1 --jobs 8 $subs
git -C "$SRC/Externals/libadrenotools" submodule update --init --recursive --depth 1

cmake -S "$SRC" -B "$WORK/core" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release -DLIBRETRO=ON -DENABLE_LTO=OFF
ninja -C "$WORK/core"

OUT="$HERE/prebuilt/dolphin"
mkdir -p "$OUT"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded -o "$OUT/libdolphin_libretro.so" \
  "$(find "$WORK/core" -name dolphin_libretro_android.so | head -1)"
# Sys sans les thèmes de l'interface Qt, base de titres en anglais et français seulement.
rm -f "$OUT/dolphin-sys.zip"
(cd "$SRC/Data/Sys" && zip -q -r -9 "$OUT/dolphin-sys.zip" . -x 'Themes/*' -x 'wiitdb-*.txt' \
  && zip -q -9 "$OUT/dolphin-sys.zip" wiitdb-en.txt wiitdb-fr.txt)
echo "$COMMIT" > "$OUT/COMMIT"
ls -la "$OUT"
