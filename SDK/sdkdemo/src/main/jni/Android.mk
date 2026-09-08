LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := ttsespeak
LOCAL_SRC_FILES := ../jniLibs/$(TARGET_ARCH_ABI)/libttsespeak.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := embedded_tts
LOCAL_SRC_FILES := ../cpp/espeak_jni.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../cpp
LOCAL_SHARED_LIBRARIES := ttsespeak
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
