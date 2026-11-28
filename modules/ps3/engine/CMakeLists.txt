cmake_minimum_required(VERSION 3.30)
if(NOT ANDROID OR NOT TARGET rpcs3_emu)
    message(FATAL_ERROR "Configure through the pinned upstream Android engine project")
endif()
find_package(Python3 REQUIRED COMPONENTS Interpreter)
set(OMNIDROID_EMULATION_JOBS 2 CACHE STRING "Maximum concurrent emulator compilations")
set_property(GLOBAL APPEND PROPERTY JOB_POOLS ps3_emulation=${OMNIDROID_EMULATION_JOBS})
set_property(TARGET rpcs3_emu PROPERTY JOB_POOL_COMPILE ps3_emulation)
# The upstream identity preprocessor includes generated LLVM IR headers.
# Make that dependency explicit rather than relying on incidental build order.
set(identity_dir "${CMAKE_BINARY_DIR}/android/spu_codegen_identity")
add_custom_target(omnidroid_ps3_codegen_identity
    COMMAND "${Python3_EXECUTABLE}" -I "${CMAKE_SOURCE_DIR}/android/spu_codegen_identity.py"
        "${CMAKE_SOURCE_DIR}" "${CMAKE_BINARY_DIR}" "${identity_dir}/spu_codegen_identity_android.h"
    BYPRODUCTS "${identity_dir}/spu_codegen_identity_android.h" VERBATIM)
add_dependencies(omnidroid_ps3_codegen_identity intrinsics_gen)
add_dependencies(rpcs3_emu omnidroid_ps3_codegen_identity)
set_property(SOURCE "${CMAKE_SOURCE_DIR}/rpcs3/Emu/Cell/SPUCommonRecompiler.cpp"
    TARGET_DIRECTORY rpcs3_emu APPEND PROPERTY INCLUDE_DIRECTORIES "${identity_dir}")
execute_process(COMMAND "${Python3_EXECUTABLE}" "${CMAKE_CURRENT_SOURCE_DIR}/prepare_sources.py"
    "${CMAKE_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/platform"
    COMMAND_ERROR_IS_FATAL ANY)

# The upstream Android application target is not part of this project.
get_target_property(engine_sources rpcs3_emu SOURCES)
list(FILTER engine_sources EXCLUDE REGEX "RSX/Overlays/(HomeMenu|BigPicture)/")
list(FILTER engine_sources EXCLUDE REGEX "RSX/RSXThread.cpp$")
list(FILTER engine_sources EXCLUDE REGEX "^System.cpp$")
list(FILTER engine_sources EXCLUDE REGEX "CPU/CPUThread.cpp$")
list(APPEND engine_sources "${CMAKE_CURRENT_BINARY_DIR}/platform/CPUThread.cpp")
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/platform/CPUThread.cpp"
    TARGET_DIRECTORY rpcs3_emu PROPERTY INCLUDE_DIRECTORIES "${CMAKE_SOURCE_DIR}/rpcs3/Emu/CPU")
list(FILTER engine_sources EXCLUDE REGEX "RSX/(VK/VKPresent|GL/GLPresent).cpp$")
list(FILTER engine_sources EXCLUDE REGEX "RSX/Core/RSXDisplayEngine.cpp$")
list(APPEND engine_sources "${CMAKE_CURRENT_BINARY_DIR}/platform/RSXDisplayEngine.cpp")
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/platform/RSXDisplayEngine.cpp"
    TARGET_DIRECTORY rpcs3_emu PROPERTY INCLUDE_DIRECTORIES "${CMAKE_SOURCE_DIR}/rpcs3/Emu/RSX/Core;${CMAKE_CURRENT_SOURCE_DIR}")
list(APPEND engine_sources "${CMAKE_CURRENT_BINARY_DIR}/platform/RSXThread.cpp")
list(APPEND engine_sources "${CMAKE_CURRENT_BINARY_DIR}/platform/System.cpp")
list(APPEND engine_sources "${CMAKE_CURRENT_BINARY_DIR}/platform/VKPresent.cpp" "${CMAKE_CURRENT_BINARY_DIR}/platform/GLPresent.cpp")
set_property(TARGET rpcs3_emu PROPERTY SOURCES "${engine_sources}")
target_include_directories(rpcs3_emu PRIVATE "${CMAKE_SOURCE_DIR}/rpcs3/Emu/RSX" "${CMAKE_SOURCE_DIR}/rpcs3/Emu"
    "${CMAKE_SOURCE_DIR}/3rdparty/librashader/include")
foreach(backend VK GL)
    set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/platform/${backend}Present.cpp"
        TARGET_DIRECTORY rpcs3_emu PROPERTY INCLUDE_DIRECTORIES "${CMAKE_SOURCE_DIR}/rpcs3/Emu/RSX/${backend}")
endforeach()
set(platform_sources)
foreach(source product_info hid_pad_handler ds3_pad_handler ds4_pad_handler
    dualsense_pad_handler skateboard_pad_handler ps_move_handler ps_move_config
    ps_move_calibration ps_move_tracker mouse_gyro_handler virtual_pad_handler)
    list(APPEND platform_sources "${CMAKE_SOURCE_DIR}/rpcs3/Input/${source}.cpp")
endforeach()
list(APPEND platform_sources "${CMAKE_SOURCE_DIR}/rpcs3/dev/iso.cpp" "${CMAKE_SOURCE_DIR}/rpcs3/rpcs3_version.cpp")

add_library(omnidroid-ps3-engine SHARED
    ../bridge/bridge.cpp ../bridge/host_callbacks.cpp
    performance.cpp api.cpp runtime.cpp configuration.cpp snapshots.cpp content.cpp
    guest_dialogs.cpp guest_strings.cpp platform_support.cpp storage.cpp
    "${CMAKE_CURRENT_BINARY_DIR}/platform/pad_thread.cpp"
    ${platform_sources})
target_compile_features(omnidroid-ps3-engine PRIVATE cxx_std_23)
target_include_directories(omnidroid-ps3-engine PRIVATE include
    "${CMAKE_SOURCE_DIR}" "${CMAKE_SOURCE_DIR}/rpcs3" "${CMAKE_SOURCE_DIR}/rpcs3/Input"
    "${CMAKE_SOURCE_DIR}/3rdparty/hidapi/hidapi/hidapi"
    "${CMAKE_SOURCE_DIR}/3rdparty/hidapi/hidapi/libusb"
    "${CMAKE_SOURCE_DIR}/3rdparty/libusb/libusb/libusb"
    "${CMAKE_SOURCE_DIR}/3rdparty/yaml-cpp/yaml-cpp/include")
target_link_libraries(omnidroid-ps3-engine PRIVATE rpcs3_emu 3rdparty::fusion
    3rdparty::hidapi 3rdparty::libusb android log aaudio dl)
target_link_options(omnidroid-ps3-engine PRIVATE
    "-Wl,--no-undefined" "-Wl,-z,max-page-size=16384"
    "-Wl,-z,start-stop-visibility=hidden"
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
set_target_properties(omnidroid-ps3-engine PROPERTIES
    OUTPUT_NAME omnidroid-armsx3
    CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN YES)

# Keep JNI dispatch baseline-safe; the emulator retains its ARMv8.2 target.
set_source_files_properties(../bridge/bridge.cpp ../bridge/host_callbacks.cpp
    PROPERTIES COMPILE_OPTIONS "-march=armv8-a")
