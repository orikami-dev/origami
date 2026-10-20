#!/usr/bin/env bash
# Garde le cache de compilation des moteurs (ccache) d'un conteneur cloud à l'autre.
#   bash tools/native-cache.sh restore   avant build-armsx3.sh / build-xenia.sh
#   bash tools/native-cache.sh save      après une compilation réussie
# Sans cache, LLVM et le cœur PS3 se recompilent en plusieurs heures ; avec, en quelques minutes.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
STORE="${CACHE_STORE:-$HERE/cache}"
export CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"
case "${1:-}" in
  restore)
    [ -f "$STORE/ccache.tar.zst" ] || { echo "pas de cache dans $STORE"; exit 0; }
    mkdir -p "$CCACHE_DIR"
    tar -I zstd -xf "$STORE/ccache.tar.zst" -C "$CCACHE_DIR"
    ccache -s | head -5 ;;
  save)
    mkdir -p "$STORE"
    ccache -c >/dev/null 2>&1 || true
    tar -I 'zstd -T0 -3' -cf "$STORE/ccache.tar.zst.tmp" -C "$CCACHE_DIR" .
    mv "$STORE/ccache.tar.zst.tmp" "$STORE/ccache.tar.zst"
    ls -la "$STORE/ccache.tar.zst" ;;
  *) echo "usage : $0 restore|save"; exit 1 ;;
esac
