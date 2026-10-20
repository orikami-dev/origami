#!/usr/bin/env bash
# Envoie la version courante d'Origami sur GitHub (dépôt orikami-dev/origami).
#  1. sources : commit + push de la branche de travail (docs/releases/<v>.md et CHANGELOG à jour).
#  2. canal bêta : la branche orpheline « beta » (écrasée à chaque fois) reçoit beta.json et
#     l'APK ; l'app les lit (Réglages > Mises à jour > Versions bêta), sans release à créer.
#  3. archive : branche orpheline apk-<v> (supprimable après la vraie release du PC).
# Usage : tools/publish.sh [-m "message"] [--no-archive]   (à lancer depuis la copie git du dépôt)
set -euo pipefail
REPO_DIR="${REPO_DIR:-/home/user/origami}"
SRC="${SRC:-/mnt/project-files/foldplay}"
MSG=""; ARCHIVE=1
while [ $# -gt 0 ]; do
  case "$1" in -m) MSG="$2"; shift 2;; --no-archive) ARCHIVE=0; shift;; *) echo "option inconnue $1"; exit 2;; esac
done
V="$(sed -n 's/^VERSION=//p' "$SRC/app/build.sh" | head -1)"
APK="$SRC/dist/origami-$V-debug.apk"
[ -f "$APK" ] || { echo "APK introuvable : $APK (lancer app/build.sh)"; exit 1; }
BR="$(git -C "$REPO_DIR" branch --show-current)"
URL="$(git -C "$REPO_DIR" remote get-url origin)"
push() { for d in 0 2 4 8 16; do sleep $d; git "$@" && return 0; done; return 1; }

echo "== 1/3 sources ($V sur $BR)"
for d in app native docs tools CHANGELOG.md PASSATION.md README.md; do
  [ -e "$SRC/$d" ] && cp -a "$SRC/$d" "$REPO_DIR/"
done
git -C "$REPO_DIR" add -A
git -C "$REPO_DIR" diff --cached --quiet || git -C "$REPO_DIR" commit -q -m "${MSG:-$V}"
push -C "$REPO_DIR" push -u origin "$BR"

SUMMARY="$(grep -E '^[-*] ' "$SRC/docs/releases/$V.md" 2>/dev/null | head -6 | sed 's/^[-*] //; s/\*\*//g' | tr '\n' '|' | sed 's/|$//; s/|/\\n• /g; s/^/• /' || true)"

publish_orphan() { # $1 = branche, $2 = nom du fichier apk
  T="$(mktemp -d)"
  git -C "$T" init -q -b "$1"
  cp "$APK" "$T/$2"
  printf '{"version":"%s","apk":"%s","notes":"%s"}\n' "$V" "$2" "$SUMMARY" > "$T/beta.json"
  git -C "$T" add -A
  git -C "$T" -c user.name=KAMI-WORKS -c user.email=noreply@kami-works.invalid commit -q -m "Origami $V"
  git -C "$T" remote add origin "$URL"
  push -C "$T" push -f origin "$1"
  rm -rf "$T"
}
echo "== 2/3 canal bêta"
publish_orphan beta origami-beta.apk
if [ "$ARCHIVE" = 1 ]; then
  echo "== 3/3 archive apk-$V"
  publish_orphan "apk-$V" "origami-$V-debug.apk"
fi
echo "Terminé : $V publié (bêta : https://github.com/orikami-dev/origami/tree/beta)"
