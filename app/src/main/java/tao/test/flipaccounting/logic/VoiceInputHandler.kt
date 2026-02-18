package tao.test.flipaccounting.logic

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tao.test.flipaccounting.AIService
import tao.test.flipaccounting.AiAssistant
import tao.test.flipaccounting.Utils
import java.io.File

class VoiceInputHandler(
    private val ctx: Context,
    private val aiAssistant: AiAssistant,
    private val isMultiModeProvider: () -> Boolean, // [新增] 提供当前是单笔还是多笔模式
    private val onResult: (JSONObject) -> Unit
) {
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var audioFile: File? = null
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isRecording = false
    private var isWannaCancel = false // [新增] 是否进入取消状态

    fun setupVoiceButton(btnVoice: View) {
        btnVoice.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 检查麦克风权限
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            Utils.toast(ctx, "使用语音功能需要麦克风权限")
                            // 注意：在悬浮窗中申请 Activity 权限比较复杂，
                            // 通常引导用户回主界面，或者在 OverlayService 启动时预判，
                            // 这里我们简单提示。
                            return@setOnTouchListener true
                        }
                    }

                    // 1. 立即缩放动画，即使是短按也有反馈
                    v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).start()
                    
                    // 2. 延迟判断长按
                    isRecording = false
                    isWannaCancel = false
                    handler.postDelayed({
                        isRecording = true
                        // 触发震动反馈：长按确认进入录音
                        Utils.vibrate(ctx) 
                        
                        aiAssistant.showInputPanel(
                            mode = AiAssistant.MODE_RECORDING, 
                            isMultiMode = isMultiModeProvider()
                        ) { resultJson ->
                            onResult(resultJson)
                        }

                        try {
                            startRecording()
                        } catch (e: Exception) {
                            aiAssistant.dismiss()
                            Utils.toast(ctx, "录音启动失败")
                            isRecording = false
                        }
                    }, 200) // 200ms 作为触发阈值
                    true
                }
                MotionEvent.ACTION_MOVE -> { // [新增] 上滑取消逻辑
                    if (isRecording) {
                        // 如果手指上滑超过阈值（例如 150 像素），标记为取消
                        // 因为 btnVoice 可能布局在底部，负值 y 代表手指移出了 View 上边缘
                        if (event.y < -150f) {
                            if (!isWannaCancel) {
                                isWannaCancel = true
                                Utils.vibrate(ctx, 30) // 触感反馈：进入取消区
                                aiAssistant.showInputPanel(
                                    mode = AiAssistant.MODE_CANCEL, 
                                    isMultiMode = isMultiModeProvider()
                                ) { onResult(it) }
                            }
                        } else {
                            if (isWannaCancel) {
                                isWannaCancel = false
                                Utils.vibrate(ctx, 10) // 触感反馈：回到录音区
                                aiAssistant.showInputPanel(
                                    mode = AiAssistant.MODE_RECORDING, 
                                    isMultiMode = isMultiModeProvider()
                                ) { onResult(it) }
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    
                    // 取消延迟任务，防止短按触发录音
                    handler.removeCallbacksAndMessages(null)

                    if (isRecording) {
                        isRecording = false
                        if (isWannaCancel) { // [新增]
                            stopRecording { /* discard */ }
                            aiAssistant.dismiss()
                            Utils.toast(ctx, "已取消录音")
                        } else {
                            stopRecording { file ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    // 4. 调用 API 转文字
                                    val text = AIService.speechToText(ctx, file)

                                    withContext(Dispatchers.Main) {
                                        if (!text.isNullOrEmpty()) {
                                            // 5. 成功：显示结果
                                            aiAssistant.showInputPanel(
                                                defaultText = text,
                                                mode = AiAssistant.MODE_LOADING,
                                                isMultiMode = isMultiModeProvider()
                                            ) { resultJson ->
                                                onResult(resultJson)
                                            }
                                        } else {
                                            aiAssistant.dismiss()
                                            Utils.toast(ctx, "未检测到语音")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecording() {
        audioFile = File(ctx.cacheDir, "voice_input.mp3")
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.media.MediaRecorder(ctx)
        } else {
            android.media.MediaRecorder()
        }.apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            prepare()
            start()
        }
    }

    private fun stopRecording(onFileReady: (File) -> Unit) {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // 录音时间太短可能抛出异常
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
        audioFile?.let { if (it.exists() && it.length() > 0) onFileReady(it) }
    }
}
