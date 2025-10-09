package com.example.andro

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyGLRenderer : GLSurfaceView.Renderer {

    companion object {
        init {
            // This is the conventional way to load the library in a companion object.
            System.loadLibrary("native-lib")
        }
    }

    // Native methods that are part of the rendering lifecycle.
    private external fun nativeOnSurfaceCreated()
    private external fun nativeOnSurfaceChanged(width: Int, height: Int)
    private external fun nativeOnDrawFrame()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        nativeOnSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        nativeOnSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        nativeOnDrawFrame()
    }
}

