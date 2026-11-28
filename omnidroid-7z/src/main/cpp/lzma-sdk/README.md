# Vendored LZMA SDK

- **Version:** 26.06 (2026-11-06)
- **Source:** https://github.com/ip7z/7zip/releases/download/26.06/lzma2606.7z
- **Archive SHA-256:** `1D9DA340870B12712E9068BE178A4436FBAD785486B62AED6E2AA88EA1E3BB65`
- **License:** Public domain (Igor Pavlov)
- **Scope:** Unmodified `C/` directory only.

## Notes for 26.06
- Single-thread compile-time macro is `Z7_ST` (not `_7ZIP_ST`).
- Size-optimization macro `Z7_LZMA_SIZE_OPT` is intentionally left undefined for decode throughput.
- Struct and API usage in `../core/sz_extract.c` was verified against `C/7z.h`, `C/7zTypes.h`, `C/7zArcIn.c`, `C/7zDec.c`, `C/LzmaDec.h`, and `C/Lzma2Dec.h` from this version:
  - `CSzArEx` / `CSzAr` (`FoCodersOffsets`, `CodersData`, `FoStartPackStreamIndex`, `FoToCoderUnpackSizes`, `FoToMainUnpackSizeIndex`, `CoderUnpackSizes`, `PackPositions`, `UnpackPositions`, `FolderToFile`, `FileToFolder`, `CRCs`, `IsDirs`, `dataPos`).
  - `SzGetNextFolderItem(CSzFolder *f, CSzData *sd)` to inspect folder coder IDs, properties offsets/sizes, and bond counts.
  - `CLzma2Dec` / `CLzmaDec` with `Lzma2Dec_AllocateProbs` / `LzmaDec_AllocateProbs`, manual circular `dic` buffer, and `Lzma2Dec_DecodeToDic` / `LzmaDec_DecodeToDic`.
