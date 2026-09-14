#include <jni.h>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "CapvidWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_jvm = nullptr;
static jobject g_bridgeObj = nullptr;
static jmethodID g_onProgressMethod = nullptr;

static void progress_trampoline(struct whisper_context *ctx, struct whisper_state *state, int progress, void *user_data) {
    if (g_jvm == nullptr || g_bridgeObj == nullptr || g_onProgressMethod == nullptr) return;
    JNIEnv *env;
    if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) {
        env->CallVoidMethod(g_bridgeObj, g_onProgressMethod, progress);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("Failed to load whisper model");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_freeContext(JNIEnv *env, jobject, jlong ctxPtr) {
    if (ctxPtr == 0) return;
    whisper_free(reinterpret_cast<whisper_context *>(ctxPtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_fullTranscribe(JNIEnv *env, jobject thiz, jlong ctxPtr, jfloatArray audioData) {
    if (ctxPtr == 0) return -1;
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);

    env->GetJavaVM(&g_jvm);
    g_bridgeObj = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    g_onProgressMethod = env->GetMethodID(cls, "onNativeProgress", "(I)V");

    jsize len = env->GetArrayLength(audioData);
    jfloat *audio = env->GetFloatArrayElements(audioData, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;
    params.token_timestamps = true;
    params.progress_callback = progress_trampoline;
    params.progress_callback_user_data = nullptr;

    int result = whisper_full(ctx, params, audio, len);

    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);

    if (g_bridgeObj != nullptr) {
        env->DeleteGlobalRef(g_bridgeObj);
        g_bridgeObj = nullptr;
    }

    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTextSegmentCount(JNIEnv *env, jobject, jlong ctxPtr) {
    return whisper_full_n_segments(reinterpret_cast<whisper_context *>(ctxPtr));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenCount(JNIEnv *env, jobject, jlong ctxPtr, jint segmentIndex) {
    return whisper_full_n_tokens(reinterpret_cast<whisper_context *>(ctxPtr), segmentIndex);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenText(JNIEnv *env, jobject, jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    return env->NewStringUTF(whisper_full_get_token_text(ctx, segmentIndex, tokenIndex));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenT0(JNIEnv *env, jobject, jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    whisper_token_data data = whisper_full_get_token_data(ctx, segmentIndex, tokenIndex);
    return data.t0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenT1(JNIEnv *env, jobject, jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    whisper_token_data data = whisper_full_get_token_data(ctx, segmentIndex, tokenIndex);
    return data.t1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenId(JNIEnv *env, jobject, jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    return whisper_full_get_token_id(ctx, segmentIndex, tokenIndex);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getEotToken(JNIEnv *env, jobject, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    return whisper_token_eot(ctx);
}
