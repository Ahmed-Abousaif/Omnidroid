#ifndef OMNIDROID_RA_BRIDGE_ABI_H
#define OMNIDROID_RA_BRIDGE_ABI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define OMNI_RA_ABI_VERSION_MAJOR 1u
#define OMNI_RA_ABI_VERSION_MINOR 0u
#define OMNI_RA_MAKE_ABI_VERSION(major, minor) ((((uint32_t)(major)) << 16) | ((uint32_t)(minor) & 0xFFFFu))
#define OMNI_RA_ABI_MAJOR(ver) (((uint32_t)(ver)) >> 16)
#define OMNI_RA_ABI_MINOR(ver) (((uint32_t)(ver)) & 0xFFFFu)

enum {
  OMNI_RA_OK = 0,
  OMNI_RA_HTTP_CLIENT_ERROR = -1,
  OMNI_RA_HTTP_RETRYABLE_CLIENT_ERROR = -2
};

enum {
  OMNI_RA_EVENT_NONE = 0,
  OMNI_RA_EVENT_ACHIEVEMENT_TRIGGERED = 1,
  OMNI_RA_EVENT_LEADERBOARD_STARTED = 2,
  OMNI_RA_EVENT_LEADERBOARD_FAILED = 3,
  OMNI_RA_EVENT_LEADERBOARD_SUBMITTED = 4,
  OMNI_RA_EVENT_CHALLENGE_INDICATOR_SHOW = 5,
  OMNI_RA_EVENT_CHALLENGE_INDICATOR_HIDE = 6,
  OMNI_RA_EVENT_PROGRESS_INDICATOR_SHOW = 7,
  OMNI_RA_EVENT_PROGRESS_INDICATOR_HIDE = 8,
  OMNI_RA_EVENT_PROGRESS_INDICATOR_UPDATE = 9,
  OMNI_RA_EVENT_LEADERBOARD_TRACKER_SHOW = 10,
  OMNI_RA_EVENT_LEADERBOARD_TRACKER_HIDE = 11,
  OMNI_RA_EVENT_LEADERBOARD_TRACKER_UPDATE = 12,
  OMNI_RA_EVENT_LEADERBOARD_SCOREBOARD = 13,
  OMNI_RA_EVENT_RESET = 14,
  OMNI_RA_EVENT_GAME_COMPLETED = 15,
  OMNI_RA_EVENT_SERVER_ERROR = 16,
  OMNI_RA_EVENT_DISCONNECTED = 17,
  OMNI_RA_EVENT_RECONNECTED = 18,
  OMNI_RA_EVENT_SUBSET_COMPLETED = 19
};

typedef struct omni_ra_s omni_ra_t;
typedef struct omni_ra_async_s omni_ra_async_t;

typedef struct omni_ra_build_info_s {
  uint32_t struct_size;
  uint32_t abi_major;
  uint32_t abi_minor;
  const char* module_version;
  const char* rcheevos_version;
  const char* git_hash;
} omni_ra_build_info_t;

typedef struct omni_ra_http_request_s {
  uint32_t struct_size;
  uint32_t reserved;
  const char* url;
  const char* post_data;
  const char* content_type;
  const char* user_agent;
} omni_ra_http_request_t;

typedef struct omni_ra_http_response_s {
  uint32_t struct_size;
  int32_t http_status;
  const void* body;
  uint32_t body_len;
} omni_ra_http_response_t;

typedef struct omni_ra_file_callbacks_s {
  uint32_t struct_size;
  uint32_t reserved;
  void* (*open)(void* userdata, const char* path_utf8);
  void (*seek)(void* userdata, void* file_handle, int64_t offset, int32_t origin);
  int64_t (*tell)(void* userdata, void* file_handle);
  uint32_t (*read)(void* userdata, void* file_handle, void* buffer, uint32_t requested_bytes);
  void (*close)(void* userdata, void* file_handle);
} omni_ra_file_callbacks_t;

typedef struct omni_ra_cdreader_callbacks_s {
  uint32_t struct_size;
  uint32_t reserved;
  void* (*open_track)(void* userdata, const char* path_utf8, uint32_t track);
  uint32_t (*read_sector)(void* userdata, void* track_handle, uint32_t sector, void* buffer, uint32_t requested_bytes);
  void (*close_track)(void* userdata, void* track_handle);
  uint32_t (*first_track_sector)(void* userdata, void* track_handle);
} omni_ra_cdreader_callbacks_t;

typedef struct omni_ra_memory_descriptor_s {
  uint32_t struct_size;
  uint32_t reserved;
  uint64_t flags;
  void* ptr;
  uint64_t offset;
  uint64_t start;
  uint64_t select;
  uint64_t disconnect;
  uint64_t len;
  const char* addrspace;
} omni_ra_memory_descriptor_t;

typedef struct omni_ra_core_memory_region_s {
  uint32_t struct_size;
  uint32_t memory_type;
  void* data;
  uint64_t size;
} omni_ra_core_memory_region_t;

typedef struct omni_ra_memory_map_s {
  uint32_t struct_size;
  uint32_t console_id;
  const omni_ra_memory_descriptor_t* descriptors;
  uint32_t num_descriptors;
  uint32_t num_core_regions;
  const omni_ra_core_memory_region_t* core_regions;
} omni_ra_memory_map_t;

typedef struct omni_ra_achievement_s {
  uint32_t struct_size;
  uint32_t id;
  uint32_t points;
  int64_t unlock_time;
  float measured_percent;
  float rarity;
  float rarity_hardcore;
  uint8_t state;
  uint8_t category;
  uint8_t bucket;
  uint8_t unlocked;
  uint8_t type;
  uint8_t reserved[3];
  const char* title;
  const char* description;
  const char* badge_name;
  const char* measured_progress;
  const char* badge_url;
  const char* badge_locked_url;
} omni_ra_achievement_t;

typedef struct omni_ra_achievement_bucket_s {
  uint32_t struct_size;
  uint32_t subset_id;
  uint8_t bucket_type;
  uint8_t reserved[3];
  uint32_t num_achievements;
  const char* label;
  const omni_ra_achievement_t* const* achievements;
} omni_ra_achievement_bucket_t;

typedef struct omni_ra_achievement_list_s {
  uint32_t struct_size;
  uint32_t num_buckets;
  const omni_ra_achievement_bucket_t* buckets;
} omni_ra_achievement_list_t;

typedef struct omni_ra_leaderboard_s {
  uint32_t struct_size;
  uint32_t id;
  uint8_t state;
  uint8_t format;
  uint8_t lower_is_better;
  uint8_t reserved;
  const char* title;
  const char* description;
  const char* tracker_value;
} omni_ra_leaderboard_t;

typedef struct omni_ra_leaderboard_bucket_s {
  uint32_t struct_size;
  uint32_t subset_id;
  uint8_t bucket_type;
  uint8_t reserved[3];
  uint32_t num_leaderboards;
  const char* label;
  const omni_ra_leaderboard_t* const* leaderboards;
} omni_ra_leaderboard_bucket_t;

typedef struct omni_ra_leaderboard_list_s {
  uint32_t struct_size;
  uint32_t num_buckets;
  const omni_ra_leaderboard_bucket_t* buckets;
} omni_ra_leaderboard_list_t;

typedef struct omni_ra_leaderboard_tracker_s {
  uint32_t struct_size;
  uint32_t id;
  const char* display;
} omni_ra_leaderboard_tracker_t;

typedef struct omni_ra_leaderboard_scoreboard_entry_s {
  uint32_t struct_size;
  uint32_t rank;
  const char* username;
  const char* score;
} omni_ra_leaderboard_scoreboard_entry_t;

typedef struct omni_ra_leaderboard_scoreboard_s {
  uint32_t struct_size;
  uint32_t leaderboard_id;
  uint32_t new_rank;
  uint32_t num_entries;
  uint32_t num_top_entries;
  const char* submitted_score;
  const char* best_score;
  const omni_ra_leaderboard_scoreboard_entry_t* top_entries;
} omni_ra_leaderboard_scoreboard_t;

typedef struct omni_ra_subset_s {
  uint32_t struct_size;
  uint32_t id;
  uint32_t num_achievements;
  uint32_t num_leaderboards;
  const char* title;
  const char* badge_name;
  const char* badge_url;
} omni_ra_subset_t;

typedef struct omni_ra_server_error_s {
  uint32_t struct_size;
  int32_t result;
  uint32_t related_id;
  const char* error_message;
  const char* api;
} omni_ra_server_error_t;

typedef struct omni_ra_event_s {
  uint32_t struct_size;
  uint32_t type;
  const omni_ra_achievement_t* achievement;
  const omni_ra_leaderboard_t* leaderboard;
  const omni_ra_leaderboard_tracker_t* leaderboard_tracker;
  const omni_ra_leaderboard_scoreboard_t* leaderboard_scoreboard;
  const omni_ra_server_error_t* server_error;
  const omni_ra_subset_t* subset;
} omni_ra_event_t;

typedef struct omni_ra_user_info_s {
  uint32_t struct_size;
  uint32_t score;
  uint32_t score_softcore;
  uint32_t num_unread_messages;
  int64_t avatar_last_updated;
  char username[64];
  char display_name[64];
  char token[64];
  char avatar_url[256];
} omni_ra_user_info_t;

typedef struct omni_ra_game_info_s {
  uint32_t struct_size;
  uint32_t id;
  uint32_t console_id;
  char title[256];
  char hash[33];
  char badge_name[32];
  char badge_url[256];
} omni_ra_game_info_t;

typedef struct omni_ra_user_game_summary_s {
  uint32_t struct_size;
  uint32_t num_core_achievements;
  uint32_t num_unofficial_achievements;
  uint32_t num_unlocked_achievements;
  uint32_t num_unsupported_achievements;
  uint32_t points_core;
  uint32_t points_unlocked;
  int64_t beaten_time;
  int64_t completed_time;
} omni_ra_user_game_summary_t;

typedef struct omni_ra_host_s {
  uint32_t struct_size;
  uint32_t host_abi_version;
  void* userdata;
  uint64_t (*get_time_ms)(void* userdata);
  void (*http_request)(void* userdata, uint64_t request_id, const omni_ra_http_request_t* req);
  void (*on_event)(void* userdata, const omni_ra_event_t* event);
  uint32_t (*read_memory)(void* userdata, uint32_t address, uint8_t* buffer, uint32_t num_bytes);
  void (*log_message)(void* userdata, int32_t level, const char* message);
  const omni_ra_file_callbacks_t* file_callbacks;
  const omni_ra_cdreader_callbacks_t* cdreader_callbacks;
} omni_ra_host_t;

typedef void (*omni_ra_async_cb_t)(void* cb_userdata, int32_t result, const char* error_message);

/* Libretro / LibretroDroid binary-compatible structs resolved at runtime via dlsym */
enum {
  OMNI_RETRO_MEMORY_SAVE_RAM = 0,
  OMNI_RETRO_MEMORY_RTC = 1,
  OMNI_RETRO_MEMORY_SYSTEM_RAM = 2,
  OMNI_RETRO_MEMORY_VIDEO_RAM = 3
};

struct omni_retro_memory_descriptor {
  uint64_t flags;
  void* ptr;
  size_t offset;
  size_t start;
  size_t select;
  size_t disconnect;
  size_t len;
  const char* addrspace;
};

typedef struct LibretroDroidNativeHooks {
  uint32_t struct_size;
  uint32_t reserved;
  void* userdata;
  void (*on_after_frame)(void* userdata);
  void (*on_after_reset)(void* userdata);
  void (*on_memory_map_changed)(void* userdata);
  void (*on_before_unload)(void* userdata);
} LibretroDroidNativeHooks;

#ifdef __cplusplus
}
#endif

#endif
