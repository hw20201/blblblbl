/* QuickJS 1.4.6 interrupt guard. The app uses createRuntime(), not the optional
 * event-queue runtime: creation, install, evaluation, detach and disposal all
 * run on the caller/owner thread. Each runtime owns an immutable deadline;
 * no global deadline can be reset by a different execution. */
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <pthread.h>
#include <time.h>
#ifndef QJS_GUARD_TEST
#include <jni.h>
#include <dlfcn.h>
#endif

typedef struct JSContext JSContext;
typedef struct JSRuntime JSRuntime;
typedef int JSInterruptHandler(JSRuntime *, void *);
typedef void (*SetInterruptFn)(JSRuntime *, JSInterruptHandler *, void *);
typedef void (*SetSizeFn)(JSRuntime *, size_t);
static SetInterruptFn p_set_interrupt;
static SetSizeFn p_set_memory;
static SetSizeFn p_set_stack;

typedef struct Guard {
    JSRuntime *runtime;
    int64_t deadline_ms;
    pthread_t owner;
} Guard;

static int64_t monotonic_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return -1;
    return (int64_t)ts.tv_sec * 1000 + (int64_t)ts.tv_nsec / 1000000;
}

static int guard_interrupt(JSRuntime *runtime, void *opaque) {
    Guard *guard = (Guard *)opaque;
    int64_t now = monotonic_ms();
    return !guard || guard->runtime != runtime || now < 0 || now >= guard->deadline_ms;
}

static Guard *guard_create(JSRuntime *runtime, int64_t timeout_ms, size_t memory_bytes) {
    int64_t now = monotonic_ms();
    if (!runtime || now < 0 || timeout_ms <= 0 || timeout_ms > 30000 ||
        memory_bytes == 0 || !p_set_interrupt || !p_set_memory || !p_set_stack) return NULL;
    Guard *guard = (Guard *)calloc(1, sizeof(*guard));
    if (!guard) return NULL;
    guard->runtime = runtime;
    guard->deadline_ms = now + timeout_ms;
    guard->owner = pthread_self();
    p_set_memory(runtime, memory_bytes);
    p_set_stack(runtime, 512u * 1024u);
    p_set_interrupt(runtime, guard_interrupt, guard);
    return guard;
}

static void guard_destroy(Guard *guard) {
    if (!guard || !pthread_equal(guard->owner, pthread_self())) return;
    /* No evaluator is running: detach before the owning context/runtime closes. */
    p_set_interrupt(guard->runtime, NULL, NULL);
    free(guard);
}

#ifndef QJS_GUARD_TEST
typedef JSRuntime *(*GetRuntimeFn)(JSContext *);
static GetRuntimeFn p_get_runtime;
static pthread_once_t symbols_once = PTHREAD_ONCE_INIT;
static int symbols_ready;

static void resolve_symbols(void) {
    /* Keep the handle open for process lifetime, including while guards exist. */
    void *handle = dlopen("libquickjs.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) handle = dlopen("libquickjs-android.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) return;
    p_get_runtime = (GetRuntimeFn)dlsym(handle, "JS_GetRuntime");
    p_set_interrupt = (SetInterruptFn)dlsym(handle, "JS_SetInterruptHandler");
    p_set_memory = (SetSizeFn)dlsym(handle, "JS_SetMemoryLimit");
    p_set_stack = (SetSizeFn)dlsym(handle, "JS_SetMaxStackSize");
    symbols_ready = p_get_runtime && p_set_interrupt && p_set_memory && p_set_stack;
}

JNIEXPORT jlong JNICALL
Java_com_github_mihomo_android_data_QuickJsGuard_nativeInstall(
    JNIEnv *env, jobject instance, jobject context, jlong timeout_ms, jlong memory_bytes) {
    (void)instance;
    if (!context || timeout_ms <= 0 || timeout_ms > 30000 || memory_bytes <= 0) return 0;
    pthread_once(&symbols_once, resolve_symbols);
    if (!symbols_ready) return 0;
    /* getContextPtr() is package-private in the actual 1.4.6 AAR. JNI can read
     * the retained final contextPtr field; do not pretend it is a public API. */
    jclass context_class = (*env)->GetObjectClass(env, context);
    if (!context_class) return 0;
    jfieldID field = (*env)->GetFieldID(env, context_class, "contextPtr", "J");
    (*env)->DeleteLocalRef(env, context_class);
    if (!field) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        return 0;
    }
    jlong pointer = (*env)->GetLongField(env, context, field);
    if (!pointer) return 0;
    JSRuntime *runtime = p_get_runtime((JSContext *)(intptr_t)pointer);
    Guard *guard = guard_create(runtime, (int64_t)timeout_ms, (size_t)memory_bytes);
    return (jlong)(intptr_t)guard;
}

JNIEXPORT jboolean JNICALL
Java_com_github_mihomo_android_data_QuickJsGuard_nativeClear(
    JNIEnv *env, jobject instance, jlong handle) {
    (void)env;
    (void)instance;
    if (!handle) return JNI_TRUE;
    Guard *guard = (Guard *)(intptr_t)handle;
    if (!pthread_equal(guard->owner, pthread_self())) return JNI_FALSE;
    guard_destroy(guard);
    return JNI_TRUE;
}
#endif
