package tao.test.flipaccounting

import tao.test.flipaccounting.R
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import org.json.JSONObject
import tao.test.flipaccounting.logic.AccountingFormController

class MainActivity : AppCompatActivity() {

    private val providers = listOf("硅基流动", "DeepSeek", "ChatGPT", "Gemini", "Kimi", "智谱清言", "OpenRouter", "通义千问", "小米MiMo", "自定义")
    private val providerUrls = mapOf(
        "硅基流动" to "https://api.siliconflow.cn",
        "DeepSeek" to "https://api.deepseek.com",
        "ChatGPT" to "https://api.openai.com",
        "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
        "Kimi" to "https://api.moonshot.cn",
        "智谱清言" to "https://open.bigmodel.cn/api",
        "OpenRouter" to "https://openrouter.ai/api",
        "通义千问" to "https://dashscope.aliyuncs.com/compatible-mode",
        "小米MiMo" to "https://api.xiaomimimo.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // --- 请求必要权限 ---
        checkAndRequestPermissions()

        // --- 0. 初始化状态 ---
        val isHide = Prefs.isHideRecents(this)
        setExcludeFromRecents(isHide)

        // 初始化导航
        setupNavigation()
        // 初始化 AI 配置页逻辑
        setupAIConfigPage()

        // 核心修复：启动时如果开关是开启的，确保服务也在运行
        if (Prefs.isFlipEnabled(this)) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START_FLIP
            }
            startServiceCompat(intent)
        }

        // --- 1. 权限与悬浮窗 ---
        findViewById<MaterialButton>(R.id.btnRequestOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Utils.toast(this, "悬浮窗权限已授予")
            }
        }

        findViewById<MaterialButton>(R.id.btnShowOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Utils.toast(this, "请先授予悬浮窗权限")
                return@setOnClickListener
            }
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OVERLAY
            }
            startServiceCompat(intent)
        }

        // --- 2. 开关逻辑 ---

        // 日志开关
        val btnShareLogs = findViewById<MaterialButton>(R.id.btn_share_logs)
        findViewById<SwitchMaterial>(R.id.switch_logging).apply {
            isChecked = Prefs.isLoggingEnabled(this@MainActivity)
            btnShareLogs.visibility = if (isChecked) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setLoggingEnabled(this@MainActivity, isChecked)
                btnShareLogs.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (isChecked) {
                    Utils.toast(context, "日志记录已开启")
                } else {
                    Logger.clearLogs(context)
                    Utils.toast(context, "日志记录已关闭并清空")
                }
            }
        }

        btnShareLogs.setOnClickListener {
            val logFile = Logger.getLogFile(this)
            if (!logFile.exists() || logFile.length() == 0L) {
                Utils.toast(this, "当前没有日志内容")
                return@setOnClickListener
            }
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        // 翻转开关
        findViewById<SwitchMaterial>(R.id.switch_flip_trigger).apply {
            isChecked = Prefs.isFlipEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setFlipEnabled(this@MainActivity, isChecked)
                val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                    action = if (isChecked) OverlayService.ACTION_START_FLIP else OverlayService.ACTION_STOP_FLIP
                }
                startServiceCompat(intent)
                if (isChecked) Utils.toast(context, "翻转触发已开启")
            }
        }


        // 隐藏最近任务开关
        findViewById<SwitchMaterial>(R.id.switch_hide_recent).apply {
            isChecked = isHide
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setHideRecents(this@MainActivity, isChecked)
                setExcludeFromRecents(isChecked)
                Utils.toast(this@MainActivity, if (isChecked) "已隐藏最近任务卡片" else "已恢复显示")
            }
        }

        // --- 3. 数据管理跳转 ---
        findViewById<View>(R.id.btn_manage_assets).setOnClickListener {
            startActivity(Intent(this, AssetActivity::class.java))
        }

        findViewById<View>(R.id.btn_manage_categories).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.btn_manage_currencies).setOnClickListener {
            Utils.toast(this, "货币管理功能开发中...")
        }

        findViewById<View>(R.id.btn_backup_restore).setOnClickListener {
            val options = arrayOf("📤 导出备份文件 (.json)", "📥 选择备份文件恢复")
            AlertDialog.Builder(this)
                .setTitle("数据备份")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        BackupManager.startExport(this)
                    } else {
                        BackupManager.startImport(this)
                    }
                }
                .show()
        }

        findViewById<View>(R.id.btn_manage_whitelist).setOnClickListener {
            if (!Shizuku.pingBinder()) {
                Utils.toast(this, "请先启动 Shizuku 并授权")
                return@setOnClickListener
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(101)
            } else {
                startActivity(Intent(this, AppListActivity::class.java))
            }
        }
    }

    private fun setupNavigation() {
        val navHome = findViewById<View>(R.id.nav_home)
        val navBills = findViewById<View>(R.id.nav_bills_tab)
        val navAI = findViewById<View>(R.id.nav_ai_config)

        val pageHome = findViewById<View>(R.id.page_home)
        val pageBills = findViewById<View>(R.id.page_bills)
        val pageAI = findViewById<View>(R.id.page_ai_config)

        val tvHomeText = findViewById<TextView>(R.id.tv_home_text)
        val tvBillsText = findViewById<TextView>(R.id.tv_bills_text)
        val tvAIText = findViewById<TextView>(R.id.tv_ai_text)

        navHome.setOnClickListener {
            pageHome.visibility = View.VISIBLE
            pageBills.visibility = View.GONE
            pageAI.visibility = View.GONE

            setNavActive(tvHomeText, listOf(tvBillsText, tvAIText))
        }

        navBills.setOnClickListener {
            pageHome.visibility = View.GONE
            pageBills.visibility = View.VISIBLE
            pageAI.visibility = View.GONE

            setNavActive(tvBillsText, listOf(tvHomeText, tvAIText))
            loadBills()
        }

        navAI.setOnClickListener {
            pageHome.visibility = View.GONE
            pageBills.visibility = View.GONE
            pageAI.visibility = View.VISIBLE

            setNavActive(tvAIText, listOf(tvHomeText, tvBillsText))
        }
    }

    private fun setNavActive(active: TextView, others: List<TextView>) {
        active.setTextColor(android.graphics.Color.parseColor("#5C6BC0"))
        active.setTypeface(null, android.graphics.Typeface.BOLD)
        others.forEach {
            it.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun findAssetIcon(ctx: Context, name: String): String {
        if (name.isEmpty()) return ""
        
        // 1. 尝试从用户自定义资产中获取
        // 注意：loadAssetsFromRaw 返回的是 List<BuiltInCategory>，而 getAssets 返回的是 List<Asset>
        // 它们的数据结构不同，但都有 name 和 icon 属性
        
        // 先检查用户资产配置
        val userAssets = Prefs.getAssets(ctx)
        val userAsset = userAssets.find { it.name == name }
        if (userAsset != null && userAsset.icon.isNotEmpty()) {
            return userAsset.icon
        }
        
        // 2. 如果用户资产没有图标，或者用户根本没存这个资产（仅仅是账单里有名字），尝试从内置库匹配
        val builtIn = Prefs.loadAssetsFromRaw(ctx)
        
        // A. 精确匹配
        var matched = builtIn.find { it.name == name }?.icon
        
        // B. 模糊匹配 (如果名字包含内置资产名，如"招商银行"包含"银行")
        if (matched == null) {
            // 这里逻辑按照长度倒序，优先匹配更长的词
             val candidate = builtIn.sortedByDescending { it.name.length }
                .find { name.contains(it.name) || it.name.contains(name) }
             
             matched = candidate?.icon
        }

        return matched ?: ""
    }

    private fun editBill(bill: Bill) {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("修改账单需要悬浮窗权限，是否前往开启？")
                .setPositiveButton("去开启") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val json = JSONObject().apply {
            put("amount", bill.amount)
            put("type", bill.type)
            put("asset_name", bill.assetName)
            put("remarks", bill.remarks)
            put("time", bill.time)
            
            if (bill.type == 2) {
                // 如果是转账，解析出目标资产
                val toAsset = bill.categoryName.replace("转账到 ", "").trim()
                put("to_asset_name", toAsset)
            } else {
                put("category_name", bill.categoryName)
            }
        }
        
        OverlayManager(this).showOverlay(json)
    }

    private fun loadBills() {
        val rv = findViewById<RecyclerView>(R.id.rv_bills)
        val empty = findViewById<View>(R.id.tv_empty_bills)
        val allBills = Prefs.getBills(this)

        if (allBills.isEmpty()) {
            rv.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            empty.visibility = View.GONE
            rv.layoutManager = LinearLayoutManager(this)
            
            // 按记账时间降序排列
            val sortedBills = allBills.sortedByDescending { it.time }

            // 数据分块处理：按日期分组
            val displayItems = mutableListOf<Any>()
            var lastDate = ""
            sortedBills.forEach { bill ->
                val date = if(bill.time.length >= 10) bill.time.substring(0, 10) else "未知时间"
                if (date != lastDate) {
                    displayItems.add(date) // 日期标题
                    lastDate = date
                }
                displayItems.add(bill)
            }
            rv.adapter = BillAdapter(displayItems)
        }
    }

    inner class BillAdapter(private val items: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_DATE = 0
        private val TYPE_BILL = 1

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is String) TYPE_DATE else TYPE_BILL
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_DATE) {
                val v = TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                    setPadding(16, 32, 16, 16)
                    setTextColor(android.graphics.Color.parseColor("#777777"))
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                object : RecyclerView.ViewHolder(v) {}
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bill, parent, false)
                BillViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_DATE) {
                (holder.itemView as TextView).text = items[position] as String
            } else {
                val bill = items[position] as Bill
                val h = holder as BillViewHolder
                
                // 处理转账/还款类型的显示名
                if (bill.type == 2 || bill.type == 3) {
                    h.category.text = if (bill.type == 2) "转账" else "还款"
                    val icon = if (bill.type == 2) " ➔ " else " ➔ "
                    h.detail.text = "${bill.assetName}$icon${bill.categoryName.replace("转账到 ", "").replace("还款到 ", "")}"
                } else {
                    h.category.text = if (bill.categoryName.contains(" > ")) bill.categoryName.split(" > ").last() else bill.categoryName
                    h.detail.text = "${bill.assetName}${if(bill.remarks.isNotEmpty()) " | ${bill.remarks}" else ""}"
                }

                h.time.text = if(bill.time.length > 11) bill.time.substring(11) else ""
                
                // 金额显示逻辑
                val amountText = String.format("%.2f", bill.amount)
                when(bill.type) {
                    1 -> { // 收入
                        h.amount.text = "+$amountText"
                        h.amount.setTextColor(android.graphics.Color.parseColor("#388E3C"))
                    }
                    2, 3 -> { // 转账 或 还款
                        h.amount.text = amountText
                        h.amount.setTextColor(android.graphics.Color.parseColor("#333333"))
                    }
                    else -> { // 支出
                        h.amount.text = "-$amountText"
                        h.amount.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                    }
                }
                
                // 加载图标
                // 优先使用 bill 自身存储的 iconUrl (针对 AI 记账场景优化)
                // 如果为空 (旧数据)，则通过 Helper 实时查找
                val catIcon = if (bill.iconUrl.isNotEmpty()) {
                    bill.iconUrl
                } else {
                    CategoryIconHelper.findCategoryIcon(this@MainActivity, bill.categoryName, bill.type)
                }

                val defaultResId = if(bill.type == 1) android.R.drawable.ic_input_add else android.R.drawable.ic_menu_edit
                
                if (catIcon.isNotEmpty()) {
                    Glide.with(this@MainActivity)
                        .load(catIcon)
                        .transform(CircleCrop())
                        .error(defaultResId) // 加载失败时显示默认图标
                        .into(h.categoryIcon)
                } else {
                    h.categoryIcon.setImageResource(defaultResId)
                }

                // 加载资产图标
                val assetIcon = findAssetIcon(this@MainActivity, bill.assetName)
                if (assetIcon.isNotEmpty()) {
                    h.assetIcon.visibility = View.VISIBLE
                    Glide.with(this@MainActivity)
                        .load(assetIcon)
                        .transform(CircleCrop())
                        .into(h.assetIcon)
                } else {
                    h.assetIcon.visibility = View.GONE
                }

                h.itemView.setOnClickListener {
                    editBill(bill)
                }

                // 长按删除
                h.itemView.setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("确认删除")
                        .setMessage("确定要删除这条账单记录吗？")
                        .setPositiveButton("删除") { _, _ ->
                            Prefs.deleteBill(this@MainActivity, bill)
                            loadBills() // 刷新列表
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
            }
        }
        override fun getItemCount() = items.size
    }

    class BillViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val categoryIcon = v.findViewById<ImageView>(R.id.iv_bill_category_icon)
        val assetIcon = v.findViewById<ImageView>(R.id.iv_bill_asset_icon)
        val category = v.findViewById<TextView>(R.id.tv_bill_category)
        val detail = v.findViewById<TextView>(R.id.tv_bill_detail)
        val time = v.findViewById<TextView>(R.id.tv_bill_time)
        val amount = v.findViewById<TextView>(R.id.tv_bill_amount)
    }



    private fun setupAIConfigPage() {
        val spinnerProviders = findViewById<Spinner>(R.id.spinner_providers)
        val etUrl = findViewById<EditText>(R.id.et_api_url)
        val etKey = findViewById<EditText>(R.id.et_api_key)
        val spinnerModels = findViewById<Spinner>(R.id.spinner_models)
        val etPrompt = findViewById<EditText>(R.id.et_custom_prompt)
        val btnResetPrompt = findViewById<MaterialButton>(R.id.btn_reset_prompt)
        val btnTest = findViewById<MaterialButton>(R.id.btn_test_conn)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save_config)

        // 初始化提供商选择器
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        spinnerProviders.adapter = providerAdapter

        // 加载保存的值
        val currentProvider = Prefs.getAiProvider(this)
        val currentUrl = Prefs.getAiUrl(this)
        val currentKey = Prefs.getAiKey(this)
        val currentModel = Prefs.getAiModel(this)
        var currentPrompt = Prefs.getAiPrompt(this)
        if (currentPrompt.isEmpty()) {
            currentPrompt = AIService.DEFAULT_PROMPT
        }

        if (currentProvider.isNotEmpty()) {
            val idx = providers.indexOf(currentProvider)
            if (idx >= 0) spinnerProviders.setSelection(idx)
        }
        etUrl.setText(currentUrl)
        etKey.setText(currentKey)
        etPrompt.setText(currentPrompt)

        btnResetPrompt.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确定恢复默认？")
                .setMessage("这将清除您的自定义提示词，并恢复为系统最新的默认版本（包含对'还款'模式的识别优化）。")
                .setPositiveButton("恢复") { _, _ ->
                    etPrompt.setText(AIService.DEFAULT_PROMPT)
                    Utils.toast(this, "已恢复，请记得点击下方的'保存配置'")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        val modelList = if (currentModel.isNotEmpty()) mutableListOf(currentModel) else mutableListOf()
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelList)
        spinnerModels.adapter = modelAdapter

        // 提供商切换逻辑
        spinnerProviders.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = providers[position]
                if (selected != "自定义") {
                    providerUrls[selected]?.let { etUrl.setText(it) }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnTest.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Utils.toast(this, "请输入 API 令牌")
                return@setOnClickListener
            }

            // 临时保存 URL 以便测试
            Prefs.setAiUrl(this, url)

            btnTest.text = "正在尝试连接..."
            btnTest.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                val models = AIService.fetchModels(this@MainActivity, key)
                withContext(Dispatchers.Main) {
                    btnTest.isEnabled = true
                    btnTest.text = "测试连接并获取模型"

                    if (models.isNotEmpty()) {
                        Utils.toast(this@MainActivity, "连接成功！获取到 ${models.size} 个模型")
                        val newAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, models)
                        spinnerModels.adapter = newAdapter
                        val idx = models.indexOf(currentModel)
                        if (idx >= 0) spinnerModels.setSelection(idx)
                    } else {
                        Utils.toast(this@MainActivity, "连接失败，请检查令牌或网络")
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val provider = spinnerProviders.selectedItem?.toString() ?: "自定义"
            val url = etUrl.text.toString().trim()
            val key = etKey.text.toString().trim()
            val selectedModel = spinnerModels.selectedItem?.toString() ?: currentModel
            val prompt = etPrompt.text.toString().trim()

            if (key.isEmpty()) {
                Utils.toast(this, "API 令牌不能为空")
                return@setOnClickListener
            }

            Prefs.setAiProvider(this, provider)
            Prefs.setAiUrl(this, url)
            Prefs.setAiKey(this, key)
            Prefs.setAiModel(this, selectedModel)
            Prefs.setAiPrompt(this, prompt)
            Utils.toast(this, "配置已成功保存")
        }
    }

    private fun startServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        val uri = data.data ?: return
        when (requestCode) {
            BackupManager.REQUEST_CODE_EXPORT -> {
                BackupManager.handleExportResult(this, uri)
            }
            BackupManager.REQUEST_CODE_IMPORT -> {
                AlertDialog.Builder(this)
                    .setTitle("确认恢复")
                    .setMessage("恢复将覆盖当前所有资产、分类及白名单数据，是否继续？")
                    .setPositiveButton("确定") { _, _ ->
                        BackupManager.handleImportResult(this, uri)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun setExcludeFromRecents(exclude: Boolean) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks?.forEach { it.setExcludeFromRecents(exclude) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val needed = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), 100)
            }
        }
    }
}