#include "host_callbacks.h"
#include <android/log.h>
#include <codecvt>
#include <locale>
#include <cstdlib>

namespace ps3 {
namespace {
JavaVM* vm = nullptr;
jclass host = nullptr, storage = nullptr;
struct {
    jmethodID progress, installed, firmware, dialog;
    jmethodID stat, list, open, mkdir, remove, rename;
} methods{};

class callback_env {
    JNIEnv* env_ = nullptr;
public:
    callback_env() {
        if (!vm) return;
        if (vm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6) != JNI_OK) {
            struct attachment { bool attached = false; ~attachment() { if (attached) vm->DetachCurrentThread(); } };
            thread_local attachment tls;
            if (vm->AttachCurrentThreadAsDaemon(&env_, nullptr) != JNI_OK) { env_ = nullptr; return; }
            tls.attached = true;
        }
        if (env_->PushLocalFrame(32) < 0) env_ = nullptr;
    }
    ~callback_env() {
        if (!env_) return;
        if (env_->ExceptionCheck()) {
            __android_log_write(ANDROID_LOG_ERROR, "Omnidroid-PS3", "Host callback raised an exception");
            env_->ExceptionDescribe(); env_->ExceptionClear();
        }
        env_->PopLocalFrame(nullptr);
    }
    operator JNIEnv*() const { return env_; }
    JNIEnv* operator->() const { return env_; }
    explicit operator bool() const { return env_ != nullptr; }
};
}
std::string utf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const auto* chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    std::u16string text(reinterpret_cast<const char16_t*>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}.to_bytes(text);
}
jstring java_string(JNIEnv* env, const char* value) {
    const auto text = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}.from_bytes(value ? value : "");
    return env->NewString(reinterpret_cast<const jchar*>(text.data()), text.size());
}
void fail(JNIEnv* env, const char* message) {
    if (!env->ExceptionCheck()) {
        auto type = env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
    }
}
bool attach(JNIEnv* env, jclass hostClass, jclass storageClass) {
    if (host) return true;
    env->GetJavaVM(&vm);
    methods.progress = env->GetStaticMethodID(hostClass, "onProgress", "(JJJLjava/lang/String;)V");
    methods.installed = env->GetStaticMethodID(hostClass, "onInstalled", "(JILjava/lang/String;)V");
    methods.firmware = env->GetStaticMethodID(hostClass, "onFirmware", "(Ljava/lang/String;)V");
    methods.dialog = env->GetStaticMethodID(hostClass, "onDialog", "(JILjava/lang/String;)V");
    methods.stat = env->GetStaticMethodID(storageClass, "statPath", "(Ljava/lang/String;)[J");
    methods.list = env->GetStaticMethodID(storageClass, "listDir", "(Ljava/lang/String;)[Ljava/lang/String;");
    methods.open = env->GetStaticMethodID(storageClass, "openFd", "(Ljava/lang/String;Ljava/lang/String;Z)I");
    methods.mkdir = env->GetStaticMethodID(storageClass, "createDir", "(Ljava/lang/String;)Z");
    methods.remove = env->GetStaticMethodID(storageClass, "deletePath", "(Ljava/lang/String;)Z");
    methods.rename = env->GetStaticMethodID(storageClass, "renamePath", "(Ljava/lang/String;Ljava/lang/String;)Z");
    if (env->ExceptionCheck()) return false;
    host = static_cast<jclass>(env->NewGlobalRef(hostClass));
    storage = static_cast<jclass>(env->NewGlobalRef(storageClass));
    return host && storage;
}
omni_ps3_callbacks callbacks() {
    omni_ps3_callbacks result{};
    result.size = sizeof(result);
    result.log = [](void*, int32_t severity, const char* message) {
        __android_log_write(severity <= 2 ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "Omnidroid-PS3", message);
    };
    result.progress = [](void*, uint64_t id, int64_t value, int64_t maximum, const char* message) {
        callback_env env; if (env) env->CallStaticVoidMethod(host, methods.progress, jlong(id), jlong(value), jlong(maximum), java_string(env, message));
    };
    result.installation_finished = [](void*, uint64_t id, omni_ps3_result status, const char* message) {
        callback_env env; if (env) env->CallStaticVoidMethod(host, methods.installed, jlong(id), jint(status), java_string(env, message));
    };
    result.firmware_version = [](void*, const char* version) {
        callback_env env; if (env) env->CallStaticVoidMethod(host, methods.firmware, java_string(env, version));
    };
    result.dialog = [](void*, uint64_t token, omni_ps3_dialog_kind kind, const char* json) {
        callback_env env; if (env) env->CallStaticVoidMethod(host, methods.dialog, jlong(token), jint(kind), java_string(env, json));
    };
    result.stat_file = [](void*, const char* path, omni_ps3_file_info* info) -> int32_t {
        callback_env env; if (!env) return 0;
        auto reply = static_cast<jlongArray>(env->CallStaticObjectMethod(storage, methods.stat, java_string(env, path)));
        if (env->ExceptionCheck() || !reply || env->GetArrayLength(reply) < 3) return 0;
        jlong fields[3]; env->GetLongArrayRegion(reply, 0, 3, fields);
        if (fields[0] < 0) return 0;
        *info = {int32_t(fields[0]), uint64_t(fields[1]), fields[2]}; return 1;
    };
    result.list_directory = [](void*, const char* path, void* sink, omni_ps3_directory_entry emit) -> int32_t {
        callback_env env; if (!env) return 0;
        auto reply = static_cast<jobjectArray>(env->CallStaticObjectMethod(storage, methods.list, java_string(env, path)));
        if (env->ExceptionCheck() || !reply) return 0;
        for (jsize i = 0; i < env->GetArrayLength(reply); ++i) {
            auto item = static_cast<jstring>(env->GetObjectArrayElement(reply, i));
            const auto record = utf8(env, item); env->DeleteLocalRef(item);
            const auto a = record.find('\t');
            if (a == std::string::npos) continue;
            const auto b = record.find('\t', a + 1);
            if (b == std::string::npos) continue;
            const auto c = record.find('\t', b + 1);
            if (c == std::string::npos) continue;
            omni_ps3_file_info info{record[0] == 'D', std::strtoull(record.c_str() + a + 1, nullptr, 10), std::strtoll(record.c_str() + b + 1, nullptr, 10)};
            emit(sink, record.c_str() + c + 1, &info);
        }
        return 1;
    };
    result.open_file = [](void*, const char* path, const char* mode, int32_t create) -> int32_t {
        callback_env env; if (!env) return -1;
        const auto fd = env->CallStaticIntMethod(storage, methods.open, java_string(env, path), java_string(env, mode), create ? JNI_TRUE : JNI_FALSE);
        return env->ExceptionCheck() ? -1 : fd;
    };
    result.create_directory = [](void*, const char* path) -> int32_t {
        callback_env env; if (!env) return 0;
        return env->CallStaticBooleanMethod(storage, methods.mkdir, java_string(env, path)) && !env->ExceptionCheck();
    };
    result.remove_file = [](void*, const char* path) -> int32_t {
        callback_env env; if (!env) return 0;
        return env->CallStaticBooleanMethod(storage, methods.remove, java_string(env, path)) && !env->ExceptionCheck();
    };
    result.rename_file = [](void*, const char* from, const char* to) -> int32_t {
        callback_env env; if (!env) return 0;
        return env->CallStaticBooleanMethod(storage, methods.rename, java_string(env, from), java_string(env, to)) && !env->ExceptionCheck();
    };
    return result;
}
}
