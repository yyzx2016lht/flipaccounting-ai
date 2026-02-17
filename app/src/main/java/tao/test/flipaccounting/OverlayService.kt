package tao.test.flipaccounting

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_foreground_channel"
        const val NOTIF_ID = 2001
        const val ACTION_SHOW_OVERLAY = "tao.test.flipaccounting.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "tao.test.flipaccounting.HIDE_OVERLAY"

        const val ACTION_START_FLIP = "ACTION_START_FLIP"
        const val ACTION_STOP_FLIP = "ACTION_STOP_FLIP"

        const val ACTION_START_BACK_TAP = "action_start_back_tap"
        const val ACTION_STOP_BACK_TAP = "action_stop_back_tap"
    }

    private lateinit var overlayManager: OverlayManager
    private var flipDetector: FlipDetector? = null


    private var isFlipEnabled = false
    private var isBackTapEnabled = false


    override fun onCreate() {
        super.onCreate()
        Logger.d(this, "OverlayService", "Service Created")

        // 使用 this (Service Context) 而不是 applicationContext，
        // 这样系统能正确将麦克风访问关联到这个前台服务。
        overlayManager = OverlayManager(this)
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            // API 34 (Android 14) 引入了 SPECIAL_USE
            if (Build.VERSION.SDK_INT >= 34) {
                 type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIF_ID, buildNotification("记账助手正在后台运行"), type)
        } else {
            startForeground(NOTIF_ID, buildNotification("记账助手正在后台运行"))
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }


        // 初始化状态
        isFlipEnabled = Prefs.isFlipEnabled(this)


        if (isFlipEnabled) startFlipDetection()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(this, "OverlayService", "onStartCommand: ${intent?.action}")
        if (intent == null) {
            // 服务被系统重启，恢复状态
            isFlipEnabled = Prefs.isFlipEnabled(this)

            if (isFlipEnabled) startFlipDetection()

        } else {
            when (intent.action) {
                ACTION_SHOW_OVERLAY -> overlayManager.showOverlay() // 按钮点击强制显示，无需检查白名单
                ACTION_HIDE_OVERLAY -> overlayManager.removeOverlay()

                ACTION_START_FLIP -> {
                    isFlipEnabled = true
                    startFlipDetection()
                }
                ACTION_STOP_FLIP -> {
                    isFlipEnabled = false
                    stopFlipDetection()
                }


            }
        }
        return START_STICKY
    }

    override fun onDestroy() {


        stopFlipDetection()

        overlayManager.removeOverlay()



        super.onDestroy()
    }

    // --- 核心逻辑：统一检查白名单 ---
    private fun checkAndShowOverlay() {
        // 1. 获取白名单和当前前台应用
        // 注意：ShizukuShell.getForegroundApp() 可能有轻微耗时，但在传感器回调线程中执行是可以的
        val whiteList = Prefs.getAppWhiteList(this)
        val currentApp = ShizukuShell.getForegroundApp() //

        Logger.d(this, "OverlayService", "Trigger Logic -> Current Foreground: $currentApp, Whitelist: $whiteList")

        // 2. 判断是否允许触发
        // 规则说明：
        // - 如果当前就在本 App 内，始终允许触发。
        // - 如果白名单不为空，则进入【严格模式】：只允许白名单内的应用触发。
        // - 如果白名单为空，为了避免刚装好就在微信等不相干应用中乱跳，我们默认进入【安全模式】：
        //   除非无法检测到当前应用（Shizuku未授权），否则不自动感应。这样用户必须手动在白名单添加应用后，感应才生效。
        
        val isAllowed = when {
            currentApp == packageName -> true
            whiteList.contains(currentApp) -> true
            whiteList.isEmpty() -> {
                // 如果白名单为空，且我们明确知道是在其他 App（如微信），则不允许触发
                // 但如果检测不到当前 App（currentApp == null），我们依然允许触发，作为兜底
                currentApp == null
            }
            else -> false
        }

        if (isAllowed) {
            // 3. 只有允许时，才震动
            triggerVibration()

            // 4. 切回主线程显示 UI
            Handler(Looper.getMainLooper()).post {
                overlayManager.showOverlay()
            }
        }
    }

    // --- Flip Detector ---
    private fun startFlipDetection() {
        if (flipDetector != null) return
        Logger.d(this, "OverlayService", "Starting FlipDetector")
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        flipDetector = FlipDetector(this, sensorManager) {
            Logger.d(this, "OverlayService", "Flip Triggered!")
            // 翻转成功，弹出悬浮窗
            checkAndShowOverlay()
        }
        flipDetector?.start()
    }

    private fun stopFlipDetection() {
        Logger.d(this, "OverlayService", "Stopping FlipDetector")
        flipDetector?.stop()
        flipDetector = null
    }



    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }

    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("翻转记账助手").setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_edit).setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "记账助手服务", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null
}