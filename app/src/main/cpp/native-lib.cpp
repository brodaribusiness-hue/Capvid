#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <thread>

#include "whisper.h"

#define LOG_TAG "CapvidWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Design notes
//
// The previous version of this file kept the progress callback's target in
// file-scope globals (g_jvm / g_bridgeObj / g_onProgressMethod) that were
// overwritten by every call to fullTranscribe(). Two problems with that:
//
//   * it was not thread safe - a second transcription would replace the first
//     one's global reference, leaking it and delivering progress to the wrong
//     object; and
//   * the global reference was only deleted on the success path, so any early
//     return (or a thrown Java exception) leaked a reference permanently.
//
// All per-call state now travels through whisper's own user_data pointers in a
// heap CallContext, which is destroyed on every exit path. A mutex serialises
// transcriptions because a single whisper_context is not safe to run twice
// concurrently.
// ---------------------------------------------------------------------------

namespace {

JavaVM *g_jvm = nullptr;

/** Per-call state handed to whisper through its *_user_data pointers. */
struct CallContext {
    jobject bridgeRef = nullptr;   // global ref to the WhisperBridge instance
    jmethodID progressMethod = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<int> lastProgress{-1};
};

/** The single global flag consulted by whisper's abort callback. */
std::atomic<bool> g_cancelRequested{false};

std::mutex g_transcribeMutex;

void progress_trampoline(struct whisper_context * /*ctx*/, struct whisper_state * /*state*/,
                         int progress, void *user_data) {
    auto *call = static_cast<CallContext *>(user_data);
    if (call == nullptr || g_jvm == nullptr || call->bridgeRef == nullptr ||
        call->progressMethod == nullptr) {
        return;
    }
    // Throttle: whisper can report the same percentage many times.
    if (call->lastProgress.exchange(progress) == progress) return;

    JNIEnv *env = nullptr;
    bool attachedHere = false;
    jint rc = g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attachedHere = true;
    } else if (rc != JNI_OK || env == nullptr) {
        return;
    }

    env->CallVoidMethod(call->bridgeRef, call->progressMethod, static_cast<jint>(progress));
    if (env->ExceptionCheck()) {
        // A Java listener threw; log and clear so the native frame unwinds cleanly.
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (attachedHere) g_jvm->DetachCurrentThread();
}

bool abort_trampoline(void *user_data) {
    auto *call = static_cast<CallContext *>(user_data);
    if (call != nullptr && call->cancelled.load()) return true;
    return g_cancelRequested.load();
}

int pick_thread_count() {
    unsigned int hw = std::thread::hardware_concurrency();
    if (hw == 0) hw = 4;
    // Leave a core for the UI and the OS; whisper scales poorly past 4 on phones
    // and extra threads mostly add thermal load.
    int n = static_cast<int>(hw) - 1;
    if (n < 1) n = 1;
    if (n > 4) n = 4;
    return n;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_initContext(JNIEnv *env, jobject /*thiz*/, jstring modelPath) {
    if (modelPath == nullptr) {
        LOGE("initContext: null model path");
        return 0;
    }
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        LOGE("initContext: GetStringUTFChars failed");
        return 0;
    }

    struct whisper_context_params cparams = whisper_context_default_params();
    // use_gpu stays at its default; on Android the CPU backend is what whisper
    // builds here, and leaving the default avoids failing on devices without a
    // usable GPU backend.
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("initContext: whisper_init_from_file_with_params failed (corrupt or wrong model?)");
        return 0;
    }
    LOGI("initContext: model loaded");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_freeContext(JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    if (ctxPtr == 0) return;
    whisper_free(reinterpret_cast<whisper_context *>(ctxPtr));
    LOGI("freeContext: model released");
}

JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_fullTranscribe(JNIEnv *env, jobject thiz,
                                                          jlong ctxPtr, jfloatArray audioData) {
    if (ctxPtr == 0) {
        LOGE("fullTranscribe: null context");
        return -1;
    }
    if (audioData == nullptr) {
        LOGE("fullTranscribe: null audio");
        return -1;
    }

    // A whisper_context cannot service two whisper_full() calls at once.
    std::lock_guard<std::mutex> lock(g_transcribeMutex);

    CallContext call;
    g_cancelRequested.store(false);

    call.bridgeRef = env->NewGlobalRef(thiz);
    if (call.bridgeRef == nullptr) {
        LOGE("fullTranscribe: NewGlobalRef failed");
        return -1;
    }
    jclass cls = env->GetObjectClass(thiz);
    if (cls != nullptr) {
        call.progressMethod = env->GetMethodID(cls, "onNativeProgress", "(I)V");
        env->DeleteLocalRef(cls);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        call.progressMethod = nullptr;  // progress reporting is optional
    }

    jsize len = env->GetArrayLength(audioData);
    if (len <= 0) {
        env->DeleteGlobalRef(call.bridgeRef);
        LOGE("fullTranscribe: empty audio array");
        return -1;
    }

    // JNI_ABORT on release: we never write back into the caller's array, and this
    // lets the VM avoid copying back.
    jfloat *audio = env->GetFloatArrayElements(audioData, nullptr);
    if (audio == nullptr) {
        env->DeleteGlobalRef(call.bridgeRef);
        LOGE("fullTranscribe: GetFloatArrayElements failed (out of memory for %d samples?)", len);
        return -1;
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress   = false;
    params.print_special    = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    params.translate        = false;
    params.language         = "en";
    params.n_threads        = pick_thread_count();
    params.token_timestamps = true;   // required for word-level timings
    params.progress_callback           = progress_trampoline;
    params.progress_callback_user_data = &call;
    params.abort_callback              = abort_trampoline;
    params.abort_callback_user_data    = &call;

    LOGI("fullTranscribe: %d samples (%.1fs), %d threads",
         static_cast<int>(len), len / 16000.0f, params.n_threads);

    int result = whisper_full(reinterpret_cast<whisper_context *>(ctxPtr), params, audio, len);

    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
    env->DeleteGlobalRef(call.bridgeRef);
    call.bridgeRef = nullptr;

    if (g_cancelRequested.load() || call.cancelled.load()) {
        LOGI("fullTranscribe: cancelled");
        return -2;   // distinct from a real failure so Java can say "cancelled"
    }
    if (result != 0) {
        LOGE("fullTranscribe: whisper_full returned %d", result);
    } else {
        LOGI("fullTranscribe: done, %d segments",
             whisper_full_n_segments(reinterpret_cast<whisper_context *>(ctxPtr)));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_cancelTranscribe(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_cancelRequested.store(true);
    LOGI("cancelTranscribe: abort requested");
}

JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTextSegmentCount(JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    if (ctxPtr == 0) return 0;
    return whisper_full_n_segments(reinterpret_cast<whisper_context *>(ctxPtr));
}

JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenCount(JNIEnv * /*env*/, jobject /*thiz*/,
                                                         jlong ctxPtr, jint segmentIndex) {
    if (ctxPtr == 0) return 0;
    return whisper_full_n_tokens(reinterpret_cast<whisper_context *>(ctxPtr), segmentIndex);
}

JNIEXPORT jstring JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenText(JNIEnv *env, jobject /*thiz*/,
                                                        jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    if (ctxPtr == 0) return nullptr;
    const char *text = whisper_full_get_token_text(
            reinterpret_cast<whisper_context *>(ctxPtr), segmentIndex, tokenIndex);
    // whisper can return NULL for special tokens; NewStringUTF(NULL) is
    // undefined behaviour, so map it to an empty string instead.
    return env->NewStringUTF(text != nullptr ? text : "");
}

JNIEXPORT jlong JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenT0(JNIEnv * /*env*/, jobject /*thiz*/,
                                                      jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    if (ctxPtr == 0) return 0;
    whisper_token_data data = whisper_full_get_token_data(
            reinterpret_cast<whisper_context *>(ctxPtr), segmentIndex, tokenIndex);
    // t0 is in CENTISECONDS (see whisper.h: "in centiseconds"); the caller
    // multiplies by 10 to get milliseconds.
    return static_cast<jlong>(data.t0);
}

JNIEXPORT jlong JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenT1(JNIEnv * /*env*/, jobject /*thiz*/,
                                                      jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    if (ctxPtr == 0) return 0;
    whisper_token_data data = whisper_full_get_token_data(
            reinterpret_cast<whisper_context *>(ctxPtr), segmentIndex, tokenIndex);
    return static_cast<jlong>(data.t1);
}

JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getTokenId(JNIEnv * /*env*/, jobject /*thiz*/,
                                                      jlong ctxPtr, jint segmentIndex, jint tokenIndex) {
    if (ctxPtr == 0) return -1;
    return whisper_full_get_token_id(
            reinterpret_cast<whisper_context *>(ctxPtr), segmentIndex, tokenIndex);
}

JNIEXPORT jint JNICALL
Java_com_saad_capvid_whisper_WhisperBridge_getEotToken(JNIEnv * /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    if (ctxPtr == 0) return 0;
    return whisper_token_eot(reinterpret_cast<whisper_context *>(ctxPtr));
}

} // extern "C"
