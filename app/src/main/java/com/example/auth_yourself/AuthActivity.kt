package com.example.auth_yourself

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.auth_yourself.databinding.ActivityAuthBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.*

class AuthActivity : Activity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var imageReader: ImageReader

    private lateinit var textureView: TextureView

    private val imageHeight = 100
    private val imageWidth = 100

    private var captureFlg = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val timer = Timer()
        val task = object : TimerTask() {
            override fun run() {
                captureFlg = true
            }
        }

        timer.scheduleAtFixedRate(task, 0, 1000)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[1] // Use the first available camera

        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera(cameraId)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun openCamera(cameraId: String) {
        val cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraDevice.close()
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission if not granted
            return
        }

        cameraManager.openCamera(cameraId, cameraStateCallback, null)
    }

    private fun createCaptureSession() {
        val surfaceTexture = textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(textureView.width, textureView.height)
        val surface = Surface(surfaceTexture)

        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({
            val image = it.acquireLatestImage()

            if (captureFlg) {
                val timeStamp = SimpleDateFormat("yyyyMMdd_Hms", Locale.getDefault()).format(Date())
                val imageFileName = "JPEG_${timeStamp}_"
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
                val outputStream = FileOutputStream(imageFile)

                image?.let {
                    val buffer = it.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    outputStream.write(bytes)
                    it.close()
                    outputStream.close()
                    println("画像取得${imageFile}")
//                    val imagePath = storageDir.toString() + "/" + imageFile
                    GlobalScope.launch {
                        sendImage(imageFile.toString())
//                        sendImage("/Users/kiyama/AndroidStudioProjects/auth_yourself/app/src/main/res/drawable/kim.jpg")
                    }
                }
            }
            // Save the image or process it as needed
            image.close()
            captureFlg = false

        }, null)

        val captureStateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session

                val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.addTarget(surface)
                captureRequestBuilder.addTarget(imageReader.surface)

                captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)

                startImageCapture()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                // Handle configuration failure
            }
        }

        cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), captureStateCallback, null)
    }

    private fun startImageCapture() {
        println("startImageCapture start!!")
        val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(imageReader.surface)

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                // Image capture completed
                println("startImageCapture completed!! ${result}")
            }
        }

        captureSession.capture(captureRequestBuilder.build(), captureCallback, null)
    }

    suspend fun sendImage(imageFilePath: String) {
        val client = OkHttpClient()

        val apiKey = "B3O6iSeMzV1QaeqlhXKFv89DHtRCY1DPa02jVb68"
        val apiUrl = "https://3mzxwfdb6e.execute-api.ap-northeast-1.amazonaws.com/dev/"
//        imageFilePath = "PATH_TO_YOUR_IMAGE_FILE" // 画像ファイルのパス

        val imageFile = File(imageFilePath)
        val mediaType = "image/*".toMediaType()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", imageFile.name, imageFile.asRequestBody(mediaType))
            .build()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", apiKey)
            .post(requestBody)
            .build()

        val response: Response = client.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBody = response.body?.string()
            println("Response: $responseBody")
        } else {
            println("Error: ${response.code} - ${response.message}")
        }
    }
}
