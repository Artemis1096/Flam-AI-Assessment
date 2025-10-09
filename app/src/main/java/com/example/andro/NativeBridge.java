package com.example.andro;

public class NativeBridge {

    static {
        // It's good practice to load OpenCV first as your native library depends on it.
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    // Process camera frame (YUV NV21)
    public static native void processFrame(byte[] frameData, int width, int height);

    // Clean up native renderer resources
    public static native void nativeOnDestroy();

    // Sets the processing mode: 1 for edge detection, 0 for raw feed
    public static native void setProcessingMode(int mode);
}

