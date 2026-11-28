#ifndef OMNIDROID_7Z_SZ_EXTRACT_H
#define OMNIDROID_7Z_SZ_EXTRACT_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    SZ_EXT_OK = 0,
    SZ_EXT_ERR_INVALID_ARG = -1,
    SZ_EXT_ERR_IO = -2,
    SZ_EXT_ERR_NOT_SEEKABLE = -3,
    SZ_EXT_ERR_NO_ARCHIVE = -4,
    SZ_EXT_ERR_BAD_ARCHIVE = -5,
    SZ_EXT_ERR_UNSUPPORTED_CODER = -6,
    SZ_EXT_ERR_ENCRYPTED = -7,
    SZ_EXT_ERR_MEM_LIMIT = -8,
    SZ_EXT_ERR_CRC = -9,
    SZ_EXT_ERR_DISK_FULL = -10,
    SZ_EXT_ERR_CANCELLED = -11,
    SZ_EXT_ERR_INDEX_OUT_OF_RANGE = -12,
    SZ_EXT_ERR_IS_DIRECTORY = -13,
    SZ_EXT_ERR_BUSY = -14
} SzExtractError;

#define SZ_ENTRY_FLAG_DIR              (1ULL << 0)
#define SZ_ENTRY_FLAG_HAS_CRC          (1ULL << 1)
#define SZ_ENTRY_FLAG_ENCRYPTED        (1ULL << 2)
#define SZ_ENTRY_FLAG_SUPPORTED_CODER  (1ULL << 3)

typedef struct SzArchive SzArchive;

int sz_open_fd(int fd, SzArchive **out_archive);
int sz_open_memory(const uint8_t *data, size_t size, SzArchive **out_archive);

uint32_t sz_entry_count(const SzArchive *archive);
int sz_entry_name_utf16(
    const SzArchive *archive,
    uint32_t index,
    uint16_t *buf,
    uint32_t cap_u16,
    uint32_t *out_len_with_nul
);
int sz_entry_name_utf8(
    const SzArchive *archive,
    uint32_t index,
    char *buf,
    uint32_t buf_cap,
    uint32_t *out_len
);
uint64_t sz_entry_size(const SzArchive *archive, uint32_t index);
uint64_t sz_entry_flags_and_crc(const SzArchive *archive, uint32_t index);

int sz_extract_to_fd(
    SzArchive *archive,
    uint32_t index,
    int out_fd,
    uint64_t max_memory_bytes
);

uint64_t sz_progress(const SzArchive *archive);
void sz_cancel(SzArchive *archive);
void sz_close(SzArchive *archive);

#ifdef __cplusplus
}
#endif

#endif /* OMNIDROID_7Z_SZ_EXTRACT_H */
