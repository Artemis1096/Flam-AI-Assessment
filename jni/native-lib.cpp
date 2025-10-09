#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring>
#include <memory>
#include <mutex>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

// Make sure these paths are correct based on your project structure
#include "../gl/CameraRenderer.h"
#include "../gl/FrameServer.h"

#define LOG_TAG "NATIVE_PIPELINE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// --- Global Variables for Renderer, Server, and Thread Safety ---

// 1. Pointer to our C++ OpenGL renderer class
std::unique_ptr<CameraRenderer> renderer;

// 2. Pointer to our C++ WebSocket server class
std::unique_ptr<FrameServer> server;

// 3. A mutex to safely handle the frame data for the local renderer
std::mutex frameMutex;

// 4. The latest processed frame from OpenCV that is ready to be rendered
cv::Mat processedFrame;


// --- JNI Functions for the App Lifecycle ---

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_MyGLRenderer_nativeOnSurfaceCreated(JNIEnv *env, jclass clazz) {
    LOGD("GL Surface created.");
    renderer = std::make_unique<CameraRenderer>();
    renderer->init();

    // Create and start the WebSocket server on port 9001
    LOGD("Starting WebSocket server...");
    server = std::make_unique<FrameServer>();
    server->start(9001);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_MyGLRenderer_nativeOnSurfaceChanged(JNIEnv *env, jclass clazz, jint width, jint height) {
    LOGD("GL Surface changed: %dx%d", width, height);
    if (renderer) {
        renderer->onSurfaceChanged(width, height);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_MyGLRenderer_nativeOnDrawFrame(JNIEnv *env, jclass clazz) {
    // This renders the frame locally on the Android device
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

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_NativeBridge_nativeOnDestroy(JNIEnv *env, jclass clazz) {
    // Call this from your Android Activity's onDestroy() to clean up
    LOGD("Shutting down WebSocket server.");
    if (server) {
        server->stop();
    }
}


// --- JNI Function for Camera Frame Processing and Broadcasting ---

extern "C" JNIEXPORT void JNICALL
Java_com_example_andro_NativeBridge_processFrame(
        JNIEnv *env,
        jclass,
        jbyteArray frameData,
        jint width,
        jint height) {

    // Get direct access to the YUV data from Java
    jbyte *data = env->GetByteArrayElements(frameData, nullptr);

    // Create a cv::Mat header for the YUV data without copying it
    cv::Mat yuv(height + height / 2, width, CV_8UC1, data);
    cv::Mat rgb;
    cv::cvtColor(yuv, rgb, cv::COLOR_YUV2RGB_NV21);

    // Grayscale conversion for Canny
    cv::Mat gray;
    cv::cvtColor(rgb, gray, cv::COLOR_RGB2GRAY);

    // Canny edge detection
    cv::Mat edges;
    cv::Canny(gray, edges, 100, 200);

    // --- REAL-TIME BROADCAST ---
    // Send the processed frame to all connected web clients
    if (server) {
        server->sendFrame(edges);
    }

    // --- LOCAL DISPLAY UPDATE ---
    // Safely update the frame for the local OpenGL renderer
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        processedFrame = edges;
    }

    // Release the byte array back to the JVM
    env->ReleaseByteArrayElements(frameData, data, JNI_ABORT);
}