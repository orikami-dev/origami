/*
 * libfoldplay_cores.so (0.66.24) : épingle un fil du processus sur des cœurs choisis (sched_setaffinity).
 * Aucune dépendance : appel système direct, compilable sans NDK (native/build-cores.sh).
 */
typedef int jint;
typedef long long jlong;
typedef void *JNIEnv;
typedef void *jclass;

static long sys3(long n, long a, long b, long c) {
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2) : "memory");
    return x0;
}

/** Rend 0 si le fil tid est épinglé sur les cœurs du masque (bit n = cœur n), sinon le code d'erreur négatif. */
__attribute__((visibility("default")))
jint Java_com_hideo_foldplay_CoreAffinity_nativePin(JNIEnv *env, jclass cls, jint tid, jlong mask) {
    unsigned long m[2] = {(unsigned long) mask, 0};
    return (jint) sys3(122 /* sched_setaffinity */, tid, sizeof(m), (long) m);
}
