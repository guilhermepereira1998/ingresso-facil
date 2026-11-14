package com.wildrift.aicoach

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wildrift.aicoach.capture.ScreenCaptureService
import com.wildrift.aicoach.capture.MatchRecordingService
import com.wildrift.aicoach.training.TrainingStore
import com.wildrift.aicoach.training.TrainingArchive
import android.widget.CompoundButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import com.wildrift.aicoach.overlay.FloatingOverlayService
import com.wildrift.aicoach.ui.CalibrationActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvPermissionStatus: TextView
    private lateinit var tvTelemetryLog: TextView
    private lateinit var etLaneRole: EditText
    private lateinit var etCloudEndpoint: EditText
    private lateinit var etCloudToken: EditText
    private lateinit var swSaveTraining: CompoundButton
    private var pendingMode = "coach"
    private var exportBusy = false

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            exportBusy = true
            lifecycleScope.launch {
                var completed = false
                try {
                    val currentJob = coroutineContext
                    val count = withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri, "w")!!.use { out ->
                            TrainingArchive.write(TrainingStore.root(this@MainActivity), out) { currentJob.ensureActive() }
                        }
                    }
                    completed = true
                    Toast.makeText(this@MainActivity, "ZIP exportado: $count arquivos. Envie-o para preparar o treino.", Toast.LENGTH_LONG).show()
                } catch (cancel: CancellationException) { throw cancel
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Não foi possível exportar. Verifique o espaço no destino.", Toast.LENGTH_LONG).show()
                } finally {
                    if (!completed) runCatching { contentResolver.delete(uri, null, null) }
                    exportBusy = false
                }
            }
        }
    }

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionsUi() }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val video = pendingMode == "video"
            val captureIntent = Intent(this, if (video) MatchRecordingService::class.java else ScreenCaptureService::class.java).apply {
                action = if (video) MatchRecordingService.ACTION_START else ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_PLAYER_LANE, etLaneRole.text.toString().ifBlank { "Mid" })
                putExtra(ScreenCaptureService.EXTRA_CLOUD_URL, etCloudEndpoint.text.toString().trim())
                putExtra(ScreenCaptureService.EXTRA_CLOUD_TOKEN, etCloudToken.text.toString().trim())
                putExtra(ScreenCaptureService.EXTRA_SAVE_TRAINING, swSaveTraining.isChecked)
            }
            ContextCompat.startForegroundService(this, captureIntent)
            Toast.makeText(this, if (video) "Gravação iniciada. Abra o jogo; use Encerrar para salvar o MP4." else "AI Coach iniciado! Abra o Wild Rift.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Permissão de captura de tela negada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        tvTelemetryLog = findViewById(R.id.tvTelemetryLog)
        etLaneRole = findViewById(R.id.etLaneRole)
        etCloudEndpoint = findViewById(R.id.etCloudEndpoint)
        etCloudToken = findViewById(R.id.etCloudToken)
        swSaveTraining = findViewById(R.id.swSaveTraining)
        pendingMode = savedInstanceState?.getString("pending_mode") ?: "coach"

        val prefs = getSharedPreferences("coach_settings", MODE_PRIVATE)
        etLaneRole.setText(prefs.getString("lane", "Mid"))
        etCloudEndpoint.setText(prefs.getString("endpoint", ""))
        etCloudToken.setText(prefs.getString("cloud_token", ""))
        swSaveTraining.isChecked = prefs.getBoolean("save_training", false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        findViewById<Button>(R.id.btnOpenCalibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        findViewById<Button>(R.id.btnStartCoach).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Conceda primeiro a permissão de Overlay Flutuante.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ScreenCaptureService.isRunning) {
                Toast.makeText(this, "O coach já está ativo. Encerre antes de iniciar outra captura.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cloudUrl = etCloudEndpoint.text.toString().trim()
            if (cloudUrl.isNotBlank() && !cloudUrl.startsWith("https://")) {
                etCloudEndpoint.error = "Use uma URL HTTPS válida."
                return@setOnClickListener
            }
            if (cloudUrl.isNotBlank() && etCloudToken.text.toString().isBlank()) {
                etCloudToken.error = "Informe o token do seu servidor de IA."
                return@setOnClickListener
            }
            getSharedPreferences("coach_settings", MODE_PRIVATE).edit()
                .putString("lane", etLaneRole.text.toString())
                .putString("endpoint", cloudUrl)
                 .putString("cloud_token", etCloudToken.text.toString().trim())
                .putBoolean("save_training", swSaveTraining.isChecked).apply()
            stopService(Intent(this, MatchRecordingService::class.java))
            pendingMode = "coach"
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStartRecording).setOnClickListener {
            if (MatchRecordingService.isRecording) {
                Toast.makeText(this, "A gravação já está ativa.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, FloatingOverlayService::class.java))
            pendingMode = "video"
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
        }
        findViewById<Button>(R.id.btnSaveFrame).setOnClickListener {
            if (!ScreenCaptureService.isRunning) {
                Toast.makeText(this, "Inicie o coach e abra o jogo para salvar uma captura.", Toast.LENGTH_LONG).show()
            } else startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_SAVE_FRAME))
        }
        findViewById<Button>(R.id.btnExportTraining).setOnClickListener {
            if (exportBusy) return@setOnClickListener
            if (MatchRecordingService.isRecording || ScreenCaptureService.isRunning) {
                Toast.makeText(this, "Use Encerrar antes de exportar para concluir os arquivos.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            exportLauncher.launch("WildRiftCoach_training_${System.currentTimeMillis()}.zip")
        }

        findViewById<Button>(R.id.btnStopCoach).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, FloatingOverlayService::class.java))
            stopService(Intent(this, MatchRecordingService::class.java))
            tvTelemetryLog.text = "Status: Serviços encerrados."
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    tvTelemetryLog.text = "Telemetria: ${ScreenCaptureService.latestTelemetry}"
                    val summary = withContext(Dispatchers.IO) { TrainingStore.summary(this@MainActivity) }
                    findViewById<TextView>(R.id.tvCollectionStatus).text =
                        "${ScreenCaptureService.collectionStatus}\n${MatchRecordingService.latestStatus}\n$summary"
                    delay(2000L)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_mode", pendingMode)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsUi()
    }

    private fun refreshPermissionsUi() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        tvPermissionStatus.text = "Overlay: ${if (hasOverlay) "OK" else "Pendente"} | Notificações: ${if (hasNotif) "OK" else "Pendente"}"
    }
}
