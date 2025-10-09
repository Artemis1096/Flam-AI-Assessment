#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <memory>
#include <mutex>
#include <chrono> // Added for high-resolution timing

// Make sure this path is correct based on your project structure
#include "../gl/CameraRenderer.h"

#define LOG_TAG "NATIVE_PIPELINE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// --- Global Variables ---
std::unique_ptr<CameraRenderer> renderer;
std::mutex frameMutex;
cv::Mat processedFrame;
int processingMode = 1;


// --- JNI Functions for GLSurfaceView Lifecycle (called from MyGLRenderer) ---
extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_MyGLRenderer_nativeOnSurfaceCreated(JNIEnv *env, jclass clazz) {
    LOGD("-> nativeOnSurfaceCreated called");
    renderer = std::make_unique<CameraRenderer>();
    renderer->init();
    LOGD("<- nativeOnSurfaceCreated finished");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_MyGLRenderer_nativeOnSurfaceChanged(JNIEnv *env, jclass clazz, jint width, jint height) {
    LOGD("-> nativeOnSurfaceChanged called with %dx%d", width, height);
    if (renderer) {
        renderer->onSurfaceChanged(width, height);
    }
    LOGD("<- nativeOnSurfaceChanged finished");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_MyGLRenderer_nativeOnDrawFrame(JNIEnv *env, jclass clazz) {
    cv::Mat frameToRender;
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        if (processedFrame.empty()) {
            return;
        }
        frameToRender = processedFrame.clone();
    }

    if (renderer) {
        renderer->drawFrame(frameToRender);
    }
}


// --- JNI Functions for Frame Processing & Cleanup (called from NativeBridge) ---
extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_NativeBridge_setProcessingMode(JNIEnv *env, jclass, jint mode) {
    LOGD("Setting processing mode to %d", mode);
    processingMode = mode;
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_NativeBridge_nativeOnDestroy(JNIEnv *env, jclass clazz) {
    LOGD("-> nativeOnDestroy called");
    renderer.reset();
    LOGD("<- nativeOnDestroy finished");
}

// --- UPDATED: processFrame now contains timing logic ---
extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_NativeBridge_processFrame(
        JNIEnv *env,
        jclass,
        jbyteArray frameData,
        jint width,
        jint height) {

    // --- Start Timer ---
    auto startTime = std::chrono::high_resolution_clock::now();

    jbyte* data = env->GetByteArrayElements(frameData, nullptr);
    cv::Mat yuv(height + height / 2, width, CV_8UC1, reinterpret_cast<unsigned char*>(data));

    cv::Mat finalRgbaFrame;

    if (processingMode == 1) {
        // --- EDGE DETECTION MODE ---
        cv::Mat rgb, gray, edges;
        cv::cvtColor(yuv, rgb, cv::COLOR_YUV2RGB_NV21);
        cv::cvtColor(rgb, gray, cv::COLOR_RGB2GRAY);
        cv::Canny(gray, edges, 50, 150);
        cv::cvtColor(edges, finalRgbaFrame, cv::COLOR_GRAY2RGBA);
    } else {
        // --- RAW FEED MODE ---
        cv::cvtColor(yuv, finalRgbaFrame, cv::COLOR_YUV2RGBA_NV21);
    }

    {
        std::lock_guard<std::mutex> lock(frameMutex);
        processedFrame = finalRgbaFrame;
    }

    env->ReleaseByteArrayElements(frameData, data, JNI_ABORT);

    // --- End Timer & Log Duration ---
    auto endTime = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime).count();
    LOGD("Frame processed in: %lld ms", duration);
}