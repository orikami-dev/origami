// Pont PS3 d'Origami : charge le cœur ARMSX3 (libarmsx3-core.so, RPCS3 pour Android,
// GPL-2.0-only) et expose ses fonctions _rpcsx_* à com.hideo.foldplay.Ps3Core.
// Tourne dans le processus ":ps3" (Ps3Activity), jamais à côté du cœur PS2 (GPL-3).
// Compilé par native/build-ps3-bridge.sh avec le NDK (même libc++ que le cœur : les
// std::string_view et std::string traversent la frontière).
#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <android/log.h>
#include <string>
#include <string_view>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "OrigamiPS3", __VA_ARGS__)

namespace {

struct Core {
  bool (*initialize)(std::string_view rootDir, std::string_view user);
  void (*setSocInfo)(std::string_view socInfo);
  bool (*processCompilationQueue)(JNIEnv *env);
  bool (*startMainThreadProcessor)(JNIEnv *env);
  int (*boot)(std::string_view path);
  int (*getState)();
  void (*kill)();
  void (*resume)();
  void (*pause)();
  void (*setAppBackgrounded)(bool);
  void (*openHomeMenu)();
  std::string (*getTitle)();
  std::string (*getTitleId)();
  unsigned long long (*getFramePeriodNs)();
  bool (*surfaceEvent)(JNIEnv *env, jobject surface, jint event);
  void (*surfaceSizeChanged)(int width, int height);
  bool (*overlayPadData)(int port, int d1, int d2, int lx, int ly, int rx, int ry);
  bool (*installFw)(JNIEnv *env, int fd, long progressId);
  bool (*install)(JNIEnv *env, int fd, long progressId);
  bool (*settingsSet)(std::string_view path, std::string_view value);
  std::string (*settingsGet)(std::string_view path);
  bool (*saveStateToSlot)(unsigned int slot);
  bool (*loadStateFromSlot)(unsigned int slot);
  bool (*hasStateInSlot)(unsigned int slot);
  std::string (*systemInfo)();
  std::string (*getVersion)();
};

Core core{};
void *handle = nullptr;
JavaVM *vm = nullptr;
std::string lastError;

template <typename T> void bind(T &fn, const char *name) {
  fn = reinterpret_cast<T>(dlsym(handle, name));
  if (!fn) LOG("symbole absent : %s", name);
}

std::string str(JNIEnv *env, jstring s) {
  if (!s) return {};
  const char *c = env->GetStringUTFChars(s, nullptr);
  std::string out(c ? c : "");
  if (c) env->ReleaseStringUTFChars(s, c);
  return out;
}

jstring jstr(JNIEnv *env, const std::string &s) { return env->NewStringUTF(s.c_str()); }

// Boucles bloquantes que le cœur attend qu'on fasse tourner (file du « fil principal » et
// compilation PPU/SPU), chacune sur un fil attaché à la JVM pour toute la vie du processus.
void *pump(void *arg) {
  JNIEnv *env = nullptr;
  JavaVMAttachArgs args{JNI_VERSION_1_6, arg ? "ps3-main-thread" : "ps3-compile", nullptr};
  if (vm->AttachCurrentThread(&env, &args) != JNI_OK) return nullptr;
  if (arg) {
    if (core.startMainThreadProcessor) core.startMainThreadProcessor(env);
  } else {
    if (core.processCompilationQueue) core.processCompilationQueue(env);
  }
  vm->DetachCurrentThread();
  return nullptr;
}

} // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *v, void *) {
  vm = v;
  return JNI_VERSION_1_6;
}

#define FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_hideo_foldplay_Ps3Core_##name

FN(jboolean, nativeOpen)(JNIEnv *env, jclass, jstring path) {
  if (handle) return JNI_TRUE;
  std::string p = str(env, path);
  handle = dlopen(p.c_str(), RTLD_LOCAL | RTLD_NOW);
  if (!handle) {
    const char *e = dlerror();
    lastError = e ? e : "dlopen";
    LOG("cœur non chargé : %s", lastError.c_str());
    return JNI_FALSE;
  }
  bind(core.initialize, "_rpcsx_initialize");
  bind(core.setSocInfo, "_rpcsx_setSocInfo");
  bind(core.processCompilationQueue, "_rpcsx_processCompilationQueue");
  bind(core.startMainThreadProcessor, "_rpcsx_startMainThreadProcessor");
  bind(core.boot, "_rpcsx_boot");
  bind(core.getState, "_rpcsx_getState");
  bind(core.kill, "_rpcsx_kill");
  bind(core.resume, "_rpcsx_resume");
  bind(core.pause, "_rpcsx_pause");
  bind(core.setAppBackgrounded, "_rpcsx_setAppBackgrounded");
  bind(core.openHomeMenu, "_rpcsx_openHomeMenu");
  bind(core.getTitle, "_rpcsx_getTitle");
  bind(core.getTitleId, "_rpcsx_getTitleId");
  bind(core.getFramePeriodNs, "_rpcsx_getFramePeriodNs");
  bind(core.surfaceEvent, "_rpcsx_surfaceEvent");
  bind(core.surfaceSizeChanged, "_rpcsx_surfaceSizeChanged");
  bind(core.overlayPadData, "_rpcsx_overlayPadData");
  bind(core.installFw, "_rpcsx_installFw");
  bind(core.install, "_rpcsx_install");
  bind(core.settingsSet, "_rpcsx_settingsSet");
  bind(core.settingsGet, "_rpcsx_settingsGet");
  bind(core.saveStateToSlot, "_rpcsx_saveStateToSlot");
  bind(core.loadStateFromSlot, "_rpcsx_loadStateFromSlot");
  bind(core.hasStateInSlot, "_rpcsx_hasStateInSlot");
  bind(core.systemInfo, "_rpcsx_systemInfo");
  bind(core.getVersion, "_rpcsx_getVersion");
  return core.initialize && core.boot && core.surfaceEvent ? JNI_TRUE : JNI_FALSE;
}

FN(jstring, nativeLastError)(JNIEnv *env, jclass) { return jstr(env, lastError); }

FN(jboolean, nativeInitialize)(JNIEnv *env, jclass, jstring root, jstring user, jstring soc) {
  if (!core.initialize) return JNI_FALSE;
  if (core.setSocInfo) core.setSocInfo(str(env, soc));
  bool ok = core.initialize(str(env, root), str(env, user));
  static bool pumps = false;
  if (ok && !pumps) {
    pumps = true;
    pthread_t t;
    pthread_create(&t, nullptr, pump, reinterpret_cast<void *>(1));
    pthread_detach(t);
    pthread_create(&t, nullptr, pump, nullptr);
    pthread_detach(t);
  }
  return ok ? JNI_TRUE : JNI_FALSE;
}

FN(jint, nativeBoot)(JNIEnv *env, jclass, jstring path) {
  return core.boot ? core.boot(str(env, path)) : -1;
}

FN(jint, nativeState)(JNIEnv *, jclass) { return core.getState ? core.getState() : -1; }
FN(void, nativeKill)(JNIEnv *, jclass) { if (core.kill) core.kill(); }
FN(void, nativePause)(JNIEnv *, jclass) { if (core.pause) core.pause(); }
FN(void, nativeResume)(JNIEnv *, jclass) { if (core.resume) core.resume(); }
FN(void, nativeBackground)(JNIEnv *, jclass, jboolean b) { if (core.setAppBackgrounded) core.setAppBackgrounded(b); }
FN(void, nativeHomeMenu)(JNIEnv *, jclass) { if (core.openHomeMenu) core.openHomeMenu(); }

FN(jstring, nativeTitle)(JNIEnv *env, jclass) {
  return jstr(env, core.getTitle ? core.getTitle() : std::string());
}

FN(jstring, nativeTitleId)(JNIEnv *env, jclass) {
  return jstr(env, core.getTitleId ? core.getTitleId() : std::string());
}

FN(jlong, nativeFramePeriodNs)(JNIEnv *, jclass) {
  return core.getFramePeriodNs ? static_cast<jlong>(core.getFramePeriodNs()) : 0;
}

FN(jboolean, nativeSurface)(JNIEnv *env, jclass, jobject surface, jint event) {
  return core.surfaceEvent && core.surfaceEvent(env, surface, event) ? JNI_TRUE : JNI_FALSE;
}

FN(void, nativeSurfaceSize)(JNIEnv *, jclass, jint w, jint h) {
  if (core.surfaceSizeChanged) core.surfaceSizeChanged(w, h);
}

FN(jboolean, nativePad)(JNIEnv *, jclass, jint port, jint d1, jint d2, jint lx, jint ly, jint rx, jint ry) {
  return core.overlayPadData && core.overlayPadData(port, d1, d2, lx, ly, rx, ry) ? JNI_TRUE : JNI_FALSE;
}

FN(jboolean, nativeInstallFw)(JNIEnv *env, jclass, jint fd, jlong id) {
  return core.installFw && core.installFw(env, fd, static_cast<long>(id)) ? JNI_TRUE : JNI_FALSE;
}

FN(jboolean, nativeInstallPkg)(JNIEnv *env, jclass, jint fd, jlong id) {
  return core.install && core.install(env, fd, static_cast<long>(id)) ? JNI_TRUE : JNI_FALSE;
}

FN(jboolean, nativeSettingsSet)(JNIEnv *env, jclass, jstring path, jstring value) {
  return core.settingsSet && core.settingsSet(str(env, path), str(env, value)) ? JNI_TRUE : JNI_FALSE;
}

FN(jstring, nativeSettingsGet)(JNIEnv *env, jclass, jstring path) {
  return jstr(env, core.settingsGet ? core.settingsGet(str(env, path)) : std::string());
}

FN(jboolean, nativeSaveState)(JNIEnv *, jclass, jint slot) {
  return core.saveStateToSlot && core.saveStateToSlot(slot) ? JNI_TRUE : JNI_FALSE;
}

FN(jboolean, nativeLoadState)(JNIEnv *, jclass, jint slot) {
  return core.loadStateFromSlot && core.loadStateFromSlot(slot) ? JNI_TRUE : JNI_FALSE;
}

FN(jboolean, nativeHasState)(JNIEnv *, jclass, jint slot) {
  return core.hasStateInSlot && core.hasStateInSlot(slot) ? JNI_TRUE : JNI_FALSE;
}

FN(jstring, nativeVersion)(JNIEnv *env, jclass) {
  return jstr(env, core.getVersion ? core.getVersion() : std::string());
}
