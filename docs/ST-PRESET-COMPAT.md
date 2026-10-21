# SillyTavern 预设兼容（ST Preset Compatibility）

本 fork 增加了 SillyTavern **Chat Completion 预设**的导入与运行时组装支持。旧助手（没有绑定 ST 预设）完全沿用原来的 `systemPrompt + ModeInjection + Lorebook` 路径，不会回归。

## 使用方式

1. 打开 **提示词 → ST 预设**。
2. 点右下角「导入 ST 预设」，选择 SillyTavern 导出的 Chat Completion 预设 JSON。
3. 打开助手的 **拓展管理**，在「ST 预设」里启用刚导入的预设。只导入预设不会自动启用。
4. 想让 `personaDescription` marker / `{{persona}}` 有内容，在 **提示词 → ST 预设** 顶部填写「用户人设」。
5. 预设详情页可以查看 prompt_order、marker、In-Chat 深度和 Regex 列表，并点「查看组装结果」做一次离线 Inspect。
6. 详情页里每条已挂载的提示都可以单独启用/停用，用上下箭头调整顺序；改动后会显示「自定义排序」，点「恢复默认」可回到导入时的原始预设。未挂载、未知 marker 或空内容的条目会标注「不适用」，不可启用。
7. 点任意条目可以查看并修改名称、角色、正文；所有修改都写入导入副本，原始 JSON 保留在 `original_json`，可随时恢复默认。`main` / `worldInfoBefore` / `worldInfoAfter` / `chatHistory` 是结构性 marker，不允许停用；如果预设缺少这些 marker，组装器仍有兜底。

预设保存在 Room 的 `st_presets` 表里，原始 JSON 原样保留，可随时导出回 SillyTavern。会话开始后预设 ID 会随 `ConversationConfig` 固定下来，之后改助手不会影响已经开始的会话。排序/启用修改保存在 `order_json` 列，不会改写原始 JSON。

## 已支持

- `prompts[]` + `prompt_order[]` 解析，**以 `prompt_order[].enabled` 为唯一启用状态**。
- `prompt_order` 全局桶（`character_id: 100001`）选择。
- marker：`main`、`worldInfoBefore`、`worldInfoAfter`、`charDescription`、`charPersonality`、`scenario`、`personaDescription`、`dialogueExamples`、`chatHistory`。
- relative prompt 顺序；`injection_position=1` 的 In-Chat prompt 按 `injection_depth` 插入聊天历史（depth 0 = 历史末尾，1 = 最后一条历史消息之前）。
- `injection_order`：同一 depth 内数值小的更靠前、数值大的更靠近最新消息。
- `injection_trigger`：`normal / continue / regenerate / impersonate / swipe / quiet`。
- 宏：`{{char}}`、`{{user}}`、`{{persona}}`、`{{description}}`、`{{personality}}`、`{{scenario}}`、`{{mesExample}}`、`{{model}}`、`{{input}}`、`{{lastMessage}}`、`{{original}}`、`{{time}}`、`{{date}}`、`{{random}}`、`{{pick}}`、`{{roll}}`、`{{getvar}}`、`{{setvar}}`、`{{getglobalvar}}`、`{{setglobalvar}}`、`{{hasVar}}`、`{{// comment}}`；未识别宏会置空并在报告里记录。
- 会话变量持久化到 `st_variables` 表（按会话 ID 隔离），全局变量使用独立 scope。
- 预设 Regex：`promptOnly` / 默认脚本会作用于发送给模型的历史消息，支持 `placement`、`minDepth`、`maxDepth`、`trimStrings`、`substituteRegex`、`/pattern/flags`。
- 世界书 marker：支持角色卡内嵌 `character_book` 和助手绑定的 RikkaHub Lorebook；常驻、关键词、secondary keys（AND_ANY / NOT_ALL / NOT_ANY / AND_ALL）、`scanDepth`、`order`、字符预算。
- `squash_system_messages`。
- Claude / Gemini / OpenAI Responses：自动把开头 system 合并为一条，历史之后的 system 降级成 user，避免 provider 丢弃 prompt。
- `assistant_prefill`：在组装结果末尾追加一条 assistant 消息。
- 采样参数：助手自身的 `temperature / topP / maxTokens` 优先，未设置时回退到预设值。
- 角色卡导入：V2/V3 卡片现在会同时保存结构化 `CharacterCard`（description / personality / scenario / first_mes / alternate_greetings / mes_example / system_prompt / post_history_instructions / creator_notes / character_book），旧 `systemPrompt` 仍会生成，旧路径不变。

## 暂不支持 / 已知差异

- **Text Completion 预设**：会明确报错，只支持 Chat Completion 预设。
- **显示层 Regex（`markdownOnly` / `visualOnly`）**：已解析并在详情页列出，但不会改聊天渲染；ST 的美化脚本通常注入 `<style>`/HTML，直接套用到 Compose Markdown 有安全与渲染风险。
- 世界书：**递归、sticky / cooldown / delay、分组竞争、向量检索**未实现；`probability` 只做简单随机。
- `use_sysprompt`：不按 ST 的后端细节执行，改为按 provider 能力合并/降级 system 消息。
- `continue_prefill` / `continue_postfix`：未实现请求级 continue prefill。
- `names_behavior`：未向 provider 注入消息 `name`。
- 预设 Regex 的 `{{match}}` 等 ST 专有替换变量、`runOnEdit` 的编辑时机未实现。
- 预设内 `character_book` 只按静态角色书处理；世界书的 `originalData` 影子副本不会读取（以 `entries` 为准）。

## 安全

- 预设视为不可信输入：Regex 有 pattern/输入长度上限，编译失败或执行异常只记录警告，不会崩溃。
- 显示层 Regex 不执行，因此不会把 `@import` 远程字体、`<style>` 等注入聊天界面。
- 第三方预设 JSON 不应提交到公共仓库；测试夹具请脱敏。

## 代码位置

```text
app/src/main/java/me/rerere/rikkahub/data/stpreset/
  StPreset.kt              数据模型
  StPresetParser.kt        JSON 解析 / 校验 / 报告
  StPromptAssembler.kt     prompt_order + marker + in-chat 组装
  StMacroResolver.kt       ST 宏与变量
  StRegexPipeline.kt       预设 Regex
  StWorldInfoEngine.kt     character_book / Lorebook 世界书
app/src/main/java/me/rerere/rikkahub/data/db/entity/StPresetEntity.kt
app/src/main/java/me/rerere/rikkahub/data/db/dao/StPresetDao.kt
app/src/main/java/me/rerere/rikkahub/data/repository/StPresetRepository.kt
app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt   // preset / legacy 分支
app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PromptPage.kt // ST 预设 tab
```

## 测试

```bash
./gradlew :app:testGithubDebugUnitTest --tests "me.rerere.rikkahub.data.stpreset.*"
```

覆盖：解析与 `prompt_order[].enabled`、marker 顺序、In-Chat depth、trigger 过滤、squash、角色卡 marker、prefill、宏与变量、random/roll 固定种子、prompt Regex 的 minDepth/maxDepth/trimStrings/非法正则。
