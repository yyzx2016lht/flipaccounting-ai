package tao.test.flipaccounting

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- 数据模型 ---
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.0,
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

    const val DEFAULT_PROMPT = """【任务角色】你是一个专业且严谨的个人账单数据提取助手。你的目标是从用户的自然语言输入中提取具体的、结构化的财务交易信息，并根据提供的资源库（分类、资产、币种）进行准确的语义映射。

【当前上下文】
1. 资产库: {{ASSETS}} 
2. 支出分类: {{EXPENSE_CATS}}
3. 收入分类: {{INCOME_CATS}}
4. 币种库: {{CURRENCIES}}
5. 基准日期（用于推算昨天、刚才等）: {{TIME}}

【提取与映射规则】
1. **分类匹配**: 必须依据语义从对应分类库（支出/收入）中选择最准确的项。如果是多级分类，请输出其完整格式（例如: "餐饮/::/午餐"）。
2. **资产映射**: 必须映射到提供的“资产库”中存在的名称。
3. **类型确认**: 
   - 0: 支出（支付、消费、买入、吃饭等）
   - 1: 收入（返现、退款、工资、收钱等）
   - 2: 转账（如“支付宝转到工行”）
   - 3: 还款（如“支出1000还信用卡”）
4. **时间转换**: 将所有描述（如“刚才”、“刚才吃完午餐”、“昨晚”）转换为格式 "yyyy-MM-dd HH:mm:ss"。
5. **详细备注**: 提取交易中的具体备注信息（如“汉堡王”、“手机贴膜”），如果没有具体内容，则用分类名代替。

【输出格式】
只输出一个合法的 JSON 对象，不包含代码块标记或多余的解释文字。
JSON 结构示例：
{"amount":0.0, "type":0, "asset_name":"微信", "category_name":"餐饮/::/晚餐", "time":"2026-02-18 19:00:00", "remarks":"晚餐", "currency":"CNY", "to_asset_name":"", "fee":0.0}
"""

    const val MULTI_BILL_PROMPT = """【任务角色】你是一个高效的批量账单提取助手。
你的任务是解析一段话中的多笔交易，并按照指定的资源库进行结构化格式化。

【当前上下文】
1. 资产库: {{ASSETS}} 
2. 支出分类: {{EXPENSE_CATS}}
3. 收入分类: {{INCOME_CATS}}
4. 币种库: {{CURRENCIES}}
5. 基准日期（用于推算）: {{TIME}}

【批量提取核心指令】
1. **分条处理**: 将每一笔具体的金额消费都解析为一个独立的账单对象。
2. **语义匹配**: 分类和资产必须严格匹配库中的现有名称（层级分类使用 /::/ 连接）。
3. **日期解析**: 基准日期是 {{TIME}}。请确保一段文字内的时间逻辑自洽（如“刚才”、“然后”等词的衔接）。
4. **备注细化**: 提取每项交易的具体备注内容。

【输出格式要求】
严格按照以下 JSON 格式输出结果：
{"bills": [{"amount":0.0, "type":0, "asset_name":"微信", "category_name":"餐饮/::/午餐", "time":"2026-02-18 12:30:00", "remarks":"午餐", "currency":"CNY"}]}
"""

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
     * 语音转文字：使用 SenseVoiceSmall 模型
     */
    suspend fun speechToText(ctx: Context, audioFile: File): String? {
        val apiKey = Prefs.getAiKey(ctx)
        if (apiKey.isEmpty()) return null

        return try {
            val requestFile = okhttp3.RequestBody.create("audio/mpeg".toMediaTypeOrNull(), audioFile)
            val filePart = okhttp3.MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
            val modelPart = okhttp3.MultipartBody.Part.createFormData("model", "FunAudioLLM/SenseVoiceSmall")

            val response = getApi(ctx).transcribe("Bearer $apiKey", modelPart, filePart)
            response.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 分析记账文本
     */
    suspend fun analyzeAccounting(ctx: Context, userInput: String, isMultiModeOverride: Boolean? = null): JSONObject? {
        Logger.d(ctx, "AIService", "Analyzing: $userInput")
        val apiKey = Prefs.getAiKey(ctx)
        val model = Prefs.getAiModel(ctx)
        if (apiKey.isEmpty()) return null

        val isMultiMode = isMultiModeOverride ?: Prefs.isMultiBillEnabled(ctx)

        // 1. 准备数据
        val assets = Prefs.getAssets(ctx).map { it.name }
        val currencies = Prefs.getActiveCurrencies(ctx).toList()
        
        val expenseCats = mutableListOf<String>()
        Prefs.getCategories(ctx, Prefs.TYPE_EXPENSE).forEach { parentNode ->
            if (parentNode.subs.isEmpty()) {
                expenseCats.add(parentNode.name)
            } else {
                parentNode.subs.forEach { childNode ->
                    expenseCats.add("${parentNode.name}/::/${childNode.name}")
                }
            }
        }
        
        val incomeCats = mutableListOf<String>()
        Prefs.getCategories(ctx, Prefs.TYPE_INCOME).forEach { parentNode ->
            if (parentNode.subs.isEmpty()) {
                incomeCats.add(parentNode.name)
            } else {
                parentNode.subs.forEach { childNode ->
                    incomeCats.add("${parentNode.name}/::/${childNode.name}")
                }
            }
        }

        val demoAsset = assets.firstOrNull() ?: "微信"
        val demoExpenseCat = expenseCats.firstOrNull() ?: "吃的"
        val demoIncomeCat = incomeCats.firstOrNull() ?: "工资"

        val now = Date()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val weekFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val currentTimeStr = "${timeFormat.format(now)} (${weekFormat.format(now)})"

        // 2. 构建 System Prompt
        var p = if (isMultiMode) Prefs.getMultiBillPrompt(ctx) else Prefs.getAiPrompt(ctx)
        if (p.isEmpty()) {
            p = if (isMultiMode) MULTI_BILL_PROMPT else DEFAULT_PROMPT
        }
        
        val systemPrompt = p.replace("{{TIME}}", currentTimeStr)
            .replace("{{ASSETS}}", Gson().toJson(assets))
            .replace("{{EXPENSE_CATS}}", Gson().toJson(expenseCats))
            .replace("{{INCOME_CATS}}", Gson().toJson(incomeCats))
            .replace("{{CURRENCIES}}", Gson().toJson(currencies))
            .replace("{{DEMO_ASSET}}", demoAsset)
            .replace("{{DEMO_EXPENSE_CAT}}", demoExpenseCat)
            .replace("{{DEMO_INCOME_CAT}}", demoIncomeCat)

        // 3. 发送请求
        return try {
            val messages = listOf(
                Message("system", systemPrompt),
                Message("user", userInput)
            )
            val request = ChatRequest(model, messages)
            val response = getApi(ctx).chat("Bearer $apiKey", request)
            
            val content = response.choices.first().message.content
            Logger.d(ctx, "AIService", "AI Response: $content")
            
            val result = if (isMultiMode) {
                val cleaned = cleanJsonString(content)
                val json = JSONObject(cleaned)
                if (!json.has("bills") && json.has("amount")) {
                   val wrapper = JSONObject()
                   wrapper.put("bills", JSONArray().put(json))
                   wrapper
                } else if (json.has("bills")) {
                   json
                } else {
                   null
                }
            } else {
                val cleaned = cleanJsonString(content)
                val json = JSONObject(cleaned)
                if (json.has("bills")) {
                    val bills = json.getJSONArray("bills")
                    if (bills.length() > 0) bills.getJSONObject(0) else null
                } else {
                    json
                }
            }

            // 4. 分类合法性检查与修正
            result?.let { root ->
                if (root.has("bills")) {
                    val billsArr = root.getJSONArray("bills")
                    for (i in 0 until billsArr.length()) {
                        val b = billsArr.getJSONObject(i)
                        val type = b.optInt("type", 0)
                        val candidates = if (type == 1) incomeCats else expenseCats
                        val rawCate = b.optString("category_name", "")
                        val normalized = rawCate.replace(" > ", "/::/").replace(" - ", "/::/").replace(" / ", "/::/").trim()
                        
                        val matched = findBestMatch(normalized, candidates)
                        if (matched != null) {
                            b.put("category_name", matched)
                        } else if (normalized.isNotEmpty()) {
                            b.put("category_name", resolveOtherCategory(candidates))
                        }
                    }
                } else if (root.has("amount")) {
                    val type = root.optInt("type", 0)
                    val candidates = if (type == 1) incomeCats else expenseCats
                    val rawCate = root.optString("category_name", "")
                    val normalized = rawCate.replace(" > ", "/::/").replace(" - ", "/::/").replace(" / ", "/::/").trim()

                    val matched = findBestMatch(normalized, candidates)
                    if (matched != null) {
                        root.put("category_name", matched)
                    } else if (normalized.isNotEmpty()) {
                        root.put("category_name", resolveOtherCategory(candidates))
                    }
                }
            }
            result
        } catch (e: Exception) {
            Logger.d(ctx, "AIService", "AI Request Failed: ${e.message}")
            null
        }
    }

    /**
     * 在候选库中寻找最佳匹配
     */
    private fun findBestMatch(input: String, candidates: List<String>): String? {
        if (input.isEmpty()) return null
        if (candidates.contains(input)) return input
        candidates.find { it.endsWith("/::/$input") }?.let { return it }
        candidates.find { it.startsWith("$input/::/") }?.let { return it }
        candidates.find { input.contains(it) || it.contains(input) }?.let { return it }
        return null
    }

    private fun resolveOtherCategory(candidates: List<String>): String {
        val otherMatch = candidates.find { it.contains("其他") }
        if (otherMatch != null) return otherMatch
        return if (candidates.isNotEmpty()) candidates.first() else "其他"
    }

    private fun cleanJsonString(input: String): String {
        var s = input.trim()
        if (s.startsWith("```json")) s = s.removePrefix("```json")
        if (s.startsWith("```")) s = s.removePrefix("```")
        if (s.endsWith("```")) s = s.removeSuffix("```")
        return s.trim()
    }

    suspend fun fetchModels(ctx: Context, apiKey: String): List<String> {
        return try {
            val response = getApi(ctx).getModels("Bearer $apiKey")
            val data = response.getAsJsonArray("data")
            data.map { it.asJsonObject.get("id").asString }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
