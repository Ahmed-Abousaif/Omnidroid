#ifndef OMNIDROID_OMNICHD_H
#define OMNIDROID_OMNICHD_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

__attribute__((visibility("default")))
void* omnichd_cd_open_track_fd(int fd, uint32_t track);

__attribute__((visibility("default")))
void* omnichd_cd_open_track_path(const char* path, uint32_t track);

__attribute__((visibility("default")))
uint32_t omnichd_cd_read_sector(void* track_handle, uint32_t sector, void* buffer, uint32_t requested_bytes);

__attribute__((visibility("default")))
void omnichd_cd_close_track(void* track_handle);

__attribute__((visibility("default")))
uint32_t omnichd_cd_first_track_sector(void* track_handle);

#ifdef __cplusplus
}
#endif

#endif
