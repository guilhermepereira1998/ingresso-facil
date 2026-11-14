package com.wildrift.aicoach.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wildrift.aicoach.MainActivity
import com.wildrift.aicoach.R
import android.os.SystemClock
import com.wildrift.aicoach.model.GameStateSnapshot
import com.wildrift.aicoach.training.CollectionPolicy
import com.wildrift.aicoach.training.TrainingStore
import com.wildrift.aicoach.brain.TacticalBrain
import com.wildrift.aicoach.model.HudConfig
import com.wildrift.aicoach.overlay.FloatingOverlayService
import com.wildrift.aicoach.thermal.ThermalGuard
import com.wildrift.aicoach.vision.VisionPipeline
import com.wildrift.aicoach.voice.VoiceCoachManager
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.wildrift.aicoach.ACTION_START_CAPTURE"
        const val ACTION_STOP = "com.wildrift.aicoach.ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_PLAYER_LANE = "extra_player_lane"
        const val EXTRA_CLOUD_URL = "extra_cloud_url"
        const val EXTRA_CLOUD_TOKEN = "extra_cloud_token"
        const val EXTRA_SAVE_TRAINING = "save_training"
        const val ACTION_SAVE_FRAME = "com.wildrift.aicoach.SAVE_FRAME"
        @Volatile var collectionStatus = "Capturas automáticas desativadas"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wr_capture_channel"
        @Volatile var isPaused = false
        @Volatile var isRunning = false
        @Volatile var latestTelemetry = "Coach encerrado"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureJob: Job? = null
    private val readerLock = Any()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var destroyed = false
    @Volatile private var captureRequested = false
    private var saveTraining = false
    private var trainingStore: TrainingStore? = null
    private var lastSavedAt: Long? = null
    private var captureStartedAt = 0L
    private lateinit var visionPipeline: VisionPipeline
    private lateinit var tacticalBrain: TacticalBrain
    private lateinit var voiceCoach: VoiceCoachManager
    private lateinit var thermalGuard: ThermalGuard
    @Volatile private var captureIntervalMs = 1000L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (!destroyed) resizeCapture(width, height)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_capture_name), NotificationManager.IMPORTANCE_LOW)
        )
        visionPipeline = VisionPipeline(this)
        tacticalBrain = TacticalBrain()
        voiceCoach = VoiceCoachManager(this)
        thermalGuard = ThermalGuard(this) { interval, enableModel, _ ->
            captureIntervalMs = interval
            visionPipeline.isMinimapDetectionEnabled = enableModel
        }
        thermalGuard.register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_SAVE_FRAME) {
            captureRequested = true
            return START_NOT_STICKY
        }
        // A MediaProjection token authorizes exactly one capture session.
        if (isRunning) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            latestTelemetry = "Autorize uma nova captura para iniciar."
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(NOTIFICATION_ID, buildNotification())
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            saveTraining = intent.getBooleanExtra(EXTRA_SAVE_TRAINING, false)
            collectionStatus = if (saveTraining) "Capturas a cada 5s ativas" else "Capturas automáticas desativadas"
            captureStartedAt = SystemClock.elapsedRealtime()
            val metrics = resources.displayMetrics
            resizeCapture(metrics.widthPixels, metrics.heightPixels)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "WildRiftAICoachDisplay", captureWidth, captureHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
            ) ?: error("Projeção de tela indisponível")
            isPaused = false
            isRunning = true
            val overlay = Intent(this, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(overlay) else startService(overlay)
            startCaptureLoop(intent.getStringExtra(EXTRA_PLAYER_LANE) ?: "Mid", intent.getStringExtra(EXTRA_CLOUD_URL).orEmpty(), intent.getStringExtra(EXTRA_CLOUD_TOKEN).orEmpty())
        } catch (e: Exception) {
            latestTelemetry = "Falha ao iniciar captura: ${e.javaClass.simpleName}. Autorize novamente."
            Log.e("ScreenCaptureService", latestTelemetry, e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun resizeCapture(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val divisor = if (saveTraining) 1 else 2
        val w = (width / divisor).coerceAtLeast(1)
        val h = (height / divisor).coerceAtLeast(1)
        synchronized(readerLock) {
            if (w == captureWidth && h == captureHeight && imageReader != null) return
            val replacement = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            virtualDisplay?.resize(w, h, resources.displayMetrics.densityDpi)
            virtualDisplay?.surface = replacement.surface
            val previous = imageReader
            imageReader = replacement
            captureWidth = w
            captureHeight = h
            previous?.close()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < 34 && !destroyed) {
            val metrics = resources.displayMetrics
            resizeCapture(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun startCaptureLoop(lane: String, cloudUrl: String, cloudToken: String) {
        captureJob = serviceScope.launch {
            val hudConfig = HudConfig.load(this@ScreenCaptureService)
            while (isActive) {
                if (isPaused) { latestTelemetry = "Pausado"; voiceCoach.pause() } else voiceCoach.resume()
                val bitmap = if (!isPaused || captureRequested) acquireLatestBitmap() else null
                if (bitmap != null) {
                    val acquiredAtMs = System.currentTimeMillis()
                    val acquiredElapsedMs = SystemClock.elapsedRealtime()
                    try {
                        if (bitmap.width <= bitmap.height) {
                            latestTelemetry = "Abra o jogo na horizontal. ${visionPipeline.modelStatus}"
                        } else {
                            var snapshot: GameStateSnapshot? = null
                            if (!isPaused) {
                                try { snapshot = visionPipeline.processFrame(bitmap, hudConfig, lane) }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { latestTelemetry = "Falha no OCR; imagem de treino preservada se a coleta estiver ativa" }
                            }
                            maybeSaveTraining(bitmap, hudConfig, snapshot, lane, acquiredAtMs, acquiredElapsedMs)
                            if (snapshot != null && !isPaused) {
                                latestTelemetry = "${snapshot.formattedClock} | Ouro: ${snapshot.currentGold}\n${visionPipeline.modelStatus}\n${snapshot.rawOcrDebug}"
                                tacticalBrain.analyze(snapshot, cloudUrl, cloudToken) { calls ->
                                    calls.forEach { voiceCoach.enqueue(it) }
                                }.forEach { voiceCoach.enqueue(it) }
                                latestTelemetry += "\nIA: ${tacticalBrain.cloudStatus}"
                            }
                        }
                    } catch (cancel: CancellationException) { throw cancel
                    } catch (e: Exception) { latestTelemetry = "Falha na leitura: ${e.javaClass.simpleName}"
                    } finally { bitmap.recycle() }
                }
                delay(captureIntervalMs)
            }
        }
    }

    private suspend fun maybeSaveTraining(bitmap: Bitmap, config: HudConfig, snapshot: GameStateSnapshot?, lane: String, acquiredAtMs: Long, acquiredElapsedMs: Long) {
        val manual = captureRequested
        if (!manual && !saveTraining) return
        val now = acquiredElapsedMs
        val root = TrainingStore.root(this)
        if (!CollectionPolicy.shouldSave(now, lastSavedAt, saveTraining, manual, isPaused, trainingStore?.sampleCount ?: 0, root.usableSpace)) {
            if (manual || root.usableSpace < CollectionPolicy.RESERVE_BYTES || (trainingStore?.sampleCount ?: 0) >= CollectionPolicy.MAX_SAMPLES) {
                collectionStatus = "Coleta pausada: limite de amostras ou espaço livre atingido"
                captureRequested = false
            }
            return
        }
        captureRequested = false
        try {
            withContext(Dispatchers.IO) {
                val store = trainingStore ?: TrainingStore(this@ScreenCaptureService, "screenshots", lane).also { trainingStore = it }
                store.save(bitmap, config, snapshot, now - captureStartedAt, acquiredAtMs)
                collectionStatus = "${store.sampleCount} capturas PNG salvas nesta sessão"
            }
            lastSavedAt = now
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { collectionStatus = "Falha ao salvar captura. Verifique o espaço livre e tente exportar." }
    }

    private fun acquireLatestBitmap(): Bitmap? = synchronized(readerLock) {
        val image = imageReader?.acquireLatestImage() ?: return@synchronized null
        var raw: Bitmap? = null
        try {
            val plane = image.planes[0]
            val paddedWidth = image.width + (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
            val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            raw = bitmap
            bitmap.copyPixelsFromBuffer(plane.buffer)
            val crop = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            if (crop === bitmap) raw = null
            crop
        } catch (e: Exception) {
            Log.w("ScreenCaptureService", "Frame descartado", e)
            null
        } finally { raw?.recycle(); image.close() }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wild Rift AI Coach ativo")
            .setContentText("Captura de tela autorizada. Toque para abrir.")
            .setContentIntent(open).setSmallIcon(R.drawable.ic_coach).setOngoing(true)
            .addAction(R.drawable.ic_coach, "Encerrar", stop).build()
    }

    override fun onDestroy() {
        destroyed = true
        isRunning = false
        isPaused = false
        latestTelemetry = "Coach encerrado"
        stopService(Intent(this, FloatingOverlayService::class.java))
        thermalGuard.unregister()
        captureJob?.cancel()
        mediaProjection?.unregisterCallback(projectionCallback)
        synchronized(readerLock) {
            virtualDisplay?.release(); virtualDisplay = null
            imageReader?.close(); imageReader = null
        }
        mediaProjection?.stop(); mediaProjection = null
        voiceCoach.shutdown()
        // Await any outstanding ML Kit read before closing its recognizer.
        serviceScope.launch {
            captureJob?.join()
            visionPipeline.close()
            serviceScope.cancel()
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
