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
import tao.test.flipaccounting.Bill
import tao.test.flipaccounting.R
import tao.test.flipaccounting.Utils
import tao.test.flipaccounting.ui.dialog.OverlayDialogs
import java.text.SimpleDateFormat
import java.util.*

import tao.test.flipaccounting.CurrencyData

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
    private val spCurrency: Spinner = rootView.findViewById(R.id.spinner_currency)

    init {
        CurrencyManager.init(ctx)
        setupVisibility()
        setupSpinner()
        setupCurrencySpinner()
        setupListeners()
        setupDefaults()
        setupAnimations()
    }

    private fun setupVisibility() {
        // [新增] 根据偏好设置显示/隐藏组件
        val showAiText = Prefs.isShowAiText(ctx)
        val showAiVoice = Prefs.isShowAiVoice(ctx)
        val showMultiCur = Prefs.isShowMultiCurrency(ctx)

        layoutAiEntry.visibility = if (showAiText) View.VISIBLE else View.GONE
        btnVoice.visibility = if (showAiVoice) View.VISIBLE else View.GONE
        spCurrency.visibility = if (showMultiCur) View.VISIBLE else View.GONE
        
        // 如果 AI 文本和语音都隐藏了，通常意味着用户不想用 AI，
        // 我们可以在布局上做进一步调整（如隐藏分隔线等，这里简单处理）
    }

    private fun setupSpinner() {
        val types = ctx.resources.getStringArray(R.array.bill_types)
        val adapter = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, types) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.gravity = android.view.Gravity.CENTER
                v.setPadding(0, 0, 0, 0)
                v.textSize = 13f
                v.setTextColor(Color.parseColor("#666666"))
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.gravity = android.view.Gravity.CENTER
                v.setPadding(0, 30, 0, 30) // 增加下拉列表的垂直间距
                return v
            }
        }
        spType.adapter = adapter
    }

    private fun setupCurrencySpinner() {
        val enabledCodes = CurrencyManager.getEnabledCurrencies(ctx)
        
        // Map codes to display names with emoji
        val displayList = enabledCodes.map { code ->
            val info = CurrencyData.getInfo(code)
            info?.getDisplayName() ?: code
        }

        val adapter = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, displayList) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                
                // MAIN CHANGE: For the collapsed view, show only flag and code
                val code = enabledCodes[position]
                val info = CurrencyData.getInfo(code)
                v.text = info?.getShortName() ?: code
                
                v.gravity = android.view.Gravity.CENTER
                v.setTextColor(Color.parseColor("#333333"))
                v.textSize = 14f 
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                // Keep full display name in dropdown for clarity
                v.setPadding(30, 20, 30, 20)
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCurrency.adapter = adapter
        
        // Default to CNY
        val defaultIndex = enabledCodes.indexOf("CNY")
        if (defaultIndex >= 0) {
            spCurrency.setSelection(defaultIndex)
        }
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
                    2, 3 -> { // 转账(2)或还款(3)
                        layoutCategory.visibility = View.GONE
                        layoutAccount2.visibility = View.VISIBLE
                        layoutFee.visibility = if (pos == 2) View.VISIBLE else View.GONE
                        
                        if (tvAccount.text.toString().contains("选择") || tvAccount.text.toString().contains("资产")) {
                            tvAccount.text = if (pos == 2) "选择转出账户" else "选择付款账户"
                        }
                        if (tvAccount2.text.toString().contains("选择") || tvAccount2.text.toString().contains("账户")) {
                            tvAccount2.text = if (pos == 2) "转入账户" else "还款账户(信用卡)"
                        }
                        etMoney.hint = if (pos == 2) "转账金额" else "还款金额"
                    }
                    else -> { // 支出(0)或收入(1)
                        layoutCategory.visibility = View.VISIBLE
                        layoutAccount2.visibility = View.GONE
                        layoutFee.visibility = View.GONE
                        if (tvAccount.text.toString().contains("选择") || tvAccount.text.toString().contains("转出")) {
                            tvAccount.text = "选择资产"
                        }
                        etMoney.hint = if (pos == 1) "收入金额" else "支出金额"
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

        // Handle Currency Conversion
        val enabledCodes = CurrencyManager.getEnabledCurrencies(ctx)
        val selectedIndex = spCurrency.selectedItemPosition
        val selectedCurrency = if (selectedIndex >= 0 && selectedIndex < enabledCodes.size) {
            enabledCodes[selectedIndex]
        } else {
            "CNY"
        }

        var remarkStr = etRemark.text.toString()
        
        if (selectedCurrency != "CNY") {
            val cnyAmount = CurrencyManager.convertToCny(finalMoney, selectedCurrency)
            val formattedCnyStr = String.format(Locale.US, "%.2f", cnyAmount)
            
            // Append info to remarks: e.g. (10.00 EUR ≈ 78.50 CNY)
            val curInfo = "(${String.format(Locale.US, "%.2f", finalMoney)} $selectedCurrency ≈ $formattedCnyStr CNY)"
            remarkStr = if (remarkStr.isEmpty()) curInfo else "$remarkStr $curInfo"
            
            finalMoney = formattedCnyStr.toDouble()
        }

        var finalCategory = if (tvCategory.text.contains("选择")) "" else tvCategory.text.toString().replace(" > ", "/::/").replace(">", "/::/")

        if (typeIndex == 2 || typeIndex == 3) { // 转账或还款模式
            account2 = if (tvAccount2.text.contains("选择")) "" else tvAccount2.text.toString()
            if (account1.isEmpty() || account2.isEmpty()) {
                val msg = if (typeIndex == 2) "转账需选择两个账户" else "还款需选择付款和还款账户"
                Utils.toast(ctx, msg)
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
            remark = remarkStr,
            catename = finalCategory,
            accountname = account1,
            accountname2 = account2,
            fee = feeStr,
            currency = "CNY",
            showresult = "0"
        )

        // 如果分类中包含 /::/，说明是 AI 识别的层级结构，需要保留 /::/ 用于传给钱迹，但在显示时替换为 >
        val finalCategoryString = when (typeIndex) {
            2 -> "转账到 $account2"
            3 -> "还款到 $account2"
            else -> finalCategory.replace("/::/", " > ")
        }

        // 核心修改：在此处计算并保存图标，确保 AI 记账也能有图标
        val resolvedIcon = tao.test.flipaccounting.CategoryIconHelper.findCategoryIcon(ctx, if (typeIndex == 2 || typeIndex == 3) "转账" else finalCategoryString, typeIndex)

        // 保存到本地数据库 (SharedPreferences)
        val bill = Bill(
            finalMoney,
            typeIndex, // 0-支出, 1-收入, 2-转账, 3-还款
            account1,
            finalCategoryString,
            tvTime.text.toString(),
            remarkStr,
            resolvedIcon, // 保存图标链接
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) // 记录当前记账时间
        )
        Prefs.addBill(ctx, bill)

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
            onCloseRequest()
        } catch (e: Exception) {
            Utils.toast(ctx, "未安装钱迹")
        }
    }

    fun fillDataToUi(result: JSONObject, showToast: Boolean = true) {
        val targetType = result.optInt("type", 0)
        val currentType = spType.selectedItemPosition

        val executeFillData = {
            // A. 填充金额
            val amount = result.optDouble("amount", 0.0)
            if (amount > 0) {
                etMoney.setText(amount.toString())
                if (showToast) playHighlightAnimation(etMoney)
            }

            // B. 填充主账户
            val asset = result.optString("asset_name", "")
            if (asset.isNotEmpty()) {
                tvAccount.text = asset
                if (showToast) playHighlightAnimation(tvAccount.parent as View)
            }

            // C. 根据类型填充其他数据
            if (targetType == 2 || targetType == 3) { // 转账 或 还款
                val toAsset = result.optString("to_asset_name", "")
                if (toAsset.isNotEmpty()) {
                    tvAccount2.text = toAsset
                    if (showToast) playHighlightAnimation(tvAccount2.parent as View)
                }
                val fee = result.optDouble("fee", 0.0)
                if (fee > 0) {
                    etFee.setText(fee.toString())
                    if (showToast) playHighlightAnimation(etFee)
                }
            } else { // 支出/收入
                val category = result.optString("category_name", "")
                if (category.isNotEmpty()) {
                    tvCategory.text = category.replace("/::/", " > ")
                    if (showToast) playHighlightAnimation(tvCategory.parent as View)
                }
            }

            // D. 填充备注
            val remark = result.optString("remarks", "")
            if (remark.isNotEmpty()) {
                etRemark.setText(remark)
                if (showToast) playHighlightAnimation(etRemark)
            }
            val timeStr = result.optString("time", "")
            if (timeStr.isNotEmpty() && timeStr.contains("-")) {
                tvTime.text = timeStr
                if (showToast) playHighlightAnimation(tvTime)
            }

            if (showToast) Utils.toast(ctx, "✨ 智能填写完成")
        }

        if (currentType != targetType) {
            spType.setSelection(targetType)
            // 增加延迟确保 Spinner 的监听器触发完毕后，我们的数据填充才开始
            spType.postDelayed({ executeFillData() }, 350)
        } else {
            // 如果类型没变，直接填充
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
