package me.rerere.rikkahub.data.datastore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import kotlin.uuid.Uuid

/**
 * 推荐的提供商列表，在提供商设置页右上角的推荐 Sheet 中展示。
 */
val RECOMMENDED_PROVIDERS: List<ProviderSetting> = emptyList()
