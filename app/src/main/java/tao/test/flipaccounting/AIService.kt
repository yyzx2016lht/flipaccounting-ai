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
    private const val BASE_URL = "https://api.siliconflow.cn/"

    private fun getApi(): SiliconFlowApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SiliconFlowApi::class.java)
    }

    /**
     * 语音转文字：使用 TeleAI/TeleSpeechASR 模型
     */
    // 在 AIService object 中更新此方法

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
            val response = getApi().transcribe("Bearer $apiKey", modelPart, filePart)
            response.text
        } catch (e: Exception) {
            e.printStackTrace()
            // 可以在这里打 Log 查看具体报错
            null
        }
    }
    // 核心：分析记账文本
    suspend fun analyzeAccounting(ctx: Context, userInput: String): JSONObject? {
        val apiKey = Prefs.getAiKey(ctx)
        val model = Prefs.getAiModel(ctx)
        if (apiKey.isEmpty()) return null

        // 1. 准备上下文数据 (为了省钱和速度，只提取名称)
        val assets = Prefs.getAssets(ctx).map { it.name }
        val expenseCats = Prefs.getCategories(ctx, Prefs.TYPE_EXPENSE).map { flattenCategory(it) }
        val incomeCats = Prefs.getCategories(ctx, Prefs.TYPE_INCOME).map { flattenCategory(it) }
// 1. 【新增】获取当前精确时间，格式：2026-02-15 14:30:00 (星期日)
        val now = Date()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val weekFormat = SimpleDateFormat("EEEE", Locale.getDefault()) // 获取星期几
        val currentTimeStr = "${timeFormat.format(now)} (${weekFormat.format(now)})"
        // 2. 构建 Prompt
// ... 在 analyzeAccounting 方法中修改 systemPrompt ...
        val systemPrompt = """
    你是一个记账助手。请根据输入提取信息，返回严格 JSON。
    
    【资产库】: ${Gson().toJson(assets)}
    【分类库】: 支出:${Gson().toJson(expenseCats)}, 收入:${Gson().toJson(incomeCats)}
    【当前时间】: $currentTimeStr
    【核心规范】:
    1. type: 0:支出, 1:收入, 2:转账, 3:信用卡还款, 5:报销
    2. asset_name/to_asset_name: 必须从【资产库】精确匹配。若用户未提及具体支付工具（如没说微信、支付宝等），必须返回 ""。
    3. category_name: 必须是【分类库】中**完整存在的路径字符串**。禁止跨父类拼接！匹配不到则返回 "其他"。
    4. time: 格式 "yyyy-MM-dd HH:mm:ss"。未提及返回 ""。支持"昨天/前天"等计算，支持"早/午/晚饭"推断(08:00/12:00/19:00)。
    
    【JSON字段】:
    - amount(数字), type(整数), asset_name(字符串), to_asset_name(转入账户), fee(手续费), category_name(分类全路径), time(时间字符串), remarks(备注)

    示例输入1: "昨天中午吃拉面花了20" (假设今日2026-02-15)
    返回: {"amount": 20, "type": 0, "time": "2026-02-14 12:00:00", "asset_name": "", "category_name": "餐饮 > 午餐", "remarks": "吃拉面"}
    
    示例输入2: "支付宝转账500到招行"
    返回: {"amount": 500, "type": 2, "asset_name": "支付宝", "to_asset_name": "招商银行", "category_name": ""}
""".trimIndent()
        // 3. 发送请求
        return try {
            val messages = listOf(
                Message("system", systemPrompt),
                Message("user", userInput)
            )
            val response = getApi().chat(
                "Bearer $apiKey",
                ChatRequest(model.ifEmpty { "Qwen/Qwen2.5-7B-Instruct" }, messages)
            )
            val content = response.choices.first().message.content
            JSONObject(response.choices.first().message.content)
        } catch (e: Exception) {
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
    suspend fun fetchModels(apiKey: String): List<String> {
        return try {
            val response = getApi().getModels("Bearer $apiKey")
            // 从 JsonObject 中解析 data 数组
            val data = response.getAsJsonArray("data")
            data.map { it.asJsonObject.get("id").asString }
                .filter {
                    // 过滤出常用的对话模型
                    it.contains("Qwen") || it.contains("deepseek") || it.contains("glm")
                }
                .sorted()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}