package com.yd.signai

import android.speech.tts.TextToSpeech
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var targetLangSpinners: Spinner
    private lateinit var previewView: PreviewView
    private lateinit var gestureText: TextView
    private lateinit var gestureText2: TextView
    private lateinit var overlayView: OverlayView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var gestureRecognizer: GestureRecognizer
    private lateinit var gestureDetector: GestureDetector
    private var detectedGesture: String? = null
    private lateinit var tts: TextToSpeech
    private var isFrontCamera = true
    private lateinit var gestureRecyclerView: RecyclerView
    private lateinit var gestureAdapter: GestureAdapter
    private val gestureList = mutableListOf<Gesture>()
    private var currentGesture: Gesture = Gesture()
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tts = TextToSpeech(this, this)
        previewView = findViewById(R.id.previewView)
        gestureText = findViewById(R.id.gestureText)
        overlayView = findViewById(R.id.overlayView)

        gestureRecyclerView = findViewById(R.id.recycleviewid)
        gestureRecyclerView.layoutManager = LinearLayoutManager(this)

        val myButton: ImageButton = findViewById(R.id.switchCameraButton)
        myButton.setOnClickListener {
            switchCamera() // Panggil callback untuk switch camera
        }

        // Daftar bahasa
        val languages = mapOf(
            "Indonesia" to "id",
            "Inggris" to "en",
            "Jerman" to "de",
            "Prancis" to "fr",
            "Spanyol" to "es",
            "Japanese" to "ja"
        )

        // Inisialisasi GestureAdapter dengan daftar bahasa
        // Inisialisasi GestureAdapter dengan daftar bahasa
        gestureAdapter = GestureAdapter(currentGesture, languages, ::translateText, ::speak) {
            // Logika untuk reset gesture
            currentGesture = Gesture() // Reset gesture ke default
            gestureAdapter.updateGesture(
                currentGesture.detectedGesture,
                currentGesture.translatedText
            ) // Update adapter
            sentence = "" // Reset kalimat
            lastDetectedGesture = "" // Reset last detected gesture
        }

        gestureRecyclerView.adapter = gestureAdapter


        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup GestureDetector untuk swipe up detection
//        gestureDetector = GestureDetector(this, GestureListener())
//        previewView.setOnTouchListener { _, event ->
//            gestureDetector.onTouchEvent(event)
//            true  // Pastikan event diteruskan
//        }


        // Meminta izin kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            startGestureRecognition()
        } else {
            startGestureRecognition()
        }
    }

    private fun switchCamera() {
        isFrontCamera = !isFrontCamera // Toggle camera state
        startCamera() // Restart camera to apply changes
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set bahasa default untuk TextToSpeech (misalnya bahasa Indonesia)
            val result = tts.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Bahasa tidak didukung!")
            }
        } else {
            Log.e("TTS", "Inisialisasi TextToSpeech gagal!")
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun startGestureRecognition() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("kalimat2.task") // Ganti dengan path model .task Anda
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setResultListener { result: GestureRecognizerResult, mpImage: MPImage ->
                handleGestureResult(result, mpImage)
            }
            .build()

        gestureRecognizer = GestureRecognizer.createFromOptions(this, options)
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor, { imageProxy ->
                    analyzeImage(imageProxy, isFrontCamera) // Kirim status kamera ke analyzeImage
                })
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val frameTime = SystemClock.uptimeMillis()
        val bitmap = imageProxyToBitmap(imageProxy)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) {
                bitmap?.let {
                    postScale(-1f, 1f) // Membalik gambar jika menggunakan kamera depan
                }
            }
        }

        val rotatedBitmap = bitmap?.let {
            Bitmap.createBitmap(it, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        gestureRecognizer.recognizeAsync(mpImage, frameTime)
        imageProxy.close()
    }


    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
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

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private var lastDetectedGesture: String? = null // Simpan gesture terakhir yang terdeteksi

    private var sentence = ""  // Untuk menyimpan kalimat
    private val handler = Handler(Looper.getMainLooper())
    private var delayJob: Runnable? = null

    private fun handleGestureResult(result: GestureRecognizerResult, mpImage: MPImage) {
        if (result.gestures().isNotEmpty()) {
            val gestureName = result.gestures()[0][0].categoryName()

            runOnUiThread {
                gestureText.text = "Detected Gesture: $gestureName"
                detectedGesture = gestureName

                // Perbarui overlay dengan hasil dan dimensi gambar
                overlayView.setResults(result, mpImage.height, mpImage.width, RunningMode.LIVE_STREAM)

                // Reset kalimat jika ada gesture baru
                if (gestureName != lastDetectedGesture) {
                    lastDetectedGesture = gestureName

                    // Batalkan delay jika ada gesture baru
                    delayJob?.let { handler.removeCallbacks(it) }

                    // Set delay untuk memperbarui kalimat pada RecyclerView
                    delayJob = Runnable {
                        sentence += "$gestureName "  // Tambah ke kalimat
                        gestureAdapter.updateGesture(sentence.trim(), "--")  // Perbarui kalimat di adapter
                    }
                    handler.postDelayed(delayJob!!, 2000)  // Jeda 2 detik
                }
            }
        } else {
            // Jika tidak ada gesture terdeteksi, bersihkan overlay
            runOnUiThread {
                overlayView.clear() // Bersihkan hasil gesture
            }
        }
    }





    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGestureRecognition()
            } else {
                gestureText.text = "Camera permission denied"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gestureRecognizer.close()
        cameraExecutor.shutdown()
    }

    // Tambahkan GestureListener untuk mendeteksi swipe up
//    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
//        private val SWIPE_THRESHOLD = 100
//        private val SWIPE_VELOCITY_THRESHOLD = 100
//
//        override fun onFling(
//            e1: MotionEvent?,
//            e2: MotionEvent,
//            velocityX: Float,
//            velocityY: Float
//        ): Boolean {
//            if (e1 != null) {
//                val diffY = e2.y - e1.y
//                val diffX = e2.x - e1.x
//                if (abs(diffY) > abs(diffX)) {
//                    if (abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
//                        if (diffY < 0) {
//                            // Swipe up detected, gunakan detectedGesture
//                            detectedGesture?.let { gestureName ->
//                                showBottomSheet(gestureName)
//                            } ?: run {
//                                // Jika gesture belum terdeteksi, gunakan fallback message
//                                showBottomSheet("No gesture detected")
//                            }
//                        }
//                    }
//                }
//            }
//            return true
//        }
//    }


    private var bottomSheetDialog: BottomSheetDialog? = null
    private var gestureResultTextView: TextView? = null
    private lateinit var gestureResultTextTranslate: TextView
    // Fungsi untuk menampilkan BottomSheetDialog
    // Fungsi untuk menampilkan BottomSheetDialog dan menampilkan gesture yang terdeteksi
//    private fun showBottomSheet(s: String) {
//        if (bottomSheetDialog == null) {
//            bottomSheetDialog = BottomSheetDialog(this)
//            val view = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)
//
//            // Temukan view dari layout yang di-inflate
//            targetLangSpinners = view.findViewById(R.id.targetLangSpinner)
//            gestureResultTextView = view.findViewById(R.id.gestureResultText)
//            gestureResultTextTranslate = view.findViewById(R.id.gestureResultTextTranslate)
//
//            // Daftar bahasa
//            val languages = mapOf(
//                "Indonesia" to "id",
//                "Inggris" to "en",
//                "Jerman" to "de",
//                "Prancis" to "fr",
//                "Spanyol" to "es"
//            )
//            val adapter = ArrayAdapter(
//                this,
//                android.R.layout.simple_spinner_item,
//                languages.keys.toList()
//            )
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//            targetLangSpinners.adapter = adapter
//
//            // Referensi tombol
//            val translateButton = view.findViewById<Button>(R.id.translateButton)
//            val playVoiceButton = view.findViewById<Button>(R.id.playVoiceButton)
//
//            // Tombol translate
////            translateButton.setOnClickListener {
////                val textToTranslate = gestureResultTextView?.text.toString()
////                val targetLang = languages[targetLangSpinners.selectedItem.toString()]
////                if (targetLang != null) {
////                    translateText(textToTranslate, targetLang) { translatedText ->
////                        runOnUiThread {
////                            gestureResultTextTranslate.text = translatedText  // Update TextView hasil translate
////                            gestureAdapter.updateGesture(textToTranslate, translatedText)
////                        }
////                    }
////                }
////            }
//
//            // Tombol untuk memutar suara hasil terjemahan
//            playVoiceButton.setOnClickListener {
//                val textToSpeak = gestureResultTextTranslate.text.toString()  // Memutar teks dari hasil translate
//                speak(textToSpeak)  // Panggil fungsi Text-to-Speech
//            }
//
//            // Set content view untuk bottom sheet dialog
//            bottomSheetDialog?.setContentView(view)
//        }
//
//        // Tampilkan bottom sheet dialog
//        bottomSheetDialog?.show()
//    }

//    private fun translateText(text: String, callback: (String) -> Unit) {
//        // Logika untuk melakukan translate, misalnya menggunakan Google Translate API
//        // Contoh pseudo-code:
//        val translatedText = text  // Ganti dengan hasil dari API penerjemahan
//
//        callback(translatedText)  // Panggil callback dengan teks yang sudah diterjemahkan
//    }

    private fun translateText(text: String, targetLang: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // URL API yang disesuaikan dengan bahasa yang dipilih
                val url =
                    "https://655.mtis.workers.dev/translate?text=$text&source_lang=en&target_lang=$targetLang"

                // Menggunakan OkHttp untuk request GET
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .build()

                // Mengambil respons dari API
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // Tangani jika respons tidak berhasil
                    throw Exception("Error: ${response.code}")
                }

                val responseData = response.body?.string()
                // Parsing JSON jika respons valid
                val json = JSONObject(responseData)
                val translatedText = json.getJSONObject("response").getString("translated_text")

                // Update UI di main thread
                withContext(Dispatchers.Main) {
                    gestureAdapter.updateGesture(text, translatedText)
                }

            } catch (e: Exception) {
                // Tangkap error, dan kirim pesan error ke callback
                withContext(Dispatchers.Main) {
                    gestureAdapter.updateGesture(text, "Error: ${e.message}") // Menampilkan error di UI
                }
            }
        }
    }




}
