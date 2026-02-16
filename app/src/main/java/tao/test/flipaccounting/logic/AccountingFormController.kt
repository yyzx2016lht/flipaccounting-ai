package tao.test.flipaccounting.logic

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.*
import org.json.JSONObject
import tao.test.flipaccounting.Prefs
import tao.test.flipaccounting.R
import tao.test.flipaccounting.Utils
import tao.test.flipaccounting.ui.dialog.OverlayDialogs
import java.text.SimpleDateFormat
import java.util.*

class AccountingFormController(
    private val ctx: Context,
    private val rootView: View,
    private val onCloseRequest: () -> Unit
) {
    private val etMoney: EditText = rootView.findViewById(R.id.et_amount)
    private val spType: Spinner = rootView.findViewById(R.id.spinner_type)
    private val layoutAccount: View = rootView.findViewById(R.id.layout_account)
    private val tvAccount: TextView = rootView.findViewById(R.id.tv_account)
    private val layoutAccount2: View = rootView.findViewById(R.id.layout_account_2)
    private val tvAccount2: TextView = rootView.findViewById(R.id.tv_account_2)
    private val layoutCategory: View = rootView.findViewById(R.id.layout_category)
    private val tvCategory: TextView = rootView.findViewById(R.id.tv_category)
    private val layoutFee: View = rootView.findViewById(R.id.layout_fee)
    private val etFee: EditText = rootView.findViewById(R.id.et_fee)
    private val tvTime: TextView = rootView.findViewById(R.id.tv_time)
    private val etRemark: EditText = rootView.findViewById(R.id.et_remark)
    private val btnSave: Button = rootView.findViewById(R.id.btn_save)
    private val btnCancel: Button = rootView.findViewById(R.id.btn_cancel)
    val btnVoice: ImageView = rootView.findViewById(R.id.btn_ai_voice)
    val layoutAiEntry: LinearLayout = rootView.findViewById(R.id.layout_ai_entry)
    val btnAiIcon: ImageView = rootView.findViewById(R.id.btn_ai_magic)

    init {
        setupListeners()
        setupDefaults()
        setupAnimations()
    }

    private fun setupAnimations() {
        ObjectAnimator.ofFloat(btnAiIcon, "alpha", 1.0f, 0.4f, 1.0f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun setupDefaults() {
        tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun setupListeners() {
        val assets = Prefs.getAssets(ctx)
        val assetNames = assets.map { it.name }

        layoutAccount.setOnClickListener { anchor ->
            if (assetNames.isNotEmpty()) OverlayDialogs.showAnchoredMenu(ctx, anchor, assetNames) { tvAccount.text = it }
            else Utils.toast(ctx, "请先在App内添加资产")
        }

        layoutAccount2.setOnClickListener { anchor ->
            if (assetNames.isNotEmpty()) OverlayDialogs.showAnchoredMenu(ctx, anchor, assetNames) { tvAccount2.text = it }
            else Utils.toast(ctx, "请先在App内添加资产")
        }

        layoutCategory.setOnClickListener {
            // 根据钱迹规范：1 是收入，0 是支出
            val currentType = if (spType.selectedItemPosition == 1) Prefs.TYPE_INCOME else Prefs.TYPE_EXPENSE
            OverlayDialogs.showGridCategoryPicker(ctx, tvCategory.text.toString(), currentType) {
                tvCategory.text = it
            }
        }

        rootView.findViewById<View>(R.id.layout_time).setOnClickListener { 
            OverlayDialogs.showCustomTimePicker(ctx) { tvTime.text = it }
        }

        spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                when (pos) {
                    2 -> { // 转账
                        layoutCategory.visibility = View.GONE
                        layoutAccount2.visibility = View.VISIBLE
                        layoutFee.visibility = View.VISIBLE
                        tvAccount.text = "选择转出账户"
                        etMoney.hint = "转账金额"
                    }
                    else -> { // 支出(0)或收入(1)
                        layoutCategory.visibility = View.VISIBLE
                        layoutAccount2.visibility = View.GONE
                        layoutFee.visibility = View.GONE
                        tvAccount.text = "选择资产"
                        etMoney.hint = "0.00"
                    }
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener { handleSave() }
        btnCancel.setOnClickListener { onCloseRequest() }
    }

    private fun handleSave() {
        val rawMoneyStr = etMoney.text.toString().replace("-", "")
        if (rawMoneyStr.isEmpty() || (rawMoneyStr.toDoubleOrNull() ?: 0.0) <= 0.0) {
            Utils.toast(ctx, "金额无效")
            return
        }

        val typeIndex = spType.selectedItemPosition
        val account1 = if (tvAccount.text.contains("选择")) "" else tvAccount.text.toString()
        var account2 = ""
        var feeStr = "0"
        var finalMoney = rawMoneyStr.toDouble()
        var finalCategory = if (tvCategory.text.contains("选择")) "" else tvCategory.text.toString().replace(" > ", "/::/")

        if (typeIndex == 2) { // 转账模式
            account2 = if (tvAccount2.text.contains("选择")) "" else tvAccount2.text.toString()
            if (account1.isEmpty() || account2.isEmpty()) {
                Utils.toast(ctx, "转账需选择两个账户")
                return
            }
            val feeVal = etFee.text.toString().toDoubleOrNull() ?: 0.0
            feeStr = feeVal.toString()
            finalCategory = ""
        }

        val url = Utils.buildQianjiUrl(
            type = typeIndex.toString(),
            money = finalMoney.toString(),
            time = tvTime.text.toString(),
            remark = etRemark.text.toString(),
            catename = finalCategory,
            accountname = account1,
            accountname2 = account2,
            fee = feeStr,
            currency = "CNY",
            showresult = "0"
        )

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
            onCloseRequest()
        } catch (e: Exception) {
            Utils.toast(ctx, "未安装钱迹")
        }
    }

    fun fillDataToUi(result: JSONObject) {
        val targetType = result.optInt("type", 0)
        val currentType = spType.selectedItemPosition

        val executeFillData = {
            // A. 填充金额
            val amount = result.optDouble("amount", 0.0)
            if (amount > 0) {
                etMoney.setText(amount.toString())
                playHighlightAnimation(etMoney)
            }

            // B. 填充主账户
            val asset = result.optString("asset_name", "")
            if (asset.isNotEmpty()) {
                tvAccount.text = asset
                playHighlightAnimation(tvAccount.parent as View)
            }

            // C. 根据类型填充其他数据
            if (targetType == 2) { // 转账
                val toAsset = result.optString("to_asset_name", "")
                if (toAsset.isNotEmpty()) {
                    tvAccount2.text = toAsset
                    playHighlightAnimation(tvAccount2.parent as View)
                }
                val fee = result.optDouble("fee", 0.0)
                if (fee > 0) {
                    etFee.setText(fee.toString())
                    playHighlightAnimation(etFee)
                }
            } else { // 支出/收入
                val category = result.optString("category_name", "")
                if (category.isNotEmpty()) {
                    tvCategory.text = category.replace("/::/", " > ")
                    playHighlightAnimation(tvCategory.parent as View)
                }
            }

            // D. 填充备注
            val remark = result.optString("remarks", "")
            if (remark.isNotEmpty()) {
                etRemark.setText(remark)
                playHighlightAnimation(etRemark)
            }
            val timeStr = result.optString("time", "")
            if (timeStr.isNotEmpty() && timeStr.contains("-")) {
                tvTime.text = timeStr
                playHighlightAnimation(tvTime)
            }
            Utils.toast(ctx, "✨ 智能填写完成")
        }

        if (currentType != targetType) {
            spType.setSelection(targetType)
            spType.postDelayed({ executeFillData() }, 200)
        } else {
            executeFillData()
        }
    }

    private fun playHighlightAnimation(view: View) {
        val colorAnim = android.animation.ObjectAnimator.ofArgb(
            view, "backgroundColor", Color.TRANSPARENT, Color.parseColor("#33FFC107"), Color.TRANSPARENT
        )
        colorAnim.duration = 1000
        colorAnim.start()
    }
}
