package com.example.andro;

public class NativeBridge {

    static {
        System.loadLibrary("native-lib"); // Must match the .so name built from C++
        System.loadLibrary("opencv_java4");
    }

    // This function will be implemented in native C++ code
    public static native void processFrame(byte[] frameData, int width, int height);
}
