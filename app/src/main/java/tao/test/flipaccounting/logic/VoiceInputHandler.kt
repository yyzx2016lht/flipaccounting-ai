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
    private val onResult: (JSONObject) -> Unit
) {
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var audioFile: File? = null
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isRecording = false

    fun setupVoiceButton(btnVoice: View) {
        btnVoice.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 1. 立即缩放动画，即使是短按也有反馈
                    v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).start()
                    
                    // 2. 延迟判断长按
                    isRecording = false
                    handler.postDelayed({
                        isRecording = true
                        // 触发震动反馈：长按确认进入录音
                        Utils.vibrate(ctx) 
                        
                        aiAssistant.showInputPanel(mode = AiAssistant.MODE_RECORDING) { resultJson ->
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    
                    // 取消延迟任务，防止短按触发录音
                    handler.removeCallbacksAndMessages(null)

                    if (isRecording) {
                        isRecording = false
                        stopRecording { file ->
                            CoroutineScope(Dispatchers.IO).launch {
                                // 4. 调用 API 转文字
                                val text = AIService.speechToText(ctx, file)

                                withContext(Dispatchers.Main) {
                                    if (!text.isNullOrEmpty()) {
                                        // 5. 成功：显示结果
                                        aiAssistant.showInputPanel(
                                            defaultText = text,
                                            mode = AiAssistant.MODE_LOADING
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
