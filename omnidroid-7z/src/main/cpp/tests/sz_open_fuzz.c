#include "sz_extract.h"

#include <stddef.h>
#include <stdint.h>

int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    SzArchive *archive = NULL;
    if (sz_open_memory(data, size, &archive) == SZ_EXT_OK && archive != NULL) {
        uint32_t count = sz_entry_count(archive);
        char name[256];
        for (uint32_t i = 0; i < count && i < 64u; i++) {
            uint32_t out_len = 0;
            (void)sz_entry_name_utf8(archive, i, name, sizeof(name), &out_len);
            (void)sz_entry_size(archive, i);
            (void)sz_entry_flags_and_crc(archive, i);
        }
        sz_close(archive);
    }
    return 0;
}
