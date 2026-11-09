package com.musixa.player

import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: AudioServiceActivity() {
    private val CHANNEL = "com.msixa.player/app_share"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val ctx: Context = applicationContext

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
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
                            putExtra(Intent.EXTRA_TEXT, "Listen to high-res music & 10-band equalizer on Musixa Player: https://play.google.com/store/apps/details?id=com.musixa.player")
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
