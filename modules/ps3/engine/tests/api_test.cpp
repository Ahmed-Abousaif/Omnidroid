#include "runtime.h"
#include <cassert>
#include <stdexcept>

namespace omnidroid::ps3 {
omni_ps3_callbacks callbacks{};
omni_ps3_result initialize(const char*, const char*, const omni_ps3_callbacks*) { throw std::runtime_error("initialize failed"); }
omni_ps3_result boot(const char*) { return OMNI_PS3_NOT_FOUND; }
int32_t state() { throw 42; }
omni_ps3_result pause() { return OMNI_PS3_OK; }
omni_ps3_result resume() { return OMNI_PS3_OK; }
omni_ps3_result stop() { return OMNI_PS3_OK; }
void surface(void*, uint32_t, uint32_t) { throw 42; }
int32_t state_operation_failed() { return 0; }
void display_rate(double) {}
double display_rate_hz() { return 60.; }
void performance_stats(omni_ps3_performance*) {}
void performance_options(uint32_t) {}
void pad(uint32_t, uint8_t, uint8_t, uint8_t, uint8_t, uint8_t, uint8_t) {}
omni_ps3_result setting(const char*, const char*) { return OMNI_PS3_OK; }
size_t settings(const char*, char*, size_t) { throw 42; }
size_t probe(const char*, const char*, char*, size_t) { return 0; }
omni_ps3_result save_slot(uint32_t) { return OMNI_PS3_OK; }
omni_ps3_result load_slot(uint32_t) { return OMNI_PS3_OK; }
omni_ps3_result install(uint32_t, int32_t, uint64_t) { return OMNI_PS3_OK; }
omni_ps3_result dialog_result(uint64_t, int32_t, const char*) { return OMNI_PS3_OK; }
}
int main() {
    assert(!omni_ps3_get_api(0));
    assert(!omni_ps3_get_api(OMNI_PS3_ABI_VERSION + 1));
    const auto* api = omni_ps3_get_api(OMNI_PS3_ABI_VERSION);
    assert(api && api->size == sizeof(*api));
    assert(std::string(api->profile) == "omnidroid-engine-only");
    assert(api->initialize("", "", nullptr) == OMNI_PS3_FAILED);
    assert(api->boot("") == OMNI_PS3_NOT_FOUND);
    assert(api->state() == -1);
    assert(api->settings("", nullptr, 0) == 0);
    api->surface(nullptr, 0, 0); // A void C entry point also absorbs exceptions.
}
