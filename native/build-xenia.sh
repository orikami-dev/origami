#!/usr/bin/env bash
# Compile le moteur Xbox 360 d'Origami : Xenia (xenia-canary, branche arm64, licence BSD) pour
# Android arm64 avec le NDK r29, en bibliothèque libxenia-app.so chargée par X360Activity.
# Correctifs Android dans native/xenia-patches (cible Android de CMake, mutex sans « robust »,
# pas de fontconfig ni d'ALSA, pilote de manette « origami »).
# Prérequis : git python3 ninja-build, cmake >= 3.30 (pip install cmake), glslang-tools et
# spirv-tools (shaders Vulkan compilés sur la machine hôte).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
COMMIT="${XENIA_COMMIT:-67cdaad18d464e744496835d064ff528f34e8b81}"
NDK="${ANDROID_NDK:-$HOME/sdk/android-ndk-r29}"
WORK="${WORK:-$HOME/x360}"
SRC="$WORK/src"
JOBS="${JOBS:-$(nproc)}"
mkdir -p "$WORK"
if [ ! -d "$SRC/.git" ]; then
  git init -q "$SRC" && git -C "$SRC" remote add origin https://github.com/xenia-canary/xenia-canary
fi
if [ -z "$(git -C "$SRC" status --porcelain --untracked-files=no)" ]; then
  git -C "$SRC" fetch -q --depth 1 origin "$COMMIT" && git -C "$SRC" checkout -q FETCH_HEAD
  git -C "$SRC" submodule update --init --depth 1 --jobs 4
  for p in "$HERE"/native/xenia-patches/*.patch; do [ -f "$p" ] && git -C "$SRC" apply "$p"; done
fi

# spirv-opt d'Ubuntu ne connaît pas --canonicalize-ids (simple renumérotation des ids) : on l'ignore.
mkdir -p "$WORK/vksdk/bin"
ln -sf "$(command -v glslangValidator)" "$(command -v spirv-dis)" "$WORK/vksdk/bin/"
cat > "$WORK/vksdk/bin/spirv-opt" <<'SH'
#!/bin/sh
args=""
for a in "$@"; do [ "$a" = "--canonicalize-ids" ] || args="$args \"$a\""; done
eval exec /usr/bin/spirv-opt $args
SH
chmod +x "$WORK/vksdk/bin/spirv-opt"

mkdir -p "$WORK/build"
# version.h est normalement écrit par xenia-build.py, qu'on n'utilise pas.
cat > "$WORK/build/version.h" <<H
#ifndef GENERATED_VERSION_H_
#define GENERATED_VERSION_H_
#define XE_BUILD_BRANCH "arm64"
#define XE_BUILD_COMMIT "$COMMIT"
#define XE_BUILD_COMMIT_SHORT "${COMMIT:0:7}"
#define XE_BUILD_DATE __DATE__
#endif
H
# ccache (0.66) : une recompilation après un petit correctif ne refait que les fichiers touchés.
# tools/native-cache.sh restore/save garde le cache entre deux conteneurs.
LAUNCH=()
if command -v ccache >/dev/null; then
  export CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}" CCACHE_MAXSIZE="${CCACHE_MAXSIZE:-12G}" CCACHE_SLOPPINESS=time_macros,include_file_mtime
  LAUNCH=(-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache)
fi
cmake -S "$SRC" -B "$WORK/build" -G Ninja \
  "${LAUNCH[@]}" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release
VULKAN_SDK="$WORK/vksdk" ninja -C "$WORK/build" -j "$JOBS" xenia-app

OUT="$HERE/prebuilt/xenia"
mkdir -p "$OUT"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded \
  -o "$OUT/libxenia-app.so" "$WORK/build/bin/Linux/libxenia-app.so"
echo "$COMMIT" > "$OUT/COMMIT"
ls -la "$OUT"
