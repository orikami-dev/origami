# 📝 Journal des versions

Studio : KAMI-WORKS.

## 0.67.3

- Précompilation PS3 : dès que tu quittes le plein écran (autre app, écran d'accueil, fenêtre réduite), une notification apparaît avec la charge de compilation (code du jeu et shaders), la taille du cache et la durée ; elle disparaît quand tu reviens dans le jeu.

## 0.67.2

- Mode développeur : les 10 appuis sur le logo sont pris en compte même très rapides, et le compteur « Encore N appuis » se met à jour à chaque appui, dès le 3e.
- Turnip : le pilote est choisi d'après la génération Adreno citée dans le nom du fichier (Gen7, Gen8…).
- Sauvegarde et chargement rapides : des pictogrammes (disquette, plateau) aux couleurs du thème remplacent les mots SAVE et LOAD.
- Précompilation PS3 : l'écran reste allumé au minimum de luminosité pendant la compilation, car sans écran le moteur ne démarrait pas son rendu.

## 0.67.1

- PS3 : le téléchargement de Turnip détecte le GPU du téléphone (tous les Snapdragon Adreno, pas seulement les Fold) et choisit le pilote adapté ; les téléphones sans Adreno gardent leur pilote.

## 0.67.0 ⭐

- Le débogage, les préversions et le contenu expérimental sont désormais cachés : appuie 10 fois sur le logo dans Réglages > À propos pour les débloquer.
- PS3 : la précompilation des jeux utilise tous les cœurs du processeur, avec deux jauges séparées (code du jeu et shaders) et un chrono qui s'arrête à la fin.
- Précompilation PS3 : à la fin, la notification de l'écran verrouillé change de texte, sonne et rallume l'écran.
- Journal : modules compilés et fréquences des cœurs.
- Débogage : essai pas à pas pour l'écran noir de certains jeux Xbox 360.

## 0.66.35

- Précompilation PS3 (essai) : le jeu peut continuer de compiler écran éteint, puis se met en pause à la fin ; le téléphone se met alors en veille normalement.

## 0.66.34

- Réglages : la section Débogage est maintenant dans Monitoring.
- Réglages > Monitoring > Débogage : « Précompilation PS3 », pour laisser compiler écran dim puis mettre le jeu en pause à la fin, avec la durée exacte.

## 0.66.33

- Mises à jour : la recherche de préversion ne peut plus renvoyer une ancienne réponse gardée par le réseau.
- Profil de test : précision des minuteries « All Timers » (essai pour raccourcir la compilation de MGS4) ; la mesure « Shaders » compte aussi la compilation du code du jeu.
- ATH et journal : durée de la session (chronomètre), à activer dans Réglages > Monitoring.

## 0.66.32

- Profil de test (usage interne) : le son tient mieux pendant les ralentis du jeu (tampon 150 ms, étirement du temps plus précoce).
- Profil de test : la compilation des shaders peut utiliser tous les cœurs (6 fils).
- Réglages > Débogage : bouton pour vider le cache de shaders PS3 (mesures à froid).
- Mise à jour : le poids de la version et la vitesse de téléchargement s'affichent pendant le téléchargement.

## 0.66.31

- PS3 : nouveaux réglages « Son » (tampon de 60 à 250 ms, étirement du temps) pour régler le décalage avec l'image.
- ATH : nouvelle mesure « Shaders (PS3) » (compilation en cours, taille du cache), à activer dans Réglages > Monitoring.
- Réglages > Débogage : profil de test (usage interne) qui regroupe les pistes d'optimisation PS3 en essai.

## 0.66.30

- PS3 : nouveaux réglages « Son » (tampon de 60 à 250 ms, étirement du temps) pour régler le décalage avec l'image.
- ATH : nouvelle mesure « Shaders (PS3) » (compilation en cours, taille du cache), à activer dans Réglages > Monitoring.
- Réglages > Débogage : profil de test (usage interne) qui regroupe les pistes d'optimisation PS3 en essai.

## 0.66.29

- Journal de débogage : la session PS3 et Xbox 360 est écrite en direct dans un fichier à part, et « Copier le journal » la joint en entier.

## 0.66.28

- La répartition des cœurs passe par défaut sur Économie : le téléphone chauffe moins en PS3.
- Journal de débogage PS3 enrichi (images par seconde, images lentes, sortie son) pour régler le son et la fluidité.

## 0.66.27

- Corrections et réglages de stabilité pour la PS3 et la Xbox 360.

## 0.66.26

- « Copier le journal » dépose un fichier .txt complet dans le presse-papier, à coller directement dans une conversation.
- Le journal est aussi écrit dans Origami/LOGS.

## 0.66.25

- PS3 : nouveau réglage « Pilote graphique » (Auto ou Turnip), pour les jeux qui plantent dans le pilote du téléphone.
- Réglages > Monitoring : bouton pour télécharger le dernier pilote Turnip.

## 0.66.24 ⭐

- Fond Torii refait : la pagode vermillon allumée sur son mont, un petit torii et ses lanternes, la lune rouge en facettes, des feuilles d'érable qui tombent.
- Hanami : un grand cerisier en fleurs au premier plan du fond d'écran.
- Manette Hanami refaite : le pont de lune et son reflet, des branches de cerisier, de l'eau qui ondule et des pétales.
- Réglages du XMB : la grande bulle de droite qui répétait le réglage choisi est retirée.
- Lanceur d'applications : la liste n'est plus coupée en bas.
- Dossier Origami créé à la racine du téléphone (Origami/GAMES pour les jeux avec GAMES/DLC, Origami/BIOS pour les BIOS), proposé par l'assistant de démarrage avec l'accès aux fichiers.
- PS3 et Xbox 360 : nouveau réglage « Répartition des cœurs » (Performance par défaut) : son, rendu et jeu sur les gros cœurs, compilation des shaders sur les cœurs moyens ; mode Économie pour moins chauffer.
- Alerte quand le téléphone surchauffe en jeu.

## 0.66.22

- Manette Torii refaite : grand portique vermillon, lanternes et braises qui montent.
- Hanami : nouvelle eau du fond d'écran, douce, avec reflets de lune et pétales qui dérivent.
- Descriptions des thèmes réécrites, une phrase pour chacun.
- PS3 : définition par défaut en 720p en Performance et 1080p en Équilibré (Demon's Souls compris).
- Toucher le vide ne fait plus de bruit, quel que soit le thème.

## 0.66.21 ⭐

- Thèmes renommés : Vigie, Aurore, Torii, Hanami, Mégapole, Averse, Brume et Clair de lune, avec des descriptions à jour.
- Manettes : radar à un seul faisceau avec échos rouges qui clignotent, Mégapole refaite (tour, tours de verre, projecteurs), gouttes qui font des ronds sur Averse, temple de Brume plus détaillé.
- Hanami : l'eau du fond d'écran refaite (facettes, éclats de lune, rides).
- Nouvelles touches Capture d'écran et Vidéo sur toutes les manettes.
- Sauvegarde et chargement rapides retirés des menus (les touches de la manette suffisent).
- Xbox 360 : le menu Home ne fige plus le jeu, shaders en arrière-plan rétablis.

## 0.66.20

- Mises à jour : le détail (version installée, stable, préversion) s'affiche sous « Origami est à jour ».

## 0.66.19 ⭐

- Manettes animées pour tous les thèmes : ondes sur l'eau (Argent), neige, braises, pétales, phares, pluie, lucioles, balayage radar.

## 0.66.18

- Titres des menus dans la police du thème.
- Menus de l'accueil sur fond plein, sans la liste des jeux en transparence.

## 0.66.17

- Nouvel écran de démarrage : les plis de la grue se dessinent, puis elle se colore au centre.
- Thème Argent : plus de grues sur le fond ni sur la manette ; étoiles filantes et brume devant la lune.

## 0.66.16

- XMB : plus de texte dans les icônes, sauf les consoles et Remote Play.

## 0.66.15

- Nouvelle grue partout : fonds animés, manette, joystick, écran de démarrage.
- Interrupteurs des Réglages plus coupés.
- Liste des applications sur fond plein.
- PS3 : cache de shaders sur disque toujours actif (shaders recompilés au chargement).

## 0.66.14

- Une police par thème : Exo 2, Cinzel, Quicksand, Marcellus, Orbitron, Josefin Sans, Jura.
- PS3 : moins de compilations de shaders en parallèle pour éviter un plantage du pilote au démarrage.

## 0.66.13 ⭐

- Nouveau logo : la grue en vol, angles arrondis, contours, ombres et lumières.
- Nouvelles icônes du lanceur, une par thème.
- Bec de la grue en jaune.

## 0.66.12

- « Mises à jour et À propos » devient « Version ».

## 0.66.11

- Plus de texte sous l'icône Réglages du XMB.

## 0.66.10

- Son de Demon's Souls : tampon plus long, sans étirement du temps (moins de distorsion).

## 0.66.9

- Son du premier appui : plus de lecture avant chargement.
- Grues du thème Argent : ailes qui pivotent à la racine.

## 0.66.8

- XMB en mode Tsuru : la catégorie « Paramètres » devient « Réglages », son nom ne se coupe plus.
- Argent : les grues du fond battent des ailes plus doucement, ailes toujours du bon côté, le corps monte quand elles redescendent.

## 0.66.7

- Trois nouveaux thèmes complets : Néon (ruelle de Tokyo sous la pluie), Jade (temple de montagne dans la brume) et Argent (grues en origami sous la pleine lune), avec leurs fonds, manettes, icônes d'appli, bruitages et musiques.
- Le thème Codec devient Radar ; sa tour a une antenne qui tourne, un feu clignotant et des fenêtres qui s'allument.
- Ambre : nouveau fond, une métropole japonaise de nuit avec la tour de Tokyo.
- Écarlate : colline agrandie, pagode posée sur son socle, forêt de conifères au fond. Sakura : pont posé sur ses berges. Jade : vrai vert jade, temple et escalier posés sur le relief.
- Nouvelles musiques de thème, composées ou réarrangées pour chacun des huit thèmes.
- XMB en mode Tsuru : les entrées de « Paramètres » s'ouvrent dans le XMB, façon PS3, avec une icône propre à chaque thème ; le menu classique reste celui du mode Washi.
- Paramètres réordonnés et redécrits ; « Mises à jour » et « À propos » ne forment plus qu'une page.
- « Assombrir le fond » remplace l'effet cathodique, éteint par défaut.
- Bulle de description et cartes des menus moins sombres.

## 0.66.6

- Nouveaux fonds d'écran pour Codec (base radar), Arctique (nuit polaire), Écarlate (Japon sous la lune rouge) et Sakura (rivière et pont).
- Nouveaux fonds de manette assortis pour ces quatre thèmes.
- XMB en mode Tsuru : nouvelle catégorie « Paramètres », à gauche de « Tous », qui regroupe Thème, Manette et Réglages.
- Le logo Origami fait passer de Tsuru à Washi ; nouvelle icône « Applications » pour le lanceur d'apps.
- Profils : bouton « Réglages avancés » accessible depuis l'accueil, hors jeu.
- Pliage : plus d'étiquettes déformées, et la manette reste transparente en paysage.

## 0.66.5

- Andon devient Lumen.
- Menu en jeu : nouveau sous-menu « Affichage » qui regroupe l'ATH, le format d'image et Lumen.
- « Redémarrer le jeu » relance la partie sur place, sans repasser par l'accueil.
- Profils : Qualité est retiré, il reste Performance, Équilibré et Personnalisé, refaits pour chaque système. Performance par défaut à partir de la PS3.
- Bouton Home sur un jeu : seulement l'émulateur, le profil et les réglages du jeu.
- Remote Play : le client choisi par défaut s'ouvre directement, Home permet d'en changer ; icônes sans lettre.
- Ambre : nouveau fond d'écran, une ville Art déco de nuit avec projecteurs.
- Les skins des manettes restent visibles, Lumen allumé ou non.

## 0.66.4

- Mises à jour : vérification automatique toutes les 6 heures, avec une fenêtre codec « Installer » ou « Installer ultérieurement » (redemande après 24 heures) et une barre de téléchargement aux couleurs du thème.
- Bouton Home agrandi sur toutes les manettes.
- Andon éteint : plus de fond coloré derrière les manettes, seules les touches restent.
- Réglages : l'option devient « Mode Washi par défaut », et l'accueil s'ouvre en Washi par défaut.

## 0.66.3

- Assistant de démarrage : plus de fond visible derrière la barre de notifications et la barre de navigation.

## 0.66.2

- Menus et Réglages : seul l'interrupteur réagit au toucher, plus la ligne entière, pour éviter les appuis involontaires. À la manette, la ligne sélectionnée bascule toujours.

## 0.66.1

- PS3 : moteur mis à jour (ARMSX3 1.3.1), avec les derniers correctifs des SPU (boîtes aux lettres, réveil des fils) et du rendu Vulkan.

## 0.66.0

- Interrupteurs redessinés aux couleurs de chaque thème, dans les Réglages comme dans les menus codec.
- Andon : la lumière est enfermée dans un contour propre à chaque thème (Codec, Arctique, Écarlate, Sakura, Ambre).
- Nouvelle option Réglages > Affichage > « Pastilles » : coins arrondis ou écran bord à bord. Sans pastilles, Andon est désactivé et grisé.
- Manette de l'accueil : L et R retirés.
- PS3 : boucles d'attente SPU détectées pour libérer le processeur, caches de code forcés, envoi GPU rapide, tampon son de 200 ms contre les coupures pendant les ralentis.
- Mode débogage : charge de chaque fil du moteur relevée toutes les 2 s et jointe à « Copier le journal ».

## 0.65.30
- Assistant de démarrage : les coins ne laissent plus voir le fond derrière.

## 0.65.29
- Journal : le bouton « Copier le journal » retire le bruit système et garde les lignes utiles ; la musique n’inonde plus le journal.

## 0.65.28
- Bibliothèque : l’entrée « Actualiser la bibliothèque » de la catégorie Tous est retirée, le geste « tirer pour actualiser » la remplace.

## 0.65.27
- Bibliothèque en mode Washi : tire vers le bas pour actualiser la liste des jeux.

## 0.65.26
- Réglages > Débogage : nouveau bouton « Copier le journal », un clic pour copier la fin du journal et la coller dans une conversation.

## 0.65.25
- Gâchettes à bascule : elles marchent aussi avec L et R des jeux 2D.
- Andon : la lumière suit l'image réelle du jeu et se reforme selon le format (rotation, pliage), sans couper l'interface.
- Menu codec : en portrait, la carte s'ajuste à son contenu au lieu de prendre tout l'écran, avec des entrées plus serrées.

## 0.65.24 ⭐
- Sons système propres à chaque thème pour les actions : suivant, changement d'onglet, option activée ou désactivée, erreur, opération terminée, fermeture d'un menu.

## 0.65.23
- Mises à jour : le bouton et sa barre de progression sont pleine largeur, texte centré.

## 0.65.22
- Haricots L et R : les lettres restent droites, quelle que soit l'inclinaison de la gâchette.

## 0.65.21
- Mises à jour : barre de progression dans l'interface, plus de notification de téléchargement Android.
- Les APK téléchargés sont supprimés une fois la mise à jour installée.

## 0.65.20
- Manette de l'accueil : les haricots L et R descendent à hauteur de la flèche droite et du X, côté centre.

## 0.65.19
- Bulle du synopsis dynamique : elle suit la longueur du texte, sans dépasser la zone, et défile au-delà.
- Apparition de la bulle propre à chaque thème (balayage Codec, givre Arctique, éventail Écarlate, éclosion Sakura, scintillement Ambre).
- Sous-textes des menus réécrits pour être plus clairs.

## 0.65.18 ⭐
- Andon : lumière d'ambiance autour de l'écran du jeu, qui reprend les couleurs de l'image. Modes Éteinte, Thème, Image, Image et thème, Douce, dans le menu Codec en jeu et dans le menu Thème de l'accueil.
- Manette de l'accueil : L et R descendus en haricot, un de chaque côté.

## 0.65.17
- Plein écran automatique en paysage dans toute l'app (barres de notification et de navigation masquées).
- Mises à jour installées sans la fenêtre Android de fin à fermer.

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

## 0.65.11 ⭐
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
