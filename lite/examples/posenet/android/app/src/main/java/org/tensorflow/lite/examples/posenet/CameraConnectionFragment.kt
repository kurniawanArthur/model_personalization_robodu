/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.posenet

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.Person
import org.tensorflow.lite.examples.posenet.lib.Posenet

class CameraConnectionFragment :
  Fragment(),
  View.OnClickListener,
  ActivityCompat.OnRequestPermissionsResultCallback {

  /** An object for the Posenet library.    */
  private val posenet = Posenet()

  /** List of body joints that should be connected.    */
  val bodyJoints = listOf(
    Pair(BodyPart.LEFT_WRIST, BodyPart.LEFT_ELBOW),
    Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_SHOULDER),
    Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
    Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
    Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
    Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
    Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
    Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_SHOULDER),
    Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
    Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
    Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
    Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
  )

  /** Threshold for confidence score. */
  private val MIN_CONFIDENCE = 0.2

  /** Radius of circle used to draw keypoints.  */
  private val CIRCLE_RADIUS = 8.0f

  /** Paint class holds the style and color information to draw geometries,text and bitmaps. */
  private var paint = Paint()

  /** Preview shape for images.   */
  private val PREVIEW_WIDTH = 640
  private val PREVIEW_HEIGHT = 480

  /** ID of the current [CameraDevice].   */
  private var cameraId: String? = null

  /** A [SurfaceView] for camera preview.   */
  private var surfaceView: SurfaceView? = null

  /** A [CameraCaptureSession] for camera preview.   */
  private var captureSession: CameraCaptureSession? = null

  /** A reference to the opened [CameraDevice].    */
  private var cameraDevice: CameraDevice? = null

  /** The [android.util.Size] of camera preview.  */
  private var previewSize: Size? = null

  /** The [android.util.Size.getWidth] of camera preview. */
  protected var previewWidth = 0

  /** The [android.util.Size.getHeight] of camera preview.  */
  protected var previewHeight = 0

  /** A counter to keep count of total frames.  */
  private var frameCounter = 0

  /** An IntArray to save image data in ARGB8888 format  */
  private lateinit var rgbBytes: IntArray

  /** A ByteArray to save image data in YUV format  */
  private var yuvBytes = arrayOfNulls<ByteArray>(3)

  /** An Interpreter for TFLite model.   */
  private var interpreter: Interpreter? = null

  /** An additional thread for running tasks that shouldn't block the UI.   */
  private var backgroundThread: HandlerThread? = null

  /** A [Handler] for running tasks in the background.    */
  private var backgroundHandler: Handler? = null

  /** An [ImageReader] that handles preview frame capture.   */
  private var imageReader: ImageReader? = null

  /** [CaptureRequest.Builder] for the camera preview   */
  private var previewRequestBuilder: CaptureRequest.Builder? = null

  /** [CaptureRequest] generated by [.previewRequestBuilder   */
  private var previewRequest: CaptureRequest? = null

  /** A [Semaphore] to prevent the app from exiting before closing the camera.    */
  private val cameraOpenCloseLock = Semaphore(1)

  /** Whether the current camera device supports Flash or not.    */
  private var flashSupported = false

  /** Orientation of the camera sensor.   */
  private var sensorOrientation: Int? = null

  /** Abstract interface to someone holding a display surface.    */
  private var surfaceHolder: SurfaceHolder? = null

  /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.   */
  private val stateCallback = object : CameraDevice.StateCallback() {

    override fun onOpened(cameraDevice: CameraDevice) {
      cameraOpenCloseLock.release()
      this@CameraConnectionFragment.cameraDevice = cameraDevice
      createCameraPreviewSession()
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {
      cameraOpenCloseLock.release()
      cameraDevice.close()
      this@CameraConnectionFragment.cameraDevice = null
    }

    override fun onError(cameraDevice: CameraDevice, error: Int) {
      onDisconnected(cameraDevice)
      this@CameraConnectionFragment.activity?.finish()
    }
  }

  /**
   * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
   */
  private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureProgressed(
      session: CameraCaptureSession,
      request: CaptureRequest,
      partialResult: CaptureResult
    ) {
    }

    override fun onCaptureCompleted(
      session: CameraCaptureSession,
      request: CaptureRequest,
      result: TotalCaptureResult
    ) {
    }
  }

  /**
   * Shows a [Toast] on the UI thread.
   *
   * @param text The message to show
   */
  private fun showToast(text: String) {
    val activity = activity
    activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.fragment_camera2_basic, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.findViewById<View>(R.id.info).setOnClickListener(this)
    surfaceView = view.findViewById(R.id.surfaceView)
    surfaceHolder = surfaceView!!.holder
  }

  override fun onResume() {
    super.onResume()
    startBackgroundThread()
    interpreter = Interpreter(loadModelFile("posenet_model.tflite"))
  }

  override fun onStart() {
    super.onStart()

    openCamera()
  }

  override fun onPause() {
    closeCamera()
    stopBackgroundThread()
    super.onPause()
  }

  private fun requestCameraPermission() {
    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
      ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
    } else {
      requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }
  }

  private fun requestStoragePermission() {
    if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
    } else {
      requestPermissions(
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION
      )
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    if (requestCode == REQUEST_CAMERA_PERMISSION) {
      if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
        ErrorDialog.newInstance(getString(R.string.request_permission))
          .show(childFragmentManager, FRAGMENT_DIALOG)
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  fun setCamera(cameraId: String) {
    this.cameraId = cameraId
  }

  /**
   * Sets up member variables related to camera.
   */
  private fun setUpCameraOutputs() {

    val activity = activity
    val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      for (cameraId in manager.cameraIdList) {
        val characteristics = manager.getCameraCharacteristics(cameraId)

        // We don't use a front facing camera in this sample.
        val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
        if (cameraDirection != null &&
          cameraDirection == CameraCharacteristics.LENS_FACING_FRONT
        ) {
          continue
        }

        val map = characteristics.get(
          CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        ) ?: continue

        // For still image captures, we use the largest available size.
        val largest = Collections.max(
          Arrays.asList(*map.getOutputSizes(ImageFormat.YUV_420_888)),
          CompareSizesByArea()
        )

        previewSize = Size(640, 480)

        imageReader = ImageReader.newInstance(
          PREVIEW_WIDTH, PREVIEW_HEIGHT,
          ImageFormat.YUV_420_888, /*maxImages*/ 2
        )

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        val displayRotation = activity.windowManager.defaultDisplay.rotation

        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        previewHeight = previewSize!!.height
        previewWidth = previewSize!!.width

        // Initialize the storage bitmaps once when the resolution is known.
        rgbBytes = IntArray(previewWidth * previewHeight)

        // Check if the flash is supported.
        flashSupported =
          characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

        this.cameraId = cameraId

        // We've found a viable camera and finished setting up member variables,
        // so we don't need to iterate through other available cameras.
        return
      }
    } catch (e: CameraAccessException) {
      Log.e(TAG, e.toString())
    } catch (e: NullPointerException) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_error))
        .show(childFragmentManager, FRAGMENT_DIALOG)
    }
  }

  /**
   * Determines if the dimensions are swapped given the phone's current rotation.
   *
   * @param displayRotation The current rotation of the display
   *
   * @return true if the dimensions are swapped, false otherwise.
   */
  private fun areDimensionsSwapped(displayRotation: Int): Boolean {
    var swappedDimensions = false
    when (displayRotation) {
      Surface.ROTATION_0, Surface.ROTATION_180 -> {
        if (sensorOrientation == 90 || sensorOrientation == 270) {
          swappedDimensions = true
        }
      }
      Surface.ROTATION_90, Surface.ROTATION_270 -> {
        if (sensorOrientation == 0 || sensorOrientation == 180) {
          swappedDimensions = true
        }
      }
      else -> {
        Log.e(TAG, "Display rotation is invalid: $displayRotation")
      }
    }
    return swappedDimensions
  }

  /**
   * Opens the camera specified by [CameraConnectionFragment.cameraId].
   */
  private fun openCamera() {
    val permissionCamera = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
    if (permissionCamera != PackageManager.PERMISSION_GRANTED) {
      requestCameraPermission()
    } else {
      setUpCameraOutputs()
      val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      try {
        // Wait for camera to open - 2.5 seconds is sufficient
        if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
          throw RuntimeException("Time out waiting to lock camera opening.")
        }
        manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
      } catch (e: CameraAccessException) {
        Log.e(TAG, e.toString())
      } catch (e: InterruptedException) {
        throw RuntimeException("Interrupted while trying to lock camera opening.", e)
      }
    }
    val permissionStorage = ContextCompat.checkSelfPermission(
      activity!!,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
      requestStoragePermission()
    }
  }

  /**
   * Closes the current [CameraDevice].
   */
  private fun closeCamera() {
    if (captureSession == null) {
      return
    }

    try {
      cameraOpenCloseLock.acquire()
      captureSession!!.close()
      captureSession = null
      cameraDevice!!.close()
      cameraDevice = null
      imageReader!!.close()
      imageReader = null
    } catch (e: InterruptedException) {
      throw RuntimeException("Interrupted while trying to lock camera closing.", e)
    } finally {
      cameraOpenCloseLock.release()
    }
  }

  /**
   * Starts a background thread and its [Handler].
   */
  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("imageAvailableListener").also { it.start() }
    backgroundHandler = Handler(backgroundThread!!.looper)
  }

  /**
   * Stops the background thread and its [Handler].
   */
  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      Log.e(TAG, e.toString())
    }
  }

  /** Preload and memory map the model file, returning a MappedByteBuffer containing the model. */
  private fun loadModelFile(path: String): MappedByteBuffer {
    val fileDescriptor = this.context!!.assets.openFd(path)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    return inputStream.channel.map(
      FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength
    )
  }

  /** Fill the yuvBytes with data from image planes.   */
  private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
    // Row stride is the total number of bytes occupied in memory by a row of an image.
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (i in planes.indices) {
      val buffer = planes[i].buffer
      if (yuvBytes[i] == null) {
        yuvBytes[i] = ByteArray(buffer.capacity())
      }
      buffer.get(yuvBytes[i]!!)
    }
  }

  /** A [OnImageAvailableListener] to receive frames as they are available.  */
  private var imageAvailableListener = object : OnImageAvailableListener {
    override fun onImageAvailable(imageReader: ImageReader) {
      // We need wait until we have some size from onPreviewSizeChosen
      if (previewWidth == 0 || previewHeight == 0) {
        return
      }

      var image = imageReader.acquireLatestImage() ?: return
      fillBytes(image.planes, yuvBytes)

      ImageUtils.convertYUV420ToARGB8888(
        yuvBytes[0]!!,
        yuvBytes[1]!!,
        yuvBytes[2]!!,
        previewWidth,
        previewHeight,
        /*yRowStride=*/ image.planes[0].rowStride,
        /*uvRowStride=*/ image.planes[1].rowStride,
        /*uvPixelStride=*/ image.planes[1].pixelStride,
        rgbBytes
      )

      // Create bitmap from int array
      var imageBitmap = Bitmap.createBitmap(
        rgbBytes, previewWidth, previewHeight,
        Bitmap.Config.ARGB_8888
      )

      // Create rotated version for portrait display
      var rotateMatrix = Matrix()
      rotateMatrix.postRotate(90.0f)

      var rotatedBitmap = Bitmap.createBitmap(
        imageBitmap, 0, 0, previewWidth, previewHeight,
        rotateMatrix, true
      )

      // Save an image for analysis in every 30 frames.
      frameCounter += 1
      if (frameCounter % 30 == 0) {
        ImageUtils.saveBitmap(imageBitmap)
      }
      image.close()
      processImage(rotatedBitmap)
    }
  }

  /** Set the paint color and size.    */
  private fun setPaint() {
    paint.color = Color.RED
    paint.textSize = 40.0f
    paint.strokeWidth = 8.0f
  }

  /** Draw bitmap on Canvas.   */
  private fun draw(canvas: Canvas, person: Person, bitmap: Bitmap) {
    // TODO: crop bitmap to not squish it
    val screenWidth: Int = canvas.width
    val screenHeight: Int = canvas.height
    setPaint()
    canvas.drawBitmap(
      bitmap,
      Rect(0, 0, previewHeight, previewWidth),
      Rect(0, 0, screenWidth, screenHeight),
      paint
    )

    val widthRatio = screenWidth.toFloat() / MODEL_WIDTH
    val heightRatio = screenHeight.toFloat() / MODEL_HEIGHT

    // Draw key points over the image.
    for (keyPoint in person.keyPoints) {
      if (keyPoint.score > MIN_CONFIDENCE) {
        val position = keyPoint.position
        val adjustedX: Float = position.x.toFloat() * widthRatio
        val adjustedY: Float = position.y.toFloat() * heightRatio
        canvas.drawCircle(adjustedX, adjustedY, CIRCLE_RADIUS, paint)
      }
    }

    for (line in bodyJoints) {
      if (
        (person.keyPoints[line.first.ordinal].score > MIN_CONFIDENCE) and
        (person.keyPoints[line.second.ordinal].score > MIN_CONFIDENCE)
      ) {
        canvas.drawLine(
          person.keyPoints[line.first.ordinal].position.x.toFloat() * widthRatio,
          person.keyPoints[line.first.ordinal].position.y.toFloat() * heightRatio,
          person.keyPoints[line.second.ordinal].position.x.toFloat() * widthRatio,
          person.keyPoints[line.second.ordinal].position.y.toFloat() * heightRatio,
          paint
        )
      }
    }

    // Draw confidence score of a person.
    canvas.drawText(
      "%.2f".format(person.score),
      (200.0f * widthRatio),
      (200.0f * heightRatio),
      paint
    )

    // Draw!
    surfaceHolder!!.unlockCanvasAndPost(canvas)
  }

  /** Process image using Posenet library.   */
  private fun processImage(bitmap: Bitmap) {
    // Create scaled version of bitmap for model input
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_WIDTH, MODEL_HEIGHT, true)

    // Perform inference
    val person = posenet.estimateSinglePose(interpreter!!, scaledBitmap)
    val canvas: Canvas = surfaceHolder!!.lockCanvas()
    draw(canvas, person, bitmap)
  }

  /**
   * Creates a new [CameraCaptureSession] for camera preview.
   */
  private fun createCameraPreviewSession() {
    try {

      // We capture images from preview in YUV format
      imageReader = ImageReader.newInstance(
        previewSize!!.getWidth(), previewSize!!.getHeight(), ImageFormat.YUV_420_888, 2
      )
      imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

      // This is the surface we need to record images for processing
      val recordingSurface = imageReader!!.surface

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice!!.createCaptureRequest(
        CameraDevice.TEMPLATE_PREVIEW
      )
      previewRequestBuilder!!.addTarget(recordingSurface)

      // Here, we create a CameraCaptureSession for camera preview.
      cameraDevice!!.createCaptureSession(
        Arrays.asList(recordingSurface),
        object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            // The camera is already closed
            if (cameraDevice == null) return

            // When the session is ready, we start displaying the preview.
            captureSession = cameraCaptureSession
            try {
              // Auto focus should be continuous for camera preview.
              previewRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
              )
              // Flash is automatically enabled when necessary.
              setAutoFlash(previewRequestBuilder!!)

              // Finally, we start displaying the camera preview.
              previewRequest = previewRequestBuilder!!.build()
              captureSession!!.setRepeatingRequest(
                previewRequest!!,
                captureCallback, backgroundHandler
              )
            } catch (e: CameraAccessException) {
              Log.e(TAG, e.toString())
            }
          }

          override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            showToast("Failed")
          }
        },
        null
      )
    } catch (e: CameraAccessException) {
      Log.e(TAG, e.toString())
    }
  }

  override fun onClick(view: View) {
    when (view.id) {
      R.id.info -> {
        if (activity != null) {
          AlertDialog.Builder(activity)
            .setMessage(R.string.intro_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        }
      }
    }
  }

  private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
    if (flashSupported) {
      requestBuilder.set(
        CaptureRequest.CONTROL_AE_MODE,
        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
      )
    }
  }

  /** Compares two `Size`s based on their areas.  */
  internal class CompareSizesByArea : Comparator<Size> {
    override fun compare(lhs: Size, rhs: Size): Int {
      // We cast here to ensure the multiplications won't overflow
      return java.lang.Long.signum(
        lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
      )
    }
  }

  /**
   * Shows an error message dialog.
   */
  class ErrorDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
      AlertDialog.Builder(activity)
        .setMessage(arguments!!.getString(ARG_MESSAGE))
        .setPositiveButton(android.R.string.ok) { _, _ -> activity!!.finish() }
        .create()

    companion object {

      @JvmStatic
      private val ARG_MESSAGE = "message"

      @JvmStatic
      fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
        arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
      }
    }
  }

  companion object {
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private val ORIENTATIONS = SparseIntArray()
    private val FRAGMENT_DIALOG = "dialog"

    init {
      ORIENTATIONS.append(Surface.ROTATION_0, 90)
      ORIENTATIONS.append(Surface.ROTATION_90, 0)
      ORIENTATIONS.append(Surface.ROTATION_180, 270)
      ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    /**
     * Tag for the [Log].
     */
    private val TAG = "Camera2BasicFragment"

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private val MAX_PREVIEW_WIDTH = 1920

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private val MAX_PREVIEW_HEIGHT = 1080

    /**
     * Given `choices` of `Size`s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as
     * the respective max size, and whose aspect ratio matches with the specified value. If such
     * size doesn't exist, choose the largest one that is at most as large as the respective max
     * size, and whose aspect ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended
     *                          output class
     * @param textureViewWidth The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth The maximum width that can be chosen
     * @param maxHeight The maximum height that can be chosen
     * @param aspectRatio The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    @JvmStatic
    private fun chooseOptimalSize(
      choices: Array<Size>,
      textureViewWidth: Int,
      textureViewHeight: Int,
      maxWidth: Int,
      maxHeight: Int,
      aspectRatio: Size
    ): Size {

      // Collect the supported resolutions that are at least as big as the preview Surface
      val bigEnough = ArrayList<Size>()
      // Collect the supported resolutions that are smaller than the preview Surface
      val notBigEnough = ArrayList<Size>()
      val w = aspectRatio.width
      val h = aspectRatio.height
      for (option in choices) {
        if (option.width <= maxWidth && option.height <= maxHeight &&
          option.height == option.width * h / w
        ) {
          if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
            bigEnough.add(option)
          } else {
            notBigEnough.add(option)
          }
        }
      }

      // Pick the smallest of those big enough. If there is no one big enough, pick the
      // largest of those not big enough.
      if (bigEnough.size > 0) {
        return Collections.min(bigEnough, CompareSizesByArea())
      } else if (notBigEnough.size > 0) {
        return Collections.max(notBigEnough, CompareSizesByArea())
      } else {
        Log.e(TAG, "Couldn't find any suitable preview size")
        return choices[0]
      }
    }
  }
}
