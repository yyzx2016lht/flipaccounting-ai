package tao.test.flipaccounting

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShell {

    fun exec(cmd: String): String {
        if (!Shizuku.pingBinder()) return ""

        return try {
            val commandArray = arrayOf("sh", "-c", cmd)

            // --- 核心修改：通过 Java 助手类来创建进程 ---
            val process = ShizukuHelper.createProcess(commandArray, null, null)

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.use { it.readText() }
            process.waitFor()
            result
        } catch (e: Exception) {
            android.util.Log.e("ShizukuShell", "Shell执行异常: ${e.message}")
            ""
        }
    }

    /**
     * 获取当前最顶层（Resumed）的应用包名
     */
    fun getForegroundApp(): String? {
        val output = exec("dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity'")
        val regex = Regex("""([a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+)/""", RegexOption.IGNORE_CASE)
        return output.lineSequence()
            .mapNotNull { line -> regex.find(line)?.groupValues?.get(1) }
            .firstOrNull { it.contains('.') && it != "android" }
    }
}