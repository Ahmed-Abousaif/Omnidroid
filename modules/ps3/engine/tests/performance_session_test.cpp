#include "performance_session.h"
#include <cassert>
using namespace omnidroid::ps3;
struct driver final : hint_driver {
    int creates=0, closes=0, reports=0, targets=0;
    int32_t tid=0;
    int64_t deadline=0;
    bool available=true, success=true;
    void* create(int32_t thread, int64_t target) override {
        ++creates; tid=thread; deadline=target; return available ? this : nullptr;
    }
    void close(void*) override { ++closes; }
    bool target(void*, int64_t ns) override { ++targets; deadline=ns; return success; }
    bool report(void*, int64_t) override { ++reports; return success; }
};
int main() {
    driver fake;
    hint_session hints(fake);
    hints.frame(16'666'667, 5'000'000, 42);
    assert(!fake.creates); // Never hint while paused/backgrounded.
    hints.set_active(true);
    hints.frame(0, 1, 42); hints.frame(1, 2, 42); hints.frame(300'000'000, 1, 42);
    assert(!fake.creates); // No stale, impossible or compilation samples.
    hints.frame(33'333'333, 5'000'000, 42);
    assert(fake.tid==42 && fake.deadline==33'333'333 && hints.submitted==1);
    hints.frame(16'666'667, 4'000'000, 42);
    assert(fake.targets==1 && hints.submitted==2);
    hints.frame(50'000'000, 40'000'000, 43);
    assert(fake.closes==1 && fake.tid==43 && fake.deadline==33'333'333);
    hints.set_active(false);
    assert(!hints.tid && fake.closes==2);
    hints.set_active(true); hints.frame(16'666'667, 4'000'000, 44);
    assert(hints.created==3 && hints.tid==44);
    hints.set_enabled(false); hints.frame(16'666'667, 4'000'000, 44);
    assert(fake.closes==3 && hints.created==3);
    hints.set_enabled(true); fake.available=false;
    hints.frame(16'666'667, 4'000'000, 44);
    const auto attempts=fake.creates;
    for (int i=0;i<60;++i) hints.frame(16'666'667, 4'000'000, 44);
    assert(fake.creates==attempts && hints.errors==1); // Back off on unsupported devices.
    fake.available=true; hints.frame(16'666'667, 4'000'000, 44);
    assert(hints.tid==44);
    fake.success=false; hints.frame(16'666'667, 4'000'000, 44);
    assert(!hints.tid && hints.errors==2); // Service failure must not stop the game.
}
