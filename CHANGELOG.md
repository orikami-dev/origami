# 📝 Journal des versions

Studio : KAMI-WORKS.

## 0.65.16
- PS3 et Xbox 360 en portrait : filet entre l'écran et la manette réduit à 4 dp, centré sur la charnière, comme les autres consoles.

## 0.65.15
- Jeux PS3 et Xbox 360 en portrait : l'écran du jeu et la manette sont coupés exactement au milieu, là où passe la charnière, et chacun remplit sa moitié. Le paysage reste en plein écran.

## 0.65.14
- Accueil en portrait : la pastille du haut retrouve ses coins ronds et commence sous la barre d'état ; en paysage, plus aucune pastille, plein écran.
- Astuces retirées de l'app (accueil et Réglages).

## 0.65.13
- Après une mise à jour, une notification « Origami <version> installé » permet de rouvrir l'app d'un geste.

## 0.65.12
- Le canal des builds de test s'appelle « Préversions » (Réglages > Mises à jour) ; « bêta » reste réservé à la future bêta publique.

## 0.65.11
- Préversions dans l'app (Réglages > Mises à jour > Préversions) : les nouvelles versions apparaissent seules, avec notification.
- Format et mode : le message précédent est remplacé tout de suite, plus de notifications qui s'enchaînent en retard.
- PS3 et Xbox 360 : l'écran et la manette se replacent d'après la hauteur réelle de la scène (plus de chevauchement au pliage).
- Xbox 360 : le menu Codec met maintenant le jeu en pause.
- Lanceur d'apps : curseur de défilement rapide et bouton de tri (nom A-Z, Z-A, installées récemment, mises à jour récemment, jeux d'abord), aux couleurs du thème.
- Barres de notification et de navigation transparentes aussi dans l'assistant de démarrage, l'éditeur de manette et l'import.

## Ce que fait Origami aujourd'hui

**Lanceur**
- Accueil façon XMB en mode Tsuru (téléphone à moitié plié) : catégories par console, jeux en colonne, fiche du jeu, illustrations de fond larges.
- Mode Washi sur l'écran externe : lanceur d'apps Android en grille, option lanceur par défaut.
- Catégories « Jeux mobiles » (jeux Android installés) et « Remote Play » (clients PlayStation, Xbox, Steam Link par constructeur, « Ajouter un client… »).
- Jaquettes automatiques et jaquette manuelle, synopsis officiels courts.
- Tous les pliants et tous les ratios, changements à chaud ; navigation complète à la manette ; menus Codec partout.
- Réglages en deux volets, 10 langues (fr, en, es, de, it, pt-BR, ru, ja, ko, zh), langue système automatique.
- Cinq thèmes (Codec, Arctique, Écarlate, Sakura, Ambre) appliqués sans redémarrage, avec fond, fond de manette, icône et pack d'icônes assortis.

**Émulation intégrée**
- Consoles 8/16/32 bits, arcade (FBNeo), PS1 (HD si BIOS), N64, PSP, Dreamcast en OpenGL ES.
- PS2 (ARMSX2), GameCube et Wii (Dolphin), PS3 (ARMSX3), Xbox 360 (Xenia).
- Profils Performance, Équilibré, Qualité qui règlent chaque cœur.
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

## Toutes les versions

⭐ = mise à jour majeure.

- 0.65.10 : jeux PS3 et Xbox 360 en portrait avec bande d'état, écran du jeu et manette en deux pastilles.
- 0.65.9 : menu Profil et réglages sans blocage, barres Tsuru aux couleurs du thème, formats d'image centrés.
- 0.65.8 : PS3, le jeu démarre enfin (plus d'écran noir).
- 0.65.7 : une sortie voulue d'un moteur n'est plus signalée comme un plantage.
- 0.65.6 : PS3 et Xbox 360, relance automatique du jeu après une sortie pendant le chargement.
- 0.65.5 : profil « Personnalisé », menu Profil et réglages en direct, bouton « Redémarrer la console ».
- 0.65.4 : Xbox 360, compilation synchrone des shaders (DMC 4).
- 0.65.3 : Xbox 360, relecture des images rapide (plus d'image noire sur DMC 4).
- 0.65.2 : Xbox 360, correctif du son inclus.
- 0.65.1 : transition Washi/Tsuru animée, ATH avec températures CPU et GPU séparées, profils PS3 recalés.
- 0.65.0 : bouton Format sans plantage, menus Codec alignés droitier/gaucher, catégorie Monitoring, manette 2D rétablie.
- 0.62.5 : bouton Format sans plantage, manettes prédéfinies de retour, Accessibilité et Mises à jour dans les réglages, Sakura et Ambre refaites, liseré qui respire, sons du thème au doigt, ATH aux couleurs du téléphone.
- 0.62.4 : son PS3 sans grésillement ni saturation, Demon's Souls calé à 30 images/s, ATH PS3/360 complet.
- 0.62.3 : gâchettes en haricots façon GameCube, Format et ×2 en icônes de coin, musique dès le choix du thème, éditeur de manette allégé.
- 0.62.2 : moteurs PS3/360 qui plantaient au lancement, sons seulement en mode sonnerie, curseurs de volume, fonds animés.
- 0.62.1 : fenêtres 360 validées seules (DMC 4), jaquettes 360, catégorie Son, flèche retour, musique sans coupure, nouvelle musique Écarlate, emblèmes Remote Play.
- ⭐ 0.62.0 : sons et musique par thème, assistant simple ou avancé, logo animé, Remote Play par plateforme, jaquettes 360, gâchettes en arc, touche FORMAT, menus Home PS3/360 enrichis, sauvegarde rapide PS3, profil joueur 360 (DMC 4).
- 0.60.14 : profils et réglages PS3 et Xbox 360 dans le menu Home, ATH depuis le menu.
- 0.60.13 : Xbox 360 : Skyrim corrigé, son, images/s dans l'ATH.
- 0.60.12 : ATH façon RTSS sur PS3 et Xbox 360.
- 0.60.11 : Xbox 360 : plantage au chargement corrigé.
- 0.60.10 : Xbox 360 : cache d'instructions coupé pour éviter le plantage au chargement.
- 0.60.9 : Xbox 360 : premier correctif du plantage au chargement.
- 0.60.8 : correctif des jeux PS3 et Xbox 360 qui revenaient à l'accueil.
- 0.60.7 : manette d'accueil réservée à la navigation, L3/R3 au double appui.
- 0.60.6 : Remote Play rangé par constructeur, jaquettes PS3 et Xbox 360, « Choisir la jaquette », lanceur d'apps en grille.
- 0.60.5 : catégories « Jeux mobiles » et « Remote Play » au bout du XMB.
- 0.60.4 : fond de façade de manette par thème, aperçu de la Flex dans l'assistant, logo Origami = applis du téléphone.
- 0.60.3 : manette Flex par console et import BIOS complet.
- 0.60.2 : les emblèmes de consoles du XMB suivent le pack d'icônes.
- 0.60.1 : fond Ambre « soleil Art déco », packs d'icônes, Origami comme lanceur par défaut.
- ⭐ 0.60.0 : Xbox 360 intégrée (portage Android de Xenia).
- ⭐ 0.50.1 : PS3 intégrée (ARMSX3).
- ⭐ 0.50.0 : thèmes sans redémarrage, nouveaux fonds facettés.
- 0.42.7 : icône de l'app aux couleurs du thème, fond de manette par thème.
- 0.42.6 : couleurs de la manette dans le menu Thème, couleur du logo, un fond par thème.
- 0.42.5 : thème partout, manettes fournies redessinées, grue affinée.
- 0.42.4 : accueil en paysage plein écran, bouton Thème.
- 0.42.3 : profils d'émulation plus complets, Mini en fenêtre flottante, PS2 à la définition choisie.
- 0.42.2 : Mini et Flex en plein écran, 2D et 3D conservées en paysage.
- 0.42.1 : redimensionnement d'une touche par ses quatre coins.
- 0.42.0 : manettes 2D, 3D, Flex et Mini ; XMB plus fluide.
- 0.41.2 : emblèmes de console redessinés en silhouettes simplifiées.
- 0.41.1 : grue redessinée en miroir autour du pli central.
- ⭐ 0.41.0 : réglages en deux volets et 10 langues.
- ⭐ 0.40.0 : accueil façon XMB et navigation à la manette partout.
- 0.35.0 : retouche de la manette sur le jeu, nouvelle animation de démarrage.
- 0.34.0 : manette superposée = touches seules.
- 0.33.0 : menu pause rangé, options du cœur par thème, navigation manette de l'accueil refaite.
- 0.32.0 : tous les pliants (clapets et livres).
- 0.31.0 : script d'installation des cœurs, profils selon l'appareil, choix de version des cœurs.
- 0.30.0 : émulateurs externes retirés de l'interface, onglet Cœurs dans les réglages.
- ⭐ 0.29.0 : l'app devient Origami.
- 0.28.0 : illustrations de fond façon accueil PS5.
- 0.27.0 : Réglages > Émulateurs en quatre blocs, accueil plein écran avec manette physique.
- 0.26.0 : menu « Options contrôleur », manette superposée par défaut en paysage, mode débogage.
- ⭐ 0.25.0 : GameCube et Wii intégrées (Dolphin), export des sauvegardes.
- 0.24.0 : accéléré ×2 effectif sur PS2, import de sauvegardes.
- ⭐ 0.23.0 : PS2 intégrée (ARMSX2), ATH façon RTSS.
- 0.22.3 : jaquettes ScreenScraper en secours.
- 0.22.2 : bouton « Actualiser la bibliothèque ».
- 0.22.1 : ATH de monitoring, NetherSX2/AetherSX2 sans plantage.
- 0.22.0 : console Arcade (FBNeo), PS1 en HD.
- 0.21.2 : joystick N64/PSP/Dreamcast sans croix parasite.
- 0.21.1 : retéléchargement des cœurs, éditeur plus précis.
- ⭐ 0.21.0 : la 3D : N64, PSP et Dreamcast en OpenGL ES.
- 0.20.3 : bouton Maison agrandi sur la Flex.
- 0.20.2 : aperçu en direct des manettes dans l'assistant.
- 0.20.1 : bulles de filtre espacées.
- 0.20.0 : assistant de démarrage au premier lancement.
- 0.19.0 : disposition Flex verrouillée, habillage Codec en relief.
- 0.18.0 : vrais onglets dans l'éditeur, manettes physiques jusqu'à 4 joueurs.
- 0.17.0 : disposition divisée en surimpression.
- 0.16.0 : disposition « Flex divisée ».
- 0.15.0 : disposition par défaut refaite, roue de couleurs, vibration réglable.
- 0.14.0 : touches qui ne se chevauchent plus, menus codec partout.
- 0.13.0 : manette qui garde ses proportions, section STYLE, menus façon codec.
- 0.12.0 : bibliothèque rangée par console, synopsis officiels en français.
- 0.11.0 : synopsis dans la fiche, croix de navigation, A/B/X/Y cohérents.
- 0.10.0 : format d'affichage par console, options avancées du cœur, codes de triche.
- 0.9.0 : fiche du jeu en vue Flex, nouvelles touches, deux pilules.
- ⭐ 0.8.0 : moteur d'émulation intégré, les jeux tournent dans Origami.
- 0.7.0 : manette du bas entièrement configurable.
- 0.6.0 : 21 consoles de la NES à la Switch, tri par dossier et par disque, pages Réglages.
- 0.5.0 : carrousel en haut à droite, gâchettes L/R.
- 0.4.0 : lancement sans sélecteur Android, jaquettes automatiques.
- 0.3.0 : croix directionnelle complète, boutons A/B/X/Y en losange, bouton maison.
- 0.2.0 : vue Flex par défaut, écran titre façon Metal Gear.
- ⭐ 0.1.0 : premier lanceur rétro pensé pour le Galaxy Fold.
