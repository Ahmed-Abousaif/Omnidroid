#include "runtime.h"
#include "guest_dialogs.h"
#include "omnidroid_performance.h"
#include <cmath>
#include "Emu/System.h"
#include "Emu/IdManager.h"
#include "Emu/emu_callbacks.h"
#include "Emu/android_app_state.h"
#include "Emu/system_config.h"
#include "Emu/RSX/GSFrameBase.h"
#include "Emu/RSX/Core/RSXRendererFactory.h"
#include "Emu/Audio/Oboe/OboeBackend.h"
#include "Emu/Audio/Null/NullAudioBackend.h"
#include "Emu/system_utils.hpp"
#include "Emu/Io/Null/NullKeyboardHandler.h"
#include "Emu/Io/Null/NullMouseHandler.h"
#include "Emu/Io/Null/null_camera_handler.h"
#include "Emu/Io/Null/null_music_handler.h"
#include "Input/pad_thread.h"
#include "Input/virtual_pad_handler.h"
#include "Emu/Io/pad_config.h"
#include "Emu/Cell/Modules/cellSaveData.h"
#include "Emu/Cell/Modules/sceNpTrophy.h"
#include "Utilities/File.h"
#include "Utilities/Thread.h"
#include "util/logs.hpp"
#include "util/video_source.h"
#include "Utilities/StrUtil.h"
#include <android/native_window.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <filesystem>
#include <functional>
#include <thread>
#include <sys/prctl.h>

extern std::string g_android_executable_dir, g_android_config_dir,
    g_android_cache_dir, g_android_private_cache_dir;

namespace omnidroid::ps3 {
std::string guest_string(localized_string_id, const char*);
omni_ps3_callbacks callbacks{};
std::mutex lifecycle;
namespace {
std::shared_ptr<host_requests> requests;
std::mutex surface_mutex, pads_mutex;
ANativeWindow* window = nullptr;
std::atomic<uint64_t> dimensions{0}, epoch{0};
std::atomic<double> refresh_rate{60.};
std::array<std::shared_ptr<Pad>, 7> pads;
std::atomic<bool> initialized{false};
std::unique_ptr<logs::listener> file_log;
class host_log final : public logs::listener {
public:
    host_log() { logs::listener::add(this); }
    void log(u64, const logs::message& message, std::string_view, std::string_view text) override {
        const auto severity = static_cast<logs::level>(message);
        if ((severity == logs::level::fatal || severity == logs::level::error || severity == logs::level::warning) && callbacks.log)
            callbacks.log(callbacks.userdata, static_cast<int32_t>(severity), std::string(text).c_str());
    }
};

class dispatcher {
    std::mutex mutex;
    std::condition_variable changed;
    std::deque<std::function<void()>> tasks;
public:
    dispatcher() {
        std::thread([this] {
            for (;;) {
                std::function<void()> task;
                {
                    std::unique_lock lock(mutex);
                    changed.wait(lock, [&] { return !tasks.empty(); });
                    task = std::move(tasks.front()); tasks.pop_front();
                }
                try { task(); }
                catch (const std::exception& error) {
                    if (callbacks.log) callbacks.log(callbacks.userdata, 1, error.what());
                }
                catch (...) {
                    if (callbacks.log) callbacks.log(callbacks.userdata, 1, "Engine dispatcher failed");
                }
            }
        }).detach();
    }
    void post(std::function<void()> task) {
        { std::lock_guard lock(mutex); tasks.push_back(std::move(task)); }
        changed.notify_one();
    }
};
dispatcher& main_dispatcher() { static auto* value = new dispatcher; return *value; }

class frame final : public GSFrameBase {
    mutable ANativeWindow* acquired = nullptr;
public:
    ~frame() override { if (acquired) ANativeWindow_release(acquired); }
    display_handle_t handle() const override {
        for (;;) {
            {
                std::lock_guard lock(surface_mutex);
                if (window) {
                    if (window != acquired) {
                        ANativeWindow_acquire(window);
                        if (acquired) ANativeWindow_release(acquired);
                        acquired = window;
                    }
                    return acquired;
                }
            }
            if (Emu.IsStopped()) return acquired;
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
    }
    void close() override {}
    void reset() override {}
    video_renderer renderer() const override { return video_renderer::vulkan; }
    bool shown() override { return true; }
    void hide() override {}
    void show() override {}
    void toggle_fullscreen() override {}
    void delete_context(draw_context_t) override {}
    draw_context_t make_context() override { return nullptr; }
    void set_current(draw_context_t) override {}
    void flip(draw_context_t, bool) override {}
    int client_width() override { return static_cast<int>(dimensions.load() >> 32); }
    int client_height() override { return static_cast<int>(dimensions.load() & 0xffffffff); }
    f64 client_display_rate() override { return refresh_rate.load(); }
    bool has_alpha() override { return false; }
    u64 display_epoch() const override { return epoch.load(); }
    bool can_consume_frame() const override { return false; }
    void present_frame(std::vector<u8>&&, u32, u32, u32, bool) const override {}
    void take_screenshot(std::vector<u8>&&, u32, u32, bool) override {}
    void update_title(double) override {}
};

bool connect_pad(const std::shared_ptr<Pad>& pad) {
    const auto port = pad->m_player_id;
    if (port >= pads.size()) return false;
    std::lock_guard lock(pads_mutex);
    pads[port] = pad;
    pad->Init(port == 0 ? CELL_PAD_STATUS_CONNECTED : 0,
        CELL_PAD_CAPABILITY_PS3_CONFORMITY | CELL_PAD_CAPABILITY_PRESS_MODE,
        CELL_PAD_DEV_TYPE_STANDARD, CELL_PAD_PCLASS_TYPE_STANDARD, 0, 0x054c, 0x0268, 50);
    for (const u32 mask : {1u, 2u, 4u, 8u, 16u, 32u, 64u, 128u}) {
        pad->m_buttons.emplace_back(CELL_PAD_BTN_OFFSET_DIGITAL1, std::vector<std::set<u32>>{}, mask);
        pad->m_buttons.emplace_back(CELL_PAD_BTN_OFFSET_DIGITAL2, std::vector<std::set<u32>>{}, mask);
    }
    pad->m_sticks[0] = AnalogStick(CELL_PAD_BTN_OFFSET_ANALOG_LEFT_X, {}, {});
    pad->m_sticks[1] = AnalogStick(CELL_PAD_BTN_OFFSET_ANALOG_LEFT_Y, {}, {});
    pad->m_sticks[2] = AnalogStick(CELL_PAD_BTN_OFFSET_ANALOG_RIGHT_X, {}, {});
    pad->m_sticks[3] = AnalogStick(CELL_PAD_BTN_OFFSET_ANALOG_RIGHT_Y, {}, {});
    return true;
}

void configure_callbacks() {
    g_emu_callbacks = emu_callbacks{};
    g_emu_callbacks.call_from_main_thread = [](std::function<void()> task, atomic_t<u32>* wake) {
        if (wake) { task(); *wake = true; wake->notify_one(); }
        else main_dispatcher().post(std::move(task));
    };
    g_emu_callbacks.on_run = [](bool) { performance_active(true); };
    g_emu_callbacks.on_pause = [] { performance_active(false); };
    g_emu_callbacks.on_resume = [] { performance_active(true); };
    g_emu_callbacks.on_stop = [] { performance_active(false); };
    g_emu_callbacks.on_ready = [] {};
    g_emu_callbacks.on_missing_fw = [] {};
    g_emu_callbacks.on_emulation_stop_no_response = [](auto, int) {};
    g_emu_callbacks.on_save_state_progress = [](auto...) {};
    g_emu_callbacks.enable_disc_eject = [](bool) {};
    g_emu_callbacks.enable_disc_insert = [](bool) {};
    g_emu_callbacks.try_to_quit = [](bool, auto done) { if (done) done(); return true; };
    g_emu_callbacks.handle_taskbar_progress = [](s32, s32) {};
    g_emu_callbacks.init_kb_handler = [] { g_fxo->init<KeyboardHandlerBase, NullKeyboardHandler>(Emu.DeserialManager()); };
    g_emu_callbacks.init_mouse_handler = [] { g_fxo->init<MouseHandlerBase, NullMouseHandler>(Emu.DeserialManager()); };
    g_emu_callbacks.init_pad_handler = [](auto) { g_fxo->init<named_thread<pad_thread>>(nullptr, nullptr, ""); };
    g_emu_callbacks.update_emu_settings = [] {};
    g_emu_callbacks.save_emu_settings = [] { Emulator::SaveSettings(g_cfg.to_string(), Emu.GetTitleID()); };
    g_emu_callbacks.close_gs_frame = [] {};
    g_emu_callbacks.get_gs_frame = [] { return std::make_unique<frame>(); };
    g_emu_callbacks.replace_gs_frame = [] { return std::make_unique<frame>(); };
    g_emu_callbacks.get_camera_handler = [] { return std::make_shared<null_camera_handler>(); };
    g_emu_callbacks.get_music_handler = [] { return std::make_shared<null_music_handler>(); };
    g_emu_callbacks.init_gs_render = [](utils::serial* archive) { rsx::create_render_thread(archive); };
    g_emu_callbacks.get_audio = []() -> std::shared_ptr<AudioBackend> {
        auto audio = std::make_shared<OboeBackend>();
        if (audio->Initialized()) return audio;
        return std::make_shared<NullAudioBackend>();
    };
    g_emu_callbacks.get_audio_enumerator = [](auto) { return nullptr; };
    g_emu_callbacks.get_msg_dialog = [] { return make_message_dialog(dialogs()); };
    g_emu_callbacks.get_osk_dialog = [] { return make_keyboard_dialog(dialogs()); };
    g_emu_callbacks.get_save_dialog = [] { return make_savedata_dialog(dialogs()); };
    g_emu_callbacks.get_sendmessage_dialog = [] { return nullptr; };
    g_emu_callbacks.get_recvmessage_dialog = [] { return nullptr; };
    g_emu_callbacks.get_trophy_notification_dialog = [] { return nullptr; };
    g_emu_callbacks.get_localized_string = guest_string;
    g_emu_callbacks.get_localized_u32string = [](auto id, const char* argument) {
        const auto text = utf8_to_wchar(guest_string(id, argument));
        return std::u32string(text.begin(), text.end());
    };
    g_emu_callbacks.get_localized_setting = [](auto, auto) { return std::string{}; };
    g_emu_callbacks.get_photo_path = [](auto) { return std::string{}; };
    g_emu_callbacks.play_sound = [](auto&, auto) {};
    g_emu_callbacks.get_image_info = [](auto...) { return false; };
    g_emu_callbacks.get_scaled_image = [](auto...) { return false; };
    g_emu_callbacks.get_font_dirs = [] { return std::vector<std::string>{}; };
    g_emu_callbacks.on_install_pkgs = [](const std::vector<std::string>& paths, bool optical) {
        for (const auto& path : paths) if (!rpcs3::utils::install_pkg(path, optical)) return false;
        return true;
    };
    g_emu_callbacks.add_breakpoint = [](u32) {};
    g_emu_callbacks.display_sleep_control_supported = [] { return false; };
    g_emu_callbacks.enable_display_sleep = [](bool) {};
    g_emu_callbacks.check_microphone_permissions = [] {};
    g_emu_callbacks.make_video_source = []() -> std::unique_ptr<video_source> { return nullptr; };
    g_emu_callbacks.enable_gamemode = [](bool) {};
    g_emu_callbacks.get_database_config = [](auto&) { return std::string{}; };
}
}

std::shared_ptr<host_requests> dialogs() { return std::atomic_load(&requests); }
omni_ps3_result initialize(const char* data, const char* cache, const omni_ps3_callbacks* host) {
    if (!data || !cache || !host || host->size < sizeof(omni_ps3_callbacks)) return OMNI_PS3_INVALID_ARGUMENT;
    std::lock_guard lock(lifecycle);
    if (initialized) return OMNI_PS3_OK;
    callbacks = *host;
    install_storage();
    std::atomic_store(&requests, std::make_shared<host_requests>(callbacks));
    prctl(PR_SET_TIMERSLACK, 1, 0, 0, 0);
    g_android_executable_dir = std::string(data) + "/";
    g_android_config_dir = g_android_executable_dir + "config/";
    g_android_cache_dir = g_android_executable_dir + "cache/";
    g_android_private_cache_dir = std::string(cache) + "/";
    std::filesystem::create_directories(g_android_config_dir);
    std::filesystem::create_directories(g_android_cache_dir);
    std::filesystem::create_directories(g_android_private_cache_dir);
    logs::set_init({});
    performance_options(3);
    file_log = logs::make_file_listener(fs::get_log_dir() + "Omnidroid-PS3.log", 32ull << 20);
    static auto* log = new host_log;
    static_cast<void>(log);
    static_cast<void>(named_thread("", [](int) {}));
    configure_callbacks();
    virtual_pad_handler::set_on_connect_cb(connect_pad);
    Emu.SetHasGui(false);
    Emu.SetUsr("00000001");
    Emu.SetSupportedRenderers({video_renderer::null, video_renderer::vulkan});
    Emu.SetDefaultRenderer(video_renderer::vulkan);
    Emu.SetDefaultGraphicsAdapter("Default");
    Emu.Init();
    g_cfg.video.renderer.set(video_renderer::vulkan);
    g_cfg.core.thread_scheduler.set(thread_scheduler_mode::alt);
    g_cfg.core.llvm_cpu.from_string("");
    g_cfg.misc.autoexit.set(false);
    g_cfg.misc.use_native_interface.set(false);
    g_cfg.savestate.compatible_mode.set(true);
    g_cfg.savestate.suspend_emu.set(false);
    for (auto* player : g_cfg_input.player) {
        player->handler.set(pad_handler::virtual_pad);
        player->device.from_string("Virtual");
    }
    g_cfg_input.save("", g_cfg_input_configs.default_config);
    Emulator::SaveSettings(g_cfg.to_string(), "");
    initialized = true;
    return OMNI_PS3_OK;
}
omni_ps3_result boot(const char* path) {
    if (!path || !*path) return OMNI_PS3_INVALID_ARGUMENT;
    std::lock_guard lock(lifecycle);
    if (!initialized || !Emu.IsStopped(true)) return OMNI_PS3_BUSY;
    std::atomic_store(&requests, std::make_shared<host_requests>(callbacks));
    Emu.SetForceBoot(true);
    std::string target(path);
    while (target.size() > 1 && target.back() == '/') target.pop_back();
    if (fs::is_dir(target)) {
        for (const char* suffix : {"/PS3_GAME/USRDIR/EBOOT.BIN", "/USRDIR/EBOOT.BIN"}) {
            if (fs::is_file(target + suffix)) { target += suffix; break; }
        }
    }
    // Capture Omnidroid's selected settings for this session. Boot must not
    // reload per-title frontend configuration left by an older package.
    const auto config = fs::get_config_dir() + "omnidroid-session.yml";
    { fs::file file(config, fs::rewrite); if (!file || !file.write(g_cfg.to_string())) return OMNI_PS3_FAILED; }
    const auto result = Emu.BootGame(target, "", false, cfg_mode::config_override, config);
    return result == game_boot_result::no_errors || result == game_boot_result::already_added ? OMNI_PS3_OK :
        result == game_boot_result::nothing_to_boot ? OMNI_PS3_NOT_FOUND : OMNI_PS3_FAILED;
}
int32_t state() { return initialized ? static_cast<int32_t>(Emu.GetStatus(false)) : 0; }
omni_ps3_result pause() { if (!initialized) return OMNI_PS3_BUSY; Emu.Pause(); return OMNI_PS3_OK; }
omni_ps3_result resume() { if (!initialized) return OMNI_PS3_BUSY; Emu.Resume(); return OMNI_PS3_OK; }
omni_ps3_result stop() {
    std::lock_guard lock(lifecycle);
    if (!initialized) return OMNI_PS3_OK;
    if (auto host = dialogs()) host->close(INT32_MIN);
    Emu.SetContinuousMode(false);
    Emu.Kill();
    return OMNI_PS3_OK;
}
void surface(void* native, uint32_t width, uint32_t height) {
    auto* replacement = static_cast<ANativeWindow*>(native);
    std::lock_guard lock(surface_mutex);
    if (replacement) ANativeWindow_acquire(replacement);
    if (window) ANativeWindow_release(window);
    window = replacement;
    g_android_app_backgrounded.store(!replacement);
    dimensions = (uint64_t{width} << 32) | height;
    ++epoch;
}
double display_rate_hz() { return refresh_rate.load(); }
void display_rate(double hz) {
    if (!std::isfinite(hz) || hz < 24. || hz > 360.) return;
    if (refresh_rate.exchange(hz) != hz) ++epoch;
}
void pad(uint32_t port, uint8_t d1, uint8_t d2, uint8_t lx, uint8_t ly, uint8_t rx, uint8_t ry) {
    std::lock_guard lock(pads_mutex);
    if (port >= pads.size() || !pads[port]) return;
    auto& pad = *pads[port];
    pad.m_port_status |= CELL_PAD_STATUS_CONNECTED;
    for (auto& button : pad.m_buttons) {
        button.m_pressed = ((button.m_offset == CELL_PAD_BTN_OFFSET_DIGITAL1 ? d1 : d2) & button.m_outKeyCode) != 0;
        button.m_value = button.m_pressed ? 255 : 0;
    }
    const uint8_t axes[] = {lx, ly, rx, ry};
    for (size_t i = 0; i < 4; ++i) pad.m_sticks[i].m_value = axes[i];
}
omni_ps3_result dialog_result(uint64_t token, int32_t status, const char* text) {
    auto host = dialogs();
    return host && host->reply(token, status, text ? text : "") ? OMNI_PS3_OK : OMNI_PS3_NOT_FOUND;
}
}
