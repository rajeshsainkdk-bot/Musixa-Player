package com.musixa.player

import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.WindowManager
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: AudioServiceActivity() {
    private val CHANNEL = "com.musixa.player/app_share"
    private var wakeLock: PowerManager.WakeLock? = null
    private var initialMediaUri: String? = null
    private var initialMediaMime: String? = null
    private var initialMediaName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(inIntent: Intent?) {
        if (inIntent == null) return
        val action = inIntent.action
        val data: Uri? = inIntent.data
        if (Intent.ACTION_VIEW == action && data != null) {
            initialMediaUri = data.toString()
            initialMediaMime = inIntent.type ?: contentResolver.getType(data)
            initialMediaName = resolveDisplayName(data)
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Media File"
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                }
            } catch (_: Exception) {}
        }
        return name
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val ctx: Context = applicationContext

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getInitialMediaUri" -> {
                    if (initialMediaUri != null) {
                        val uriStr = initialMediaUri ?: ""
                        val mime = initialMediaMime ?: ""
                        val isVideo = mime.startsWith("video") ||
                                uriStr.endsWith(".mp4", ignoreCase = true) ||
                                uriStr.endsWith(".mkv", ignoreCase = true) ||
                                uriStr.endsWith(".webm", ignoreCase = true) ||
                                uriStr.endsWith(".3gp", ignoreCase = true) ||
                                uriStr.endsWith(".avi", ignoreCase = true)
                        val isAudio = mime.startsWith("audio") ||
                                uriStr.endsWith(".mp3", ignoreCase = true) ||
                                uriStr.endsWith(".m4a", ignoreCase = true) ||
                                uriStr.endsWith(".wav", ignoreCase = true) ||
                                uriStr.endsWith(".aac", ignoreCase = true) ||
                                uriStr.endsWith(".flac", ignoreCase = true)

                        val map = mapOf(
                            "uri" to uriStr,
                            "mimeType" to mime,
                            "displayName" to (initialMediaName ?: "Media File"),
                            "isVideo" to isVideo,
                            "isAudio" to isAudio
                        )
                        // Clear after consumption so back navigation doesn't re-trigger
                        initialMediaUri = null
                        result.success(map)
                    } else {
                        result.success(null)
                    }
                }
                "queryDeviceVideos" -> {
                    try {
                        val videoList = mutableListOf<Map<String, Any?>>()
                        val projection = arrayOf(
                            MediaStore.Video.Media._ID,
                            MediaStore.Video.Media.TITLE,
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.DATA,
                            MediaStore.Video.Media.DURATION,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.RESOLUTION,
                            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                            MediaStore.Video.Media.DATE_ADDED
                        )
                        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        val cursor = ctx.contentResolver.query(
                            uri,
                            projection,
                            null,
                            null,
                            "${MediaStore.Video.Media.DATE_ADDED} DESC"
                        )
                        cursor?.use { c ->
                            val idCol = c.getColumnIndex(MediaStore.Video.Media._ID)
                            val titleCol = c.getColumnIndex(MediaStore.Video.Media.TITLE)
                            val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                            val pathCol = c.getColumnIndex(MediaStore.Video.Media.DATA)
                            val durCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                            val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                            val resCol = c.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
                            val bucketCol = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                            val dateCol = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                            while (c.moveToNext()) {
                                val id = if (idCol != -1) c.getLong(idCol) else 0L
                                val title = if (titleCol != -1) c.getString(titleCol) else null
                                val name = if (nameCol != -1) c.getString(nameCol) else null
                                val path = if (pathCol != -1) c.getString(pathCol) else ""
                                val duration = if (durCol != -1) c.getLong(durCol) else 0L
                                val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                                val resolution = if (resCol != -1) c.getString(resCol) else null
                                val folder = if (bucketCol != -1) c.getString(bucketCol) else null
                                val dateAdded = if (dateCol != -1) c.getLong(dateCol) else 0L

                                val item = mutableMapOf<String, Any?>()
                                item["id"] = id.toString()
                                item["title"] = title ?: name ?: "Video"
                                item["displayName"] = name ?: "Video"
                                item["path"] = path ?: ""
                                item["duration"] = duration
                                item["size"] = size
                                item["resolution"] = resolution ?: "HD"
                                item["folder"] = folder ?: "Videos"
                                item["dateAdded"] = dateAdded
                                videoList.add(item)
                            }
                        }
                        result.success(videoList)
                    } catch (e: Exception) {
                        result.error("VIDEO_QUERY_ERR", e.localizedMessage, null)
                    }
                }
                "openVideoWithApp" -> {
                    try {
                        val path = call.argument<String>("path") ?: ""
                        val idStr = call.argument<String>("id")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            if (!idStr.isNullOrEmpty() && idStr.toLongOrNull() != null) {
                                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idStr.toLong())
                                setDataAndType(uri, "video/*")
                            } else if (path.startsWith("content://")) {
                                setDataAndType(Uri.parse(path), "video/*")
                            } else {
                                setDataAndType(Uri.parse(path), "video/*")
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = Intent.createChooser(intent, "Play Video With")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(chooser)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("OPEN_VIDEO_ERR", e.localizedMessage, null)
                    }
                }
                "acquireWakeLock" -> {
                    try {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (wakeLock == null) {
                            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Musixa:BulkUploadWakeLock")
                        }
                        if (wakeLock?.isHeld != true) {
                            wakeLock?.acquire(45 * 60 * 1000L)
                        }
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("WAKELOCK_ERR", e.localizedMessage, null)
                    }
                }
                "releaseWakeLock" -> {
                    try {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        if (wakeLock?.isHeld == true) {
                            wakeLock?.release()
                        }
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("WAKELOCK_RELEASE_ERR", e.localizedMessage, null)
                    }
                }
                "showUploadNotification" -> {
                    try {
                        val title = call.argument<String>("title") ?: "Musixa Upload"
                        val message = call.argument<String>("message") ?: "Upload Completed!"
                        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val channelId = "musixa_upload_channel"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val channel = NotificationChannel(
                                channelId,
                                "Musixa Upload Status",
                                NotificationManager.IMPORTANCE_HIGH
                            )
                            notificationManager.createNotificationChannel(channel)
                        }
                        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Notification.Builder(ctx, channelId)
                        } else {
                            @Suppress("DEPRECATION")
                            Notification.Builder(ctx)
                        }
                        builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setAutoCancel(true)
                        notificationManager.notify(101, builder.build())
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("NOTIF_ERR", e.localizedMessage, null)
                    }
                }
                "shareApk" -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Musixa Player")
                            putExtra(Intent.EXTRA_TEXT, "Listen to high-res music & watch videos with AI Clarity on Musixa Player: https://play.google.com/store/apps/details?id=com.musixa.player")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Share Musixa Player").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(chooser)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SHARE_ERROR", e.localizedMessage, null)
                    }
                }
                "getAppVersion" -> {
                    try {
                        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode.toLong()
                        }
                        val versionName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
                        result.success(mapOf(
                            "versionCode" to pInfo,
                            "versionName" to versionName
                        ))
                    } catch (e: Exception) {
                        result.error("VERSION_ERROR", e.localizedMessage, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
}
