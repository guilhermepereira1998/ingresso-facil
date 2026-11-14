package com.wildrift.aicoach.capture

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.wildrift.aicoach.MainActivity
import com.wildrift.aicoach.R
import com.wildrift.aicoach.training.CollectionPolicy
import com.wildrift.aicoach.training.TrainingStore
import org.json.JSONObject
import java.io.File

/** Separate collection mode: one consent token and one virtual display per recording session. */
class MatchRecordingService : Service() {
    companion object {
        const val ACTION_START = "com.wildrift.aicoach.START_RECORDING"
        const val ACTION_STOP = "com.wildrift.aicoach.STOP_RECORDING"
        @Volatile var isRecording = false
        @Volatile var latestStatus = "Gravação encerrada"
    }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pendingFile: File? = null
    private var store: TrainingStore? = null
    private var width = 0
    private var height = 0
    private var segment = 0
    private var completedSegments = 0
    private var sessionStartMs = 0L
    private var segmentStartMs = 0L
    private var destroyed = false
    private var failure = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(w: Int, h: Int) {
            if (!destroyed && display != null) {
                try { resize(w, h) } catch (_: Exception) { fail("Falha ao adaptar gravação à tela") }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (isRecording) return START_NOT_STICKY
        val code = intent?.getIntExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(ScreenCaptureService.EXTRA_RESULT_DATA, Intent::class.java)
            else { @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(ScreenCaptureService.EXTRA_RESULT_DATA) }
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            startNotification()
            store = TrainingStore(this, "video", intent.getStringExtra(ScreenCaptureService.EXTRA_PLAYER_LANE) ?: "Mid")
            sessionStartMs = SystemClock.elapsedRealtime()
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            projection?.registerCallback(callback, Handler(Looper.getMainLooper()))
            val metrics = resources.displayMetrics
            prepareSegment(metrics.widthPixels, metrics.heightPixels)
            display = projection?.createVirtualDisplay("WildRiftTrainingVideo", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder?.surface, null, null)
                ?: error("Captura indisponível")
            recorder?.start()
            segmentStartMs = SystemClock.elapsedRealtime() - sessionStartMs
            isRecording = true
            latestStatus = "Gravando MP4 sem áudio. Segmento $segment"
        } catch (_: Exception) { fail("Não foi possível iniciar o vídeo. Autorize a captura novamente e verifique o espaço livre.") }
        return START_NOT_STICKY
    }

    private fun prepareSegment(captureWidth: Int, captureHeight: Int) {
        val folder = requireNotNull(store).directory
        val available = folder.usableSpace - CollectionPolicy.RESERVE_BYTES
        check(available > 16L * 1024 * 1024) { "Espaço insuficiente" }
        val size = CollectionPolicy.videoSize(captureWidth, captureHeight)
        width = size.first; height = size.second
        segment++
        segmentStartMs = SystemClock.elapsedRealtime() - sessionStartMs
        val file = File(folder, "match_${segment.toString().padStart(3, '0')}.mp4.part")
        pendingFile = file
        @Suppress("DEPRECATION")
        val engine = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        recorder = engine
        engine.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        engine.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        engine.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        engine.setVideoSize(width, height)
        engine.setVideoFrameRate(24)
        engine.setVideoEncodingBitRate(4_000_000)
        engine.setOutputFile(file.absolutePath)
        engine.setMaxFileSize(minOf(1_900_000_000L, available))
        engine.setOnErrorListener { _, _, _ -> fail("A gravação foi interrompida pelo codificador") }
        engine.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                latestStatus = "Limite de espaço/tamanho atingido; gravação encerrada"
                failure = true
                stopSelf()
            }
        }
        engine.prepare()
    }

    private fun resize(w: Int, h: Int) {
        val size = CollectionPolicy.videoSize(w, h)
        if (size.first == width && size.second == height) return
        display?.surface = null
        finishSegment()
        prepareSegment(w, h)
        display?.resize(width, height, resources.displayMetrics.densityDpi)
        display?.surface = recorder?.surface
        recorder?.start()
        segmentStartMs = SystemClock.elapsedRealtime() - sessionStartMs
        latestStatus = "Gravando MP4 sem áudio. Segmento $segment"
    }

    private fun finishSegment() {
        val engine = recorder ?: return
        recorder = null
        val pending = pendingFile
        pendingFile = null
        var valid = false
        try { engine.stop(); valid = pending != null && pending.length() > 0 }
        catch (_: RuntimeException) { /* A too-short/interrupted segment cannot be used for training. */ }
        finally { runCatching { engine.reset() }; runCatching { engine.release() } }
        if (valid && pending != null) {
            val done = File(pending.parentFile, pending.name.removeSuffix(".part"))
            if (pending.renameTo(done)) {
                completedSegments++
                File(done.parentFile, done.name + ".json").writeText(JSONObject().apply {
                    put("schemaVersion", 1); put("file", done.name); put("sessionId", store?.sessionId)
                    put("segmentStartMs", segmentStartMs); put("segmentEndMs", SystemClock.elapsedRealtime() - sessionStartMs)
                    put("width", width); put("height", height); put("audio", false)
                    put("annotationsAreGroundTruth", false)
                }.toString(2))
            }
        } else pending?.delete()
    }

    private fun fail(message: String) { failure = true; latestStatus = message; stopSelf() }

    private fun startNotification() {
        val channel = "wr_training_video"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Gravação de partidas", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 40, Intent(this, MatchRecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 41, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_coach)
            .setContentTitle("Gravando partida para treinamento")
            .setContentText("Vídeo salvo no aparelho. Toque em Encerrar para concluir o MP4.")
            .setContentIntent(open).setOngoing(true).addAction(R.drawable.ic_coach, "Encerrar", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1003, notification)
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        if (Build.VERSION.SDK_INT < 34 && display != null && !destroyed) {
            val metrics = resources.displayMetrics
            try { resize(metrics.widthPixels, metrics.heightPixels) } catch (_: Exception) { fail("Falha ao girar a gravação") }
        }
    }
    override fun onDestroy() {
        destroyed = true
        isRecording = false
        projection?.unregisterCallback(callback)
        runCatching { display?.surface = null }
        try { finishSegment() } catch (_: Exception) { failure = true; latestStatus = "Falha ao concluir o vídeo" }
        display?.release(); display = null
        projection?.stop(); projection = null
        if (!failure) latestStatus = if (completedSegments > 0)
            "$completedSegments segmento(s) MP4 salvo(s). Use Exportar material de treino."
            else "Nenhum vídeo válido. Grave por alguns segundos antes de encerrar."
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
