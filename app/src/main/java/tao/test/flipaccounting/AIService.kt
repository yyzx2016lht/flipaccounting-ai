package tao.test.flipaccounting

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// --- 数据模型 ---
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3,
    val response_format: ResponseFormat? = ResponseFormat("json_object")
)

data class Message(val role: String, val content: String)
data class ResponseFormat(val type: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)
data class AudioResponse(val text: String)

// --- API 接口 ---
interface SiliconFlowApi {
    @GET("v1/models")
    suspend fun getModels(@Header("Authorization") auth: String): JsonObject

    @POST("v1/chat/completions")
    suspend fun chat(@Header("Authorization") auth: String, @Body body: ChatRequest): ChatResponse

    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") auth: String,
        @Part model: MultipartBody.Part,
        @Part file: MultipartBody.Part
    ): AudioResponse
}

// --- 服务实现 ---
object AIService {
  const val DEFAULT_PROMPT = """const val DEFAULT_PROMPT = ""${'"'}你是一个【严格的数据匹配助手】。你的任务是将用户的**单条**自然语言映射到提供的【固定选项列表】中。

【当前基准时间】: {{TIME}}

【选项列表 (必须严格从以下数据中选择)】
1. 可用资产库: {{ASSETS}}
2. 支出分类库: {{EXPENSE_CATS}} 
   (结构说明: 包含父分类和子分类。请优先匹配最精准的子分类)
3. 收入分类库: {{INCOME_CATS}}
4. 可用币种库: {{CURRENCIES}} (默认 CNY)

【Type (记账类型) 严格定义】
- 0: 支出 (默认。消费、购物、**发红包**)
- 1: 收入 (工资、**收红包**、退款)
- 2: 转账 (资金在两个自有账户间流动)
- 3: 还款 (偿还花呗、白条、信用卡等债务)

【核心匹配逻辑 (Matching Logic)】
1. **Category (分类) - 格式必须严格遵守钱迹规范**:
   - **格式要求**: 必须返回 `父分类名称/::/子分类名称`。
     - 示例: 若匹配到父类 "吃的" 下的子类 "三餐"，必须返回 `"吃的/::/三餐"`。
     - 特例: 若匹配到的父类没有子分类 (如 "发红包")，则直接返回 `"发红包"`。
   - **红包专项规则**: 
     - 动作含 "发/给/塞" 红包 -> 匹配支出库 **"发红包"** (Type 0)。
     - 动作含 "收/领/抢" 红包 -> 匹配收入库 **"收红包"** (Type 1)。
   - **通用规则**: 必须在库中找到语义最匹配的一项。**禁止创造分类名**。
   - 转账(Type 2)和还款(Type 3)分类返回空字符串 ""。

2. **Asset (资产)**:
   - `asset_name`: 支付来源 / 收款账户 / 转出方 / 还款付款方。
   - `to_asset_name`: 仅用于 转账的转入方 / 还款的还款对象。
   - **约束**: 必须严格从【可用资产库】中提取 (如 "微信", "支付宝", "招商银行")。若未提及或找不到，返回 ""。

3. **Currency (币种)**:
   - 默认 "CNY"。若提及其他币种，返回对应的 3字母代码 (如 USD)。

4. **remarks(备注)**:
   - 从文本中提取简短的概念作为备注，例如我刚刚去超市买菜花了十块钱，提取超市买菜
【JSON 输出格式】
{"amount":0.0, "type":0, "asset_name":"", "to_asset_name":"", "category_name":"", "time":"", "remarks":"", "currency":"CNY"}

【格式演示 (Few-Shot)】
警告：以下示例仅演示 `/::/` 格式，实际分类名请以上方列表为准。

输入: "吃拉面20"
(假设库中有: "吃的" > "三餐")
输出: {"amount":20.0, "type":0, "asset_name":"", "category_name":"吃的/::/三餐", "time":"...", "remarks":"吃拉面", "currency":"CNY"}

输入: "给小明发了500红包"
(假设库中有: "发红包", 无子分类)
输出: {"amount":500.0, "type":0, "asset_name":"", "category_name":"发红包", "time":"...", "remarks":"给小明发红包", "currency":"CNY"}

输入: "打车去公司30元"
(假设库中有: "交通" > "打车")
输出: {"amount":30.0, "type":0, "asset_name":"", "category_name":"交通/::/打车", "time":"...", "remarks":"打车去公司", "currency":"CNY"}

输入: "招行还花呗2000"
输出: {"amount":2000.0, "type":3, "asset_name":"招商银行", "to_asset_name":"花呗", "category_name":"", "time":"...", "remarks":"还款", "currency":"CNY"}""${'"'}"""
    private fun getApi(ctx: Context): SiliconFlowApi {
        var baseUrl = Prefs.getAiUrl(ctx)
        if (baseUrl.isEmpty()) {
            baseUrl = "https://api.siliconflow.cn/"
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SiliconFlowApi::class.java)
    }

    /**
     * 语音转文字：使用 TeleAI/TeleSpeechASR 模型
     */
    suspend fun speechToText(ctx: Context, audioFile: File): String? {
        val apiKey = Prefs.getAiKey(ctx)
        if (apiKey.isEmpty()) return null

        return try {
            // 1. 准备文件 RequestBody
            val requestFile = okhttp3.RequestBody.create("audio/mpeg".toMediaTypeOrNull(), audioFile)
            val filePart = okhttp3.MultipartBody.Part.createFormData("file", audioFile.name, requestFile)

            // 2. 关键修改：设置正确的模型名称
            // 根据你的 cURL: model=FunAudioLLM/SenseVoiceSmall
            val modelPart = okhttp3.MultipartBody.Part.createFormData("model", "FunAudioLLM/SenseVoiceSmall")

            // 3. 发送请求
            val response = getApi(ctx).transcribe("Bearer $apiKey", modelPart, filePart)
            response.text
        } catch (e: Exception) {
            e.printStackTrace()
            // 可以在这里打 Log 查看具体报错
            null
        }
    }
    // 核心：分析记账文本
    // 核心：分析记账文本
    suspend fun analyzeAccounting(ctx: Context, userInput: String): JSONObject? {
        Logger.d(ctx, "AIService", "Analyzing: $userInput")
        val apiKey = Prefs.getAiKey(ctx)
        val model = Prefs.getAiModel(ctx)
        if (apiKey.isEmpty()) return null

        // 1. 准备数据
        val assets = Prefs.getAssets(ctx).map { it.name }
        val currencies = Prefs.getActiveCurrencies(ctx).toList()
        // 为了提高 token 利用率，我们将分类拍扁，但保留层级结构字符串
        val expenseCats = Prefs.getCategories(ctx, Prefs.TYPE_EXPENSE).flatMap { parent ->
            if (parent.subs.isEmpty()) listOf(parent.name)
            else parent.subs.map { "${"$"}{parent.name} > ${"$"}{it.name}" }
        }
        val incomeCats = Prefs.getCategories(ctx, Prefs.TYPE_INCOME).flatMap { parent ->
            if (parent.subs.isEmpty()) listOf(parent.name)
            else parent.subs.map { "${"$"}{parent.name} > ${"$"}{it.name}" }
        }
        val demoAsset = assets.firstOrNull() ?: "微信"
        val demoExpenseCat = expenseCats.firstOrNull() ?: "吃的"
        val demoIncomeCat = incomeCats.firstOrNull() ?: "工资"
        // 2. 时间锚点：格式化为 "2026-02-15 14:30:00 (星期日)"
        val now = Date()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val weekFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val currentTimeStr = "${"$"}{timeFormat.format(now)} (${"$"}{weekFormat.format(now)})"

        // 3. 构建 System Prompt
        var p = Prefs.getAiPrompt(ctx)
        if (p.isEmpty()) {
            p = DEFAULT_PROMPT
        }
        
        val systemPrompt = p.replace("{{TIME}}", currentTimeStr)
            .replace("{{ASSETS}}", Gson().toJson(assets))
            .replace("{{EXPENSE_CATS}}", Gson().toJson(expenseCats))
            .replace("{{INCOME_CATS}}", Gson().toJson(incomeCats))
            .replace("{{CURRENCIES}}", Gson().toJson(currencies))
            .replace("{{DEMO_ASSET}}", demoAsset)
            .replace("{{DEMO_EXPENSE_CAT}}", demoExpenseCat)
            .replace("{{DEMO_INCOME_CAT}}", demoIncomeCat)

        // 4. 发送请求
        return try {
            val messages = listOf(
                Message("system", systemPrompt),
                Message("user", userInput)
            )
            val request = ChatRequest(model, messages)
            Logger.d(ctx, "AIService", "Requesting AI: $model")
            val response = getApi(ctx).chat("Bearer $apiKey", request)
            // 注意：API 返回的 content 可能是字符串形式的 JSON，或者是直接对象
            // 硅基流动通常返回字符串格式，需要解析
            val content = response.choices.first().message.content
            Logger.d(ctx, "AIService", "AI Response: $content")
            JSONObject(content)
        } catch (e: Exception) {
            Logger.d(ctx, "AIService", "AI Request Failed: ${"$"}{e.message}")
            e.printStackTrace()
            null
        }
    }

    // 辅助：简化分类结构，减少 Token 消耗
    private fun flattenCategory(node: CategoryNode): Any {
        if (node.subs.isEmpty()) return node.name
        // 返回 "父类: [子类1, 子类2]" 的形式
        return mapOf(node.name to node.subs.map { it.name })
    }
    // AIService.kt 内部

    /**
     * 获取模型列表：用于 AI 配置页面的连接测试
     */
    suspend fun fetchModels(ctx: Context, apiKey: String): List<String> {
        return try {
            val response = getApi(ctx).getModels("Bearer $apiKey")
            val data = response.getAsJsonArray("data")
            data.map { it.asJsonObject.get("id").asString }.sorted()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
