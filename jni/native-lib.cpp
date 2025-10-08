#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring>

#define LOG_TAG "JNI_PIPELINE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_example_andro_NativeBridge_processFrame(
        JNIEnv *env,
        jclass,
        jbyteArray frameData,
        jint width,
        jint height) {

    jbyte *data = env->GetByteArrayElements(frameData, nullptr);
    jsize length = env->GetArrayLength(frameData);

    LOGD("Received frame: %d bytes, width=%d, height=%d", length, width, height);

    std::vector<uint8_t> buffer(length);
    memcpy(buffer.data(), data, length);

    env->ReleaseByteArrayElements(frameData, data, 0);
}
