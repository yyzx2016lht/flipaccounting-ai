package tao.test.flipaccounting

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
        val navAI = findViewById<View>(R.id.nav_ai_config)
        val pageHome = findViewById<View>(R.id.page_home)
        val pageAI = findViewById<View>(R.id.page_ai_config)

        val tvHomeText = findViewById<TextView>(R.id.tv_home_text)
        val tvAIText = findViewById<TextView>(R.id.tv_ai_text)

        navHome.setOnClickListener {
            pageHome.visibility = View.VISIBLE
            pageAI.visibility = View.GONE

            // 设置激活颜色 (首页)
            tvHomeText.setTextColor(android.graphics.Color.parseColor("#5C6BC0"))
            tvHomeText.setTypeface(null, android.graphics.Typeface.BOLD)
            tvAIText.setTextColor(android.graphics.Color.parseColor("#666666"))
            tvAIText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        navAI.setOnClickListener {
            pageHome.visibility = View.GONE
            pageAI.visibility = View.VISIBLE

            // 设置激活颜色 (AI 配置)
            tvAIText.setTextColor(android.graphics.Color.parseColor("#5C6BC0"))
            tvAIText.setTypeface(null, android.graphics.Typeface.BOLD)
            tvHomeText.setTextColor(android.graphics.Color.parseColor("#666666"))
            tvHomeText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun setupAIConfigPage() {
        val spinnerProviders = findViewById<Spinner>(R.id.spinner_providers)
        val etUrl = findViewById<EditText>(R.id.et_api_url)
        val etKey = findViewById<EditText>(R.id.et_api_key)
        val spinnerModels = findViewById<Spinner>(R.id.spinner_models)
        val etPrompt = findViewById<EditText>(R.id.et_custom_prompt)
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