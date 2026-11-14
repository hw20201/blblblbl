#define QJS_GUARD_TEST 1
#include "qjs_guard.c"

#include <assert.h>
#include <stdio.h>
#include <time.h>

#define WORKERS 16
#define TEST_MEMORY ((size_t)64 * 1024 * 1024)
#define TEST_STACK ((size_t)512 * 1024)

typedef struct FakeRuntime {
    JSInterruptHandler *callback;
    void *opaque;
    size_t memory;
    size_t stack;
} FakeRuntime;

static void fake_set_interrupt(JSRuntime *rt, JSInterruptHandler *callback, void *opaque) {
    FakeRuntime *fake = (FakeRuntime *)rt;
    fake->callback = callback;
    fake->opaque = opaque;
}
static void fake_set_memory(JSRuntime *rt, size_t bytes) { ((FakeRuntime *)rt)->memory = bytes; }
static void fake_set_stack(JSRuntime *rt, size_t bytes) { ((FakeRuntime *)rt)->stack = bytes; }

static void test_deadlines_and_clear(void) {
    FakeRuntime a = {0}, b = {0};
    Guard *ga = guard_create((JSRuntime *)&a, 50, TEST_MEMORY);
    Guard *gb = guard_create((JSRuntime *)&b, 500, TEST_MEMORY);
    assert(ga && gb);
    assert(a.callback && b.callback && a.opaque == ga && b.opaque == gb);
    assert(a.memory == TEST_MEMORY && b.memory == TEST_MEMORY);
    assert(a.stack == TEST_STACK && b.stack == TEST_STACK);
    assert(a.callback((JSRuntime *)&a, a.opaque) == 0);
    assert(b.callback((JSRuntime *)&b, b.opaque) == 0);
    const struct timespec pause = { .tv_sec = 0, .tv_nsec = 80000000 };
    assert(nanosleep(&pause, NULL) == 0);
    assert(a.callback((JSRuntime *)&a, a.opaque) != 0);
    assert(b.callback((JSRuntime *)&b, b.opaque) == 0);
    guard_destroy(ga);
    assert(a.callback == NULL && a.opaque == NULL);
    assert(b.callback((JSRuntime *)&b, b.opaque) == 0);
    guard_destroy(gb);
    assert(b.callback == NULL && b.opaque == NULL);
}

typedef struct WorkerArg { unsigned index; } WorkerArg;
static void *worker(void *opaque) {
    WorkerArg *arg = (WorkerArg *)opaque;
    FakeRuntime runtime = {0};
    Guard *g = guard_create((JSRuntime *)&runtime, 5000, TEST_MEMORY);
    assert(g && runtime.callback && runtime.opaque == g);
    assert(runtime.memory == TEST_MEMORY && runtime.stack == TEST_STACK);
    assert(runtime.callback((JSRuntime *)&runtime, runtime.opaque) == 0);
    guard_destroy(g);
    assert(runtime.callback == NULL && runtime.opaque == NULL);
    (void)arg;
    return NULL;
}

int main(void) {
    /* Function pointers are installed once before any worker thread starts. */
    p_set_interrupt = fake_set_interrupt;
    p_set_memory = fake_set_memory;
    p_set_stack = fake_set_stack;
    test_deadlines_and_clear();
    pthread_t threads[WORKERS];
    WorkerArg args[WORKERS];
    for (unsigned i = 0; i < WORKERS; ++i) {
        args[i].index = i;
        assert(pthread_create(&threads[i], NULL, worker, &args[i]) == 0);
    }
    for (unsigned i = 0; i < WORKERS; ++i) assert(pthread_join(threads[i], NULL) == 0);
    puts("qjs_guard_test: PASS");
    return 0;
}
