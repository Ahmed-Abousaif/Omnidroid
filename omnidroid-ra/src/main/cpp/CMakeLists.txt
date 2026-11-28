cmake_minimum_required(VERSION 3.22.1)
project(omnidroid_ra_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(omnidroid-ra-bridge SHARED
    ra_native_bridge.cpp
)

target_include_directories(omnidroid-ra-bridge PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

target_compile_options(omnidroid-ra-bridge PRIVATE
    -Os
    -fvisibility=hidden
    -Wall
    -Wextra
)

target_link_options(omnidroid-ra-bridge PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
    "-Wl,--exclude-libs,ALL"
)

target_link_libraries(omnidroid-ra-bridge
    dl
    log
)
