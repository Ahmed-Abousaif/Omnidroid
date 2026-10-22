cmake_minimum_required(VERSION 3.22.1)
project(omnidroid-chd)

set(CHDR_WANT_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(libchdr)

add_library(omnidroid-chd SHARED chd_jni.c)
target_include_directories(omnidroid-chd PRIVATE libchdr/include)
target_compile_options(omnidroid-chd PRIVATE -Os -flto -fvisibility=hidden)
target_link_options(omnidroid-chd PRIVATE -flto -Wl,--gc-sections -Wl,--exclude-libs,ALL)
target_link_libraries(omnidroid-chd chdr-static log)
