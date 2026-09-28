package com.piremote.app.data.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Native execution tools available to the Local AI Agent.
 * Runs directly on Android without Termux or external packages.
 */
class LocalAgentTools(private val context: Context, private var workspaceDir: File) {

    companion object {
        private const val TAG = "LocalAgentTools"

        fun getDefaultWorkspace(context: Context): File {
            return try {
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val target = File(docsDir, "PiAgent")
                if (!target.exists()) target.mkdirs()
                if (target.canWrite()) target else File(context.filesDir, "workspace").apply { mkdirs() }
            } catch (_: Exception) {
                File(context.filesDir, "workspace").apply { mkdirs() }
            }
        }
    }

    init {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
    }

    fun setWorkspace(newDir: File) {
        workspaceDir = newDir
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
    }

    fun getWorkspace(): File = workspaceDir

    /**
     * Resolves a file path relative to the active workspace.
     */
    fun resolveFile(path: String): File {
        val trimmed = path.trim()
        val file = if (trimmed.startsWith("/") || trimmed.startsWith("sdcard")) {
            File(trimmed)
        } else {
            File(workspaceDir, trimmed)
        }
        return file.canonicalFile
    }

    /**
     * Returns OpenAI-compliant Function Calling tools definition JSON array.
     */
    fun getToolsSpecification(): JSONArray {
        val tools = JSONArray()

        // 1. create_or_write_file
        tools.put(createToolJson(
            name = "create_or_write_file",
            description = "在手机工作区或指定路径创建或写入文件内容（UTF-8编码）。支持代码、HTML、文本等各种文件。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("path", JSONObject().put("type", "string").put("description", "相对工作区的路径或绝对路径，例如 index.html 或 src/app.js"))
                    .put("content", JSONObject().put("type", "string").put("description", "文件的完整文本内容"))
                )
                .put("required", JSONArray().put("path").put("content"))
        ))

        // 2. read_file
        tools.put(createToolJson(
            name = "read_file",
            description = "读取手机中指定文件的完整文本内容。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("path", JSONObject().put("type", "string").put("description", "要读取的文件路径"))
                )
                .put("required", JSONArray().put("path"))
        ))

        // 3. list_directory
        tools.put(createToolJson(
            name = "list_directory",
            description = "列出手机工作区或指定目录下的子文件与目录列表。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("path", JSONObject().put("type", "string").put("description", "目录路径，为空时代表当前工作区根目录"))
                )
        ))

        // 4. edit_file_snippet
        tools.put(createToolJson(
            name = "edit_file_snippet",
            description = "精准替换手机中现有文件的局部内容。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("path", JSONObject().put("type", "string").put("description", "目标文件路径"))
                    .put("old_snippet", JSONObject().put("type", "string").put("description", "需要被替换的原始内容片段"))
                    .put("new_snippet", JSONObject().put("type", "string").put("description", "替换后的新内容"))
                )
                .put("required", JSONArray().put("path").put("old_snippet").put("new_snippet"))
        ))

        // 5. create_webpage_project
        tools.put(createToolJson(
            name = "create_webpage_project",
            description = "一键在手机上创建现代 Web 网页工程（含 index.html, style.css, script.js），并自动启动手机本地 HTTP 服务器供 App 实时网页弹窗预览交互。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("project_name", JSONObject().put("type", "string").put("description", "项目文件夹名称，如 calculator, dashboard, game"))
                    .put("html", JSONObject().put("type", "string").put("description", "index.html 页面完整源码"))
                    .put("css", JSONObject().put("type", "string").put("description", "style.css 样式源码（可为空）"))
                    .put("js", JSONObject().put("type", "string").put("description", "script.js 逻辑源码（可为空）"))
                )
                .put("required", JSONArray().put("project_name").put("html"))
        ))

        // 6. generate_word_document
        tools.put(createToolJson(
            name = "generate_word_document",
            description = "直接在手机本地生成标准排版的 Word 文档 (.docx)。生成后支持一键调用手机自带 WPS Office 或微软 Office 打开。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("file_name", JSONObject().put("type", "string").put("description", "文档文件名（如 市场调研报告.docx）"))
                    .put("title", JSONObject().put("type", "string").put("description", "文档主标题"))
                    .put("subtitle", JSONObject().put("type", "string").put("description", "副标题或概述"))
                    .put("author", JSONObject().put("type", "string").put("description", "作者或编制机构"))
                    .put("sections", JSONObject().put("type", "array").put("description", "文档章节列表。每个对象包含 heading(章节标题), level(1/2/3), paragraphs(正文段落列表), bullets(要点列表), table_headers(表格表头), table_rows(表格行列表)"))
                )
                .put("required", JSONArray().put("file_name").put("title").put("sections"))
        ))

        // 7. generate_presentation
        tools.put(createToolJson(
            name = "generate_presentation",
            description = "直接在手机本地生成标准 16:9 宽屏 PowerPoint 幻灯片文档 (.pptx)。支持一键调用手机自带 WPS Office 查看与放映。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("file_name", JSONObject().put("type", "string").put("description", "演示文稿文件名（如 季度汇报.pptx）"))
                    .put("title", JSONObject().put("type", "string").put("description", "PPT 主标题（封面大标题）"))
                    .put("subtitle", JSONObject().put("type", "string").put("description", "PPT 副标题"))
                    .put("slides", JSONObject().put("type", "array").put("description", "幻灯片页面列表。每个对象包含 title(页标题), bullets(要点内容列表), notes(演讲者备注)"))
                )
                .put("required", JSONArray().put("file_name").put("title").put("slides"))
        ))

        // 8. run_shell_command
        tools.put(createToolJson(
            name = "run_shell_command",
            description = "在手机本地执行 Linux/Android 命令行命令（如 ls, grep, ps, cat, curl, ping, date 等）。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("command", JSONObject().put("type", "string").put("description", "要执行的 Shell 指令"))
                )
                .put("required", JSONArray().put("command"))
        ))

        // 9. delete_file_or_directory
        tools.put(createToolJson(
            name = "delete_file_or_directory",
            description = "删除手机工作区或指定路径下的文件或文件夹。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("path", JSONObject().put("type", "string").put("description", "要删除的文件或目录路径"))
                    .put("recursive", JSONObject().put("type", "boolean").put("description", "如果目标是文件夹，是否递归删除内部所有文件（默认 true）"))
                )
                .put("required", JSONArray().put("path"))
        ))

        // 10. move_or_rename_file
        tools.put(createToolJson(
            name = "move_or_rename_file",
            description = "在手机本地移动或重命名文件/文件夹。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("source_path", JSONObject().put("type", "string").put("description", "源文件或文件夹路径"))
                    .put("destination_path", JSONObject().put("type", "string").put("description", "目标文件或文件夹路径"))
                )
                .put("required", JSONArray().put("source_path").put("destination_path"))
        ))

        // 11. ask_user_question (HDVA 核心：遇疑主动反问确认)
        tools.put(createToolJson(
            name = "ask_user_question",
            description = "当面临设计选择、技术选型（如选哪个框架/库）、需求歧义或用户未明确指定偏好时，调用此工具向用户提出单选/多选确认问题。界面将弹出可交互的选项卡片供用户直接点选，待用户反馈后再继续后续任务。",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("question", JSONObject().put("type", "string").put("description", "向用户提出的核心澄清问题"))
                    .put("options", JSONObject()
                        .put("type", "array")
                        .put("items", JSONObject().put("type", "string"))
                        .put("description", "供用户点选的选项文本列表（如 ['深色拟物极客风 (推荐)', '浅色清新简约风', '高对比黑白风']）")
                    )
                    .put("is_multi_select", JSONObject().put("type", "boolean").put("description", "是否允许用户多选，默认为 false 单选"))
                    .put("allow_custom_input", JSONObject().put("type", "boolean").put("description", "是否允许用户自行输入补充文字，默认为 true"))
                )
                .put("required", JSONArray().put("question").put("options"))
        ))

        return tools
    }

    // HDVA 影子沙箱写时复制 (COW) 备份映射: filePath -> originalContent (若原先不存在则为 null)
    private val shadowBackup = mutableMapOf<String, String?>()

    fun backupForShadow(file: File) {
        val key = file.absolutePath
        if (!shadowBackup.containsKey(key)) {
            shadowBackup[key] = if (file.exists()) file.readText(Charsets.UTF_8) else null
        }
    }

    fun rollbackShadow(): List<String> {
        val rolledBack = mutableListOf<String>()
        shadowBackup.forEach { (path, original) ->
            try {
                val f = File(path)
                if (original == null) {
                    if (f.exists()) f.delete()
                } else {
                    f.writeText(original, Charsets.UTF_8)
                }
                rolledBack.add(path)
            } catch (_: Exception) {}
        }
        shadowBackup.clear()
        return rolledBack
    }

    fun commitShadow() {
        shadowBackup.clear()
    }

    // HDVA Tier-0 本地静态语法门禁检测
    fun validateSyntaxGate(fileName: String, content: String): String? {
        val lower = fileName.lowercase()
        if (lower.endsWith(".json")) {
            try {
                val trimmed = content.trim()
                if (trimmed.startsWith("{")) JSONObject(trimmed)
                else if (trimmed.startsWith("[")) JSONArray(trimmed)
                else return "JSON 格式非法：根节点必须以 '{' 或 '[' 开头"
            } catch (e: Exception) {
                return "JSON 语法解析错误: ${e.message}"
            }
        } else if (lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".css") || lower.endsWith(".java") || lower.endsWith(".kt")) {
            var braces = 0
            var brackets = 0
            var parens = 0
            var inSingle = false
            var inDouble = false
            var inBacktick = false
            var escaped = false
            for (ch in content) {
                if (escaped) { escaped = false; continue }
                if (ch == '\\') { escaped = true; continue }
                if (ch == '\'' && !inDouble && !inBacktick) inSingle = !inSingle
                else if (ch == '"' && !inSingle && !inBacktick) inDouble = !inDouble
                else if (ch == '`' && !inSingle && !inDouble) inBacktick = !inBacktick

                if (!inSingle && !inDouble && !inBacktick) {
                    when (ch) {
                        '{' -> braces++
                        '}' -> braces--
                        '[' -> brackets++
                        ']' -> brackets--
                        '(' -> parens++
                        ')' -> parens--
                    }
                }
            }
            if (braces != 0) return "语法门禁警示: 花括号 '{}' 未配对闭合 (差额: $braces)"
            if (brackets != 0) return "语法门禁警示: 方括号 '[]' 未配对闭合 (差额: $brackets)"
            if (parens != 0) return "语法门禁警示: 圆括号 '()' 未配对闭合 (差额: $parens)"
        }
        return null
    }

    // 挂起式人机交互回调
    var onQuestionRequested: (suspend (requestId: String, question: String, options: List<String>, isMultiSelect: Boolean, allowCustomInput: Boolean) -> String)? = null

    private fun createToolJson(name: String, description: String, parameters: JSONObject): JSONObject {
        return JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", name)
                .put("description", description)
                .put("parameters", parameters)
            )
    }

    /**
     * Executes a tool by name with the given JSON arguments.
     * Returns a human/AI-readable result string.
     */
    suspend fun executeTool(name: String, argumentsJson: String): ToolExecutionResult {
        return try {
            val args = if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
            when (name) {
                "ask_user_question" -> {
                    val question = args.getString("question")
                    val optionsArr = args.optJSONArray("options") ?: JSONArray()
                    val optionsList = mutableListOf<String>()
                    for (i in 0 until optionsArr.length()) {
                        optionsList.add(optionsArr.getString(i))
                    }
                    val isMulti = args.optBoolean("is_multi_select", false)
                    val allowCustom = args.optBoolean("allow_custom_input", true)
                    val reqId = java.util.UUID.randomUUID().toString()

                    val handler = onQuestionRequested
                    if (handler != null) {
                        val answer = handler(reqId, question, optionsList, isMulti, allowCustom)
                        ToolExecutionResult(
                            output = "用户已确认并选择: $answer",
                            isError = false,
                            extraAction = ExtraAction.QuestionAnswered(question, answer)
                        )
                    } else {
                        ToolExecutionResult("交互通道未激活，默认采纳推荐方案: ${optionsList.firstOrNull() ?: "确认执行"}", isError = false)
                    }
                }

                "create_or_write_file" -> {
                    val path = args.getString("path")
                    val content = args.getString("content")
                    val file = resolveFile(path)
                    backupForShadow(file)
                    file.parentFile?.mkdirs()
                    file.writeText(content, Charsets.UTF_8)
                    val syntaxWarn = validateSyntaxGate(file.name, content)
                    val warnSuffix = if (syntaxWarn != null) "\n【形式化门禁警示】: $syntaxWarn" else ""
                    ToolExecutionResult(
                        output = "成功写入文件: ${file.absolutePath} (共 ${content.length} 字符, 大小: ${file.length()} 字节)$warnSuffix",
                        isError = false,
                        extraAction = ExtraAction.FileCreated(file.absolutePath)
                    )
                }

                "read_file" -> {
                    val path = args.getString("path")
                    val file = resolveFile(path)
                    if (!file.exists()) {
                        ToolExecutionResult("错误: 文件不存在: ${file.absolutePath}", isError = true)
                    } else if (file.isDirectory) {
                        ToolExecutionResult("错误: 目标是目录而非文件: ${file.absolutePath}", isError = true)
                    } else {
                        val text = file.readText(Charsets.UTF_8)
                        val preview = if (text.length > 5000) text.take(5000) + "\n... (已截断显示)" else text
                        ToolExecutionResult(preview, isError = false)
                    }
                }

                "list_directory" -> {
                    val path = args.optString("path", "")
                    val dir = if (path.isBlank()) workspaceDir else resolveFile(path)
                    if (!dir.exists()) {
                        ToolExecutionResult("错误: 目录不存在: ${dir.absolutePath}", isError = true)
                    } else if (!dir.isDirectory) {
                        ToolExecutionResult("错误: 目标不是目录: ${dir.absolutePath}", isError = true)
                    } else {
                        val items = dir.listFiles() ?: emptyArray()
                        val sb = StringBuilder("目录: ${dir.absolutePath}\n")
                        sb.append("总共包含 ${items.size} 个项目:\n")
                        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { item ->
                            val prefix = if (item.isDirectory) "[目录]" else "[文件] (${item.length()} B)"
                            sb.append("  $prefix ${item.name}\n")
                        }
                        ToolExecutionResult(sb.toString(), isError = false)
                    }
                }

                "edit_file_snippet" -> {
                    val path = args.getString("path")
                    val oldSnippet = args.getString("old_snippet")
                    val newSnippet = args.getString("new_snippet")
                    val file = resolveFile(path)
                    if (!file.exists()) {
                        ToolExecutionResult("错误: 文件不存在: ${file.absolutePath}", isError = true)
                    } else {
                        val original = file.readText(Charsets.UTF_8)
                        if (!original.contains(oldSnippet)) {
                            ToolExecutionResult("错误: 文件中未找到指定的 old_snippet，无法替换。", isError = true)
                        } else {
                            backupForShadow(file)
                            val updated = original.replace(oldSnippet, newSnippet)
                            file.writeText(updated, Charsets.UTF_8)
                            val syntaxWarn = validateSyntaxGate(file.name, updated)
                            val warnSuffix = if (syntaxWarn != null) "\n【形式化门禁警示】: $syntaxWarn" else ""
                            ToolExecutionResult("成功局部替换文件: ${file.absolutePath}$warnSuffix", isError = false)
                        }
                    }
                }

                "delete_file_or_directory" -> {
                    val path = args.getString("path")
                    val recursive = args.optBoolean("recursive", true)
                    val file = resolveFile(path)
                    if (!file.exists()) {
                        ToolExecutionResult("错误: 文件或目录不存在: ${file.absolutePath}", isError = true)
                    } else {
                        backupForShadow(file)
                        val success = if (file.isDirectory) {
                            if (recursive) file.deleteRecursively() else file.delete()
                        } else {
                            file.delete()
                        }
                        if (success) {
                            ToolExecutionResult("成功删除: ${file.absolutePath}", isError = false)
                        } else {
                            ToolExecutionResult("删除失败: ${file.absolutePath}（可能文件正在被占用或权限受限）", isError = true)
                        }
                    }
                }

                "move_or_rename_file" -> {
                    val src = resolveFile(args.getString("source_path"))
                    val dest = resolveFile(args.getString("destination_path"))
                    if (!src.exists()) {
                        ToolExecutionResult("错误: 源文件不存在: ${src.absolutePath}", isError = true)
                    } else {
                        backupForShadow(src)
                        dest.parentFile?.mkdirs()
                        val success = src.renameTo(dest)
                        if (success) {
                            ToolExecutionResult("成功移动/重命名: ${src.name} -> ${dest.absolutePath}", isError = false)
                        } else {
                            try {
                                src.copyTo(dest, overwrite = true)
                                src.deleteRecursively()
                                ToolExecutionResult("成功移动/重命名: ${dest.absolutePath}", isError = false)
                            } catch (e: Exception) {
                                ToolExecutionResult("移动/重命名失败: ${e.message}", isError = true)
                            }
                        }
                    }
                }

                "create_webpage_project" -> {
                    val projName = args.getString("project_name").trim().replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                    val html = args.getString("html")
                    val css = args.optString("css", "")
                    val js = args.optString("js", "")

                    val projDir = File(workspaceDir, projName).apply { mkdirs() }
                    val htmlFile = File(projDir, "index.html").apply {
                        backupForShadow(this)
                        writeText(html, Charsets.UTF_8)
                    }
                    if (css.isNotBlank()) {
                        File(projDir, "style.css").apply {
                            backupForShadow(this)
                            writeText(css, Charsets.UTF_8)
                        }
                    }
                    if (js.isNotBlank()) {
                        File(projDir, "script.js").apply {
                            backupForShadow(this)
                            writeText(js, Charsets.UTF_8)
                        }
                    }

                    val serverUrl = LocalWebPreviewServer.getInstance().start(projDir)
                    ToolExecutionResult(
                        output = "本地网页工程构建完成。\n" +
                                "工程目录: ${projDir.absolutePath}\n" +
                                "本地服务: $serverUrl",
                        isError = false,
                        extraAction = ExtraAction.WebPreview(serverUrl, htmlFile.absolutePath)
                    )
                }

                "generate_word_document" -> {
                    val fileName = args.getString("file_name").let { if (it.endsWith(".docx")) it else "$it.docx" }
                    val title = args.getString("title")
                    val subtitle = args.optString("subtitle", null)
                    val author = args.optString("author", "Pi Remote AI")
                    val sectionsArr = args.optJSONArray("sections") ?: JSONArray()

                    val sections = mutableListOf<OfficeDocumentBuilder.WordSection>()
                    for (i in 0 until sectionsArr.length()) {
                        val secObj = sectionsArr.getJSONObject(i)
                        val heading = secObj.optString("heading", null)
                        val level = secObj.optInt("level", 1)

                        val paragraphs = mutableListOf<String>()
                        val pArr = secObj.optJSONArray("paragraphs")
                        if (pArr != null) {
                            for (j in 0 until pArr.length()) paragraphs.add(pArr.getString(j))
                        }

                        val bullets = mutableListOf<String>()
                        val bArr = secObj.optJSONArray("bullets")
                        if (bArr != null) {
                            for (j in 0 until bArr.length()) bullets.add(bArr.getString(j))
                        }

                        val headers = mutableListOf<String>()
                        val hArr = secObj.optJSONArray("table_headers")
                        if (hArr != null) {
                            for (j in 0 until hArr.length()) headers.add(hArr.getString(j))
                        }

                        val rows = mutableListOf<List<String>>()
                        val rArr = secObj.optJSONArray("table_rows")
                        if (rArr != null) {
                            for (j in 0 until rArr.length()) {
                                val rowArr = rArr.getJSONArray(j)
                                val rowList = mutableListOf<String>()
                                for (k in 0 until rowArr.length()) rowList.add(rowArr.getString(k))
                                rows.add(rowList)
                            }
                        }

                        sections.add(OfficeDocumentBuilder.WordSection(
                            heading = heading,
                            level = level,
                            paragraphs = paragraphs,
                            bullets = bullets,
                            tableHeaders = headers,
                            tableRows = rows,
                        ))
                    }

                    val outputFile = resolveFile(fileName)
                    backupForShadow(outputFile)
                    val ok = OfficeDocumentBuilder.createDocx(outputFile, title, subtitle, author, sections)
                    if (ok) {
                        ToolExecutionResult(
                            output = "已生成 Word 文档: ${outputFile.absolutePath} (${outputFile.length()} 字节)",
                            isError = false,
                            extraAction = ExtraAction.OfficeDocument(outputFile.absolutePath, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        )
                    } else {
                        ToolExecutionResult("错误: 生成 Word 文档失败", isError = true)
                    }
                }

                "generate_presentation" -> {
                    val fileName = args.getString("file_name").let { if (it.endsWith(".pptx")) it else "$it.pptx" }
                    val title = args.getString("title")
                    val subtitle = args.optString("subtitle", null)
                    val slidesArr = args.optJSONArray("slides") ?: JSONArray()

                    val slides = mutableListOf<OfficeDocumentBuilder.PptSlide>()
                    for (i in 0 until slidesArr.length()) {
                        val sObj = slidesArr.getJSONObject(i)
                        val slideTitle = sObj.getString("title")
                        val notes = sObj.optString("notes", null)

                        val bullets = mutableListOf<String>()
                        val bArr = sObj.optJSONArray("bullets")
                        if (bArr != null) {
                            for (j in 0 until bArr.length()) bullets.add(bArr.getString(j))
                        }

                        slides.add(OfficeDocumentBuilder.PptSlide(
                            title = slideTitle,
                            bullets = bullets,
                            notes = notes,
                        ))
                    }

                    val outputFile = resolveFile(fileName)
                    backupForShadow(outputFile)
                    val ok = OfficeDocumentBuilder.createPptx(outputFile, title, subtitle, slides)
                    if (ok) {
                        ToolExecutionResult(
                            output = "已生成 PPT 演示文稿: ${outputFile.absolutePath} (${slides.size + 1} 页幻灯片)",
                            isError = false,
                            extraAction = ExtraAction.OfficeDocument(outputFile.absolutePath, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
                        )
                    } else {
                        ToolExecutionResult("错误: 生成 PPT 演示文稿失败", isError = true)
                    }
                }

                "run_shell_command" -> {
                    val cmd = args.getString("command")
                    val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                    val output = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    val errOutput = StringBuilder()
                    while (errorReader.readLine().also { line = it } != null) {
                        errOutput.append(line).append("\n")
                    }
                    process.waitFor()

                    val result = if (errOutput.isNotEmpty()) {
                        "${output.toString().trim()}\n[STDERR]\n${errOutput.toString().trim()}"
                    } else {
                        output.toString().trim().ifEmpty { "(命令已执行，无输出)" }
                    }
                    ToolExecutionResult(result, isError = process.exitValue() != 0)
                }

                else -> ToolExecutionResult("未知工具: $name", isError = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $name", e)
            ToolExecutionResult("工具执行异常 (${e.javaClass.simpleName}): ${e.message}", isError = true)
        }
    }

    /**
     * Opens an office document or file using the Android system default app (WPS Office, Word, etc.).
     */
    fun openFileInSystem(filePath: String, mimeType: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        return try {
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file in system", e)
            false
        }
    }
}

data class ToolExecutionResult(
    val output: String,
    val isError: Boolean,
    val extraAction: ExtraAction? = null,
)

sealed class ExtraAction {
    data class WebPreview(val serverUrl: String, val filePath: String) : ExtraAction()
    data class OfficeDocument(val filePath: String, val mimeType: String) : ExtraAction()
    data class FileCreated(val filePath: String) : ExtraAction()
    data class QuestionAnswered(val question: String, val answer: String) : ExtraAction()
}
