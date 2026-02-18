package tao.test.flipaccounting

import android.app.Activity
import android.content.Context
import android.content.Intent
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
        const val MODE_CANCEL = 3     // [新增] 松开取消模式
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
        isMultiMode: Boolean? = null, // [新增]
        onResult: (JSONObject) -> Unit
    ) {
        // 如果弹窗已存在，直接复用，避免闪烁
        if (currentDialog?.isShowing == true) {
            updatePanelState(mode, defaultText)
            // 如果是 Loading 模式且有文字，说明语音转写完成了，触发分析
            if (mode == MODE_LOADING && !defaultText.isNullOrEmpty()) {
                startAnalysis(defaultText, isMultiMode, onResult)
            }
            return
        }

        // 仅在真正弹出新面板时，通知停止翻转监测，减少输入干扰并释放传感器句柄
        if (Prefs.isFlipEnabled(ctx)) {
            val stopIntent = Intent(ctx, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP_FLIP
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(stopIntent)
                } else {
                    ctx.startService(stopIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- 初始化弹窗 ---
        val themeContext = ContextThemeWrapper(ctx, R.style.Theme_FlipAccounting)
        val view = LayoutInflater.from(themeContext).inflate(R.layout.layout_dialog_ai_input, null)

        val dialog = AlertDialog.Builder(themeContext)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.setOnDismissListener {
            // 弹窗关闭后，恢复翻转检测（如果开启了的话）
            if (Prefs.isFlipEnabled(ctx)) {
                val startIntent = Intent(ctx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START_FLIP
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(startIntent)
                    } else {
                        ctx.startService(startIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            currentDialog = null
        }

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

        // [修复] 解决在部分系统（如 OPPO/Vivo/三星）中，Service 覆盖层 EditText 点击多次导致的浮动工具栏崩溃 (UnsupportedOperationException)
        // 该异常是因为 Service Context 并非视觉 Context，不关联 Display，导致系统尝试弹出“复制/粘贴/插入”浮动菜单时失败。
        // 在 Service 环境中通过禁用 Insertion/Selection ActionModeCallback 来阻止系统调起该工具栏。
        if (ctx !is Activity) {
            val blankCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
            }
            etInput.customInsertionActionModeCallback = blankCallback
            etInput.customSelectionActionModeCallback = blankCallback
        }

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
                startAnalysis(text, isMultiMode, onResult)
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
                tvThinkingLog?.setTextColor(android.graphics.Color.parseColor("#7B61FF"))
                tvRecordedTextPreview?.visibility = View.GONE // 录音时不显示预览
                dialog.setCancelable(false)
                btnClose.visibility = View.GONE
            }
            MODE_CANCEL -> { // [新增]
                layoutInput.visibility = View.GONE
                layoutLoading.visibility = View.VISIBLE
                layoutResult.visibility = View.GONE
                tvThinkingLog?.text = "松开即可取消"
                tvThinkingLog?.setTextColor(android.graphics.Color.RED)
                tvRecordedTextPreview?.visibility = View.GONE
                dialog.setCancelable(false)
                btnClose.visibility = View.GONE
            }
            MODE_LOADING -> {
                layoutInput.visibility = View.GONE
                layoutLoading.visibility = View.VISIBLE
                layoutResult.visibility = View.GONE
                tvThinkingLog?.setTextColor(android.graphics.Color.parseColor("#7B61FF"))
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
    private fun startAnalysis(text: String, isMultiMode: Boolean?, onResult: (JSONObject) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = AIService.analyzeAccounting(ctx, text, isMultiMode)
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

        if (result.has("bills")) {
            // 多账单模式显示预览
            val bills = result.getJSONArray("bills")
            val count = bills.length()
            
            tvResMoney.text = "识别到 $count 条账单"
            tvResMoney.setTextColor(android.graphics.Color.parseColor("#5C6BC0"))
            
            // 取第一条作为简单的预览
            if (count > 0) {
                val first = bills.getJSONObject(0)
                val amt = first.optDouble("amount", 0.0)
                val cat = first.optString("category_name", "").replace("/::/", " > ")
                tvResCate.text = "首笔: $cat ($amt)"
                tvResAsset.text = "点击确认后将依次处理"
            }
            tvResTime.visibility = View.GONE
        } else {
            // 原有的单笔模式解析数据
            val type = result.optInt("type", 0)
            val amt = result.optDouble("amount", 0.0)
            val fee = result.optDouble("fee", 0.0)
            val symbol = when(type) {
                1 -> "+"
                2 -> "⇄"
                3 -> "💸" // 还款标志
                else -> "-"
            }

            tvResMoney.text = if (type == 1) "+$amt" else if (type == 2 || type == 3) "$amt" else "-$amt"
            tvResMoney.setTextColor(android.graphics.Color.parseColor(if (type == 1) "#E91E63" else "#2E7D32"))
            
            val timeStr = result.optString("time", "")
            if (timeStr.isNotEmpty()) {
                tvResTime.text = "时间: $timeStr"
                tvResTime.visibility = View.VISIBLE
            } else {
                tvResTime.text = "时间: 现在"
            }

            when (type) {
                2 -> { // 转账
                    tvResCate.text = "转入: ${result.optString("to_asset_name", "--")}"
                    tvResAsset.text = "转出: ${result.optString("asset_name", "--")}"
                }
                3 -> { // 还款
                    tvResCate.text = "还款给: ${result.optString("to_asset_name", "--")}"
                    tvResAsset.text = "支付方: ${result.optString("asset_name", "--")}"
                }
                else -> { // 支出、收入
                    val cat = result.optString("category_name", "--")
                    tvResCate.text = "分类: ${cat.replace("/::/", " > ")}"
                    val assetName = result.optString("asset_name", "")
                    tvResAsset.text = "账户: ${if (assetName.isEmpty()) "未识别" else assetName}"
                }
            }
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