package tao.test.flipaccounting

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku

class AppListActivity : AppCompatActivity() {

    private val selectedPackageNames = mutableSetOf<String>()
    private lateinit var allFilteredApps: List<ApplicationInfo> // 保存完整列表用于搜索恢复
    private lateinit var adapter: ArrayAdapter<String>
    private val displayNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        checkShizukuStatus()

        val listView = findViewById<ListView>(R.id.lv_apps)
        val etSearch = findViewById<EditText>(R.id.et_search_app)
        val pm = packageManager

        // 1. 先初始化白名单数据 (关键步骤：移到获取应用列表之前)
        selectedPackageNames.addAll(Prefs.getAppWhiteList(this))

        // 2. 获取并排序应用
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        allFilteredApps = allApps.filter { app ->
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val hasLauncher = pm.getLaunchIntentForPackage(app.packageName) != null
            app.packageName != packageName && (hasLauncher || !isSystemApp)
        }.sortedWith(
            // 核心修改：自定义比较器
            compareByDescending<ApplicationInfo> {
                // 规则1：已选中的排在前面 (Boolean true > false)
                selectedPackageNames.contains(it.packageName)
            }.thenBy {
                // 规则2：按名称排序
                it.loadLabel(pm).toString()
            }
        )

        // 3. 设置适配器
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, displayNames)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        // 初次显示
        updateListView(allFilteredApps)

        // 4. 点击事件：实时更新选中状态
        listView.setOnItemClickListener { _, _, position, _ ->
            // 注意：这里取的是当前 adapter 显示的数据对应的包名
            // 因为有搜索功能，必须从当前显示的列表中找
            // 为了简化逻辑，我们在 updateListView 时会同步 filteredList，这里假设 adapter 数据与 filteredList 一一对应
            // 更好的做法是搜索时维护一个 currentList

            // 由于上面逻辑稍显复杂，我们简化处理：通过 adapter 获取当前点击项的名字，反查 app (这在同名app时有风险，但概率低)
            // 更稳妥的方式：
            val app = currentDisplayingApps[position]
            val pkg = app.packageName

            if (listView.isItemChecked(position)) {
                selectedPackageNames.add(pkg)
            } else {
                selectedPackageNames.remove(pkg)
            }
        }

        // 5. 搜索逻辑
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s.toString().lowercase()
                val searchResult = allFilteredApps.filter {
                    it.loadLabel(pm).toString().lowercase().contains(keyword) ||
                            it.packageName.lowercase().contains(keyword)
                }
                updateListView(searchResult)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 6. 保存逻辑
        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            Prefs.saveAppWhiteList(this, selectedPackageNames)
            Utils.toast(this, "白名单已保存 (${selectedPackageNames.size}个应用)")
            finish()
        }
    }

    private var currentDisplayingApps: List<ApplicationInfo> = emptyList()

    private fun updateListView(list: List<ApplicationInfo>) {
        currentDisplayingApps = list
        displayNames.clear()
        val pm = packageManager
        list.forEach { displayNames.add(it.loadLabel(pm).toString()) }
        adapter.notifyDataSetChanged()

        // 恢复勾选状态
        val listView = findViewById<ListView>(R.id.lv_apps)
        list.forEachIndexed { index, app ->
            if (selectedPackageNames.contains(app.packageName)) {
                listView.setItemChecked(index, true)
            } else {
                listView.setItemChecked(index, false)
            }
        }
    }

    // ... checkShizukuStatus 保持不变 ...
    private fun checkShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            Utils.toast(this, "提示：Shizuku 未运行，白名单功能将失效")
        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Utils.toast(this, "提示：尚未获得 Shizuku 授权")
        }
    }
}