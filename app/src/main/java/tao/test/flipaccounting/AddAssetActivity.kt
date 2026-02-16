package tao.test.flipaccounting

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class AddAssetActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var etName: EditText
    private var selectedIconUrl: String = ""

    // 实际保存到 Asset 对象的货币代码，默认为人民币
    private var finalCurrency: String = "CNY"

    // 核心变量：记录上一次自动填充的名字
    private var lastAutoFilledName: String = ""

    inner class IconPickerAdapter(
        private val icons: List<BuiltInCategory>,
        private val onSelect: (BuiltInCategory) -> Unit
    ) : RecyclerView.Adapter<IconPickerAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView = v.findViewById(R.id.iv_asset_icon)
            val tv: TextView = v.findViewById(R.id.tv_asset_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_asset_grid, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = icons[position]
            holder.tv.text = item.name
            Glide.with(holder.itemView).load(item.icon).transform(CircleCrop()).into(holder.iv)

            holder.itemView.setOnClickListener { onSelect(item) }
        }
        override fun getItemCount() = icons.size
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_asset)

        ivPreview = findViewById(R.id.iv_preview)
        etName = findViewById(R.id.et_name)
        val spType = findViewById<Spinner>(R.id.sp_type)
        val spCurrency = findViewById<Spinner>(R.id.sp_currency)
        val rvIcons = findViewById<RecyclerView>(R.id.rv_icon_list)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 1. 加载 assets.json 中的图标数据
        val builtInIcons = loadAssetsJson()

        // 2. 设置下方图标列表
        rvIcons.layoutManager = GridLayoutManager(this, 5)
        rvIcons.adapter = IconPickerAdapter(builtInIcons) { selected ->
            selectedIconUrl = selected.icon
            Glide.with(this).load(selected.icon).transform(CircleCrop()).into(ivPreview)

            // 自动填充名字逻辑
            val currentText = etName.text.toString().trim()
            if (currentText.isEmpty() || currentText == lastAutoFilledName) {
                etName.setText(selected.name)
                etName.setSelection(selected.name.length)
                lastAutoFilledName = selected.name
            }
        }

        // 3. 货币选择逻辑（含自定义功能）
        spCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position).toString()
                if (selected == "自定义") {
                    showCustomCurrencyDialog(spCurrency)
                } else {
                    // 提取前面的代号，例如从 "CNY - 人民币" 提取 "CNY"
                    finalCurrency = selected.split(" - ")[0]
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. 保存逻辑
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Utils.toast(this, "请输入资产名称")
                return@setOnClickListener
            }

            val assets = Prefs.getAssets(this).toMutableList()
            if (assets.any { it.name == name }) {
                Utils.toast(this, "资产名称已存在")
                return@setOnClickListener
            }

            val newAsset = Asset(
                name = name,
                type = spType.selectedItem.toString(),
                currency = finalCurrency, // 使用最终确定的货币代码
                icon = selectedIconUrl
            )
            assets.add(newAsset)
            Prefs.saveAssets(this, assets)
            Utils.toast(this, "添加成功")
            finish()
        }
    }

    /**
     * 弹出自定义货币输入对话框
     */
    private fun showCustomCurrencyDialog(spinner: Spinner) {
        val et = EditText(this)
        et.hint = "输入货币代码 (如: CAD)"
        et.setSingleLine()

        // 给 EditText 加点边距
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(60, 20, 60, 10)
        et.layoutParams = params
        container.addView(et)

        AlertDialog.Builder(this)
            .setTitle("自定义货币")
            .setMessage("请输入三个字母的货币代码")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("确定") { _, _ ->
                val input = et.text.toString().trim().uppercase()
                if (input.length >= 2) {
                    finalCurrency = input
                    Utils.toast(this, "已设定货币为: $input")
                } else {
                    Utils.toast(this, "请输入有效的货币代码")
                    spinner.setSelection(0) // 回退到人民币
                }
            }
            .setNegativeButton("取消") { _, _ ->
                spinner.setSelection(0)
            }
            .show()
    }

    private fun loadAssetsJson(): List<BuiltInCategory> {
        val list = mutableListOf<BuiltInCategory>()
        try {
            val inputStream = resources.openRawResource(R.raw.assets)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.use { it.readText() }
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(BuiltInCategory(
                    name = obj.getString("name"),
                    icon = obj.getString("icon")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}