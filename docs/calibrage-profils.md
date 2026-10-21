# Calibrage des profils PS3 et Xbox 360 (20/10, 0.65.1)

Moteurs lus : `prebuilt/armsx3/libarmsx3-core.so` (ARMSX3/RPCS3, 100 Mo) et `prebuilt/xenia/libxenia-app.so` (Xenia Canary, 20 Mo).
Appareil visé : Galaxy Fold 8 (Snapdragon 8 Elite, 8 cœurs Oryon sans SMT, Adreno 840).

## Méthode

- Ghidra 12.1.4 (release NSA, URL directe OK) installé dans `~/tools/ghidra-dist/ghidra_12.1.4_PUBLIC`, JDK 21 présent.
  Pas d'analyse complète : import `-noanalysis` puis script ciblé `tools/rea/DecompNamed.java`
  (décompile les fonctions dont le nom contient un motif). Utilisé sur Xenia (bundle Android, SetupConfig, PrintConfigToLog).
- PS3 : les noms de nœuds sont construits en ligne (constantes immédiates), donc `strings` ne les voit pas tous
  (« Write Color Buffers », « Strict Rendering Mode »… absents). On a donc **exécuté** les constructeurs
  `cfg_root::node_*` du .so dans un émulateur aarch64 (Unicorn, `tools/rea/loader.py` + `tools/rea/ps3cfg.py`),
  en interceptant `cfg::_base::_base(type, owner, name, dynamic)` et `cfg::string::string` : on obtient l'arbre
  exact (Section@@Clé), le type de chaque nœud (bornes `_int<min,max>` / `uint<min,max>` lues dans le nom de type)
  et, en appelant `fmt_class_string<enum>::format(i)`, la liste exacte des valeurs d'enum acceptées.
  281 nœuds, 237 enums. Relancer : `python3 -I tools/rea/ps3cfg.py prebuilt/armsx3/libarmsx3-core.so /tmp/ps3cfg.json`
  (pip : unicorn, pyelftools).
- Xenia : `strings` + `objdump` (binutils-aarch64-linux-gnu) + tailles des symboles `cvars::*` (1 = bool, 4 = int32,
  24 = std::string) + décompilation Ghidra.

## PS3 : ce que fait `_rpcsx_settingsSet`

Chemin `Section@@Clé` (sous-nœuds : `Video@@Vulkan@@…`). Valeur passée à `from_string` du nœud :
enum = texte exact de la liste ci-dessous, entiers = bornes vérifiées, bool = `true`/`false`.
Messages : `node %s not found`, `node %s does not accept value '%s'`, `%s changes at the next boot` (nœuds non dynamiques,
OK chez nous car `applyPs3` passe avant `nativeBoot`). Hors lot (`settingsBeginBatch/EndBatch`), **chaque appel
réécrit config.yml** (`Emulator::SaveSettings`) : ~22 sauvegardes par lancement. Le pont natif n'expose pas le lot
(à ajouter à la prochaine recompilation de `native/ps3/foldplay_ps3.cpp`).

### Clés utilisées par EngineSettings.java

| Clé envoyée | Existe ? | Valeurs acceptées | Valeurs du profil | Verdict |
|---|---|---|---|---|
| Video@@Resolution Scale | oui | uint 25 à 800 | 75 / 100 / 150 | OK (75 accepté) |
| Video@@Shader Mode | oui (au boot) | Legacy Recompiler (single-threaded) / Async Recompiler (multi-threaded) / Async Recompiler with Shader Interpreter / Shader Interpreter only | Async Recompiler (multi-threaded) | OK |
| Video@@Anisotropic Filter Override | oui | uint 0 à 16 | 0 / 4 / 16 | OK |
| Video@@Output Scaling Mode | oui | Nearest / Bilinear / FidelityFX Super Resolution / Shader chain (librashader) / Snapdragon Game Super Resolution / Snapdragon Game Super Resolution (Edge Direction) | — | **« FidelityFX Super Resolution 1 » refusé** (pas de « 1 ») : FSR ne s'appliquait jamais en Équilibré/Qualité. Corrigé |
| Video@@Frame limit | oui | Off / 30 / 50 / 60 / 120 / Display / Auto / PS3 Native / Infinite | Auto (30 pour Demon's Souls) | OK |
| Video@@Multithreaded RSX | oui (au boot) | bool | true | OK |
| Video@@Write Color Buffers | oui (au boot) | bool | false (true Demon's Souls) | OK |
| Video@@Strict Rendering Mode | oui (au boot) | bool | false | OK |
| Video@@Stretch To Display Area (Ps3Activity) | oui | bool | | OK |
| Core@@SPU Block Size | oui (au boot) | Safe / Mega / Giga | Mega / Mega / Safe | OK |
| Core@@SPU XFloat Accuracy | oui (au boot) | Accurate / Approximate / Relaxed / Inaccurate | Relaxed / Approximate / Approximate | OK (l'ancien nom `Core@@XFloat Accuracy` du journal n'existe pas ; le code l'avait déjà corrigé) |
| Core@@Max SPURS Threads | oui | int 1 à 6 | 4 / 6 / 6 | OK |
| Core@@Preferred SPU Threads | oui | int 0 à 6 (0 = auto) | 0 | OK |
| Video@@Shader Compiler Threads | oui (au boot) | int 0 à 16 (0 = auto) | 6 | OK |
| Video@@Relaxed ZCULL Sync | oui (au boot) | bool | true / true / false | OK |
| Video@@Asynchronous Texture Streaming | **non** | — | true | **Introuvable** : le nœud est `Video@@Vulkan@@Asynchronous Texture Streaming` (bool, au boot ; pas de variante « 2 »). Corrigé |
| Savestate@@Compatible Savestate Mode | oui (au boot) | bool | true | OK |
| Audio@@Enable Buffering | oui | bool | true | OK |
| Audio@@Desired Audio Buffer Duration | oui | int 4 à 250 | 150 | OK |
| Audio@@Enable Time Stretching | oui | bool | true | OK |
| Audio@@Time Stretching Threshold | oui | int 0 à 100 | 75 | OK |
| Audio@@Master Volume | oui | int 0 à 200 | 80 | OK |

Autres nœuds utiles repérés (non utilisés) : `Core@@PPU Threads` (1 à 8), `Core@@Thread Scheduler Mode`
(Operating System / RPCS3 Scheduler / RPCS3 Alternative Scheduler), `Core@@SPU Decoder`, `Core@@Clocks scale` (10 à 3000),
`Video@@VSync Mode` (Disabled / Adaptive / Full), `Video@@Shader Precision` (Ultra / High / Low / Auto),
`Video@@Vulkan@@Asynchronous Queue Scheduler` (Safe / Fast), `Video@@Minimum Scalable Dimension` (1 à 1024),
`Video@@SGSR Edge Sharpness` (0 à 200), `Video@@FidelityFX CAS Sharpening Intensity` (0 à 100),
`Video@@Frame Generation` (Off / x2 / x3 / x4) + Performance Mode, Flow Scale (25 à 100), Target Rate (0 à 480).
Liste complète des nœuds Core/Video/Audio/Savestate en annexe.

## Xbox 360 : cvars

Le Bundle `EXTRA_CVARS` est lu par `cvar::ParseLaunchArgumentsFromAndroidBundle` : pour chaque clé connue,
`dynamic_cast` sur le type de la cvar puis `getBoolean` / `getInt` / `getLong` / `getDouble` / `getString`, et
`SetCommandLineValue`. Il faut donc le bon type Java (déjà le cas : bool → putBoolean, int32 → putInt, chaîne → putString).

| Cvar | Type | Valeurs permises (texte du moteur) | Profil P / É / Q | Verdict |
|---|---|---|---|---|
| draw_resolution_scale_x / _y | int32 | 1 à 7 selon l'appareil ; au-delà de 1 il faut le sparse binding Vulkan, sinon le moteur ramène à la limite | 1 / 1 / 2 | OK (voir doutes) |
| render_target_path_vulkan | chaîne | `fbo`, `fsi`, toute autre valeur (dont "") = choix auto, aujourd'hui toujours FBO | fbo | OK ; "" et "fbo" sont équivalents |
| readback_resolve | chaîne | `fast` (image précédente, sans attente), `full` (attend le GPU, lent), `none` (coupé) | none / none / fast | OK |
| anisotropic_override | int32 | -1 sans forçage, 0 coupé, 1 = ×1, 2 = ×2, 3 = ×4, **4 = ×8, 5 = ×16** | 0 / -1 / ×8 | **Bug** : on envoyait 4/8/16 (×4 donnait ×8, 8 et 16 hors bornes). Corrigé : les valeurs du menu restent des facteurs, `putCvars` les traduit |
| gpu_allow_invalid_fetch_constants | bool | | true | OK |
| async_shader_compilation | bool | | true | OK |
| vsync | bool | | true | OK |
| disable_instruction_infocache, headless, hid, target, storage_root, content_root, cache_root (X360Activity) | bool / chaîne | | | existent toutes |

### Pourquoi le dump affiche `readback_resolve = "none"` et `render_target_path_vulkan = ""`

`config::PrintConfigToLog` est appelé au tout début de `config::ReadConfig` et recopie ligne à ligne le **fichier**
`xenia-canary.config.toml` (lecture par getline). Ce fichier ne contient que les valeurs « config » ; `SaveConfig`
n'y écrit visiblement pas les valeurs de ligne de commande (`render_target_path_vulkan` y reste "" alors que "fbo" est envoyé depuis la 0.62.5). Or le Bundle est appliqué avant (`initializeWindowedAppOnCreate`) comme
valeur de ligne de commande, qui passe devant le fichier et la config par jeu. Le dump ne prouve donc rien : il montre
les valeurs par défaut du fichier, pas celles du profil. Pour le banc d'essai, `putCvars` écrit maintenant dans logcat
(étiquette `Origami`) la liste des cvars envoyées. Le moteur a aussi une ligne « Readback Resolve: » et le raccourci
« A + Guide » qui bascule ce réglage en jeu.

## Changements faits

`app/src/com/hideo/foldplay/EngineSettings.java` :
- PS3, définition : Performance 75 % (≈ 540p), Équilibré 100 %, Qualité 150 % (1080p, proche de la hauteur de l'écran
  intérieur ; 200 % = 1440p chauffait pour rien sur des jeux limités par le CPU). Choix « 75 % » ajouté au menu.
  Demon's Souls : ramené à 100 % seulement si le profil est au-dessus (Performance garde 75 %).
- PS3, mise à l'échelle : `FidelityFX Super Resolution` (nom exact) en Équilibré et Qualité ; `Snapdragon Game Super
  Resolution` (SGSR, une passe, pensé pour Adreno) en Performance, pour agrandir le 75 %. SGSR ajouté au menu.
  Un ancien choix manuel « … Resolution 1 » est corrigé à la volée.
- PS3 : `Video@@Vulkan@@Asynchronous Texture Streaming` au lieu du nœud introuvable.
- PS3 : chaque refus du moteur (`nativeSettingsSet` = false) est écrit dans logcat (`PS3 refuse clé = valeur`).
- 360 : anisotrope traduit vers l'échelle de Xenia ; Qualité passe de « 16 » (invalide) à ×8.
- 360 : journal des cvars envoyées.
- Nouvelle aide « FSR : agrandissement net (AMD). SGSR : équivalent Snapdragon, plus léger. » ajoutée à
  `app/i18n/source-fr.json` et aux 9 `app/assets/i18n/*.json`.
- `assets/cores/install.json` : inchangé (cœurs libretro non analysés ici).
- Scripts d'analyse : `tools/rea/` (loader.py, ps3cfg.py, DecompNamed.java).

Compilation : `javac … -source 8 -target 8` : seule erreur, celle attendue (`Updater.java`, `R.drawable`).

## Demon's Souls et DMC 4

- Demon's Souls : 30 i/s à 215 à 239 % de CPU sur 800 % possibles, donc limité par un ou deux fils (PPU / un SPU), pas par
  le GPU ni le nombre de cœurs. La définition ne change rien à la cadence ; elle est baissée pour la chauffe (le
  ralentissement thermique des cœurs Oryon est ce qui pénalise le plus un jeu limité par un fil). Les ralentis en cassant
  des tonneaux (physique sur SPU) et le son qui suit (étirement du temps, voulu) ne se règlent pas par le profil.
- DMC 4 : Performance et Équilibré sont déjà au plus léger (×1, relecture coupée, FBO). Le profil ne peut pas faire mieux ;
  à mesurer avec le journal (« Skipped 88 frames » = fil principal Android bloqué au lancement, côté app).

## Doutes restants

- Adreno 840 et sparse binding : si le pilote ne l'expose pas, `draw_resolution_scale 2` du profil Qualité 360 retombe
  à 1 sans le dire. À lire dans le journal (`sparseBinding`, ligne « clamp »).
- SGSR dans ARMSX3 : présent (enum + réglage de netteté), jamais essayé sur le Fold. S'il dessine mal, remettre Bilinear
  en Performance.
- Les bools PS3 : on suppose `true`/`false` (comme RPCS3) ; c'était déjà le cas avant et Write Color Buffers marchait.
- Un fichier de config par jeu PS3 (custom config) chargé au boot pourrait passer devant ces réglages : non vérifié.
- Valeurs par défaut des nœuds PS3 non relevées (l'émulation n'exécute pas les initialisations de valeurs).
- Rien n'a été testé sur l'appareil : tout reste à confirmer au banc d'essai (logcat `Origami`).

## Annexe : nœuds PS3 Core / Video / Audio / Savestate (« au démarrage » = non modifiable en jeu)

- `Core@@PPU Decoder` : enum : Interpreter (static) / Recompiler (LLVM) (au démarrage)
- `Core@@PPU Threads` : _int<1, 8> (au démarrage)
- `Core@@PPU Debug` : _bool (au démarrage)
- `Core@@PPU Calling History` : _bool (au démarrage)
- `Core@@Save LLVM logs` : _bool (au démarrage)
- `Core@@Use LLVM CPU` : string (au démarrage)
- `Core@@Max LLVM Compile Threads` : _int<0, 1024> (au démarrage)
- `Core@@LLVM Precompilation` : _bool (au démarrage)
- `Core@@Thread Scheduler Mode` : enum : Operating System / RPCS3 Scheduler / RPCS3 Alternative Scheduler (au démarrage)
- `Core@@Set DAZ and FTZ` : _bool (au démarrage)
- `Core@@SPU Decoder` : enum : Interpreter (static) / Interpreter (dynamic) / Recompiler (ASMJIT) / Recompiler (LLVM) (au démarrage)
- `Core@@SPU Reservation Busy Waiting Percentage 1` : uint<0, 100>
- `Core@@SPU Reservation Busy Waiting Enabled` : _bool
- `Core@@SPU GETLLAR Busy Waiting Percentage` : uint<0, 100>
- `Core@@Disable SPU GETLLAR Spin Optimization` : _bool
- `Core@@SPU Debug` : _bool (au démarrage)
- `Core@@MFC Debug` : _bool (au démarrage)
- `Core@@Preferred SPU Threads` : _int<0, 6>
- `Core@@SPU delay penalty` : _int<0, 16> (au démarrage)
- `Core@@SPU loop detection` : _bool (au démarrage)
- `Core@@Max SPURS Threads` : _int<1, 6>
- `Core@@SPU Block Size` : enum : Safe / Mega / Giga (au démarrage)
- `Core@@Accurate SPU DMA` : _bool (au démarrage)
- `Core@@Accurate SPU Reservations` : _bool (au démarrage)
- `Core@@Accurate Cache Line Stores` : _bool (au démarrage)
- `Core@@Accurate RSX reservation access` : _bool
- `Core@@PPU Reservation Priority Over SPUs` : _bool
- `Core@@RSX FIFO Fetch Accuracy` : cfg_root::node_core::fifo_setting (au démarrage)
- `Core@@SPU Verification` : _bool (au démarrage)
- `Core@@SPU Cache` : _bool (au démarrage)
- `Core@@SPU Profiler` : _bool (au démarrage)
- `Core@@PPU Profiler` : _bool (au démarrage)
- `Core@@MFC Commands Shuffling Limit` : uint<0, 16> (au démarrage)
- `Core@@MFC Commands Timeout` : uint<0, 10000>
- `Core@@MFC Commands Shuffling In Steps` : _bool
- `Core@@SPU XFloat Accuracy` : enum : Accurate / Approximate / Relaxed / Inaccurate (au démarrage)
- `Core@@Accurate PPU 128-byte Reservation Op Max Length` : _int<-1, 14>
- `Core@@Stub PPU Traps` : _int<-64, 64>
- `Core@@Precise SPU Verification` : _bool (au démarrage)
- `Core@@PPU Vector NaN Handling` : _bool (au démarrage)
- `Core@@Use Accurate DFMA` : _bool (au démarrage)
- `Core@@PPU Set Saturation Bit` : _bool (au démarrage)
- `Core@@PPU Accurate Non-Java Mode` : _bool (au démarrage)
- `Core@@PPU Accurate Vector NaN Values` : _bool (au démarrage)
- `Core@@PPU Set FPCC Bits` : _bool (au démarrage)
- `Core@@Debug Console Mode` : _bool (au démarrage)
- `Core@@Hook static functions` : _bool (au démarrage)
- `Core@@Libraries Control` : set_entry (au démarrage)
- `Core@@HLE lwmutex` : _bool (au démarrage)
- `Core@@SPU LLVM Lower Bound` : uint<0, max> (au démarrage)
- `Core@@SPU LLVM Upper Bound` : uint<0, max> (au démarrage)
- `Core@@Clocks scale` : _int<10, 3000> (au démarrage)
- `Core@@SPU Wake-Up Delay` : uint<0, 3000>
- `Core@@SPU Wake-Up Delay Thread Mask` : uint<0, 63>
- `Core@@Max CPU Preempt Count` : uint<0, 400>
- `Core@@PPU Event Wake-Up Delay` : uint<0, 200>
- `Core@@Allow RSX CPU Preemptions` : _bool
- `Core@@Sleep Timers Accuracy` : enum : As Host / Usleep Only / All Timers
- `Core@@Usleep Time Addend` : _int<-1000, 1500>
- `Core@@Performance Report Threshold` : uint<0, max>
- `Core@@Enable Performance Report` : _bool
- `Core@@Assume External Debugger` : _bool (au démarrage)
- `Video@@Renderer` : enum : Null / OpenGL / Vulkan (au démarrage)
- `Video@@Resolution` : enum : 1920x1080 / 1920x1080i / 1280x720 / 720x480 / 720x480i / 720x576 / 720x576i / 1600x1080 / 1440x1080 / 1280x1080 / 960x1080 (au démarrage)
- `Video@@Aspect ratio` : enum : 4:3 / 16:9 (au démarrage)
- `Video@@Frame limit` : enum : Off / 30 / 50 / 60 / 120 / Display / Auto / PS3 Native / Infinite
- `Video@@Second Frame Limit` : _float<0, 1000>
- `Video@@MSAA` : enum : Disabled / Auto (au démarrage)
- `Video@@Shader Mode` : enum : Legacy Recompiler (single-threaded) / Async Recompiler (multi-threaded) / Async Recompiler with Shader Interpreter / Shader Interpreter only (au démarrage)
- `Video@@Shader Precision` : enum : Ultra / High / Low / Auto (au démarrage)
- `Video@@VSync Mode` : enum : Disabled / Adaptive / Full
- `Video@@Write Color Buffers` : _bool (au démarrage)
- `Video@@Write Depth Buffer` : _bool (au démarrage)
- `Video@@Read Color Buffers` : _bool (au démarrage)
- `Video@@Read Depth Buffer` : _bool (au démarrage)
- `Video@@Handle RSX Memory Tiling` : _bool
- `Video@@Log shader programs` : _bool (au démarrage)
- `Video@@Debug output` : _bool (au démarrage)
- `Video@@Debug overlay` : _bool
- `Video@@Renderdoc Compatibility Mode` : _bool (au démarrage)
- `Video@@Use GPU texture scaling` : _bool (au démarrage)
- `Video@@Stretch To Display Area` : _bool
- `Video@@Display Aspect Override` : _int<0, 4000>
- `Video@@Force High Precision Z buffer` : _bool (au démarrage)
- `Video@@Strict Rendering Mode` : _bool (au démarrage)
- `Video@@Framebuffer Aliasing Heuristic Bias` : enum : Auto / Prefer Color / Prefer Depth
- `Video@@Disable ZCull Occlusion Queries` : _bool
- `Video@@Disable Video Output` : _bool
- `Video@@Disable Vertex Cache` : _bool (au démarrage)
- `Video@@Disable FIFO Reordering` : _bool (au démarrage)
- `Video@@Emulate Special Depth Comparison` : _bool (au démarrage)
- `Video@@Emulate Extended Depth Range` : _bool (au démarrage)
- `Video@@Enable Frame Skip` : _bool
- `Video@@Force CPU Blit` : _bool
- `Video@@Disable On-Disk Shader Cache` : _bool (au démarrage)
- `Video@@Disable Vulkan Memory Allocator` : _bool (au démarrage)
- `Video@@Use full RGB output range` : _bool
- `Video@@Strict Texture Flushing` : _bool (au démarrage)
- `Video@@Multithreaded RSX` : _bool (au démarrage)
- `Video@@Relaxed ZCULL Sync` : _bool (au démarrage)
- `Video@@RSX Profiler` : _bool
- `Video@@Force Hardware MSAA Resolve` : _bool
- `Video@@3D Display Enabled` : _bool (au démarrage)
- `Video@@3D Display Mode` : enum : Disabled / Side-by-Side / Over-Under / Interlaced / Anaglyph Red-Green / Anaglyph Red-Blue / Anaglyph Red-Cyan / Anaglyph Magenta-Cyan / Anaglyph Trioscopic / Anaglyph Amber-Blue / Anaglyph Custom
- `Video@@Screen size in inches` : _int<10, 99> (au démarrage)
- `Video@@Debug Program Analyser` : _bool (au démarrage)
- `Video@@Accurate ZCULL stats` : _bool (au démarrage)
- `Video@@Consecutive Frames To Draw` : _int<1, 8>
- `Video@@Consecutive Frames To Skip` : _int<1, 8>
- `Video@@Resolution Scale` : uint<25, 800>
- `Video@@Anisotropic Filter Override` : uint<0, 16>
- `Video@@Texture LOD Bias Addend` : _float<-32, 32>
- `Video@@Minimum Scalable Dimension` : uint<1, 1024>
- `Video@@Shader Compiler Threads` : _int<0, 16> (au démarrage)
- `Video@@Driver Recovery Timeout` : _int<0, 30000000>
- `Video@@Driver Wake-Up Delay` : uint<0, 16667>
- `Video@@Vblank Rate` : _int<1, 6000>
- `Video@@Vblank NTSC Fixup` : _bool
- `Video@@DECR memory layout` : _bool (au démarrage)
- `Video@@Allow Host GPU Labels` : _bool (au démarrage)
- `Video@@Disable MSL Fast Math` : _bool (au démarrage)
- `Video@@Disable Asynchronous Memory Manager` : _bool
- `Video@@Output Scaling Mode` : enum : Nearest / Bilinear / FidelityFX Super Resolution / Shader chain (librashader) / Snapdragon Game Super Resolution / Snapdragon Game Super Resolution (Edge Direction)
- `Video@@Frame Generation` : enum : Off / x2 / x3 / x4
- `Video@@Frame Generation Performance Mode` : _bool
- `Video@@Frame Generation Flow Scale` : uint<25, 100>
- `Video@@Frame Generation Target Rate` : uint<0, 480>
- `Video@@Frame Generation Lossless Path` : string
- `Video@@Shader Preset Path` : string
- `Video@@Record With Overlays` : _bool
- `Video@@Disable Hardware Blending` : _bool
- `Video@@Disable Programmable Blending` : _bool
- `Video@@Disable Hardware ColorSpace Remapping` : _bool
- `Video@@FidelityFX CAS Sharpening Intensity` : uint<0, 100>
- `Video@@SGSR Edge Sharpness` : uint<0, 200>
- `Video@@Disable Blit Engine Upscaling` : _bool
- `Video@@Vulkan` : cfg_root::node_video::node_vk
- `Video@@Vulkan@@Adapter` : string (au démarrage)
- `Video@@Vulkan@@Exclusive Fullscreen Mode` : enum : Automatic / Disable / Enable (au démarrage)
- `Video@@Vulkan@@Asynchronous Texture Streaming` : _bool (au démarrage)
- `Video@@Vulkan@@Asynchronous Queue Scheduler` : enum : Safe / Fast (au démarrage)
- `Video@@Vulkan@@VRAM allocation limit (MB)` : uint<256, 65536> (au démarrage)
- `Video@@Vulkan@@Use Re-BAR for GPU uploads` : _bool (au démarrage)
- `Video@@Performance Overlay` : cfg_root::node_video::node_perf_overlay
- `Video@@Performance Overlay@@Enabled` : _bool
- `Video@@Performance Overlay@@Enable Framerate Graph` : _bool
- `Video@@Performance Overlay@@Enable Frametime Graph` : _bool
- `Video@@Performance Overlay@@Framerate datapoints` : uint<2, 6000>
- `Video@@Performance Overlay@@Frametime datapoints` : uint<2, 6000>
- `Video@@Performance Overlay@@Detail level` : enum : None / Minimal / Low / Medium / High
- `Video@@Performance Overlay@@Framerate graph detail level` : enum : Minimal / Show min and max / Show 1% and average / All
- `Video@@Performance Overlay@@Frametime graph detail level` : enum : Minimal / Show min and max / Show 1% and average / All
- `Video@@Performance Overlay@@Metrics update interval (ms)` : uint<1, 1000>
- `Video@@Performance Overlay@@Font size (px)` : uint<4, 36>
- `Video@@Performance Overlay@@Position` : enum : Top Left / Top Right / Bottom Left / Bottom Right
- `Video@@Performance Overlay@@Font` : string
- `Video@@Performance Overlay@@Horizontal Margin (%)` : _float<0, 100>
- `Video@@Performance Overlay@@Vertical Margin (%)` : _float<0, 100>
- `Video@@Performance Overlay@@Center Horizontally` : _bool
- `Video@@Performance Overlay@@Center Vertically` : _bool
- `Video@@Performance Overlay@@Opacity (%)` : uint<0, 100>
- `Video@@Performance Overlay@@Body Color (hex)` : string
- `Video@@Performance Overlay@@Body Background (hex)` : string
- `Video@@Performance Overlay@@Title Color (hex)` : string
- `Video@@Performance Overlay@@Title Background (hex)` : string
- `Video@@Performance Overlay@@Use Window Space` : _bool
- `Video@@Custom Anaglyph Matrices` : cfg_root::node_video::anaglyph_matrices
- `Video@@Custom Anaglyph Matrices@@Matrix Left` : node_map_entry
- `Video@@Custom Anaglyph Matrices@@Matrix Right` : node_map_entry
- `Audio@@Renderer` : enum : Null / Cubeb / Oboe
- `Audio@@Audio Provider` : enum : None / CellAudio / RSXAudio (au démarrage)
- `Audio@@RSXAudio Avport` : enum : HDMI 0 / HDMI 1 / AV multiout / SPDIF 0 / SPDIF 1
- `Audio@@Dump to file` : _bool
- `Audio@@Convert to 16 bit` : _bool
- `Audio@@Audio Format` : enum : Stereo / Surround 5.1 / Surround 7.1 / Automatic / Manual (au démarrage)
- `Audio@@Audio Formats` : uint<0, 255> (au démarrage)
- `Audio@@Audio Channel Layout` : enum : Automatic / Mono / Stereo / Stereo LFE / Quadraphonic / Quadraphonic LFE / Surround 5.1 / Surround 7.1 (au démarrage)
- `Audio@@Audio Device` : string
- `Audio@@Master Volume` : _int<0, 200>
- `Audio@@Enable Buffering` : _bool
- `Audio@@Desired Audio Buffer Duration` : _int<4, 250>
- `Audio@@Enable Time Stretching` : _bool
- `Audio@@Recording Compatible` : _bool
- `Audio@@Disable Sampling Skip` : _bool
- `Audio@@Cubeb Backend` : string (au démarrage)
- `Audio@@Time Stretching Threshold` : _int<0, 100>
- `Audio@@Microphone Type` : enum : Null / Standard / SingStar / Real SingStar / Rocksmith / Eye Toy / PS Eye (au démarrage)
- `Audio@@Microphone Devices` : string (au démarrage)
- `Audio@@Music Handler` : enum : Null / Qt (au démarrage)
- `Savestate@@Start Paused` : _bool (au démarrage)
- `Savestate@@Suspend Emulation Savestate Mode` : _bool (au démarrage)
- `Savestate@@Compatible Savestate Mode` : _bool (au démarrage)
- `Savestate@@Inspection Mode Savestates` : _bool (au démarrage)
- `Savestate@@Save Disc Game Data` : _bool (au démarrage)
- `Savestate@@Maximum SaveState Files` : uint<0, 64> (au démarrage)
- `Savestate@@Maximum SaveState Files Space (MiB)` : uint<0, 524288> (au démarrage)
