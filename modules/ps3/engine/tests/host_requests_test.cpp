#include "host_requests.h"
#include <cassert>
#include <string>
#include <vector>
#include <future>
#include <thread>

int main() {
    using omnidroid::ps3::host_requests;
    std::vector<omni_ps3_dialog_kind> events;
    omni_ps3_callbacks callbacks{};
    callbacks.size = sizeof(callbacks);
    callbacks.userdata = &events;
    callbacks.dialog = [](void* data, uint64_t, omni_ps3_dialog_kind kind, const char*) {
        static_cast<std::vector<omni_ps3_dialog_kind>*>(data)->push_back(kind);
    };
    host_requests requests(callbacks);
    int completed = 0;
    auto token = requests.open(OMNI_PS3_KEYBOARD, "{}", [&](int32_t status, const std::string& text) {
        assert(status == 0 && text == "World"); ++completed;
    });
    assert(token != 0 && requests.reply(token, 0, "World"));
    assert(!requests.reply(token, 0, "duplicate") && completed == 1);
    requests.open(OMNI_PS3_SAVEDATA, "{}", [&](int32_t status, const std::string&) {
        assert(status == -1); ++completed;
    });
    requests.close(-1);
    assert(completed == 2 && events.back() == OMNI_PS3_DIALOG_CLOSE);
    assert(requests.open(OMNI_PS3_MESSAGE, "{}", {}) == 0);

    // Missing UI must cancel rather than strand a guest callback.
    host_requests headless({});
    int canceled = 0;
    headless.open(OMNI_PS3_MESSAGE, "{}", [&](int32_t status, const std::string&) { canceled = status; });
    assert(canceled == -1);

    host_requests progress(callbacks);
    int aborted = 0;
    const auto progress_token = progress.open(OMNI_PS3_MESSAGE, "{}", [&](int32_t, const std::string&) { ++aborted; });
    progress.dismiss(progress_token);
    assert(aborted == 0 && events.back() == OMNI_PS3_DIALOG_CLOSE);
    assert(!progress.reply(progress_token, 3, ""));

    // Shutdown cannot overtake a host delivery and leave a dialog opening after
    // its close event. Hold delivery while a second thread requests shutdown.
    std::promise<void> delivering, release;
    auto released = release.get_future().share();
    struct delivery_state { std::promise<void>* started; std::shared_future<void>* release; } delivery{&delivering, &released};
    callbacks.userdata = &delivery;
    callbacks.dialog = [](void* value, uint64_t, omni_ps3_dialog_kind kind, const char*) {
        if (kind == OMNI_PS3_MESSAGE) {
            auto& state = *static_cast<delivery_state*>(value);
            state.started->set_value(); state.release->wait();
        }
    };
    host_requests concurrent(callbacks);
    std::thread opening([&] { concurrent.open(OMNI_PS3_MESSAGE, "{}", {}); });
    delivering.get_future().wait();
    auto closing = std::async(std::launch::async, [&] { concurrent.close(-1); });
    assert(closing.wait_for(std::chrono::milliseconds(20)) == std::future_status::timeout);
    release.set_value();
    opening.join(); closing.get();
    assert(concurrent.open(OMNI_PS3_MESSAGE, "{}", {}) == 0);
}
