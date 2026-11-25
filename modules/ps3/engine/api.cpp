#include "runtime.h"
#include <algorithm>
#include <cstring>
#include <exception>
#include <type_traits>

namespace omnidroid::ps3 {
size_t copy_string(const std::string& value, char* buffer, size_t capacity) {
    if (buffer && capacity) {
        const auto copied = std::min(value.size(), capacity - 1);
        std::memcpy(buffer, value.data(), copied);
        buffer[copied] = 0;
    }
    return value.size() + 1;
}
}

namespace {
// The public ABI must never unwind through a host compiled with another STL.
template<auto Function> struct boundary;
template<class R, class... A, R (*Function)(A...)> struct boundary<Function> {
    static R invoke(A... args) noexcept {
        try { return Function(args...); }
        catch (const std::exception& error) { report(error.what()); }
        catch (...) { report("Unhandled engine exception"); }
        if constexpr (std::is_same_v<R, omni_ps3_result>) return OMNI_PS3_FAILED;
        else if constexpr (std::is_same_v<R, int32_t>) return -1;
        else if constexpr (!std::is_void_v<R>) return R{};
    }
    static void report(const char* message) noexcept {
        try {
            const auto& host = omnidroid::ps3::callbacks;
            if (host.log) host.log(host.userdata, 1, message);
        } catch (...) {}
    }
};
}

extern "C" const omni_ps3_api* omni_ps3_get_api(uint32_t version) {
    using namespace omnidroid::ps3;
    static const omni_ps3_api api = {
        sizeof(omni_ps3_api), OMNI_PS3_ABI_VERSION, "omnidroid-engine-only",
        boundary<initialize>::invoke, boundary<boot>::invoke, boundary<state>::invoke,
        boundary<omnidroid::ps3::pause>::invoke, boundary<resume>::invoke, boundary<stop>::invoke,
        boundary<surface>::invoke, boundary<pad>::invoke, boundary<setting>::invoke,
        boundary<settings>::invoke, boundary<probe>::invoke, boundary<save_slot>::invoke,
        boundary<load_slot>::invoke, boundary<install>::invoke, boundary<dialog_result>::invoke,
        boundary<state_operation_failed>::invoke, boundary<display_rate>::invoke, boundary<performance_stats>::invoke, boundary<performance_options>::invoke
    };
    return version == OMNI_PS3_ABI_VERSION ? &api : nullptr;
}
