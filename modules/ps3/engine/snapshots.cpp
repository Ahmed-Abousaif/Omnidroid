#include "runtime.h"
#include <atomic>
#include "Utilities/File.h"
#include "util/endian.hpp"
#include "Emu/System.h"
#include "Emu/savestate_utils.hpp"

namespace omnidroid::ps3 {
namespace {
std::atomic<bool> capture_failed{false};
std::string slot_directory(const std::string& title) {
    // Preserve the prototype's on-disk numbering; this is data compatibility,
    // not a dependency on its frontend or Java classes.
    return fs::get_config_dir() + "savestates/" + title + "/armsx3_slots/";
}
std::string slot_file(uint32_t index) {
    const auto title = Emu.GetTitleID();
    if (title.empty()) return {};
    for (const auto* suffix : {".zst", ".gz", ""}) {
        const auto path = slot_directory(title) + "slot" + std::to_string(index) + ".SAVESTAT" + suffix;
        if (fs::is_file(path)) return path;
    }
    return {};
}
}
void snapshot_failed() { capture_failed = true; }
int32_t state_operation_failed() { return capture_failed ? 1 : 0; }
omni_ps3_result save_slot(uint32_t index) {
    if (!Emu.IsRunning() && !Emu.IsPaused()) return OMNI_PS3_BUSY;
    const auto title = Emu.GetTitleID();
    const auto boot = Emu.GetBoot();
    if (title.empty()) return OMNI_PS3_NOT_FOUND;
    capture_failed = false;
    Emu.CallFromMainThread([index, title, boot] {
        Emu.after_kill_callback = [index, title, boot] {
            const auto newest = get_savestate_file(title, boot, 1);
            const auto directory = slot_directory(title);
            if (fs::is_file(newest) && fs::create_path(directory)) {
                const auto stem = newest.rfind(".SAVESTAT");
                const auto suffix = stem == std::string::npos ? std::string{} : newest.substr(stem + 9);
                const auto destination = directory + "slot" + std::to_string(index) + ".SAVESTAT" + suffix;
                if (!fs::copy_file(newest, destination, true)) snapshot_failed();
            } else snapshot_failed();
            Emu.Restart(true, false);
        };
        Emu.SetContinuousMode(true);
        Emu.Kill(false, true);
    });
    return OMNI_PS3_OK;
}
omni_ps3_result load_slot(uint32_t index) {
    if (!Emu.IsRunning() && !Emu.IsPaused()) return OMNI_PS3_BUSY;
    const auto path = slot_file(index);
    if (path.empty()) return OMNI_PS3_NOT_FOUND;
    if (!is_savestate_compatible(path)) return OMNI_PS3_INCOMPATIBLE;
    capture_failed = false;
    Emu.CallFromMainThread([path] {
        Emu.after_kill_callback = [path] { Emu.BootGame(path, "", true); };
        Emu.SetContinuousMode(true);
        Emu.Kill(false, false);
    });
    return OMNI_PS3_OK;
}
}
