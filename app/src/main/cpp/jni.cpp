#include <jni.h>
#include <memory>
#include <mutex>
#include "audio/OboeEngine.h"

using namespace vfx;

static std::shared_ptr<OboeEngine> g_engine;
static std::mutex g_mtx;

static std::shared_ptr<OboeEngine> engine() {
    std::lock_guard<std::mutex> l(g_mtx);
    if (!g_engine) g_engine = std::make_shared<OboeEngine>();
    return g_engine;
}

extern "C" {

JNIEXPORT jint JNICALL Java_com_voicefxpro_NativeEngine_nParamCount(JNIEnv*, jobject) { return PARAM_COUNT; }

JNIEXPORT jint JNICALL Java_com_voicefxpro_NativeEngine_nStart(JNIEnv*, jobject, jint inId, jint outId) { return engine()->start(inId, outId); }

JNIEXPORT void JNICALL Java_com_voicefxpro_NativeEngine_nStop(JNIEnv*, jobject) { engine()->stop(); }

JNIEXPORT void JNICALL Java_com_voicefxpro_NativeEngine_nSetParam(JNIEnv*, jobject, jint idx, jfloat v) { engine()->dsp.setParam(idx, v); }

JNIEXPORT void JNICALL Java_com_voicefxpro_NativeEngine_nSetMonitor(JNIEnv*, jobject, jboolean on, jfloat vol) { engine()->setMonitor(on == JNI_TRUE, vol); }

JNIEXPORT void JNICALL Java_com_voicefxpro_NativeEngine_nGetMeters(JNIEnv* env, jobject, jfloatArray arr) {
    float m[METER_COUNT]; engine()->dsp.getMeters(m);
    jsize n = env->GetArrayLength(arr);
    env->SetFloatArrayRegion(arr, 0, std::min<jsize>(n, METER_COUNT), m);
}

JNIEXPORT void JNICALL Java_com_voicefxpro_NativeEngine_nGetStatus(JNIEnv* env, jobject, jintArray arr) {
    int s[S_COUNT]; engine()->getStatus(s);
    jsize n = env->GetArrayLength(arr);
    env->SetIntArrayRegion(arr, 0, std::min<jsize>(n, S_COUNT), s);
}

JNIEXPORT jboolean JNICALL Java_com_voicefxpro_NativeEngine_nStartRecording(JNIEnv* env, jobject, jstring path, jint sampleRate) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    bool ok = engine()->recorder.start(p, sampleRate);
    env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_com_voicefxpro_NativeEngine_nStopRecording(JNIEnv*, jobject) { return (jlong)engine()->recorder.stop(); }

JNIEXPORT jdouble JNICALL Java_com_voicefxpro_NativeEngine_nRecordingSeconds(JNIEnv*, jobject) { return engine()->recorder.seconds(); }

}  // extern "C"
