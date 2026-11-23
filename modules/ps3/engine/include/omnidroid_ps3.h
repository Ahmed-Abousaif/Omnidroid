#ifndef OMNIDROID_PS3_H
#define OMNIDROID_PS3_H

/* Omnidroid's host contract. No STL objects, JVM package names or frontend UI
 * types cross this boundary. Strings are UTF-8; callbacks must copy their
 * arguments before returning. File descriptors remain owned by the caller. */
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define OMNI_PS3_ABI_VERSION 1u

typedef enum omni_ps3_result {
    OMNI_PS3_OK = 0,
    OMNI_PS3_INVALID_ARGUMENT = 1,
    OMNI_PS3_BUSY = 2,
    OMNI_PS3_NOT_FOUND = 3,
    OMNI_PS3_INCOMPATIBLE = 4,
    OMNI_PS3_FAILED = 5
} omni_ps3_result;

typedef enum omni_ps3_dialog_kind {
    OMNI_PS3_MESSAGE = 1,
    OMNI_PS3_KEYBOARD = 2,
    OMNI_PS3_SAVEDATA = 3,
    OMNI_PS3_DIALOG_UPDATE = 4,
    OMNI_PS3_DIALOG_CLOSE = 5
} omni_ps3_dialog_kind;

typedef struct omni_ps3_file_info {
    int32_t directory;
    uint64_t size;
    int64_t modified_seconds;
} omni_ps3_file_info;
typedef void (*omni_ps3_directory_entry)(void *sink, const char *name,
                                        const omni_ps3_file_info *info);

typedef struct omni_ps3_callbacks {
    uint32_t size;
    void *userdata;
    /* These can arrive on engine threads; the host dispatches UI work itself. */
    void (*log)(void *userdata, int32_t severity, const char *message);
    void (*progress)(void *userdata, uint64_t operation, int64_t value,
                     int64_t maximum, const char *message);
    void (*installation_finished)(void *userdata, uint64_t operation,
                                  omni_ps3_result result, const char *message);
    void (*firmware_version)(void *userdata, const char *version);
    /* JSON describes guest-system dialogs, never emulator menus/settings. */
    void (*dialog)(void *userdata, uint64_t token, omni_ps3_dialog_kind kind,
                   const char *json);
    /* Optional virtual-storage provider. Paths are relative to the host's
     * registered tree keys. open_file transfers ownership of its returned fd. */
    int32_t (*stat_file)(void*, const char*, omni_ps3_file_info*);
    int32_t (*list_directory)(void*, const char*, void *sink, omni_ps3_directory_entry);
    int32_t (*open_file)(void*, const char*, const char *mode, int32_t create);
    int32_t (*create_directory)(void*, const char*);
    int32_t (*remove_file)(void*, const char*);
    int32_t (*rename_file)(void*, const char*, const char*);
} omni_ps3_callbacks;

typedef struct omni_ps3_performance {
    uint64_t frames, period_ns, work_ns, last_period_ns, last_work_ns;
    uint64_t submitted, errors, sessions_created, sessions_closed;
    int32_t rsx_tid, hint_tid;
    uint32_t options, active, display_millihz;
} omni_ps3_performance;

typedef struct omni_ps3_api {
    uint32_t size;
    uint32_t version;
    const char *profile; /* Exactly "omnidroid-engine-only". */
    omni_ps3_result (*initialize)(const char *data_path, const char *cache_path,
                                  const omni_ps3_callbacks *callbacks);
    omni_ps3_result (*boot)(const char *game_path);
    int32_t (*state)(void);
    omni_ps3_result (*pause)(void);
    omni_ps3_result (*resume)(void);
    omni_ps3_result (*stop)(void);
    /* Android ANativeWindow*, acquired by the engine before returning. */
    void (*surface)(void *native_window, uint32_t width, uint32_t height);
    void (*pad)(uint32_t port, uint8_t digital1, uint8_t digital2,
                uint8_t lx, uint8_t ly, uint8_t rx, uint8_t ry);
    omni_ps3_result (*setting)(const char *path, const char *json_value);
    /* Returns required buffer size INCLUDING NUL; buffer may be NULL. */
    size_t (*settings)(const char *path, char *buffer, size_t capacity);
    size_t (*probe)(const char *path, const char *icon_path, char *buffer,
                    size_t capacity);
    omni_ps3_result (*save_slot)(uint32_t slot);
    omni_ps3_result (*load_slot)(uint32_t slot);
    omni_ps3_result (*install)(uint32_t kind, int32_t fd, uint64_t operation);
    /* status is the dialog-specific guest result; text is keyboard input. */
    omni_ps3_result (*dialog_result)(uint64_t token, int32_t status,
                                    const char *text);
    int32_t (*state_operation_failed)(void);
    void (*display_rate)(double hz);
    void (*performance_stats)(omni_ps3_performance* result);
    void (*performance_options)(uint32_t options); /* Debug A/B tests; production uses 3. */
} omni_ps3_api;

/* Internal engine contract, linked directly to JNI. A mismatched ABI returns NULL. */
const omni_ps3_api *omni_ps3_get_api(uint32_t version);

#ifdef __cplusplus
}
#endif
#endif
