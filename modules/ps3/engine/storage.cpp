// Host-provided storage mounted through the engine's filesystem abstraction.
// No Android/JVM types or application class names enter the engine.
#include "runtime.h"
#include "Utilities/File.h"
#include <unistd.h>

namespace omnidroid::ps3 {
namespace {
constexpr const char* device_prefix = "/vfsv0_virtual_saf_storage_fs_dev";
std::string relative(std::string_view path) {
    if (path.starts_with(device_prefix)) path.remove_prefix(std::char_traits<char>::length(device_prefix));
    while (path.starts_with('/')) path.remove_prefix(1);
    return std::string(path);
}
fs::stat_t convert(const omni_ps3_file_info& info) {
    return {.is_directory = info.directory != 0, .is_symlink = false, .is_writable = true,
        .size = info.size, .atime = info.modified_seconds, .mtime = info.modified_seconds,
        .ctime = info.modified_seconds};
}
class directory final : public fs::dir_base {
    std::vector<fs::dir_entry> entries_;
    size_t next_ = 0;
public:
    explicit directory(std::vector<fs::dir_entry> entries) : entries_(std::move(entries)) {}
    bool read(fs::dir_entry& entry) override {
        if (next_ == entries_.size()) return false;
        entry = entries_[next_++]; return true;
    }
    void rewind() override { next_ = 0; }
};
class storage final : public fs::device_base {
public:
    storage() { fs_prefix = device_prefix; }
    bool stat(const std::string& path, fs::stat_t& info) override {
        omni_ps3_file_info result{};
        if (!callbacks.stat_file || !callbacks.stat_file(callbacks.userdata, relative(path).c_str(), &result)) {
            fs::g_tls_error = fs::error::noent; return false;
        }
        info = convert(result); return true;
    }
    bool statfs(const std::string& path, fs::device_stat& info) override {
        fs::stat_t found;
        if (!stat(path, found)) return false;
        constexpr u64 headroom = 64ull << 30;
        info = {.block_size = 4096, .total_size = headroom, .total_free = headroom, .avail_free = headroom};
        return true;
    }
    std::unique_ptr<fs::file_base> open(const std::string& path, bs_t<fs::open_mode> mode) override {
        fs::stat_t found;
        const bool exists = stat(path, found);
        if (exists && found.is_directory) { fs::g_tls_error = fs::error::isdir; return {}; }
        if (exists && (mode & fs::excl) && (mode & fs::create)) { fs::g_tls_error = fs::error::exist; return {}; }
        if (!exists && !(mode & fs::create)) return {};
        const bool truncate = exists && !!(mode & fs::trunc);
        const char* flags = "r";
        if (mode & fs::write) flags = mode & fs::read ? (truncate ? "rwt" : "rw") :
            mode & fs::append ? "wa" : (truncate ? "wt" : "w");
        const int fd = callbacks.open_file ? callbacks.open_file(callbacks.userdata, relative(path).c_str(), flags, !!(mode & fs::create)) : -1;
        if (fd < 0) { fs::g_tls_error = fs::error::noent; return {}; }
        return fs::file::from_native_handle(fd).release();
    }
    std::unique_ptr<fs::dir_base> open_dir(const std::string& path) override {
        fs::stat_t info;
        if (!stat(path, info)) return {};
        if (!info.is_directory) { fs::g_tls_error = fs::error::notdir; return {}; }
        std::vector<fs::dir_entry> entries;
        const auto emit = [](void* sink, const char* name, const omni_ps3_file_info* info) {
            if (!name || !*name || !info) return;
            fs::dir_entry entry{};
            static_cast<fs::stat_t&>(entry) = convert(*info);
            entry.name = name;
            static_cast<std::vector<fs::dir_entry>*>(sink)->push_back(std::move(entry));
        };
        if (!callbacks.list_directory || !callbacks.list_directory(callbacks.userdata, relative(path).c_str(), &entries, emit)) return {};
        return std::make_unique<directory>(std::move(entries));
    }
    bool create_dir(const std::string& path) override {
        return callbacks.create_directory && callbacks.create_directory(callbacks.userdata, relative(path).c_str());
    }
    bool remove(const std::string& path) override {
        return callbacks.remove_file && callbacks.remove_file(callbacks.userdata, relative(path).c_str());
    }
    bool remove_dir(const std::string& path) override { return remove(path); }
    bool rename(const std::string& from, const std::string& to) override {
        return callbacks.rename_file && callbacks.rename_file(callbacks.userdata, relative(from).c_str(), relative(to).c_str());
    }
    bool trunc(const std::string& path, u64 length) override {
        const int fd = callbacks.open_file ? callbacks.open_file(callbacks.userdata, relative(path).c_str(), "rw", 0) : -1;
        if (fd < 0) return false;
        const bool result = ::ftruncate(fd, length) == 0;
        ::close(fd); return result;
    }
    bool utime(const std::string&, s64, s64) override { return true; }
};
}
void install_storage() {
    if (callbacks.stat_file && callbacks.open_file)
        fs::set_virtual_device("saf_storage_fs_dev", stx::make_shared<storage>());
}
}
