#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>

#include "sz_extract.h"

#define SZ_MAX_SLOTS 256u

typedef struct {
    SzArchive *archive;
    uint32_t generation;
    uint32_t active_ops;
    bool in_use;
    bool closing;
} SzSlot;

static pthread_mutex_t g_table_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_table_cond = PTHREAD_COND_INITIALIZER;
static SzSlot g_slots[SZ_MAX_SLOTS];

static jlong make_handle(uint32_t idx, uint32_t gen) {
    /* Keep bit 63 clear so valid handles are always strictly > 0. */
    uint64_t high = (uint64_t)(gen & 0x7FFFFFFFu) << 32u;
    uint64_t low = (uint64_t)(idx + 1u);
    return (jlong)(high | low);
}

static SzSlot *find_slot_locked(jlong handle) {
    if (handle <= 0) {
        return NULL;
    }
    uint64_t u = (uint64_t)handle;
    uint32_t idx_plus_one = (uint32_t)(u & 0xFFFFFFFFu);
    uint32_t gen = (uint32_t)(u >> 32u);
    if (idx_plus_one == 0 || idx_plus_one > SZ_MAX_SLOTS) {
        return NULL;
    }
    SzSlot *slot = &g_slots[idx_plus_one - 1u];
    if (!slot->in_use || slot->generation != gen) {
        return NULL;
    }
    return slot;
}

static SzArchive *acquire_archive(jlong handle) {
    pthread_mutex_lock(&g_table_lock);
    SzSlot *slot = find_slot_locked(handle);
    if (slot == NULL || slot->closing || slot->archive == NULL) {
        pthread_mutex_unlock(&g_table_lock);
        return NULL;
    }
    slot->active_ops++;
    SzArchive *archive = slot->archive;
    pthread_mutex_unlock(&g_table_lock);
    return archive;
}

static void release_archive(jlong handle) {
    pthread_mutex_lock(&g_table_lock);
    SzSlot *slot = find_slot_locked(handle);
    if (slot != NULL && slot->active_ops > 0u) {
        slot->active_ops--;
        if (slot->active_ops == 0u && slot->closing) {
            pthread_cond_broadcast(&g_table_cond);
        }
    }
    pthread_mutex_unlock(&g_table_lock);
}

JNIEXPORT jlong JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_open(JNIEnv *env, jclass clazz, jint archive_fd) {
    (void)env;
    (void)clazz;

    SzArchive *archive = NULL;
    int err = sz_open_fd((int)archive_fd, &archive);
    if (err != SZ_EXT_OK || archive == NULL) {
        return (jlong)(err < 0 ? err : SZ_EXT_ERR_IO);
    }

    pthread_mutex_lock(&g_table_lock);
    uint32_t free_idx = SZ_MAX_SLOTS;
    for (uint32_t i = 0; i < SZ_MAX_SLOTS; i++) {
        if (!g_slots[i].in_use) {
            free_idx = i;
            break;
        }
    }
    if (free_idx >= SZ_MAX_SLOTS) {
        pthread_mutex_unlock(&g_table_lock);
        sz_close(archive);
        return (jlong)SZ_EXT_ERR_MEM_LIMIT;
    }
    SzSlot *slot = &g_slots[free_idx];
    slot->generation = (slot->generation + 1u) & 0x7FFFFFFFu;
    slot->archive = archive;
    slot->active_ops = 0;
    slot->in_use = true;
    slot->closing = false;
    jlong handle = make_handle(free_idx, slot->generation);
    pthread_mutex_unlock(&g_table_lock);
    return handle;
}

JNIEXPORT jobjectArray JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_entryNames(JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    jclass str_cls = (*env)->FindClass(env, "java/lang/String");
    if (str_cls == NULL) {
        return NULL;
    }

    SzArchive *archive = acquire_archive(handle);
    if (archive == NULL) {
        return (*env)->NewObjectArray(env, 0, str_cls, NULL);
    }

    uint32_t count = sz_entry_count(archive);
    jobjectArray result = (*env)->NewObjectArray(env, (jsize)count, str_cls, NULL);
    if (result == NULL) {
        release_archive(handle);
        return NULL;
    }

    static const jchar empty_jchar = 0;
    uint16_t stack_buf[512];
    uint16_t *heap_buf = NULL;
    uint32_t heap_cap = 0;

    for (uint32_t i = 0; i < count; i++) {
        uint32_t len_with_nul = 0;
        if (sz_entry_name_utf16(archive, i, NULL, 0, &len_with_nul) != SZ_EXT_OK || len_with_nul == 0) {
            jstring empty = (*env)->NewString(env, &empty_jchar, 0);
            if (empty == NULL) {
                goto oom;
            }
            (*env)->SetObjectArrayElement(env, result, (jsize)i, empty);
            (*env)->DeleteLocalRef(env, empty);
            continue;
        }

        uint16_t *use_buf = stack_buf;
        uint32_t use_cap = (uint32_t)(sizeof(stack_buf) / sizeof(stack_buf[0]));
        if (len_with_nul > use_cap) {
            if (len_with_nul > heap_cap) {
                uint16_t *grown = (uint16_t *)realloc(heap_buf, (size_t)len_with_nul * sizeof(uint16_t));
                if (grown == NULL) {
                    goto oom;
                }
                heap_buf = grown;
                heap_cap = len_with_nul;
            }
            use_buf = heap_buf;
            use_cap = heap_cap;
        }

        if (sz_entry_name_utf16(archive, i, use_buf, use_cap, &len_with_nul) != SZ_EXT_OK) {
            jstring empty = (*env)->NewString(env, &empty_jchar, 0);
            if (empty == NULL) {
                goto oom;
            }
            (*env)->SetObjectArrayElement(env, result, (jsize)i, empty);
            (*env)->DeleteLocalRef(env, empty);
            continue;
        }

        jsize char_len = (len_with_nul > 0u) ? (jsize)(len_with_nul - 1u) : 0;
        jstring s = (*env)->NewString(env, (const jchar *)use_buf, char_len);
        if (s == NULL) {
            goto oom;
        }
        (*env)->SetObjectArrayElement(env, result, (jsize)i, s);
        (*env)->DeleteLocalRef(env, s);
    }

    free(heap_buf);
    release_archive(handle);
    return result;

oom:
    free(heap_buf);
    release_archive(handle);
    return NULL;
}

JNIEXPORT jlongArray JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_entryInfo(JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    SzArchive *archive = acquire_archive(handle);
    if (archive == NULL) {
        return (*env)->NewLongArray(env, 0);
    }

    uint32_t count = sz_entry_count(archive);
    jsize len = (jsize)(count * 2u);
    jlongArray result = (*env)->NewLongArray(env, len);
    if (result == NULL) {
        release_archive(handle);
        return NULL;
    }

    if (count > 0) {
        jlong *pairs = (jlong *)malloc((size_t)len * sizeof(jlong));
        if (pairs == NULL) {
            release_archive(handle);
            return (*env)->NewLongArray(env, 0);
        }
        for (uint32_t i = 0; i < count; i++) {
            pairs[(size_t)i * 2u] = (jlong)sz_entry_size(archive, i);
            pairs[(size_t)i * 2u + 1u] = (jlong)sz_entry_flags_and_crc(archive, i);
        }
        (*env)->SetLongArrayRegion(env, result, 0, len, pairs);
        free(pairs);
    }

    release_archive(handle);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_extract(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jint index,
    jint out_fd,
    jlong max_memory
) {
    (void)env;
    (void)clazz;
    if (index < 0 || out_fd < 0 || max_memory <= 0) {
        return (jint)SZ_EXT_ERR_INVALID_ARG;
    }

    SzArchive *archive = acquire_archive(handle);
    if (archive == NULL) {
        return (jint)SZ_EXT_ERR_INVALID_ARG;
    }

    int dup_out = dup((int)out_fd);
    if (dup_out < 0) {
        release_archive(handle);
        return (jint)SZ_EXT_ERR_IO;
    }

    int rc = sz_extract_to_fd(archive, (uint32_t)index, dup_out, (uint64_t)max_memory);
    close(dup_out);
    release_archive(handle);
    return (jint)rc;
}

JNIEXPORT jlong JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_progress(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    SzArchive *archive = acquire_archive(handle);
    if (archive == NULL) {
        return 0;
    }
    uint64_t bytes = sz_progress(archive);
    release_archive(handle);
    return (jlong)bytes;
}

JNIEXPORT void JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_cancel(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    pthread_mutex_lock(&g_table_lock);
    SzSlot *slot = find_slot_locked(handle);
    if (slot != NULL && slot->archive != NULL) {
        sz_cancel(slot->archive);
    }
    pthread_mutex_unlock(&g_table_lock);
}

JNIEXPORT void JNICALL
Java_com_omnidroid_sevenzip_SevenZipNative_close(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    pthread_mutex_lock(&g_table_lock);
    SzSlot *slot = find_slot_locked(handle);
    if (slot == NULL || slot->closing) {
        pthread_mutex_unlock(&g_table_lock);
        return;
    }
    slot->closing = true;
    if (slot->archive != NULL) {
        sz_cancel(slot->archive);
    }
    while (slot->active_ops > 0u) {
        pthread_cond_wait(&g_table_cond, &g_table_lock);
    }
    SzArchive *archive = slot->archive;
    slot->archive = NULL;
    slot->in_use = false;
    slot->closing = false;
    pthread_mutex_unlock(&g_table_lock);

    sz_close(archive);
}
