#!/usr/bin/env bash
# Script d'installation du SDK Android pour l'environnement cloud du projet KAMI-WORKS.
# À coller dans "Setup script" de l'environnement cloud. Nécessite dl.google.com et
# maven.google.com dans les domaines autorisés.
set -euo pipefail

export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME"
curl -sSL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q -o clt.zip -d cmdline-tools
rm -rf cmdline-tools/latest && mv cmdline-tools/cmdline-tools cmdline-tools/latest
rm clt.zip

SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --licenses >/dev/null
"$SDKM" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "export ANDROID_HOME=$ANDROID_HOME" >> "$HOME/.bashrc"
echo 'export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH' >> "$HOME/.bashrc"
