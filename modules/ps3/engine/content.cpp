// Firmware/package operations adapted from the pinned ARMSX3 platform host.
// Uses RPCS3's loaders directly; no game repository, frontend scanner or UI.
#include "runtime.h"
#include "Emu/System.h"
#include "Emu/IdManager.h"
#include "Emu/system_utils.hpp"
#include "Emu/vfs_config.h"
#include "Crypto/unpkg.h"
#include "Crypto/unself.h"
#include "Crypto/key_vault.h"
#include "Loader/PUP.h"
#include "Loader/TAR.h"
#include "Loader/PSF.h"
#include "Loader/ISO.h"
#include "Utilities/File.h"
#include "util/serialization.hpp"
#include "util/sysinfo.hpp"
#include <algorithm>
#include <chrono>
#include <exception>
#include <thread>
#include <unistd.h>

namespace omnidroid::ps3 {
namespace {
std::string quote(std::string_view input) {
    std::string output = "\"";
    for (char c : input) {
        if (c == '"' || c == '\\') output += '\\';
        if (c == '\n') output += "\\n";
        else if (c == '\r') output += "\\r";
        else if (c == '\t') output += "\\t";
        else if (static_cast<unsigned char>(c) >= 32) output += c;
    }
    return output + '"';
}
std::string read_metadata(const std::string& root, const char* icon_path) {
    fs::file source(root + "/PARAM.SFO");
    if (!source || source.size() > (1u << 20)) return "{}";
    const auto values = psf::load_object(source, root + "/PARAM.SFO");
    const std::string title_id(psf::get_string(values, "TITLE_ID"));
    if (title_id.empty()) return "{}";
    bool have_icon = false;
    if (icon_path && *icon_path) {
        if (fs::file icon{root + "/ICON0.PNG"}) {
            const auto bytes = icon.size() <= (8u << 20) ? icon.to_vector<u8>() : std::vector<u8>{};
            if (!bytes.empty()) if (fs::file destination{icon_path, fs::rewrite}) {
                destination.write(bytes.data(), bytes.size()); have_icon = true;
            }
        }
    }
    return "{\"titleId\":" + quote(title_id) + ",\"title\":" + quote(psf::get_string(values, "TITLE")) +
        ",\"icon\":" + std::string(have_icon ? "true" : "false") + "}";
}
void progress(uint64_t operation, int64_t value, int64_t maximum, const char* message) {
    if (callbacks.progress) callbacks.progress(callbacks.userdata, operation, value, maximum, message);
}
omni_ps3_result firmware(fs::file file, uint64_t operation, std::string& message) {
    pup_object pup(std::move(file));
    if (static_cast<pup_error>(pup) != pup_error::ok) { message = "Invalid or damaged PS3 firmware update"; return OMNI_PS3_INVALID_ARGUMENT; }
    fs::file update = pup.get_file(0x300);
    if (!update || !update.size()) { message = "Firmware update contains no system files"; return OMNI_PS3_INVALID_ARGUMENT; }
    tar_object files(update);
    auto names = files.get_filenames();
    std::erase_if(names, [](const std::string& name) { return !name.starts_with("dev_flash_"); });
    if (names.empty()) { message = "Firmware update contains no flash archives"; return OMNI_PS3_INVALID_ARGUMENT; }
    int64_t completed = 0;
    for (const auto& name : names) {
        auto archive = files.get_file(name);
        if (!archive) { message = "Firmware archive is missing"; return OMNI_PS3_FAILED; }
        if (archive->m_file_handler) archive->m_file_handler->handle_file_op(*archive, 0, archive->get_size(umax), nullptr);
        fs::file encrypted = fs::make_stream(std::move(archive->data));
        SCEDecrypter decrypter(encrypted);
        decrypter.LoadHeaders();
        decrypter.LoadMetadata(SCEPKG_ERK, SCEPKG_RIV);
        decrypter.DecryptData();
        auto decrypted = decrypter.MakeFile();
        if (decrypted.size() < 3) { message = "Could not decrypt firmware system archive"; return OMNI_PS3_FAILED; }
        tar_object flash(decrypted[2]);
        if (!flash.extract()) { message = "Could not write firmware into the PS3 data directory"; return OMNI_PS3_FAILED; }
        progress(operation, ++completed, names.size(), "Installing PS3 firmware");
    }
    const auto version = utils::get_firmware_version();
    if (callbacks.firmware_version) callbacks.firmware_version(callbacks.userdata, version.c_str());
    // Firmware modules compile through the normal engine boot path. No frontend
    // VSH precompiler or application-level compilation worker is required.
    message = "PS3 firmware installed";
    return OMNI_PS3_OK;
}
omni_ps3_result package(fs::file file, uint64_t operation, std::string& message) {
    std::deque<package_reader> readers;
    readers.emplace_back("game.pkg", std::move(file));
    std::deque<std::string> bootable;
    package_install_result result{};
    std::atomic<bool> finished{false};
    std::exception_ptr failure;
    std::thread worker([&] {
        try { result = package_reader::extract_data(readers, bootable, false); }
        catch (...) { failure = std::current_exception(); }
        finished.store(true, std::memory_order_release);
    });
    while (!finished.load(std::memory_order_acquire)) {
        progress(operation, readers.front().get_progress(10000), 10000, "Installing PS3 package");
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    worker.join();
    if (failure) std::rethrow_exception(failure);
    if (result.error != package_install_result::error_type::no_error) {
        message = result.error == package_install_result::error_type::app_version ?
            "The package requires a different installed game version" : "Could not install PS3 package";
        return OMNI_PS3_FAILED;
    }
    message = "PS3 package installed";
    return OMNI_PS3_OK;
}
}
size_t probe(const char* raw, const char* icon_path, char* buffer, size_t capacity) {
    if (!raw || !*raw) return copy_string("{}", buffer, capacity);
    std::lock_guard lock(lifecycle);
    if (!Emu.IsStopped(true)) return copy_string("{\"busy\":true}", buffer, capacity);
    const std::string path(raw);
    if (fs::is_dir(path)) {
        auto result = read_metadata(path + "/PS3_GAME", icon_path);
        if (result == "{}") result = read_metadata(rpcs3::utils::get_sfo_dir_from_game_path(path), icon_path);
        return copy_string(result, buffer, capacity);
    }
    if (!is_iso_file(path)) return copy_string("{}", buffer, capacity);
    const bool temporary_fxo = !g_fxo->is_init();
    if (temporary_fxo) g_fxo->reset();
    std::string result;
    try {
        load_iso(path);
        result = read_metadata(iso_device::virtual_device_name + "/PS3_GAME", icon_path);
    } catch (...) {
        unload_iso();
        if (temporary_fxo) g_fxo->clear();
        throw;
    }
    unload_iso();
    if (temporary_fxo) g_fxo->clear();
    return copy_string(result, buffer, capacity);
}
omni_ps3_result install(uint32_t kind, int32_t fd, uint64_t operation) {
    if (fd < 0 || kind > 1) return OMNI_PS3_INVALID_ARGUMENT;
    std::lock_guard lock(lifecycle);
    if (!Emu.IsStopped(true)) return OMNI_PS3_BUSY;
    const int owned = dup(fd);
    if (owned < 0) return OMNI_PS3_FAILED;
    std::string message;
    auto file = fs::file::from_native_handle(owned);
    const auto result = kind == 0 ? firmware(std::move(file), operation, message) : package(std::move(file), operation, message);
    if (callbacks.installation_finished) callbacks.installation_finished(callbacks.userdata, operation, result, message.c_str());
    return result;
}
}
