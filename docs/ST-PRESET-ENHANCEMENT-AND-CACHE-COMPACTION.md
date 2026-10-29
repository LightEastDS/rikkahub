# SillyTavern 预设全兼容与 DeepSeek 极速缓存优化实施规格书
## （含上下文检查点压缩与 /hide 命令深度联动）

> **目标受众**：负责在 RikkaHub (LightEastDS 分支) 中落地该架构的 AI 辅助编码智能体 / 开发者。  
> **基线环境**：RikkaHub 3.1.1.x / Kotlin 2.x / Jetpack Compose / Room 数据库版本 35 / Koin DI。  
> **核心目标**：
> 1. **预设变量 100% 兼容**：彻底解决 ST 预设在条目中通过 `setvar` 声明变量导致的前置引用落空、语法别名识别失败、条件宏缺失等问题。
> 2. **DeepSeek 极致前缀缓存（90%+ 命中率）**：重构组装器时序为「三区动静隔离」，彻底消除世界书与时间宏的前缀投毒。
> 3. **检查点式上下文压缩（Compaction Checkpoint）**：支持将前 100 轮（300K）对话浓缩为 20K 结构化大纲并替换上下文，与 `/hide` 命令深度互补。

---

## 目录
- [一、 模块架构总览](#一-模块架构总览)
- [二、 模块 A：ST 预设变量与高级宏引擎增强](#二-模块-ast-预设变量与高级宏引擎增强)
- [三、 模块 B：DeepSeek 缓存友好三区组装器重构](#三-模块-bdeepseek-缓存友好三区组装器重构)
- [四、 模块 C：检查点上下文压缩系统（与 /hide 联动）](#四-模块-c检查点上下文压缩系统与-hide-联动)
- [五、 数据库演进规范（Room 35 → 36）](#五-数据库演进规范room-35--36)
- [六、 代码改动文件清单](#六-代码改动文件清单)
- [七、 验收标准与测试用例](#七-验收标准与测试用例)

---

## 一、 模块架构总览

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ChatRequestFactory                                │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
     ┌─────────────────────────────────┴─────────────────────────────────┐
     ▼                                                                   ▼
【模块 C：检查点上下文裁剪】                                          【模块 A：宏与变量预扫描】
 读取 conversation_checkpoint 表                                    StMacroResolver.preScan()
 ├─ 存在检查点：[20K摘要] + [边界后未隐藏消息]                         ├─ 扫描全部启用条目 setvar 默认值
 └─ 无检查点：全量未隐藏历史 (filterNot { isHidden })                  ├─ 拓扑展开嵌套依赖 (最多 6 轮)
                                                                      └─ 解析 Handlebars {{#if}} 条件
                                       │                                 │
                                       └────────────────┬────────────────┘
                                                        │
                                                        ▼
                                    【模块 B：三区时序组装器】
                                      StPromptAssembler.assemble()
     ┌──────────────────────────────────────────────────┴──────────────────────────────────────────────────┐
     ▼                                                  ▼                                                  ▼
【Zone A：静态前缀区】                             【Zone B：稳定历史区】                             【Zone C：动态后缀区】
 · 基础 System 提示词                               · 检查点结构化摘要 (20K)                          · 关键词动态触发的世界书
 · 角色卡 (设定/性格/背景)                          · 历史只增追加消息 (只增流)                       · 动态环境感知 (时间/日期)
 · 常驻世界书 (constant=true)                       · 严格保持字节稳定                                · In-Chat 输出协议/状态栏
 ────────────────────────────────                   ────────────────────────────────                   ────────────────────────────────
 🔒 100% 永久命中 DeepSeek 前缀                      ⚡ 持续追加，整段历史 100% 命中缓存               🔄 轮次级动态变动锁定在尾部
```

---

## 二、 模块 A：ST 预设变量与高级宏引擎增强

### 2.1 痛点诊断
1. **前置引用死锁**：ST 预设通常将配置集中在第 7 条（如《预设设置》），但在第 3 条的主提示中已提前引用 `{{getvar::AI_role}}`。单遍流式处理会导致前置引用读出空字符串。
2. **社区语法多样性**：`{{var::key}}` 是 ST 官方高频别名；部分作者混用单冒号 `{{setvar:k:v}}` 或赋值符 `{{setvar::k=v}}`。
3. **缺少条件宏**：社区大量使用 `{{#if {{getvar::flag}}}}...{{/if}}` 进行逻辑分支开关。

### 2.2 实施规范：`data/stpreset/StMacroResolver.kt`

#### 2.2.1 增强正则表达式
```kotlin
// 别名与冒号容错正则
private val getVarRegex = Regex("""^\s*(getvar|var)(?:::|:)\s*([^}]+)$""", RegexOption.IGNORE_CASE)
private val setVarRegex = Regex("""^\s*setvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
private val setGlobalVarRegex = Regex("""^\s*setglobalvar(?:::|:)([^:=]+)(?:::|:|=)([\s\S]*)$""", RegexOption.IGNORE_CASE)
private val ifBlockRegex = Regex("""\{\{#if\s+([^}]+)\}\}([\s\S]*?)\{\{/if\}\}""", RegexOption.IGNORE_CASE)
private val unlessBlockRegex = Regex("""\{\{#unless\s+([^}]+)\}\}([\s\S]*?)\{\{/unless\}\}""", RegexOption.IGNORE_CASE)
```

#### 2.2.2 双遍预扫描算法（Two-Pass Pre-scan）
在组装正文前，必须先调用 `preScanVariables`：

```kotlin
/**
 * Pass 1: 扫描所有启用的条目，提前提取 setvar 默认值并展开拓扑依赖
 */
fun preScanVariables(
    prompts: List<StPrompt>,
    persistedVariables: Map<String, String>,
    context: StMacroContext
): MutableMap<String, String> {
    val vars = persistedVariables.toMutableMap()

    // 1. 抽取所有条目里的静态声明
    prompts.forEach { prompt ->
        if (prompt.content.contains("setvar", ignoreCase = true)) {
            setVarRegex.findAll(prompt.content).forEach { match ->
                val key = match.groupValues[1].trim()
                val rawVal = match.groupValues[2].trim()
                // 会话已存的用户自定义变量优先；不存在才采用预设默认值
                if (!vars.containsKey(key)) {
                    vars[key] = rawVal
                }
            }
        }
    }

    // 2. 拓扑迭代展开（处理 {{setvar::A::{{getvar::B}}}} 这种嵌套依赖，最多 6 轮防死循环）
    repeat(6) {
        var changed = false
        vars.keys.toList().forEach { k ->
            val oldVal = vars[k] ?: ""
            val newVal = render(oldVal, context)
            if (newVal != oldVal) {
                vars[k] = newVal
                changed = true
            }
        }
        if (!changed) return@repeat
    }
    return vars
}
```

#### 2.2.3 条件块宏求值逻辑
在 `render()` 的字符串处理开头先执行条件裁剪：
```kotlin
private fun processConditionals(text: String, context: StMacroContext): String {
    var output = text
    if (output.contains("{{#if", ignoreCase = true)) {
        output = ifBlockRegex.replace(output) { match ->
            val conditionExpr = match.groupValues[1].trim()
            val innerBody = match.groupValues[2]
            val eval = render(conditionExpr, context).trim()
            if (eval.isNotEmpty() && eval != "0" && !eval.equals("false", ignoreCase = true)) {
                innerBody
            } else {
                ""
            }
        }
    }
    if (output.contains("{{#unless", ignoreCase = true)) {
        output = unlessBlockRegex.replace(output) { match ->
            val conditionExpr = match.groupValues[1].trim()
            val innerBody = match.groupValues[2]
            val eval = render(conditionExpr, context).trim()
            if (eval.isEmpty() || eval == "0" || eval.equals("false", ignoreCase = true)) {
                innerBody
            } else {
                ""
            }
        }
    }
    return output
}
```

### 2.3 UI 交互规范：预设变量可视化面板
在 `ui/pages/extensions/PromptPage.kt`（预设详情）及 `AssistantExtensionsPage.kt` 中：
1. 解析预设时提取变量字典，渲染名为 **「预设参数配置 (Variables)」** 的卡片；
2. 自动生成 Key-Value 列表（例如 `AI_role`、`Word_count`、`Language_settings`）；
3. 用户在输入框修改后，直接写入 `st_variables` 表（当前会话 ID 作用域），组装时自动优先使用。

---

## 三、 模块 B：DeepSeek 缓存友好三区组装器重构

### 3.1 动静隔离时序准则
DeepSeek 采用 64-token 块级前缀自动缓存。**前缀只要有一处字节变动，后文全部穿透**。

在 `StPromptAssembler.kt` 中实施以下重构：

```kotlin
fun assemble(req: StAssemblyRequest): StAssemblyResult {
    // =========================================================================
    // 【Zone A: 静态前缀区 (Static Prefix)】-> 绝对冻结，保证第 0 个 token 开始全中
    // =========================================================================
    val zoneA = mutableListOf<UIMessage>()
    
    // 1. 系统基础设定与格式规约
    zoneA += resolveMainPrompt(req.preset, req.character)
    // 2. 结构化角色卡（描述、性格、背景场景）
    zoneA += resolveCharacterDetails(req.character)
    // 3. 常驻世界书（constant == true）
    zoneA += req.worldInfo.constantEntries.map { it.toSystemMessage() }
    
    // ⚠️ 规则：严禁在 Zone A 包含任何时间/日期宏、随机数宏

    // =========================================================================
    // 【Zone B: 稳定历史区 (Append-only Stream)】-> 严格单调递增流
    // =========================================================================
    val zoneB = mutableListOf<UIMessage>()
    if (req.checkpoint != null) {
        // 压缩检查点：以一条格式化系统消息承载 20K 结构化大纲
        zoneB += UIMessage.system(
            """
            <conversation_summary>
            [以下是历史会话（第 1 轮至第 ${req.checkpoint.boundaryFloor} 轮）的核心大纲与背景记忆，请将其视为既定事实并在此基础上继续对话]
            ${req.checkpoint.summaryContent}
            </conversation_summary>
            """.trimIndent()
        )
        // 仅追加检查点之后的未隐藏活消息
        zoneB += req.liveMessages.filterAfter(req.checkpoint.boundaryNodeId).filterNot { it.isHidden }
    } else {
        zoneB += req.liveMessages.filterNot { it.isHidden }
    }

    // =========================================================================
    // 【Zone C: 动态后缀区 (Turn-Tail Dynamic)】-> 所有易变内容沉底
    // =========================================================================
    val zoneC = mutableListOf<UIMessage>()
    
    // 1. 关键词动态触发的世界书（动静分离：离开 Zone A，移至此处）
    zoneC += req.worldInfo.dynamicTriggeredEntries.map { it.toSystemMessage() }
    
    // 2. 动态环境感知（时间宏下沉）
    if (req.presetRequiresEnvironmentTime) {
        zoneC += UIMessage.system("[Environment Context: Time=${req.formattedNow}]")
    }
    
    // 3. In-Chat 动态提示（输出协议引导、手记状态栏、思维链规范）
    zoneC += resolveInChatAndProtocols(req.preset, req.macroContext)
    
    // 4. Assistant Prefill
    val finalMessages = squashIfConfigured(zoneA + zoneB + zoneC)
    return StAssemblyResult(finalMessages, generateReport())
}
```

---

## 四、 模块 C：检查点上下文压缩系统（与 /hide 联动）

### 4.1 方案定位
* **为何不用纯丢弃式截断？**：现代大模型具备较强理解力，单纯踢掉旧历史会导致剧情失忆。
* **压缩的缓存经济性**：将 300K 压缩成 20K 替换进历史头部，**仅在压缩当前轮次付一次 20K 的写入成本**，随后 100 轮享受基底仅 20K 的持续 90%+ 缓存命中，且极大降低 DeepSeek 服务端驱逐 KV Cache 的概率。

### 4.2 数据模型：`ConversationCheckpointEntity.kt`
```kotlin
package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(
    tableName = "conversation_checkpoint",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class ConversationCheckpointEntity(
    @PrimaryKey val id: String = Uuid.random().toString(),
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "boundary_node_id") val boundaryNodeId: Long,   // 压缩覆盖到的最后一条消息节点 ID
    @ColumnInfo(name = "boundary_floor") val boundaryFloor: Int,       // 对应楼层数（如第 100 轮）
    @ColumnInfo(name = "summary_content") val summaryContent: String,  // 压缩生成的结构化大纲正文（约 10K~20K tokens）
    @ColumnInfo(name = "original_tokens") val originalTokens: Int,     // 压缩前体积（如 300,000）
    @ColumnInfo(name = "compressed_tokens") val compressedTokens: Int, // 压缩后体积（如 18,500）
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

### 4.3 压缩 Prompt 模板设计
压缩任务调用后台轻量模型（如 `deepseek-chat` 非思考模式），Prompt 模板严格约束结构化要素：

```text
你是一个专业的小说与角色扮演上下文总结引擎。
请将以下历史对话记录进行高度凝练的结构化总结，目标是将冗长的对话压缩为高密度、保留全部核心事实的大纲。

输出必须严格按照以下四个结构化区块输出，不要任何多余前言：
<timeline_events>
[核心剧情事件推进线：按时间顺序记载发生了什么、谁做出了什么关键动作]
</timeline_events>

<character_relationships>
[角色关系与状态变动：态度变化、羁绊建立、当前角色的物理状态与位置]
</character_relationships>

<agreements_and_promises>
[关键承诺与契约：彼此达成的协定、获得的物品/线索、既成事实]
</agreements_and_promises>

<open_threads>
[未完待续与伏笔：正在进行的目标、尚未解决的危机、悬而未决的问题]
</open_threads>
```

### 4.4 与 `/hide` 命令的深度联动交互
1. **压缩前降噪（Pre-compaction Pruning）**：
   - 用户在长跑中通过 `/hide 12` 或 `/hide 35-40` 隐藏的楼层（`isHidden == true`），在输入给总结模型时**自动被过滤剔除**，避免将垃圾报错与跑题闲聊总结进长期记忆。
2. **高光时刻穿透（Milestone Whitelist）**：
   - 即使处在压缩区间内，若某条关键消息用户未执行隐藏且被标记为收藏/固定，允许其作为完整原文透传在摘要之后。
3. **UI 非破坏性呈现**：
   - 本地 `message_node` 表完整保留全部原文；
   - `ChatList.kt` 中将 1 至 `boundaryFloor` 轮消息折叠为一个交互卡片：
     *“🗜️ 早期对话已压缩为背景记忆 (300K → 20K，已折叠 100 轮) [点击展开回看原文]”*。

---

## 五、 数据库演进规范（Room 35 → 36）

### 5.1 迁移脚本：`Migration_35_36.kt`
```kotlin
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversation_checkpoint` (
                `id` TEXT NOT NULL,
                `conversation_id` TEXT NOT NULL,
                `boundary_node_id` INTEGER NOT NULL,
                `boundary_floor` INTEGER NOT NULL,
                `summary_content` TEXT NOT NULL,
                `original_tokens` INTEGER NOT NULL,
                `compressed_tokens` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`conversation_id`) REFERENCES `conversation`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_conversation_checkpoint_conversation_id` ON `conversation_checkpoint` (`conversation_id`)"
        )
    }
}
```

### 5.2 `AppDatabase.kt` 注册
在 `AppDatabase` 的 `@Database(version = 36, ...)` 中追加 `ConversationCheckpointEntity::class`，并在 `AppDatabaseFactory` 中挂载 `MIGRATION_35_36`。

---

## 六、 代码改动文件清单

| 序号 | 文件相对路径 | 变更性质 | 核心职责 |
| :--- | :--- | :--- | :--- |
| 1 | `data/stpreset/StMacroResolver.kt` | 修改 | 实现双遍变量预扫描、`var::` 别名容错、Handlebars `{{#if}}` / `{{#unless}}` 条件求值 |
| 2 | `data/stpreset/StPromptAssembler.kt` | 修改 | 重构为 Zone A / B / C 三区组装时序，动态世界书与时间宏下沉 |
| 3 | `data/db/entity/ConversationCheckpointEntity.kt` | **新增** | 检查点 Room 实体定义 |
| 4 | `data/db/dao/ConversationCheckpointDao.kt` | **新增** | 检查点持久化与查询 DAO（按 `conversationId` 获取最新检查点） |
| 5 | `data/db/migrations/Migration_35_36.kt` | **新增** | Room 数据库升级迁移脚本 |
| 6 | `data/db/AppDatabase.kt` | 修改 | 数据库版本升至 36，注册新实体与迁移 |
| 7 | `data/repository/ConversationCheckpointRepository.kt` | **新增** | 检查点存取、触发总结生成与生命周期管理 |
| 8 | `data/ai/ChatRequestFactory.kt` | 修改 | 在装配上下文时读取最新检查点，替代历史区间，联动 `isHidden` 过滤 |
| 9 | `service/ChatService.kt` | 修改 | 拦截 `/compact` 指令，执行后台上下文浓缩流水线 |
| 10 | `ui/pages/extensions/PromptPage.kt` | 修改 | 预设详情页增加「预设参数配置 (Variables)」折叠卡片，支持可视化修改变量 |
| 11 | `ui/pages/chat/ChatList.kt` | 修改 | 渲染「已压缩 N 轮历史」的展开/折叠交互条 |

---

## 七、 验收标准与测试用例

### 7.1 单元测试（Unit Tests）
1. **`StMacroResolverTest` 增项**：
   - 验证跨条目逆向变量依赖：第 2 条引用 `{{getvar::role}}`，第 8 条定义 `{{setvar::role::Hero}}`，断言 Pass 1 扫描后第 2 条成功解析为 `"Hero"`；
   - 验证别名兼容：`{{var::mood}}` 与 `{{getvar::mood}}` 产物一致；
   - 验证条件宏：`{{#if {{getvar::enable}}}}YES{{/if}}`，在 `enable=1` 时输出 `"YES"`，在 `enable=0` 时输出 `""`。
2. **`StPromptAssemblerTest` 增项**：
   - 验证三区结构：断言静态 System 与角色卡固定位于 Index 0~3，关键词世界书与时间宏严格位于消息列表末尾。

### 7.2 真机与端到端验收（DeepSeek E2E）
1. **预设表现**：导入包含大量 `setvar` 的第三方成熟预设（如 Bragi），进入 Inspect 页面查看，所有变量引用处必须 100% 填充真实数据，无空标签、无未识别警告。
2. **缓存命中指标**：
   - 进行连续 10 轮对话，查看状态行用量：自第 2 轮起，API 回传的 `cached_tokens` 占比稳定处于 **85% ~ 95%**；
   - 触发新世界书词条时，验证前缀 `cached_tokens` 不发生断崖式下跌。
3. **压缩与 `/hide` 联动验证**：
   - 会话进行至 30 轮，使用 `/hide 5-10` 隐藏无用轮次，随后执行 `/compact`；
   - 验证生成的摘要中未包含 5~10 轮的废话；
   - 验证聊天列表前 30 轮折叠收起，随后的第 31、32 轮继续正常流式生成，且第 32 轮再次达到 90%+ 缓存命中。
