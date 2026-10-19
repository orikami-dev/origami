#!/usr/bin/env bash
# Teste le rendu OpenGL ES du pont sur la machine (Mesa en mode surfaceless) avec un cœur d'essai
# qui peint le haut en rouge et le bas en bleu : l'image doit arriver à l'endroit, en RGBA.
# Prérequis : apt install libegl1 libgles2 libegl-mesa0 libgl1-mesa-dri
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HOME/gltest}"
JI="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")/include"
mkdir -p "$OUT/src/com/hideo/foldplay"
ln -sf /usr/lib/x86_64-linux-gnu/libEGL.so.1 "$OUT/libEGL.so"
ln -sf /usr/lib/x86_64-linux-gnu/libGLESv2.so.2 "$OUT/libGLESv2.so"
gcc -shared -fPIC -I"$HERE/../include" -o "$OUT/libglcore.so" "$HERE/glcore.c"
gcc -shared -fPIC -o "$OUT/libshim.so" "$HERE/shim.c"
gcc -shared -fPIC -O1 -g -I"$HERE/../include" -I"$JI" -I"$JI/linux" -o "$OUT/libfoldplay.so" \
  "$HERE/../foldplay_libretro.c" -L"$OUT" -lshim -ldl -Wl,-rpath,"$OUT"
cp "$HERE/GLTest.java" "$HERE/../../app/src/com/hideo/foldplay/Libretro.java" "$OUT/src/com/hideo/foldplay/"
javac -d "$OUT/out" "$OUT"/src/com/hideo/foldplay/*.java
EGL_PLATFORM=surfaceless LD_LIBRARY_PATH="$OUT" java -cp "$OUT/out" com.hideo.foldplay.GLTest "$OUT"
