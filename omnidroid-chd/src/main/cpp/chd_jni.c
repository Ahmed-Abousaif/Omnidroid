#include <android/log.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <fcntl.h>
#include <libchdr/chd.h>
#include "omnichd.h"

#define CHD_LOG_TAG "omnidroid-chd"

typedef struct {
    chd_file *file;
    FILE *stdio;
    uint8_t *hunk;
    uint32_t hunk_bytes;
    uint32_t unit_bytes;
    int32_t cached_hunk;
    int dreamcast;
} ChdHandle;

static int has_metadata(chd_file *file, uint32_t tag) {
    char buffer[8];
    uint32_t result_length = 0;
    return chd_get_metadata(file, tag, 0, buffer, sizeof(buffer), &result_length, NULL, NULL) == CHDERR_NONE;
}

JNIEXPORT jlong JNICALL
Java_com_omnidroid_chd_ChdNative_open(JNIEnv *env, jclass clazz, jint fd) {
    int duplicated = dup(fd);
    FILE *stdio;
    chd_file *file = NULL;
    const chd_header *header;
    ChdHandle *handle;
    (void) env;
    (void) clazz;
    if (duplicated < 0) {
        return 0;
    }
    stdio = fdopen(duplicated, "rb");
    if (stdio == NULL) {
        close(duplicated);
        return 0;
    }
    if (chd_open_file(stdio, CHD_OPEN_READ, NULL, &file) != CHDERR_NONE || file == NULL) {
        fclose(stdio);
        return 0;
    }
    header = chd_get_header(file);
    if (header == NULL || header->hunkbytes == 0 || header->hunkbytes > 16u * 1024u * 1024u ||
        header->unitbytes == 0 || header->hunkbytes % header->unitbytes != 0) {
        chd_close(file);
        fclose(stdio);
        return 0;
    }
    handle = calloc(1, sizeof(ChdHandle));
    if (handle == NULL) {
        chd_close(file);
        fclose(stdio);
        return 0;
    }
    handle->file = file;
    handle->stdio = stdio;
    handle->hunk_bytes = header->hunkbytes;
    handle->unit_bytes = header->unitbytes;
    handle->cached_hunk = -1;
    handle->hunk = malloc(header->hunkbytes);
    handle->dreamcast = has_metadata(file, GDROM_TRACK_METADATA_TAG);
    if (handle->hunk == NULL) {
        chd_close(file);
        fclose(stdio);
        free(handle);
        return 0;
    }
    chd_set_cache_budget(file, 8u * 1024u * 1024u);
    return (jlong) handle;
}

JNIEXPORT jboolean JNICALL
Java_com_omnidroid_chd_ChdNative_isDreamcast(JNIEnv *env, jclass clazz, jlong pointer) {
    ChdHandle *handle = (ChdHandle *) pointer;
    (void) env;
    (void) clazz;
    return handle != NULL && handle->dreamcast ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_omnidroid_chd_ChdNative_readSector(JNIEnv *env, jclass clazz, jlong pointer, jint lba) {
    ChdHandle *handle = (ChdHandle *) pointer;
    uint32_t units_per_hunk;
    uint32_t hunk;
    uint32_t index;
    uint32_t data_offset;
    const uint8_t *sector;
    jbyteArray result;
    (void) clazz;
    if (handle == NULL || lba < 0) {
        return NULL;
    }
    units_per_hunk = handle->hunk_bytes / handle->unit_bytes;
    if (units_per_hunk == 0) {
        return NULL;
    }
    hunk = ((uint32_t) lba) / units_per_hunk;
    index = ((uint32_t) lba) % units_per_hunk;
    if (handle->cached_hunk != (int32_t) hunk) {
        chd_error error = chd_read(handle->file, hunk, handle->hunk);
        if (error != CHDERR_NONE) {
            __android_log_print(ANDROID_LOG_WARN, CHD_LOG_TAG, "chd_read hunk %u failed: %d", hunk, error);
            return NULL;
        }
        handle->cached_hunk = (int32_t) hunk;
    }
    sector = handle->hunk + (index * handle->unit_bytes);
    if (handle->unit_bytes >= 2352u && sector[0] == 0x00 && sector[1] == 0xFF) {
        /* Mode 2 raw frames keep an 8-byte subheader before the 2048 user bytes. */
        data_offset = (sector[15] == 0x02) ? 24u : 16u;
    } else {
        data_offset = 0u;
    }
    if (data_offset + 2048u > handle->unit_bytes) {
        return NULL;
    }
    result = (*env)->NewByteArray(env, 2048);
    if (result == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, 2048, (const jbyte *) (sector + data_offset));
    return result;
}

JNIEXPORT void JNICALL
Java_com_omnidroid_chd_ChdNative_close(JNIEnv *env, jclass clazz, jlong pointer) {
    ChdHandle *handle = (ChdHandle *) pointer;
    (void) env;
    (void) clazz;
    if (handle == NULL) {
        return;
    }
    free(handle->hunk);
    if (handle->file != NULL) {
        chd_close(handle->file);
    }
    if (handle->stdio != NULL) {
        fclose(handle->stdio);
    }
    free(handle);
}

/* =========================================================================
 * Native CD-ROM track reader exported for RetroAchievements CHD hashing
 * ========================================================================= */

#define OMNI_CDTRACK_FIRST_DATA              ((uint32_t)-1)
#define OMNI_CDTRACK_LAST                    ((uint32_t)-2)
#define OMNI_CDTRACK_LARGEST                 ((uint32_t)-3)
#define OMNI_CDTRACK_FIRST_OF_SECOND_SESSION ((uint32_t)-4)
#define OMNI_CHD_CD_TRACK_PADDING            4u

typedef struct {
    int track_num;
    int is_data;
    uint32_t frames;
    uint32_t pregap;
    uint32_t chd_frame_offset;
    uint32_t first_sector;
    uint32_t sector_data_offset;
    uint32_t sector_data_size;
} ChdTrackMeta;

typedef struct {
    chd_file *file;
    FILE *stdio;
    uint8_t *hunk;
    uint32_t hunk_bytes;
    uint32_t unit_bytes;
    int32_t cached_hunk;
    ChdTrackMeta track;
} ChdCdTrackHandle;

static int parse_chd_track_meta(chd_file *file, uint32_t index, ChdTrackMeta *out,
                                uint32_t *running_chd_frame, uint32_t *running_lba, int *is_gdrom) {
    char buf[256];
    uint32_t res_len = 0;
    uint32_t tag = 0;
    char type[32] = {0};
    char subtype[32] = {0};
    char pgtype[32] = {0};
    char pgsub[32] = {0};
    int trk = 0, frames = 0, pad = 0, pregap = 0, postgap = 0;

    if (chd_get_metadata(file, GDROM_TRACK_METADATA_TAG, index, buf, sizeof(buf) - 1, &res_len, NULL, NULL) == CHDERR_NONE) {
        tag = GDROM_TRACK_METADATA_TAG;
        *is_gdrom = 1;
    } else if (chd_get_metadata(file, CDROM_TRACK_METADATA2_TAG, index, buf, sizeof(buf) - 1, &res_len, NULL, NULL) == CHDERR_NONE) {
        tag = CDROM_TRACK_METADATA2_TAG;
    } else if (chd_get_metadata(file, CDROM_TRACK_METADATA_TAG, index, buf, sizeof(buf) - 1, &res_len, NULL, NULL) == CHDERR_NONE) {
        tag = CDROM_TRACK_METADATA_TAG;
    } else {
        return 0;
    }

    buf[res_len < sizeof(buf) ? res_len : sizeof(buf) - 1] = '\0';
    if (tag == GDROM_TRACK_METADATA_TAG) {
        if (sscanf(buf, GDROM_TRACK_METADATA_FORMAT, &trk, type, subtype, &frames, &pad, &pregap, pgtype, pgsub, &postgap) < 4)
            return 0;
    } else if (tag == CDROM_TRACK_METADATA2_TAG) {
        if (sscanf(buf, CDROM_TRACK_METADATA2_FORMAT, &trk, type, subtype, &frames, &pregap, pgtype, pgsub, &postgap) < 4)
            return 0;
    } else {
        if (sscanf(buf, CDROM_TRACK_METADATA_FORMAT, &trk, type, subtype, &frames) < 4)
            return 0;
    }

    memset(out, 0, sizeof(*out));
    out->track_num = trk;
    out->is_data = (strcmp(type, "AUDIO") != 0);
    out->frames = (uint32_t)(frames > 0 ? frames : 0);
    out->pregap = (uint32_t)(pregap > 0 ? pregap : 0);

    uint32_t pregap_in_chd = (pgtype[0] == 'V') ? out->pregap : 0u;
    if (*is_gdrom && trk >= 3 && *running_lba < 45000u) {
        *running_lba = 45000u;
    }
    out->chd_frame_offset = *running_chd_frame + pregap_in_chd;
    out->first_sector = *running_lba;

    if (strcmp(type, "MODE1") == 0 || strcmp(type, "MODE2_FORM1") == 0) {
        out->sector_data_offset = 0u;
        out->sector_data_size = 2048u;
    } else if (strcmp(type, "MODE1_RAW") == 0) {
        out->sector_data_offset = 16u;
        out->sector_data_size = 2048u;
    } else if (strcmp(type, "MODE2_RAW") == 0 || strcmp(type, "MODE2") == 0) {
        out->sector_data_offset = 24u;
        out->sector_data_size = 2048u;
    } else if (strcmp(type, "MODE2_FORM_MIX") == 0) {
        out->sector_data_offset = 8u;
        out->sector_data_size = 2048u;
    } else {
        out->sector_data_offset = 0u;
        out->sector_data_size = 2048u;
    }

    uint32_t padded = ((out->frames + (OMNI_CHD_CD_TRACK_PADDING - 1u)) / OMNI_CHD_CD_TRACK_PADDING) * OMNI_CHD_CD_TRACK_PADDING;
    *running_chd_frame += padded;
    *running_lba += out->frames;
    return 1;
}

static void* omnichd_open_stdio(FILE *stdio, uint32_t track) {
    chd_file *file = NULL;
    const chd_header *header;
    if (!stdio) return NULL;
    if (chd_open_file(stdio, CHD_OPEN_READ, NULL, &file) != CHDERR_NONE || !file) {
        fclose(stdio);
        return NULL;
    }
    header = chd_get_header(file);
    if (!header || header->hunkbytes == 0 || header->hunkbytes > 16u * 1024u * 1024u ||
        header->unitbytes == 0 || (header->hunkbytes % header->unitbytes) != 0) {
        chd_close(file);
        fclose(stdio);
        return NULL;
    }

    ChdTrackMeta tracks[128];
    uint32_t count = 0;
    uint32_t chd_frame = 0;
    uint32_t lba = 0;
    int is_gdrom = 0;
    while (count < 128 && parse_chd_track_meta(file, count, &tracks[count], &chd_frame, &lba, &is_gdrom)) {
        count++;
    }

    int selected = -1;
    if (count == 0) {
        memset(&tracks[0], 0, sizeof(tracks[0]));
        tracks[0].track_num = 1;
        tracks[0].is_data = 1;
        tracks[0].frames = (uint32_t)header->totalhunks * (header->hunkbytes / header->unitbytes);
        tracks[0].sector_data_size = 2048u;
        count = 1;
        selected = 0;
    } else if (track == 0 || track == OMNI_CDTRACK_LARGEST) {
        uint32_t max_frames = 0;
        for (uint32_t i = 0; i < count; ++i) {
            if (tracks[i].is_data && tracks[i].frames >= max_frames) {
                max_frames = tracks[i].frames;
                selected = (int)i;
            }
        }
        if (selected < 0) selected = 0;
    } else if (track == OMNI_CDTRACK_FIRST_DATA) {
        for (uint32_t i = 0; i < count; ++i) {
            if (tracks[i].is_data) { selected = (int)i; break; }
        }
    } else if (track == OMNI_CDTRACK_LAST) {
        selected = (int)(count - 1u);
    } else if (track == OMNI_CDTRACK_FIRST_OF_SECOND_SESSION) {
        selected = (count >= 2u) ? 1 : 0;
    } else {
        for (uint32_t i = 0; i < count; ++i) {
            if ((uint32_t)tracks[i].track_num == track) { selected = (int)i; break; }
        }
    }

    if (selected < 0) {
        chd_close(file);
        fclose(stdio);
        return NULL;
    }

    ChdCdTrackHandle *h = (ChdCdTrackHandle*)calloc(1, sizeof(ChdCdTrackHandle));
    if (!h) {
        chd_close(file);
        fclose(stdio);
        return NULL;
    }
    h->file = file;
    h->stdio = stdio;
    h->hunk_bytes = header->hunkbytes;
    h->unit_bytes = header->unitbytes;
    h->cached_hunk = -1;
    h->hunk = (uint8_t*)malloc(header->hunkbytes);
    if (!h->hunk) {
        chd_close(file);
        fclose(stdio);
        free(h);
        return NULL;
    }
    h->track = tracks[selected];
    chd_set_cache_budget(file, 4u * 1024u * 1024u);
    return h;
}

void* omnichd_cd_open_track_fd(int fd, uint32_t track) {
    int dupfd = dup(fd);
    if (dupfd < 0) return NULL;
    FILE *stdio = fdopen(dupfd, "rb");
    if (!stdio) {
        close(dupfd);
        return NULL;
    }
    return omnichd_open_stdio(stdio, track);
}

void* omnichd_cd_open_track_path(const char* path, uint32_t track) {
    if (!path) return NULL;
    FILE *stdio = fopen(path, "rb");
    if (!stdio) return NULL;
    return omnichd_open_stdio(stdio, track);
}

uint32_t omnichd_cd_read_sector(void* track_handle, uint32_t sector, void* buffer, uint32_t requested_bytes) {
    ChdCdTrackHandle *h = (ChdCdTrackHandle*)track_handle;
    if (!h || !buffer || requested_bytes == 0) return 0;
    if (sector < h->track.first_sector) return 0;

    uint32_t rel_sector = sector - h->track.first_sector;
    if (rel_sector >= h->track.frames) return 0;

    uint32_t units_per_hunk = h->hunk_bytes / h->unit_bytes;
    if (units_per_hunk == 0) return 0;

    uint8_t *out = (uint8_t*)buffer;
    uint32_t total_read = 0;
    uint32_t remaining = requested_bytes;

    while (remaining > 0) {
        if (rel_sector >= h->track.frames) break;
        uint32_t chd_unit = h->track.chd_frame_offset + rel_sector;
        uint32_t hunk = chd_unit / units_per_hunk;
        uint32_t idx = chd_unit % units_per_hunk;
        if (h->cached_hunk != (int32_t)hunk) {
            if (chd_read(h->file, hunk, h->hunk) != CHDERR_NONE) break;
            h->cached_hunk = (int32_t)hunk;
        }
        const uint8_t *raw = h->hunk + idx * h->unit_bytes;
        uint32_t data_off = h->track.sector_data_offset;
        if (h->unit_bytes >= 2352u && raw[0] == 0x00 && raw[1] == 0xFF) {
            data_off = (raw[15] == 0x02) ? 24u : 16u;
        } else if (data_off + 2048u > h->unit_bytes) {
            data_off = 0u;
        }
        uint32_t avail = (data_off < h->unit_bytes) ? (h->unit_bytes - data_off) : 0u;
        if (avail > 2048u) avail = 2048u;
        uint32_t chunk = remaining < avail ? remaining : avail;
        if (chunk == 0) break;
        memcpy(out + total_read, raw + data_off, chunk);
        total_read += chunk;
        remaining -= chunk;
        rel_sector++;
    }
    return total_read;
}

void omnichd_cd_close_track(void* track_handle) {
    ChdCdTrackHandle *h = (ChdCdTrackHandle*)track_handle;
    if (!h) return;
    free(h->hunk);
    if (h->file) chd_close(h->file);
    if (h->stdio) fclose(h->stdio);
    free(h);
}

uint32_t omnichd_cd_first_track_sector(void* track_handle) {
    ChdCdTrackHandle *h = (ChdCdTrackHandle*)track_handle;
    return h ? h->track.first_sector : 0u;
}
