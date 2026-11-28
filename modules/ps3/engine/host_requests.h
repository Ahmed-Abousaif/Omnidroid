#pragma once

#include "omnidroid_ps3.h"
#include <atomic>
#include <functional>
#include <map>
#include <mutex>
#include <string>

namespace omnidroid::ps3 {

/* Owns guest-dialog completions, independently of Android and RPCS3 UI code.
 * Replies are consumed once. Stop cancels outstanding requests, so a guest
 * thread waiting on a save-data selector cannot keep shutdown alive. */
class host_requests {
public:
    using completion = std::function<void(int32_t, const std::string&)>;
    explicit host_requests(omni_ps3_callbacks callbacks) : callbacks_(callbacks) {}

    uint64_t open(omni_ps3_dialog_kind kind, const std::string& json, completion done) {
        std::lock_guard dispatch(dispatch_);
        const auto token = next_.fetch_add(1);
        {
            std::lock_guard lock(mutex_);
            if (closed_) return 0;
            pending_.emplace(token, std::move(done));
        }
        // A host may reply immediately. Never call it while holding mutex_.
        if (callbacks_.dialog) callbacks_.dialog(callbacks_.userdata, token, kind, json.c_str());
        else reply(token, -1, "");
        return token;
    }

    bool reply(uint64_t token, int32_t status, const std::string& text) {
        std::lock_guard dispatch(dispatch_);
        completion done;
        {
            std::lock_guard lock(mutex_);
            auto found = pending_.find(token);
            if (found == pending_.end()) return false;
            done = std::move(found->second);
            pending_.erase(found);
        }
        if (done) done(status, text);
        return true;
    }

    void update(uint64_t token, const std::string& json) {
        std::lock_guard dispatch(dispatch_);
        {
            std::lock_guard lock(mutex_);
            if (!pending_.contains(token)) return;
        }
        if (callbacks_.dialog) callbacks_.dialog(callbacks_.userdata, token, OMNI_PS3_DIALOG_UPDATE, json.c_str());
    }

    void cancel(uint64_t token, int32_t status) {
        std::lock_guard dispatch(dispatch_);
        if (reply(token, status, "") && callbacks_.dialog)
            callbacks_.dialog(callbacks_.userdata, token, OMNI_PS3_DIALOG_CLOSE, "{}");
    }

    // Engine-originated closure is not a user cancellation. In particular,
    // completing a compilation progress dialog must not call its abort handler.
    void dismiss(uint64_t token) {
        std::lock_guard dispatch(dispatch_);
        completion discarded;
        {
            std::lock_guard lock(mutex_);
            const auto found = pending_.find(token);
            if (found == pending_.end()) return;
            discarded = std::move(found->second);
            pending_.erase(found);
        }
        if (callbacks_.dialog) callbacks_.dialog(callbacks_.userdata, token, OMNI_PS3_DIALOG_CLOSE, "{}");
    }

    void close(int32_t status) {
        std::lock_guard dispatch(dispatch_);
        std::map<uint64_t, completion> pending;
        {
            std::lock_guard lock(mutex_);
            closed_ = true;
            pending.swap(pending_);
        }
        for (auto& [token, done] : pending) {
            if (done) done(status, "");
            if (callbacks_.dialog) callbacks_.dialog(callbacks_.userdata, token, OMNI_PS3_DIALOG_CLOSE, "{}");
        }
    }

private:
    omni_ps3_callbacks callbacks_;
    inline static std::atomic<uint64_t> next_{1};
    // Serializes host delivery against close; immediate host replies may reenter.
    std::recursive_mutex dispatch_;
    std::mutex mutex_;
    std::map<uint64_t, completion> pending_;
    bool closed_ = false;
};
}
