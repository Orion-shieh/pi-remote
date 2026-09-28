package com.piremote.app.ui.gui

object GuiSampleData {

    fun createInitialConversation(): List<GuiMessageItem> {
        val now = System.currentTimeMillis()
        return listOf(
            SystemStatusMessage(
                timestamp = now - 90_000,
                text = "会话已连接 · Pi Agent v0.85.1 (deepseek-v4-flash)",
            ),
            UserMessage(
                timestamp = now - 85_000,
                text = "请帮我检查当前 Git 仓库状态，并优化设置页的主题切换按钮样式，需要更圆润的高级质感。",
            ),
            ThinkingBlock(
                timestamp = now - 78_000,
                durationSeconds = 3.8f,
                isFinished = true,
                content = """
1. 首先需要通过 bash 运行 git status 查看当前工作区状态是否有未提交改动。
2. 随后定位到 app/src/main/kotlin/com/piremote/app/ui/Screens.kt 中的 ThemeOptionButton 组件。
3. 检查当前的 RoundedCornerShape 与 Surface 边框设计，调整为 12.dp 圆角，并加入高亮边框和触控反馈。
4. 完成代码编辑后验证改动。
                """.trimIndent(),
            ),
            ToolCallBlock(
                timestamp = now - 70_000,
                toolName = "bash",
                summary = "git status --short",
                arguments = "git status --short",
                status = ToolStatus.SUCCESS,
                output = """
 M android/app/src/main/kotlin/com/piremote/app/ui/Screens.kt
 M android/app/src/main/kotlin/com/piremote/app/ui/Theme.kt
?? android/app/src/main/kotlin/com/piremote/app/ui/gui/
                """.trimIndent(),
            ),
            ToolCallBlock(
                timestamp = now - 60_000,
                toolName = "read_file",
                summary = "Screens.kt:140-165",
                arguments = "Target: app/src/main/kotlin/com/piremote/app/ui/Screens.kt (lines 140-165)",
                status = ToolStatus.SUCCESS,
                output = """
150:                     ThemeOptionButton(
151:                         label = "深色 (黑)",
152:                         icon = "🌙",
153:                         selected = themeMode == SettingsStore.THEME_DARK,
154:                         modifier = Modifier.weight(1f),
155:                         onClick = { store.themeMode = SettingsStore.THEME_DARK },
156:                     )
                """.trimIndent(),
            ),
            ToolCallBlock(
                timestamp = now - 50_000,
                toolName = "edit_file",
                summary = "更新 ThemeOptionButton 圆角与质感边框",
                arguments = "Target: Screens.kt\n+ shape = RoundedCornerShape(12.dp)\n+ border = BorderStroke(1.dp, if (selected) GeekColors.BrandAccent else GeekColors.BorderSubtle)",
                status = ToolStatus.SUCCESS,
                output = "Successfully applied replacement chunk at line 145-160.",
            ),
            AssistantResponse(
                timestamp = now - 40_000,
                text = """
已为你完成设置页主题切换组件的质感升级：

1. **圆角与轮廓**：将原有按钮升级为 `12.dp` 柔和圆角，匹配系统全局的 Geek 卡片设计规范；
2. **选中高亮态**：未选中时采用次级微边框 `BorderSubtle`，选中时呈现极客紫晶发光边框 `BrandAccent`；
3. **触控与水波纹**：内嵌 `Modifier.clip(shape)` 保证按压涟漪完全贴合圆角边界，无直角溢出。

```kotlin
@Composable
fun ThemeOptionButton(
    label: String,
    icon: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = if (selected) GeekColors.CardElevated else GeekColors.CardSurface,
        border = BorderStroke(1.dp, if (selected) GeekColors.BrandAccent else GeekColors.BorderSubtle),
        modifier = modifier.clip(shape),
        onClick = onClick,
    ) { ... }
}
```
请在真机或模拟器中查看效果！如需进一步微调阴影或渐变背景随时告诉我。
                """.trimIndent(),
            ),
            QuestionOptionBlock(
                timestamp = now - 30_000,
                question = "是否立即提交当前主题优化代码并运行测试？",
                options = listOf(
                    InteractiveOption(key = "1", label = "1) 提交代码并运行单元测试 (推荐)"),
                    InteractiveOption(key = "2", label = "2) 仅保存修改，稍后手动测试"),
                    InteractiveOption(key = "3", label = "3) 放弃修改并还原工作区"),
                ),
            ),
            QuestionOptionBlock(
                timestamp = now - 10_000,
                question = "请勾选当前工程需要启用的特性组件与代码检查规范：",
                method = "multi_select",
                isMultiSelect = true,
                options = listOf(
                    InteractiveOption(key = "compose_lint", label = "Compose 规范 Lint 规则集", description = "严格检查重组闭包、remember 与 Modifier 链"),
                    InteractiveOption(key = "haptic_feedback", label = "触摸触觉微反馈 (Haptic Spec)", description = "点击或长按时提供微弱震动响应"),
                    InteractiveOption(key = "motion_spring", label = "Spring 弹性物理微动效", description = "用于按钮反弹、复选框伸缩与抽屉动画"),
                    InteractiveOption(key = "auto_format", label = "Ktlint 自动代码格式化钩子", description = "在保存与提交时自动统一格式"),
                ),
                allowCustomInput = true,
                placeholder = "补充其它配置需求...",
            ),
        )
    }
}
