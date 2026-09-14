cmake_minimum_required(VERSION 3.22.1)
project(capvid_native)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper)

add_library(capvid_native SHARED
    native-lib.cpp
)

target_include_directories(capvid_native PRIVATE
    whisper/include
    whisper/ggml/include
)

target_link_libraries(capvid_native
    whisper
    android
    log
)
