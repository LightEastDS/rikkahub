<div align="center">
  <img src="docs/icon.png" alt="App 圖標" width="100" />
  <h1>RikkaHub (LightEastDS 修改版)</h1>

一個原生 Android LLM 聊天客戶端，支援靈活切換不同的 AI 提供商進行對話 🤖💬

[English](README.md) | 繁體中文 | [简体中文](README_ZH_CN.md)

</div>

<div align="center">
  <img src="docs/img/poster.png" alt="RikkaHub" width="800" />
</div>

## 🌟 本分支定制特性

這是基於 RikkaHub 原專案維護的定制與純淨化修改版分支（LightEastDS Fork），主要包含以下增強與改動：

1. **樓層輪次標識 (Floor Turn Indicator)**
   - 每條訊息底部狀態欄清晰展示所在樓層序號（如 `1`、`2`、`3`...），方便長對話快速定位。
2. **隱藏樓層功能 (`/hide`)**
   - 在輸入框中使用 `/hide` 指令即可精準隱藏指定樓層的訊息上下文：
     - `/hide 5`：隱藏第 5 層訊息。
     - `/hide 3-18`：隱藏第 3 層至第 18 層（均包含）之間的所有訊息。
     - `/unhide 5` 或 `/unhide 3-18`：取消隱藏對應樓層。
   - **被隱藏的訊息將不再作為上下文請求發送給 AI 模型**，既節省上下文 Token，又能消除無關歷史的干擾。
   - 訊息操作選單中亦提供快捷切換隱藏/恢復上下文的入口。
3. **訊息字數統計 (Word Count)**
   - 在每條訊息下方狀態欄即時顯示該訊息的具體字元數。可在 **設定 -> 介面顯示設定** 中依需求開關。
4. **徹底純淨化與去商業化 (Clean & De-commercialized)**
   - 徹底移除了原版內建並鎖定的商業中轉站提供商及推廣返利資訊（AFF）。
   - 移除了啟動 100 次後的贊助彈窗提示以及設定頁面的捐贈選單。
   - 移除了針對特定中轉站的隱藏請求頭追蹤與硬編碼優惠角標。
5. **正式簽名與構建優化 (Build & Packaging)**
   - 內建獨立正式發布簽名，生成開箱即用的 Release APK，杜絕主流系統報「缺乏開發者憑證」的攔截問題。
   - 構建腳本整合 Maven 鏡像加速，提升依賴下載與編譯速度。

---

## 🚀 下載安裝

請前往本專案的 GitHub Releases 頁面下載最新版本：

🔗 **[GitHub Releases 下載](https://github.com/LightEastDS/rikkahub/releases)**

推薦下載 `app-arm64-v8a-release.apk`（適用於絕大多數現代 Android 設備）。

---

## ✨ 功能特色

- 🎨 現代化安卓應用設計（Material 3 Expressive / 預測性返回）與 🌙 暗色模式
- 📦 工作區：基於 PRoot 的 Linux 智能體環境
- 🎬 媒體生成（圖片、影片）
- 🖥️ Web 多端訪問支援（支援透過瀏覽器在電腦等設備上訪問與聊天）
- 🛠️ MCP 與 Skills 擴充支援
- 🔄 多種類型的供應商支援，自訂 API / URL / 模型（支援 OpenAI、Google Gemini、Anthropic 相容介面）
- 🖼️ 多模態輸入支援（圖片、文字檔案、PDF、Docx、音訊、影片）
- 📝 Markdown 高性能渲染（支援代碼高亮、LaTeX 數學公式、表格、Mermaid 圖表）
- 🔍 搜尋功能（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等）
- 🧩 Prompt 變數支援（模型名稱、當前時間等）
- 🤳 二維碼快速導出與匯入提供商配置
- 🤖 智能體自訂配置
- 🧠 基於檔案的長期記憶
- 📝 AI 訊息即時翻譯
- 🌐 自訂 HTTP 請求頭和請求主體

---

## 🛠️ 專案構建與開發

本專案使用 [Android Studio](https://developer.android.com/studio) 進行開發。

### 技術棧

- [Kotlin](https://kotlinlang.org/)（開發語言）
- [Jetpack Compose](https://developer.android.com/jetpack/compose)（宣告式 UI 框架）
- [Material 3 Expressive](https://m3.material.io/)（UI 設計規範）
- [Koin](https://insert-koin.io/)（依賴注入）
- [Room](https://developer.android.com/training/data-storage/room)（本地資料庫）
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore?hl=zh-cn#preferences-datastore)（鍵值偏好存儲）
- [OkHttp](https://square.github.io/okhttp/)（網路客戶端）
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)（JSON 序列化）
- [Coil](https://coil-kt.github.io/coil/)（圖片載入）

### 構建步驟

1. 複製本倉庫到本地：
   ```bash
   git clone https://github.com/LightEastDS/rikkahub.git
   ```
2. 在 `app` 目錄下新增你的 `google-services.json` 檔案。
3. 編譯 Release 版本：
   ```bash
   ./gradlew :app:assembleRelease
   ```

---

## 📄 開源許可證

本專案基於 [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0) 協議開源。
