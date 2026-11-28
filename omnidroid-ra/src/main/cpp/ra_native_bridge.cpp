#include "omni_ra_abi.h"

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <unistd.h>

#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "OmnidroidRA"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct RaApiTable {
  void* dlHandle = nullptr;

  uint32_t (*abi_version)(void) = nullptr;
  int32_t (*get_build_info)(omni_ra_build_info_t*) = nullptr;
  omni_ra_t* (*create)(const omni_ra_host_t*) = nullptr;
  void (*destroy)(omni_ra_t*) = nullptr;
  int32_t (*set_user_agent)(omni_ra_t*, const char*) = nullptr;
  int32_t (*set_host)(omni_ra_t*, const char*) = nullptr;
  void (*set_log_level)(omni_ra_t*, int32_t) = nullptr;
  void (*set_hardcore)(omni_ra_t*, int32_t) = nullptr;
  int32_t (*get_hardcore)(const omni_ra_t*) = nullptr;
  void (*set_encore_mode)(omni_ra_t*, int32_t) = nullptr;
  void (*set_unofficial_enabled)(omni_ra_t*, int32_t) = nullptr;
  void (*set_spectator_mode)(omni_ra_t*, int32_t) = nullptr;
  int32_t (*is_system_allowed)(const char*, uint32_t) = nullptr;
  int32_t (*is_setting_allowed)(const char*, uint32_t, const char*, const char*) = nullptr;
  omni_ra_async_t* (*login_with_password)(omni_ra_t*, const char*, const char*, omni_ra_async_cb_t, void*) = nullptr;
  omni_ra_async_t* (*login_with_token)(omni_ra_t*, const char*, const char*, omni_ra_async_cb_t, void*) = nullptr;
  void (*logout)(omni_ra_t*) = nullptr;
  int32_t (*get_user_info)(const omni_ra_t*, omni_ra_user_info_t*) = nullptr;
  void (*cancel_async)(omni_ra_t*, omni_ra_async_t*) = nullptr;
  void (*http_complete)(omni_ra_t*, uint64_t, const omni_ra_http_response_t*) = nullptr;
  int32_t (*set_memory_map)(omni_ra_t*, const omni_ra_memory_map_t*) = nullptr;
  int32_t (*dump_memory_regions)(const omni_ra_t*, char*, uint32_t, uint32_t*) = nullptr;
  omni_ra_async_t* (*begin_identify_and_load_game)(
      omni_ra_t*, uint32_t, const char*, const uint8_t*, uint64_t, omni_ra_async_cb_t, void*) = nullptr;
  omni_ra_async_t* (*begin_load_game_hash)(omni_ra_t*, const char*, omni_ra_async_cb_t, void*) = nullptr;
  omni_ra_async_t* (*begin_identify_and_change_media)(
      omni_ra_t*, const char*, const uint8_t*, uint64_t, omni_ra_async_cb_t, void*) = nullptr;
  void (*unload_game)(omni_ra_t*) = nullptr;
  int32_t (*is_game_loaded)(const omni_ra_t*) = nullptr;
  int32_t (*get_load_state)(const omni_ra_t*) = nullptr;
  void (*do_frame)(omni_ra_t*) = nullptr;
  void (*idle)(omni_ra_t*) = nullptr;
  void (*reset)(omni_ra_t*) = nullptr;
  int32_t (*is_processing_required)(omni_ra_t*) = nullptr;
  int32_t (*can_pause)(omni_ra_t*, uint32_t*) = nullptr;
  uint32_t (*progress_size)(omni_ra_t*) = nullptr;
  int32_t (*serialize_progress)(omni_ra_t*, uint8_t*, uint32_t) = nullptr;
  int32_t (*deserialize_progress)(omni_ra_t*, const uint8_t*, uint32_t) = nullptr;
  int32_t (*get_game_info)(const omni_ra_t*, omni_ra_game_info_t*) = nullptr;
  int32_t (*get_user_game_summary)(const omni_ra_t*, omni_ra_user_game_summary_t*) = nullptr;
  int32_t (*has_rich_presence)(omni_ra_t*) = nullptr;
  int32_t (*get_rich_presence_message)(omni_ra_t*, char*, uint32_t, uint32_t*) = nullptr;
  omni_ra_achievement_list_t* (*get_achievement_list)(omni_ra_t*, int32_t, int32_t) = nullptr;
  void (*free_achievement_list)(omni_ra_achievement_list_t*) = nullptr;
  omni_ra_leaderboard_list_t* (*get_leaderboard_list)(omni_ra_t*, int32_t) = nullptr;
  void (*free_leaderboard_list)(omni_ra_leaderboard_list_t*) = nullptr;

  void clear() {
    if (dlHandle) {
      dlclose(dlHandle);
    }
    *this = RaApiTable{};
  }
};

struct LibretroSymbols {
  void (*set_native_hooks)(const LibretroDroidNativeHooks*) = nullptr;
  size_t (*get_memory_descriptors)(struct omni_retro_memory_descriptor*, size_t) = nullptr;
  void* (*get_memory_data)(unsigned) = nullptr;
  size_t (*get_memory_size)(unsigned) = nullptr;
  const char* (*get_library_name)(void) = nullptr;
  const char* (*get_library_version)(void) = nullptr;

  bool resolved() const {
    return set_native_hooks && get_memory_descriptors && get_memory_data && get_memory_size;
  }
};

struct ChdSymbols {
  void* (*open_track_fd)(int, uint32_t) = nullptr;
  void* (*open_track_path)(const char*, uint32_t) = nullptr;
  uint32_t (*read_sector)(void*, uint32_t, void*, uint32_t) = nullptr;
  void (*close_track)(void*) = nullptr;
  uint32_t (*first_track_sector)(void*) = nullptr;

  bool resolved() const {
    return open_track_fd && open_track_path && read_sector && close_track && first_track_sector;
  }
};

struct BridgeState {
  std::mutex mutex;
  JavaVM* jvm = nullptr;
  jobject bridgeRef = nullptr;
  jmethodID onHttpReqMethod = nullptr;
  jmethodID onAsyncCompleteMethod = nullptr;
  jmethodID onEventMethod = nullptr;
  jmethodID onOpenFdMethod = nullptr;

  RaApiTable api;
  omni_ra_t* ra = nullptr;
  uint32_t attachedConsoleId = 0;
  bool sessionAttached = false;

  LibretroSymbols retro;
  ChdSymbols chd;
};

BridgeState g_state;

class JniEnvScope {
 public:
  JniEnvScope() {
    JavaVM* vm = g_state.jvm;
    if (!vm) return;
    jint res = vm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
      if (vm->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
        attached_ = true;
      } else {
        env_ = nullptr;
      }
    } else if (res != JNI_OK) {
      env_ = nullptr;
    }
  }

  ~JniEnvScope() {
    if (attached_ && g_state.jvm) {
      g_state.jvm->DetachCurrentThread();
    }
  }

  JNIEnv* env() const { return env_; }

 private:
  JNIEnv* env_ = nullptr;
  bool attached_ = false;
};

jstring toJString(JNIEnv* env, const char* s) {
  return env->NewStringUTF(s ? s : "");
}

bool endsWithChd(const char* path) {
  if (!path) return false;
  size_t len = strlen(path);
  return len >= 4 && strcasecmp(path + len - 4, ".chd") == 0;
}

template <typename Fn>
bool resolveSym(void* handle, const char* name, Fn& out, std::string& missing) {
  void* sym = dlsym(handle, name);
  if (!sym) {
    missing = name;
    return false;
  }
  out = reinterpret_cast<Fn>(sym);
  return true;
}

bool resolveModuleTable(const char* path, RaApiTable& out, uint32_t expectedMajor, std::string& error) {
  void* handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
  if (!handle) {
    const char* err = dlerror();
    error = err ? err : "dlopen failed";
    return false;
  }
  out.dlHandle = handle;

  std::string missing;
  bool ok =
      resolveSym(handle, "omni_ra_abi_version", out.abi_version, missing) &&
      resolveSym(handle, "omni_ra_get_build_info", out.get_build_info, missing) &&
      resolveSym(handle, "omni_ra_create", out.create, missing) &&
      resolveSym(handle, "omni_ra_destroy", out.destroy, missing) &&
      resolveSym(handle, "omni_ra_set_user_agent", out.set_user_agent, missing) &&
      resolveSym(handle, "omni_ra_set_host", out.set_host, missing) &&
      resolveSym(handle, "omni_ra_set_log_level", out.set_log_level, missing) &&
      resolveSym(handle, "omni_ra_set_hardcore", out.set_hardcore, missing) &&
      resolveSym(handle, "omni_ra_get_hardcore", out.get_hardcore, missing) &&
      resolveSym(handle, "omni_ra_set_encore_mode", out.set_encore_mode, missing) &&
      resolveSym(handle, "omni_ra_set_unofficial_enabled", out.set_unofficial_enabled, missing) &&
      resolveSym(handle, "omni_ra_set_spectator_mode", out.set_spectator_mode, missing) &&
      resolveSym(handle, "omni_ra_is_system_allowed", out.is_system_allowed, missing) &&
      resolveSym(handle, "omni_ra_is_setting_allowed", out.is_setting_allowed, missing) &&
      resolveSym(handle, "omni_ra_login_with_password", out.login_with_password, missing) &&
      resolveSym(handle, "omni_ra_login_with_token", out.login_with_token, missing) &&
      resolveSym(handle, "omni_ra_logout", out.logout, missing) &&
      resolveSym(handle, "omni_ra_get_user_info", out.get_user_info, missing) &&
      resolveSym(handle, "omni_ra_cancel_async", out.cancel_async, missing) &&
      resolveSym(handle, "omni_ra_http_complete", out.http_complete, missing) &&
      resolveSym(handle, "omni_ra_set_memory_map", out.set_memory_map, missing) &&
      resolveSym(handle, "omni_ra_dump_memory_regions", out.dump_memory_regions, missing) &&
      resolveSym(handle, "omni_ra_begin_identify_and_load_game", out.begin_identify_and_load_game, missing) &&
      resolveSym(handle, "omni_ra_begin_load_game_hash", out.begin_load_game_hash, missing) &&
      resolveSym(handle, "omni_ra_begin_identify_and_change_media", out.begin_identify_and_change_media, missing) &&
      resolveSym(handle, "omni_ra_unload_game", out.unload_game, missing) &&
      resolveSym(handle, "omni_ra_is_game_loaded", out.is_game_loaded, missing) &&
      resolveSym(handle, "omni_ra_get_load_state", out.get_load_state, missing) &&
      resolveSym(handle, "omni_ra_do_frame", out.do_frame, missing) &&
      resolveSym(handle, "omni_ra_idle", out.idle, missing) &&
      resolveSym(handle, "omni_ra_reset", out.reset, missing) &&
      resolveSym(handle, "omni_ra_is_processing_required", out.is_processing_required, missing) &&
      resolveSym(handle, "omni_ra_can_pause", out.can_pause, missing) &&
      resolveSym(handle, "omni_ra_progress_size", out.progress_size, missing) &&
      resolveSym(handle, "omni_ra_serialize_progress", out.serialize_progress, missing) &&
      resolveSym(handle, "omni_ra_deserialize_progress", out.deserialize_progress, missing) &&
      resolveSym(handle, "omni_ra_get_game_info", out.get_game_info, missing) &&
      resolveSym(handle, "omni_ra_get_user_game_summary", out.get_user_game_summary, missing) &&
      resolveSym(handle, "omni_ra_has_rich_presence", out.has_rich_presence, missing) &&
      resolveSym(handle, "omni_ra_get_rich_presence_message", out.get_rich_presence_message, missing) &&
      resolveSym(handle, "omni_ra_get_achievement_list", out.get_achievement_list, missing) &&
      resolveSym(handle, "omni_ra_free_achievement_list", out.free_achievement_list, missing) &&
      resolveSym(handle, "omni_ra_get_leaderboard_list", out.get_leaderboard_list, missing) &&
      resolveSym(handle, "omni_ra_free_leaderboard_list", out.free_leaderboard_list, missing);

  if (!ok) {
    error = "Missing required RA symbol: " + missing;
    out.clear();
    return false;
  }

  const uint32_t ver = out.abi_version();
  const uint32_t major = OMNI_RA_ABI_MAJOR(ver);
  if (major != expectedMajor) {
    error = "Unsupported ABI major version: " + std::to_string(major);
    out.clear();
    return false;
  }
  return true;
}

void resolveOptionalRuntimeSymbols() {
  if (!g_state.retro.resolved()) {
    void* lr = dlopen("libretrodroid.so", RTLD_NOLOAD);
    if (lr) {
      std::string ignored;
      resolveSym(lr, "libretrodroid_set_native_hooks", g_state.retro.set_native_hooks, ignored);
      resolveSym(lr, "libretrodroid_get_memory_descriptors", g_state.retro.get_memory_descriptors, ignored);
      resolveSym(lr, "libretrodroid_get_memory_data", g_state.retro.get_memory_data, ignored);
      resolveSym(lr, "libretrodroid_get_memory_size", g_state.retro.get_memory_size, ignored);
      resolveSym(lr, "libretrodroid_get_library_name", g_state.retro.get_library_name, ignored);
      resolveSym(lr, "libretrodroid_get_library_version", g_state.retro.get_library_version, ignored);
    }
  }
  if (!g_state.chd.resolved()) {
    void* chd = dlopen("libomnidroid-chd.so", RTLD_NOLOAD);
    if (chd) {
      std::string ignored;
      resolveSym(chd, "omnichd_cd_open_track_fd", g_state.chd.open_track_fd, ignored);
      resolveSym(chd, "omnichd_cd_open_track_path", g_state.chd.open_track_path, ignored);
      resolveSym(chd, "omnichd_cd_read_sector", g_state.chd.read_sector, ignored);
      resolveSym(chd, "omnichd_cd_close_track", g_state.chd.close_track, ignored);
      resolveSym(chd, "omnichd_cd_first_track_sector", g_state.chd.first_track_sector, ignored);
    }
  }
}

void applyLibretroMemoryMapLocked(uint32_t consoleId) {
  if (!g_state.ra || !g_state.api.set_memory_map || !g_state.retro.resolved()) return;
  if (consoleId == 0) {
    g_state.api.set_memory_map(g_state.ra, nullptr);
    return;
  }

  const size_t totalDescs = g_state.retro.get_memory_descriptors(nullptr, 0);
  std::vector<omni_retro_memory_descriptor> rawDescs(totalDescs);
  if (totalDescs > 0) {
    g_state.retro.get_memory_descriptors(rawDescs.data(), totalDescs);
  }

  std::vector<omni_ra_memory_descriptor_t> abiDescs(totalDescs);
  for (size_t i = 0; i < totalDescs; ++i) {
    omni_ra_memory_descriptor_t& dst = abiDescs[i];
    memset(&dst, 0, sizeof(dst));
    dst.struct_size = static_cast<uint32_t>(sizeof(dst));
    dst.flags = rawDescs[i].flags;
    dst.ptr = rawDescs[i].ptr;
    dst.offset = rawDescs[i].offset;
    dst.start = rawDescs[i].start;
    dst.select = rawDescs[i].select;
    dst.disconnect = rawDescs[i].disconnect;
    dst.len = rawDescs[i].len;
    dst.addrspace = rawDescs[i].addrspace;
  }

  const unsigned regionTypes[4] = {
      OMNI_RETRO_MEMORY_SAVE_RAM,
      OMNI_RETRO_MEMORY_RTC,
      OMNI_RETRO_MEMORY_SYSTEM_RAM,
      OMNI_RETRO_MEMORY_VIDEO_RAM,
  };
  omni_ra_core_memory_region_t coreRegions[4];
  uint32_t regionCount = 0;
  for (unsigned t : regionTypes) {
    void* data = g_state.retro.get_memory_data(t);
    size_t sz = g_state.retro.get_memory_size(t);
    if (data && sz > 0) {
      omni_ra_core_memory_region_t& reg = coreRegions[regionCount++];
      memset(&reg, 0, sizeof(reg));
      reg.struct_size = static_cast<uint32_t>(sizeof(reg));
      reg.memory_type = t;
      reg.data = data;
      reg.size = sz;
    }
  }

  omni_ra_memory_map_t map;
  memset(&map, 0, sizeof(map));
  map.struct_size = static_cast<uint32_t>(sizeof(map));
  map.console_id = consoleId;
  map.descriptors = abiDescs.empty() ? nullptr : abiDescs.data();
  map.num_descriptors = static_cast<uint32_t>(abiDescs.size());
  map.core_regions = regionCount == 0 ? nullptr : coreRegions;
  map.num_core_regions = regionCount;

  g_state.api.set_memory_map(g_state.ra, &map);
}

/* =========================================================================
 * Host Callbacks from libomnidroid_ra.so & libretrodroid.so
 * ========================================================================= */

uint64_t hostGetTimeMs(void*) {
  struct timespec ts {};
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return static_cast<uint64_t>(ts.tv_sec) * 1000ull + static_cast<uint64_t>(ts.tv_nsec) / 1000000ull;
}

void hostLogMessage(void*, int32_t level, const char* msg) {
  if (!msg) return;
  int prio = ANDROID_LOG_INFO;
  if (level <= 1) prio = ANDROID_LOG_ERROR;
  else if (level == 2) prio = ANDROID_LOG_WARN;
  else if (level >= 4) prio = ANDROID_LOG_DEBUG;
  __android_log_print(prio, LOG_TAG, "%s", msg);
}

void hostHttpRequest(void*, uint64_t requestId, const omni_ra_http_request_t* req) {
  JniEnvScope scope;
  JNIEnv* env = scope.env();
  jobject ref = nullptr;
  jmethodID mid = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ref = g_state.bridgeRef;
    mid = g_state.onHttpReqMethod;
  }
  if (!env || !ref || !mid || !req) return;

  jstring jUrl = toJString(env, req->url);
  jstring jPost = req->post_data ? toJString(env, req->post_data) : nullptr;
  jstring jCt = req->content_type ? toJString(env, req->content_type) : nullptr;
  jstring jUa = req->user_agent ? toJString(env, req->user_agent) : nullptr;

  env->CallVoidMethod(ref, mid, static_cast<jlong>(requestId), jUrl, jPost, jCt, jUa);
  if (env->ExceptionCheck()) env->ExceptionClear();
  if (jUrl) env->DeleteLocalRef(jUrl);
  if (jPost) env->DeleteLocalRef(jPost);
  if (jCt) env->DeleteLocalRef(jCt);
  if (jUa) env->DeleteLocalRef(jUa);
}

void hostOnEvent(void*, const omni_ra_event_t* ev) {
  JniEnvScope scope;
  JNIEnv* env = scope.env();
  jobject ref = nullptr;
  jmethodID mid = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ref = g_state.bridgeRef;
    mid = g_state.onEventMethod;
  }
  if (!env || !ref || !mid || !ev) return;

  uint32_t primaryId = 0;
  uint32_t points = 0;
  int32_t errCode = 0;
  uint32_t newRank = 0;
  uint32_t numEntries = 0;
  const char* title = nullptr;
  const char* desc = nullptr;
  const char* badgeUrl = nullptr;
  const char* measured = nullptr;
  const char* extra1 = nullptr;
  const char* extra2 = nullptr;

  if (ev->achievement) {
    primaryId = ev->achievement->id;
    points = ev->achievement->points;
    title = ev->achievement->title;
    desc = ev->achievement->description;
    badgeUrl = ev->achievement->unlocked ? ev->achievement->badge_url : ev->achievement->badge_locked_url;
    if (!badgeUrl || !badgeUrl[0]) badgeUrl = ev->achievement->badge_url;
    measured = ev->achievement->measured_progress;
  } else if (ev->leaderboard) {
    primaryId = ev->leaderboard->id;
    title = ev->leaderboard->title;
    desc = ev->leaderboard->description;
    extra1 = ev->leaderboard->tracker_value;
  } else if (ev->leaderboard_tracker) {
    primaryId = ev->leaderboard_tracker->id;
    extra1 = ev->leaderboard_tracker->display;
  } else if (ev->leaderboard_scoreboard) {
    primaryId = ev->leaderboard_scoreboard->leaderboard_id;
    newRank = ev->leaderboard_scoreboard->new_rank;
    numEntries = ev->leaderboard_scoreboard->num_entries;
    extra1 = ev->leaderboard_scoreboard->submitted_score;
    extra2 = ev->leaderboard_scoreboard->best_score;
  } else if (ev->subset) {
    primaryId = ev->subset->id;
    title = ev->subset->title;
    badgeUrl = ev->subset->badge_url;
    points = ev->subset->num_achievements;
  } else if (ev->server_error) {
    errCode = ev->server_error->result;
    primaryId = ev->server_error->related_id;
    title = ev->server_error->api;
    desc = ev->server_error->error_message;
  }

  jstring jTitle = toJString(env, title);
  jstring jDesc = toJString(env, desc);
  jstring jBadge = toJString(env, badgeUrl);
  jstring jMeasured = toJString(env, measured);
  jstring jExtra1 = toJString(env, extra1);
  jstring jExtra2 = toJString(env, extra2);

  env->CallVoidMethod(
      ref, mid,
      static_cast<jint>(ev->type),
      static_cast<jint>(primaryId),
      static_cast<jint>(points),
      static_cast<jint>(newRank),
      static_cast<jint>(numEntries),
      static_cast<jint>(errCode),
      jTitle, jDesc, jBadge, jMeasured, jExtra1, jExtra2);
  if (env->ExceptionCheck()) env->ExceptionClear();
  env->DeleteLocalRef(jTitle);
  env->DeleteLocalRef(jDesc);
  env->DeleteLocalRef(jBadge);
  env->DeleteLocalRef(jMeasured);
  env->DeleteLocalRef(jExtra1);
  env->DeleteLocalRef(jExtra2);
}

int askJavaOpenFd(const char* pathUtf8) {
  JniEnvScope scope;
  JNIEnv* env = scope.env();
  jobject ref = nullptr;
  jmethodID mid = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ref = g_state.bridgeRef;
    mid = g_state.onOpenFdMethod;
  }
  if (!env || !ref || !mid || !pathUtf8) return -1;
  jstring jPath = toJString(env, pathUtf8);
  jint fd = env->CallIntMethod(ref, mid, jPath);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    fd = -1;
  }
  env->DeleteLocalRef(jPath);
  return static_cast<int>(fd);
}

void* hostFileOpen(void*, const char* pathUtf8) {
  if (!pathUtf8 || !pathUtf8[0]) return nullptr;
  FILE* fp = fopen(pathUtf8, "rb");
  if (fp) return fp;

  int fd = askJavaOpenFd(pathUtf8);
  if (fd >= 0) {
    fp = fdopen(fd, "rb");
    if (fp) return fp;
  }
  return nullptr;
}

void hostFileSeek(void*, void* fileHandle, int64_t offset, int32_t origin) {
  if (!fileHandle) return;
  int whence = SEEK_SET;
  if (origin == 1) whence = SEEK_CUR;
  else if (origin == 2) whence = SEEK_END;
  fseeko(static_cast<FILE*>(fileHandle), static_cast<off_t>(offset), whence);
}

int64_t hostFileTell(void*, void* fileHandle) {
  if (!fileHandle) return -1;
  return static_cast<int64_t>(ftello(static_cast<FILE*>(fileHandle)));
}

uint32_t hostFileRead(void*, void* fileHandle, void* buffer, uint32_t requestedBytes) {
  if (!fileHandle || !buffer || requestedBytes == 0) return 0;
  return static_cast<uint32_t>(fread(buffer, 1, requestedBytes, static_cast<FILE*>(fileHandle)));
}

void hostFileClose(void*, void* fileHandle) {
  if (fileHandle) fclose(static_cast<FILE*>(fileHandle));
}

void* hostCdOpenTrack(void*, const char* pathUtf8, uint32_t track) {
  if (!endsWithChd(pathUtf8)) {
    return nullptr; // Allows omni_ra to fall back cleanly to default CUE/BIN/GDI/ISO cdreader
  }
  ChdSymbols chd;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    resolveOptionalRuntimeSymbols();
    chd = g_state.chd;
  }
  if (!chd.resolved()) return nullptr;
  void* h = chd.open_track_path(pathUtf8, track);
  if (h) return h;

  int fd = askJavaOpenFd(pathUtf8);
  if (fd >= 0) {
    h = chd.open_track_fd(fd, track);
    ::close(fd);
    return h;
  }
  return nullptr;
}

uint32_t hostCdReadSector(void*, void* trackHandle, uint32_t sector, void* buffer, uint32_t requestedBytes) {
  return g_state.chd.read_sector ? g_state.chd.read_sector(trackHandle, sector, buffer, requestedBytes) : 0;
}

void hostCdCloseTrack(void*, void* trackHandle) {
  if (g_state.chd.close_track) g_state.chd.close_track(trackHandle);
}

uint32_t hostCdFirstTrackSector(void*, void* trackHandle) {
  return g_state.chd.first_track_sector ? g_state.chd.first_track_sector(trackHandle) : 0;
}

void hookAfterFrame(void*) {
  // Runs natively inside LibretroDroid::step() right after core->retro_run(): zero JNI per frame!
  omni_ra_t* ra = nullptr;
  void (*doFrame)(omni_ra_t*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.sessionAttached) return;
    ra = g_state.ra;
    doFrame = g_state.api.do_frame;
  }
  if (ra && doFrame) {
    doFrame(ra);
  }
}

void hookAfterReset(void*) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.sessionAttached && g_state.ra && g_state.api.reset) {
    g_state.api.reset(g_state.ra);
  }
}

void hookMemoryMapChanged(void*) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.sessionAttached && g_state.attachedConsoleId != 0) {
    applyLibretroMemoryMapLocked(g_state.attachedConsoleId);
  }
}

void hookBeforeUnload(void*) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  g_state.sessionAttached = false;
  if (g_state.ra && g_state.api.set_memory_map) {
    g_state.api.set_memory_map(g_state.ra, nullptr);
  }
}

void hostAsyncCallback(void* cbUserdata, int32_t result, const char* err) {
  uint64_t token = reinterpret_cast<uintptr_t>(cbUserdata);
  JniEnvScope scope;
  JNIEnv* env = scope.env();
  jobject ref = nullptr;
  jmethodID mid = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ref = g_state.bridgeRef;
    mid = g_state.onAsyncCompleteMethod;
  }
  if (!env || !ref || !mid) return;
  jstring jErr = toJString(env, err);
  env->CallVoidMethod(ref, mid, static_cast<jlong>(token), static_cast<jint>(result), jErr);
  if (env->ExceptionCheck()) env->ExceptionClear();
  env->DeleteLocalRef(jErr);
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  g_state.jvm = vm;
  return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeLoadModule(
    JNIEnv* env, jobject thiz, jstring pathStr, jint expectedAbiMajor, jobjectArray outBuildInfo) {
  const char* path = pathStr ? env->GetStringUTFChars(pathStr, nullptr) : nullptr;
  if (!path) return toJString(env, "Module path is null");
  std::string pathCopy(path);
  env->ReleaseStringUTFChars(pathStr, path);

  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.sessionAttached) {
    return toJString(env, "Cannot swap RA module while a game session is running");
  }

  RaApiTable candidate;
  std::string err;
  if (!resolveModuleTable(pathCopy.c_str(), candidate, static_cast<uint32_t>(expectedAbiMajor), err)) {
    return toJString(env, err.c_str());
  }

  omni_ra_build_info_t binfo {};
  binfo.struct_size = static_cast<uint32_t>(sizeof(binfo));
  if (candidate.get_build_info(&binfo) != OMNI_RA_OK) {
    candidate.clear();
    return toJString(env, "Failed to query omni_ra_get_build_info");
  }

  omni_ra_file_callbacks_t fileCbs {};
  fileCbs.struct_size = static_cast<uint32_t>(sizeof(fileCbs));
  fileCbs.open = hostFileOpen;
  fileCbs.seek = hostFileSeek;
  fileCbs.tell = hostFileTell;
  fileCbs.read = hostFileRead;
  fileCbs.close = hostFileClose;

  omni_ra_cdreader_callbacks_t cdCbs {};
  cdCbs.struct_size = static_cast<uint32_t>(sizeof(cdCbs));
  cdCbs.open_track = hostCdOpenTrack;
  cdCbs.read_sector = hostCdReadSector;
  cdCbs.close_track = hostCdCloseTrack;
  cdCbs.first_track_sector = hostCdFirstTrackSector;

  omni_ra_host_t host {};
  host.struct_size = static_cast<uint32_t>(sizeof(host));
  host.host_abi_version = OMNI_RA_MAKE_ABI_VERSION(OMNI_RA_ABI_VERSION_MAJOR, OMNI_RA_ABI_VERSION_MINOR);
  host.userdata = &g_state;
  host.get_time_ms = hostGetTimeMs;
  host.http_request = hostHttpRequest;
  host.on_event = hostOnEvent;
  host.read_memory = nullptr;
  host.log_message = hostLogMessage;
  host.file_callbacks = &fileCbs;
  host.cdreader_callbacks = &cdCbs;

  omni_ra_t* instance = candidate.create(&host);
  if (!instance) {
    candidate.clear();
    return toJString(env, "omni_ra_create returned null");
  }

  if (g_state.ra && g_state.api.destroy) {
    g_state.api.destroy(g_state.ra);
  }
  g_state.api.clear();
  if (g_state.bridgeRef) {
    env->DeleteGlobalRef(g_state.bridgeRef);
  }

  jclass cls = env->GetObjectClass(thiz);
  g_state.bridgeRef = env->NewGlobalRef(thiz);
  g_state.onHttpReqMethod = env->GetMethodID(
      cls, "onNativeHttpRequest", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
  g_state.onAsyncCompleteMethod = env->GetMethodID(
      cls, "onNativeAsyncComplete", "(JILjava/lang/String;)V");
  g_state.onEventMethod = env->GetMethodID(
      cls, "onNativeEvent",
      "(IIIIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
  g_state.onOpenFdMethod = env->GetMethodID(cls, "onNativeOpenFd", "(Ljava/lang/String;)I");

  g_state.api = candidate;
  g_state.ra = instance;
  g_state.api.set_log_level(g_state.ra, 3);

  if (outBuildInfo && env->GetArrayLength(outBuildInfo) >= 5) {
    jstring jMaj = toJString(env, std::to_string(binfo.abi_major).c_str());
    jstring jMin = toJString(env, std::to_string(binfo.abi_minor).c_str());
    jstring jModVer = toJString(env, binfo.module_version);
    jstring jRcVer = toJString(env, binfo.rcheevos_version);
    jstring jHash = toJString(env, binfo.git_hash);
    env->SetObjectArrayElement(outBuildInfo, 0, jMaj);
    env->SetObjectArrayElement(outBuildInfo, 1, jMin);
    env->SetObjectArrayElement(outBuildInfo, 2, jModVer);
    env->SetObjectArrayElement(outBuildInfo, 3, jRcVer);
    env->SetObjectArrayElement(outBuildInfo, 4, jHash);
    env->DeleteLocalRef(jMaj);
    env->DeleteLocalRef(jMin);
    env->DeleteLocalRef(jModVer);
    env->DeleteLocalRef(jRcVer);
    env->DeleteLocalRef(jHash);
  }

  return nullptr;
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeUnloadModule(JNIEnv* env, jobject) {
  omni_ra_t* raToDestroy = nullptr;
  RaApiTable oldApi;
  jobject refToDelete = nullptr;
  LibretroSymbols retroCopy;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    g_state.sessionAttached = false;
    g_state.attachedConsoleId = 0;
    retroCopy = g_state.retro;
    raToDestroy = g_state.ra;
    oldApi = g_state.api;
    g_state.ra = nullptr;
    g_state.api = RaApiTable{};
    refToDelete = g_state.bridgeRef;
    g_state.bridgeRef = nullptr;
  }
  if (retroCopy.set_native_hooks) {
    retroCopy.set_native_hooks(nullptr);
  }
  if (raToDestroy) {
    if (oldApi.set_memory_map) oldApi.set_memory_map(raToDestroy, nullptr);
    if (oldApi.destroy) oldApi.destroy(raToDestroy);
  }
  oldApi.clear();
  if (refToDelete) {
    env->DeleteGlobalRef(refToDelete);
  }
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeHttpComplete(
    JNIEnv* env, jobject, jlong requestId, jint status, jbyteArray body) {
  omni_ra_t* ra = nullptr;
  void (*httpComplete)(omni_ra_t*, uint64_t, const omni_ra_http_response_t*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    httpComplete = g_state.api.http_complete;
  }
  if (!ra || !httpComplete) return;

  jsize len = body ? env->GetArrayLength(body) : 0;
  jbyte* ptr = body ? env->GetByteArrayElements(body, nullptr) : nullptr;
  omni_ra_http_response_t resp {};
  resp.struct_size = static_cast<uint32_t>(sizeof(resp));
  resp.http_status = static_cast<int32_t>(status);
  resp.body = ptr;
  resp.body_len = static_cast<uint32_t>(len > 0 ? len : 0);

  httpComplete(ra, static_cast<uint64_t>(requestId), &resp);

  if (ptr) env->ReleaseByteArrayElements(body, ptr, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeSetUserAgent(
    JNIEnv* env, jobject, jstring ua) {
  const char* s = ua ? env->GetStringUTFChars(ua, nullptr) : nullptr;
  if (!s) return;
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.ra && g_state.api.set_user_agent) {
    g_state.api.set_user_agent(g_state.ra, s);
  }
  env->ReleaseStringUTFChars(ua, s);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeApplySettings(
    JNIEnv*, jobject, jboolean hardcore, jboolean encore, jboolean unofficial, jboolean spectator) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra) return;
  if (g_state.api.set_hardcore) g_state.api.set_hardcore(g_state.ra, hardcore ? 1 : 0);
  if (g_state.api.set_encore_mode) g_state.api.set_encore_mode(g_state.ra, encore ? 1 : 0);
  if (g_state.api.set_unofficial_enabled) g_state.api.set_unofficial_enabled(g_state.ra, unofficial ? 1 : 0);
  if (g_state.api.set_spectator_mode) g_state.api.set_spectator_mode(g_state.ra, spectator ? 1 : 0);
}

JNIEXPORT jboolean JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetHardcore(JNIEnv*, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  return (g_state.ra && g_state.api.get_hardcore && g_state.api.get_hardcore(g_state.ra)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeLoginWithPassword(
    JNIEnv* env, jobject, jlong token, jstring user, jstring pass) {
  const char* u = user ? env->GetStringUTFChars(user, nullptr) : nullptr;
  const char* p = pass ? env->GetStringUTFChars(pass, nullptr) : nullptr;
  omni_ra_t* ra = nullptr;
  omni_ra_async_t* (*fn)(omni_ra_t*, const char*, const char*, omni_ra_async_cb_t, void*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    fn = g_state.api.login_with_password;
  }
  if (ra && fn && u && p) {
    fn(ra, u, p, hostAsyncCallback, reinterpret_cast<void*>(static_cast<uintptr_t>(token)));
  } else {
    hostAsyncCallback(reinterpret_cast<void*>(static_cast<uintptr_t>(token)), -25, "Module not loaded");
  }
  if (u) env->ReleaseStringUTFChars(user, u);
  if (p) env->ReleaseStringUTFChars(pass, p);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeLoginWithToken(
    JNIEnv* env, jobject, jlong token, jstring user, jstring apiToken) {
  const char* u = user ? env->GetStringUTFChars(user, nullptr) : nullptr;
  const char* t = apiToken ? env->GetStringUTFChars(apiToken, nullptr) : nullptr;
  omni_ra_t* ra = nullptr;
  omni_ra_async_t* (*fn)(omni_ra_t*, const char*, const char*, omni_ra_async_cb_t, void*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    fn = g_state.api.login_with_token;
  }
  if (ra && fn && u && t) {
    fn(ra, u, t, hostAsyncCallback, reinterpret_cast<void*>(static_cast<uintptr_t>(token)));
  } else {
    hostAsyncCallback(reinterpret_cast<void*>(static_cast<uintptr_t>(token)), -25, "Module not loaded");
  }
  if (u) env->ReleaseStringUTFChars(user, u);
  if (t) env->ReleaseStringUTFChars(apiToken, t);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeLogout(JNIEnv*, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.ra && g_state.api.logout) g_state.api.logout(g_state.ra);
}

JNIEXPORT jobjectArray JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetUserInfo(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.get_user_info) return nullptr;
  omni_ra_user_info_t info {};
  info.struct_size = static_cast<uint32_t>(sizeof(info));
  if (g_state.api.get_user_info(g_state.ra, &info) != OMNI_RA_OK) return nullptr;

  jclass strCls = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(8, strCls, nullptr);
  const char* vals[8] = {
      info.username,
      info.display_name,
      info.token,
      info.avatar_url,
      nullptr, nullptr, nullptr, nullptr
  };
  std::string score = std::to_string(info.score);
  std::string softScore = std::to_string(info.score_softcore);
  std::string unread = std::to_string(info.num_unread_messages);
  std::string avatarTs = std::to_string(info.avatar_last_updated);
  vals[4] = score.c_str();
  vals[5] = softScore.c_str();
  vals[6] = unread.c_str();
  vals[7] = avatarTs.c_str();
  for (int i = 0; i < 8; ++i) {
    jstring js = toJString(env, vals[i]);
    env->SetObjectArrayElement(arr, i, js);
    env->DeleteLocalRef(js);
  }
  return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeIsSystemAllowed(
    JNIEnv* env, jobject, jstring libName, jint consoleId) {
  const char* lib = libName ? env->GetStringUTFChars(libName, nullptr) : nullptr;
  bool allowed = true;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (g_state.api.is_system_allowed && lib) {
      allowed = g_state.api.is_system_allowed(lib, static_cast<uint32_t>(consoleId)) != 0;
    }
  }
  if (lib) env->ReleaseStringUTFChars(libName, lib);
  return allowed ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeIsSettingAllowed(
    JNIEnv* env, jobject, jstring libName, jint consoleId, jstring key, jstring value) {
  const char* l = libName ? env->GetStringUTFChars(libName, nullptr) : nullptr;
  const char* k = key ? env->GetStringUTFChars(key, nullptr) : nullptr;
  const char* v = value ? env->GetStringUTFChars(value, nullptr) : nullptr;
  bool allowed = true;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (g_state.api.is_setting_allowed && l && k && v) {
      allowed = g_state.api.is_setting_allowed(l, static_cast<uint32_t>(consoleId), k, v) != 0;
    }
  }
  if (l) env->ReleaseStringUTFChars(libName, l);
  if (k) env->ReleaseStringUTFChars(key, k);
  if (v) env->ReleaseStringUTFChars(value, v);
  return allowed ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeBeginIdentifyAndLoadGame(
    JNIEnv* env, jobject, jlong token, jint consoleId, jstring path, jbyteArray data) {
  const char* p = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
  jsize sz = data ? env->GetArrayLength(data) : 0;
  jbyte* ptr = data ? env->GetByteArrayElements(data, nullptr) : nullptr;

  omni_ra_t* ra = nullptr;
  omni_ra_async_t* (*fn)(omni_ra_t*, uint32_t, const char*, const uint8_t*, uint64_t, omni_ra_async_cb_t, void*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    fn = g_state.api.begin_identify_and_load_game;
  }
  if (ra && fn) {
    fn(ra, static_cast<uint32_t>(consoleId), p,
       reinterpret_cast<const uint8_t*>(ptr), static_cast<uint64_t>(sz > 0 ? sz : 0),
       hostAsyncCallback, reinterpret_cast<void*>(static_cast<uintptr_t>(token)));
  } else {
    hostAsyncCallback(reinterpret_cast<void*>(static_cast<uintptr_t>(token)), -25, "Module not loaded");
  }
  if (ptr) env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
  if (p) env->ReleaseStringUTFChars(path, p);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeBeginLoadGameHash(
    JNIEnv* env, jobject, jlong token, jstring hash) {
  const char* h = hash ? env->GetStringUTFChars(hash, nullptr) : nullptr;
  omni_ra_t* ra = nullptr;
  omni_ra_async_t* (*fn)(omni_ra_t*, const char*, omni_ra_async_cb_t, void*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    fn = g_state.api.begin_load_game_hash;
  }
  if (ra && fn && h) {
    fn(ra, h, hostAsyncCallback, reinterpret_cast<void*>(static_cast<uintptr_t>(token)));
  } else {
    hostAsyncCallback(reinterpret_cast<void*>(static_cast<uintptr_t>(token)), -25, "Module not loaded");
  }
  if (h) env->ReleaseStringUTFChars(hash, h);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeBeginIdentifyAndChangeMedia(
    JNIEnv* env, jobject, jlong token, jstring path, jbyteArray data) {
  const char* p = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
  jsize sz = data ? env->GetArrayLength(data) : 0;
  jbyte* ptr = data ? env->GetByteArrayElements(data, nullptr) : nullptr;
  omni_ra_t* ra = nullptr;
  omni_ra_async_t* (*fn)(omni_ra_t*, const char*, const uint8_t*, uint64_t, omni_ra_async_cb_t, void*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    fn = g_state.api.begin_identify_and_change_media;
  }
  if (ra && fn) {
    fn(ra, p, reinterpret_cast<const uint8_t*>(ptr), static_cast<uint64_t>(sz > 0 ? sz : 0),
       hostAsyncCallback, reinterpret_cast<void*>(static_cast<uintptr_t>(token)));
  } else {
    hostAsyncCallback(reinterpret_cast<void*>(static_cast<uintptr_t>(token)), -25, "Module not loaded");
  }
  if (ptr) env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
  if (p) env->ReleaseStringUTFChars(path, p);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeUnloadGame(JNIEnv*, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.ra && g_state.api.unload_game) g_state.api.unload_game(g_state.ra);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeAttachLibretroSession(
    JNIEnv*, jobject, jint consoleId) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  resolveOptionalRuntimeSymbols();
  g_state.attachedConsoleId = static_cast<uint32_t>(consoleId);
  g_state.sessionAttached = true;
  if (g_state.retro.set_native_hooks) {
    LibretroDroidNativeHooks hooks {};
    hooks.struct_size = static_cast<uint32_t>(sizeof(hooks));
    hooks.userdata = &g_state;
    hooks.on_after_frame = hookAfterFrame;
    hooks.on_after_reset = hookAfterReset;
    hooks.on_memory_map_changed = hookMemoryMapChanged;
    hooks.on_before_unload = hookBeforeUnload;
    g_state.retro.set_native_hooks(&hooks);
  }
  applyLibretroMemoryMapLocked(static_cast<uint32_t>(consoleId));
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeDetachLibretroSession(JNIEnv*, jobject) {
  LibretroSymbols retroCopy;
  omni_ra_t* ra = nullptr;
  int32_t (*setMmap)(omni_ra_t*, const omni_ra_memory_map_t*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    g_state.sessionAttached = false;
    g_state.attachedConsoleId = 0;
    retroCopy = g_state.retro;
    ra = g_state.ra;
    setMmap = g_state.api.set_memory_map;
  }
  if (retroCopy.set_native_hooks) retroCopy.set_native_hooks(nullptr);
  if (ra && setMmap) setMmap(ra, nullptr);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeRefreshMemoryMap(JNIEnv*, jobject, jint consoleId) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  g_state.attachedConsoleId = static_cast<uint32_t>(consoleId);
  applyLibretroMemoryMapLocked(static_cast<uint32_t>(consoleId));
}

JNIEXPORT jstring JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeDumpMemoryRegions(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.dump_memory_regions) return toJString(env, "");
  char buf[2048];
  uint32_t outLen = 0;
  g_state.api.dump_memory_regions(g_state.ra, buf, sizeof(buf), &outLen);
  return toJString(env, buf);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeIdle(JNIEnv*, jobject) {
  omni_ra_t* ra = nullptr;
  void (*idle)(omni_ra_t*) = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    ra = g_state.ra;
    idle = g_state.api.idle;
  }
  if (ra && idle) idle(ra);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeReset(JNIEnv*, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (g_state.ra && g_state.api.reset) g_state.api.reset(g_state.ra);
}

JNIEXPORT jint JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeCanPause(JNIEnv*, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.can_pause) return -1;
  uint32_t rem = 0;
  if (g_state.api.can_pause(g_state.ra, &rem)) return -1;
  return static_cast<jint>(rem);
}

JNIEXPORT jbyteArray JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeSerializeProgress(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.progress_size || !g_state.api.serialize_progress) return nullptr;
  uint32_t sz = g_state.api.progress_size(g_state.ra);
  if (sz == 0) return nullptr;
  std::vector<uint8_t> buf(sz);
  if (g_state.api.serialize_progress(g_state.ra, buf.data(), sz) != OMNI_RA_OK) return nullptr;
  jbyteArray out = env->NewByteArray(static_cast<jsize>(sz));
  if (out) env->SetByteArrayRegion(out, 0, static_cast<jsize>(sz), reinterpret_cast<const jbyte*>(buf.data()));
  return out;
}

JNIEXPORT jboolean JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeDeserializeProgress(
    JNIEnv* env, jobject, jbyteArray data) {
  if (!data) return JNI_FALSE;
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.deserialize_progress) return JNI_FALSE;
  jsize sz = env->GetArrayLength(data);
  if (sz <= 0) return JNI_FALSE;
  jbyte* ptr = env->GetByteArrayElements(data, nullptr);
  int32_t rc = g_state.api.deserialize_progress(
      g_state.ra, reinterpret_cast<const uint8_t*>(ptr), static_cast<uint32_t>(sz));
  env->ReleaseByteArrayElements(data, ptr, JNI_ABORT);
  return rc == OMNI_RA_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetGameInfo(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.get_game_info) return nullptr;
  omni_ra_game_info_t info {};
  info.struct_size = static_cast<uint32_t>(sizeof(info));
  if (g_state.api.get_game_info(g_state.ra, &info) != OMNI_RA_OK) return nullptr;

  jclass strCls = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(6, strCls, nullptr);
  std::string idStr = std::to_string(info.id);
  std::string conStr = std::to_string(info.console_id);
  const char* vals[6] = {idStr.c_str(), conStr.c_str(), info.title, info.hash, info.badge_name, info.badge_url};
  for (int i = 0; i < 6; ++i) {
    jstring js = toJString(env, vals[i]);
    env->SetObjectArrayElement(arr, i, js);
    env->DeleteLocalRef(js);
  }
  return arr;
}

JNIEXPORT jlongArray JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetUserGameSummary(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.get_user_game_summary) return nullptr;
  omni_ra_user_game_summary_t s {};
  s.struct_size = static_cast<uint32_t>(sizeof(s));
  if (g_state.api.get_user_game_summary(g_state.ra, &s) != OMNI_RA_OK) return nullptr;
  jlong vals[8] = {
      static_cast<jlong>(s.num_core_achievements),
      static_cast<jlong>(s.num_unofficial_achievements),
      static_cast<jlong>(s.num_unlocked_achievements),
      static_cast<jlong>(s.num_unsupported_achievements),
      static_cast<jlong>(s.points_core),
      static_cast<jlong>(s.points_unlocked),
      static_cast<jlong>(s.beaten_time),
      static_cast<jlong>(s.completed_time),
  };
  jlongArray arr = env->NewLongArray(8);
  if (arr) env->SetLongArrayRegion(arr, 0, 8, vals);
  return arr;
}

JNIEXPORT jstring JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetRichPresence(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.has_rich_presence || !g_state.api.get_rich_presence_message) {
    return toJString(env, "");
  }
  if (!g_state.api.has_rich_presence(g_state.ra)) return toJString(env, "");
  char buf[512] {};
  uint32_t len = 0;
  g_state.api.get_rich_presence_message(g_state.ra, buf, sizeof(buf), &len);
  return toJString(env, buf);
}

JNIEXPORT jobjectArray JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetAchievements(
    JNIEnv* env, jobject, jint category, jint grouping) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.get_achievement_list || !g_state.api.free_achievement_list) return nullptr;
  omni_ra_achievement_list_t* list =
      g_state.api.get_achievement_list(g_state.ra, static_cast<int32_t>(category), static_cast<int32_t>(grouping));
  if (!list) return nullptr;

  std::vector<std::vector<std::string>> rows;
  for (uint32_t b = 0; b < list->num_buckets; ++b) {
    const omni_ra_achievement_bucket_t& bucket = list->buckets[b];
    for (uint32_t i = 0; i < bucket.num_achievements; ++i) {
      const omni_ra_achievement_t* a = bucket.achievements[i];
      if (!a) continue;
      rows.push_back({
          std::to_string(bucket.subset_id),
          std::to_string(bucket.bucket_type),
          bucket.label ? bucket.label : "",
          std::to_string(a->id),
          a->title ? a->title : "",
          a->description ? a->description : "",
          std::to_string(a->points),
          std::to_string(a->unlock_time),
          std::to_string(a->measured_percent),
          a->measured_progress ? a->measured_progress : "",
          std::to_string(a->rarity),
          std::to_string(a->rarity_hardcore),
          std::to_string(a->state),
          std::to_string(a->category),
          std::to_string(a->bucket),
          std::to_string(a->unlocked),
          std::to_string(a->type),
          a->badge_name ? a->badge_name : "",
          a->badge_url ? a->badge_url : "",
          a->badge_locked_url ? a->badge_locked_url : "",
      });
    }
  }
  g_state.api.free_achievement_list(list);

  jclass strArrCls = env->FindClass("[Ljava/lang/String;");
  jclass strCls = env->FindClass("java/lang/String");
  jobjectArray result = env->NewObjectArray(static_cast<jsize>(rows.size()), strArrCls, nullptr);
  for (size_t r = 0; r < rows.size(); ++r) {
    jobjectArray row = env->NewObjectArray(20, strCls, nullptr);
    for (size_t c = 0; c < 20; ++c) {
      jstring js = toJString(env, rows[r][c].c_str());
      env->SetObjectArrayElement(row, static_cast<jsize>(c), js);
      env->DeleteLocalRef(js);
    }
    env->SetObjectArrayElement(result, static_cast<jsize>(r), row);
    env->DeleteLocalRef(row);
  }
  return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetLeaderboards(JNIEnv* env, jobject, jint grouping) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  if (!g_state.ra || !g_state.api.get_leaderboard_list || !g_state.api.free_leaderboard_list) return nullptr;
  omni_ra_leaderboard_list_t* list =
      g_state.api.get_leaderboard_list(g_state.ra, static_cast<int32_t>(grouping));
  if (!list) return nullptr;

  std::vector<std::vector<std::string>> rows;
  for (uint32_t b = 0; b < list->num_buckets; ++b) {
    const omni_ra_leaderboard_bucket_t& bucket = list->buckets[b];
    for (uint32_t i = 0; i < bucket.num_leaderboards; ++i) {
      const omni_ra_leaderboard_t* lb = bucket.leaderboards[i];
      if (!lb) continue;
      rows.push_back({
          std::to_string(bucket.subset_id),
          std::to_string(bucket.bucket_type),
          bucket.label ? bucket.label : "",
          std::to_string(lb->id),
          lb->title ? lb->title : "",
          lb->description ? lb->description : "",
          lb->tracker_value ? lb->tracker_value : "",
          std::to_string(lb->state),
          std::to_string(lb->format),
          lb->lower_is_better ? "1" : "0",
      });
    }
  }
  g_state.api.free_leaderboard_list(list);

  jclass strArrCls = env->FindClass("[Ljava/lang/String;");
  jclass strCls = env->FindClass("java/lang/String");
  jobjectArray result = env->NewObjectArray(static_cast<jsize>(rows.size()), strArrCls, nullptr);
  for (size_t r = 0; r < rows.size(); ++r) {
    jobjectArray row = env->NewObjectArray(10, strCls, nullptr);
    for (size_t c = 0; c < 10; ++c) {
      jstring js = toJString(env, rows[r][c].c_str());
      env->SetObjectArrayElement(row, static_cast<jsize>(c), js);
      env->DeleteLocalRef(js);
    }
    env->SetObjectArrayElement(result, static_cast<jsize>(r), row);
    env->DeleteLocalRef(row);
  }
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetLibraryName(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  resolveOptionalRuntimeSymbols();
  if (!g_state.retro.get_library_name) return nullptr;
  const char* s = g_state.retro.get_library_name();
  return (s && s[0]) ? toJString(env, s) : nullptr;
}

JNIEXPORT jstring JNICALL
Java_com_omnidroid_ra_NativeRetroAchievementsBridge_nativeGetLibraryVersion(JNIEnv* env, jobject) {
  std::lock_guard<std::mutex> lock(g_state.mutex);
  resolveOptionalRuntimeSymbols();
  if (!g_state.retro.get_library_version) return nullptr;
  const char* s = g_state.retro.get_library_version();
  return (s && s[0]) ? toJString(env, s) : nullptr;
}

}
