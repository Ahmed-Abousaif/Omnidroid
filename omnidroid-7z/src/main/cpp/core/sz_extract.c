#ifndef _WIN32
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#endif

#include "sz_extract.h"

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <io.h>
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <pthread.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#endif

#include "7z.h"
#include "7zCrc.h"
#include "CpuArch.h"
#include "Lzma2Dec.h"
#include "LzmaDec.h"

#define SZ_LOOKAHEAD_BUF_SIZE   (512u * 1024u)
#define SZ_MAX_HEADER_ALLOC     (64u * 1024u * 1024u)
#define SZ_METHOD_LZMA2         0x21u
#define SZ_METHOD_LZMA          0x030101u
#define SZ_METHOD_AES           0x06F10701u

/* ---------- Platform I/O & Locking Helpers ---------- */

#ifdef _WIN32
typedef CRITICAL_SECTION sz_mutex_t;
static void sz_mutex_init(sz_mutex_t *m) { InitializeCriticalSection(m); }
static void sz_mutex_destroy(sz_mutex_t *m) { DeleteCriticalSection(m); }
static int sz_mutex_trylock(sz_mutex_t *m) { return TryEnterCriticalSection(m) ? 0 : EBUSY; }
static void sz_mutex_lock(sz_mutex_t *m) { EnterCriticalSection(m); }
static void sz_mutex_unlock(sz_mutex_t *m) { LeaveCriticalSection(m); }

static int sz_dup_fd(int fd) { return _dup(fd); }
static void sz_close_fd(int fd) { if (fd >= 0) _close(fd); }

static int sz_probe_seekable_size(int fd, uint64_t *out_size) {
    __int64 end = _lseeki64(fd, 0, SEEK_END);
    if (end < 0) {
        return (errno == EINVAL || errno == EBADF) ? SZ_EXT_ERR_NOT_SEEKABLE : SZ_EXT_ERR_IO;
    }
    if (_lseeki64(fd, 0, SEEK_SET) < 0) {
        return SZ_EXT_ERR_IO;
    }
    *out_size = (uint64_t)end;
    return SZ_EXT_OK;
}

static int sz_pread_exact_step(int fd, uint8_t *buf, size_t count, uint64_t offset, size_t *out_read) {
    if (_lseeki64(fd, (__int64)offset, SEEK_SET) < 0) {
        return SZ_EXT_ERR_IO;
    }
    unsigned int to_read = (count > (size_t)INT_MAX) ? (unsigned int)INT_MAX : (unsigned int)count;
    int r = _read(fd, buf, to_read);
    if (r < 0) {
        return SZ_EXT_ERR_IO;
    }
    *out_read = (size_t)r;
    return SZ_EXT_OK;
}

static int sz_preallocate_fd(int fd, uint64_t size) {
    (void)fd;
    (void)size;
    return SZ_EXT_OK;
}

static int sz_rewind_fd(int fd) {
    (void)_lseeki64(fd, 0, SEEK_SET);
    return SZ_EXT_OK;
}

static int sz_truncate_zero_fd(int fd) {
    (void)_chsize_s(fd, 0);
    return SZ_EXT_OK;
}

static int sz_write_all_fd(int fd, const uint8_t *buf, size_t len) {
    size_t off = 0;
    while (off < len) {
        unsigned int chunk = ((len - off) > (size_t)INT_MAX) ? (unsigned int)INT_MAX : (unsigned int)(len - off);
        int w = _write(fd, buf + off, chunk);
        if (w < 0) {
            return (errno == ENOSPC) ? SZ_EXT_ERR_DISK_FULL : SZ_EXT_ERR_IO;
        }
        if (w == 0) {
            return SZ_EXT_ERR_IO;
        }
        off += (size_t)w;
    }
    return SZ_EXT_OK;
}

static int sz_fsync_fd(int fd) {
    if (_commit(fd) < 0 && errno != EINVAL && errno != EBADF) {
        return (errno == ENOSPC) ? SZ_EXT_ERR_DISK_FULL : SZ_EXT_ERR_IO;
    }
    return SZ_EXT_OK;
}

static void sz_ensure_crc_table(void) {
    static volatile LONG crc_once = 0;
    if (InterlockedCompareExchange(&crc_once, 1, 0) == 0) {
        CrcGenerateTable();
        InterlockedExchange(&crc_once, 2);
    } else {
        while (InterlockedCompareExchange(&crc_once, 2, 2) != 2) {
            Sleep(0);
        }
    }
}
#else
typedef pthread_mutex_t sz_mutex_t;
static void sz_mutex_init(sz_mutex_t *m) { pthread_mutex_init(m, NULL); }
static void sz_mutex_destroy(sz_mutex_t *m) { pthread_mutex_destroy(m); }
static int sz_mutex_trylock(sz_mutex_t *m) { return pthread_mutex_trylock(m); }
static void sz_mutex_lock(sz_mutex_t *m) { pthread_mutex_lock(m); }
static void sz_mutex_unlock(sz_mutex_t *m) { pthread_mutex_unlock(m); }

static int sz_dup_fd(int fd) { return dup(fd); }
static void sz_close_fd(int fd) { if (fd >= 0) close(fd); }

static int sz_probe_seekable_size(int fd, uint64_t *out_size) {
    off64_t end = lseek64(fd, 0, SEEK_END);
    if (end < 0) {
        return (errno == ESPIPE) ? SZ_EXT_ERR_NOT_SEEKABLE : SZ_EXT_ERR_IO;
    }
    /* Probe pread64 directly as well in case lseek64 succeeds on a custom wrapper fd. */
    uint8_t dummy = 0;
    ssize_t pr;
    do {
        pr = pread64(fd, &dummy, 1, 0);
    } while (pr < 0 && errno == EINTR);
    if (pr < 0) {
        return (errno == ESPIPE) ? SZ_EXT_ERR_NOT_SEEKABLE : SZ_EXT_ERR_IO;
    }
    *out_size = (uint64_t)end;
    return SZ_EXT_OK;
}

static int sz_pread_exact_step(int fd, uint8_t *buf, size_t count, uint64_t offset, size_t *out_read) {
    size_t max_chunk = (size_t)0x7ffff000u;
    size_t req = (count > max_chunk) ? max_chunk : count;
    ssize_t r;
    do {
        r = pread64(fd, buf, req, (off64_t)offset);
    } while (r < 0 && errno == EINTR);
    if (r < 0) {
        return (errno == ESPIPE) ? SZ_EXT_ERR_NOT_SEEKABLE : SZ_EXT_ERR_IO;
    }
    *out_read = (size_t)r;
    return SZ_EXT_OK;
}

static int sz_preallocate_fd(int fd, uint64_t size) {
    if (size == 0) {
        return SZ_EXT_OK;
    }
    if (size > (uint64_t)INT64_MAX) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
#if defined(__linux__) || defined(__ANDROID__)
    int rc;
    do {
        rc = fallocate64(fd, 0, 0, (off64_t)size);
    } while (rc < 0 && errno == EINTR);
    if (rc == 0) {
        return SZ_EXT_OK;
    }
    if (errno == ENOSPC || errno == EDQUOT) {
        return SZ_EXT_ERR_DISK_FULL;
    }
    if (errno == EOPNOTSUPP || errno == ENOSYS || errno == EINVAL || errno == ENODEV || errno == ESPIPE) {
        return SZ_EXT_OK;
    }
    return SZ_EXT_ERR_IO;
#else
    (void)fd;
    return SZ_EXT_OK;
#endif
}

static int sz_rewind_fd(int fd) {
    if (lseek64(fd, 0, SEEK_SET) < 0 && errno != ESPIPE) {
        return SZ_EXT_ERR_IO;
    }
    return SZ_EXT_OK;
}

static int sz_truncate_zero_fd(int fd) {
    int rc;
    do {
        rc = ftruncate64(fd, 0);
    } while (rc < 0 && errno == EINTR);
    if (rc < 0 && errno != EINVAL && errno != ESPIPE) {
        return SZ_EXT_ERR_IO;
    }
    return SZ_EXT_OK;
}

static int sz_write_all_fd(int fd, const uint8_t *buf, size_t len) {
    size_t off = 0;
    while (off < len) {
        size_t rem = len - off;
        size_t chunk = (rem > (size_t)0x7ffff000u) ? (size_t)0x7ffff000u : rem;
        ssize_t w = write(fd, buf + off, chunk);
        if (w < 0) {
            if (errno == EINTR) {
                continue;
            }
            if (errno == ENOSPC || errno == EDQUOT) {
                return SZ_EXT_ERR_DISK_FULL;
            }
            return SZ_EXT_ERR_IO;
        }
        if (w == 0) {
            return SZ_EXT_ERR_IO;
        }
        off += (size_t)w;
    }
    return SZ_EXT_OK;
}

static int sz_fsync_fd(int fd) {
    int rc;
    do {
        rc = fsync(fd);
    } while (rc < 0 && errno == EINTR);
    if (rc < 0 && errno != EINVAL && errno != EROFS) {
        if (errno == ENOSPC || errno == EDQUOT) {
            return SZ_EXT_ERR_DISK_FULL;
        }
        return SZ_EXT_ERR_IO;
    }
    return SZ_EXT_OK;
}

static pthread_once_t g_crc_once = PTHREAD_ONCE_INIT;
static void sz_ensure_crc_table(void) {
    pthread_once(&g_crc_once, CrcGenerateTable);
}
#endif

/* ---------- Bounded Allocators ---------- */

static void *sz_std_alloc(ISzAllocPtr p, size_t size) {
    (void)p;
    if (size == 0) {
        return NULL;
    }
    return malloc(size);
}

static void sz_std_free(ISzAllocPtr p, void *address) {
    (void)p;
    free(address);
}

static const ISzAlloc g_std_alloc = { sz_std_alloc, sz_std_free };

typedef struct {
    ISzAlloc vt;
    size_t used;
    size_t limit;
} SzCappedAlloc;

static void *sz_capped_alloc(ISzAllocPtr pp, size_t size) {
    Z7_CONTAINER_FROM_VTBL_TO_DECL_VAR_pp_vt_p(SzCappedAlloc)
    if (size == 0) {
        return NULL;
    }
    if (size > p->limit) {
        return NULL;
    }
    return malloc(size);
}

static void sz_capped_free(ISzAllocPtr pp, void *address) {
    (void)pp;
    free(address);
}

/* ---------- Seekable Input Streams ---------- */

typedef struct {
    ISeekInStream vt;
    int fd;
    const uint8_t *mem;
    uint64_t pos;
    uint64_t size;
    int last_err;
} SzSourceStream;

static SRes sz_source_read(ISeekInStreamPtr pp, void *buf, size_t *size) {
    Z7_CONTAINER_FROM_VTBL_TO_DECL_VAR_pp_vt_p(SzSourceStream)
    size_t wanted = *size;
    *size = 0;
    if (wanted == 0) {
        return SZ_OK;
    }
    if (p->pos >= p->size) {
        return SZ_OK;
    }
    uint64_t avail = p->size - p->pos;
    if ((uint64_t)wanted > avail) {
        wanted = (size_t)avail;
    }
    if (p->mem != NULL) {
        memcpy(buf, p->mem + p->pos, wanted);
        p->pos += (uint64_t)wanted;
        *size = wanted;
        return SZ_OK;
    }
    size_t got = 0;
    int r = sz_pread_exact_step(p->fd, (uint8_t *)buf, wanted, p->pos, &got);
    if (r != SZ_EXT_OK) {
        p->last_err = r;
        return SZ_ERROR_READ;
    }
    p->pos += (uint64_t)got;
    *size = got;
    return SZ_OK;
}

static SRes sz_source_seek(ISeekInStreamPtr pp, Int64 *pos, ESzSeek origin) {
    Z7_CONTAINER_FROM_VTBL_TO_DECL_VAR_pp_vt_p(SzSourceStream)
    Int64 base = 0;
    switch (origin) {
        case SZ_SEEK_SET:
            base = 0;
            break;
        case SZ_SEEK_CUR:
            if (p->pos > (uint64_t)INT64_MAX) {
                return SZ_ERROR_FAIL;
            }
            base = (Int64)p->pos;
            break;
        case SZ_SEEK_END:
            if (p->size > (uint64_t)INT64_MAX) {
                return SZ_ERROR_FAIL;
            }
            base = (Int64)p->size;
            break;
        default:
            return SZ_ERROR_PARAM;
    }
    Int64 delta = *pos;
    if ((delta > 0 && base > INT64_MAX - delta) || (delta < 0 && base < -delta)) {
        return SZ_ERROR_FAIL;
    }
    Int64 target = base + delta;
    if (target < 0) {
        return SZ_ERROR_FAIL;
    }
    p->pos = (uint64_t)target;
    *pos = target;
    return SZ_OK;
}

/* ---------- Archive Struct ---------- */

struct SzArchive {
    CSzArEx db;
    SzSourceStream source;
    CLookToRead2 look_stream;
    uint8_t *look_buf;
    size_t look_buf_size;
    _Atomic uint64_t progress_bytes;
    _Atomic int cancelled;
    sz_mutex_t extract_mutex;
};

/* ---------- Header / Folder Inspection Helpers ---------- */

static bool read_var_u64(CSzData *sd, uint64_t *val) {
    if (sd->Size == 0) {
        return false;
    }
    unsigned first = *sd->Data++;
    sd->Size--;
    if ((first & 0x80u) == 0) {
        *val = first;
        return true;
    }
    unsigned mask = 0x40u;
    unsigned extra = 1;
    while ((first & mask) != 0) {
        mask >>= 1;
        extra++;
    }
    if (extra > sd->Size) {
        return false;
    }
    uint64_t low = 0;
    for (unsigned i = 0; i < extra; i++) {
        low |= ((uint64_t)sd->Data[i]) << (8u * i);
    }
    sd->Data += extra;
    sd->Size -= extra;
    uint64_t high = (uint64_t)(first & (mask - 1u));
    if (extra < 8) {
        low |= high << (8u * extra);
    }
    *val = low;
    return true;
}

static bool skip_header_data(CSzData *sd) {
    uint64_t sz = 0;
    if (!read_var_u64(sd, &sz) || sz > sd->Size) {
        return false;
    }
    sd->Data += (size_t)sz;
    sd->Size -= (size_t)sz;
    return true;
}

static bool is_aes_method(UInt32 method_id) {
    return method_id == SZ_METHOD_AES || ((method_id & 0xFFFF0000u) == 0x06F10000u) ||
           ((method_id & 0xFFFF0000u) == 0x06F00000u);
}

/*
 * When SzArEx_Open fails with SZ_ERROR_UNSUPPORTED on an encoded header (-mhe=on),
 * inspect the encoded header's UnpackInfo/Folder coders to report SZ_EXT_ERR_ENCRYPTED
 * whenever 7zAES is used.
 */
static bool encoded_header_is_encrypted(SzArchive *archive) {
    uint8_t start_hdr[k7zStartHeaderSize];
    Int64 zero = 0;
    if (ISeekInStream_Seek(&archive->source.vt, &zero, SZ_SEEK_SET) != SZ_OK) {
        return false;
    }
    size_t got = k7zStartHeaderSize;
    if (ISeekInStream_Read(&archive->source.vt, start_hdr, &got) != SZ_OK || got != k7zStartHeaderSize) {
        return false;
    }
    if (memcmp(start_hdr, k7zSignature, k7zSignatureSize) != 0 || start_hdr[6] != 0) {
        return false;
    }
    uint64_t next_off = GetUi64(start_hdr + 12);
    uint64_t next_sz = GetUi64(start_hdr + 20);
    if (next_sz == 0 || next_sz > (16u * 1024u * 1024u)) {
        return false;
    }
    if (next_off > archive->source.size - k7zStartHeaderSize ||
        next_sz > archive->source.size - k7zStartHeaderSize - next_off) {
        return false;
    }
    uint8_t *buf = (uint8_t *)malloc((size_t)next_sz);
    if (buf == NULL) {
        return false;
    }
    Int64 hdr_pos = (Int64)(k7zStartHeaderSize + next_off);
    bool encrypted = false;
    if (ISeekInStream_Seek(&archive->source.vt, &hdr_pos, SZ_SEEK_SET) == SZ_OK) {
        size_t rem = (size_t)next_sz;
        size_t off = 0;
        while (off < rem) {
            size_t step = rem - off;
            if (ISeekInStream_Read(&archive->source.vt, buf + off, &step) != SZ_OK || step == 0) {
                break;
            }
            off += step;
        }
        if (off == rem) {
            CSzData sd;
            sd.Data = buf;
            sd.Size = rem;
            uint64_t nid = 0;
            if (read_var_u64(&sd, &nid) && nid == 0x17ULL /* k7zIdEncodedHeader */) {
                while (read_var_u64(&sd, &nid) && nid != 0x00ULL) {
                    if (nid == 0x06ULL /* k7zIdPackInfo */) {
                        uint64_t tmp = 0, num_pack = 0;
                        if (!read_var_u64(&sd, &tmp) || !read_var_u64(&sd, &num_pack)) {
                            break;
                        }
                        while (read_var_u64(&sd, &nid) && nid != 0x00ULL) {
                            if (nid == 0x09ULL /* k7zIdSize */) {
                                for (uint64_t i = 0; i < num_pack; i++) {
                                    if (!read_var_u64(&sd, &tmp)) {
                                        goto done;
                                    }
                                }
                            } else if (!skip_header_data(&sd)) {
                                goto done;
                            }
                        }
                    } else if (nid == 0x07ULL /* k7zIdUnpackInfo */) {
                        while (read_var_u64(&sd, &nid) && nid != 0x00ULL) {
                            if (nid == 0x0BULL /* k7zIdFolder */) {
                                uint64_t num_folders = 0;
                                if (!read_var_u64(&sd, &num_folders) || sd.Size == 0) {
                                    goto done;
                                }
                                uint8_t external = *sd.Data++;
                                sd.Size--;
                                if (external != 0 || num_folders > 64) {
                                    goto done;
                                }
                                for (uint64_t f = 0; f < num_folders; f++) {
                                    CSzFolder folder;
                                    if (SzGetNextFolderItem(&folder, &sd) != SZ_OK) {
                                        goto done;
                                    }
                                    for (UInt32 c = 0; c < folder.NumCoders; c++) {
                                        if (is_aes_method(folder.Coders[c].MethodID)) {
                                            encrypted = true;
                                            goto done;
                                        }
                                    }
                                }
                                goto done;
                            } else if (!skip_header_data(&sd)) {
                                goto done;
                            }
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }
done:
    free(buf);
    return encrypted;
}

static int parse_folder_item(
    const CSzArEx *db,
    UInt32 folder_index,
    CSzFolder *out_folder,
    const Byte **out_props_base,
    size_t *out_props_len
) {
    if (folder_index >= db->db.NumFolders) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    const Byte *base = db->db.CodersData + db->db.FoCodersOffsets[folder_index];
    size_t len = db->db.FoCodersOffsets[(size_t)folder_index + 1] -
                 db->db.FoCodersOffsets[folder_index];
    CSzData sd;
    sd.Data = base;
    sd.Size = len;
    SRes res = SzGetNextFolderItem(out_folder, &sd);
    if (res != SZ_OK) {
        return (res == SZ_ERROR_UNSUPPORTED) ? SZ_EXT_ERR_UNSUPPORTED_CODER : SZ_EXT_ERR_BAD_ARCHIVE;
    }
    if (sd.Size != 0 || out_folder->UnpackStream != db->db.FoToMainUnpackSizeIndex[folder_index]) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    *out_props_base = base;
    *out_props_len = len;
    return SZ_EXT_OK;
}

static int validate_folder_coder(
    const CSzFolder *folder,
    const Byte *props_base,
    size_t props_len,
    bool *out_is_lzma2,
    const Byte **out_coder_props,
    unsigned *out_coder_props_size
) {
    for (UInt32 i = 0; i < folder->NumCoders; i++) {
        if (is_aes_method(folder->Coders[i].MethodID)) {
            return SZ_EXT_ERR_ENCRYPTED;
        }
    }
    if (folder->NumCoders != 1 ||
        folder->NumPackStreams != 1 ||
        folder->NumBonds != 0 ||
        folder->PackStreams[0] != 0) {
        return SZ_EXT_ERR_UNSUPPORTED_CODER;
    }
    const CSzCoderInfo *coder = &folder->Coders[0];
    if (coder->NumStreams != 1) {
        return SZ_EXT_ERR_UNSUPPORTED_CODER;
    }
    if (coder->PropsOffset > props_len || (size_t)coder->PropsSize > props_len - coder->PropsOffset) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    if (coder->MethodID == SZ_METHOD_LZMA2) {
        *out_is_lzma2 = true;
    } else if (coder->MethodID == SZ_METHOD_LZMA) {
        *out_is_lzma2 = false;
    } else {
        return SZ_EXT_ERR_UNSUPPORTED_CODER;
    }
    *out_coder_props = props_base + coder->PropsOffset;
    *out_coder_props_size = coder->PropsSize;
    return SZ_EXT_OK;
}

/* ---------- Open / Close ---------- */

static int sz_open_common(SzArchive *archive, SzArchive **out_archive) {
    sz_ensure_crc_table();

    archive->look_buf_size = SZ_LOOKAHEAD_BUF_SIZE;
    archive->look_buf = (uint8_t *)malloc(archive->look_buf_size);
    if (archive->look_buf == NULL) {
        return SZ_EXT_ERR_MEM_LIMIT;
    }

    LookToRead2_CreateVTable(&archive->look_stream, False);
    archive->look_stream.realStream = &archive->source.vt;
    archive->look_stream.buf = archive->look_buf;
    archive->look_stream.bufSize = archive->look_buf_size;
    LookToRead2_INIT(&archive->look_stream)

    SzArEx_Init(&archive->db);

    SzCappedAlloc header_alloc = {
        { sz_capped_alloc, sz_capped_free },
        0,
        SZ_MAX_HEADER_ALLOC
    };

    SRes res = SzArEx_Open(&archive->db, &archive->look_stream.vt, &header_alloc.vt, &header_alloc.vt);
    if (res != SZ_OK) {
        if (archive->source.last_err != SZ_EXT_OK) {
            return archive->source.last_err;
        }
        switch (res) {
            case SZ_ERROR_NO_ARCHIVE:
                return SZ_EXT_ERR_NO_ARCHIVE;
            case SZ_ERROR_CRC:
                return SZ_EXT_ERR_CRC;
            case SZ_ERROR_MEM:
                return SZ_EXT_ERR_MEM_LIMIT;
            case SZ_ERROR_READ:
                return SZ_EXT_ERR_IO;
            case SZ_ERROR_UNSUPPORTED:
                return encoded_header_is_encrypted(archive)
                    ? SZ_EXT_ERR_ENCRYPTED
                    : SZ_EXT_ERR_UNSUPPORTED_CODER;
            default:
                return SZ_EXT_ERR_BAD_ARCHIVE;
        }
    }

    *out_archive = archive;
    return SZ_EXT_OK;
}

int sz_open_fd(int fd, SzArchive **out_archive) {
    if (out_archive == NULL) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    *out_archive = NULL;
    if (fd < 0) {
        return SZ_EXT_ERR_INVALID_ARG;
    }

    int duped = sz_dup_fd(fd);
    if (duped < 0) {
        return SZ_EXT_ERR_IO;
    }

    uint64_t file_size = 0;
    int probe = sz_probe_seekable_size(duped, &file_size);
    if (probe != SZ_EXT_OK) {
        sz_close_fd(duped);
        return probe;
    }

    SzArchive *archive = (SzArchive *)calloc(1, sizeof(SzArchive));
    if (archive == NULL) {
        sz_close_fd(duped);
        return SZ_EXT_ERR_MEM_LIMIT;
    }

    archive->source.vt.Read = sz_source_read;
    archive->source.vt.Seek = sz_source_seek;
    archive->source.fd = duped;
    archive->source.size = file_size;
    sz_mutex_init(&archive->extract_mutex);
    atomic_init(&archive->progress_bytes, 0);
    atomic_init(&archive->cancelled, 0);

    int err = sz_open_common(archive, out_archive);
    if (err != SZ_EXT_OK) {
        sz_close(archive);
        return err;
    }
    return SZ_EXT_OK;
}

int sz_open_memory(const uint8_t *data, size_t size, SzArchive **out_archive) {
    if (out_archive == NULL) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    *out_archive = NULL;
    if (data == NULL && size > 0) {
        return SZ_EXT_ERR_INVALID_ARG;
    }

    SzArchive *archive = (SzArchive *)calloc(1, sizeof(SzArchive));
    if (archive == NULL) {
        return SZ_EXT_ERR_MEM_LIMIT;
    }

    archive->source.vt.Read = sz_source_read;
    archive->source.vt.Seek = sz_source_seek;
    archive->source.fd = -1;
    archive->source.mem = data;
    archive->source.size = (uint64_t)size;
    sz_mutex_init(&archive->extract_mutex);
    atomic_init(&archive->progress_bytes, 0);
    atomic_init(&archive->cancelled, 0);

    int err = sz_open_common(archive, out_archive);
    if (err != SZ_EXT_OK) {
        sz_close(archive);
        return err;
    }
    return SZ_EXT_OK;
}

/* ---------- Metadata Queries ---------- */

uint32_t sz_entry_count(const SzArchive *archive) {
    return (archive != NULL) ? (uint32_t)archive->db.NumFiles : 0u;
}

int sz_entry_name_utf16(
    const SzArchive *archive,
    uint32_t index,
    uint16_t *buf,
    uint32_t cap_u16,
    uint32_t *out_len_with_nul
) {
    if (archive == NULL || out_len_with_nul == NULL) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    if (index >= archive->db.NumFiles) {
        return SZ_EXT_ERR_INDEX_OUT_OF_RANGE;
    }
    size_t req = SzArEx_GetFileNameUtf16(&archive->db, index, NULL);
    if (req > (size_t)UINT32_MAX) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    *out_len_with_nul = (uint32_t)req;
    if (buf == NULL) {
        return SZ_EXT_OK;
    }
    if (cap_u16 < (uint32_t)req) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    SzArEx_GetFileNameUtf16(&archive->db, index, (UInt16 *)buf);
    return SZ_EXT_OK;
}

static size_t utf16_to_utf8(const UInt16 *src, size_t src_len_with_nul, char *dst, size_t dst_cap) {
    size_t src_chars = (src_len_with_nul > 0) ? (src_len_with_nul - 1u) : 0u;
    size_t out = 0;

    for (size_t i = 0; i < src_chars; i++) {
        uint32_t cp = src[i];
        if (cp >= 0xD800u && cp <= 0xDBFFu) {
            if (i + 1u < src_chars && src[i + 1u] >= 0xDC00u && src[i + 1u] <= 0xDFFFu) {
                uint32_t lo = src[++i];
                cp = 0x10000u + (((cp - 0xD800u) << 10u) | (lo - 0xDC00u));
            } else {
                cp = 0xFFFDu;
            }
        } else if (cp >= 0xDC00u && cp <= 0xDFFFu) {
            cp = 0xFFFDu;
        }

        uint8_t enc[4];
        size_t n = 0;
        if (cp < 0x80u) {
            enc[0] = (uint8_t)cp;
            n = 1;
        } else if (cp < 0x800u) {
            enc[0] = (uint8_t)(0xC0u | (cp >> 6u));
            enc[1] = (uint8_t)(0x80u | (cp & 0x3Fu));
            n = 2;
        } else if (cp < 0x10000u) {
            enc[0] = (uint8_t)(0xE0u | (cp >> 12u));
            enc[1] = (uint8_t)(0x80u | ((cp >> 6u) & 0x3Fu));
            enc[2] = (uint8_t)(0x80u | (cp & 0x3Fu));
            n = 3;
        } else {
            enc[0] = (uint8_t)(0xF0u | (cp >> 18u));
            enc[1] = (uint8_t)(0x80u | ((cp >> 12u) & 0x3Fu));
            enc[2] = (uint8_t)(0x80u | ((cp >> 6u) & 0x3Fu));
            enc[3] = (uint8_t)(0x80u | (cp & 0x3Fu));
            n = 4;
        }
        if (dst != NULL && out + n < dst_cap) {
            memcpy(dst + out, enc, n);
        }
        out += n;
    }
    if (dst != NULL && dst_cap > 0) {
        size_t nul_pos = (out < dst_cap) ? out : (dst_cap - 1u);
        dst[nul_pos] = '\0';
    }
    return out + 1u;
}

int sz_entry_name_utf8(
    const SzArchive *archive,
    uint32_t index,
    char *buf,
    uint32_t buf_cap,
    uint32_t *out_len
) {
    if (archive == NULL || out_len == NULL) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    if (index >= archive->db.NumFiles) {
        return SZ_EXT_ERR_INDEX_OUT_OF_RANGE;
    }
    uint32_t len_u16 = 0;
    int rc = sz_entry_name_utf16(archive, index, NULL, 0, &len_u16);
    if (rc != SZ_EXT_OK) {
        return rc;
    }
    UInt16 stack_u16[256];
    UInt16 *u16_buf = stack_u16;
    if (len_u16 > 256u) {
        u16_buf = (UInt16 *)malloc((size_t)len_u16 * sizeof(UInt16));
        if (u16_buf == NULL) {
            return SZ_EXT_ERR_MEM_LIMIT;
        }
    }
    SzArEx_GetFileNameUtf16(&archive->db, index, u16_buf);
    size_t req_utf8 = utf16_to_utf8(u16_buf, len_u16, buf, (buf != NULL) ? buf_cap : 0u);
    if (u16_buf != stack_u16) {
        free(u16_buf);
    }
    if (req_utf8 > (size_t)UINT32_MAX) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    *out_len = (uint32_t)req_utf8;
    if (buf != NULL && buf_cap < (uint32_t)req_utf8) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    return SZ_EXT_OK;
}

uint64_t sz_entry_size(const SzArchive *archive, uint32_t index) {
    if (archive == NULL || index >= archive->db.NumFiles) {
        return 0;
    }
    if (SzArEx_IsDir(&archive->db, index)) {
        return 0;
    }
    return (uint64_t)SzArEx_GetFileSize(&archive->db, index);
}

uint64_t sz_entry_flags_and_crc(const SzArchive *archive, uint32_t index) {
    if (archive == NULL || index >= archive->db.NumFiles) {
        return 0;
    }
    uint64_t flags = 0;
    if (SzArEx_IsDir(&archive->db, index)) {
        flags |= SZ_ENTRY_FLAG_DIR;
    }
    if (SzBitWithVals_Check(&archive->db.CRCs, index)) {
        flags |= SZ_ENTRY_FLAG_HAS_CRC;
        flags |= ((uint64_t)archive->db.CRCs.Vals[index]) << 32;
    }

    UInt32 folder_index = archive->db.FileToFolder[index];
    if (folder_index == (UInt32)-1) {
        flags |= SZ_ENTRY_FLAG_SUPPORTED_CODER;
        return flags;
    }

    CSzFolder folder;
    const Byte *props_base = NULL;
    size_t props_len = 0;
    if (parse_folder_item(&archive->db, folder_index, &folder, &props_base, &props_len) == SZ_EXT_OK) {
        bool is_lzma2 = false;
        const Byte *coder_props = NULL;
        unsigned coder_props_size = 0;
        int verr = validate_folder_coder(
            &folder, props_base, props_len, &is_lzma2, &coder_props, &coder_props_size
        );
        if (verr == SZ_EXT_ERR_ENCRYPTED) {
            flags |= SZ_ENTRY_FLAG_ENCRYPTED;
        } else if (verr == SZ_EXT_OK) {
            flags |= SZ_ENTRY_FLAG_SUPPORTED_CODER;
        }
    }
    return flags;
}

/* ---------- Streaming Extraction ---------- */

static int extract_locked(
    SzArchive *archive,
    uint32_t index,
    int out_fd,
    uint64_t max_memory_bytes
) {
    atomic_store_explicit(&archive->progress_bytes, 0, memory_order_relaxed);

    if (atomic_load_explicit(&archive->cancelled, memory_order_acquire) != 0) {
        return SZ_EXT_ERR_CANCELLED;
    }
    if (index >= archive->db.NumFiles) {
        return SZ_EXT_ERR_INDEX_OUT_OF_RANGE;
    }
    if (SzArEx_IsDir(&archive->db, index)) {
        return SZ_EXT_ERR_IS_DIRECTORY;
    }
    if (max_memory_bytes == 0) {
        return SZ_EXT_ERR_INVALID_ARG;
    }

    uint64_t target_size = (uint64_t)SzArEx_GetFileSize(&archive->db, index);
    UInt32 folder_index = archive->db.FileToFolder[index];

    if (sz_rewind_fd(out_fd) != SZ_EXT_OK || sz_truncate_zero_fd(out_fd) != SZ_EXT_OK) {
        return SZ_EXT_ERR_IO;
    }

    /* Empty file: verify empty CRC if present and flush 0 bytes. */
    if (folder_index == (UInt32)-1 || target_size == 0) {
        if (SzBitWithVals_Check(&archive->db.CRCs, index)) {
            uint32_t empty_crc = CRC_GET_DIGEST(CRC_INIT_VAL);
            if (empty_crc != archive->db.CRCs.Vals[index]) {
                return SZ_EXT_ERR_CRC;
            }
        }
        return sz_fsync_fd(out_fd);
    }

    CSzFolder folder;
    const Byte *props_base = NULL;
    size_t props_len = 0;
    int ferr = parse_folder_item(&archive->db, folder_index, &folder, &props_base, &props_len);
    if (ferr != SZ_EXT_OK) {
        return ferr;
    }

    bool is_lzma2 = false;
    const Byte *coder_props = NULL;
    unsigned coder_props_size = 0;
    int cerr = validate_folder_coder(
        &folder, props_base, props_len, &is_lzma2, &coder_props, &coder_props_size
    );
    if (cerr != SZ_EXT_OK) {
        return cerr;
    }

    uint64_t folder_unpack_size = (uint64_t)SzAr_GetFolderUnpackSize(&archive->db.db, folder_index);
    UInt32 first_file = archive->db.FolderToFile[folder_index];
    if (first_file > index || first_file >= archive->db.NumFiles) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    uint64_t folder_base_unpack = (uint64_t)archive->db.UnpackPositions[first_file];
    uint64_t target_abs_unpack = (uint64_t)archive->db.UnpackPositions[index];
    if (target_abs_unpack < folder_base_unpack) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    uint64_t target_offset = target_abs_unpack - folder_base_unpack;
    if (target_offset > folder_unpack_size || target_size > folder_unpack_size - target_offset) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    uint64_t target_end = target_offset + target_size;

    UInt32 start_pack_idx = archive->db.db.FoStartPackStreamIndex[folder_index];
    if (start_pack_idx >= archive->db.db.NumPackStreams) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    uint64_t pack_off = (uint64_t)archive->db.db.PackPositions[start_pack_idx];
    uint64_t pack_next = (uint64_t)archive->db.db.PackPositions[(size_t)start_pack_idx + 1u];
    if (pack_next < pack_off) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    uint64_t pack_size = pack_next - pack_off;
    if (archive->db.dataPos > archive->source.size ||
        pack_off > archive->source.size - archive->db.dataPos ||
        pack_size > archive->source.size - archive->db.dataPos - pack_off) {
        return SZ_EXT_ERR_BAD_ARCHIVE;
    }
    uint64_t stream_start = archive->db.dataPos + pack_off;

    /* Read dictionary size and coder properties BEFORE allocating. */
    uint64_t raw_dic_size = 0;
    uint64_t probs_bytes_64 = 0;
    CLzmaProps lzma1_props;
    memset(&lzma1_props, 0, sizeof(lzma1_props));

    if (is_lzma2) {
        if (coder_props_size != 1u) {
            return SZ_EXT_ERR_UNSUPPORTED_CODER;
        }
        Byte prop = coder_props[0];
        if (prop > 40u) {
            return SZ_EXT_ERR_UNSUPPORTED_CODER;
        }
        raw_dic_size = (prop == 40u)
            ? 0xFFFFFFFFULL
            : (((uint64_t)2u | (prop & 1u)) << (prop / 2u + 11u));
        /* Matches NUM_BASE_PROBS (1984) + (LZMA_LIT_SIZE (0x300) << LZMA2_LCLP_MAX (4)) in LzmaDec.c. */
        probs_bytes_64 = (uint64_t)(1984u + (0x300u << 4u)) * sizeof(CLzmaProb);
    } else {
        if (coder_props_size != LZMA_PROPS_SIZE) {
            return SZ_EXT_ERR_UNSUPPORTED_CODER;
        }
        if (LzmaProps_Decode(&lzma1_props, coder_props, coder_props_size) != SZ_OK) {
            return SZ_EXT_ERR_UNSUPPORTED_CODER;
        }
        raw_dic_size = (uint64_t)lzma1_props.dicSize;
        probs_bytes_64 = (1984ULL + (0x300ULL << (lzma1_props.lc + lzma1_props.lp))) * sizeof(CLzmaProb);
    }

    uint64_t dic_bytes_64 = (folder_unpack_size < raw_dic_size) ? folder_unpack_size : raw_dic_size;
    if (dic_bytes_64 == 0) {
        dic_bytes_64 = 1;
    }
    if (probs_bytes_64 > max_memory_bytes ||
        dic_bytes_64 > max_memory_bytes - probs_bytes_64 ||
        dic_bytes_64 > (uint64_t)SIZE_MAX) {
        return SZ_EXT_ERR_MEM_LIMIT;
    }
    SizeT dic_buf_size = (SizeT)dic_bytes_64;

    /* Pre-allocate output file before allocating/decoding to fail fast on ENOSPC. */
    int falloc_err = sz_preallocate_fd(out_fd, target_size);
    if (falloc_err != SZ_EXT_OK) {
        return falloc_err;
    }

    Byte *dic = (Byte *)malloc(dic_buf_size);
    if (dic == NULL) {
        return SZ_EXT_ERR_MEM_LIMIT;
    }

    CLzma2Dec lzma2;
    CLzmaDec lzma1;
    Lzma2Dec_CONSTRUCT(&lzma2)
    LzmaDec_CONSTRUCT(&lzma1)

    int err = SZ_EXT_OK;
    SRes alloc_res;
    if (is_lzma2) {
        alloc_res = Lzma2Dec_AllocateProbs(&lzma2, coder_props[0], &g_std_alloc);
        if (alloc_res == SZ_OK) {
            lzma2.decoder.dic = dic;
            lzma2.decoder.dicBufSize = dic_buf_size;
            Lzma2Dec_Init(&lzma2);
        }
    } else {
        alloc_res = LzmaDec_AllocateProbs(&lzma1, coder_props, coder_props_size, &g_std_alloc);
        if (alloc_res == SZ_OK) {
            lzma1.dic = dic;
            lzma1.dicBufSize = dic_buf_size;
            LzmaDec_Init(&lzma1);
        }
    }

    if (alloc_res != SZ_OK) {
        free(dic);
        return (alloc_res == SZ_ERROR_MEM) ? SZ_EXT_ERR_MEM_LIMIT : SZ_EXT_ERR_UNSUPPORTED_CODER;
    }

    LookToRead2_INIT(&archive->look_stream)
    if (LookInStream_SeekTo(&archive->look_stream.vt, stream_start) != SZ_OK) {
        err = (archive->source.last_err != SZ_EXT_OK) ? archive->source.last_err : SZ_EXT_ERR_IO;
        goto cleanup;
    }

    {
        SizeT *dic_pos_ptr = is_lzma2 ? &lzma2.decoder.dicPos : &lzma1.dicPos;
        uint64_t decoded_total = 0;
        uint64_t written_total = 0;
        uint64_t pack_remaining = pack_size;
        uint32_t crc = CRC_INIT_VAL;

        for (;;) {
            if (atomic_load_explicit(&archive->cancelled, memory_order_acquire) != 0) {
                err = SZ_EXT_ERR_CANCELLED;
                break;
            }

            if (*dic_pos_ptr == dic_buf_size && decoded_total < target_end) {
                *dic_pos_ptr = 0;
            }

            SizeT dic_start = *dic_pos_ptr;
            SizeT space_in_dic = dic_buf_size - dic_start;
            uint64_t rem_to_target_end = target_end - decoded_total;
            SizeT step_out = (rem_to_target_end < (uint64_t)space_in_dic)
                ? (SizeT)rem_to_target_end
                : space_in_dic;
            SizeT dic_limit = dic_start + step_out;
            ELzmaFinishMode finish_mode =
                (decoded_total + (uint64_t)step_out == folder_unpack_size)
                    ? LZMA_FINISH_END
                    : LZMA_FINISH_ANY;

            const void *in_ptr = NULL;
            size_t look_req = archive->look_buf_size;
            if (pack_remaining < (uint64_t)look_req) {
                look_req = (size_t)pack_remaining;
            }
            if (look_req > 0) {
                SRes lres = ILookInStream_Look(&archive->look_stream.vt, &in_ptr, &look_req);
                if (lres != SZ_OK) {
                    err = (archive->source.last_err != SZ_EXT_OK)
                        ? archive->source.last_err
                        : SZ_EXT_ERR_IO;
                    break;
                }
            }

            SizeT in_processed = (SizeT)look_req;
            ELzmaStatus status = LZMA_STATUS_NOT_SPECIFIED;
            SRes dres = is_lzma2
                ? Lzma2Dec_DecodeToDic(&lzma2, dic_limit, (const Byte *)in_ptr, &in_processed, finish_mode, &status)
                : LzmaDec_DecodeToDic(&lzma1, dic_limit, (const Byte *)in_ptr, &in_processed, finish_mode, &status);

            if (in_processed > 0) {
                SRes sres = ILookInStream_Skip(&archive->look_stream.vt, in_processed);
                if (sres != SZ_OK) {
                    err = (archive->source.last_err != SZ_EXT_OK)
                        ? archive->source.last_err
                        : SZ_EXT_ERR_IO;
                    break;
                }
                pack_remaining -= (uint64_t)in_processed;
            }

            SizeT produced = *dic_pos_ptr - dic_start;
            if (produced > 0) {
                uint64_t chunk_start = decoded_total;
                uint64_t chunk_end = decoded_total + (uint64_t)produced;
                decoded_total = chunk_end;
                atomic_store_explicit(&archive->progress_bytes, decoded_total, memory_order_relaxed);

                if (chunk_end > target_offset && chunk_start < target_end) {
                    uint64_t write_start_abs = (chunk_start > target_offset) ? chunk_start : target_offset;
                    uint64_t write_end_abs = (chunk_end < target_end) ? chunk_end : target_end;
                    SizeT buf_off = (SizeT)(write_start_abs - chunk_start);
                    SizeT write_len = (SizeT)(write_end_abs - write_start_abs);

                    crc = CrcUpdate(crc, dic + dic_start + buf_off, write_len);
                    int werr = sz_write_all_fd(out_fd, dic + dic_start + buf_off, write_len);
                    if (werr != SZ_EXT_OK) {
                        err = werr;
                        break;
                    }
                    written_total += (uint64_t)write_len;
                }
            }

            if (dres != SZ_OK) {
                err = SZ_EXT_ERR_BAD_ARCHIVE;
                break;
            }

            if (decoded_total == target_end) {
                if (target_end < folder_unpack_size) {
                    /* Solid archive: target file completed before folder end; stop early. */
                    break;
                }
                /* Single file or final file in folder: verify end of compressed stream. */
                if (status == LZMA_STATUS_FINISHED_WITH_MARK) {
                    if (pack_remaining != 0) {
                        err = SZ_EXT_ERR_BAD_ARCHIVE;
                    }
                    break;
                }
                if (!is_lzma2 && status == LZMA_STATUS_MAYBE_FINISHED_WITHOUT_MARK && pack_remaining == 0) {
                    break;
                }
                if (in_processed == 0 && produced == 0) {
                    err = SZ_EXT_ERR_BAD_ARCHIVE;
                    break;
                }
                /* Otherwise loop once more to consume trailing end marker / EOD byte. */
                continue;
            }

            if (status == LZMA_STATUS_FINISHED_WITH_MARK) {
                /* Stream finished before reaching target_end. */
                err = SZ_EXT_ERR_BAD_ARCHIVE;
                break;
            }

            if (in_processed == 0 && produced == 0) {
                err = SZ_EXT_ERR_BAD_ARCHIVE;
                break;
            }
        }

        if (err == SZ_EXT_OK) {
            if (written_total != target_size) {
                err = SZ_EXT_ERR_BAD_ARCHIVE;
            } else if (SzBitWithVals_Check(&archive->db.CRCs, index)) {
                uint32_t computed_crc = CRC_GET_DIGEST(crc);
                if (computed_crc != archive->db.CRCs.Vals[index]) {
                    err = SZ_EXT_ERR_CRC;
                }
            }
        }
        if (err == SZ_EXT_OK) {
            err = sz_fsync_fd(out_fd);
        }
    }

cleanup:
    if (is_lzma2) {
        Lzma2Dec_FreeProbs(&lzma2, &g_std_alloc);
    } else {
        LzmaDec_FreeProbs(&lzma1, &g_std_alloc);
    }
    free(dic);
    return err;
}

int sz_extract_to_fd(
    SzArchive *archive,
    uint32_t index,
    int out_fd,
    uint64_t max_memory_bytes
) {
    if (archive == NULL || out_fd < 0) {
        return SZ_EXT_ERR_INVALID_ARG;
    }
    if (sz_mutex_trylock(&archive->extract_mutex) != 0) {
        return SZ_EXT_ERR_BUSY;
    }
    int rc = extract_locked(archive, index, out_fd, max_memory_bytes);
    sz_mutex_unlock(&archive->extract_mutex);
    return rc;
}

uint64_t sz_progress(const SzArchive *archive) {
    if (archive == NULL) {
        return 0;
    }
    return atomic_load_explicit(&((SzArchive *)archive)->progress_bytes, memory_order_relaxed);
}

void sz_cancel(SzArchive *archive) {
    if (archive != NULL) {
        atomic_store_explicit(&archive->cancelled, 1, memory_order_release);
    }
}

void sz_close(SzArchive *archive) {
    if (archive == NULL) {
        return;
    }
    sz_cancel(archive);
    sz_mutex_lock(&archive->extract_mutex);
    sz_mutex_unlock(&archive->extract_mutex);
    sz_mutex_destroy(&archive->extract_mutex);

    SzArEx_Free(&archive->db, &g_std_alloc);
    free(archive->look_buf);
    sz_close_fd(archive->source.fd);
    free(archive);
}
