#include "host_callbacks.h"
#include <android/native_window_jni.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>
#include <cstring>
#include <vector>

namespace {
const omni_ps3_api* api = nullptr;
template<class Read> jstring read_string(JNIEnv* env, Read read) {
    // Metadata reads can perform I/O; avoid probing the disc twice.
    std::vector<char> buffer(1024 * 1024);
    const auto needed = read(buffer.data(), buffer.size());
    if (!needed || needed > buffer.size()) { ps3::fail(env, "Invalid engine response"); return nullptr; }
    return ps3::java_string(env, buffer.data());
}
}
#define JNI_METHOD(name) Java_com_omnidroid_app_shared_ps3_Armsx3Native_##name
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(cpuError)(JNIEnv* env, jobject) {
#if defined(__aarch64__)
    const auto required = HWCAP_ATOMICS | HWCAP_ASIMDRDM | HWCAP_FPHP | HWCAP_ASIMDHP | HWCAP_ASIMDDP;
    if ((getauxval(AT_HWCAP) & required) == required) return nullptr;
    return ps3::java_string(env, "PS3 requires an ARM64 CPU with LSE, RDM, FP16 and dot-product instructions.");
#else
    return ps3::java_string(env, "PS3 requires an ARM64 device.");
#endif
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(open)(JNIEnv* env, jobject) {
    if (api) return;
    const auto* contract = omni_ps3_get_api(OMNI_PS3_ABI_VERSION);
    if (!contract || contract->size < sizeof(omni_ps3_api) || contract->version != OMNI_PS3_ABI_VERSION ||
        !contract->profile || std::strcmp(contract->profile, "omnidroid-engine-only")) {
        ps3::fail(env, "The installed PS3 engine is incompatible. Update PS3 support in Omnidroid."); return;
    }
    api = contract;
}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(initialize)(JNIEnv* env, jobject, jstring root, jstring cache, jclass host, jclass storage) {
    if (!api || !ps3::attach(env, host, storage)) return false;
    const auto callbacks = ps3::callbacks();
    return api->initialize(ps3::utf8(env, root).c_str(), ps3::utf8(env, cache).c_str(), &callbacks) == OMNI_PS3_OK;
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(boot)(JNIEnv* env, jobject, jstring path) { return api ? api->boot(ps3::utf8(env, path).c_str()) : OMNI_PS3_FAILED; }
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(state)(JNIEnv*, jobject) { return api ? api->state() : 0; }
extern "C" JNIEXPORT void JNICALL JNI_METHOD(control)(JNIEnv*, jobject, jint action) { if (api) { if (action == 0) api->pause(); else if (action == 1) api->resume(); else if (action == 2) api->stop(); } }
extern "C" JNIEXPORT void JNICALL JNI_METHOD(surface)(JNIEnv* env, jobject, jobject surface, jint width, jint height) {
    if (!api) return;
    auto* window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    api->surface(window, width > 0 ? width : 0, height > 0 ? height : 0);
    if (window) ANativeWindow_release(window);
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(pad)(JNIEnv*, jobject, jint port, jint d1, jint d2, jint lx, jint ly, jint rx, jint ry) { if (api && port >= 0 && port < 7) api->pad(port, d1, d2, lx, ly, rx, ry); }
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(setting)(JNIEnv* env, jobject, jstring path, jstring value) { return api && api->setting(ps3::utf8(env, path).c_str(), ps3::utf8(env, value).c_str()) == OMNI_PS3_OK; }
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(settings)(JNIEnv* env, jobject, jstring path) {
    if (!api) return nullptr;
    const auto key = ps3::utf8(env, path);
    return read_string(env, [&](char* buffer, size_t capacity) { return api->settings(key.c_str(), buffer, capacity); });
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(probe)(JNIEnv* env, jobject, jstring path, jstring icon) {
    if (!api) return nullptr;
    const auto game = ps3::utf8(env, path), destination = ps3::utf8(env, icon);
    return read_string(env, [&](char* buffer, size_t capacity) { return api->probe(game.c_str(), destination.c_str(), buffer, capacity); });
}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(stateSlot)(JNIEnv*, jobject, jboolean save, jint slot) { return api && slot >= 0 && (save ? api->save_slot(slot) : api->load_slot(slot)) == OMNI_PS3_OK; }
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(install)(JNIEnv*, jobject, jint kind, jint fd, jlong id) { return api && api->install(kind, fd, id) == OMNI_PS3_OK; }
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(dialogResult)(JNIEnv* env, jobject, jlong token, jint status, jstring text) { return api && api->dialog_result(token, status, ps3::utf8(env, text).c_str()) == OMNI_PS3_OK; }

extern "C" JNIEXPORT void JNICALL JNI_METHOD(displayRate)(JNIEnv*, jobject, jfloat hz) { if (api) api->display_rate(hz); }
extern "C" JNIEXPORT void JNICALL JNI_METHOD(performanceOptions)(JNIEnv*, jobject, jint options) { if (api) api->performance_options(options); }
extern "C" JNIEXPORT jlongArray JNICALL JNI_METHOD(performanceStats)(JNIEnv* env, jobject) {
    omni_ps3_performance stats{};
    if (api) api->performance_stats(&stats);
    const jlong values[] = {static_cast<jlong>(stats.frames), static_cast<jlong>(stats.period_ns),
        static_cast<jlong>(stats.work_ns), static_cast<jlong>(stats.last_period_ns), static_cast<jlong>(stats.last_work_ns),
        static_cast<jlong>(stats.submitted), static_cast<jlong>(stats.errors), static_cast<jlong>(stats.sessions_created),
        static_cast<jlong>(stats.sessions_closed), stats.rsx_tid, stats.hint_tid, stats.options, stats.active, stats.display_millihz};
    auto result = env->NewLongArray(14);
    if (result) env->SetLongArrayRegion(result, 0, 14, values);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(stateOperationFailed)(JNIEnv*, jobject) { return api && api->state_operation_failed() != 0; }
