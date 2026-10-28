<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHub (LightEastDS Fork)</h1>

A native Android LLM chat client that supports flexibly switching between different AI providers 🤖💬

[English](README.md) | [繁體中文](README_ZH_TW.md) | [简体中文](README_ZH_CN.md)

</div>

<div align="center">
  <img src="docs/img/poster.png" alt="RikkaHub" width="800" />
</div>

## 🌟 Customizations in this Fork

This is a customized and de-commercialized fork of RikkaHub maintained by LightEastDS, featuring the following enhancements:

1. **Message Floor Indicator (Turn Numbers)**
   - Chronological turn numbers (`1`, `2`, `3`...) are displayed directly in each message's nerd stats line to help quickly track dialogue turns.
2. **Hide Floor Context (`/hide`)**
   - Precise control over which messages are included in prompt requests via the `/hide` chat command:
     - `/hide 5`: Hides the 5th message floor.
     - `/hide 3-18`: Hides all messages between floor 3 and 18 inclusive.
     - `/unhide 5` or `/unhide 3-18`: Unhides the corresponding floors.
   - **Hidden messages are excluded from AI model context requests**, saving prompt tokens and eliminating distractions from irrelevant history.
   - Messages also feature a quick toggle in the action bottom sheet.
3. **Message Word Count Display**
   - Displays real-time word count for each message in the stats bar, featuring smart multilingual counting (Chinese: characters & punctuation; English: words & punctuation; Japanese: kanji, kana & punctuation; spaces and newlines excluded). Can be toggled in **Settings -> Display Settings**.
4. **Pure & De-commercialized**
   - Completely stripped of built-in locked third-party proxy sponsors, referral links, and affiliate parameters (AFF).
   - Removed the 100-launch donation alert popup and the donation entry in Settings.
   - Removed hidden request tracking headers and hardcoded promotional badges.
5. **Production Release Signing & Fast Domestic Builds**
   - Includes official release signing configurations to ensure packaged APKs can be directly installed on Android OEM skins (MIUI/HyperOS, ColorOS, etc.) without "missing certificate" blocks.
   - Integrated domestic Maven repository mirrors for high-speed builds.

---

## 🚀 Download & Installation

Download the latest version from our GitHub Releases page:

🔗 **[GitHub Releases](https://github.com/LightEastDS/rikkahub/releases)**

Recommended: `app-arm64-v8a-release.apk` (for all modern Android devices).

---

## ✨ Features

- 🎨 Material 3 Expressive design with predictive back gesture and 🌙 Dark Mode
- 📦 Workspace: a PRoot-based Linux agent environment
- 🎬 Media generation (image and video generation)
- 🖥️ Web access: seamless multi-platform access via desktop browsers
- 🛠️ MCP and Skills extensions support
- 🔄 Multiple AI Provider Support: custom API / URL / models (OpenAI, Google Gemini, Anthropic compatible)
- 🖼️ Multimodal input support (Images, Text Documentation, PDF, Docx, Audio, Video)
- 📝 Markdown rendering with code highlighting, LaTeX math formulas, tables, and Mermaid charts
- 🔍 Web search capabilities (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, etc.)
- 🧩 Prompt variables (model name, current time, etc.)
- 🤳 QR code export and import for provider settings
- 🤖 Agent customization
- 🧠 File-based long-term memory
- 📝 AI message translation
- 🌐 Custom HTTP request headers and request bodies

---

## 🛠️ Build & Development

This project is developed with [Android Studio](https://developer.android.com/studio).

### Tech Stack

- [Kotlin](https://kotlinlang.org/) (Development language)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI framework)
- [Material 3 Expressive](https://m3.material.io/) (Design system)
- [Koin](https://insert-koin.io/) (Dependency Injection)
- [Room](https://developer.android.com/training/data-storage/room) (Database)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preferences storage)
- [OkHttp](https://square.github.io/okhttp/) (HTTP client)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON serialization)
- [Coil](https://coil-kt.github.io/coil/) (Image loading)

### Building the Project

1. Clone this repository:
   ```bash
   git clone https://github.com/LightEastDS/rikkahub.git
   ```
2. Place your `google-services.json` file inside the `app/` folder.
3. Build the Release APK:
   ```bash
   ./gradlew :app:assembleRelease
   ```

---

## 📄 License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0).
