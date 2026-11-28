#include "sz_extract.h"

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <io.h>
#include <sys/stat.h>
#define open_rd(p) _open((p), _O_RDONLY | _O_BINARY)
#define open_wr_tmp(p) _open((p), _O_RDWR | _O_CREAT | _O_TRUNC | _O_BINARY, _S_IREAD | _S_IWRITE)
#define open_null() _open("NUL", _O_WRONLY | _O_BINARY)
#define close_fd(f) _close(f)
#define lseek_start(f) _lseeki64((f), 0, SEEK_SET)
#define lseek_end(f) _lseeki64((f), 0, SEEK_END)
#define read_fd(f, b, n) _read((f), (b), (unsigned int)(n))
#define write_fd(f, b, n) _write((f), (b), (unsigned int)(n))
#define unlink_file(p) _unlink(p)
#else
#include <sys/stat.h>
#include <unistd.h>
#define open_rd(p) open((p), O_RDONLY)
#define open_wr_tmp(p) open((p), O_RDWR | O_CREAT | O_TRUNC, 0600)
#define open_null() open("/dev/null", O_WRONLY)
#define close_fd(f) close(f)
#define lseek_start(f) lseek((f), 0, SEEK_SET)
#define lseek_end(f) lseek((f), 0, SEEK_END)
#define read_fd(f, b, n) read((f), (b), (n))
#define write_fd(f, b, n) write((f), (b), (n))
#define unlink_file(p) unlink(p)
#endif

#define ASSERT_TRUE(cond)                                                     \
    do {                                                                      \
        if (!(cond)) {                                                        \
            fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);   \
            exit(1);                                                          \
        }                                                                     \
    } while (0)

#define ASSERT_EQ_INT(expected, actual)                                       \
    do {                                                                      \
        int _e = (expected);                                                  \
        int _a = (actual);                                                    \
        if (_e != _a) {                                                       \
            fprintf(stderr, "FAIL %s:%d: expected %d, got %d\n",              \
                    __FILE__, __LINE__, _e, _a);                              \
            exit(1);                                                          \
        }                                                                     \
    } while (0)

static void pattern_bytes(uint32_t seed, uint8_t *buf, size_t len) {
    uint32_t x = seed;
    for (size_t i = 0; i < len; i++) {
        x = 1664525u * x + 1013904223u;
        buf[i] = (uint8_t)(x >> 24u);
    }
}

static void join_path(char *out, size_t cap, const char *dir, const char *name) {
    snprintf(out, cap, "%s/%s", dir, name);
}

static uint8_t *read_entire_fd(int fd, size_t *out_len) {
    int64_t sz = (int64_t)lseek_end(fd);
    ASSERT_TRUE(sz >= 0);
    ASSERT_TRUE(lseek_start(fd) >= 0);
    uint8_t *buf = (uint8_t *)malloc((size_t)(sz > 0 ? sz : 1));
    ASSERT_TRUE(buf != NULL);
    size_t off = 0;
    while (off < (size_t)sz) {
        int r = (int)read_fd(fd, buf + off, (size_t)sz - off);
        ASSERT_TRUE(r > 0);
        off += (size_t)r;
    }
    *out_len = (size_t)sz;
    return buf;
}

static int find_entry_by_name(const SzArchive *ar, const char *target_utf8) {
    uint32_t count = sz_entry_count(ar);
    char name[512];
    for (uint32_t i = 0; i < count; i++) {
        uint32_t len = 0;
        if (sz_entry_name_utf8(ar, i, name, sizeof(name), &len) == SZ_EXT_OK) {
            if (strcmp(name, target_utf8) == 0) {
                return (int)i;
            }
        }
    }
    return -1;
}

static void test_single_lzma2(const char *fixtures, const char *tmp_dir) {
    char in_path[512], out_path[512];
    join_path(in_path, sizeof(in_path), fixtures, "single_lzma2.7z");
    join_path(out_path, sizeof(out_path), tmp_dir, "out_single_lzma2.bin");

    int fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    SzArchive *ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd); /* Verify dup() lets caller close original fd immediately. */
    ASSERT_TRUE(ar != NULL);
    ASSERT_TRUE(sz_entry_count(ar) == 1u);

    char name[128];
    uint32_t name_len = 0;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_entry_name_utf8(ar, 0, name, sizeof(name), &name_len));
    ASSERT_TRUE(strcmp(name, "hello.txt") == 0);
    ASSERT_TRUE(sz_entry_size(ar, 0) == 26ULL);
    ASSERT_TRUE((sz_entry_flags_and_crc(ar, 0) & SZ_ENTRY_FLAG_SUPPORTED_CODER) != 0);

    int out_fd = open_wr_tmp(out_path);
    ASSERT_TRUE(out_fd >= 0);
    ASSERT_EQ_INT(SZ_EXT_OK, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));
    ASSERT_TRUE(sz_progress(ar) == 26ULL);

    size_t got_len = 0;
    uint8_t *got = read_entire_fd(out_fd, &got_len);
    ASSERT_TRUE(got_len == 26u);
    ASSERT_TRUE(memcmp(got, "Hello Omnidroid 7z world!\n", 26u) == 0);
    free(got);
    close_fd(out_fd);
    unlink_file(out_path);
    sz_close(ar);
    printf("PASS test_single_lzma2\n");
}

static void test_solid_multi(const char *fixtures, const char *tmp_dir) {
    char in_path[512], out_path[512], empty_path[512];
    join_path(in_path, sizeof(in_path), fixtures, "solid_multi.7z");
    join_path(out_path, sizeof(out_path), tmp_dir, "out_solid_target.bin");
    join_path(empty_path, sizeof(empty_path), tmp_dir, "out_solid_empty.bin");

    int fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    SzArchive *ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);

    int dir_idx = find_entry_by_name(ar, "02_dir");
    ASSERT_TRUE(dir_idx >= 0);
    ASSERT_TRUE((sz_entry_flags_and_crc(ar, (uint32_t)dir_idx) & SZ_ENTRY_FLAG_DIR) != 0);
    int null_fd = open_null();
    ASSERT_TRUE(null_fd >= 0);
    ASSERT_EQ_INT(SZ_EXT_ERR_IS_DIRECTORY, sz_extract_to_fd(ar, (uint32_t)dir_idx, null_fd, 64ULL * 1024 * 1024));
    close_fd(null_fd);

    int empty_idx = find_entry_by_name(ar, "03_empty.bin");
    ASSERT_TRUE(empty_idx >= 0);
    int efd = open_wr_tmp(empty_path);
    ASSERT_TRUE(efd >= 0);
    ASSERT_EQ_INT(SZ_EXT_OK, sz_extract_to_fd(ar, (uint32_t)empty_idx, efd, 64ULL * 1024 * 1024));
    size_t empty_len = 0;
    free(read_entire_fd(efd, &empty_len));
    ASSERT_TRUE(empty_len == 0u);
    close_fd(efd);
    unlink_file(empty_path);

    /* "04_target_\xe6\x97\xa5\xe6\x9c\xac\xe8\xaa\x9e.bin" = "04_target_日本語.bin" */
    int target_idx = find_entry_by_name(ar, "04_target_\xE6\x97\xA5\xE6\x9C\xAC\xE8\xAA\x9E.bin");
    ASSERT_TRUE(target_idx >= 0);
    int out_fd = open_wr_tmp(out_path);
    ASSERT_TRUE(out_fd >= 0);
    ASSERT_EQ_INT(SZ_EXT_OK, sz_extract_to_fd(ar, (uint32_t)target_idx, out_fd, 64ULL * 1024 * 1024));
    /* Progress includes skipped 80 KiB (01_first.bin) + 90 KiB target = 170 KiB. */
    ASSERT_TRUE(sz_progress(ar) == (80ULL + 90ULL) * 1024ULL);

    size_t got_len = 0;
    uint8_t *got = read_entire_fd(out_fd, &got_len);
    size_t exp_len = 90u * 1024u;
    ASSERT_TRUE(got_len == exp_len);
    uint8_t *expected = (uint8_t *)malloc(exp_len);
    ASSERT_TRUE(expected != NULL);
    pattern_bytes(2u, expected, exp_len);
    ASSERT_TRUE(memcmp(got, expected, exp_len) == 0);
    free(expected);
    free(got);
    close_fd(out_fd);
    unlink_file(out_path);

    ASSERT_EQ_INT(SZ_EXT_ERR_INDEX_OUT_OF_RANGE, sz_extract_to_fd(ar, sz_entry_count(ar), 0, 64ULL * 1024 * 1024));
    sz_close(ar);
    printf("PASS test_solid_multi\n");
}

static void test_lzma1_circular(const char *fixtures, const char *tmp_dir) {
    char in_path[512], out_path[512];
    join_path(in_path, sizeof(in_path), fixtures, "single_lzma1.7z");
    join_path(out_path, sizeof(out_path), tmp_dir, "out_lzma1.bin");

    int fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    SzArchive *ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);

    int out_fd = open_wr_tmp(out_path);
    ASSERT_TRUE(out_fd >= 0);
    ASSERT_EQ_INT(SZ_EXT_OK, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));

    size_t got_len = 0;
    uint8_t *got = read_entire_fd(out_fd, &got_len);
    size_t exp_len = 70u * 1024u;
    ASSERT_TRUE(got_len == exp_len);
    uint8_t *expected = (uint8_t *)malloc(exp_len);
    ASSERT_TRUE(expected != NULL);
    pattern_bytes(4u, expected, exp_len);
    ASSERT_TRUE(memcmp(got, expected, exp_len) == 0);
    free(expected);
    free(got);
    close_fd(out_fd);
    unlink_file(out_path);
    sz_close(ar);
    printf("PASS test_lzma1_circular\n");
}

static void test_error_cases(const char *fixtures, const char *tmp_dir) {
    char in_path[512], out_path[512], trunc_path[512];
    join_path(out_path, sizeof(out_path), tmp_dir, "out_err.bin");

    /* 1. BCJ -> UNSUPPORTED_CODER */
    join_path(in_path, sizeof(in_path), fixtures, "bcj_lzma2.7z");
    int fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    SzArchive *ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);
    ASSERT_TRUE((sz_entry_flags_and_crc(ar, 0) & SZ_ENTRY_FLAG_SUPPORTED_CODER) == 0);
    int out_fd = open_wr_tmp(out_path);
    ASSERT_EQ_INT(SZ_EXT_ERR_UNSUPPORTED_CODER, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));
    close_fd(out_fd);
    sz_close(ar);

    /* 2. Copy -> UNSUPPORTED_CODER */
    join_path(in_path, sizeof(in_path), fixtures, "copy_uncompressed.7z");
    fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);
    out_fd = open_wr_tmp(out_path);
    ASSERT_EQ_INT(SZ_EXT_ERR_UNSUPPORTED_CODER, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));
    close_fd(out_fd);
    sz_close(ar);

    /* 3. Encrypted data -> ENCRYPTED */
    join_path(in_path, sizeof(in_path), fixtures, "encrypted_data.7z");
    fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);
    ASSERT_TRUE((sz_entry_flags_and_crc(ar, 0) & SZ_ENTRY_FLAG_ENCRYPTED) != 0);
    out_fd = open_wr_tmp(out_path);
    ASSERT_EQ_INT(SZ_EXT_ERR_ENCRYPTED, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));
    close_fd(out_fd);
    sz_close(ar);

    /* 4. Encrypted header -> ENCRYPTED on open */
    join_path(in_path, sizeof(in_path), fixtures, "encrypted_header.7z");
    fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_ERR_ENCRYPTED, sz_open_fd(fd, &ar));
    ASSERT_TRUE(ar == NULL);
    close_fd(fd);

    /* 5. Bad CRC -> CRC error */
    join_path(in_path, sizeof(in_path), fixtures, "bad_crc.7z");
    fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);
    out_fd = open_wr_tmp(out_path);
    ASSERT_EQ_INT(SZ_EXT_ERR_CRC, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));
    close_fd(out_fd);
    sz_close(ar);

    /* 6. Oversized dictionary -> MEM_LIMIT, then succeeds with larger limit */
    join_path(in_path, sizeof(in_path), fixtures, "big_dict.7z");
    fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);
    out_fd = open_wr_tmp(out_path);
    ASSERT_EQ_INT(SZ_EXT_ERR_MEM_LIMIT, sz_extract_to_fd(ar, 0, out_fd, 1024ULL * 1024ULL));
    ASSERT_EQ_INT(SZ_EXT_OK, sz_extract_to_fd(ar, 0, out_fd, 8ULL * 1024ULL * 1024ULL));
    close_fd(out_fd);
    sz_close(ar);

    /* 7. Truncated archive (truncate single_lzma2.7z) */
    join_path(in_path, sizeof(in_path), fixtures, "single_lzma2.7z");
    join_path(trunc_path, sizeof(trunc_path), tmp_dir, "trunc.7z");
    fd = open_rd(in_path);
    size_t orig_len = 0;
    uint8_t *orig = read_entire_fd(fd, &orig_len);
    close_fd(fd);
    int tfd = open_wr_tmp(trunc_path);
    ASSERT_TRUE(orig_len > 60u);
    ASSERT_TRUE(write_fd(tfd, orig, orig_len / 2u) == (int)(orig_len / 2u));
    ASSERT_TRUE(lseek_start(tfd) >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_ERR_BAD_ARCHIVE, sz_open_fd(tfd, &ar));
    close_fd(tfd);
    unlink_file(trunc_path);
    free(orig);

    /* 8. Non-archive garbage */
    join_path(trunc_path, sizeof(trunc_path), tmp_dir, "garbage.7z");
    tfd = open_wr_tmp(trunc_path);
    ASSERT_TRUE(write_fd(tfd, "NOT_A_7Z_ARCHIVE_AT_ALL_0123456789", 34) == 34);
    ASSERT_TRUE(lseek_start(tfd) >= 0);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_ERR_NO_ARCHIVE, sz_open_fd(tfd, &ar));
    close_fd(tfd);
    unlink_file(trunc_path);

    /* 9. Pre-cancelled extraction -> CANCELLED */
    join_path(in_path, sizeof(in_path), fixtures, "single_lzma2.7z");
    fd = open_rd(in_path);
    ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);
    sz_cancel(ar);
    out_fd = open_wr_tmp(out_path);
    ASSERT_EQ_INT(SZ_EXT_ERR_CANCELLED, sz_extract_to_fd(ar, 0, out_fd, 64ULL * 1024 * 1024));
    close_fd(out_fd);
    sz_close(ar);

    unlink_file(out_path);
    printf("PASS test_error_cases\n");
}

static void test_over_4gb_entry(const char *fixtures) {
    char in_path[512];
    join_path(in_path, sizeof(in_path), fixtures, "over4gb.7z");
    int fd = open_rd(in_path);
    ASSERT_TRUE(fd >= 0);
    SzArchive *ar = NULL;
    ASSERT_EQ_INT(SZ_EXT_OK, sz_open_fd(fd, &ar));
    close_fd(fd);

    uint64_t expected_size = 4100ULL * 1024ULL * 1024ULL;
    ASSERT_TRUE(sz_entry_size(ar, 0) == expected_size);
    int null_fd = open_null();
    ASSERT_TRUE(null_fd >= 0);
    ASSERT_EQ_INT(SZ_EXT_OK, sz_extract_to_fd(ar, 0, null_fd, 64ULL * 1024 * 1024));
    ASSERT_TRUE(sz_progress(ar) == expected_size);
    close_fd(null_fd);
    sz_close(ar);
    printf("PASS test_over_4gb_entry\n");
}

int main(int argc, char **argv) {
    const char *fixtures = (argc > 1) ? argv[1] : "tests/fixtures";
    const char *tmp_dir = (argc > 2) ? argv[2] : ".";

    test_single_lzma2(fixtures, tmp_dir);
    test_solid_multi(fixtures, tmp_dir);
    test_lzma1_circular(fixtures, tmp_dir);
    test_error_cases(fixtures, tmp_dir);
    test_over_4gb_entry(fixtures);

    printf("ALL HOST 7Z TESTS PASSED\n");
    return 0;
}
