package br.com.baixador.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

class DownloadService : Service() {
    private val channel = "downloads"
    private val notificationId = 191
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService<NotificationManager>()?.createNotificationChannel(NotificationChannel(channel, getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, notification(getString(R.string.download_preparing), null))
        val url = intent?.getStringExtra("url")
        if (url.isNullOrBlank()) {
            DownloadState.busy.set(false); DownloadState.status.value = DisplayMessage(R.string.download_start_failed)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); return START_NOT_STICKY
        }
        val audio = intent.getBooleanExtra("audio", false)
        val quality = intent.getStringExtra("quality") ?: DownloadOptions.BEST
        val folder = intent.getStringExtra("folder")
        Thread { download(url, audio, quality, folder, startId) }.start()
        return START_NOT_STICKY
    }

    private fun notification(message: String, percent: Int?, file: Uri? = null): Notification {
        val open = if (file == null) Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        else Intent(Intent.ACTION_VIEW).setDataAndType(file, contentResolver.getType(file) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pending = PendingIntent.getActivity(this, if (file == null) 1 else 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("KuroFetch")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setAutoCancel(file != null)
            .setOngoing(file == null && DownloadState.busy.get())
        if (percent != null) builder.setProgress(100, percent, false)
        return builder.build()
    }

    private fun publish(message: DisplayMessage, percent: Int?) {
        DownloadState.progress.value = percent
        DownloadState.status.value = message
        val text = getString(message.resource, *message.arguments.toTypedArray())
        getSystemService<NotificationManager>()?.notify(notificationId, notification(text, percent))
    }

    private fun download(url: String, audio: Boolean, quality: String, folder: String?, startId: Int) {
        val temporary = File(cacheDir, "download-${UUID.randomUUID()}")
        var completed: Uri? = null
        try {
            temporary.mkdirs()
            installBundledYtDlp()
            YoutubeDL.init(applicationContext)
            FFmpeg.init(applicationContext)
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist").addOption("--newline").addOption("--no-mtime")
                .addOption("-P", temporary.absolutePath).addOption("-o", "%(title).150B [%(id)s].%(ext)s")
            if (audio) request.addOption("-x").addOption("--audio-format", "mp3").addOption("--audio-quality", DownloadOptions.bitrate(quality))
            else request.addOption("-f", DownloadOptions.selector(quality)).addOption("-S", "res,ext:mp4:m4a").addOption("--merge-output-format", "mp4")
            YoutubeDL.execute(request, "download", false) { percent, _, _ ->
                val p = percent.toInt().coerceIn(0, 100)
                publish(if (p == 100) DisplayMessage(R.string.download_preparing_file) else DisplayMessage(R.string.download_progress, listOf(p)), p)
            }
            publish(DisplayMessage(R.string.download_saving), null)
            val file = temporary.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.lastModified() } ?: throw IllegalStateException(getString(R.string.file_not_found))
            completed = saveFile(file, folder)
            DownloadState.result.value = completed.toString()
            DownloadState.busy.set(false)
            DownloadState.progress.value = 100
            DownloadState.status.value = DisplayMessage(R.string.download_complete)
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService<NotificationManager>()?.notify(notificationId, notification(getString(R.string.notification_complete), null, completed))
        } catch (e: Exception) {
            DownloadState.busy.set(false)
            val detail = e.message?.replace('\n', ' ')?.take(280).orEmpty()
            val msg = if (detail.isNotEmpty()) DisplayMessage(R.string.download_failed_details, listOf(detail)) else DisplayMessage(R.string.download_failed)
            DownloadState.status.value = msg
            DownloadState.progress.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService<NotificationManager>()?.notify(notificationId, notification(getString(msg.resource, *msg.arguments.toTypedArray()), null))
        } finally {
            temporary.deleteRecursively()
            stopSelf(startId)
        }
    }

    private fun installBundledYtDlp() {
        val directory = File(noBackupFilesDir, "youtubedl-android/yt-dlp")
        val binary = File(directory, "yt-dlp")
        val marker = File(directory, "bundled-version")
        val version = "2026.10.26"
        if (binary.isFile && marker.isFile && marker.readText() == version) return
        check(directory.isDirectory || directory.mkdirs()) { getString(R.string.ytdlp_prepare_failed) }
        val temporary = File(directory, "yt-dlp.new")
        resources.openRawResource(R.raw.ytdlp).use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
        check(temporary.renameTo(binary)) { getString(R.string.ytdlp_update_failed) }
        marker.writeText(version)
    }

    private fun saveFile(file: File, folder: String?): Uri {
        val mime = when (file.extension.lowercase()) { "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; else -> "application/octet-stream" }
        val tree = folder?.let(Uri::parse)
        val uri = if (tree != null) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(contentResolver, parent, mime, file.name) ?: throw IllegalStateException(getString(R.string.folder_create_failed))
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Baixador")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException(getString(R.string.downloads_create_failed))
        }
        try {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
                ?: throw IllegalStateException(getString(R.string.file_write_failed))
            if (tree == null) contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            throw e
        }
    }
}
