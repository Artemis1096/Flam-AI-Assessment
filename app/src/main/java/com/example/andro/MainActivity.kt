package com.example.andro

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var imageReader: ImageReader
    private lateinit var previewSize: Size
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val CAMERA_REQUEST_CODE = 100
    private val TAG = "CameraApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)

        // Check and request permissions
        if (checkPermissions()) {
            textureView.surfaceTextureListener = surfaceTextureListener
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                textureView.surfaceTextureListener = surfaceTextureListener
                if (textureView.isAvailable) {
                    openCamera()
                }
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture available: ${width}x${height}")
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture size changed: ${width}x${height}")
        }
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "Surface texture destroyed")
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Called every frame - avoid logging here to prevent spam
        }
    }

    private fun openCamera() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = getCameraId(cameraManager)

            if (cameraId == null) {
                Toast.makeText(this, "No camera found", Toast.LENGTH_SHORT).show()
                return
            }

            if (!checkPermissions()) {
                requestPermissions()
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            if (map == null) {
                Log.e(TAG, "Cannot get stream configuration map")
                return
            }

            // Choose appropriate preview size
            val outputSizes = map.getOutputSizes(SurfaceTexture::class.java)
            previewSize = chooseOptimalSize(outputSizes, textureView.width, textureView.height)
            Log.d(TAG, "Selected preview size: ${previewSize.width}x${previewSize.height}")

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception", e)
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCameraId(cameraManager: CameraManager): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // Use back camera if available
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId
                }
            }
            // Fallback to first available camera
            return cameraManager.cameraIdList.firstOrNull()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error getting camera ID", e)
            return null
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int): Size {
        // Filter sizes that are at least as large as the texture view
        val bigEnough = mutableListOf<Size>()
        val notBigEnough = mutableListOf<Size>()

        for (option in choices) {
            if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                bigEnough.add(option)
            } else {
                notBigEnough.add(option)
            }
        }

        // Pick the smallest of the big enough ones
        return when {
            bigEnough.isNotEmpty() -> bigEnough.minByOrNull { it.width * it.height }!!
            notBigEnough.isNotEmpty() -> notBigEnough.maxByOrNull { it.width * it.height }!!
            else -> choices[0]
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened successfully")
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            cameraDevice?.close()
            cameraDevice = null

            val errorMsg = when(error) {
                ERROR_CAMERA_IN_USE -> "Camera is in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                ERROR_CAMERA_DISABLED -> "Camera disabled"
                ERROR_CAMERA_DEVICE -> "Camera device error"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown camera error"
            }

            runOnUiThread {
                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPreview() {
        try {
            val texture = textureView.surfaceTexture
            if (texture == null) {
                Log.e(TAG, "Surface texture is null")
                return
            }

            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)

            // Create ImageReader for raw YUV frames
            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                2
            )
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)

            val outputs = listOf(surface, imageReader.surface)

            cameraDevice?.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Capture session configured")
                    if (cameraDevice == null) return

                    captureSession = session

                    try {
                        val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        requestBuilder.addTarget(surface)
                        requestBuilder.addTarget(imageReader.surface)

                        // Set auto-focus and auto-exposure
                        requestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        Log.d(TAG, "Preview started successfully")

                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to start preview", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed")
                    Toast.makeText(this@MainActivity, "Failed to configure camera", Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception in startPreview", e)
        }
    }

    private var frameCount = 0
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image: Image? = reader.acquireLatestImage()

        if (image == null) {
            return@OnImageAvailableListener
        }

        try {
            // Log every 30 frames to avoid spam
            frameCount++
            if (frameCount % 30 == 0) {
                Log.d(TAG, "Processing frame #$frameCount - Format: ${image.format}, Size: ${image.width}x${image.height}")
            }

            // Extract YUV planes
            val planes = image.planes
            val y = planes[0].buffer
            val u = planes[1].buffer
            val v = planes[2].buffer

            val ySize = y.remaining()
            val uSize = u.remaining()
            val vSize = v.remaining()

            val yuvBytes = ByteArray(ySize + uSize + vSize)
            y.get(yuvBytes, 0, ySize)
            u.get(yuvBytes, ySize, uSize)
            v.get(yuvBytes, ySize + uSize, vSize)

            // TODO: Process raw YUV bytes here
            // - Send to ML model
            // - Convert to RGB/Bitmap
            // - Perform image processing
            // - etc.

            processYUVFrame(yuvBytes, image.width, image.height)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            image.close()
        }
    }

    private fun processYUVFrame(yuvBytes: ByteArray, width: Int, height: Int) {
        // Your custom processing logic here
        // Example: Calculate average brightness
        // val brightness = yuvBytes.take(width * height).average()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        if (textureView.isAvailable && checkPermissions()) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera resources", e)
        }

        stopBackgroundThread()
        super.onPause()
    }
}