<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHub (LightEastDS 修改版)</h1>

一个原生 Android LLM 聊天客户端，支持灵活切换不同的 AI 提供商进行对话 🤖💬

[English](README.md) | [繁體中文](README_ZH_TW.md) | 简体中文

</div>

<div align="center">
  <img src="docs/img/poster.png" alt="RikkaHub" width="800" />
</div>

## 🌟 本分支定制特性

这是基于 RikkaHub 原项目维护的定制与纯净化修改版分支（LightEastDS Fork），主要包含以下增强与改动：

1. **楼层轮次标识 (Floor Turn Indicator)**
   - 每条消息底部状态栏清晰展示所在楼层序号（如 `1`、`2`、`3`...），方便长会话快速定位。
2. **楼层上下文管理与隐藏 (`/hide` & `/unhide`)**
   - 快捷指令：
     - `/hide 5`：隐藏第 5 层消息。
     - `/hide 3-18`：隐藏第 3 层至第 18 层（均包含）之间的所有消息。
     - `/unhide 5` 或 `/unhide 3-18`：取消隐藏对应楼层。
   - **全新管理弹窗**：不带任何参数输入 `/hide` 或 `/unhide` 即弹出楼层管理窗口：
     - 输入指定范围一键隐藏或取消隐藏；
     - 查看目前被隐藏的楼层列表、内容预览，支持取消隐藏或直接跳转；
     - 进入消息列表视图，展示每层内容摘要（可展开查看），支持多选并批量隐藏/恢复。
   - **被隐藏的消息彻底排除在 AI 模型上下文之外**，既节省 Token 又杜绝干扰。
3. **对话上下文智能压缩 (`/compact`)**
   - **弹窗用法 (`/compact`)**：弹出压缩设置窗口，自定义压缩楼层范围、目标大小 (K tokens)、自选启用的模型、编辑压缩提示词，并选择是否自动隐藏已压缩楼层。
   - **快捷命令行 (`/compact 起始 结尾 目标大小 [hide]`)**：不弹窗直接执行（如 `/compact 1 15 2 hide`）。
   - **撤销压缩 (`/compact withdraw`)**：一键撤销压缩记录并自动恢复此前自动隐藏的楼层。
   - **视觉呈现**：对话流中居中展示压缩摘要（纯净 Markdown，不显示字数、楼层、Token 等冗余信息），点击可随时展开查看原始被压缩楼层（保留原排版与字数/Token 等信息）。
   - **纯净请求**：压缩时不发送主对话系统提示词、模式注入、世界书、技能工具与预设，且压缩请求同样可在 HTTP 请求日志中完整审查。
4. **输入框 `/` 斜杠指令自动补全与说明**
   - 输入 `/` 即弹出所有可用指令列表，随输入动态联想匹配，每条指令下方均标注参数格式与使用说明。
5. **消息字数统计 (Word Count)**
   - 在每条消息下方状态栏实时显示该消息的具体字数。支持中英日智能混合统计（中文计汉字和标点、英文计单词和标点、日文计汉字假名和标点，且不计空格与换行）。可在 **设置 -> 界面显示设置** 中按需开关。
6. **彻底纯净化与去商业化 (Clean & De-commercialized)**
   - 彻底移除了原版内置并锁定的商业中转站提供商及推广返利信息（AFF）。
   - 移除了启动 100 次后的赞助弹窗提示以及设置页面的捐赠菜单。
   - 移除了针对特定中转站的隐藏请求头追踪与硬编码优惠角标。
7. **正式签名与构建优化 (Build & Packaging)**
   - 内置独立正式发布签名，生成开箱即用的 Release APK，杜绝国内主流厂商系统（如小米澎湃 OS、OPPO ColorOS 等）报“缺乏开发者证书”的拦截问题。
   - 构建脚本集成国内 Maven 镜像加速，提升依赖下载与编译速度。

---

## 🚀 下载安装

请前往本项目的 GitHub Releases 页面下载最新版本：

🔗 **[GitHub Releases 下载](https://github.com/LightEastDS/rikkahub/releases)**

推荐下载 `app-arm64-v8a-release.apk`（适用于绝大多数现代 Android 设备）。

---

## ✨ 功能特色

- 🎨 现代化安卓应用设计（Material 3 Expressive / 预测性返回）与 🌙 暗色模式
- 📦 工作区：基于 PRoot 的 Linux 智能体环境
- 🎬 媒体生成（图片、视频）
- 🖥️ Web 多端访问支持（支持通过浏览器在电脑等设备上访问与聊天）
- 🛠️ MCP 与 Skills 扩展支持
- 🔄 多种类型的供应商支持，自定义 API / URL / 模型（支持 OpenAI、Google Gemini、Anthropic 兼容接口）
- 🖼️ 多模态输入支持（图片、文本文件、PDF、Docx、音频、视频）
- 📝 Markdown 高性能渲染（支持代码高亮、LaTeX 数学公式、表格、Mermaid 图表）
- 🔍 搜索功能（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等）
- 🧩 Prompt 变量支持（模型名称、当前时间等）
- 🤳 二维码快速导出与导入提供商配置
- 🤖 智能体自定义配置
- 🧠 基于文件的长期记忆
- 📝 AI 消息实时翻译
- 🌐 自定义 HTTP 请求头和请求正文

---

## 🛠️ 项目构建与开发

本项目使用 [Android Studio](https://developer.android.com/studio) 进行开发。

### 技术栈

- [Kotlin](https://kotlinlang.org/)（开发语言）
- [Jetpack Compose](https://developer.android.com/jetpack/compose)（声明式 UI 框架）
- [Material 3 Expressive](https://m3.material.io/)（UI 设计规范）
- [Koin](https://insert-koin.io/)（依赖注入）
- [Room](https://developer.android.com/training/data-storage/room)（本地数据库）
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore?hl=zh-cn#preferences-datastore)（键值偏好存储）
- [OkHttp](https://square.github.io/okhttp/)（网络客户端）
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)（JSON 序列化）
- [Coil](https://coil-kt.github.io/coil/)（图片加载）

### 构建步骤

1. 克隆本仓库到本地：
   ```bash
   git clone https://github.com/LightEastDS/rikkahub.git
   ```
2. 在 `app` 目录下添加你的 `google-services.json` 文件。
3. 编译 Debug 版本或 Release 版本：
   ```bash
   ./gradlew :app:assembleRelease
   ```

---

## 📄 开源许可证

本项目基于 [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0) 协议开源。
