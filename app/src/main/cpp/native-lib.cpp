#include <jni.h>

#include "TapeEngine.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeCreate(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new TapeEngine());
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<TapeEngine *>(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeLoadAudio(JNIEnv *env, jobject, jlong handle, jfloatArray pcm,
                                                   jint channelCount, jint sampleRate) {
    auto *engine = reinterpret_cast<TapeEngine *>(handle);
    const jsize totalSamples = env->GetArrayLength(pcm);
    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    const int64_t frameCount = totalSamples / channelCount;
    const bool ok = engine->loadAudio(data, frameCount, channelCount, sampleRate);
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativePlay(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<TapeEngine *>(handle)->play();
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativePause(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<TapeEngine *>(handle)->pause();
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeStop(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<TapeEngine *>(handle)->stop();
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeSeekToFrame(JNIEnv *, jobject, jlong handle, jlong frame) {
    reinterpret_cast<TapeEngine *>(handle)->seekToFrame(frame);
}

JNIEXPORT jlong JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeGetPositionFrames(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<TapeEngine *>(handle)->getPositionFrames();
}

JNIEXPORT jlong JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeGetDurationFrames(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<TapeEngine *>(handle)->getDurationFrames();
}

JNIEXPORT jboolean JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeIsPlaying(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<TapeEngine *>(handle)->isPlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeSetTapeAge(JNIEnv *, jobject, jlong handle, jfloat value) {
    reinterpret_cast<TapeEngine *>(handle)->setTapeAge(value);
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeSetDustDirt(JNIEnv *, jobject, jlong handle, jfloat value) {
    reinterpret_cast<TapeEngine *>(handle)->setDustDirt(value);
}

JNIEXPORT void JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeSetTapeType(JNIEnv *, jobject, jlong handle, jint type) {
    reinterpret_cast<TapeEngine *>(handle)->setTapeType(type);
}

JNIEXPORT jfloat JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeGetVuLeft(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<TapeEngine *>(handle)->getVuLeft();
}

JNIEXPORT jfloat JNICALL
Java_com_tapedeck_dsp_AudioEngine_nativeGetVuRight(JNIEnv *, jobject, jlong handle) {
    return reinterpret_cast<TapeEngine *>(handle)->getVuRight();
}

} // extern "C"
