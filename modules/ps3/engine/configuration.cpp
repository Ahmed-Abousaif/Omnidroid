#include "runtime.h"
#include "Emu/System.h"
#include "Emu/system_config.h"
#include <cstring>

namespace omnidroid::ps3 {
namespace {
cfg::_base* node(std::string_view path) {
    cfg::_base* current = &g_cfg;
    while (!path.empty()) {
        if (current->get_type() != cfg::type::node) return nullptr;
        const auto separator = path.find("@@");
        const auto name = path.substr(0, separator);
        cfg::_base* found = nullptr;
        for (auto* child : static_cast<cfg::node*>(current)->get_nodes())
            if (child->get_name() == name) { found = child; break; }
        if (!found) return nullptr;
        current = found;
        if (separator == std::string_view::npos) break;
        path.remove_prefix(separator + 2);
    }
    return current;
}
std::string quote(std::string_view text) {
    std::string output = "\"";
    for (char c : text) {
        if (c == '\\' || c == '"') output += '\\';
        if (c == '\n') output += "\\n";
        else if (c == '\r') output += "\\r";
        else if (c == '\t') output += "\\t";
        else output += c;
    }
    return output + '"';
}
bool allowed(std::string_view path) {
    // Host policy, not a general-purpose application configuration editor.
    constexpr std::string_view options[] = {
        "Video@@Resolution Scale", "Video@@Frame limit", "Video@@VSync Mode",
        "Video@@Anisotropic Filter Override", "Video@@Output Scaling Mode",
        "Video@@FidelityFX CAS Sharpening Intensity", "Video@@Shader Mode",
        "Video@@Shader Precision", "Video@@MSAA", "Video@@Write Color Buffers",
        "Video@@Write Depth Buffer", "Video@@Strict Rendering Mode",
        "Video@@Force High Precision Z buffer", "Video@@Strict Texture Flushing",
        "Video@@Disable ZCull Occlusion Queries", "Audio@@Master Volume",
        "Savestate@@Compatible Savestate Mode", "Savestate@@Suspend Emulation Savestate Mode"
    };
    for (auto option : options) if (path == option) return true;
    return false;
}
}
omni_ps3_result setting(const char* path, const char* json) {
    if (!path || !json || !allowed(path)) return OMNI_PS3_INVALID_ARGUMENT;
    std::lock_guard lock(lifecycle);
    auto* entry = node(path);
    if (!entry || entry->get_type() == cfg::type::node) return OMNI_PS3_NOT_FOUND;
    std::string value(json);
    if (value.size() >= 2 && value.front() == '"' && value.back() == '"') {
        std::string decoded;
        for (size_t i = 1; i + 1 < value.size(); ++i) {
            if (value[i] == '\\' && i + 2 < value.size()) {
                switch (value[++i]) {
                case 'n': decoded += '\n'; break;
                case 'r': decoded += '\r'; break;
                case 't': decoded += '\t'; break;
                default: decoded += value[i]; break;
                }
            } else decoded += value[i];
        }
        value = std::move(decoded);
    }
    if (!Emu.IsStopped(true) && !entry->get_is_dynamic() && value != entry->to_string()) return OMNI_PS3_BUSY;
    return entry->from_string(value, !Emu.IsStopped()) ? OMNI_PS3_OK : OMNI_PS3_INVALID_ARGUMENT;
}
size_t settings(const char* path, char* buffer, size_t capacity) {
    if (!path || !allowed(path)) return copy_string("{}", buffer, capacity);
    std::lock_guard lock(lifecycle);
    auto* entry = node(path);
    if (!entry || entry->get_type() == cfg::type::node) return copy_string("{}", buffer, capacity);
    const bool boolean = entry->get_type() == cfg::type::_bool;
    std::string json = "{\"value\":" + (boolean ? entry->to_string() : quote(entry->to_string())) +
        ",\"default\":" + (boolean ? entry->def_to_string() : quote(entry->def_to_string()));
    if (entry->get_type() == cfg::type::_enum) {
        json += ",\"type\":\"enum\",\"variants\":[";
        bool first = true;
        for (const auto& choice : entry->to_list()) { if (!first) json += ','; first = false; json += quote(choice); }
        json += ']';
    } else json += boolean ? ",\"type\":\"bool\"" : ",\"type\":\"number\"";
    return copy_string(json + '}', buffer, capacity);
}
}
