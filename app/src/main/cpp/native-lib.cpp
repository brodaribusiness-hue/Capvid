#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <sstream>
#include <string>
#include <thread>

#include "whisper.h"

namespace {

constexpr const char * TAG = "CapvidWhisper";

struct ProgressBridge {
    JNIEnv *env;
    jobject callback;
    jmethodID method;
};

void progress_callback(whisper_context *, whisper_state *, int progress, void *user_data) {
    auto *bridge = static_cast<ProgressBridge *>(user_data);
    if (bridge == nullptr || bridge->env == nullptr || bridge->callback == nullptr || bridge->method == nullptr) {
        return;
    }
    bridge->env->CallVoidMethod(bridge->callback, bridge->method, static_cast<jint>(std::clamp(progress, 0, 100)));
}

std::string json_escape(const char *value) {
    if (value == nullptr) return {};
    std::ostringstream output;
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(value); *p != '\0'; ++p) {
        switch (*p) {
            case '\\': output << "\\\\"; break;
            case '"': output << "\\\""; break;
            case '\n': output << "\\n"; break;
            case '\r': output << "\\r"; break;
            case '\t': output << "\\t"; break;
            default:
                if (*p < 0x20) {
                    output << ' ';
                } else {
                    output << static_cast<char>(*p);
                }
        }
    }
    return output.str();
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_saad_capvid_whisper_WhisperNative_transcribe(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path,
        jfloatArray audio,
        jstring language,
        jobject callback) {
    if (model_path == nullptr || audio == nullptr || language == nullptr) {
        return env->NewStringUTF("{\"error\":\"Missing transcription input\"}");
    }

    const char *model_chars = env->GetStringUTFChars(model_path, nullptr);
    const char *language_chars = env->GetStringUTFChars(language, nullptr);
    if (model_chars == nullptr || language_chars == nullptr) {
        if (model_chars != nullptr) env->ReleaseStringUTFChars(model_path, model_chars);
        if (language_chars != nullptr) env->ReleaseStringUTFChars(language, language_chars);
        return env->NewStringUTF("{\"error\":\"Unable to read transcription input\"}");
    }

    jsize sample_count = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    if (samples == nullptr || sample_count == 0) {
        env->ReleaseStringUTFChars(model_path, model_chars);
        env->ReleaseStringUTFChars(language, language_chars);
        return env->NewStringUTF("{\"error\":\"Audio contains no samples\"}");
    }

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false; // predictable memory use on every Android device
    whisper_context *context = whisper_init_from_file_with_params(model_chars, context_params);

    std::string result;
    if (context == nullptr) {
        result = "{\"error\":\"Unable to load whisper model\"}";
    } else {
        ProgressBridge bridge{};
        bridge.env = env;
        bridge.callback = callback == nullptr ? nullptr : env->NewGlobalRef(callback);
        if (bridge.callback != nullptr) {
            jclass callback_class = env->GetObjectClass(bridge.callback);
            bridge.method = env->GetMethodID(callback_class, "onProgress", "(I)V");
            env->DeleteLocalRef(callback_class);
        }

        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
        params.translate = false;
        params.no_context = true;
        params.no_timestamps = false;
        params.single_segment = false;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.token_timestamps = true;
        params.split_on_word = true;
        params.language = language_chars;
        params.detect_language = false;
        params.progress_callback = progress_callback;
        params.progress_callback_user_data = &bridge;

        const int status = whisper_full(context, params, samples, static_cast<int>(sample_count));
        if (status != 0) {
            result = "{\"error\":\"Whisper inference failed\"}";
        } else {
            std::ostringstream json;
            json << "{\"text\":\"";
            for (int segment = 0; segment < whisper_full_n_segments(context); ++segment) {
                json << json_escape(whisper_full_get_segment_text(context, segment));
            }
            json << "\",\"tokens\":[";
            bool first = true;
            for (int segment = 0; segment < whisper_full_n_segments(context); ++segment) {
                const int token_count = whisper_full_n_tokens(context, segment);
                for (int token = 0; token < token_count; ++token) {
                    const char *token_text = whisper_full_get_token_text(context, segment, token);
                    // Whisper emits timestamp/control tokens in the token stream. They
                    // have no human-readable text and are intentionally omitted.
                    if (token_text == nullptr || token_text[0] == '\0' || token_text[0] == '<') continue;
                    const int64_t start_ms = std::max<int64_t>(0, whisper_full_get_token_t0(context, segment, token) * 10);
                    int64_t end_ms = whisper_full_get_token_t1(context, segment, token) * 10;
                    if (end_ms <= start_ms) end_ms = start_ms + 40;
                    if (!first) json << ',';
                    first = false;
                    json << "{\"text\":\"" << json_escape(token_text)
                         << "\",\"startMs\":" << start_ms
                         << ",\"endMs\":" << end_ms << '}';
                }
            }
            json << "]}";
            result = json.str();
        }

        if (bridge.callback != nullptr) env->DeleteGlobalRef(bridge.callback);
        whisper_free(context);
    }

    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(model_path, model_chars);
    env->ReleaseStringUTFChars(language, language_chars);
    return env->NewStringUTF(result.c_str());
}
