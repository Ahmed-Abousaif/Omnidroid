#pragma once
#include <algorithm>
#include <cstdint>
#include <cstdlib>

namespace omnidroid::ps3 {
struct hint_driver {
    virtual ~hint_driver() = default;
    virtual void* create(int32_t tid, int64_t target) = 0;
    virtual void close(void*) = 0;
    virtual bool target(void*, int64_t) = 0;
    virtual bool report(void*, int64_t) = 0;
};
// Externally synchronized. No Android/JVM dependency, so lifecycle/error handling
// can be exercised with a fake driver without running the emulator.
class hint_session {
    hint_driver& driver;
    void* handle = nullptr;
    int64_t target_ns = 0;
    unsigned retry_frames = 0;
public:
    bool active = false, enabled = true;
    int32_t tid = 0;
    uint64_t submitted = 0, errors = 0, created = 0, closed = 0;
    explicit hint_session(hint_driver& value) : driver(value) {}
    ~hint_session() { close(); }
    void close() {
        if (handle) { driver.close(handle); ++closed; }
        handle = nullptr; tid = 0; target_ns = 0;
    }
    void set_active(bool value) { active = value; if (!value) close(); retry_frames = 0; }
    void set_enabled(bool value) { enabled = value; if (!value) close(); retry_frames = 0; }
    void frame(uint64_t period, uint64_t work, int32_t thread) {
        // Reject compilation, background/pause gaps and inconsistent samples.
        if (!active || !enabled || !thread || !period || !work || work > period || period > 250'000'000) return;
        if (handle && thread != tid) close();
        if (retry_frames) { --retry_frames; return; }
        // Follow real cadence, while still asking Android to recover to at least
        // 30fps when frames run late. No unlimited-clock or power-saving mode.
        const auto target = std::clamp<int64_t>(period, 8'333'333, 33'333'333);
        if (!handle) {
            handle = driver.create(thread, target);
            if (!handle) { ++errors; retry_frames = 60; return; }
            tid = thread; target_ns = target; ++created;
        }
        // Avoid a binder target update for tiny frame-to-frame timing noise.
        if (std::abs(target - target_ns) > target_ns / 20) {
            if (!driver.target(handle, target)) { fail(); return; }
            target_ns = target;
        }
        if (driver.report(handle, work)) ++submitted;
        else fail();
    }
private:
    void fail() { ++errors; close(); retry_frames = 60; }
};
}
