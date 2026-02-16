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
    // 核心：分析记账文本
    suspend fun analyzeAccounting(ctx: Context, userInput: String): JSONObject? {
        val apiKey = Prefs.getAiKey(ctx)
        val model = Prefs.getAiModel(ctx)
        if (apiKey.isEmpty()) return null

        // 1. 准备数据
        val assets = Prefs.getAssets(ctx).map { it.name }
        // 为了提高 token 利用率，我们将分类拍扁，但保留层级结构字符串
        val expenseCats = Prefs.getCategories(ctx, Prefs.TYPE_EXPENSE).flatMap { parent ->
            if (parent.subs.isEmpty()) listOf(parent.name)
            else parent.subs.map { "${parent.name}>${it.name}" }
        }
        val incomeCats = Prefs.getCategories(ctx, Prefs.TYPE_INCOME).flatMap { parent ->
            if (parent.subs.isEmpty()) listOf(parent.name)
            else parent.subs.map { "${parent.name}>${it.name}" }
        }
        val demoAsset = assets.firstOrNull() ?: "微信"
        val demoExpenseCat = expenseCats.firstOrNull() ?: "吃的"
        val demoIncomeCat = incomeCats.firstOrNull() ?: "工资"
        // 2. 时间锚点：格式化为 "2026-02-15 14:30:00 (星期日)"
        val now = Date()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val weekFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val currentTimeStr = "${timeFormat.format(now)} (${weekFormat.format(now)})"

        // 3. 构建 System Prompt (这是核心修改部分)
        // 3. 构建 System Prompt (修复分类幻觉的关键)
        // 2. 构建 System Prompt
        val systemPrompt = """
    你是一个【严格的数据匹配助手】。你的任务是将用户的自然语言映射到提供的【固定选项列表】中。
    
    【当前基准时间】: $currentTimeStr
    
    【选项列表 (必须严格遵守)】
    1. 可用资产库: ${Gson().toJson(assets)}
    2. 支出分类库: ${Gson().toJson(expenseCats)}
    3. 收入分类库: ${Gson().toJson(incomeCats)}
    
    【匹配逻辑】
    1. **Category (分类)**: 理解物品语义，在分类库中找到最匹配的那一项。**必须原样返回字符串**。
    2. **Asset (资产)**: 将口语别名（如"蓝色的软件"）映射为库中的标准名称（如"支付宝"）。
    3. **Time (时间)**: 基于基准时间推算，格式 yyyy-MM-dd HH:mm:ss。
    
    【JSON 输出格式】
    {"amount":0.0, "type":0, "asset_name":"", "to_asset_name":"", "category_name":"", "time":"", "remarks":""}
    
    【格式演示 (Format Demo)】
    警告：以下示例中的分类和资产仅供参考格式，实际请根据用户输入从上方列表中选择。
    
    输入: "刚才用$demoAsset 买东西花了100"
    输出: {"amount":100.0, "type":0, "asset_name":"$demoAsset", "category_name":"$demoExpenseCat", "time":"...", "remarks":"买东西"}
    
    输入: "发工资了入账$demoAsset 5000"
    输出: {"amount":5000.0, "type":1, "asset_name":"$demoAsset", "category_name":"$demoIncomeCat", "time":"...", "remarks":"工资"}
""".trimIndent()

        // 4. 发送请求
        return try {
            val messages = listOf(
                Message("system", systemPrompt),
                Message("user", userInput)
            )
            val response = getApi().chat(
                "Bearer $apiKey",
                ChatRequest(
                    model = model.ifEmpty { "Qwen/Qwen2.5-7B-Instruct" }, // 建议使用 Qwen2.5-72B 或 DeepSeek-V3 效果更好
                    messages = messages,
                    response_format = ResponseFormat("json_object") // 强制 JSON 模式
                )
            )
            val content = response.choices.first().message.content
            JSONObject(content)
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