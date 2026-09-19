cmake_minimum_required(VERSION 3.22.1)
project(capvid_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# CI clones ggml-org/whisper.cpp into this directory before Gradle runs. Keeping
# the source external prevents a 100+ MB third-party checkout from entering Git.
if (NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp is missing. Clone ggml-org/whisper.cpp into app/src/main/cpp/whisper before building.")
endif()

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper EXCLUDE_FROM_ALL)

add_library(capvid-native SHARED native-lib.cpp)

target_include_directories(capvid-native PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/whisper/include"
)

target_link_libraries(capvid-native
    whisper
    log
    android
)

if (ANDROID)
    target_compile_options(capvid-native PRIVATE -fexceptions -frtti -Wall -Wextra -Wno-unused-parameter)
endif()
