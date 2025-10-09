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

/**
 * The main activity for the application.
 * This class handles UI setup, manages camera access using the Camera2 API,
 * and passes camera frames to a C++ native library for processing and rendering
 * via OpenGL.
 */
class MainActivity : AppCompatActivity() {

    // Declare UI and Camera2 API components
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val cameraID = "0" // Use the default back-facing camera

    // State for the toggle button
    private var isEdgeDetectionEnabled = true

    // --- Properties for FPS Calculation ---
    private lateinit var fpsTextView: TextView
    private var frameCount = 0
    private var lastFpsTimestamp = 0L

    /**
     * Called when the activity is first created.
     * Sets up the user interface and initializes the camera and rendering components.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- UI Setup ---
        // Create a FrameLayout to hold all UI components
        val frameLayout = FrameLayout(this)

        // Initialize GLSurfaceView for rendering OpenGL content (video frames)
        glSurfaceView = GLSurfaceView(this).apply {
            // Set the OpenGL ES version (2.0)
            setEGLContextClientVersion(2)
            // Assign the custom renderer
            setRenderer(MyGLRenderer())
            // Render on demand, not continuously, to save power
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        // Create the toggle button to switch between edge detection and raw feed
        val toggleButton = Button(this).apply {
            text = "Show Raw Feed"
            setOnClickListener {
                toggleProcessingMode(this)
            }
        }

        // --- Create the FPS TextView to display the frames per second ---
        fpsTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black background
            setPadding(16, 8, 16, 8)
        }

        // Add views to the main layout
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

        // Get the CameraManager system service and check for permissions
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        checkCameraPermissionAndOpen()
    }

    /**
     * Toggles the video processing mode between edge detection and raw feed.
     * Communicates the state change to the native C++ code via JNI.
     */
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

    /**
     * Listener for when a new camera image is available.
     * It processes the frame and updates the GLSurfaceView.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        // --- FPS Calculation Logic ---
        calculateFps()

        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            // Get the Y, U, and V planes from the YUV_420_888 image
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // Create a single ByteArray to hold the YUV data
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy the data from the buffers into the ByteArray
            yBuffer.get(nv21, 0, ySize)
            // Note: U and V planes might be interleaved in some formats, but this is a common approach
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)


            // Process the frame in C++ via the JNI bridge
            NativeBridge.processFrame(nv21, image.width, image.height)

            // After processing, request the GLSurfaceView to render a new frame
            glSurfaceView.requestRender()

        } finally {
            // Always close the image to release the buffer for the next frame
            image.close()
        }
    }

    /**
     * Calculates and updates the frames per second (FPS) counter.
     */
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

    /**
     * Checks for camera permission and opens the camera if granted.
     * Requests permission if not granted.
     */
    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                // Launch the permission request dialog
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) openCamera()
                }.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Opens the camera and sets up the capture session.
     */
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

    /**
     * Callback for camera state changes (e.g., opened, disconnected, error).
     */
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

    /**
     * Creates a camera capture session to continuously capture images.
     */
    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val previewSize = Size(1280, 720) // Use a common supported size for performance

        // Setup an ImageReader to capture YUV frames from the camera
        imageReader = ImageReader.newInstance(
            previewSize.width, previewSize.height,
            ImageFormat.YUV_420_888, 2
        )
        // Set the listener to process new images as they become available
        imageReader?.setOnImageAvailableListener(onImageAvailableListener, null)

        val imageReaderSurface = imageReader!!.surface

        // Create the capture session
        camera.createCaptureSession(listOf(imageReaderSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    // Create a request for a preview stream
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(imageReaderSurface) // The only target is our processor
                    // Start the repeating request to get a continuous stream of images
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
        // Resume the GLSurfaceView's rendering thread
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Pause the GLSurfaceView's rendering thread
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up camera and image resources
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        // Notify native code to perform cleanup
        NativeBridge.nativeOnDestroy()
    }
}