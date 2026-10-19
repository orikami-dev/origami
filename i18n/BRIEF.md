# Traduction d'Origami

Origami est une app Android, un lanceur d'émulateurs de consoles pour téléphones pliables (Galaxy Fold, etc.), conçue par le studio KAMI-WORKS.

Source : `/mnt/project-files/foldplay/i18n/source-fr.json`. C'est un tableau JSON de 798 chaînes françaises tirées du code : libellés, détails de menus, messages et toasts.

Sortie : `/mnt/project-files/foldplay/assets/i18n/<code>.json`. C'est un objet JSON plat `{ "<chaîne française exacte>": "<traduction>" }`, en UTF-8, avec exactement une entrée par chaîne source.

Règles :
- **Fragments.** Beaucoup de chaînes sont des morceaux d'une phrase concaténée dans le code, par exemple `"Démarrage de « "` + titre + `" »…"`, ou `" jeux"`. Traduis chaque morceau pour que la phrase recollée reste naturelle. Garde exactement les espaces, retours à la ligne et ponctuation de début et de fin (un fragment qui commence par " · " doit garder " · ").
- **Noms propres inchangés.** Origami, KAMI-WORKS, Tsuru, Washi, Flex (nom de la manette), les noms de consoles (PlayStation, Mega Drive, Game Boy…), d'émulateurs et de cœurs (ARMSX2, Dolphin, mGBA…), ScreenScraper, libretro, BIOS, ROM.
- **Symboles et unités inchangés.** Garde les symboles et indications de touches : Ⓐ Ⓑ Ⓧ Ⓨ ▲ ▼ ◀ ▶ ✚ ⌂ ★ ■ □ ● ○ › ×, L1 R1 L2 R2, SELECT, START. Garde les unités (Ko → KB, Mo → MB, Go → GB est permis selon l'usage local) et les chiffres.
- **Guillemets.** « » peut devenir les guillemets usuels de la langue.
- **Ton et longueur.** Le ton est amical et direct (le français tutoie) : utilise le registre informel courant de la langue pour une app grand public. Les chaînes en MAJUSCULES restent en majuscules. Reste concis, ce sont des textes d'interface.
- **Chaînes à recopier telles quelles.** Une chaîne qui n'est pas du français (nom propre seul, code, format) est recopiée telle quelle.
- **Vérification.** Avant de finir, recharge ton fichier avec Python et vérifie trois points : JSON valide, mêmes clés que la source (ni manquante ni en trop), aucune valeur vide. Écris le fichier en une fois. Ne touche à aucun autre fichier du dossier partagé /mnt/project-files.
