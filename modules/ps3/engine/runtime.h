#pragma once
#include "omnidroid_ps3.h"
#include "host_requests.h"
#include <memory>
#include <mutex>
#include <string>

namespace omnidroid::ps3 {
extern omni_ps3_callbacks callbacks;
extern std::mutex lifecycle;
std::shared_ptr<host_requests> dialogs();
void install_storage();
omni_ps3_result initialize(const char*, const char*, const omni_ps3_callbacks*);
omni_ps3_result boot(const char*);
int32_t state();
omni_ps3_result pause();
omni_ps3_result resume();
omni_ps3_result stop();
void surface(void*, uint32_t, uint32_t);
void display_rate(double);
double display_rate_hz();
void performance_stats(omni_ps3_performance*);
void performance_options(uint32_t);
void pad(uint32_t, uint8_t, uint8_t, uint8_t, uint8_t, uint8_t, uint8_t);
omni_ps3_result setting(const char*, const char*);
size_t settings(const char*, char*, size_t);
size_t probe(const char*, const char*, char*, size_t);
int32_t state_operation_failed();
omni_ps3_result save_slot(uint32_t);
omni_ps3_result load_slot(uint32_t);
omni_ps3_result install(uint32_t, int32_t, uint64_t);
omni_ps3_result dialog_result(uint64_t, int32_t, const char*);
size_t copy_string(const std::string&, char*, size_t);
}
