package tao.test.flipaccounting

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiAssistant(private val ctx: Context) {

    private var currentDialog: AlertDialog? = null
    private var tvThinkingLog: TextView? = null
    private var tvRecordedTextPreview: TextView? = null

    companion object {
        const val MODE_INPUT = 0      // 纯文本输入模式
        const val MODE_RECORDING = 1  // 录音中模式
        const val MODE_LOADING = 2    // 识别/分析中模式
    }

    /**
     * 显示 AI 面板
     * @param defaultText 默认填入的文字（语音转文字的结果）
     * @param mode 初始启动模式
     * @param onResult 最终拿到记账数据的回调
     */
    fun showInputPanel(
        defaultText: String? = null,
        mode: Int = MODE_INPUT,
        onResult: (JSONObject) -> Unit
    ) {
        // 如果弹窗已存在，直接复用，避免闪烁
        if (currentDialog?.isShowing == true) {
            updatePanelState(mode, defaultText)
            // 如果是 Loading 模式且有文字，说明语音转写完成了，触发分析
            if (mode == MODE_LOADING && !defaultText.isNullOrEmpty()) {
                startAnalysis(defaultText, onResult)
            }
            return
        }

        // --- 初始化弹窗 ---
        val themeContext = ContextThemeWrapper(ctx, R.style.Theme_FlipAccounting)
        val view = LayoutInflater.from(themeContext).inflate(R.layout.layout_dialog_ai_input, null)

        val dialog = AlertDialog.Builder(themeContext)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.apply {
            setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
            attributes.y = 300
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        currentDialog = dialog
        dialog.show()

        // 调整宽度
        dialog.window?.let { win ->
            val dm = ctx.resources.displayMetrics
            val widthPx = (340 * dm.density).toInt() // 对应 XML 中的 340dp
            win.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        // --- 绑定控件 ---
        val btnClose = view.findViewById<View>(R.id.btn_close)
        val btnIdentify = view.findViewById<View>(R.id.btn_dialog_identify)
        val etInput = view.findViewById<EditText>(R.id.et_ai_input)

        // 保存全局引用方便 updatePanelState 使用
        tvThinkingLog = view.findViewById(R.id.tv_thinking_log)
        tvRecordedTextPreview = view.findViewById(R.id.tv_recorded_text_preview)

        // 绑定关闭事件
        btnClose.setOnClickListener { dismiss() }

        // 绑定“开始分析”按钮事件
        btnIdentify.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                updatePanelState(MODE_LOADING, "正在分析语义...")
                startAnalysis(text, onResult)
            }
        }

        // --- 初始状态设置 ---
        updatePanelState(mode, defaultText)

        // 如果是输入模式且带文字 (比如重试)
        if (mode == MODE_INPUT && !defaultText.isNullOrEmpty()) {
            etInput.setText(defaultText)
        }
    }

    /**
     * 根据状态切换 UI 显隐
     */
    private fun updatePanelState(mode: Int, text: String? = null) {
        val dialog = currentDialog ?: return
        val view = dialog.findViewById<View>(android.R.id.content) ?: return

        val layoutInput = view.findViewById<View>(R.id.layout_input)
        val layoutLoading = view.findViewById<View>(R.id.layout_loading)
        val layoutResult = view.findViewById<View>(R.id.layout_result)
        val btnClose = view.findViewById<View>(R.id.btn_close)

        when (mode) {
            MODE_INPUT -> {
                layoutInput.visibility = View.VISIBLE
                layoutLoading.visibility = View.GONE
                layoutResult.visibility = View.GONE
                dialog.setCancelable(true)
                btnClose.visibility = View.VISIBLE
            }
            MODE_RECORDING -> {
                layoutInput.visibility = View.GONE
                layoutLoading.visibility = View.VISIBLE
                layoutResult.visibility = View.GONE
                tvThinkingLog?.text = "正在倾听..."
                tvRecordedTextPreview?.visibility = View.GONE // 录音时不显示预览
                dialog.setCancelable(false)
                btnClose.visibility = View.GONE
            }
            MODE_LOADING -> {
                layoutInput.visibility = View.GONE
                layoutLoading.visibility = View.VISIBLE
                layoutResult.visibility = View.GONE

                // 显示“您说的是：xxx”
                if (!text.isNullOrEmpty() && text != "正在分析语义...") {
                    tvThinkingLog?.text = "正在分析..."
                    tvRecordedTextPreview?.visibility = View.VISIBLE
                    tvRecordedTextPreview?.text = text
                } else {
                    tvThinkingLog?.text = text ?: "正在处理..."
                }

                dialog.setCancelable(false)
                btnClose.visibility = View.GONE
            }
        }
    }

    /**
     * 执行 AI 分析请求
     */
    private fun startAnalysis(text: String, onResult: (JSONObject) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = AIService.analyzeAccounting(ctx, text)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    showResult(result, onResult)
                } else {
                    Utils.toast(ctx, "识别失败，请重试")
                    // 失败后退回输入模式，保留刚才的文字方便修改
                    updatePanelState(MODE_INPUT, text)
                }
            }
        }
    }

    /**
     * 显示结果卡片
     */
    private fun showResult(result: JSONObject, onResult: (JSONObject) -> Unit) {
        val dialog = currentDialog ?: return
        val view = dialog.findViewById<View>(android.R.id.content) ?: return

        val layoutLoading = view.findViewById<View>(R.id.layout_loading)
        val layoutResult = view.findViewById<View>(R.id.layout_result)
        val btnClose = view.findViewById<View>(R.id.btn_close)
        val tvResTime = view.findViewById<TextView>(R.id.tv_res_time)
        val tvResMoney = view.findViewById<TextView>(R.id.tv_res_money)
        val tvResCate = view.findViewById<TextView>(R.id.tv_res_cate)
        val tvResAsset = view.findViewById<TextView>(R.id.tv_res_asset)
        val btnConfirm = view.findViewById<View>(R.id.btn_confirm_fill)

        // 解析数据
        val type = result.optInt("type", 0)
        val amt = result.optDouble("amount", 0.0)
        val fee = result.optDouble("fee", 0.0)
        val symbol = if (type == 1) "+" else if (type == 2) "⇄" else "-"

        tvResMoney.text = "$symbol $amt"
        val timeStr = result.optString("time", "")
        if (timeStr.isNotEmpty()) {
            tvResTime.text = "时间: $timeStr"
            tvResTime.visibility = View.VISIBLE
        } else {
            // 如果 AI 没返回时间，显示“当前时间”或隐藏
            // 这里建议显示 "当前时间" 让界面保持整齐，或者隐藏
            tvResTime.text = "时间: 当前时间"
            // tvResTime.visibility = View.GONE // 或者选择隐藏
        }
        if (type == 2) {
            tvResCate.text = "转入: ${result.optString("to_asset_name", "--")}"
            tvResAsset.text = "转出: ${result.optString("asset_name", "--")}"
        } else {
            tvResCate.text = "分类: ${result.optString("category_name", "--")}"
            val assetName = result.optString("asset_name", "")
            tvResAsset.text = "账户: ${if (assetName.isEmpty()) "未识别" else assetName}"
        }

        // 切换视图
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        dialog.setCancelable(true)
        btnClose.visibility = View.VISIBLE

        btnConfirm.setOnClickListener {
            dismiss()
            onResult(result)
        }
    }

    fun dismiss() {
        currentDialog?.dismiss()
        currentDialog = null
    }
}