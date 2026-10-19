# Origami : versions

Toutes les versions de test sont publiées en releases privées (`v0.1.0` à la dernière), chacune avec son APK debug et ses notes (`releases/notes/<version>.md`). Studio : KAMI-WORKS.

## Ce que fait Origami aujourd'hui (0.60.12)

**Lanceur**
- Accueil façon XMB en mode Tsuru (téléphone à moitié plié) : catégories par console, jeux en colonne, fiche du jeu, illustrations de fond larges.
- Mode Washi sur l'écran externe : lanceur d'apps Android en grille, option lanceur par défaut.
- Catégories « Jeux mobiles » (jeux Android installés) et « Remote Play » (clients PlayStation, Xbox, Steam Link par constructeur, « Ajouter un client… »).
- Jaquettes automatiques (libretro-thumbnails, ScreenScraper) et jaquette manuelle, synopsis officiels courts.
- Tous les pliants et tous les ratios, changements à chaud ; navigation complète à la manette ; menus Codec partout.
- Réglages en deux volets, 10 langues (fr, en, es, de, it, pt-BR, ru, ja, ko, zh), langue système automatique.
- Cinq thèmes (Codec, Arctique, Écarlate, Sakura, Ambre) appliqués sans redémarrage, avec fond, fond de manette, icône et pack d'icônes assortis.

**Émulation intégrée**
- Pont libretro maison : consoles 8/16/32 bits, arcade (FBNeo), PS1 (HD si BIOS), N64, PSP, Dreamcast en OpenGL ES.
- PS2 (ARMSX2, livré dans l'APK), GameCube et Wii (Dolphin), PS3 (ARMSX3, processus séparé), Xbox 360 (Xenia porté sur Android).
- Profils Performance, Équilibré, Qualité qui règlent vraiment chaque cœur.
- Sauvegardes : import et export par console ; import de BIOS et du firmware PS3.
- ATH façon RTSS sur toutes les consoles (images/s, vitesse, processeur, mémoire, batterie, température, chauffe).

**Manettes**
- Profils Flex (par console, automatique au lancement, suit la rotation), 2D, 3D ; manette d'accueil de navigation.
- En paysage : manette transparente superposée au jeu en plein écran ; en portrait : skin complet.
- Éditeur complet (grille aimantée, tailles, couleurs, vibration), manettes physiques jusqu'à 4 joueurs, L3/R3 au double appui.

## Grandes étapes
| Versions | Contenu |
|---|---|
| 0.1 à 0.7 | Lanceur pour Fold, bibliothèque, 21 consoles, manette configurable |
| 0.8 à 0.22 | Moteur libretro intégré, 3D OpenGL ES, menu Maison, assistant, arcade |
| 0.23 à 0.35 | PS2 (ARMSX2), GameCube/Wii (Dolphin), nom Origami, tous les pliants |
| 0.40 à 0.42 | XMB, réglages en deux volets, langues, profils de manette, thèmes |
| 0.50 | PS3 (ARMSX3), thème sans redémarrage |
| 0.60 | Xbox 360 (Xenia), Washi, Remote Play, Jeux mobiles, manette Flex par console |

## Corrections récentes
- 0.60.8 : jeux PS3 et 360 qui revenaient à l'accueil (plein écran demandé avant la création de la fenêtre).
- 0.60.9 à 0.60.11 : plantage Xbox 360 au chargement (cache d'instructions de Xenia ; le réglage qui le coupe était transmis en texte au lieu d'un booléen).
- À venir en 0.60.13 : Skyrim (verrou détruit à la fin d'un fil du jeu), écran noir de DMC 4 (son AAudio, appel système manquant), cache des shaders PS3 enregistré.
