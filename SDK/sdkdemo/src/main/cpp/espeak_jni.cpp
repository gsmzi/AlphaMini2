#include <jni.h>
#include <string>
#include <cstring>
#include <vector>
#include <mutex>
#include <android/log.h>
#include "espeakng_min.h"

#define LOG_TAG "EmbeddedTtsJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::recursive_mutex gMutex;
static std::vector<int16_t> gSamples;
static bool gInitialized = false;

static int SynthCallback(short *wav, int numsamples, espeak_EVENT * /*events*/) {
    if (wav == nullptr || numsamples <= 0) {
        return 0;
    }
    std::lock_guard<std::recursive_mutex> lock(gMutex);
    gSamples.insert(gSamples.end(), wav, wav + numsamples);
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ubtrobot_mini_sdkdemo_voicedialogue_v2_EmbeddedTtsEngine_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring dataPath) {
    const char *path = env->GetStringUTFChars(dataPath, nullptr);
    int result = espeak_Initialize(AUDIO_OUTPUT_RETRIEVAL, 0, path, 0);
    env->ReleaseStringUTFChars(dataPath, path);

    if (result < 0) {
        LOGE("espeak_Initialize failed: %d", result);
        gInitialized = false;
        return JNI_FALSE;
    }

    espeak_SetSynthCallback(SynthCallback);
    gInitialized = true;
    LOGD("espeak-ng initialized");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ubtrobot_mini_sdkdemo_voicedialogue_v2_EmbeddedTtsEngine_nativeSetVoice(
        JNIEnv *env, jobject /*thiz*/, jstring lang) {
    if (!gInitialized) return JNI_FALSE;
    const char *voice = env->GetStringUTFChars(lang, nullptr);
    int result = espeak_SetVoiceByName(voice);
    env->ReleaseStringUTFChars(lang, voice);
    if (result < 0) {
        LOGE("espeak_SetVoiceByName failed: %d", result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ubtrobot_mini_sdkdemo_voicedialogue_v2_EmbeddedTtsEngine_nativeSynthesize(
        JNIEnv *env, jobject /*thiz*/, jstring text, jstring lang) {
    if (!gInitialized) return nullptr;

    const char *voice = env->GetStringUTFChars(lang, nullptr);
    int voiceResult = espeak_SetVoiceByName(voice);
    env->ReleaseStringUTFChars(lang, voice);
    if (voiceResult < 0) {
        LOGE("espeak_SetVoiceByName failed: %d", voiceResult);
        return nullptr;
    }

    const char *utf8 = env->GetStringUTFChars(text, nullptr);
    std::lock_guard<std::recursive_mutex> lock(gMutex);
    gSamples.clear();

    unsigned int uniqueId = 0;
    int synthResult = espeak_Synth(utf8, std::strlen(utf8), 0, 0, 0, 0, &uniqueId, nullptr);
    env->ReleaseStringUTFChars(text, utf8);
    if (synthResult < 0) {
        LOGE("espeak_Synth failed: %d", synthResult);
        return nullptr;
    }

    espeak_Synchronize();

    if (gSamples.empty()) {
        return env->NewByteArray(0);
    }

    const size_t byteCount = gSamples.size() * sizeof(int16_t);
    jbyteArray out = env->NewByteArray(static_cast<jsize>(byteCount));
    if (!out) return nullptr;

    env->SetByteArrayRegion(out, 0, static_cast<jsize>(byteCount),
                            reinterpret_cast<const jbyte *>(gSamples.data()));
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ubtrobot_mini_sdkdemo_voicedialogue_v2_EmbeddedTtsEngine_nativeShutdown(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::recursive_mutex> lock(gMutex);
    gSamples.clear();
    gInitialized = false;
}
