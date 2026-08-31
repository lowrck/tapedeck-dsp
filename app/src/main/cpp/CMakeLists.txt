cmake_minimum_required(VERSION 3.22.1)
project(tapedeckdsp)

include(FetchContent)
FetchContent_Declare(
        oboe
        GIT_REPOSITORY https://github.com/google/oboe.git
        GIT_TAG 1.10.0
)
FetchContent_MakeAvailable(oboe)

add_library(tapedeckdsp SHARED
        native-lib.cpp
        TapeEngine.cpp
        dsp/BiquadFilter.cpp
        dsp/Saturator.cpp
        dsp/WowFlutter.cpp
        dsp/NoiseGenerator.cpp
        dsp/DropoutSimulator.cpp
        dsp/Wander.cpp
)

target_include_directories(tapedeckdsp PRIVATE .)

find_library(log-lib log)

target_link_libraries(tapedeckdsp
        oboe
        android
        ${log-lib}
)

# Required for compatibility with 16 KB memory page size devices.
target_link_options(tapedeckdsp PRIVATE "-Wl,-z,max-page-size=16384")
