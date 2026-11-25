#include <android/log.h>
#include <chrono>
#include <cstdlib>
#include <functional>
#include <jni.h>
#include <string>
#include <string_view>
#include <thread>
#include "Emu/Io/pad_config.h"

std::string g_input_config_override;
cfg_input_configurations g_cfg_input_configs;

[[noreturn]] void report_fatal_error(std::string_view text, bool, bool) {
    __android_log_write(ANDROID_LOG_FATAL, "Omnidroid-PS3", std::string(text).c_str());
    std::abort();
}
void qt_events_aware_op(int interval, std::function<bool()> operation) {
    while (!operation()) std::this_thread::sleep_for(std::chrono::milliseconds(interval));
}
// The optional MIDI dependency probes the invocation API. This host does not
// provide MIDI devices and never relies on Android's private libnativehelper.
extern "C" jint JNI_GetCreatedJavaVMs(JavaVM**, jsize, jsize* count) {
    if (count) *count = 0;
    return JNI_OK;
}
