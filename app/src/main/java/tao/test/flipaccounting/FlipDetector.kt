package tao.test.flipaccounting

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * 翻转检测器：通过加速度/重力传感器检测快速翻转动作
 */
class FlipDetector(
    private val manager: SensorManager,
    private val debounceMs: Long = 500L, // 两次触发之间的防抖时间
    private val onFlipChange: () -> Unit
) : SensorEventListener {

    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private enum class Face { UP, DOWN, UNKNOWN }
    private var lastFace = Face.UNKNOWN

    private var faceDownTime = 0L // 记录进入“面朝下”状态的时间点
    private var lastTriggerTime = 0L // 上次成功激发的时间点

    // --- 灵敏度调节参数 ---
    private val G_THRESHOLD = 7.5f    // 判定阈值：越接近 9.8 越严格（要求手机越平）
    private val MAX_FLIP_DURATION = 400L // 快速翻转定义：从下到上必须在 500ms 内完成

    fun start(): Boolean = sensor?.let {
        manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        true
    } ?: false

    fun stop() = manager.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val z = e.values[2]
        val now = SystemClock.uptimeMillis()

        // 1. 判定当前朝向
        val currentFace = when {
            z > G_THRESHOLD -> Face.UP
            z < -G_THRESHOLD -> Face.DOWN
            else -> Face.UNKNOWN
        }

        // 状态未改变或无效状态直接返回
        if (currentFace == Face.UNKNOWN || currentFace == lastFace) return

        // 2. 状态切换逻辑
        if (currentFace == Face.DOWN) {
            // 记录手机开始“面朝下”的时刻
            faceDownTime = now
        } else if (currentFace == Face.UP) {
            // 当检测到“面朝上”时，检查之前是否是“面朝下”
            if (lastFace == Face.DOWN) {
                val flipDuration = now - faceDownTime

                // 判断动作快慢：只有翻转过程耗时在设定范围内，且满足防抖时间
                if (flipDuration < MAX_FLIP_DURATION && (now - lastTriggerTime > debounceMs)) {
                    onFlipChange()
                    lastTriggerTime = now
                }
            }
        }

        lastFace = currentFace
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}