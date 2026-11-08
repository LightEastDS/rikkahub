# RikkaHub (LightEastDS Fork) 修改与定制文档

本文档详细记录了 **LightEastDS** 维护的 [RikkaHub](https://github.com/LightEastDS/rikkahub) 分支相较于官方上游（[re-ovo/rikkahub](https://github.com/re-ovo/rikkahub)）的所有改动与增强，涵盖**新功能扩展**、**去商业化/净化**、**构建与签名优化**以及**改动文件清单**。

---

## 一、 新增功能 (New Features)

### 1. 消息字数统计 (Message Word Count)
* **功能描述**：在聊天消息下方的状态栏（Nerd Line）中直观显示该条消息的文本字数（例如：`128 字`）。
* **计数规则**：采用智能多语言统一计数引擎，完全不计入空白字符（空格、换行、制表符、全角空格等）：
  * **中文**：只计算汉字与标点符号；
  * **英文**：计算单词与标点符号（包含缩写内部撇号如 `don't`，按词统计）；
  * **日文**：计算汉字、假名（平假名、片假名、长音符）与标点符号；
  * **多语言混合**：支持中英日多语言混合文本自动无缝准确统计。
* **配置开关**：在 **设置 -> 界面显示设置** 中新增了独立控制开关「显示字数统计」，支持随时开启或关闭。
* **国际化支持**：同步适配了中文与英文资源字符串。

### 2. 对话楼层编号显示 (Message Floor Number)
* **功能描述**：在每条消息的状态栏左侧显示消息所在的楼层编号（如 `1轮`、`2轮`、`3轮` 等）。
  * 用户发送的第一条问题为 `1轮`。
  * LLM 紧随其后的首个回答为 `2轮`。
  * 便于长会话场景下快速定位和回溯上下文层级。
* **实现逻辑**：从聊天流 `ChatList` 递归向下传递当前消息节点的索引，并在 `ChatMessageNerdLine` 中通过消息图标渲染显示。
* **配置开关**：在 **设置 -> 界面显示设置** 中新增了独立控制开关「显示消息楼层」，默认开启，支持随时开启或关闭。

### 3. SillyTavern 预设兼容 (ST Preset Compatibility)
* **功能描述**：支持导入 SillyTavern Chat Completion 预设 JSON，并在聊天时按 `prompt_order`、marker、In-Chat depth 组装提示词，而不是把预设硬塞进 ModeInjection。
* **运行时能力**：`prompt_order[].enabled` 启用状态、relative / in-chat 排序、`injection_trigger`、`chatHistory` 与角色卡 marker、`{{char}}`/`{{getvar}}` 等 ST 宏、会话级变量持久化、`promptOnly` 预设 Regex、`character_book` / Lorebook 世界书 marker、`squash_system_messages`、`assistant_prefill`。
* **Provider 适配**：Claude / Gemini / OpenAI Responses 只接受一条顶层 system，组装器会自动合并开头 system，并把历史后的 system 降级为 user，避免提示词被 provider 静默丢弃。
* **UI**：`提示词 → ST 预设` 支持导入、启停、删除、导出原始 JSON、查看 prompt_order / marker / Regex，以及离线组装 Inspect；助手基础设置中可绑定预设。
* **兼容性**：旧助手（无 `stPresetId`）完全走原路径；预设删除或停用后自动回退 legacy 路径，不会崩溃。
* **存储**：Room `st_presets` / `st_variables` 表，原始 JSON 原样保留；数据库版本 35。
* **已知限制**：显示层 Regex、Text Completion 预设、世界书递归 / sticky / 分组竞争、`continue_prefill`、`names_behavior` 暂不支持。
* **文档**：`docs/ST-PRESET-COMPAT.md`。

### 4. SillyTavern 角色卡内嵌世界书导入 (Character Card Worldbook Import)
* **功能描述**：在助手页面导入 SillyTavern 角色卡（PNG 或 JSON）时，若角色卡包含内嵌世界书（`character_book`），系统会自动检测并弹出确认提示对话框，询问用户是否同时导入该世界书。
* **交互流程**：
  * **导入世界书**：将角色卡中的世界书完整转化为 RikkaHub 原生世界书（`Lorebook`）并存入全局世界书库，同时将新生成的 `lorebookId` 自动关联至该助手；同时清空角色卡内未转化的 `characterBook`，保证世界书在「设置 → 提示词 → 世界书」中可视化编辑、启停、导出，且避免聊天组装时双重注入。
  * **仅导入角色**：仅保留角色基本设定（名称、描述、第一条消息等），不将世界书导入至世界书库或助手中。
  * **取消**：关闭弹窗，终止本次导入操作。
* **解析兼容性**：
  * **格式支持**：兼容 PNG `tEXt` 块的 `[chara:` 及 `[ccv3:` 格式与原生 JSON 角色卡；
  * **结构支持**：同时支持 `data.character_book` 与顶层 `character_book`，兼容 `entries` 为数组（Array）或对象映射（Map）两种组织形式；
  * **条目属性转换**：自动映射条目名称/注释（`comment`/`name`）、触发关键词（`keys`/`key`）、次要关键词、常驻激活（`constant`）、启用状态（`enabled`/`!disable`）、优先级插入顺序（`insertion_order`/`order`）、插入位置（`before_char` 映射为 `BEFORE_SYSTEM_PROMPT`，`after_char` 映射为 `AFTER_SYSTEM_PROMPT`，`at_depth` 映射为 `AT_DEPTH` 等）、扫描深度（`scan_depth`）、深度（`depth`）、正则表达式开关（`use_regex`）与大小写敏感度；
  * **世界书中心扩展**：同步增强了「提示词 → 世界书」独立导入入口，支持直接选择包含世界书的卡片或独立导出的 Tavern 世界书 JSON。

### 5. `/compact` 智能上下文压缩与展开管理 (Context Compaction)
* **功能描述**：提供强大的上下文压缩指令，支持图形化弹窗与命令行快速执行，将历史对话压缩为高密度结构化总结，极大节省长会话 Token 并保持核心记忆。
* **交互与用法**：
  * **用法 1 (`/compact`)**：无参数输入直接呼出压缩配置弹窗：
    * 支持可视化配置压缩起始楼层与结尾楼层范围（默认前序全部楼层）；
    * 设定压缩目标大小（单位为 K tokens）；
    * 从当前已启用的文本模型列表中自选专门的压缩模型；
    * 自定义发给压缩模型的引导提示词（内置默认高质量压缩 Prompt）；
    * 一键开关「压缩后是否自动 hide 已压缩的楼层」。
  * **用法 2 (`/compact 起始 结尾 目标大小 [hide]`)**：直接在输入框快速执行（如 `/compact 1 20 2 hide`），不弹窗，默认使用当前对话模型与标准提示词；`hide` 为可选参数。
  * **用法 3 (`/compact withdraw`)**：一键撤销压缩，不仅恢复原始对话视图，且自动恢复被自动隐藏的各轮次楼层。
* **聊天流居中卡片与折叠展开**：
  * 压缩结果在对话列表中以居中卡片展示摘要正文（纯 Markdown 排版，不显示字数、楼层、Token 等冗余元信息，美观整洁）。
  * 卡片支持点击随时展开/收起，展开后无缝呈现被压缩的原始对话楼层（保持原有排版及楼层号、字数、Token 统计）。
* **纯净隔离请求与请求日志支持**：
  * 压缩请求严格隔离当前会话的系统提示词、模式注入、世界书、工具/技能以及预设，避免无关上下文干扰总结质量。
  * 请求直接经由标准网络客户端发出，在「设置 → 请求日志」中完整记录，支持透明审查。

### 6. `/hide` 与 `/unhide` 楼层上下文管理面板 (Floor Hide Management Sheet)
* **功能描述**：大幅增强楼层隐藏与取消隐藏指令，既保持原有 `/hide 5`、`/hide 3-18` 快捷指令体验不变，又新增无参数弹窗管理面板。
* **交互与能力**：
  * **用法**：输入 `/hide` 或 `/unhide`（无参数）直接弹出沉浸式管理底栏（BottomSheet）。
  * **范围操作**：在弹窗中直接输入楼层（如 `5` 或 `3-18`），一键执行「隐藏该范围」或「取消隐藏」。
  * **已隐藏楼层列表**：
    * 独立子页面展示当前会话中所有已被隐藏的楼层；
    * 附带消息预览片段（Snippet）与角色身份；
    * 单独提供「取消隐藏」与「跳转到该楼层」快捷按钮。
  * **进入消息列表（多选管理）**：
    * 浏览会话中的全部历史楼层，显示每层内容摘要，并支持按需点开「展开/收起」查看长文本；
    * 支持勾选复选框进行多选，支持「全选 / 取消全选」；
    * 底部批量操作栏支持「批量隐藏选中」与「批量取消隐藏选中」。

### 7. 输入框 `/` 快捷斜杠指令补全 (Slash Command Autocompletion)
* **功能描述**：在聊天输入框键入 `/` 时，即刻弹出指令联想补全悬浮面板。
* **特性**：
  * **动态搜索匹配**：随着用户继续键入（如 `/co`、`/hid`），自动模糊过滤并置顶匹配项。
  * **详尽用法说明**：每条指令候选项目下方均以精炼的小号辅助文本展示用法参数格式与功能简要说明，新手开箱即懂。

---

## 二、 纯净化与去商业化改造 (De-commercialization & Clean-up)

原项目内置了较多商业化赞助商的中转站推广信息、推广返利头、弹窗和不可删除的默认服务商。本 Fork 对其进行了彻底的净化改造：

### 1. 移除内置锁定的中转站服务商
* **默认提供商移除**：从默认 AI 提供商列表中移除了所有内置且锁定的商业中转站（包括 `AiHubMix`、`APIMart`、`随想AI网关`、`MaruCode`）。
* **推荐提供商清空**：清空了设置中“推荐服务商”底栏（原包含带返利 AFF 参数的推广中转站列表）。
* **TTS 默认提供商精简**：从默认语音合成服务商列表中移除了 `AiHubMix`，恢复纯净的系统原生 TTS 配置。
* **老用户迁移自动清理**：在 DataStore 配置加载流中加入了自动过滤逻辑，即使用户是从旧版本覆盖升级，也会在启动时自动静默清除已保存的赞助商中转配置。

### 2. 移除所有广告、弹窗与推广信息
* **启动赞助提醒弹窗**：移除了原版累计启动超过 100 次后弹出的强制赞助/捐赠提示框。
* **设置页捐赠入口**：移除了设置页面中的「捐赠」导航菜单项。
* **中转站优惠标签**：移除了服务商列表中针对特定赞助商硬编码显示的 `10% 优惠` 促销角标。
* **文档商业信息脱敏**：清理了中英文 `README` 中的「💖 Sponsors」与「💰 Donate」区块，并注明了本 Fork 分支的特性。

### 3. 清除网络隐蔽推广标识与冗余资源
* **HTTP 请求头去追踪**：移除了向 `aihubmix.com` 发送请求时强行追加的推荐码请求头 `APP-Code: DKHA9468`。
* **图标匹配规则简化**：精简了图标匹配器中针对该中转站的正则与 SVG 资源映射。

---

## 三、 构建与签名体系优化 (Build & Packaging)

### 1. 自定义 Release 签名配置
* **背景**：国内主流定制系统（如小米澎湃 OS、OPPO ColorOS 等）对使用 Android 默认 debug 密钥签名的 APK 会进行拦截并报「缺乏开发者证书」。
* **改造**：生成了独立的正式发布密钥库 `my-release-key.jks`，并在 Gradle 构建脚本中配置为默认的 Release 签名方案，确保打包出的 APK 可直接顺利安装。
* **证书信息**：
  * **别名**：`my-alias`
  * **算法**：2048 位 RSA (`SHA384withRSA`)
  * **有效期**：至 2054 年

### 2. 国内 Maven 镜像源加速
* 在 `settings.gradle.kts` 中加入了阿里云 Maven 镜像（`public`、`google`、`gradle-plugin`），大幅提升在国内网络环境下的 Gradle 依赖解析与构建速度。

### 3. 版本号规范
* 采用在原版版本号后追加微版本号的方式（例如 `2.5.6.1`、`2.5.6.2`），清晰体现修改版迭代轨迹且不与上游冲突。

---

## 四、 代码改动文件清单 (File Inventory)

| 模块 / 路径 | 改动类型 | 详细说明 |
| :--- | :--- | :--- |
| `app/build.gradle.kts` | 签名与版本 | 配置 Release 签名密钥，更新版本号（`3.1.1.2`，versionCode 200） |
| `settings.gradle.kts` | 依赖配置 | 引入阿里云 Maven 镜像仓库源加速编译 |
| `README.md` / `README_ZH_CN.md` | 文档更新 | 移除赞助商与打赏链接，标注 Fork 分支新特性 |
| `ChatMessageNerdLine.kt` | 功能优化 | 新增楼层与字数统计信息展示组件；使用 `countWords()` 进行智能多语言字数统计并带 `remember` 缓存 |
| `StringUtils.kt` | 功能增强 | 实现多语言智能字数统计扩展函数 `countWords()`（支持中/英/日与标点符号，过滤空白字符） |
| `ChatMessage.kt` | 功能支持 | 传递 `nodeIndex` 楼层索引至底层状态行 |
| `ChatList.kt` | 功能支持 | 将消息列表的真实 item index 作为楼层号向下传递 |
| `SettingPreferencesUIPage.kt` | UI 新增 | 新增「显示字数统计」开关设置项（适配 MD3 Expressive） |
| `strings.xml` (中/英) | 国际化 | 增加字数统计功能的双语文本定义 |
| `SettingPage.kt` | 纯净化 | 移除 100 次启动弹出的赞助对话框与捐赠设置条目 |
| `SettingProviderPage.kt` | 纯净化 | 移除赞助中转站的硬编码“10% 优惠”角标 |
| `DefaultProviders.kt` | 纯净化 | 移除默认内置且不可删除的 4 家赞助商模型配置 |
| `RecommendedProviders.kt` | 纯净化 | 清空推广服务商推荐列表 |
| `Settings.kt` | 功能支持 | 在 DisplaySetting 中持久化 `showWordCount` 配置 |
| `DefaultSettings.kt` | 纯净化 | 移除默认 TTS 赞助商 `AiHubMix` |
| `SettingsNormalization.kt` | 纯净化 & 迁移 | 加入配置规整与老用户数据加载时的主动过滤清除逻辑 |
| `Request.kt` (ai 模块) | 纯净化 | 移除对赞助商域名的 `APP-Code` 隐藏请求头注入 |
| `AIIconMatcher.kt` | 纯净化 | 移除赞助商图标规则与相关匹配正则 |
| `Message.kt` (ai 模块) | 功能新增 | `UIMessage` 增加 `isHidden` 字段并在 `limitContext` 中自动过滤被隐藏消息 |
| `ChatService.kt` | 功能新增 | 增加 `/hide`、`/unhide` 楼层指令拦截与解析执行器，以及切换单条消息隐藏状态 API |
| `ChatMessageNerdLine.kt` | UI 优化 | 楼层图标升级为双向轮次图标（`ArrowLeftRight`），格式为数字+“轮”（如 `1轮`），显示隐藏状态 |
| `ChatMessageActions.kt` | 功能支持 | 消息操作菜单新增「隐藏/恢复上下文」快捷入口 |
| `web-ui` 相关组件 | 功能与 UI | 同步支持轮次图标、去掉 `#`、支持 `isHidden` 状态显示与上下文排除 |
| `Logging.kt` (common 模块) | 配置优化 | 将 HTTP 请求日志中的「记录请求」（`requestLoggingEnabled`）默认开启 |
| `data/stpreset/*` | 功能新增 | ST 预设模型、解析器、宏引擎、Regex 管线、世界书与 PromptAssembler |
| `data/db/entity/StPresetEntity.kt` / `data/db/dao/StPresetDao.kt` | 功能新增 | `st_presets`、`st_variables` 表与 DAO；Room 版本 33 |
| `data/repository/StPresetRepository.kt` | 功能新增 | 预设导入/缓存/启停/删除与变量持久化 |
| `data/model/Assistant.kt` / `CharacterCard.kt` / `ConversationConfig.kt` | 功能新增 | `stPresetId` 绑定、结构化角色卡、会话级预设快照 |
| `data/ai/ChatRequestFactory.kt` | 功能新增 | `ST_PRESET` / legacy 双路径，预设模式下接管 system / marker / in-chat |
| `ui/pages/extensions/PromptPage.kt` / `PromptVM.kt` | UI 新增 | ST 预设 tab：导入、启停、删除、导出、详情与 Inspect |
| `ui/pages/assistant/detail/AssistantBasicPage.kt` / `AssistantImporter.kt` | UI / 功能 | 预设绑定入口；V2/V3 角色卡结构化字段与 `character_book`，导入世界书弹窗确认 |
| `data/stpreset/TavernWorldBookParser.kt` | 功能新增 | ST 角色卡内嵌世界书与独立世界书通用转换引擎，映射到原生 `Lorebook` |
| `app/build.gradle.kts` | 签名与版本 | 配置 Release 签名密钥，更新版本号（`3.1.1.3`，versionCode 201） |
| `docs/ST-PRESET-COMPAT.md` | 文档 | 支持范围、使用方式、限制与测试说明 |

---

## 五、 版本发布记录 (Release Notes)

* **v2.5.6**：初始构建版本，添加字数统计支持，适配国内签名。
* **v2.5.6.1**：同步上游主干代码；新增消息楼层显示（#1, #2）；清理 README 及应用界面内的赞助商和打赏入口。
* **v2.5.6.2**：彻底拔除 DataStore 默认与推荐中的锁定中转商提供者；加入老配置自动过滤清洗逻辑；移除请求头推广代码。
* **v3.0.0**：同步上游官方 3.0.0 大版本更新（全面升级至 Material 3 Expressive 风格、统一 mediagen 媒体生成模块、Markdown 收敛等）；适配新版架构下的设置拆分与 UI 组件；保持字数统计、楼层编号、去商业化及独立签名优化。
* **v3.0.0.1**：
  1. **楼层图标与格式更新**：将楼层指示器图标更换为往复轮次图标（HugeIcons `ArrowLeftRight` 与 Web 端 `ArrowRightLeft`），并且图标后不再添加 `#` 前缀（直接显示纯数字）。
  2. **隐藏楼层功能 (`/hide`)**：在聊天输入框支持使用 `/hide 5`、`/hide 3-18` 等指令（以及 `/unhide`）隐藏指定楼层消息；消息操作面板中亦可快速切换隐藏状态；**被隐藏的消息将彻底排除在后续 AI 模型请求上下文之外**。
  3. **彻底净化 README 文档**：重写中英文及繁体 `README`，彻底清除所有指向原项目的外部群组、官网下载、Google Play 链接、原项目 Star History 及一切商业赞助商宣传信息。
* **v3.1.0.1**：
  1. **同步官方上游 v3.1.0 架构与特性**：
     - 新增 distribution 渠道分发支持（github 默认 / play），共用 applicationId；
     - 新增 ChatGPT / Grok OAuth 订阅登录及用量额度查询支持；
     - 新增远程 SSH 工作区后端与连接池管理；
     - 新增 `usage_record` 用量流水记录与全维度可视化统计页面；
     - 技术状态行全面重构：显示步数、生成速率（tok/s）与上下文占用，点击弹出用量分布条形图与精确 Token 明细；
     - 修复超长消息裁剪与横向滚动、聊天多图滑动预览与绘图支持；
     - 终端模拟器内置模块迁移（`:terminal`）及新版 TypeScript i18n 工具链集成。
  2. **定制特性适配与延续**：
     - 将楼层轮次指示（往复图标与无 `#` 数字）、隐藏状态标识（`ViewOff`）以及字数统计无缝融入新版技术状态栏中；
     - 彻底清除上游新增的商业中转站推荐（钠API）及相关图标正则匹配规则；
     - 保留独立发布签名与国内 Maven 源加速。
* **v3.1.0.1.1**：新增 SillyTavern Chat Completion 预设兼容（导入、prompt_order / marker / In-Chat depth 组装、ST 宏与会话变量、prompt-only Regex、character_book / Lorebook 世界书、系统消息 provider 适配）；新增 `提示词 → ST 预设` 管理页与助手预设绑定；数据库升级到 33；版本号 `3.1.0.1.1`（versionCode 195）。
* **v3.1.0.1.2**：ST 预设详情支持逐条启用/停用、上移/下移排序、恢复默认排序；未挂载/未知 marker/空内容条目标注「不适用」；调整预设卡片和详情列表布局，修复标签换行与操作区错位；数据库升级到 34；版本号 `3.1.0.1.2`（versionCode 196）。
* **v3.1.0.1.3**：ST 预设导入时自动保存可编辑副本（原始 JSON 单独保留，可恢复默认）；条目支持点开查看/修改名称、角色、正文；`main` / `worldInfoBefore` / `worldInfoAfter` / `chatHistory` 等核心 marker 禁止停用，同时保留缺失 marker 的兜底；ST 预设改为在「助手 → 拓展管理」中启用，导入本身不启用；不使用预设的助手继续走原 ModeInjection / Lorebook 路径；数据库升级到 35；版本号 `3.1.0.1.3`（versionCode 197）。
* **v3.1.0.2**：正式发布 ST 预设兼容（导入、副本编辑、prompt_order / marker / In-Chat depth 组装、ST 宏与会话变量、prompt-only Regex、character_book / Lorebook 世界书、provider system 适配）；新增「提示词 → ST 预设」与助手拓展管理入口；默认开启 HTTP 请求日志；数据库版本 35；版本号 `3.1.0.2`（versionCode 198）。
* **v3.1.1.1**：
  1. **同步官方上游 v3.1.1 架构与特性**：
     - 新增后台生成悬浮窗支持（查看进度、快速回复与确认工具调用）；
     - 新增记忆整理模型独立设置项；
     - 媒体提供商配置改为独立详情页；
     - 全文搜索与分词架构升级（移除 jieba 依赖与外部词典，提升搜索速度并缩小 APK 体积约 5MB）；
     - 纯 Kotlin 原生实现 PDF 文本提取替换 mupdf 原生动态库，APK 缩小约 4.5MB；
     - 扫码导入提供商由 quickie 换成 CameraX + zxing，移除未使用的依赖；
     - 图片裁剪由 uCrop 替换为 Compose 自研 ImageCropDialog；
     - 模型能力识别改用 models.dev 数据源，优化 token 与模型匹配机制；
     - 修复非英文系统下余额查询数字格式错误、修复公式中 `<` 符号被当作 HTML 标签渲染失败、修复朗读带 HTML 标签等问题；
     - 新增德语多语言支持。
  2. **定制特性适配与延续**：
     - 解决多语言资源与构建脚本版本冲突，无缝合并 Fork 与上游新增内容；
     - 完整保留字数统计、隐藏楼层上下文排除（`/hide` 指令），并将楼层轮次格式更新为数字+“轮”（如 `1轮`）；
     - 完整保留 SillyTavern Chat Completion 预设兼容引擎与可视化管理体系；
     - 保持去商业化与纯净化（清空锁定商业赞助商中转与推荐列表）；
     - 保留独立发布签名密钥（`my-release-key.jks`）与阿里云 Maven 镜像加速；
     - 版本号升至 `3.1.1.1`（versionCode: 199）。
* **v3.1.1.2**：
  1. **扩展管理数量修复**：修复当助手启用 ST 预设时，拓展管理入口徽标未计入 ST 预设导致数量少算 1 的问题；
  2. **ST 预设编辑界面风格对齐**：将 ST 预设编辑底栏弹窗、条目卡片、启用开关等 UI 风格与世界书及模式注入完全对齐统一；
  3. **智能多语言字数统计**：重构消息字数统计，精准适配中文、英文、日文及中英日混排，彻底排除空白与换行字符；
  4. **版本号升级**：升至 `3.1.1.2`（versionCode: 200）。
* **v3.1.1.3**：
  1. **角色卡内嵌世界书导入支持**：助手处导入 SillyTavern 角色卡（PNG/JSON）时，若包含内嵌世界书，弹出提醒对话框询问是否导入；
  2. **世界书与助手双向绑定**：确认导入时将世界书解析转入系统 `Lorebook` 库，并自动关联至助手的 `lorebookIds`，确保在无预设和有预设模式下均能正常触发；
  3. **V2/V3 与多格式健壮兼容**：支持 PNG `chara`/`ccv3` 块、`character_book` 数组与字典结构、完整映射条目关键词、常驻激活、正则表达式、位置与深度；
  4. **版本号升级**：升至 `3.1.1.3`（versionCode: 201）。
* **v3.1.1.4**：
  1. **DeepSeek 极速前缀缓存架构时序重构**：针对 DeepSeek 64-token 块级前缀缓存机制重构组装流，划分 Zone A（静态前缀区）、Zone B（稳定历史区）、Zone C（动态后缀区）；
  2. **静态 Marker 与提示词提拔**：将角色描述、性格、场景设定、对话样例等静态 Marker 及静态 System 提示词提拔至对话历史之前；
  3. **预设导入自动优化与一键重排**：导入 ST 预设时自动重排，预设编辑页新增「优化缓存排序」操作；
  4. **版本号升级**：升至 `3.1.1.4`（versionCode: 202）。
* **v3.1.1.5**：
  1. **ST 预设编辑面板 UI 错乱修复**：
     - 条目标签采用 `FlowRow` 自动换行折行，杜绝长标签横向溢出及右侧开关/操作菜单遮挡挤压；
     - 操作菜单（`ItemActionMenu`）与启用开关结构标准化，全量条目统一固定右侧对齐；
     - 底部操作按钮层级优化（「查看装配」与「优化缓存排序」等权并排，单行截断防溢出；重置默认独立全宽显示）；
     - 修复会话变量卡片标题与副标题溢出排版异常。
  2. **全角色静态提示词全局前缀提拔（彻底根治缓存穿透）**：
     - 重构排序策略与组装流：所有不含动态宏的固定文本提示词条目（无论是 SYSTEM、USER 还是 ASSISTANT 角色，无论作者原始配置位置是否在历史之后）均全量前置提拔至 Zone A（对话历史之前）；
     - 拓宽动态宏识别引擎，仅将包含 `cur_time`、`idle_duration`、`input`、`last_message` 等轮次级变动的条目及 `worldInfoAfter`、In-Chat 深度条目保留在 Zone C（对话历史之后）；
     - 确保请求前缀从第 0 个 token 至历史前 100% 字节冻结，彻底消除每轮对话由于固定条目位置位移导致的 LLM 缓存穿透。
  3. **版本号升级**：升至 `3.1.1.5`（versionCode: 203）。
* **v3.1.1.6**：
  1. **同步官方上游最新 Commits**：
     - 同步合并官方 `master` 上游最新 commits（包含多语言完善、依赖安全升级与架构优化）；
  2. **`/compact` 上下文压缩与展开增强**：
     - **图形化弹窗 (`/compact`)**：无参数执行弹出弹窗，支持自定义压缩楼层范围、目标大小 (K tokens)、自选启用文本模型、编辑压缩提示词并支持压缩后自动隐藏已压缩楼层；
     - **命令行快速压缩 (`/compact 起始 结尾 目标大小 [hide]`)**：直接在输入框按参数快速执行，支持可选 `hide` 参数；
     - **一键撤销压缩 (`/compact withdraw`)**：撤销压缩记录并自动恢复此前自动隐藏的所有楼层；
     - **居中摘要卡片与折叠展开**：对话流中居中渲染纯 Markdown 摘要（隐藏楼层/字数/Token 等冗余信息），支持随时展开查看原始被压缩楼层（保持原有排版及楼层/字数/Token 等信息）；
     - **隔离组装与审计支持**：压缩请求不发送主对话系统提示词、模式注入、世界书、工具技能与预设，并在 HTTP 请求日志中透明记录；
  3. **`/hide` 与 `/unhide` 楼层管理面板**：
     - 无参数输入 `/hide` 或 `/unhide` 弹出全功能管理底栏面板；
     - 支持面板内输入范围快速隐藏/取消隐藏；
     - 支持查看已隐藏楼层列表、内容预览片段、一键取消隐藏与跳转楼层；
     - 新增消息列表多选管理视图，支持浏览全量楼层内容摘要、展开长文本、多选复选框、全选与批量隐藏/取消隐藏；
  4. **斜杠指令 `/` 动态自动补全**：
     - 输入框键入 `/` 弹出可用指令补全列表，随输入动态过滤高亮，每项下方附带参数说明与用法指南；
  5. **界面偏好新增「显示消息楼层」开关**：
     - 在「设置 → 界面显示设置」中新增「显示消息楼层」独立开关（默认开启），支持随时控制消息状态栏中楼层轮次标识的显示与隐藏；
  6. **版本号升级**：升至 `3.1.1.6`（versionCode: 204）。
* **v3.1.1.7**：
  1. **修复启用 ST 预设时 `/compact` 压缩后新消息丢失问题 (#1)**：
     - 修复 `ChatService.executeCompact` 保存 `boundaryNodeId` 时使用 `MessageNode.id` 而非 `UIMessage.id` 的偏差；
     - 在 `ChatRequestFactory.buildPresetStep` 中改为先基于原始完整楼层列表执行 `StPromptAssembler.trimHistoryForCheckpoint` 裁剪压缩区间（并保留区间内已收藏的里程碑消息），再执行 `limitContext` 与历史转换器，彻底解决自动隐藏已压缩楼层后因消息列表长度收缩导致新输入消息被误裁的 Bug；
  2. **ST 预设排序与占位符优化 (#2)**：
     - **长按拖动排序**：在 ST 预设详情编辑面板中支持按住条目上下拖动调整发送顺序；
     - **自动优化排序开关**：新增「自动优化排序」开关，支持自由禁用自动缓存优化排序并切换为预设原始顺序或手动自定义顺序；
     - **保持 Marker 独立排序**：保持 `worldInfoBefore` 与 `worldInfoAfter` 为独立条目并保留原有命名与定位，支持与中间夹带的 `Persona Description` / 角色人设等条目自由调整顺序；
  3. **`/compact` 压缩摘要卡片内容折叠 (#3)**：
     - 聊天流中的压缩摘要卡片默认折叠长摘要，仅展示前几行预览与渐变过渡，点击「展开压缩内容」/「收起压缩内容」可随时展开或折叠完整摘要；
  4. **版本号升级**：升至 `3.1.1.7`（versionCode: 205）。
* **v3.1.1.8**：
  1. **流式输出过程中隐藏轮次与字数统计 (#9)**：
     - 在消息生成/流式输出时隐藏状态栏（Nerd Line）中的轮次序号与动态字数统计，待生成完毕后再行渲染，避免生成过程中数字闪烁并保持界面视觉整洁（同步适配 Android 原生与 Web UI）。
  2. **HTTP 日志导出与下载支持 (#6)**：
     - 日志页面右上角新增「下载 HTTP 日志」按钮，支持一键将全局 HTTP 请求与响应记录导出为格式化 JSON 文件；
     - 详情弹窗中新增单条请求日志下载按钮，支持快速导出当前单条日志。
  3. **ST 预设提示词条目新增与删除 (#8)**：
     - 预设编辑面板新增「+」操作按钮，支持添加自定义提示词条目并指定名称、角色（System/User/Assistant）与内容；
     - 条目操作菜单与编辑弹窗中新增「删除条目」选项及二次确认对话框，同时设置受保护保留项拦截，严禁误删 `main`、`worldInfoBefore`、`worldInfoAfter`、`chatHistory` 等核心结构 Marker；
     - 底层自动同步维护原始预设 JSON 的 `prompts` 与 `prompt_order` 排序桶，支持持久化保存与原样导出。
  4. **ST 预设正则脚本（Regex Scripts）完整管理 (#5)**：
     - 全面支持查看、编辑、启用/停用、删除与新增预设内嵌的 Regex 脚本；
     - 兼容标准 SillyTavern 的 `extensions.regex_scripts` 与顶层 `regex_scripts` 双存储结构；
     - 正则编辑器支持配置查找正则、替换文本、作用目标（User 输入 / AI 输出）、仅显示层（MarkdownOnly）、仅提示词（PromptOnly）、替换宏变量（SubstituteRegex）、编辑时运行（RunOnEdit）、禁用开关以及生效深度区间（Min/Max Depth）。
  5. **ST 预设拖动排序边缘自动滚动 (#4)**：
     - 将拖动排序状态（`rememberReorderableLazyListState`）直接与父级 `LazyColumn` 滚动状态深度绑定，在长按条目拖动至屏幕顶部或底部边缘时自动触发平滑滚动，解决超长预设列表无法跨屏拖动的问题。
  6. **修复 ST 预设调整页快速滑动重复轮回 Bug (#7)**：
     - 重构预设编辑面板条目列表，将原有的单一超长复合 Item 改为 `LazyColumn` 原生轻量化虚拟条目（`items`），彻底根治 Compose 在快速滑动手势（Fling）到达边界时因超高单一组件引起的测量紊乱和回弹循环 Bug。
  7. **版本号升级**：升至 `3.1.1.8`（versionCode: 206）。
* **v3.1.1.9**：
  1. **ST 预设支持修改名称与描述 (#10)**：
     - 支持修改 ST 预设的名称与可选描述，持久保存在数据库及预设 JSON 的 `rikkahub_meta` 中，并在「提示词 → ST 预设」管理页与助手拓展管理页同步展示描述副标题。
  2. **清理 ST 预设重复的优化排序开关 (#11)**：
     - 移除 ST 预设编辑面板中与底部「优化缓存排序」按钮功能重复的「自动优化排序」开关，并在点击优化排序按钮后提供明确的 Toast 反馈。
  3. **`/compact` 压缩摘要内容支持一键复制与编辑修改 (#12)**：
     - 聊天流中的压缩摘要卡片右上角新增「复制」与「编辑」图标按钮，支持直接修改已生成的压缩总结并持久化回写数据库。
  4. **支持批量选择删除多轮聊天记录 (#13)**：
     - 消息长按/更多操作菜单新增「批量删除消息」入口，弹出多选管理面板，默认定位并勾选触发消息，支持全选、清空选择及一键删除选中的多轮消息（自动维护分支索引与滚动位置）。
  5. **支持在消息下方状态栏直接显示完整用量统计 (#14)**：
     - 在「设置 → 界面显示设置」中新增「直接显示完整用量统计」开关，开启后在消息状态栏（Nerd Line）直接内联展示输入、输出、推理及缓存命中 Token 明细（如 `↑4.2K (⚡3.8K) ↓512 (💭128)`）。
  6. **支持直接预览与编辑 AI 创建的工作区文档 (#15)**：
     - 消息下方的已编辑文件胶囊（如 `plot.md`）与工作区写入工具卡片均支持点击直接打开文件预览/编辑器；
     - 文件编辑器针对 `.md` / `.markdown` 文件新增「预览 / 编辑」模式切换按钮，支持直接渲染富文本 Markdown 排版。
  7. **修复 ST 预设下多步工具调用后不输出正文与 `<dream_think>` 吞正文问题**：
     - 在 `StPromptAssembler` 与 `ChatRequestFactory` 中将正在进行多步工具续写的 `activeToolTurn`（含 `functionCall` / `functionResponse`）挂载于 `Zone C` 尾部提示词之后，防止预设尾区预填消息插在 `functionResponse` 后打断模型工具续写链路，并保持跨工具步的前缀缓存与深度一致；
     - 将工作区提醒（`WorkspaceReminderTransformer`）在 ST 预设模式下注入 `Zone A` 静态系统区，防止污染 `<dream_history>`；
     - 增强 `ThinkTagTransformer`，支持 `<dream_think>...</think>` 混用闭合标签并在遇到 `<dream_plot>` / `<dream_body>` 时自动闭合思考块，同时支持在存在原生 `Reasoning` 时提取预设 `<dream_think>` 标签。
  8. **修复 Gemini 模型长思考/长上下文中途无报错截断问题（上游 #1166）**：
     - `GoogleStreamDecoder` 新增对流内 SSE `{"error": ...}` 事件及 `promptFeedback.blockReason` 的解析与异常抛出；
     - 当 SSE 连接在未收到 `finishReason` 且无工具调用时提前断开，或以 `MALFORMED_FUNCTION_CALL` / `OTHER` / `MAX_TOKENS` 等异常状态无输出结束时，抛出异常以触发 `AgentLoop` 网络自动重试或向用户展示明确错误提示。
  9. **版本号升级**：升至 `3.1.1.9`（versionCode: 207）。
* **v3.1.1.10**：
  1. **支持 Gemini 模型调用创建文档工具输出（防截断/防空回）(#20)**：
     - 自动识别 Gemini 模型（原生 Google 提供商或模型 ID 含 `gemini` 的中转渠道），支持强制模型通过 `create_document` 工具写入完整回复内容，大幅降低敏感或长篇创作内容直接文本回复时的截断与空回概率；
     - 新增 `ToolOutputTransformer` 输出转换器，在流式生成中与生成结束时实时解析 `create_document` 工具调用的 `content` 参数（支持未闭合流式 JSON 渐进解析），无缝转换为标准 `UIMessagePart.Text` 正文气泡渲染，并与 `<dream_think>` 思考块及 ST 预设正则完全兼容；
     - 在 `AgentLoop.finishStep` 阶段直接完成工具输出到正文的落库转换，单轮请求一步完成，无需二次工具回传请求，且在下一轮对话中自然作为标准 Assistant 历史上下文发送给 AI；
     - 在「助手设置 → 基础设定」中新增「Gemini 工具输出 (防截断)」独立开关（默认开启）。
  2. **修复 `/compact` 压缩后创建分支丢失压缩总结问题 (#19)**：
     - 在 `ChatService.forkConversationAtMessage` 创建分支会话时，若分叉位置覆盖已压缩楼层区间，自动为新分支会话克隆并保存 `ConversationCheckpointEntity` 压缩摘要记录（同步适配分叉边界楼层与自动隐藏楼层列表），确保新分支完整保留压缩总结卡片与上下文注入；若在压缩起始楼层之前分叉，则自动恢复被隐藏楼层状态。
  3. **文档预览支持一键复制与编辑保存 (#18)**：
     - 工作区文件读取/写入工具预览弹窗（`FileContentPreview`）与工作区文件编辑器（`WorkspaceFileEditorPage`）新增「复制」与「编辑 / 保存」功能，支持直接在弹窗或编辑器中修改文档内容并同步写回工作区文件与消息记录；
     - 修复 `MarkdownBlock` 外层误用 `SelectionContainer` 导致长按无法自由选择复制文本的问题，统一替换为适配 `BetterText` 的 `SelectableContent`。
  4. **版本号升级**：升至 `3.1.1.10`（versionCode: 208）。
* **v3.1.2.1**：
  1. **同步官方上游 `v3.1.2` 更新**：
     - 合并官方上游 `v3.1.2`（`fe5a28f9`）全部新特性与修复，保留所有本地定制增强与去商业化净化逻辑；
  2. **版本号升级**：升至 `3.1.2.1`（versionCode: 209）。
* **v3.1.2.2**：
  1. **修复网关超时 504 等 HTML 错误页面报错解析异常 (#21)**：
     - 修复当反代网关（OpenResty / Nginx / Cloudflare）返回 `504 Gateway Time-out`、`502 Bad Gateway` 等 HTML 错误页面时，`Json.parseToJsonElement` 将 `<html>\r\n` 误解析为非引号 Token 并在 `<head>` 处抛出 `JsonDecodingException: Expected EOF after parsing, but had < instead at offset 9`、从而吞掉真实 HTTP 状态码与错误信息的问题；
     - 在 `ErrorParser` 中新增 HTML 错误页智能提取逻辑，自动识别 `<title>`、`<h1>` 与网关标识（如 `[openresty]`、`[nginx]`）并附带可读中文原因提示，同时全量覆盖同步/流式请求响应解析。
  2. **新增「以文件方式发送输入」功能 (#22)**：
     - 在「助手设置 → 基础设定」中新增「以文件方式发送输入」独立开关（默认关闭）；
     - 开启后，发送请求时自动将真实用户文本消息封装为 `user_input.txt` 文件附件（Google Gemini 采用 `inlineData` `text/plain` Base64、Claude 采用原生 `document` 块、OpenAI 等采用 `<UploadFile>` 文档块）并附带文件读取指令，同时在 ST 预设下自动对 `{{lastUserMessage}}` 宏做掩码保护（保留世界书关键词匹配能力），大幅降低输入端敏感审查拦截与空回概率。
  3. **将工具输出防截断拓展至全部模型 (#23)**：
     - 将「工具输出 (防截断)」（`create_document` 文档工具输出）从仅限 Gemini 模型拓展至所有非生图模型；
     - 针对 Claude 思考模式、DeepSeek Reasoner 等不支持同时启用 `thinking` 与强制 `tool_choice` 的模型，自动采用注册 `create_document` 工具定义 + 尾部强制指令的兼容模式，避免触发 HTTP 400 报错。
  4. **修复 `/compact` 压缩后消息概览与楼层列表无法跳转问题 (#24)**：
     - 新增 `messageIndexToLazyIndex` 与 `lazyIndexToMessageIndex` 双向索引映射，解决启用上下文压缩卡片后 `LazyColumn` 条目索引与 `conversation.messageNodes` 真实消息索引偏移不一致导致点击概览卡片无法跳转或跳错楼层的问题；
     - 当点击位于已压缩区间内的历史消息时，自动展开压缩原文并精确定位滚动至目标楼层。
  5. **版本号升级**：升至 `3.1.2.2`（versionCode: 210）。




