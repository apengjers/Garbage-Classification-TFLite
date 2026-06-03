package com.apengjers.garbageclassification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.apengjers.garbageclassification.ml.FixedModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import android.widget.ProgressBar
import android.content.res.ColorStateList
import android.content.Intent

data class GarbageInfo(
    val label: String,
    val category: String,
    val description: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvLabel: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvDescription: TextView
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var progressConfidence: ProgressBar

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            takePicturePreview.launch(null)
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            imageView.setImageBitmap(it)
            classifyImage(it)
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(this.contentResolver, it)
                    ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                }
                imageView.setImageBitmap(bitmap)
                classifyImage(bitmap)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error picking image", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        tvLabel = findViewById(R.id.tvLabel)
        tvConfidence = findViewById(R.id.tvConfidence)
        progressConfidence = findViewById(R.id.progressConfidence)
        tvCategory = findViewById(R.id.tvCategory)
        tvDescription = findViewById(R.id.tvDescription)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        val btnTPS: Button = findViewById(R.id.btnTPS)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnGallery.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnTPS.setOnClickListener {
            val intent = Intent(this, TPSActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getConfidenceColor(confidence:Int): Int{
        return when {
            confidence >= 80 -> R.color.confidence_green
            confidence >= 60 -> R.color.confidence_yellow
            else -> R.color.confidence_red
        }
    }

    private fun getLabelText(confidence: Int, label: String): String{
        val formattedLabel = label.replaceFirstChar { it.uppercase() }
        return when {
            confidence >= 80 -> formattedLabel
            confidence >= 60 -> "Mungkin ${formattedLabel}"
            confidence >= 40 -> "Kemungkinan ${formattedLabel}"
            else -> "Tidak Yakin"
        }
    }

    private fun classifyImage(bitmap: Bitmap) {
        try {
            val model = FixedModel.newInstance(this)

            // Most TFLite models expect 224x224 and normalization (0 to 1).
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f)) // Normalisasi: nilai / 255.0
                .build()

            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // Runs model inference and gets result.
            // Using tensorImage.tensorBuffer as expected by the generated binding.
            val outputs = model.process(tensorImage.tensorBuffer)
            
            // Get the output. If probabilityAsCategoryList is not available, we use the raw buffer.
            val outputBuffer = outputs.outputFeature0AsTensorBuffer
            val scores = outputBuffer.floatArray
            
            val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: -1
            val maxScore = if (maxIndex != -1) scores[maxIndex] else 0f
            
            val garbageList = listOf(
                GarbageInfo("Baterai", "B3 (Berbahaya)", "Mengandung logam berat berbahaya. Harus dibuang di tempat pengumpulan khusus B3."),
                GarbageInfo("Biologis", "Organik", "Sisa makanan atau tanaman yang dapat membusuk. Bisa diolah menjadi kompos."),
                GarbageInfo("Kaca", "Anorganik", "Material pecah belah. Pastikan dibuang dalam keadaan bersih untuk didaur ulang."),
                GarbageInfo("Kardus", "Anorganik", "Kertas tebal yang bernilai tinggi untuk didaur ulang menjadi produk kertas baru."),
                GarbageInfo("Kertas", "Anorganik", "Dapat didaur ulang. Hindari mencampur kertas yang berminyak atau sangat kotor."),
                GarbageInfo("Logam", "Anorganik", "Seperti kaleng atau besi. Memiliki nilai ekonomi tinggi untuk didaur ulang kembali."),
                GarbageInfo("Pakaian", "Anorganik", "Tekstil bekas. Sebaiknya disumbangkan jika layak atau diolah menjadi kain lap/perca."),
                GarbageInfo("Plastik", "Anorganik", "Sulit terurai alami. Sangat penting untuk dipilah demi mengurangi polusi lingkungan."),
                GarbageInfo("Sampah Campuran", "Residu", "Berbagai jenis sampah yang sulit dipisahkan. Biasanya berakhir di TPA."),
                GarbageInfo("Sepatu", "Anorganik", "Terdiri dari material campuran. Bisa didaur ulang atau diperbaiki jika masih layak.")
            )


            if (maxIndex in garbageList.indices) {
                val info = garbageList[maxIndex]
                val confidence = (maxScore * 100).toInt()
                val labelText = getLabelText(confidence, info.label)
                val colorRes = getConfidenceColor(confidence)
                val color = ContextCompat.getColor(this, colorRes)

                tvLabel.text = labelText
                progressConfidence.progress = confidence
                tvConfidence.setTextColor(color)
                progressConfidence.progressTintList = ColorStateList.valueOf(color)

                if (confidence >= 40){
                    tvConfidence.text = "${confidence}%"
                    tvCategory.text = "Kategori: ${info.category}"
                    tvDescription.text = info.description
                } else {
                    tvConfidence.text = "-"
                    tvCategory.text = ""
                    tvDescription.text =
                        "Ambil foto kembali untuk mendapatkan hasil lebih akurat"
                }

            } else {
                tvLabel.text = "Tidak Dikenali"
                tvConfidence.text = "-"
                tvCategory.text = "-"
                tvDescription.text = "Objek tidak dapat dikenali (Index: $maxIndex)"
            }

            // Releases model resources if no longer used.
            model.close()
        } catch (e: Exception) {
            Log.e("MainActivity", "Classification failed", e)
            tvLabel.text = "Error"
            tvConfidence.text = "-"
            tvCategory.text = "-"
            tvDescription.text = e.message ?: "Terjadi kesalahan"
        }
    }

}