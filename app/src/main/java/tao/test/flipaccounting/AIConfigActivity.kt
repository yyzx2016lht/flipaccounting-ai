package tao.test.flipaccounting

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent // 修复 Intent 爆红
import android.view.View     // 修复 View 爆红

class AIConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_config)

        // 1. 绑定所有 View，请确保 ID 与 XML 严格一致
        val etKey = findViewById<EditText>(R.id.et_api_key)
        val spinner = findViewById<Spinner>(R.id.spinner_models)
        val btnTest = findViewById<MaterialButton>(R.id.btn_test_conn)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save_config)

        // 2. 回显已有配置
        val currentKey = Prefs.getAiKey(this)
        val currentModel = Prefs.getAiModel(this)
        etKey.setText(currentKey)
// 2. 首页按钮（切回首页）
        findViewById<View>(R.id.nav_home)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            finish()
        }

// 3. AI 配置按钮（当前就在此页）
        findViewById<View>(R.id.nav_ai_config)?.setOnClickListener {
            // 无需操作
        }
        // 初始 Spinner 列表
        val initialList = mutableListOf(currentModel)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, initialList)
        spinner.adapter = adapter

        // 3. 测试连接按钮逻辑
        btnTest.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isEmpty()) {
                Utils.toast(this, "请输入 API 令牌")
                return@setOnClickListener
            }

            btnTest.text = "正在尝试连接..."
            btnTest.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                val models = AIService.fetchModels(key)
                withContext(Dispatchers.Main) {
                    btnTest.isEnabled = true
                    btnTest.text = "测试连接并获取模型"

                    if (models.isNotEmpty()) {
                        Utils.toast(this@AIConfigActivity, "连接成功！获取到 ${models.size} 个模型")
                        val newAdapter = ArrayAdapter(this@AIConfigActivity, android.R.layout.simple_spinner_dropdown_item, models)
                        spinner.adapter = newAdapter
                        // 尝试选中之前的模型
                        val idx = models.indexOf(currentModel)
                        if (idx >= 0) spinner.setSelection(idx)
                    } else {
                        Utils.toast(this@AIConfigActivity, "连接失败，请检查令牌或网络")
                    }
                }
            }
        }

        // 4. 保存按钮逻辑
        btnSave.setOnClickListener {
            val key = etKey.text.toString().trim()
            val selectedModel = spinner.selectedItem?.toString() ?: currentModel

            if (key.isEmpty()) {
                Utils.toast(this, "API 令牌不能为空")
                return@setOnClickListener
            }

            Prefs.setAiKey(this, key)
            Prefs.setAiModel(this, selectedModel)
            Utils.toast(this, "配置已成功保存")
            finish() // 关闭当前页面
        }


    }
}