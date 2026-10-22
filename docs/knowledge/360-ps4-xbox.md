# Xbox 360, PS4, Xbox One/Series : état au 22/10/2026

Recherche seulement : aucun code, aucune préversion, aucune release.
Sources : notes du projet (PASSATION.md, CHANGELOG.md) et web. Les articles de tech-insider.org, pocket-gaming.org et Windows Central sont des sources secondaires : à recouper avant toute décision.

## 1. Xbox 360 (Xenia dans Origami)

### Où on en est (notes du projet)
- Xenia est porté par nous depuis la 0.60.0 (base xenia-canary, branche arm64, BSD-3). Il tourne via `X360Activity`, avec un pilote de manette maison et un patch `native/xenia-patches/02-origami-fixes.patch`.
- Étapes passées : démarrage OK en 0.60.11 ; Skyrim ne plante plus et son 360 sur Android en 0.60.13 ; profil joueur connecté d'office pour DMC 4 ; fenêtres 360 auto en 0.62.1 ; 0.65.2 à 0.65.4 : son, relecture rapide des images (plus d'image noire sur DMC 4), compilation synchrone des shaders ; 0.65.6 relance auto ; 0.66.x : répartition des cœurs (Performance par défaut).
- Pas d'états sauvegardés ni de ×2 sur 360 : Xenia ne les offre pas (message clair dans l'app).

### Ce qui reste ou reste flou
1. **Écran noir / bleu 360** : priorité n°1 de BOSS (21/10). Les notes de passation ne contiennent pas de journal récent de DMC 4 ou Skyrim après la 0.65.4 : je ne peux pas dire si c'est réglé. Pistes déjà notées : pipelines « placeholder » du hot-swap asynchrone, chemin de rendu FBO/FSI sur Adreno, erreurs « Failed to create Vulkan pipeline ».
2. **Valeurs de profil non vérifiées** : le dump de cvars au lancement montrait `readback_resolve = "none"` et `render_target_path_vulkan = ""` alors que les profils visent `fast` / `fbo`. Soit les valeurs ne passent pas, soit le dump précède les surcharges.
3. **DMC 4** : rame dès l'écran-titre (20/10) ; **Skyrim** : zones noires signalées.
4. **Pilote graphique** : le choix du pilote Adreno (Turnip) est la priorité n°2. C'est la piste la plus documentée publiquement (voir ci-dessous).
5. **Son** : correctif prêt, inclus depuis 0.65.2 ; à revalider sur un jeu long.
6. **Jeux témoins** : Demon's Souls et MGS4 sont des jeux **PS3** (ARMSX3), pas 360. Le seul suivi chiffré dans les notes est celui de Demon's Souls (30 i/s). Aucune mesure de MGS4 trouvée dans les notes. DMC 4 et Skyrim sont les témoins 360.
7. Rappel : la 360 de BOSS n'est pas débloquée ; les ISO sont dans `/sdcard/Origami/GAMES`.

### Contexte public (à date)
- **Xenia Canary** reste la ligne active (builds quasi quotidiens ; build 2026-10-10-7e8473d, commit dac2794 du 16/10). Le Xenia principal est quasi gelé (dernier push 18/02/2026). Officiellement aucun build mobile. Sources : xenia.jp ; tech-insider.org (xenia-vs-xenia-canary-vs-manager-2026, 05/09 ; dolphin-vs-cemu-vs-xenia-2026, 18/10).
- **Xenia Edge** (fork communautaire) vise Vulkan, Linux et futurs ARM64/macOS ; encore au stade de projet annexe. Source : xenia.jp.
- **Concurrents Android** : `xenia-android/xenia-android` (juillet 2026) est en fin de vie et renvoie vers `libaerto/xeo` (github.com/xenia-android/xenia-android). **X360 Mobile** (x360mobile.com, juin 2026) : port ARM64 de Canary, dit optimisé pour Snapdragon avec pilotes Turnip, 38 jeux « pleine vitesse » sur Snapdragon 8 Gen 5 (déclaration de pocket-gaming.org, 11/06/2026, non vérifiée). **aX360e Free** (Aenu, déjà sur le Play Store avec pubs, version payante 6,99 $) repose sur la branche arm64 : mêmes bases que nous. Source : pocket-gaming.org, 11 et 12/06/2026.
- Enseignement utile : la qualité sur Android dépend surtout du **pilote Mesa Turnip** et du cache de shaders, pas seulement du moteur. À creuser côté pilote.

## 2. PS4 et Xbox One/Series : veille seulement

### PS4
- **shadPS4** (GPLv2, x86-64) : v0.16.0 le 01/06/2026 ; v0.17.0 (juillet) ; v0.18.0 (18/08) ; v0.19.0 « Shadoween special » (02/10) ; **v0.20.0 « Dead Or Stephen 5 » (14/10)** avec shadNet 0.1.0 (premier jeu en ligne : Bloodborne). Préversions quotidiennes jusqu'au 21/10 (f0ea8eb). Sources : github.com/shadps4-emu/shadPS4/releases ; tech-insider.org (20/10).
- Compatibilité : environ 117 jeux jouables sous Windows, 150 sous Linux (mi-2026, tech-insider.org, 16/09) ; inFAMOUS Second Son déclaré jouable le 07/10 (tech4gamers.com).
- **Android** : pas de build officiel mûr. Des vidéos de juin 2026 montrent un démarrage de Bloodborne et Sekiro sur Android (pocket-gaming.org, 21/06), sans rendu 3D stable ; un autre article (heldgames.com, 30/05) rappelle que shadPS4 est x86 seulement. Pour Origami : recompilation ARM64 toujours à faire, comme déjà noté.
- Licence : GPLv2, donc source à publier si on le modifie.

### PS5 (au passage)
- **KytyPS5** : le plus avancé ; Demon's Souls et Astro Bot à plus de 60 i/s sur PC ; porté sur Xbox Series X en mode développeur le 13/10 (≈10 i/s) (Windows Central). **SharpEmu** : preuve de concept (0,6 i/s sur Steam Deck, tech-insider.org, 23/09).

### Xbox One / Series
- Aucun émulateur de Xbox One ou Series jouable n'a été trouvé. L'actualité du mois est l'inverse : des émulateurs **tournant sur** Xbox Series X en mode développeur (KytyPS5, 13/10 ; Eden/« NXbox » pour la Switch, 16-18/10 ; Windows Central). Une source cite aussi des jeux Xbox One sur iPhone « sans émulation » : hors sujet pour nous.
- **xemu** (Xbox d'origine, 2001) : v0.8.137 du 16/10/2026, active, mais c'est une autre console. Source : xemu.app.

## Réserves
- Les chiffres de compatibilité et les dates viennent en partie de sites agrégateurs ; aucune de ces affirmations n'a été testée par nous.
- Je n'ai pas lu les journaux récents du téléphone : l'état réel du rendu 360 est celui des dernières notes (0.65.x), à confirmer par un test.
