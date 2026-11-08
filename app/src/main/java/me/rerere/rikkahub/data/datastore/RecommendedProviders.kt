package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting

/**
 * 推荐的提供商列表，在提供商设置页右上角的推荐 Sheet 中展示。
 *
 * 推荐 Sheet 和提供商列表展示 [ProviderSetting.shortDescription]，
 * 添加后的提供商详情页展示带链接的完整 [ProviderSetting.description]。
 */
val RECOMMENDED_PROVIDERS: List<ProviderSetting> = emptyList()
