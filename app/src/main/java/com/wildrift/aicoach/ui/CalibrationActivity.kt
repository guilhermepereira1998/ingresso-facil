package com.wildrift.aicoach.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wildrift.aicoach.R
import com.wildrift.aicoach.model.HudConfig
import com.wildrift.aicoach.vision.VisionPipeline
import kotlinx.coroutines.*

class CalibrationActivity : AppCompatActivity() {

    private lateinit var ivScreenshot: ImageView
    private lateinit var roiOverlayView: RoiOverlayView
    private lateinit var tvOcrDebugResult: TextView
    private lateinit var visionPipeline: VisionPipeline

    private var ocrJob: Job? = null
    private var busy = false
    private var loadedBitmap: Bitmap? = null
    private var currentConfig: HudConfig = HudConfig()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !busy) {
            try {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            loadedBitmap?.recycle()
            loadedBitmap = bmp
            ivScreenshot.setImageBitmap(bmp)
            } catch (_: Exception) {
                Toast.makeText(this, "Não foi possível abrir esta imagem.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        ivScreenshot = findViewById(R.id.ivScreenshot)
        roiOverlayView = findViewById(R.id.roiOverlayView)
        tvOcrDebugResult = findViewById(R.id.tvOcrDebugResult)
        visionPipeline = VisionPipeline(this)

        currentConfig = HudConfig.load(this)
        roiOverlayView.hudConfig = currentConfig
        roiOverlayView.onConfigChanged = { currentConfig = it }

        findViewById<Button>(R.id.btnPickScreenshot).setOnClickListener {
            if (!busy) pickImageLauncher.launch("image/*")
        }

        findViewById<SeekBar>(R.id.seekNotchOffset).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = (progress / 1000f)
                if (!fromUser) return
                currentConfig = currentConfig.copy(
                    minimap = HudConfig().withNotchOffset(offset).minimap,
                    currentGold = HudConfig().withNotchOffset(offset).currentGold
                )
                roiOverlayView.hudConfig = currentConfig
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnRunOcrTest).setOnClickListener {
            val bmp = loadedBitmap
            if (bmp == null) {
                Toast.makeText(this, "Selecione um screenshot primeiro.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (busy) return@setOnClickListener
            busy = true
            ocrJob = lifecycleScope.launch {
                try {
                val snap = visionPipeline.processFrame(bmp, currentConfig, "Mid")
                tvOcrDebugResult.text = buildString {
                    appendLine("Relógio: ${snap.formattedClock} (${snap.matchSeconds}s)")
                    appendLine("Ouro: ${snap.currentGold}")
                    appendLine("KDA: ${snap.rawKdaText}")
                    appendLine(visionPipeline.modelStatus)
                    appendLine("Minimapa: ${snap.minimapDetections.size} detecções")
                    appendLine("Bruto: ${snap.rawOcrDebug}")
                }
                } catch (cancel: CancellationException) { throw cancel
                } catch (_: Exception) {
                    tvOcrDebugResult.text = "Falha na leitura. Tente outro print e ajuste as áreas."
                } finally { busy = false }
            }
        }

        findViewById<Button>(R.id.btnSaveRoi).setOnClickListener {
            HudConfig.save(this, currentConfig)
            Toast.makeText(this, "Calibração ROI salva com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        ocrJob?.cancel()
        CoroutineScope(Dispatchers.Main).launch {
            ocrJob?.join()
            loadedBitmap?.recycle()
            visionPipeline.close()
        }
        super.onDestroy()
    }
}
