package tao.test.flipaccounting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 核心数据模型
 */
data class CategoryNode(
    val name: String,
    val icon: String,
    val subs: MutableList<CategoryNode> = mutableListOf()
)

data class Asset(
    val name: String,
    val type: String,
    val currency: String,
    val icon: String = ""
)

data class Bill(
    val amount: Double,
    val type: Int, // 0-支出, 1-收入, 2-转账, 3-还款
    val assetName: String,
    val categoryName: String,
    val time: String,
    val remarks: String = "",
    val iconUrl: String = "",
    val recordTime: String = "" // 实际记账记录生成的时间
)

/**
 * 数据持久化管理类
 */
object Prefs {
    private const val NAME = "flip_prefs"

    // --- Key 定义 ---
    private const val KEY_FLIP_ENABLED = "flip_enabled"
    private const val KEY_BACK_TAP_ENABLED = "back_tap_enabled" // [新增]
    private const val KEY_WHITE_LIST = "app_white_list"
    private const val KEY_ASSETS = "assets_v1"
    private const val KEY_HIDE_RECENTS = "hide_recents_card"
    private const val KEY_LOGGING_ENABLED = "logging_enabled"
    private const val KEY_CAT_EXPENSE = "cat_expense_v1"
    private const val KEY_CAT_INCOME = "cat_income_v1"
    private const val KEY_AI_KEY = "ai_api_key"
    private const val KEY_AI_MODEL = "ai_model_id"
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_AI_URL = "ai_api_url"
    private const val KEY_AI_PROMPT = "ai_system_prompt"
    private const val KEY_BILLS = "bills_list"
    private const val KEY_FLIP_SENSITIVITY = "flip_sensitivity_level" // 0-100 progress
    private const val KEY_FLIP_DURATION = "flip_duration_threshold" // Long
    private const val KEY_USE_CUSTOM_SENSITIVITY = "use_custom_sensitivity"
    private const val KEY_CUSTOM_G_THRESHOLD = "custom_g_threshold"
    private const val KEY_CUSTOM_MAX_DURATION = "custom_max_duration"
    private const val KEY_ACTIVE_CURRENCIES = "active_currencies_v1"
    private const val KEY_EXCHANGE_REFRESH_INTERVAL = "exchange_refresh_interval_v1"

    private const val KEY_SHOW_AI_TEXT = "show_ai_text"
    private const val KEY_SHOW_AI_VOICE = "show_ai_voice"
    private const val KEY_SHOW_MULTI_CURRENCY = "show_multi_currency"

    const val TYPE_EXPENSE = 1
    const val TYPE_INCOME = 2

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // --- 基础配置 ---
    fun isFlipEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_FLIP_ENABLED, false)
    fun setFlipEnabled(ctx: Context, enabled: Boolean) = prefs(ctx).edit().putBoolean(KEY_FLIP_ENABLED, enabled).apply()

    fun isBackTapEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BACK_TAP_ENABLED, false)
    fun setBackTapEnabled(ctx: Context, enabled: Boolean) = prefs(ctx).edit().putBoolean(KEY_BACK_TAP_ENABLED, enabled).apply()


    fun isHideRecents(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HIDE_RECENTS, false)
    fun setHideRecents(ctx: Context, hide: Boolean) = prefs(ctx).edit().putBoolean(KEY_HIDE_RECENTS, hide).apply()

    fun isLoggingEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_LOGGING_ENABLED, false)
    fun setLoggingEnabled(ctx: Context, enabled: Boolean) = prefs(ctx).edit().putBoolean(KEY_LOGGING_ENABLED, enabled).apply()

    // --- AI 配置 ---
    fun getAiKey(ctx: Context): String = prefs(ctx).getString(KEY_AI_KEY, "") ?: ""
    fun setAiKey(ctx: Context, key: String) = prefs(ctx).edit().putString(KEY_AI_KEY, key).apply()

    fun getAiModel(ctx: Context): String = prefs(ctx).getString(KEY_AI_MODEL, "Qwen/Qwen2.5-7B-Instruct") ?: "Qwen/Qwen2.5-7B-Instruct"
    fun setAiModel(ctx: Context, model: String) = prefs(ctx).edit().putString(KEY_AI_MODEL, model).apply()

    fun getAiProvider(ctx: Context): String = prefs(ctx).getString(KEY_AI_PROVIDER, "硅基流动") ?: "硅基流动"
    fun setAiProvider(ctx: Context, provider: String) = prefs(ctx).edit().putString(KEY_AI_PROVIDER, provider).apply()

    fun getAiUrl(ctx: Context): String = prefs(ctx).getString(KEY_AI_URL, "https://api.siliconflow.cn") ?: "https://api.siliconflow.cn"
    fun setAiUrl(ctx: Context, url: String) = prefs(ctx).edit().putString(KEY_AI_URL, url).apply()

    fun getAiPrompt(ctx: Context): String = prefs(ctx).getString(KEY_AI_PROMPT, "") ?: ""
    fun setAiPrompt(ctx: Context, prompt: String) = prefs(ctx).edit().putString(KEY_AI_PROMPT, prompt).apply()

    // --- 账单管理 ---
    fun addBill(ctx: Context, bill: Bill) {
        val list = getBills(ctx).toMutableList()
        list.add(bill)
        val json = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("amount", it.amount)
            obj.put("type", it.type)
            obj.put("assetName", it.assetName)
            obj.put("categoryName", it.categoryName)
            obj.put("time", it.time)
            obj.put("remarks", it.remarks)
            obj.put("iconUrl", it.iconUrl)
            obj.put("recordTime", it.recordTime)
            json.put(obj)
        }
        prefs(ctx).edit().putString(KEY_BILLS, json.toString()).apply()
    }

    fun deleteBills(ctx: Context, billsToDelete: Collection<Bill>) {
        val list = getBills(ctx).toMutableList()
        val toDeleteHashes = billsToDelete.map { "${it.time}_${it.amount}_${it.categoryName}_${it.assetName}" }.toSet()
        
        list.removeAll { item ->
            val hash = "${item.time}_${item.amount}_${item.categoryName}_${item.assetName}"
            toDeleteHashes.contains(hash)
        }
        
        val json = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("amount", it.amount)
            obj.put("type", it.type)
            obj.put("assetName", it.assetName)
            obj.put("categoryName", it.categoryName)
            obj.put("time", it.time)
            obj.put("remarks", it.remarks)
            obj.put("iconUrl", it.iconUrl)
            obj.put("recordTime", it.recordTime)
            json.put(obj)
        }
        prefs(ctx).edit().putString(KEY_BILLS, json.toString()).apply()
    }

    fun deleteBill(ctx: Context, bill: Bill) {
        val list = getBills(ctx).toMutableList()
        // 精确匹配删除：通过时间、金额、分类和资产名称来识别
        val iterator = list.iterator()
        while(iterator.hasNext()) {
            val item = iterator.next()
            if (item.time == bill.time && item.amount == bill.amount && 
                item.categoryName == bill.categoryName && item.assetName == bill.assetName) {
                iterator.remove()
                break
            }
        }
        val json = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("amount", it.amount)
            obj.put("type", it.type)
            obj.put("assetName", it.assetName)
            obj.put("categoryName", it.categoryName)
            obj.put("time", it.time)
            obj.put("remarks", it.remarks)
            obj.put("iconUrl", it.iconUrl)
            obj.put("recordTime", it.recordTime)
            json.put(obj)
        }
        prefs(ctx).edit().putString(KEY_BILLS, json.toString()).apply()
    }

    fun getBills(ctx: Context): List<Bill> {
        val str = prefs(ctx).getString(KEY_BILLS, null) ?: return emptyList()
        val list = mutableListOf<Bill>()
        try {
            val json = JSONArray(str)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(Bill(
                    obj.getDouble("amount"),
                    obj.getInt("type"),
                    obj.getString("assetName"),
                    obj.getString("categoryName"),
                    obj.getString("time"),
                    obj.optString("remarks", ""),
                    obj.optString("iconUrl", ""),
                    obj.optString("recordTime", "")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- 应用白名单管理 ---
    fun getAppWhiteList(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_WHITE_LIST, emptySet()) ?: emptySet()
    fun setAppWhiteList(ctx: Context, list: Set<String>) = prefs(ctx).edit().putStringSet(KEY_WHITE_LIST, list).apply()

    // --- 货币与汇率管理 ---
    fun getActiveCurrencies(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_ACTIVE_CURRENCIES, setOf("CNY")) ?: setOf("CNY")
    fun setActiveCurrencies(ctx: Context, currencies: Set<String>) = prefs(ctx).edit().putStringSet(KEY_ACTIVE_CURRENCIES, currencies).apply()

    fun getExchangeRefreshInterval(ctx: Context): Long = prefs(ctx).getLong(KEY_EXCHANGE_REFRESH_INTERVAL, 12 * 3600 * 1000L) // 默认12小时
    fun setExchangeRefreshInterval(ctx: Context, interval: Long) = prefs(ctx).edit().putLong(KEY_EXCHANGE_REFRESH_INTERVAL, interval).apply()

    // --- 显示控制 ---
    fun isShowAiText(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_AI_TEXT, false)
    fun setShowAiText(ctx: Context, show: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_AI_TEXT, show).apply()

    fun isShowAiVoice(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_AI_VOICE, false)
    fun setShowAiVoice(ctx: Context, show: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_AI_VOICE, show).apply()

    fun isShowMultiCurrency(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_MULTI_CURRENCY, false)
    fun setShowMultiCurrency(ctx: Context, show: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_MULTI_CURRENCY, show).apply()

    // --- 资产管理 ---
    fun getAssets(ctx: Context): List<Asset> {
        val json = prefs(ctx).getString(KEY_ASSETS, "")
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<Asset>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Asset(
                    obj.getString("name"),
                    obj.getString("type"),
                    obj.getString("currency"),
                    obj.optString("icon", "")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveAssets(ctx: Context, assets: List<Asset>) {
        prefs(ctx).edit().putString(KEY_ASSETS, serializeAssetList(assets).toString()).apply()
    }

    // --- 分类管理 ---
    fun getCategories(ctx: Context, type: Int): MutableList<CategoryNode> {
        val key = if (type == TYPE_INCOME) KEY_CAT_INCOME else KEY_CAT_EXPENSE
        val json = prefs(ctx).getString(key, "")
        if (json.isNullOrEmpty()) {
            return loadDefaultFromRaw(ctx, type)
        }
        val list = mutableListOf<CategoryNode>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(parseNode(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            return loadDefaultFromRaw(ctx, type)
        }
        return list
    }

    fun loadDefaultFromRaw(ctx: Context, type: Int): MutableList<CategoryNode> {
        return try {
            val stream = ctx.resources.openRawResource(R.raw.default_category)
            val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(content)
            val list = mutableListOf<CategoryNode>()
            
            // 尝试多个可能的 Key，防止 JSON 编码或结构变化导致找不到
            // 钱迹自带格式一般为 "支出" 和 "收入"
            val key = if (type == TYPE_INCOME) "收入" else "支出"
            var array = root.optJSONArray(key)
            
            // 兜底方案：如果找不到对应的 Key，尝试另一个 Key (有些导出可能不带类型)
            if (array == null) {
                array = root.optJSONArray(if (type == TYPE_INCOME) "支出" else "收入")
            }
            
            if (array != null) {
                for (i in 0 until array.length()) {
                    list.add(parseNode(array.getJSONObject(i)))
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    fun loadAssetsFromRaw(ctx: Context): List<BuiltInCategory> {
        val list = mutableListOf<BuiltInCategory>()
        try {
            val stream = ctx.resources.openRawResource(R.raw.assets)
            val content = stream.bufferedReader().use { it.readText() }
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(BuiltInCategory(obj.getString("name"), obj.getString("icon")))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun saveCategories(ctx: Context, type: Int, list: List<CategoryNode>) {
        val key = if (type == TYPE_INCOME) KEY_CAT_INCOME else KEY_CAT_EXPENSE
        prefs(ctx).edit().putString(key, serializeCategoryList(list).toString()).apply()
    }

    fun deleteCategory(ctx: Context, type: Int, name: String) {
        val list = getCategories(ctx, type)
        if (recursiveDelete(list, name)) {
            saveCategories(ctx, type, list)
        }
    }

    // --- 内部解析与序列化逻辑 ---
    private fun parseNode(obj: JSONObject): CategoryNode {
        val node = CategoryNode(obj.getString("name"), obj.getString("icon"))
        // 同时支持 "subs" 和 "sub" (json 文件中是 sub)
        val subArray = obj.optJSONArray("subs") ?: obj.optJSONArray("sub")
        if (subArray != null) {
            for (i in 0 until subArray.length()) {
                node.subs.add(parseNode(subArray.getJSONObject(i)))
            }
        }
        return node
    }

    private fun serializeNode(node: CategoryNode): JSONObject {
        val obj = JSONObject()
        obj.put("name", node.name)
        obj.put("icon", node.icon)
        val subArray = JSONArray()
        node.subs.forEach { subArray.put(serializeNode(it)) }
        obj.put("subs", subArray)
        return obj
    }

    private fun recursiveDelete(list: MutableList<CategoryNode>, targetName: String): Boolean {
        val removed = list.removeIf { it.name == targetName }
        if (removed) return true
        for (node in list) {
            if (recursiveDelete(node.subs, targetName)) return true
        }
        return false
    }

    // --- 序列化与数据导入 ---
    fun serializeAssetList(assets: List<Asset>): JSONArray {
        val array = JSONArray()
        assets.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("type", it.type)
            obj.put("currency", it.currency)
            obj.put("icon", it.icon)
            array.put(obj)
        }
        return array
    }

    fun serializeCategoryList(list: List<CategoryNode>): JSONArray {
        val array = JSONArray()
        list.forEach { array.put(serializeNode(it)) }
        return array
    }

    fun importAll(ctx: Context, root: JSONObject) {
        val edit = prefs(ctx).edit()

        // 1. 资产
        if (root.has("assets_v1")) {
            edit.putString(KEY_ASSETS, root.get("assets_v1").toString())
        }

        // 2. 分类
        if (root.has("cat_expense_v1")) {
            edit.putString(KEY_CAT_EXPENSE, root.get("cat_expense_v1").toString())
        }
        if (root.has("cat_income_v1")) {
            edit.putString(KEY_CAT_INCOME, root.get("cat_income_v1").toString())
        }

        // 3. 账单 (支持 bills_v1 和旧版 bills_list)
        val billsJson = when {
            root.has("bills_v1") -> root.get("bills_v1").toString()
            root.has(KEY_BILLS) -> root.get(KEY_BILLS).toString()
            else -> null
        }
        if (billsJson != null) edit.putString(KEY_BILLS, billsJson)

        // 4. 白名单
        if (root.has("app_white_list_v1")) {
            try {
                val arr = JSONArray(root.getString("app_white_list_v1"))
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                edit.putStringSet(KEY_WHITE_LIST, set)
            } catch (e: Exception) {}
        } else if (root.has(KEY_WHITE_LIST)) {
            // 兼容旧版可能是直接的 JSONArray 字符串
            try {
                val obj = root.get(KEY_WHITE_LIST)
                if (obj is JSONArray) {
                    val set = mutableSetOf<String>()
                    for (i in 0 until obj.length()) set.add(obj.getString(i))
                    edit.putStringSet(KEY_WHITE_LIST, set)
                }
            } catch (e: Exception) {}
        }

        // 5. 多币种
        if (root.has("active_currencies_v1")) {
            val raw = root.getString("active_currencies_v1")
            val currencies = raw.split(",").filter { it.isNotBlank() }.toSet()
            if (currencies.isNotEmpty()) {
                edit.putStringSet(KEY_ACTIVE_CURRENCIES, currencies)
            }
        }
        if (root.has("exchange_refresh_interval_v1")) {
            edit.putLong(KEY_EXCHANGE_REFRESH_INTERVAL, root.getLong("exchange_refresh_interval_v1"))
        }

        // 6. 翻转设置 / 灵敏度
        if (root.has("flip_enabled_v1")) edit.putBoolean(KEY_FLIP_ENABLED, root.getBoolean("flip_enabled_v1"))
        if (root.has("flip_sensitivity_v1")) edit.putInt(KEY_FLIP_SENSITIVITY, root.getInt("flip_sensitivity_v1"))
        if (root.has("flip_debounce_v1")) edit.putLong(KEY_FLIP_DURATION, root.getLong("flip_debounce_v1"))
        if (root.has("use_custom_sensitivity_v1")) edit.putBoolean(KEY_USE_CUSTOM_SENSITIVITY, root.getBoolean("use_custom_sensitivity_v1"))
        if (root.has("custom_g_threshold_v1")) edit.putFloat(KEY_CUSTOM_G_THRESHOLD, root.getDouble("custom_g_threshold_v1").toFloat())
        if (root.has("custom_max_duration_v1")) edit.putLong(KEY_CUSTOM_MAX_DURATION, root.getLong("custom_max_duration_v1"))

        // 7. 其他偏好
        if (root.has("hide_recents_v1")) edit.putBoolean(KEY_HIDE_RECENTS, root.getBoolean("hide_recents_v1"))
        if (root.has("back_tap_enabled_v1")) edit.putBoolean(KEY_BACK_TAP_ENABLED, root.getBoolean("back_tap_enabled_v1"))

        // 8. 显示控制恢复
        if (root.has("show_ai_text_v1")) edit.putBoolean(KEY_SHOW_AI_TEXT, root.getBoolean("show_ai_text_v1"))
        if (root.has("show_ai_voice_v1")) edit.putBoolean(KEY_SHOW_AI_VOICE, root.getBoolean("show_ai_voice_v1"))
        if (root.has("show_multi_cur_v1")) edit.putBoolean(KEY_SHOW_MULTI_CURRENCY, root.getBoolean("show_multi_cur_v1"))

        // 9. AI 配置恢复
        if (root.has("ai_api_key_v1")) edit.putString(KEY_AI_KEY, root.getString("ai_api_key_v1"))
        if (root.has("ai_api_url_v1")) edit.putString(KEY_AI_URL, root.getString("ai_api_url_v1"))
        if (root.has("ai_model_id_v1")) edit.putString(KEY_AI_MODEL, root.getString("ai_model_id_v1"))
        if (root.has("ai_system_prompt_v1")) edit.putString(KEY_AI_PROMPT, root.getString("ai_system_prompt_v1"))

        edit.apply()
    }

    // --- 默认数据 ---
    private fun getDefaultExpense() = mutableListOf(
        CategoryNode("餐饮", "https://res3.qianjiapp.com/catev2/cate_icon_canyin.png", mutableListOf(
            CategoryNode("早餐", "https://res3.qianjiapp.com/catev2/cate_icon_zaocan.png"),
            CategoryNode("午餐", "https://res3.qianjiapp.com/catev2/cate_icon_wucan.png"),
            CategoryNode("晚餐", "https://res3.qianjiapp.com/catev2/cate_icon_wancan.png")
        )),
        CategoryNode("交通", "https://res3.qianjiapp.com/catev2/cate_icon_jiaotong.png"),
        CategoryNode("购物", "https://res3.qianjiapp.com/catev2/cate_icon_gouwu.png"),
        CategoryNode("娱乐", "https://res3.qianjiapp.com/catev2/cate_icon_yule.png")
    )

    private fun getDefaultIncome() = mutableListOf(
        CategoryNode("工资", "https://res3.qianjiapp.com/cateic_gongzi.png"),
        CategoryNode("兼职", "https://res3.qianjiapp.com/cateic_jianzhi.png"),
        CategoryNode("理财", "https://res3.qianjiapp.com/cateic_licai.png"),
        CategoryNode("礼金", "https://res3.qianjiapp.com/cateic_lijin.png")
    )

    // --- 翻转灵敏度 ---
    // 保存的是 Seekbar 的进度 0-100， 默认 50
    fun getFlipSensitivity(ctx: Context): Int = prefs(ctx).getInt(KEY_FLIP_SENSITIVITY, 50)
    fun setFlipSensitivity(ctx: Context, level: Int) = prefs(ctx).edit().putInt(KEY_FLIP_SENSITIVITY, level).apply()

    // 翻转时长阈值，默认 400ms
    fun getFlipDuration(ctx: Context): Long = prefs(ctx).getLong(KEY_FLIP_DURATION, 400L)
    fun setFlipDuration(ctx: Context, duration: Long) = prefs(ctx).edit().putLong(KEY_FLIP_DURATION, duration).apply()
    
    // --- 自定义灵敏度参数 ---
    fun isUseCustomSensitivity(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_USE_CUSTOM_SENSITIVITY, false)
    fun setUseCustomSensitivity(ctx: Context, use: Boolean) = prefs(ctx).edit().putBoolean(KEY_USE_CUSTOM_SENSITIVITY, use).apply()

    fun getCustomGThreshold(ctx: Context): Float = prefs(ctx).getFloat(KEY_CUSTOM_G_THRESHOLD, 7.25f)
    fun setCustomGThreshold(ctx: Context, g: Float) = prefs(ctx).edit().putFloat(KEY_CUSTOM_G_THRESHOLD, g).apply()

    fun getCustomMaxDuration(ctx: Context): Long = prefs(ctx).getLong(KEY_CUSTOM_MAX_DURATION, 550L)
    fun setCustomMaxDuration(ctx: Context, duration: Long) = prefs(ctx).edit().putLong(KEY_CUSTOM_MAX_DURATION, duration).apply()

    // --- 白名单序列化辅助 ---
    fun serializeWhiteList(set: Set<String>): String {
       return JSONArray(set).toString()
    }

    fun importWhiteList(ctx: Context, jsonStr: String) {
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for(i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            setAppWhiteList(ctx, list.toSet())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}