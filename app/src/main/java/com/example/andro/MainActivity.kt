package com.example.andro

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val cameraID = "0"

    // State for the toggle
    private var isEdgeDetectionEnabled = true

    // --- Properties for FPS Calculation ---
    private lateinit var fpsTextView: TextView
    private var frameCount = 0
    private var lastFpsTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- UI Setup ---
        val frameLayout = FrameLayout(this)

        // Initialize GLSurfaceView
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(MyGLRenderer())
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        // Create the toggle button
        val toggleButton = Button(this).apply {
            text = "Show Raw Feed"
            setOnClickListener {
                toggleProcessingMode(this)
            }
        }

        // --- Create the FPS TextView ---
        fpsTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black
            setPadding(16, 8, 16, 8)
        }

        // Add views to the layout
        frameLayout.addView(glSurfaceView)
        val buttonLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 48
        }
        frameLayout.addView(toggleButton, buttonLayoutParams)

        // --- Add FPS TextView to the top-left corner ---
        val fpsLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = 48
            leftMargin = 48
        }
        frameLayout.addView(fpsTextView, fpsLayoutParams)


        setContentView(frameLayout)
        // --- End UI Setup ---

        // Set the initial processing mode in C++ to match our state
        NativeBridge.setProcessingMode(1) // 1 for edge detection

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        checkCameraPermissionAndOpen()
    }

    private fun toggleProcessingMode(button: Button) {
        isEdgeDetectionEnabled = !isEdgeDetectionEnabled

        if (isEdgeDetectionEnabled) {
            button.text = "Show Raw Feed"
            NativeBridge.setProcessingMode(1) // 1 = Edge Detection
        } else {
            button.text = "Show Edges"
            NativeBridge.setProcessingMode(0) // 0 = Raw Feed
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        // --- FPS Calculation Logic ---
        calculateFps()

        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)


            // Process the frame in C++
            NativeBridge.processFrame(nv21, image.width, image.height)

            // After processing, request the GLSurfaceView to render a new frame
            glSurfaceView.requestRender()

        } finally {
            image.close()
        }
    }

    private fun calculateFps() {
        val currentTime = System.currentTimeMillis()
        if (lastFpsTimestamp == 0L) {
            lastFpsTimestamp = currentTime
        }

        frameCount++

        // Update the FPS counter approximately every second
        if (currentTime - lastFpsTimestamp >= 1000) {
            val fps = frameCount
            frameCount = 0
            lastFpsTimestamp = currentTime

            // Update the UI on the main thread
            runOnUiThread {
                fpsTextView.text = "FPS: $fps"
            }
        }
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) openCamera()
                }.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            cameraManager.openCamera(cameraID, cameraStateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e("MainActivity", "Cannot access camera", e)
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val previewSize = Size(1280, 720) // Use a common supported size

        imageReader = ImageReader.newInstance(
            previewSize.width, previewSize.height,
            ImageFormat.YUV_420_888, 2
        )
        imageReader?.setOnImageAvailableListener(onImageAvailableListener, null)

        val imageReaderSurface = imageReader!!.surface

        camera.createCaptureSession(listOf(imageReaderSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(imageReaderSurface) // The only target is our processor
                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                } catch (e: CameraAccessException) {
                    Log.e("MainActivity", "Failed to start repeating request", e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("MainActivity", "Failed to configure capture session")
            }
        }, null)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        NativeBridge.nativeOnDestroy()
    }
}

