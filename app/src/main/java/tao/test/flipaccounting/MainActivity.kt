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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // --- 请求必要权限 ---
        checkAndRequestPermissions()

        // --- 0. 初始化状态 ---
        val isHide = Prefs.isHideRecents(this)
        setExcludeFromRecents(isHide)

        findViewById<View>(R.id.nav_ai_config).setOnClickListener {
            startActivity(Intent(this, AIConfigActivity::class.java))
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