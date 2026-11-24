#include "runtime.h"
#include "omnidroid_performance.h"
#include "performance_session.h"
#include "util/logs.hpp"
#include <android/performance_hint.h>
#include <dlfcn.h>
#include <mutex>

namespace omnidroid::ps3 {
namespace {
class android_hints final : public hint_driver {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    // Optional API 35 symbol from the already linked Android system library.
    using prefer_power_fn = int (*)(APerformanceHintSession*, bool);
    prefer_power_fn prefer_power = reinterpret_cast<prefer_power_fn>(
        dlsym(RTLD_DEFAULT, "APerformanceHint_setPreferPowerEfficiency"));
public:
    void* create(int32_t tid, int64_t target) override {
        auto* session = manager ? APerformanceHint_createSession(manager, &tid, 1, target) : nullptr;
        if (session && prefer_power) prefer_power(session, false);
        return session;
    }
    void close(void* session) override { APerformanceHint_closeSession(static_cast<APerformanceHintSession*>(session)); }
    bool target(void* session, int64_t ns) override { return !APerformanceHint_updateTargetWorkDuration(static_cast<APerformanceHintSession*>(session), ns); }
    bool report(void* session, int64_t ns) override { return !APerformanceHint_reportActualWorkDuration(static_cast<APerformanceHintSession*>(session), ns); }
};
struct state {
    std::mutex mutex;
    android_hints driver;
    hint_session session{driver};
    uint32_t options = 3; // Always-on hints and reduced routine logging.
    uint64_t frames = 0, period = 0, work = 0, last_period = 0, last_work = 0;
    int32_t tid = 0;
};
state& current() { static auto* value = new state; return *value; }
}
void performance_active(bool active) {
    auto& value = current(); std::lock_guard lock(value.mutex);
    value.session.set_active(active);
}
void report_frame(uint64_t period, uint64_t work, int32_t tid) {
    if (!period || !work || work > period || period > 250'000'000 || tid <= 0) return;
    auto& value = current(); std::lock_guard lock(value.mutex);
    if (!value.session.active) return;
    ++value.frames; value.period += period; value.work += work;
    value.last_period = period; value.last_work = work; value.tid = tid;
    value.session.frame(period, work, tid);
}
void performance_options(uint32_t options) {
    auto& value = current();
    { std::lock_guard lock(value.mutex);
      value.options = options & 3;
      value.session.set_enabled((options & 1) != 0); }
    // The production default suppresses notice/trace formatting and file I/O,
    // while retaining errors, warnings and important always-level diagnostics.
    logs::set_level(".*", options & 2 ? logs::level::warning : logs::level::notice);
}
void performance_stats(omni_ps3_performance* result) {
    if (!result) return;
    auto& value = current(); std::lock_guard lock(value.mutex);
    *result = {value.frames, value.period, value.work, value.last_period, value.last_work,
        value.session.submitted, value.session.errors, value.session.created, value.session.closed,
        value.tid, value.session.tid, value.options, value.session.active, static_cast<uint32_t>(display_rate_hz() * 1000.)};
}
}
