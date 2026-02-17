package tao.test.flipaccounting

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import org.json.JSONObject
import tao.test.flipaccounting.logic.AccountingFormController
import tao.test.flipaccounting.logic.VoiceInputHandler

class OverlayManager(private val ctx: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    

    private val aiAssistant = AiAssistant(ctx)
    private var formController: AccountingFormController? = null

    fun showOverlay(prefill: JSONObject? = null) {
        if (overlayView != null) return
        Logger.d(ctx, "OverlayManager", "Showing Overlay")

        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 使用 ContextThemeWrapper 确保 Material 库组件在 Service/WindowManager 中能正确渲染
        val themeContext = android.view.ContextThemeWrapper(ctx, R.style.Theme_FlipAccounting)
        overlayView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_window, null)

        val params = WindowManager.LayoutParams().apply {
            width = (ctx.resources.displayMetrics.widthPixels * 0.9).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 150
        }

        setupLogic(overlayView!!, prefill)
        windowManager?.addView(overlayView, params)
    }

    private fun setupLogic(view: View, prefill: JSONObject?) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                removeOverlay()
                true 
            } else {
                false
            }
        }
        view.requestFocus()

        // Initialize Form Controller
        formController = AccountingFormController(ctx, view) {
            removeOverlay()
        }

        if (prefill != null) {
            formController?.fillDataToUi(prefill, showToast = false)
        }

        // Initialize Voice Handler
        val voiceHandler = VoiceInputHandler(ctx, aiAssistant) { resultJson ->
             formController?.fillDataToUi(resultJson, showToast = true)
        }
        voiceHandler.setupVoiceButton(formController!!.btnVoice)

        // Initialize AI Entry Click (Text input)
        formController!!.layoutAiEntry.setOnClickListener {
             aiAssistant.showInputPanel { resultJson ->
                 formController?.fillDataToUi(resultJson, showToast = true)
             }
        }
    }

    fun removeOverlay() {
        if (overlayView != null) {
            Logger.d(ctx, "OverlayManager", "Removing Overlay")
            windowManager?.removeView(overlayView)
        }
        overlayView = null
        formController = null
    }
}
