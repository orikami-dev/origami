/*
 * Pont libretro de FOLD/PLAY : charge un cœur téléchargé (dlopen), lui fournit l'environnement,
 * convertit chaque image en RGBA dans le tampon Java, accumule le son et lit les touches de la
 * manette. Tout est appelé depuis le seul fil d'émulation côté Java.
 *
 * Compilé sans l'Android NDK (introuvable depuis l'atelier) : les quelques fonctions de bionic
 * dont on a besoin sont déclarées ici, et le lien se fait contre des bibliothèques-souches.
 */
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include <jni.h>

#include "libretro.h"

/* ------------------------------------------------------------------ bionic */

void *malloc(size_t n);
void *calloc(size_t n, size_t s);
void *realloc(void *p, size_t n);
void free(void *p);
void *memcpy(void *d, const void *s, size_t n);
void *memset(void *d, int c, size_t n);
size_t strlen(const char *s);
int strcmp(const char *a, const char *b);
char *strchr(const char *s, int c);
int snprintf(char *b, size_t n, const char *fmt, ...);
int vsnprintf(char *b, size_t n, const char *fmt, va_list ap);
void *dlopen(const char *path, int flags);
void *dlsym(void *h, const char *name);
int dlclose(void *h);
char *dlerror(void);
int __android_log_print(int prio, const char *tag, const char *fmt, ...);
int __android_log_vprint(int prio, const char *tag, const char *fmt, va_list ap);
struct timespec_ { long tv_sec; long tv_nsec; };
int clock_gettime(int clk, struct timespec_ *ts);

#define RTLD_NOW 2
#define LOG_DEBUG 3
#define LOG_INFO 4
#define LOG_WARN 5
#define LOG_ERROR 6
#define TAG "FoldPlayCore"

static char *dupstr(const char *s) {
    if (!s) return NULL;
    size_t n = strlen(s) + 1;
    char *d = malloc(n);
    if (d) memcpy(d, s, n);
    return d;
}

/* ------------------------------------------------------------------ cœur */

static void *core;
static void (*p_init)(void);
static void (*p_deinit)(void);
static unsigned (*p_api_version)(void);
static void (*p_get_system_info)(struct retro_system_info *);
static void (*p_get_system_av_info)(struct retro_system_av_info *);
static void (*p_set_environment)(retro_environment_t);
static void (*p_set_video_refresh)(retro_video_refresh_t);
static void (*p_set_audio_sample)(retro_audio_sample_t);
static void (*p_set_audio_sample_batch)(retro_audio_sample_batch_t);
static void (*p_set_input_poll)(retro_input_poll_t);
static void (*p_set_input_state)(retro_input_state_t);
static void (*p_set_controller_port_device)(unsigned, unsigned);
static void (*p_reset)(void);
static void (*p_run)(void);
static size_t (*p_serialize_size)(void);
static bool (*p_serialize)(void *, size_t);
static bool (*p_unserialize)(const void *, size_t);
static bool (*p_load_game)(const struct retro_game_info *);
static void (*p_unload_game)(void);
static void *(*p_get_memory_data)(unsigned);
static size_t (*p_get_memory_size)(unsigned);
static void (*p_cheat_reset)(void);
static void (*p_cheat_set)(unsigned, bool, const char *);

static char *core_path;
static char *system_dir;
static char *save_dir;
static void *game_data;
static bool game_loaded;
static bool initialized;
static char last_error[512];

/* ------------------------------------------------------------------ état partagé */

static enum retro_pixel_format pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
static uint32_t *frame_out;      /* tampon direct fourni par Java (RGBA8888) */
static size_t frame_capacity;    /* en pixels */
static unsigned frame_w, frame_h;
static int frame_new;
static float aspect;
static double fps = 60.0;
static double sample_rate = 44100.0;

#define AUDIO_CAP (48000 * 2)    /* une seconde de stéréo à 48 kHz */
static int16_t audio_buf[AUDIO_CAP];
static size_t audio_len;         /* en int16 */

#define MAX_PORTS 4
/* Port 0 : manette à l'écran + manette physique du joueur 1 ; ports 1 à 3 : joueurs 2 à 4. */
static volatile uint32_t port_buttons[MAX_PORTS];
static volatile int16_t port_analog[MAX_PORTS][2][2];
#define buttons port_buttons[0]
#define analog port_analog[0]
static volatile int pointer_x, pointer_y, pointer_down;
/* Accéléré demandé par Java : lu par les cœurs qui gèrent eux-mêmes leur cadence (ARMSX2). */
static volatile bool fast_forward;

static struct retro_frame_time_callback frame_time_cb;
static bool has_frame_time_cb;

/* Variables d'options : clé → valeur (la valeur par défaut est la première de la liste). */
#define MAX_VARS 512
static char *var_keys[MAX_VARS];
static char *var_values[MAX_VARS];
static char *var_specs[MAX_VARS];  /* « Description; v1|v2|… » tel que donné par le cœur */
static int var_count;
static bool vars_dirty;

static int var_index(const char *key) {
    for (int i = 0; i < var_count; i++)
        if (strcmp(var_keys[i], key) == 0) return i;
    return -1;
}

static void var_put(const char *key, const char *value, bool override) {
    int i = var_index(key);
    if (i >= 0) {
        if (!override) return;
        free(var_values[i]);
        var_values[i] = dupstr(value);
        vars_dirty = true;
        return;
    }
    if (var_count >= MAX_VARS) return;
    var_keys[var_count] = dupstr(key);
    var_values[var_count] = dupstr(value);
    var_count++;
}

/* « Description; valeur1|valeur2|… » : la valeur par défaut est la première. */
static void var_define(const char *key, const char *spec) {
    if (!key || !spec) return;
    const char *p = strchr(spec, ';');
    p = p ? p + 1 : spec;
    while (*p == ' ') p++;
    char value[256];
    size_t n = 0;
    while (p[n] && p[n] != '|' && n < sizeof(value) - 1) {
        value[n] = p[n];
        n++;
    }
    value[n] = 0;
    var_put(key, value, false);
    int i = var_index(key);
    if (i >= 0) {
        free(var_specs[i]);
        var_specs[i] = dupstr(spec);
    }
}

static void vars_clear(void) {
    for (int i = 0; i < var_count; i++) {
        free(var_keys[i]);
        free(var_values[i]);
        free(var_specs[i]);
        var_specs[i] = NULL;
    }
    var_count = 0;
}

/* ------------------------------------------------------------------ rappels */

static volatile bool debug_log;

static void log_cb(enum retro_log_level level, const char *fmt, ...) {
    /* Hors mode débogage, les messages DEBUG des cœurs (très bavards) ne vont pas dans logcat. */
    if (level == RETRO_LOG_DEBUG && !debug_log) return;
    int prio = level == RETRO_LOG_ERROR ? LOG_ERROR : level == RETRO_LOG_WARN ? LOG_WARN
             : level == RETRO_LOG_INFO ? LOG_INFO : LOG_DEBUG;
    va_list ap;
    va_start(ap, fmt);
    if (level == RETRO_LOG_ERROR) {
        va_list cp;
        va_copy(cp, ap);
        vsnprintf(last_error, sizeof(last_error), fmt, cp);
        va_end(cp);
    }
    __android_log_vprint(prio, TAG, fmt, ap);
    va_end(ap);
}

static retro_time_t perf_time_usec(void) {
    struct timespec_ ts;
    clock_gettime(1 /* CLOCK_MONOTONIC */, &ts);
    return (retro_time_t) ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
}

static retro_perf_tick_t perf_counter(void) {
    struct timespec_ ts;
    clock_gettime(1, &ts);
    return (retro_perf_tick_t) ts.tv_sec * 1000000000ull + ts.tv_nsec;
}

static uint64_t perf_cpu_features(void) {
    return RETRO_SIMD_NEON | RETRO_SIMD_ASIMD;
}

static void perf_log(void) {}
static void perf_register(struct retro_perf_counter *c) { c->registered = true; }
static void perf_start(struct retro_perf_counter *c) { (void) c; }
static void perf_stop(struct retro_perf_counter *c) { (void) c; }

static bool rumble_cb(unsigned port, enum retro_rumble_effect e, uint16_t s) {
    (void) port; (void) e; (void) s;
    return false;
}

/* ------------------------------------------------------------------ rendu matériel (OpenGL ES)
 *
 * Les cœurs 3D (N64, PSP, Dreamcast) dessinent avec OpenGL ES. On leur donne un contexte EGL hors
 * écran (pbuffer) sur le fil d'émulation et un framebuffer à nous ; à chaque image, glReadPixels
 * la recopie en RGBA dans le tampon Java, qui s'affiche ensuite comme celle d'un cœur logiciel.
 * libEGL et libGLESv3 sont ouverts à l'exécution (pas de NDK pour lier contre eux).
 */

typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;

#define EGL_NONE 0x3038
#define EGL_RED_SIZE 0x3024
#define EGL_GREEN_SIZE 0x3023
#define EGL_BLUE_SIZE 0x3022
#define EGL_ALPHA_SIZE 0x3021
#define EGL_DEPTH_SIZE 0x3025
#define EGL_STENCIL_SIZE 0x3026
#define EGL_SURFACE_TYPE 0x3033
#define EGL_PBUFFER_BIT 0x0001
#define EGL_RENDERABLE_TYPE 0x3040
#define EGL_OPENGL_ES2_BIT 0x0004
#define EGL_OPENGL_ES3_BIT 0x0040
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056
#define EGL_CONTEXT_CLIENT_VERSION 0x3098

#define GL_TEXTURE_2D 0x0DE1
#define GL_TEXTURE_MIN_FILTER 0x2801
#define GL_TEXTURE_MAG_FILTER 0x2800
#define GL_LINEAR 0x2601
#define GL_RGBA 0x1908
#define GL_RGBA8 0x8058
#define GL_UNSIGNED_BYTE 0x1401
#define GL_FRAMEBUFFER 0x8D40
#define GL_READ_FRAMEBUFFER 0x8CA8
#define GL_RENDERBUFFER 0x8D41
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_DEPTH_ATTACHMENT 0x8D00
#define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
#define GL_DEPTH24_STENCIL8 0x88F0
#define GL_DEPTH_COMPONENT24 0x81A6
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_FRAMEBUFFER_BINDING 0x8CA6
#define GL_READ_FRAMEBUFFER_BINDING 0x8CAA
#define GL_RENDERBUFFER_BINDING 0x8CA7
#define GL_TEXTURE_BINDING_2D 0x8069
#define GL_MAX_RENDERBUFFER_SIZE 0x84E8
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PACK_ROW_LENGTH 0x0D02
#define GL_PACK_SKIP_ROWS 0x0D03
#define GL_PACK_SKIP_PIXELS 0x0D04
#define GL_PIXEL_PACK_BUFFER 0x88EB
#define GL_PIXEL_PACK_BUFFER_BINDING 0x88ED

static void *egl_lib, *gles_lib;
static void *(*egl_GetDisplay)(void *);
static unsigned (*egl_Initialize)(void *, int *, int *);
static unsigned (*egl_ChooseConfig)(void *, const int *, void **, int, int *);
static void *(*egl_CreateContext)(void *, void *, void *, const int *);
static void *(*egl_CreatePbufferSurface)(void *, void *, const int *);
static unsigned (*egl_MakeCurrent)(void *, void *, void *, void *);
static unsigned (*egl_DestroyContext)(void *, void *);
static unsigned (*egl_DestroySurface)(void *, void *);
static unsigned (*egl_BindAPI)(unsigned);
static int (*egl_GetError)(void);
static void (*(*egl_GetProcAddress)(const char *))(void);

static void (*gl_GenFramebuffers)(GLsizei, GLuint *);
static void (*gl_DeleteFramebuffers)(GLsizei, const GLuint *);
static void (*gl_BindFramebuffer)(GLenum, GLuint);
static void (*gl_FramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
static void (*gl_FramebufferRenderbuffer)(GLenum, GLenum, GLenum, GLuint);
static GLenum (*gl_CheckFramebufferStatus)(GLenum);
static void (*gl_GenRenderbuffers)(GLsizei, GLuint *);
static void (*gl_DeleteRenderbuffers)(GLsizei, const GLuint *);
static void (*gl_BindRenderbuffer)(GLenum, GLuint);
static void (*gl_RenderbufferStorage)(GLenum, GLenum, GLsizei, GLsizei);
static void (*gl_GenTextures)(GLsizei, GLuint *);
static void (*gl_DeleteTextures)(GLsizei, const GLuint *);
static void (*gl_BindTexture)(GLenum, GLuint);
static void (*gl_TexParameteri)(GLenum, GLenum, GLint);
static void (*gl_TexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void *);
static void (*gl_GetIntegerv)(GLenum, GLint *);
static void (*gl_PixelStorei)(GLenum, GLint);
static void (*gl_BindBuffer)(GLenum, GLuint);
static void (*gl_ReadPixels)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *);

static struct retro_hw_render_callback hw;
static bool hw_requested;   /* le cœur a demandé le rendu OpenGL ES */
static bool hw_live;        /* contexte créé et context_reset appelé */
static void *egl_dpy, *egl_ctx, *egl_surf;
static GLuint hw_fbo, hw_tex, hw_depth;
static unsigned hw_w, hw_h;         /* taille du framebuffer */
static unsigned hw_want_w, hw_want_h;
static uint32_t *hw_row;            /* une ligne, pour retourner l'image */

static retro_proc_address_t hw_get_proc(const char *sym) {
    void *p = gles_lib ? dlsym(gles_lib, sym) : NULL;
    if (!p && egl_GetProcAddress) return (retro_proc_address_t) egl_GetProcAddress(sym);
    return (retro_proc_address_t) p;
}

static uintptr_t hw_get_fbo(void) {
    return hw_fbo;
}

#define GLSYM(lib, var, name) do { *(void **) &(var) = dlsym(lib, name); if (!(var)) { \
    snprintf(last_error, sizeof(last_error), "OpenGL ES : %s introuvable", name); return false; } } while (0)

static bool gl_load(void) {
    if (gles_lib) return true;
    egl_lib = dlopen("libEGL.so", RTLD_NOW);
    void *g = dlopen("libGLESv3.so", RTLD_NOW);
    if (!g) g = dlopen("libGLESv2.so", RTLD_NOW);
    if (!egl_lib || !g) {
        snprintf(last_error, sizeof(last_error), "OpenGL ES indisponible sur cet appareil");
        return false;
    }
    GLSYM(egl_lib, egl_GetDisplay, "eglGetDisplay");
    GLSYM(egl_lib, egl_Initialize, "eglInitialize");
    GLSYM(egl_lib, egl_ChooseConfig, "eglChooseConfig");
    GLSYM(egl_lib, egl_CreateContext, "eglCreateContext");
    GLSYM(egl_lib, egl_CreatePbufferSurface, "eglCreatePbufferSurface");
    GLSYM(egl_lib, egl_MakeCurrent, "eglMakeCurrent");
    GLSYM(egl_lib, egl_DestroyContext, "eglDestroyContext");
    GLSYM(egl_lib, egl_DestroySurface, "eglDestroySurface");
    GLSYM(egl_lib, egl_BindAPI, "eglBindAPI");
    GLSYM(egl_lib, egl_GetError, "eglGetError");
    GLSYM(egl_lib, egl_GetProcAddress, "eglGetProcAddress");
    GLSYM(g, gl_GenFramebuffers, "glGenFramebuffers");
    GLSYM(g, gl_DeleteFramebuffers, "glDeleteFramebuffers");
    GLSYM(g, gl_BindFramebuffer, "glBindFramebuffer");
    GLSYM(g, gl_FramebufferTexture2D, "glFramebufferTexture2D");
    GLSYM(g, gl_FramebufferRenderbuffer, "glFramebufferRenderbuffer");
    GLSYM(g, gl_CheckFramebufferStatus, "glCheckFramebufferStatus");
    GLSYM(g, gl_GenRenderbuffers, "glGenRenderbuffers");
    GLSYM(g, gl_DeleteRenderbuffers, "glDeleteRenderbuffers");
    GLSYM(g, gl_BindRenderbuffer, "glBindRenderbuffer");
    GLSYM(g, gl_RenderbufferStorage, "glRenderbufferStorage");
    GLSYM(g, gl_GenTextures, "glGenTextures");
    GLSYM(g, gl_DeleteTextures, "glDeleteTextures");
    GLSYM(g, gl_BindTexture, "glBindTexture");
    GLSYM(g, gl_TexParameteri, "glTexParameteri");
    GLSYM(g, gl_TexImage2D, "glTexImage2D");
    GLSYM(g, gl_GetIntegerv, "glGetIntegerv");
    GLSYM(g, gl_PixelStorei, "glPixelStorei");
    GLSYM(g, gl_BindBuffer, "glBindBuffer");
    GLSYM(g, gl_ReadPixels, "glReadPixels");
    gles_lib = g;
    return true;
}

/** (Re)donne au framebuffer la taille voulue, sans changer ses identifiants (le cœur les garde). */
static bool hw_storage(unsigned w, unsigned h) {
    GLint max = 4096, tex = 0, rb = 0, fb = 0;
    gl_GetIntegerv(GL_MAX_RENDERBUFFER_SIZE, &max);
    if (max <= 0) max = 4096;
    if (w > (unsigned) max) w = (unsigned) max;
    if (h > (unsigned) max) h = (unsigned) max;
    gl_GetIntegerv(GL_TEXTURE_BINDING_2D, &tex);
    gl_GetIntegerv(GL_RENDERBUFFER_BINDING, &rb);
    gl_GetIntegerv(GL_FRAMEBUFFER_BINDING, &fb);
    if (!hw_fbo) {
        gl_GenFramebuffers(1, &hw_fbo);
        gl_GenTextures(1, &hw_tex);
        if (hw.depth || hw.stencil) gl_GenRenderbuffers(1, &hw_depth);
    }
    gl_BindTexture(GL_TEXTURE_2D, hw_tex);
    gl_TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    gl_TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    gl_TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (GLsizei) w, (GLsizei) h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    gl_BindFramebuffer(GL_FRAMEBUFFER, hw_fbo);
    gl_FramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, hw_tex, 0);
    if (hw_depth) {
        gl_BindRenderbuffer(GL_RENDERBUFFER, hw_depth);
        gl_RenderbufferStorage(GL_RENDERBUFFER, hw.stencil ? GL_DEPTH24_STENCIL8 : GL_DEPTH_COMPONENT24,
                               (GLsizei) w, (GLsizei) h);
        gl_FramebufferRenderbuffer(GL_FRAMEBUFFER, hw.stencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT,
                                   GL_RENDERBUFFER, hw_depth);
    }
    GLenum st = gl_CheckFramebufferStatus(GL_FRAMEBUFFER);
    gl_BindTexture(GL_TEXTURE_2D, (GLuint) tex);
    gl_BindRenderbuffer(GL_RENDERBUFFER, (GLuint) rb);
    gl_BindFramebuffer(GL_FRAMEBUFFER, (GLuint) fb);
    if (st != GL_FRAMEBUFFER_COMPLETE) {
        snprintf(last_error, sizeof(last_error), "framebuffer OpenGL incomplet (0x%x, %ux%u)", st, w, h);
        return false;
    }
    free(hw_row);
    hw_row = malloc((size_t) w * 4);
    hw_w = w;
    hw_h = h;
    return hw_row != NULL;
}

/** Crée le contexte et le framebuffer, puis prévient le cœur (après retro_load_game). */
static bool hw_start(unsigned w, unsigned h) {
    if (!gl_load()) return false;
    egl_dpy = egl_GetDisplay(NULL);
    int maj = 0, min = 0;
    if (!egl_dpy || !egl_Initialize(egl_dpy, &maj, &min)) {
        snprintf(last_error, sizeof(last_error), "EGL ne s'initialise pas (0x%x)", egl_GetError());
        return false;
    }
    egl_BindAPI(0x30A0 /* EGL_OPENGL_ES_API */);
    int version = 3;
    void *config = NULL;
    int n = 0;
    for (; version >= 2; version--) {
        const int attrs[] = {EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                             EGL_DEPTH_SIZE, 0, EGL_STENCIL_SIZE, 0, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                             EGL_RENDERABLE_TYPE, version == 3 ? EGL_OPENGL_ES3_BIT : EGL_OPENGL_ES2_BIT, EGL_NONE};
        if (egl_ChooseConfig(egl_dpy, attrs, &config, 1, &n) && n > 0) break;
    }
    if (n <= 0) {
        snprintf(last_error, sizeof(last_error), "aucune configuration EGL OpenGL ES");
        return false;
    }
    const int ctx_attrs[] = {EGL_CONTEXT_CLIENT_VERSION, version, EGL_NONE};
    egl_ctx = egl_CreateContext(egl_dpy, config, NULL, ctx_attrs);
    const int surf_attrs[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    egl_surf = egl_ctx ? egl_CreatePbufferSurface(egl_dpy, config, surf_attrs) : NULL;
    if (!egl_ctx || !egl_surf || !egl_MakeCurrent(egl_dpy, egl_surf, egl_surf, egl_ctx)) {
        snprintf(last_error, sizeof(last_error), "contexte OpenGL ES %d impossible (0x%x)", version, egl_GetError());
        return false;
    }
    __android_log_print(LOG_INFO, TAG, "contexte OpenGL ES %d prêt, framebuffer %ux%u", version, w, h);
    if (!hw_storage(w, h)) return false;
    hw_live = true;
    if (hw.context_reset) hw.context_reset();
    return true;
}

static void hw_stop(void) {
    if (hw_live && hw.context_destroy) hw.context_destroy();
    hw_live = false;
    if (egl_ctx) {
        if (hw_fbo) gl_DeleteFramebuffers(1, &hw_fbo);
        if (hw_tex) gl_DeleteTextures(1, &hw_tex);
        if (hw_depth) gl_DeleteRenderbuffers(1, &hw_depth);
        egl_MakeCurrent(egl_dpy, NULL, NULL, NULL);
        if (egl_surf) egl_DestroySurface(egl_dpy, egl_surf);
        egl_DestroyContext(egl_dpy, egl_ctx);
    }
    egl_ctx = egl_surf = NULL;
    hw_fbo = hw_tex = hw_depth = 0;
    hw_w = hw_h = hw_want_w = hw_want_h = 0;
    free(hw_row);
    hw_row = NULL;
}

/** Recopie l'image rendue (w × h, en bas à gauche du framebuffer) dans le tampon Java. */
static void hw_read(unsigned w, unsigned h) {
    if (w > hw_w) w = hw_w;
    if (h > hw_h) h = hw_h;
    GLint rfb = 0, pbo = 0, align = 4, len = 0, skr = 0, skp = 0;
    gl_GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &rfb);
    gl_GetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &pbo);
    gl_GetIntegerv(GL_PACK_ALIGNMENT, &align);
    gl_GetIntegerv(GL_PACK_ROW_LENGTH, &len);
    gl_GetIntegerv(GL_PACK_SKIP_ROWS, &skr);
    gl_GetIntegerv(GL_PACK_SKIP_PIXELS, &skp);
    if (pbo) gl_BindBuffer(GL_PIXEL_PACK_BUFFER, 0);
    gl_PixelStorei(GL_PACK_ALIGNMENT, 4);
    gl_PixelStorei(GL_PACK_ROW_LENGTH, 0);
    gl_PixelStorei(GL_PACK_SKIP_ROWS, 0);
    gl_PixelStorei(GL_PACK_SKIP_PIXELS, 0);
    gl_BindFramebuffer(GL_READ_FRAMEBUFFER, hw_fbo);
    gl_ReadPixels(0, 0, (GLsizei) w, (GLsizei) h, GL_RGBA, GL_UNSIGNED_BYTE, frame_out);
    gl_BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint) rfb);
    if (pbo) gl_BindBuffer(GL_PIXEL_PACK_BUFFER, (GLuint) pbo);
    gl_PixelStorei(GL_PACK_ALIGNMENT, align);
    gl_PixelStorei(GL_PACK_ROW_LENGTH, len);
    gl_PixelStorei(GL_PACK_SKIP_ROWS, skr);
    gl_PixelStorei(GL_PACK_SKIP_PIXELS, skp);
    /* OpenGL lit de bas en haut : on retourne l'image et on force l'opacité. */
    size_t row = (size_t) w * 4;
    for (unsigned y = 0; y < h; y++) {
        uint32_t *p = frame_out + (size_t) y * w;
        for (unsigned x = 0; x < w; x++) p[x] |= 0xFF000000u;
    }
    if (hw.bottom_left_origin) {
        for (unsigned y = 0; y < h / 2; y++) {
            uint32_t *a = frame_out + (size_t) y * w;
            uint32_t *b = frame_out + (size_t) (h - 1 - y) * w;
            memcpy(hw_row, a, row);
            memcpy(a, b, row);
            memcpy(b, hw_row, row);
        }
    }
}

static bool environment_cb(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *) data = true;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            enum retro_pixel_format f = *(const enum retro_pixel_format *) data;
            if (f > RETRO_PIXEL_FORMAT_RGB565) return false;
            pixel_format = f;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char **) data = system_dir;
            return system_dir != NULL;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **) data = save_dir;
            return save_dir != NULL;
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            *(const char **) data = system_dir;
            return system_dir != NULL;
        case RETRO_ENVIRONMENT_GET_LIBRETRO_PATH:
            *(const char **) data = core_path;
            return true;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            ((struct retro_log_callback *) data)->log = log_cb;
            return true;
        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE: {
            struct retro_perf_callback *p = data;
            p->get_time_usec = perf_time_usec;
            p->get_cpu_features = perf_cpu_features;
            p->get_perf_counter = perf_counter;
            p->perf_register = perf_register;
            p->perf_start = perf_start;
            p->perf_stop = perf_stop;
            p->perf_log = perf_log;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            ((struct retro_rumble_interface *) data)->set_rumble_state = rumble_cb;
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            /* Version 0 : les cœurs repassent par SET_VARIABLES, plus simple à gérer. */
            *(unsigned *) data = 0;
            return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            const struct retro_variable *v = data;
            for (; v && v->key; v++) var_define(v->key, v->value);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *v = data;
            int i = v && v->key ? var_index(v->key) : -1;
            if (i < 0) {
                if (v) v->value = NULL;
                return false;
            }
            v->value = var_values[i];
            return true;
        }
        case RETRO_ENVIRONMENT_SET_VARIABLE: {
            const struct retro_variable *v = data;
            if (!v) return true;
            if (v->key && v->value) var_put(v->key, v->value, true);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool *) data = vars_dirty;
            vars_dirty = false;
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const struct retro_game_geometry *g = data;
            aspect = g->aspect_ratio > 0 ? g->aspect_ratio
                   : g->base_height ? (float) g->base_width / g->base_height : aspect;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            const struct retro_system_av_info *av = data;
            aspect = av->geometry.aspect_ratio > 0 ? av->geometry.aspect_ratio
                   : av->geometry.base_height ? (float) av->geometry.base_width / av->geometry.base_height : aspect;
            if (av->timing.fps > 0) fps = av->timing.fps;
            if (av->timing.sample_rate > 0) sample_rate = av->timing.sample_rate;
            /* Résolution interne relevée en cours de jeu : le framebuffer grandit avant la prochaine image. */
            if (hw_requested) {
                hw_want_w = av->geometry.max_width;
                hw_want_h = av->geometry.max_height;
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_FRAME_TIME_CALLBACK:
            frame_time_cb = *(const struct retro_frame_time_callback *) data;
            has_frame_time_cb = true;
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned *) data = RETRO_LANGUAGE_FRENCH;
            return true;
        case RETRO_ENVIRONMENT_GET_USERNAME:
            *(const char **) data = "Joueur";
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_MAX_USERS:
            *(unsigned *) data = 1;
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            if (data) *(int *) data = 3;
            return true;
        case RETRO_ENVIRONMENT_GET_FASTFORWARDING:
            if (data) *(bool *) data = fast_forward;
            return true;
        case RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE:
            *(float *) data = 60.0f;
            return true;
        case RETRO_ENVIRONMENT_GET_JIT_CAPABLE:
            *(bool *) data = true;
            return true;
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            const struct retro_message *m = data;
            if (m && m->msg) __android_log_print(LOG_INFO, TAG, "message : %s", m->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT: {
            const struct retro_message_ext *m = data;
            if (m && m->msg) __android_log_print(LOG_INFO, TAG, "message : %s", m->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            /* OpenGL ES seulement : pas d'OpenGL de bureau sur Android, et Vulkan n'est pas branché. */
            struct retro_hw_render_callback *cb = data;
            bool es = cb->context_type == RETRO_HW_CONTEXT_OPENGLES2 || cb->context_type == RETRO_HW_CONTEXT_OPENGLES3
                   || (cb->context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION && cb->version_major <= 3);
            if (!es) {
                snprintf(last_error, sizeof(last_error), "ce cœur demande un rendu (%d) non géré", (int) cb->context_type);
                return false;
            }
            cb->get_current_framebuffer = hw_get_fbo;
            cb->get_proc_address = hw_get_proc;
            hw = *cb;
            hw_requested = true;
            last_error[0] = 0;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
            *(unsigned *) data = RETRO_HW_CONTEXT_OPENGLES3;
            return true;
        case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT:
            return true;
        case RETRO_ENVIRONMENT_SET_ROTATION:
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE:
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK:
        case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
        case RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE:
            return true;
        default:
            return false;
    }
}

static void video_cb(const void *data, unsigned w, unsigned h, size_t pitch) {
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        if (!hw_live || !frame_out) return;
        if (w > hw_w) w = hw_w;
        if (h > hw_h) h = hw_h;
        frame_w = w;
        frame_h = h;
        if ((size_t) w * h > frame_capacity) {
            frame_new = 2;
            return;
        }
        hw_read(w, h);
        frame_new = 1;
        return;
    }
    if (!data || !frame_out) return;
    if ((size_t) w * h > frame_capacity) {
        /* Le tampon Java est trop petit : on garde l'image d'avant, Java agrandira au prochain tour. */
        frame_w = w;
        frame_h = h;
        frame_new = 2;
        return;
    }
    uint32_t *out = frame_out;
    const uint8_t *row = data;
    /* Sortie RGBA8888 en mémoire (octets R, G, B, A) : c'est l'ordre d'un Bitmap ARGB_8888. */
    if (pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
        for (unsigned y = 0; y < h; y++, row += pitch) {
            const uint32_t *s = (const uint32_t *) row;
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = s[x];
                *out++ = 0xFF000000u | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
            }
        }
    } else if (pixel_format == RETRO_PIXEL_FORMAT_RGB565) {
        for (unsigned y = 0; y < h; y++, row += pitch) {
            const uint16_t *s = (const uint16_t *) row;
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = s[x];
                uint32_t r = (p >> 11) & 0x1F, g = (p >> 5) & 0x3F, b = p & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 2) | (g >> 4);
                b = (b << 3) | (b >> 2);
                *out++ = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    } else {
        for (unsigned y = 0; y < h; y++, row += pitch) {
            const uint16_t *s = (const uint16_t *) row;
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = s[x];
                uint32_t r = (p >> 10) & 0x1F, g = (p >> 5) & 0x1F, b = p & 0x1F;
                r = (r << 3) | (r >> 2);
                g = (g << 3) | (g >> 2);
                b = (b << 3) | (b >> 2);
                *out++ = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    }
    frame_w = w;
    frame_h = h;
    frame_new = 1;
}

static void audio_sample_cb(int16_t l, int16_t r) {
    if (audio_len + 2 > AUDIO_CAP) return;
    audio_buf[audio_len++] = l;
    audio_buf[audio_len++] = r;
}

static size_t audio_batch_cb(const int16_t *data, size_t frames) {
    size_t n = frames * 2;
    if (audio_len + n > AUDIO_CAP) n = AUDIO_CAP - audio_len;
    memcpy(audio_buf + audio_len, data, n * sizeof(int16_t));
    audio_len += n;
    return frames;
}

static void input_poll_cb(void) {}

static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= MAX_PORTS) return 0;
    switch (device) {
        case RETRO_DEVICE_JOYPAD:
            if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t) (port_buttons[port] & 0xFFFF);
            return id < 16 ? (int16_t) ((port_buttons[port] >> id) & 1) : 0;
        case RETRO_DEVICE_ANALOG:
            if (index < 2 && id < 2) return port_analog[port][index][id];
            return 0;
        case RETRO_DEVICE_POINTER:
            if (port != 0) return 0;
            if (id == RETRO_DEVICE_ID_POINTER_X) return (int16_t) pointer_x;
            if (id == RETRO_DEVICE_ID_POINTER_Y) return (int16_t) pointer_y;
            if (id == RETRO_DEVICE_ID_POINTER_PRESSED) return (int16_t) pointer_down;
            return 0;
        default:
            return 0;
    }
}

/* ------------------------------------------------------------------ JNI */

#define FN(name) Java_com_hideo_foldplay_Libretro_##name

static char *jdup(JNIEnv *env, jstring s) {
    if (!s) return NULL;
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    char *d = dupstr(c);
    (*env)->ReleaseStringUTFChars(env, s, c);
    return d;
}

#define SYM(var, name) do { \
        *(void **) &var = dlsym(core, name); \
        if (!var) { snprintf(last_error, sizeof(last_error), "symbole %s absent", name); return JNI_FALSE; } \
    } while (0)

JNIEXPORT jboolean JNICALL FN(nativeOpen)(JNIEnv *env, jclass cls, jstring path, jstring sys, jstring save) {
    (void) cls;
    last_error[0] = 0;
    if (core) {
        snprintf(last_error, sizeof(last_error), "un cœur est déjà chargé");
        return JNI_FALSE;
    }
    free(core_path);
    free(system_dir);
    free(save_dir);
    core_path = jdup(env, path);
    system_dir = jdup(env, sys);
    save_dir = jdup(env, save);
    core = dlopen(core_path, RTLD_NOW);
    if (!core) {
        const char *e = dlerror();
        snprintf(last_error, sizeof(last_error), "%s", e ? e : "dlopen a échoué");
        return JNI_FALSE;
    }
    SYM(p_init, "retro_init");
    SYM(p_deinit, "retro_deinit");
    SYM(p_api_version, "retro_api_version");
    SYM(p_get_system_info, "retro_get_system_info");
    SYM(p_get_system_av_info, "retro_get_system_av_info");
    SYM(p_set_environment, "retro_set_environment");
    SYM(p_set_video_refresh, "retro_set_video_refresh");
    SYM(p_set_audio_sample, "retro_set_audio_sample");
    SYM(p_set_audio_sample_batch, "retro_set_audio_sample_batch");
    SYM(p_set_input_poll, "retro_set_input_poll");
    SYM(p_set_input_state, "retro_set_input_state");
    SYM(p_set_controller_port_device, "retro_set_controller_port_device");
    SYM(p_reset, "retro_reset");
    SYM(p_run, "retro_run");
    SYM(p_serialize_size, "retro_serialize_size");
    SYM(p_serialize, "retro_serialize");
    SYM(p_unserialize, "retro_unserialize");
    SYM(p_load_game, "retro_load_game");
    SYM(p_unload_game, "retro_unload_game");
    SYM(p_get_memory_data, "retro_get_memory_data");
    SYM(p_get_memory_size, "retro_get_memory_size");
    *(void **) &p_cheat_reset = dlsym(core, "retro_cheat_reset");
    *(void **) &p_cheat_set = dlsym(core, "retro_cheat_set");

    pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
    has_frame_time_cb = false;
    hw_requested = false;
    memset(&hw, 0, sizeof(hw));
    audio_len = 0;
    memset((void *) port_buttons, 0, sizeof(port_buttons));
    memset((void *) port_analog, 0, sizeof(port_analog));
    p_set_environment(environment_cb);
    p_init();
    p_set_video_refresh(video_cb);
    p_set_audio_sample(audio_sample_cb);
    p_set_audio_sample_batch(audio_batch_cb);
    p_set_input_poll(input_poll_cb);
    p_set_input_state(input_state_cb);
    initialized = true;
    return JNI_TRUE;
}

/** [nom, version, extensions, need_fullpath ("1"/"0"), block_extract ("1"/"0")] */
JNIEXPORT jobjectArray JNICALL FN(nativeSystemInfo)(JNIEnv *env, jclass cls) {
    (void) cls;
    if (!core) return NULL;
    struct retro_system_info si;
    memset(&si, 0, sizeof(si));
    p_get_system_info(&si);
    jobjectArray arr = (*env)->NewObjectArray(env, 5, (*env)->FindClass(env, "java/lang/String"), NULL);
    (*env)->SetObjectArrayElement(env, arr, 0, (*env)->NewStringUTF(env, si.library_name ? si.library_name : ""));
    (*env)->SetObjectArrayElement(env, arr, 1, (*env)->NewStringUTF(env, si.library_version ? si.library_version : ""));
    (*env)->SetObjectArrayElement(env, arr, 2, (*env)->NewStringUTF(env, si.valid_extensions ? si.valid_extensions : ""));
    (*env)->SetObjectArrayElement(env, arr, 3, (*env)->NewStringUTF(env, si.need_fullpath ? "1" : "0"));
    (*env)->SetObjectArrayElement(env, arr, 4, (*env)->NewStringUTF(env, si.block_extract ? "1" : "0"));
    return arr;
}

JNIEXPORT void JNICALL FN(nativeSetVariable)(JNIEnv *env, jclass cls, jstring key, jstring value) {
    (void) cls;
    char *k = jdup(env, key);
    char *v = jdup(env, value);
    if (k && v) var_put(k, v, true);
    free(k);
    free(v);
}

JNIEXPORT jboolean JNICALL FN(nativeLoadGame)(JNIEnv *env, jclass cls, jstring path, jbyteArray data) {
    (void) cls;
    if (!core) return JNI_FALSE;
    struct retro_game_info gi;
    memset(&gi, 0, sizeof(gi));
    char *p = jdup(env, path);
    gi.path = p;
    if (data) {
        jsize n = (*env)->GetArrayLength(env, data);
        game_data = malloc(n > 0 ? (size_t) n : 1);
        if (!game_data) {
            free(p);
            snprintf(last_error, sizeof(last_error), "mémoire insuffisante pour le jeu");
            return JNI_FALSE;
        }
        (*env)->GetByteArrayRegion(env, data, 0, n, game_data);
        gi.data = game_data;
        gi.size = (size_t) n;
    }
    bool ok = p_load_game(&gi);
    free(p);
    if (!ok) {
        free(game_data);
        game_data = NULL;
        if (!last_error[0]) snprintf(last_error, sizeof(last_error), "le cœur a refusé ce jeu");
        return JNI_FALSE;
    }
    game_loaded = true;
    for (unsigned p = 0; p < MAX_PORTS; p++) p_set_controller_port_device(p, RETRO_DEVICE_JOYPAD);
    struct retro_system_av_info av;
    memset(&av, 0, sizeof(av));
    p_get_system_av_info(&av);
    fps = av.timing.fps > 0 ? av.timing.fps : 60.0;
    sample_rate = av.timing.sample_rate > 0 ? av.timing.sample_rate : 44100.0;
    aspect = av.geometry.aspect_ratio > 0 ? av.geometry.aspect_ratio
           : av.geometry.base_height ? (float) av.geometry.base_width / av.geometry.base_height : 4.0f / 3.0f;
    frame_w = av.geometry.base_width;
    frame_h = av.geometry.base_height;
    if (hw_requested) {
        unsigned w = av.geometry.max_width > av.geometry.base_width ? av.geometry.max_width : av.geometry.base_width;
        unsigned h = av.geometry.max_height > av.geometry.base_height ? av.geometry.max_height : av.geometry.base_height;
        if (!hw_start(w ? w : 640, h ? h : 480)) {
            char why[512];
            snprintf(why, sizeof(why), "%s", last_error);
            hw_live = false;
            p_unload_game();
            game_loaded = false;
            hw_stop();
            free(game_data);
            game_data = NULL;
            snprintf(last_error, sizeof(last_error), "%s", why);
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

/** [fps, fréquence audio, rapport largeur/hauteur, largeur max, hauteur max] */
JNIEXPORT jdoubleArray JNICALL FN(nativeAvInfo)(JNIEnv *env, jclass cls) {
    (void) cls;
    struct retro_system_av_info av;
    memset(&av, 0, sizeof(av));
    if (core && game_loaded) p_get_system_av_info(&av);
    jdouble v[5] = {fps, sample_rate, aspect, av.geometry.max_width, av.geometry.max_height};
    jdoubleArray arr = (*env)->NewDoubleArray(env, 5);
    (*env)->SetDoubleArrayRegion(env, arr, 0, 5, v);
    return arr;
}

JNIEXPORT void JNICALL FN(nativeSetFrameBuffer)(JNIEnv *env, jclass cls, jobject buffer) {
    (void) cls;
    frame_out = buffer ? (*env)->GetDirectBufferAddress(env, buffer) : NULL;
    frame_capacity = buffer ? (size_t) (*env)->GetDirectBufferCapacity(env, buffer) / 4 : 0;
}

/**
 * Fait tourner une image. Renvoie 0 si rien de neuf à afficher, 1 si une image est prête dans le
 * tampon, 2 si le tampon est trop petit (Java en fournit un plus grand).
 */
JNIEXPORT jint JNICALL FN(nativeRun)(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    if (!game_loaded) return 0;
    frame_new = 0;
    if (hw_live && (hw_want_w > hw_w || hw_want_h > hw_h)) {
        unsigned w = hw_want_w > hw_w ? hw_want_w : hw_w;
        unsigned h = hw_want_h > hw_h ? hw_want_h : hw_h;
        hw_want_w = hw_want_h = 0;
        hw_storage(w, h);
    }
    if (has_frame_time_cb && frame_time_cb.callback) {
        retro_usec_t ref = frame_time_cb.reference ? frame_time_cb.reference : (retro_usec_t) (1000000.0 / fps);
        frame_time_cb.callback(ref);
    }
    p_run();
    return frame_new;
}

JNIEXPORT jint JNICALL FN(nativeFrameWidth)(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    return (jint) frame_w;
}

JNIEXPORT jint JNICALL FN(nativeFrameHeight)(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    return (jint) frame_h;
}

JNIEXPORT jfloat JNICALL FN(nativeAspect)(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    return aspect;
}

/** Copie le son accumulé depuis le dernier appel ; renvoie le nombre d'échantillons (int16). */
JNIEXPORT jint JNICALL FN(nativeDrainAudio)(JNIEnv *env, jclass cls, jshortArray out) {
    (void) cls;
    jsize cap = (*env)->GetArrayLength(env, out);
    jsize n = (jsize) audio_len < cap ? (jsize) audio_len : cap;
    n &= ~1;
    if (n > 0) (*env)->SetShortArrayRegion(env, out, 0, n, audio_buf);
    audio_len = 0;
    return n;
}

JNIEXPORT void JNICALL FN(nativeSetButtons)(JNIEnv *env, jclass cls, jint mask) {
    (void) env; (void) cls;
    buttons = (uint32_t) mask;
}

JNIEXPORT void JNICALL FN(nativeSetAnalog)(JNIEnv *env, jclass cls, jint stick, jint x, jint y) {
    (void) env; (void) cls;
    if (stick < 0 || stick > 1) return;
    analog[stick][0] = (int16_t) x;
    analog[stick][1] = (int16_t) y;
}

JNIEXPORT void JNICALL FN(nativeSetPortButtons)(JNIEnv *env, jclass cls, jint port, jint mask) {
    (void) env; (void) cls;
    if (port < 0 || port >= MAX_PORTS) return;
    port_buttons[port] = (uint32_t) mask;
}

JNIEXPORT void JNICALL FN(nativeSetPortAnalog)(JNIEnv *env, jclass cls, jint port, jint stick, jint x, jint y) {
    (void) env; (void) cls;
    if (port < 0 || port >= MAX_PORTS || stick < 0 || stick > 1) return;
    port_analog[port][stick][0] = (int16_t) x;
    port_analog[port][stick][1] = (int16_t) y;
}

JNIEXPORT void JNICALL FN(nativeSetPointer)(JNIEnv *env, jclass cls, jint x, jint y, jboolean down) {
    (void) env; (void) cls;
    pointer_x = x;
    pointer_y = y;
    pointer_down = down ? 1 : 0;
}

JNIEXPORT void JNICALL FN(nativeSetDebug)(JNIEnv *env, jclass cls, jboolean on) {
    debug_log = on;
}

JNIEXPORT void JNICALL FN(nativeSetFastForward)(JNIEnv *env, jclass cls, jboolean on) {
    fast_forward = on;
}

JNIEXPORT void JNICALL FN(nativeReset)(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    if (game_loaded) p_reset();
}

JNIEXPORT jbyteArray JNICALL FN(nativeSaveState)(JNIEnv *env, jclass cls) {
    (void) cls;
    if (!game_loaded) return NULL;
    size_t n = p_serialize_size();
    if (n == 0) return NULL;
    void *buf = malloc(n);
    if (!buf) return NULL;
    jbyteArray out = NULL;
    if (p_serialize(buf, n)) {
        out = (*env)->NewByteArray(env, (jsize) n);
        if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize) n, buf);
    }
    free(buf);
    return out;
}

JNIEXPORT jboolean JNICALL FN(nativeLoadState)(JNIEnv *env, jclass cls, jbyteArray state) {
    (void) cls;
    if (!game_loaded || !state) return JNI_FALSE;
    jsize n = (*env)->GetArrayLength(env, state);
    void *buf = malloc((size_t) n);
    if (!buf) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, state, 0, n, buf);
    bool ok = p_unserialize(buf, (size_t) n);
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/** La mémoire de sauvegarde du jeu (piles des cartouches), lue et écrite directement par Java. */
JNIEXPORT jobject JNICALL FN(nativeMemory)(JNIEnv *env, jclass cls, jint id) {
    (void) cls;
    if (!game_loaded) return NULL;
    void *p = p_get_memory_data((unsigned) id);
    size_t n = p_get_memory_size((unsigned) id);
    if (!p || n == 0) return NULL;
    return (*env)->NewDirectByteBuffer(env, p, (jlong) n);
}

JNIEXPORT jstring JNICALL FN(nativeLastError)(JNIEnv *env, jclass cls) {
    (void) cls;
    return (*env)->NewStringUTF(env, last_error);
}

JNIEXPORT void JNICALL FN(nativeClose)(JNIEnv *env, jclass cls) {
    fast_forward = false;
    (void) env; (void) cls;
    if (!core) return;
    /* Le cœur libère ses ressources OpenGL pendant que le contexte est encore actif. */
    if (hw_live && hw.context_destroy) hw.context_destroy();
    hw_live = false;
    if (game_loaded) p_unload_game();
    game_loaded = false;
    if (initialized) p_deinit();
    initialized = false;
    hw_stop();
    hw_requested = false;
    memset(&hw, 0, sizeof(hw));
    free(game_data);
    game_data = NULL;
    dlclose(core);
    core = NULL;
    vars_clear();
    frame_out = NULL;
    frame_capacity = 0;
    audio_len = 0;
}

/** Options du cœur, à plat : [clé, « Description; v1|v2 », valeur actuelle] × n. */
JNIEXPORT jobjectArray JNICALL FN(nativeVariables)(JNIEnv *env, jclass cls) {
    (void) cls;
    int n = 0;
    for (int i = 0; i < var_count; i++) if (var_specs[i]) n++;
    jobjectArray arr = (*env)->NewObjectArray(env, n * 3, (*env)->FindClass(env, "java/lang/String"), NULL);
    int j = 0;
    for (int i = 0; i < var_count; i++) {
        if (!var_specs[i]) continue;
        (*env)->SetObjectArrayElement(env, arr, j++, (*env)->NewStringUTF(env, var_keys[i]));
        (*env)->SetObjectArrayElement(env, arr, j++, (*env)->NewStringUTF(env, var_specs[i]));
        (*env)->SetObjectArrayElement(env, arr, j++, (*env)->NewStringUTF(env, var_values[i] ? var_values[i] : ""));
    }
    return arr;
}

JNIEXPORT jboolean JNICALL FN(nativeCheatReset)(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    if (!game_loaded || !p_cheat_reset) return JNI_FALSE;
    p_cheat_reset();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL FN(nativeCheatSet)(JNIEnv *env, jclass cls, jint index, jboolean on, jstring code) {
    (void) cls;
    if (!game_loaded || !p_cheat_set) return;
    char *c = jdup(env, code);
    if (c) p_cheat_set((unsigned) index, on ? true : false, c);
    free(c);
}
