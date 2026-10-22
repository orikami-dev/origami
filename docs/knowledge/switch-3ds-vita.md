# Switch, 3DS, PS Vita : fiche de recherche pour la 0.70

Recherche seulement (22/10/2026). Aucun code touché, aucune préversion. Ce document ne contient ni clé, ni firmware, ni jeu, ni méthode d'extraction : chaque émulateur demande à l'utilisateur de fournir lui-même ses fichiers légitimes, et Origami ne fait que les pointer (dossier Origami/BIOS et Origami/GAMES).

Sources : sites et dépôts officiels des projets (Eden, Azahar, Vita3K) et presse spécialisée d'octobre 2026. Les chiffres de performance viennent de la presse et de la communauté, pas de nos mesures : à valider sur le Fold 8.

## Vue d'ensemble

| | Switch | 3DS | PS Vita |
|---|---|---|---|
| Émulateur retenu | Eden (Citron en secours) | Azahar | Vita3K |
| Licence | GPLv3 ou ultérieure | GPLv2+, passée à GPLv3+ dans la 2127 (alpha) | GPLv2 |
| Android | Oui, APK officiels (standard, optimisé, legacy) | Oui, Play Store et APK, plus de 1 M de téléchargements | Oui, APK en builds continus |
| Réaliste pour la 0.70 | Oui, avec réserves | Oui, le plus facile | Oui, mais compatibilité inégale |

## Nintendo Switch

**Architecture.** Tegra X1 : 4 cœurs ARM Cortex-A57 (ARMv8, 64 bits), GPU Maxwell, 4 Go de RAM partagée. Le code des jeux est déjà de l'ARM64, donc l'émulation traduit surtout le système d'exploitation (Horizon) et les shaders Maxwell vers SPIR-V puis Vulkan. La virtualisation du CPU se fait par recompilation dynamique (dynarmic).

**Système de fichiers et formats.** Formats courants : NSP (paquet numérique), XCI (cartouche), NCA (contenu), NRO (homebrew). Le contenu est chiffré par un jeu de clés de la console, que l'utilisateur doit fournir à l'émulateur, ainsi que le firmware pour les jeux qui l'exigent. Origami ne distribue rien de tout cela et n'explique pas comment l'obtenir.

**Émulateurs.** Yuzu est fermé (accord de 2024), Ryujinx arrêté (2024). Eden est le fork Yuzu actif (v0.2.2 le 11/09/2026, dépôt auto-hébergé sur git.eden-emu.dev, GPLv3+). Citron et Kenji-NX existent aussi (Citron : licence peu claire dans sa doc, à vérifier avant tout usage ; Kenji-NX : peu documenté). Lemon (octobre 2026) est un nouveau venu basé sur Yuzu, trop jeune pour être retenu. La compatibilité Switch 2 est expérimentale et hors sujet.

**Réaliste sur un Galaxy Z Fold 8.** Eden recommande un Snapdragon 8 Gen 2 ou plus récent (le 8 Elite donne les meilleurs résultats), 8 Go de RAM minimum, 12 à 16 Go recommandés, et des pilotes Turnip à jour pour Adreno. Le Fold 8 est dans la cible. Les jeux 3D lourds restent exigeants (Zelda, Pokémon), les jeux légers passent bien. Eden propose une interpolation d'images pour afficher du 120 Hz.

**Points d'attention.**
- Eden publie un APK « Genshin Spoof » qui se fait passer pour Genshin Impact afin de profiter des optimisations Samsung. C'est exactement l'astuce du build perso de BOSS (package com.miHoYo.Yuanshen) : à ne jamais reprendre dans l'app publique (usurpation).
- Risque juridique : Nintendo est la plus agressive des trois. L'app publique ne doit jamais contenir de clés, de firmware, ni d'outil de contournement ; Eden doit être un cœur téléchargé à la demande, jamais embarqué.
- Le dépôt d'Eden n'est plus sur GitHub : la récupération automatique des versions demande un adaptateur spécifique.

**Faisable pour la 0.70.** Oui : cœur Eden en module à la demande, page de cœur dans le catalogue, lancement via les dossiers Origami, manette Flex dédiée, profils Performance, Équilibré, Personnalisé (valeurs dans un fichier par gamme d'appareil). **Pas faisable** : jeux Switch 2, multijoueur en ligne officiel, toute aide à l'obtention des clés ou du firmware.

## Nintendo 3DS

**Architecture.** CPU : ARM11 MPCore (2 cœurs applicatifs en 3DS, 4 en New 3DS), plus un ARM9 de sécurité. GPU : PICA200, un GPU à fonctions fixes avec shaders propres. 128 Mo de RAM (256 Mo en New 3DS). Deux écrans (400x240 et 320x240), un tactile.

**Formats et chiffrement.** CIA, 3DS/CCI, CXI, parfois en archives de ROM compressées. Les contenus officiels sont chiffrés, avec des clés propres à la console. Point important : Azahar a retiré la prise en charge des ROM chiffrées pour réduire le risque juridique, il faut donc des fichiers déjà déchiffrés par l'utilisateur ; Origami n'a pas à s'en mêler.

**Émulateurs.** Citra est arrêté (mars 2024). Lime3DS a fusionné avec le fork de PabloMK7 pour devenir Azahar (début 2025) ; Lime3DS est archivé. Azahar est actif : 2126.2 le 08/10/2026, multijoueur sur Android, double écran, Vulkan privilégié, compression de ROM. Android 10 ou plus, 64 bits, Snapdragon 835 ou mieux ; 2 Go de RAM suffisent. Il existe aussi un cœur libretro.

**Licence.** GPLv2+ jusqu'à la 2126, GPLv3+ à partir de la 2127 (alpha) : bien suivre ce changement, il s'applique à tout ce qui embarque son code. Compatible avec une publication GPL d'Origami.

**Réaliste sur le Fold 8.** Largement : un Snapdragon récent dépasse les besoins de la 3DS. Le défi est l'interface : deux écrans à gérer sur un pliant (Azahar a déjà un mode double écran Android), tactile, et orientation.

**Faisable pour la 0.70.** Oui, le plus simple des trois : cœur Azahar en module à la demande (ou intégration du cœur libretro si Origami sait déjà les charger), disposition des deux écrans adaptée au pli, manette 3DS (croix, A/B/X/Y, L/R/ZL/ZR, stick). **Pas faisable** : l'outil Artic Base d'Azahar qui lit une vraie console, et toute aide au déchiffrement.

## PlayStation Vita

**Architecture.** CPU : ARM Cortex-A9 à 4 cœurs, GPU PowerVR SGX543MP4+, 512 Mo de RAM plus 128 Mo de VRAM. Le code est de l'ARM 32 bits, que les téléphones actuels ne savent plus exécuter nativement (AArch64 seulement) : Vita3K recompile via une couche de traduction (dynarmic ou Unicorn selon la version). Le GPU est émulé en traduisant les shaders GXM vers Vulkan ou OpenGL.

**Formats et chiffrement.** VPK (archive), PKG (paquets numériques, déchiffrés avec une clé de licence propre à chaque jeu), cartouches et cartes mémoire propriétaires. Le firmware de la console doit être installé depuis des sources officielles. Origami n'embarque ni firmware ni clé de licence.

**Émulateur.** Vita3K, GPLv2, projet en continu depuis 2018. Version Android en builds continus (build 4208 le 20/10/2026, APK de 28 Mo). Android 7 ou plus, AArch64, Vulkan 1.0. Plus de 50 % des jeux testés sont jugés jouables selon le site officiel, mais la liste de compatibilité est en cours de refonte et n'est plus fiable. Le moteur Vulkan évolue vite.

**Réaliste sur le Fold 8.** Oui pour les jeux légers et 2D, variable pour les gros titres 3D. Le projet se déclare expérimental : plantages, glitchs et performances inégales sont à attendre. Les pilotes Adreno jouent beaucoup.

**Points d'attention.**
- Pas de numéro de version stable : seulement des builds numérotés. Il faudra épingler un build testé plutôt que suivre le dernier.
- Contrôles : le Vita a deux sticks, un tactile avant et arrière, un gyroscope. Le tactile arrière demande un geste de remplacement.

**Faisable pour la 0.70.** Oui en « bêta », avec un avertissement clair sur la compatibilité : cœur Vita3K en module à la demande, build épinglé, manette Vita (deux sticks, L/R, tactile). **Pas faisable** : la garantie de compatibilité, le tactile arrière natif, toute aide à l'obtention du firmware.

## Recommandation de séquence pour la 0.70

1. Azahar (3DS) d'abord : mûr, léger, licence claire.
2. Eden (Switch) ensuite : gros morceau pour les profils et le catalogue, risque juridique le plus élevé, donc modèle « cœur à la demande » strict.
3. Vita3K en dernier : étiquette « bêta » dans le catalogue, build épinglé.

## À vérifier avant de coder

- Pour chaque cœur : comment le charger comme module à la demande Google Play sans enfreindre la règle sur le code exécutable téléchargé.
- Licences : Azahar passe en GPLv3+, Vita3K reste en GPLv2 seul (pas de « ou ultérieure »). Contrôler la compatibilité de chaque cœur avec la licence d'Origami et publier le source de toute modification.
- Mesures réelles sur le Fold 8 avec adb et le banc d'essai (PS3 en premier, méthode déjà définie).
- Dépôt d'Eden hors GitHub : prévoir l'adaptateur pour la mise à jour des cœurs.
- Presse peu fiable : une partie des articles consultés est de qualité variable (par exemple le contenu sur le dépôt d'Eden en 2026 et le dépôt juridique de février 2026). Les faits retenus ici viennent surtout des pages officielles ; le reste est à recouper.
