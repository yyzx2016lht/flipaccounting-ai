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
    private const val KEY_CAT_EXPENSE = "cat_expense_v1"
    private const val KEY_CAT_INCOME = "cat_income_v1"
    private const val KEY_AI_KEY = "ai_api_key"
    private const val KEY_AI_MODEL = "ai_model_id"

    const val TYPE_EXPENSE = 1
    const val TYPE_INCOME = 2

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // --- 基础配置 ---
    fun isFlipEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_FLIP_ENABLED, false)
    fun setFlipEnabled(ctx: Context, enabled: Boolean) = prefs(ctx).edit().putBoolean(KEY_FLIP_ENABLED, enabled).apply()


    fun isHideRecents(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HIDE_RECENTS, false)
    fun setHideRecents(ctx: Context, hide: Boolean) = prefs(ctx).edit().putBoolean(KEY_HIDE_RECENTS, hide).apply()

    // --- AI 配置 ---
    fun getAiKey(ctx: Context): String = prefs(ctx).getString(KEY_AI_KEY, "") ?: ""
    fun setAiKey(ctx: Context, key: String) = prefs(ctx).edit().putString(KEY_AI_KEY, key).apply()

    fun getAiModel(ctx: Context): String = prefs(ctx).getString(KEY_AI_MODEL, "Qwen/Qwen2.5-7B-Instruct") ?: "Qwen/Qwen2.5-7B-Instruct"
    fun setAiModel(ctx: Context, model: String) = prefs(ctx).edit().putString(KEY_AI_MODEL, model).apply()

    // --- 应用白名单管理 ---
    fun getAppWhiteList(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_WHITE_LIST, emptySet()) ?: emptySet()
    fun saveAppWhiteList(ctx: Context, list: Set<String>) = prefs(ctx).edit().putStringSet(KEY_WHITE_LIST, list).apply()

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
            return if (type == TYPE_INCOME) getDefaultIncome() else getDefaultExpense()
        }
        val list = mutableListOf<CategoryNode>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(parseNode(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            return if (type == TYPE_INCOME) getDefaultIncome() else getDefaultExpense()
        }
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
        val subArray = obj.optJSONArray("subs")
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

    fun importRawData(ctx: Context, root: JSONObject) {
        val edit = prefs(ctx).edit()
        if (root.has(KEY_ASSETS)) edit.putString(KEY_ASSETS, root.get(KEY_ASSETS).toString())
        if (root.has(KEY_CAT_EXPENSE)) edit.putString(KEY_CAT_EXPENSE, root.get(KEY_CAT_EXPENSE).toString())
        if (root.has(KEY_CAT_INCOME)) edit.putString(KEY_CAT_INCOME, root.get(KEY_CAT_INCOME).toString())
        if (root.has(KEY_WHITE_LIST)) {
            val set = mutableSetOf<String>()
            val arr = root.getJSONArray(KEY_WHITE_LIST)
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            edit.putStringSet(KEY_WHITE_LIST, set)
        }
        if (root.has(KEY_FLIP_ENABLED)) edit.putBoolean(KEY_FLIP_ENABLED, root.getBoolean(KEY_FLIP_ENABLED))
        if (root.has(KEY_BACK_TAP_ENABLED)) edit.putBoolean(KEY_BACK_TAP_ENABLED, root.getBoolean(KEY_BACK_TAP_ENABLED))
        if (root.has(KEY_HIDE_RECENTS)) edit.putBoolean(KEY_HIDE_RECENTS, root.getBoolean(KEY_HIDE_RECENTS))
        edit.apply()
    }

    // --- 默认数据 ---
    private fun getDefaultExpense() = mutableListOf(
        CategoryNode("餐饮", "http://res3.qianjiapp.com/catev2/cate_icon_canyin.png", mutableListOf(
            CategoryNode("早餐", "http://res3.qianjiapp.com/catev2/cate_icon_zaocan.png"),
            CategoryNode("午餐", "http://res3.qianjiapp.com/catev2/cate_icon_wucan.png"),
            CategoryNode("晚餐", "http://res3.qianjiapp.com/catev2/cate_icon_wancan.png")
        )),
        CategoryNode("交通", "http://res3.qianjiapp.com/catev2/cate_icon_jiaotong.png"),
        CategoryNode("购物", "http://res3.qianjiapp.com/catev2/cate_icon_gouwu.png"),
        CategoryNode("娱乐", "http://res3.qianjiapp.com/catev2/cate_icon_yule.png")
    )

    private fun getDefaultIncome() = mutableListOf(
        CategoryNode("工资", "http://res3.qianjiapp.com/cateic_gongzi.png"),
        CategoryNode("兼职", "http://res3.qianjiapp.com/cateic_jianzhi.png"),
        CategoryNode("理财", "http://res3.qianjiapp.com/cateic_licai.png"),
        CategoryNode("礼金", "http://res3.qianjiapp.com/cateic_lijin.png")
    )
}