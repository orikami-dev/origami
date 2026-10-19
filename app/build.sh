#!/usr/bin/env bash
# Compile FOLD/PLAY sans Gradle ni SDK Google : aapt2 + javac + dx + clang/lld + zipalign + apksigner.
# Prérequis (Ubuntu/Debian) : apt install aapt apksigner zipalign dalvik-exchange openjdk-17-jdk clang lld
# et, pour le pont natif, l'Android NDK dans ~/sdk/android-ndk-* (sinon repli sur des souches).
# et un android.jar API 34 dans $ANDROID_JAR (par défaut ~/sdk/android-34.jar).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
# Racine du dépôt : native/, prebuilt/, keystore/ et dist/ sont à côté de app/.
ROOT="$(cd "$HERE/.." && pwd)"
JAR="${ANDROID_JAR:-$HOME/sdk/android-34.jar}"
BT="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}"
OUT="${BUILD_DIR:-$HOME/foldplay-build}"
VERSION=0.60.13
VERSION_CODE=71
# Clé debug gardée dans le projet pour que chaque build s'installe par-dessus le précédent.
KEYSTORE="${KEYSTORE:-$ROOT/keystore/foldplay-debug.jks}"

rm -rf "$OUT" && mkdir -p "$OUT"/{gen,classes}

"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
# Cœurs livrés, déjà compilés (native/build-armsx2.sh, native/build-dolphin.sh) : .so dans lib/, fichiers dans assets/.
# PS2 = ARMSX2 (GPL-3.0), GameCube/Wii = Dolphin (GPL-2.0+).
cp -r "$HERE/assets" "$OUT/assets"
for z in "$ROOT"/prebuilt/*/*.zip; do [ -f "$z" ] && cp "$z" "$OUT/assets/"; done

"$BT/aapt2" link -I "$JAR" --manifest "$HERE/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION" \
  -0 zip -A "$OUT/assets" --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res.zip"

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" \
  -d "$OUT/classes" $(find "$OUT/gen" "$HERE/src" -name '*.java')

"$BT/dx" --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"

# Pont libretro natif (arm64), compilé sans NDK par native/build-native.sh.
mkdir -p "$OUT/apk/lib/arm64-v8a"
# Sans NDK ni clang dans le conteneur : on reprend le pont du dernier build (prebuilt/bridge).
if bash "$ROOT/native/build-native.sh" "$OUT/native" >/dev/null 2>&1; then
  cp "$OUT/native/libfoldplay.so" "$OUT/apk/lib/arm64-v8a/"
  mkdir -p "$ROOT/prebuilt/bridge" && cp "$OUT/native/libfoldplay.so" "$ROOT/prebuilt/bridge/"
else
  echo "Pont natif non compilé ici : reprise de prebuilt/bridge/libfoldplay.so" >&2
  cp "$ROOT/prebuilt/bridge/libfoldplay.so" "$OUT/apk/lib/arm64-v8a/"
fi
# PS3 = ARMSX3 (GPL-2.0-only) et son pont, chargés seulement par Ps3Activity (processus :ps3).
# Xbox 360 : Xenia (BSD), compilé par native/build-xenia.sh.
if [ -f "$ROOT/prebuilt/xenia/libxenia-app.so" ]; then cp "$ROOT/prebuilt/xenia/libxenia-app.so" "$OUT/apk/lib/arm64-v8a/"
else echo "Attention : prebuilt/xenia/libxenia-app.so absent, APK sans Xbox 360 (native/build-xenia.sh)" >&2; fi
for so in libarmsx3-core.so libfoldplay_ps3.so; do
  if [ -f "$ROOT/prebuilt/armsx3/$so" ]; then cp "$ROOT/prebuilt/armsx3/$so" "$OUT/apk/lib/arm64-v8a/"
  else echo "Attention : prebuilt/armsx3/$so absent, APK sans PS3 (native/build-armsx3.sh, native/build-ps3-bridge.sh)" >&2; fi
done
for core in armsx2 dolphin; do
  if [ -f "$ROOT/prebuilt/$core/lib${core}_libretro.so" ]; then
    cp "$ROOT/prebuilt/$core/lib${core}_libretro.so" "$OUT/apk/lib/arm64-v8a/"
  else
    echo "Attention : prebuilt/$core absent, APK sans ce moteur (lancer native/build-$core.sh)" >&2
  fi
done

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q -j unsigned.apk classes.dex)
(cd "$OUT/apk" && zip -q -r ../unsigned.apk lib)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass foldplay -keypass foldplay \
    -alias foldplay -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Origami debug, O=KAMI-WORKS" >/dev/null 2>&1
fi
"$BT/apksigner" sign --v4-signing-enabled false --ks "$KEYSTORE" --ks-pass pass:foldplay --key-pass pass:foldplay \
  --out "$ROOT/dist/origami-$VERSION-debug.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$ROOT/dist/origami-$VERSION-debug.apk"
echo "APK : $ROOT/dist/origami-$VERSION-debug.apk"
