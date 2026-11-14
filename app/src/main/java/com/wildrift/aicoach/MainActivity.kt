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

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionsUi() }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_PLAYER_LANE, etLaneRole.text.toString().ifBlank { "Mid" })
                putExtra(ScreenCaptureService.EXTRA_CLOUD_URL, etCloudEndpoint.text.toString().trim())
                putExtra(ScreenCaptureService.EXTRA_CLOUD_TOKEN, etCloudToken.text.toString().trim())
            }
            ContextCompat.startForegroundService(this, captureIntent)
            Toast.makeText(this, "AI Coach iniciado! Abra o Wild Rift.", Toast.LENGTH_LONG).show()
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

        val prefs = getSharedPreferences("coach_settings", MODE_PRIVATE)
        etLaneRole.setText(prefs.getString("lane", "Mid"))
        etCloudEndpoint.setText(prefs.getString("endpoint", ""))
        etCloudToken.setText(prefs.getString("cloud_token", ""))
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
                .putString("cloud_token", etCloudToken.text.toString().trim()).apply()
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStopCoach).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, FloatingOverlayService::class.java))
            tvTelemetryLog.text = "Status: Serviços encerrados."
        }

        lifecycleScope.launch {
            while (isActive) {
                tvTelemetryLog.text = "Telemetria Live: ${ScreenCaptureService.latestTelemetry}"
                delay(1000L)
            }
        }
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
