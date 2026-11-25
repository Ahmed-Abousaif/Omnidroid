#pragma once
#include <cstdint>
namespace omnidroid::ps3 {
void performance_active(bool active);
void report_frame(uint64_t period, uint64_t work, int32_t tid);
}
