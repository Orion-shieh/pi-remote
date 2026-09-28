package com.piremote.app.ui.agent

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.piremote.app.ui.GeekColors

/**
 * In-app interactive web development preview sheet.
 * Renders the AI-generated HTML/CSS/JS project inside a sandboxed WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewSheet(
    url: String,
    filePath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("正在载入网页...") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = GeekColors.CardSurface,
            border = null,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GeekColors.CardSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GeekColors.NeonGreen,
                            modifier = Modifier.size(8.dp),
                        ) {}
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = pageTitle,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = GeekColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = GeekColors.TextSecondary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1500,
                                ),
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法唤起系统浏览器", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "在外部浏览器中打开",
                                tint = GeekColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = GeekColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = GeekColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Loading progress indicator
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(GeekColors.DeepCanvas)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0.1f, 1f))
                                .height(2.dp)
                                .background(GeekColors.TerminalCyan)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GeekColors.BorderSubtle))
                }

                // Interactive WebView
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        pageTitle = view?.title ?: "Web Preview"
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            val htmlFile = File(filePath)
                                            if (htmlFile.exists()) {
                                                try {
                                                    val htmlContent = htmlFile.readText(Charsets.UTF_8)
                                                    view?.loadDataWithBaseURL(
                                                        "file://${htmlFile.parentFile?.absolutePath}/",
                                                        htmlContent,
                                                        "text/html",
                                                        "utf-8",
                                                        null,
                                                    )
                                                    isLoading = false
                                                    return
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        super.onReceivedError(view, request, error)
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress / 100f
                                        if (newProgress >= 100) isLoading = false
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) pageTitle = title
                                    }
                                }

                                loadUrl(url)
                            }
                        },
                        update = { view ->
                            if (view.url != url) {
                                view.loadUrl(url)
                            }
                        }
                    )
                }

                // Bottom footer with file path
                Surface(
                    color = GeekColors.CardSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "项目文件: $filePath",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = GeekColors.TextMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                            ),
                    )
                }
            }
        }
    }
}
