# RikkaHub (LightEastDS Fork) 修改与定制文档

本文档详细记录了 **LightEastDS** 维护的 [RikkaHub](https://github.com/LightEastDS/rikkahub) 分支相较于官方上游（[re-ovo/rikkahub](https://github.com/re-ovo/rikkahub)）的所有改动与增强，涵盖**新功能扩展**、**去商业化/净化**、**构建与签名优化**以及**改动文件清单**。

---

## 一、 新增功能 (New Features)

### 1. 消息字数统计 (Message Word Count)
* **功能描述**：在聊天消息下方的状态栏（Nerd Line）中直观显示该条消息的文本字符数（例如：`128 字`）。
* **配置开关**：在 **设置 -> 界面显示设置** 中新增了独立控制开关「显示字数统计」，支持随时开启或关闭。
* **国际化支持**：同步适配了中文与英文资源字符串。

### 2. 对话楼层编号显示 (Message Floor Number)
* **功能描述**：在每条消息的状态栏左侧显示消息所在的楼层编号（如 `#1`、`#2`、`#3` 等）。
  * 用户发送的第一条问题为 `#1`。
  * LLM 紧随其后的首个回答为 `#2`。
  * 便于长会话场景下快速定位和回溯上下文层级。
* **实现逻辑**：从聊天流 `ChatList` 递归向下传递当前消息节点的索引，并在 `ChatMessageNerdLine` 中通过消息图标渲染显示。

### 3. SillyTavern 预设兼容 (ST Preset Compatibility)
* **功能描述**：支持导入 SillyTavern Chat Completion 预设 JSON，并在聊天时按 `prompt_order`、marker、In-Chat depth 组装提示词，而不是把预设硬塞进 ModeInjection。
* **运行时能力**：`prompt_order[].enabled` 启用状态、relative / in-chat 排序、`injection_trigger`、`chatHistory` 与角色卡 marker、`{{char}}`/`{{getvar}}` 等 ST 宏、会话级变量持久化、`promptOnly` 预设 Regex、`character_book` / Lorebook 世界书 marker、`squash_system_messages`、`assistant_prefill`。
* **Provider 适配**：Claude / Gemini / OpenAI Responses 只接受一条顶层 system，组装器会自动合并开头 system，并把历史后的 system 降级为 user，避免提示词被 provider 静默丢弃。
* **UI**：`提示词 → ST 预设` 支持导入、启停、删除、导出原始 JSON、查看 prompt_order / marker / Regex，以及离线组装 Inspect；助手基础设置中可绑定预设。
* **兼容性**：旧助手（无 `stPresetId`）完全走原路径；预设删除或停用后自动回退 legacy 路径，不会崩溃。
* **存储**：Room `st_presets` / `st_variables` 表，原始 JSON 原样保留；数据库版本 35。
* **已知限制**：显示层 Regex、Text Completion 预设、世界书递归 / sticky / 分组竞争、`continue_prefill`、`names_behavior` 暂不支持。
* **文档**：`docs/ST-PRESET-COMPAT.md`。

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
| `app/build.gradle.kts` | 签名与版本 | 配置 Release 签名密钥，更新版本号（`3.0.0.1`，versionCode 192） |
| `settings.gradle.kts` | 依赖配置 | 引入阿里云 Maven 镜像仓库源加速编译 |
| `README.md` / `README_ZH_CN.md` | 文档更新 | 移除赞助商与打赏链接，标注 Fork 分支新特性 |
| `ChatMessageNerdLine.kt` | 功能新增 | 新增楼层 `#1, #2` 与字数统计信息展示组件 |
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
| `ChatMessageNerdLine.kt` | UI 优化 | 楼层图标升级为双向轮次图标（`ArrowLeftRight`），取消数字前的 `#`，显示隐藏状态 |
| `ChatMessageActions.kt` | 功能支持 | 消息操作菜单新增「隐藏/恢复上下文」快捷入口 |
| `web-ui` 相关组件 | 功能与 UI | 同步支持轮次图标、去掉 `#`、支持 `isHidden` 状态显示与上下文排除 |
| `Logging.kt` (common 模块) | 配置优化 | 将 HTTP 请求日志中的「记录请求」（`requestLoggingEnabled`）默认开启 |
| `data/stpreset/*` | 功能新增 | ST 预设模型、解析器、宏引擎、Regex 管线、世界书与 PromptAssembler |
| `data/db/entity/StPresetEntity.kt` / `data/db/dao/StPresetDao.kt` | 功能新增 | `st_presets`、`st_variables` 表与 DAO；Room 版本 33 |
| `data/repository/StPresetRepository.kt` | 功能新增 | 预设导入/缓存/启停/删除与变量持久化 |
| `data/model/Assistant.kt` / `CharacterCard.kt` / `ConversationConfig.kt` | 功能新增 | `stPresetId` 绑定、结构化角色卡、会话级预设快照 |
| `data/ai/ChatRequestFactory.kt` | 功能新增 | `ST_PRESET` / legacy 双路径，预设模式下接管 system / marker / in-chat |
| `ui/pages/extensions/PromptPage.kt` / `PromptVM.kt` | UI 新增 | ST 预设 tab：导入、启停、删除、导出、详情与 Inspect |
| `ui/pages/assistant/detail/AssistantBasicPage.kt` / `AssistantImporter.kt` | UI / 功能 | 预设绑定入口；V2/V3 角色卡结构化字段与 `character_book` |
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

