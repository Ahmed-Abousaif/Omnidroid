#include <android/log.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <libchdr/chd.h>

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
