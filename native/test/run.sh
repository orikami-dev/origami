#!/usr/bin/env bash
# Teste le pont libretro sur la machine (x86_64, vraie JVM) avec un cœur d'essai minimal.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HOME/bridgetest}"
JI="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")/include"
mkdir -p "$OUT/inc" "$OUT/src/com/hideo/foldplay"
cp "$HERE/../include/libretro.h" "$OUT/inc/"
gcc -shared -fPIC -I"$OUT/inc" -o "$OUT/libtestcore.so" "$HERE/testcore.c"
gcc -shared -fPIC -o "$OUT/libshim.so" "$HERE/shim.c"
gcc -shared -fPIC -O1 -g -I"$HERE/../include" -I"$JI" -I"$JI/linux" -o "$OUT/libfoldplay.so" \
  "$HERE/../foldplay_libretro.c" -L"$OUT" -lshim -ldl -Wl,-rpath,"$OUT"
cp "$HERE/BridgeTest.java" "$HERE/../../app/src/com/hideo/foldplay/Libretro.java" "$OUT/src/com/hideo/foldplay/"
javac -d "$OUT/out" "$OUT"/src/com/hideo/foldplay/*.java
java -cp "$OUT/out" com.hideo.foldplay.BridgeTest "$OUT"
