# 📝 Journal des versions

Toutes les versions de test sont publiées en releases privées (`v0.1.0` à la dernière), chacune avec son APK debug et sa page de notes (`docs/releases/<version>.md`). Studio : KAMI-WORKS.

## 0.65.10

Jeux PS3 et 360 en portrait : ATH dans la bande de la barre d état, deux pastilles égales séparées au milieu.

## 0.65.9

Menu fantôme corrigé, Tsuru sous les barres, formats sans déplacement, mesures de performance dans les journaux.

## 0.65.8

PS3 : correction de la course entre fenêtre et initialisation du moteur (écran noir).

## 0.65.7

Journaux PS3 et 360 plus riches ; sorties voulues non signalées comme plantages.

## 0.65.6

PS3 et 360 : relance automatique dans un processus neuf après une sortie pendant le chargement.

## 0.65.5

Profil Personnalisé, menu des réglages en direct, bouton Redémarrer la console.

## 0.65.4

360 : shaders compilés de façon synchrone (test).

## 0.65.3

360 : relecture rapide en Performance et Équilibré (image noire corrigée).

## 0.65.2

Moteur Xbox 360 recompilé (correctif du son 360 embarqué).

## 0.65.1

Transition Washi ↔ Tsuru par thème, pilules collées aux barres, ATH CPU/GPU en degrés, profils PS3/360 recalés.

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

## Toutes les versions

⭐ = mise à jour majeure.

- [**0.62.5**](https://github.com/orikami-dev/origami/releases/tag/v0.62.5) : bouton Format sans plantage, manettes prédéfinies de retour, Accessibilité et Mises à jour dans les réglages, Sakura et Ambre refaites, liseré qui respire, sons du thème au doigt, ATH aux couleurs du téléphone, profils calés sur le Fold 8.
- [**0.62.4**](https://github.com/orikami-dev/origami/releases/tag/v0.62.4) : son PS3 sans grésillement ni saturation, Demon's Souls calé à 30 images/s, ATH PS3/360 complet (moyenne, pic, mémoire réelle).
- [**0.62.3**](https://github.com/orikami-dev/origami/releases/tag/v0.62.3) : gâchettes en haricots façon GameCube, Format et ×2 en icônes de coin, musique dès le choix du thème, sonar sans ligne fixe, éditeur de manette allégé.
- [**0.62.2**](https://github.com/orikami-dev/origami/releases/tag/v0.62.2) : moteurs PS3/360 qui plantaient au lancement, sons seulement en mode sonnerie, curseurs de volume, fonds animés.
- [**0.62.1**](https://github.com/orikami-dev/origami/releases/tag/v0.62.1) : fenêtres 360 validées seules (DMC 4), jaquettes 360, taille de manette corrigée, catégorie Son, flèche retour, musique sans coupure, nouvelle musique Écarlate, emblèmes Remote Play.
- ⭐ [**0.62.0**](https://github.com/orikami-dev/origami/releases/tag/v0.62.0) : sons et musique par thème, assistant simple ou avancé, logo animé, Remote Play par plateforme, jaquettes 360, gâchettes en arc, touche FORMAT, menus Home PS3/360 enrichis, sauvegarde rapide PS3, décors PS3 sans interpréteur, profil joueur 360 (DMC 4), journal en un seul fichier.
- [**0.60.14**](https://github.com/orikami-dev/origami/releases/tag/v0.60.14) : profils et réglages PS3 et Xbox 360 dans le menu Home, ATH depuis le menu.
- [**0.60.13**](https://github.com/orikami-dev/origami/releases/tag/v0.60.13) : Xbox 360 : Skyrim corrigé, son AAudio (DMC 4), images/s dans l'ATH.
- [**0.60.12**](https://github.com/orikami-dev/origami/releases/tag/v0.60.12) : (19/10/2026)
- [**0.60.11**](https://github.com/orikami-dev/origami/releases/tag/v0.60.11) : Xbox 360 : le réglage qui coupe le cache d'instructions n'était jamais pris en compte (passé en texte alors que le moteur attend un…
- [**0.60.10**](https://github.com/orikami-dev/origami/releases/tag/v0.60.10) : Xbox 360 : la vraie cause du SIGBUS est le moteur qui crée executable_addr_flags.bin à 0 octet puis écrit dedans (le stockage n'y est…
- [**0.60.9**](https://github.com/orikami-dev/origami/releases/tag/v0.60.9) : tentative Xbox 360 (insuffisante). Les deux jeux plantaient en SIGBUS dans libxenia-app.so (XexInfoCache::Init, après le chargement des…
- [**0.60.8**](https://github.com/orikami-dev/origami/releases/tag/v0.60.8) : correctif des jeux PS3 et Xbox 360 qui revenaient à l'accueil. Cause trouvée dans le logcat du Fold (14 plantages en 0.60.6) :…
- [**0.60.7**](https://github.com/orikami-dev/origami/releases/tag/v0.60.7) : l'accueil a sa propre manette, réservée à la navigation (ConsolePad.HOME_PAD : croix, A/B/X/Y, L/R, Home ; ni sticks, ni L2/R2, ni…
- [**0.60.6**](https://github.com/orikami-dev/origami/releases/tag/v0.60.6) : Remote Play est rangé par constructeur (PlayStation, Xbox, PC et cloud ; Nintendo n'a pas de jeu à distance). Chaque section finit par «…
- [**0.60.5**](https://github.com/orikami-dev/origami/releases/tag/v0.60.5) : deux nouvelles catégories au bout du XMB . « Jeux mobiles » regroupe les jeux Android installés (ceux que leur éditeur déclare comme jeu…
- [**0.60.4**](https://github.com/orikami-dev/origami/releases/tag/v0.60.4) : la façade de la manette a son propre fond, pendant de celui de l'écran du haut . PadSkins.render : Codec onde de transmission (codec 1),…
- [**0.60.3**](https://github.com/orikami-dev/origami/releases/tag/v0.60.3) : manette Flex par console et import BIOS complet .
- [**0.60.2**](https://github.com/orikami-dev/origami/releases/tag/v0.60.2) : les emblèmes de consoles du XMB suivent le pack d'icônes .
- [**0.60.1**](https://github.com/orikami-dev/origami/releases/tag/v0.60.1) : demandes de BOSS du 19/10 midi.
- ⭐ [**0.60.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.60.0) : Xbox 360 intégrée (portage Android de Xenia).
- ⭐ [**0.50.1 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.50.1) : PS3 intégrée (ARMSX3).
- ⭐ [**0.50.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.50.0) : Thèmes sans redémarrage, en route vers la PS3.
- [**0.42.7**](https://github.com/orikami-dev/origami/releases/tag/v0.42.7) : voir la page
- [**0.42.6**](https://github.com/orikami-dev/origami/releases/tag/v0.42.6) : voir la page
- [**0.42.5**](https://github.com/orikami-dev/origami/releases/tag/v0.42.5) : voir la page
- [**0.42.4**](https://github.com/orikami-dev/origami/releases/tag/v0.42.4) : voir la page
- [**0.42.3**](https://github.com/orikami-dev/origami/releases/tag/v0.42.3) : voir la page
- [**0.42.2**](https://github.com/orikami-dev/origami/releases/tag/v0.42.2) : retours de BOSS sur la 0.42.1.
- [**0.42.1**](https://github.com/orikami-dev/origami/releases/tag/v0.42.1) : dans l'éditeur, une touche choisie se redimensionne par ses quatre coins (poignées vertes à chaque coin, le coin opposé reste fixe).
- [**0.42.0**](https://github.com/orikami-dev/origami/releases/tag/v0.42.0) : voir la page
- [**0.41.2**](https://github.com/orikami-dev/origami/releases/tag/v0.41.2) : emblèmes de console (XMB, grille, fiches) redessinés en silhouettes simplifiées de chaque machine, même style aplat + découpes + sigle…
- [**0.41.1**](https://github.com/orikami-dev/origami/releases/tag/v0.41.1) : grue (icône et animation de démarrage) redessinée en miroir exact autour du pli central, formes et couleurs ; seules les deux faces du…
- ⭐ [**0.41.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.41.0) : Réglages en deux volets et 10 langues.
- ⭐ [**0.40.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.40.0) : Accueil façon XMB et navigation à la manette partout.
- [**0.35.0**](https://github.com/orikami-dev/origami/releases/tag/v0.35.0) : retouche de la manette sur le jeu (Options contrôleur → « Modifier la manette ici » : jeu en pause, barre Ajouter / Retirer / − / + / ⇅…
- [**0.34.0**](https://github.com/orikami-dev/origami/releases/tag/v0.34.0) : manette superposée = touches seules (ControlDeckView : plus de pilule de fond, de moitiés, d'habillage ni d'image de fond quand overlay,…
- [**0.33.0**](https://github.com/orikami-dev/origami/releases/tag/v0.33.0) : menu pause rangé (Reprendre, sauvegarde/chargement rapides, Réglages de l'émulateur, Options contrôleur, Sauvegardes, Codes de triche,…
- [**0.32.0**](https://github.com/orikami-dev/origami/releases/tag/v0.32.0) : tous les pliants. Posture Flex détectée selon le sens de la charnière (MainActivity.hingeHorizontal) : clapets (Z Flip, Razr, écran…
- [**0.31.0**](https://github.com/orikami-dev/origami/releases/tag/v0.31.0) : script d'installation des cœurs (assets/cores/install.json lu par CoreInstaller), lancé tout seul après chaque téléchargement (en jeu ou…
- [**0.30.0**](https://github.com/orikami-dev/origami/releases/tag/v0.30.0) : les émulateurs externes disparaissent de l'interface (pages et liens Play Store retirés des Réglages, de l'assistant et du choix…
- ⭐ [**0.29.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.29.0) : L'app devient Origami.
- [**0.28.0**](https://github.com/orikami-dev/origami/releases/tag/v0.28.0) : illustrations de fond façon accueil PS5 (Backdrop.java, CoverFetcher.artwork) : quand le curseur se pose 600 ms sur un jeu, la scène…
- [**0.27.0**](https://github.com/orikami-dev/origami/releases/tag/v0.27.0) : Réglages > Émulateurs réorganisé en quatre blocs : Consoles (une ligne par console avec moteur, état et BIOS ; un menu codec par console…
- [**0.26.0**](https://github.com/orikami-dev/origami/releases/tag/v0.26.0) : menu Maison « Options contrôleur » (au lieu de « Modifier la manette ») : manette tactile (éditeur, mode paysage superposé ou séparé,…
- ⭐ [**0.25.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.25.0) : GameCube et Wii intégrées (Dolphin).
- [**0.24.0**](https://github.com/orikami-dev/origami/releases/tag/v0.24.0) : accéléré ×2 (au lieu de ×3). Sur PS2 il ne faisait que couper le son, car ARMSX2 se cale sur son propre limiteur : le pont répond…
- ⭐ [**0.23.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.23.0) : PS2 intégrée (ARMSX2).
- [**0.22.3**](https://github.com/orikami-dev/origami/releases/tag/v0.22.3) : correctifs de la série 0.22 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.22.0 pour les…
- [**0.22.2**](https://github.com/orikami-dev/origami/releases/tag/v0.22.2) : correctifs de la série 0.22 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.22.0 pour les…
- [**0.22.1**](https://github.com/orikami-dev/origami/releases/tag/v0.22.1) : correctifs de la série 0.22 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.22.0 pour les…
- [**0.22.0**](https://github.com/orikami-dev/origami/releases/tag/v0.22.0) : console « Arcade » (cœur fbneo), reconnue par le dossier (arcade/, fbneo/, neogeo/, mame/, cps1-3/) ; jaquettes « FBNeo - Arcade Games…
- [**0.21.2**](https://github.com/orikami-dev/origami/releases/tag/v0.21.2) : correctifs de la série 0.21 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.21.0 pour les…
- [**0.21.1**](https://github.com/orikami-dev/origami/releases/tag/v0.21.1) : correctifs de la série 0.21 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.21.0 pour les…
- ⭐ [**0.21.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.21.0) : La 3D : N64, PSP et Dreamcast en OpenGL ES.
- [**0.20.3**](https://github.com/orikami-dev/origami/releases/tag/v0.20.3) : correctifs de la série 0.20 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.20.0 pour les…
- [**0.20.2**](https://github.com/orikami-dev/origami/releases/tag/v0.20.2) : correctifs de la série 0.20 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.20.0 pour les…
- [**0.20.1**](https://github.com/orikami-dev/origami/releases/tag/v0.20.1) : correctifs de la série 0.20 (le détail de cette version intermédiaire n'a pas été conservé dans les notes ; voir 0.20.0 pour les…
- [**0.20.0**](https://github.com/orikami-dev/origami/releases/tag/v0.20.0) : assistant de démarrage au premier lancement (SetupActivity : bienvenue, dossier de jeux, installation de tous les moteurs intégrés…
- [**0.19.0**](https://github.com/orikami-dev/origami/releases/tag/v0.19.0) : disposition Flex verrouillée (ni ajout, ni suppression, ni taille touche par touche) ; taille globale des moitiés (splitScale 60 à 130…
- [**0.18.0**](https://github.com/orikami-dev/origami/releases/tag/v0.18.0) : vrais onglets dans l'éditeur (barre fixe soulignée, seule la page défile). Option « Interface aux mêmes couleurs » (Theme.sync : accent…
- [**0.17.0**](https://github.com/orikami-dev/origami/releases/tag/v0.17.0) : disposition divisée en surimpression. Téléphone ouvert à plat (charnière > 150° ou capteur absent) et tenu en paysage, le jeu prend tout…
- [**0.16.0**](https://github.com/orikami-dev/origami/releases/tag/v0.16.0) : prédisposition « Flex divisée » (ControlLayout.flex, kind "flex", split) façon clavier divisé Samsung : une moitié par bord avec son…
- [**0.15.0**](https://github.com/orikami-dev/origami/releases/tag/v0.15.0) : disposition par défaut refaite par BOSS (grille 43 × 28 : L2/L1 et R2/R1 empilés, SAVE/SELECT, Maison, Accélérer, LOAD/START en haut au…
- [**0.14.0**](https://github.com/orikami-dev/origami/releases/tag/v0.14.0) : les touches ne se chevauchent plus, sur les deux écrans et dans les deux sens. ControlLayout.arrange regroupe les touches qui se…
- [**0.13.0**](https://github.com/orikami-dev/origami/releases/tag/v0.13.0) : la manette garde ses proportions d'un écran à l'autre (chaque disposition retient la grille où elle a été dessinée ; tailles mises à…
- [**0.12.0**](https://github.com/orikami-dev/origami/releases/tag/v0.12.0) : bibliothèque rangée par console (dans « Tous » aussi) et en-tête « CATÉGORIE › CONSOLE ». Disposition par défaut refaite par BOSS (L1/R1…
- [**0.11.0**](https://github.com/orikami-dev/origami/releases/tag/v0.11.0) : la fiche montre le synopsis (section Synopsis/Scénario/Trame, Plot en anglais) plutôt que l'introduction de l'article. La croix ne sert…
- [**0.10.0**](https://github.com/orikami-dev/origami/releases/tag/v0.10.0) : menu Maison en jeu enrichi. Format d'affichage par console (origine, 4:3, 16:9, étiré, pixels entiers ; image lissée ou pixels nets).…
- [**0.9.0**](https://github.com/orikami-dev/origami/releases/tag/v0.9.0) : fiche du jeu à gauche en vue Flex (nom, console, année, taille du fichier, résumé) tirée de Wikipédia FR puis EN, année via Wikidata,…
- ⭐ [**0.8.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.8.0) : Moteur d'émulation intégré : les jeux tournent dans Origami, manette en bas.
- [**0.7.0**](https://github.com/orikami-dev/origami/releases/tag/v0.7.0) : manette du bas entièrement configurable. Icône manette (à gauche de l'engrenage) : éditeur sur la vraie moitié basse, avec grille grise…
- [**0.6.0**](https://github.com/orikami-dev/origami/releases/tag/v0.6.0) : 21 consoles de la NES à la Switch (PS2, GameCube, Wii, 3DS, Vita, Dreamcast, Saturn ajoutées), tri des jeux par dossier (y compris…
- [**0.5.0**](https://github.com/orikami-dev/origami/releases/tag/v0.5.0) : carrousel en haut à droite avec noms, gâchettes L/R, Select/Home/Start alignés.
- [**0.4.0**](https://github.com/orikami-dev/origami/releases/tag/v0.4.0) : lancement sans le sélecteur Android générique (détection des émulateurs installés qui savent ouvrir la console, une seule fenêtre si…
- [**0.3.0**](https://github.com/orikami-dev/origami/releases/tag/v0.3.0) : emblème de console original sur fond transparent (plus de cartouche en vedette), croix directionnelle complète (◀ ▶ jeu sans boucler, ▲…
- [**0.2.0**](https://github.com/orikami-dev/origami/releases/tag/v0.2.0) : (retours de BOSS) : vue Flex par défaut sur tous les écrans, icônes en haut (vue Flex/grille, dossier, options), plus de jeux de démo…
- ⭐ [**0.1.0 · Mise à jour majeure**](https://github.com/orikami-dev/origami/releases/tag/v0.1.0) : Premier build : lanceur rétro pensé pour le Galaxy Fold.
