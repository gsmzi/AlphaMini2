cmake_minimum_required(VERSION 3.22.1)

project(embedded_tts)

add_library(embedded_tts SHARED
    espeak_jni.cpp
)

add_library(espeak-ng SHARED IMPORTED)
set_target_properties(espeak-ng PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libespeak-ng.so
)

find_library(log-lib log)

target_include_directories(embedded_tts PRIVATE ${CMAKE_CURRENT_LIST_DIR})

target_link_libraries(embedded_tts
    espeak-ng
    ${log-lib}
)
