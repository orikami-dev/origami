# PS3 et Galaxy Z Fold 8 : base de connaissance

Recherche du 22/10/2026. Chaque chiffre porte sa source. Ce qui est une déduction de ma part est marqué **[déduction]**.

## 1. PlayStation 3

### Processeur Cell
| Élément | Valeur | Source |
|---|---|---|
| Cœur général (PPE/PPU) | 1 cœur PowerPC à 3,2 GHz, 2 threads, VMX | psdevwiki CELL_BE |
| Cœurs vectoriels (SPE/SPU) | 8 sur la puce, 1 désactivé pour le rendement, 1 réservé à l'hyperviseur : **6 utilisables par un jeu** | psdevwiki CELL_BE ; HandWiki PS3 specs |
| Mémoire des SPU | 256 Ko de « local store » par SPE, code et données partagés, aucun accès direct à la RAM | cs.york.ac.uk (tutoriel IBM) ; psdevwiki |
| Accès à la RAM | uniquement par transferts DMA via le MFC de chaque SPE | cs.york.ac.uk ; usenix HotOS09 |
| Caches PPE | 64 Ko L1, 512 Ko L2 | Wikipedia PS3 specs |
| Registres SPU | 128 registres de 128 bits | psdevwiki CELL_BE |
| SPU | en ordre, sans prédiction de branchement | cs.york.ac.uk |
| Puissance théorique | 204,8 GFLOPS simple précision (Cell seul) | Wikipedia PS3 specs |
| Endianness | gros-boutiste (big endian), 64 bits | psdevwiki CELL_BE |

### Bus et mémoire
| Élément | Valeur | Source |
|---|---|---|
| Bus interne EIB | 4 anneaux de 128 bits, jusqu'à 204,8 Go/s (300+ Go/s annoncé en crête) | psdevwiki CELL_BE ; Wikipedia |
| RAM principale | 256 Mo XDR, 25,6 Go/s | CNET ; psdevwiki |
| VRAM | 256 Mo GDDR3, 22,4 Go/s | CNET ; psdevwiki RSX |
| Cell vers RSX | 20 Go/s en écriture, 15 Go/s en lecture | psdevwiki RSX |
| Lecture Cell dans la VRAM | ~16 Mo/s (très lent) ; écriture 4 Go/s | psdevwiki RSX |
| Latence « load-hit-store » PPU | ~40 cycles | psdevwiki CELL_BE |

### GPU RSX
- Dérivé du NVIDIA G70/G71 (GeForce 7800), 500 à 550 MHz, shaders vertex et pixel séparés : 24 pipelines pixel, 8 vertex, 8 ROP. Source : psdevwiki RSX.
- Peut aussi utiliser jusqu'à 224 Mo de la XDR. Source : psdevwiki RSX.
- Sortie jusqu'à 1080p. Source : Wikipedia PS3 specs.

### Son
- Sortie Dolby 5.1, DTS, LPCM, « traitée par le Cell ». Source : CNET (fiche de 2005).
- **[déduction]** Le mixage audio est donc fait en logiciel, sur SPU, et passe par les mêmes SPU que le jeu. Un émulateur qui prend du retard sur les SPU produit du son qui saute ou se décale : cela correspond au symptôme vu sur Demon's Souls.

### Ce que cela impose à un émulateur sur ARM
1. **Les SPU sont le goulot.** Ce sont 6 cœurs vectoriels en ordre à 3,2 GHz dont le code doit être recompilé (JIT LLVM ou ASMJIT) en ARM64. Sources : armsx3.com (« heavier SPU/CPU demanding games are not [playable] ») ; generationamiga.com.
2. **Big endian vers little endian** : chaque accès mémoire PS3 est inversé. **[déduction]**
3. **Ordre mémoire** : x86 est plus strict que ARM64. ARMSX3 a dû ajouter deux barrières mémoire manquantes sur ARM64 (un SPU lisait des données périmées). Source : armsx2.net/armsx3/updates/1.0.
4. **Virgule flottante SPU et PPU** : comportement non standard à reproduire exactement. Corrigé dans ARMSX3 1.0 (324 écarts à 0 dans les tests ps3autotests). Source : armsx2.net.
5. **RSX** : shaders NVIDIA à convertir en SPIR-V/Vulkan. Sur Adreno, une extension Vulkan manquait et plus rien ne s'affichait ; ARMSX3 émet des bornes de tableau explicites. Source : armsx3.com.
6. **Cache de shaders et de modules PPU/SPU** : le premier lancement est long, le suivant bien plus rapide. Source : armsx3.com/download.
7. **Placement des threads** : SPU et RSX doivent aller sur les gros cœurs, la compilation sur les petits. Source : armsx3.com ; generationamiga.com.
8. **Mémoire** : la compilation d'un gros jeu peut saturer la RAM ; ARMSX3 règle désormais le nombre de modules compilés selon la mémoire libre. Source : armsx2.net.
9. Performances publiées : Skate 3 à 20-30 i/s sur Snapdragon 8 Gen 2 (preuve de concept, un seul jeu testé). Source : armsx3.com ; Android Authority 07/08/2026.
10. Exigences de l'app ARMSX3 : minimum 8 Go de RAM et Adreno 650 ; recommandé 12 Go et Adreno 740 ; **optimal Snapdragon 8 Elite, Adreno 830+, refroidissement actif**. Source : Google Play ARMSX3.

## 2. Samsung Galaxy Z Fold 8 (SM-F971B)

### Fiche technique
| Élément | Valeur | Source |
|---|---|---|
| SoC | Snapdragon 8 Elite Gen 5 « for Galaxy » (SM8850) | GSMArena 07/07/2026 ; Samsung.com |
| CPU | 2 cœurs Oryon à 4,74 GHz + 6 à 3,6 GHz (8 cœurs, 8 threads) | Samsung.com IE ; Notebookcheck |
| GPU | Adreno 840 à 1,3 GHz (1,2 GHz sur la version standard) | GSMArena ; devicespecifications.com |
| RAM | 12 Go LPDDR5X (16 Go selon marché) | Samsung.com ; Notebookcheck |
| Stockage | 256 Go / 512 Go / 1 To, UFS 4.0 ; ~218 Go libres sur le 256 | Samsung.com ; Notebookcheck |
| Batterie | 4800 mAh silicium-carbone (nominal 4660), charge 45 W filaire, 20 W sans fil, 1200 cycles | Samsung.com ; Notebookcheck |
| Écran intérieur | 7,6", 120 Hz, 2448 x 1848 | devicespecifications.com ; gizmochina |
| Écran extérieur | 5,5", 1972 x 1248 | Samsung.com |
| Poids / épaisseur | 201 g, 4,5 mm ouvert, 9,7 mm fermé | Samsung.com |
| Android | Android 17, One UI 9, mises à jour de sécurité jusqu'au 31/07/2033 | devicespecifications.com ; Samsung.com ; gizmochina |
| USB | USB-C 3.2 Gen 1 | Samsung.com |

Note : le Fold 8 « standard » est le modèle large 4:3 ; le « Ultra » est le livre classique. Même SoC, batterie de 5000 mAh sur l'Ultra. Source : GSMArena ; Samsung India.

### Refroidissement et thermique
- **Pas de chambre à vapeur** : feuilles de graphite, comme le Fold 7 (les modèles jusqu'au Fold 6 en avaient une). Source : Android Headlines 10/08/2026.
- Les foldables Samsung testés au stress 3DMark Wild Life montent vers 40 °C bien avant la fin du test, avec baisse progressive des performances. Source : Android Authority 02/07/2026.
- Le Fold 7 (même refroidissement) perd environ 57 % de ses performances après 20 minutes de stress test selon un utilisateur (source faible, Reddit r/GalaxyFold). Pas encore de mesure équivalente publiée pour le Fold 8 que j'aie trouvée.
- Des utilisateurs du Fold 8 signalent un bridage thermique continu, surtout écran extérieur et dehors au soleil. Source : Samsung Community, 14/08/2026 (témoignage, pas un test).
- Le Snapdragon 8 Elite Gen 5 a atteint 49,5 °C en surface sur certains flagships testés. Source : Android Authority.
- La charge rapide pendant le jeu ajoute de la chaleur. Source : TL Dev Tech (guide, source secondaire).
- **[déduction]** Un jeu PS3 est une charge CPU soutenue sur 6 à 8 threads : c'est le pire cas pour ce refroidissement. Le plafond de fréquence sera atteint en quelques minutes ; viser des performances stables plutôt que des pics (ce que fait déjà le profil Performance avec limite d'images).

### Pilotes Vulkan et Adreno 840
- Le pilote Qualcomm d'origine du 840 gère Vulkan 1.4 et le lancer de rayons matériel. Sources : sbcwiki (Adreno) ; Samsung India (« Vulkan integration enables real-time ray tracing »).
- **Turnip** (pilote libre de Mesa) : support Adreno 8xx fusionné dans Mesa 26.0 pour la partie OpenGL (Freedreno), la partie Vulkan « au fil du temps ». Source : Phoronix 20/01/2026.
- Des builds Turnip communautaires pour A8xx existent, empaquetés pour AdrenoTools (chargement sans root). Ils sont décrits comme **expérimentaux / bêta** : « hacky », risque de plantage système, bon pour OpenGL via émulateurs x86, mauvais pour les émulateurs Switch dans le test de janvier. Sources : K11MCH1/AdrenoToolsDrivers (08/01) ; Piperella-Turnip (A8xx, branche expérimentale, Vulkan 1.4.354) ; faux123/turnip-faux123-driver (correctifs d'image corrompue et de plantage KGSL sur 8xx).
- Turnip repose sur le backend KGSL du noyau Samsung, sans remplacer le noyau. Source : sbcwiki.
- **[déduction]** Cela concorde avec ce que BOSS a mesuré : Turnip A8xx sur le Fold 8 aide la stabilité de Demon's Souls, mais reste à surveiller (ne jamais l'imposer, garder le pilote Samsung en repli).
- Pour ARMSX3, l'upstream RPCS3 exige une extension Vulkan que le pilote Adreno n'implémente pas ; ARMSX3 contourne cela dans ses shaders. Source : armsx3.com.

### Ce que le Fold 8 impose à l'émulation
1. **Puissance de crête suffisante, soutenue insuffisante** : 8 cœurs Oryon rapides, mais pas de refroidissement actif. Profil Performance = limiter le travail inutile, pas pousser au maximum. **[déduction]**
2. **12 Go de RAM** : confortable pour les 512 Mo de la PS3, mais la compilation parallèle des modules PPU/SPU doit suivre la mémoire libre (comme ARMSX3). **[déduction]** appuyée par armsx2.net.
3. **Seulement 2 gros cœurs à 4,74 GHz + 6 à 3,6 GHz** : fixer SPU et RSX sur les gros, compilation sur les autres (voir CoreAffinity, 0.66.24). Source de la méthode : armsx3.com.
4. **Écran 4:3 intérieur et 5,5" extérieur** : la résolution de rendu interne doit s'adapter au ratio sans taille codée en dur (règle déjà posée par BOSS).
5. **Charge pendant le jeu** : à déconseiller dans l'app (chaleur cumulée).
6. **Pilotes** : pilote Samsung par défaut, Turnip en option téléchargeable, avec retour arrière.
7. **Stockage UFS 4.0** : suffisant pour le caches de shaders ; prévoir de la place (un jeu PS3 fait jusqu'à plus de 20 Go). Source : armsx3.com/download.

## 3. Lacunes honnêtes
- Je n'ai pas trouvé de test de stress 3DMark précis du Fold 8 standard (chiffres de baisse en %). À mesurer sur le téléphone de BOSS avec DIAG.
- Les chiffres Cell « 307,2 Gbit/s » (Wikipedia) et « 204,8 Go/s » (psdevwiki) décrivent la même bande passante de l'EIB sous deux unités différentes de comptage ; la crête réelle dépend du comptage.
- Le détail de ce que le pilote Samsung implémente en Vulkan sur Adreno 840 (liste des extensions) n'est pas dans cette fiche : à relever sur l'appareil (vulkaninfo).
- La fréquence exacte du RSX varie selon la source (500 ou 550 MHz).

## Sources
- Wikipedia, PlayStation 3 technical specifications
- psdevwiki.com/ps3/CELL_BE et /RSX
- cs.york.ac.uk, tutoriel IBM « Programming the PS3 Cell Architecture »
- CNET, « PlayStation 3 stats spotlighted » (2005)
- samsung.com (Irlande, Caraïbes, Inde), pages Galaxy Z Fold8
- GSMArena, 07/07/2026 ; Notebookcheck ; devicespecifications.com ; gizmochina
- Android Headlines 10/08/2026 ; Android Authority 02/07/2026 et 07/08/2026
- Samsung Community, 14/08/2026 ; TL Dev Tech
- Phoronix (Mesa 26.0 Adreno Gen 8) ; sbcwiki (Adreno GPU)
- GitHub : K11MCH1/AdrenoToolsDrivers, Piperella/Piperella-Turnip, faux123/turnip-faux123-driver, NightHammer1000/TurnipMagisk
- armsx3.com, Google Play ARMSX3, armsx2.net/armsx3/updates/1.0, generationamiga.com (21/08/2026)
