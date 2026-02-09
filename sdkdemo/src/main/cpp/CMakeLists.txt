cmake_minimum_required(VERSION 3.22.1)

project(embedded_tts)

add_library(embedded_tts SHARED
    espeak_jni.cpp
)

# Prebuilt eSpeak-ng library (named libttsespeak.so to match Android.mk)
add_library(ttsespeak SHARED IMPORTED)
set_target_properties(ttsespeak PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libttsespeak.so
)

find_library(log-lib log)

target_include_directories(embedded_tts PRIVATE ${CMAKE_CURRENT_LIST_DIR})

target_link_libraries(embedded_tts
    ttsespeak
    ${log-lib}
)
