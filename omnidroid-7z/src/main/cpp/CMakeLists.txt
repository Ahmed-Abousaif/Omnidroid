cmake_minimum_required(VERSION 3.22.1)
project(omnidroid-7z C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(LZMA_SDK_C_DIR "${CMAKE_CURRENT_SOURCE_DIR}/lzma-sdk/C")

set(LZMA_SDK_SOURCES
    "${LZMA_SDK_C_DIR}/7zArcIn.c"
    "${LZMA_SDK_C_DIR}/7zBuf.c"
    "${LZMA_SDK_C_DIR}/7zCrc.c"
    "${LZMA_SDK_C_DIR}/7zCrcOpt.c"
    "${LZMA_SDK_C_DIR}/7zDec.c"
    "${LZMA_SDK_C_DIR}/7zStream.c"
    "${LZMA_SDK_C_DIR}/Bcj2.c"
    "${LZMA_SDK_C_DIR}/Bra.c"
    "${LZMA_SDK_C_DIR}/Bra86.c"
    "${LZMA_SDK_C_DIR}/BraIA64.c"
    "${LZMA_SDK_C_DIR}/CpuArch.c"
    "${LZMA_SDK_C_DIR}/Delta.c"
    "${LZMA_SDK_C_DIR}/LzmaDec.c"
    "${LZMA_SDK_C_DIR}/Lzma2Dec.c"
)

add_library(sz_core STATIC
    "${CMAKE_CURRENT_SOURCE_DIR}/core/sz_extract.c"
    ${LZMA_SDK_SOURCES}
)

target_include_directories(sz_core
    PUBLIC
        "${CMAKE_CURRENT_SOURCE_DIR}/core"
    PRIVATE
        "${LZMA_SDK_C_DIR}"
)

target_compile_definitions(sz_core
    PRIVATE
        Z7_ST
        Z7_EXTRACT_ONLY
        _FILE_OFFSET_BITS=64
        _LARGEFILE64_SOURCE
)

if(MSVC)
    target_compile_options(sz_core PRIVATE /O2 /experimental:c11atomics /D_CRT_SECURE_NO_WARNINGS)
elseif(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(sz_core PRIVATE
        -O3
        -fPIC
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
    )
    set_source_files_properties("${CMAKE_CURRENT_SOURCE_DIR}/core/sz_extract.c" PROPERTIES
        COMPILE_OPTIONS "-Wall;-Wextra;-Wconversion;-Wno-sign-conversion"
    )
endif()

if(ANDROID)
    add_library(omnidroid-7z SHARED
        "${CMAKE_CURRENT_SOURCE_DIR}/jni/sevenzip_jni.c"
    )
    target_link_libraries(omnidroid-7z PRIVATE sz_core log)
    target_compile_definitions(omnidroid-7z PRIVATE
        _FILE_OFFSET_BITS=64
        _LARGEFILE64_SOURCE
    )
    target_compile_options(omnidroid-7z PRIVATE
        -O3
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
        -Wall
        -Wextra
    )
    target_link_options(omnidroid-7z PRIVATE
        -Wl,--gc-sections
        -Wl,-z,max-page-size=16384
        -Wl,--exclude-libs,ALL
        -Wl,--no-undefined
    )
else()
    option(OMNIDROID_7Z_BUILD_TESTS "Build host CTest runner" ON)
    option(OMNIDROID_7Z_ENABLE_FUZZER "Build libFuzzer parser target" OFF)

    if(OMNIDROID_7Z_BUILD_TESTS)
        enable_testing()
        add_executable(sz_extract_test
            "${CMAKE_CURRENT_SOURCE_DIR}/tests/sz_extract_test.c"
        )
        target_link_libraries(sz_extract_test PRIVATE sz_core)
        if(MSVC)
    target_compile_options(sz_core PRIVATE /O2 /experimental:c11atomics /D_CRT_SECURE_NO_WARNINGS)
elseif(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
            target_link_libraries(sz_extract_test PRIVATE pthread)
        endif()
        add_test(
            NAME sz_extract_test
            COMMAND sz_extract_test
                "${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures"
                "${CMAKE_CURRENT_BINARY_DIR}"
        )
    endif()

    if(OMNIDROID_7Z_ENABLE_FUZZER AND CMAKE_C_COMPILER_ID MATCHES "Clang")
        add_executable(sz_open_fuzz
            "${CMAKE_CURRENT_SOURCE_DIR}/tests/sz_open_fuzz.c"
        )
        target_link_libraries(sz_open_fuzz PRIVATE sz_core)
        target_compile_options(sz_open_fuzz PRIVATE -fsanitize=fuzzer,address -fno-omit-frame-pointer)
        target_link_options(sz_open_fuzz PRIVATE -fsanitize=fuzzer,address)
    endif()
endif()
