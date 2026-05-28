# Demo 里程碑文档：Card HTTP v0.1

| 字段 | 值 |
|------|-----|
| **里程碑** | Card HTTP v0.1 |
| **日期** | 2026-05-28 |
| **范围** | `demo/android-a2ui-text-demo/` |
| **状态** | 已完成，待评审 |

---

## 1. 目标与 v0.1 里程碑结论

### 目标

A2UI Text Demo 是 AGenUI SDK 的最小 Android Demo App，验证"用户输入文本 → 生成 UI 数据 → AGenUI 原生渲染 UI"闭环。核心问题不是"能不能渲染 A2UI"，而是**AI 的输出应该以什么形态进入 Android 渲染管线**。

### v0.1 里程碑结论

**LLM 不应直接生成 A2UI 协议 JSON，而应输出结构化业务数据（CardData），由 Android 端本地确定性生成 A2UI。**

这一结论经过以下验证路径得出：

1. **Mock / Fixture / LLM 模式**验证了 A2UI SDK 的基础渲染能力。
2. **LLM 模式暴露了归一化黑洞**：真实模型输出几乎不可能精确匹配 A2UI 协议结构，`A2uiMessageNormalizer` 越来越复杂，但仍无法覆盖所有变体（778d042..696445a 修复经验）。
3. **Card Fixture / Card JSON / Card HTTP 模式**验证了"AI 输出 CardData → 本地模板 → A2UI"替代路径的可行性。

v0.1 的结论是：Card 链路是推荐正式链路，LLM 直接生成 A2UI 仅作为兼容遗留方案保留。

---

## 2. 当前支持的 Provider

| Provider | 说明 | 数据来源 | 是否经过 Normalizer | 用途 |
|----------|------|----------|---------------------|------|
| **Mock** | 关键词匹配生成内置 UI | 内存 | 是 | 快速验证 SDK 渲染 |
| **Fixture** | 加载本地 A2UI JSON 样例 | `assets/fixtures/` | 是 | 验证已知 A2UI 结构 |
| **LLM** | HTTP POST 调用可配置生成接口 | 远程 LLM 服务 | 是 | 接入真实大模型（遗留路径） |
| **Card Fixture** | 加载本地 CardData，本地确定性生成 A2UI | `assets/card_fixtures/` | 否 | 验证 Card 链路 |
| **Card JSON** | 用户粘贴 CardData JSON，直接走 Card 渲染链路 | 用户输入 | 否 | 模拟 AI 组直接传入 CardData |
| **Card HTTP** | HTTP POST 调用 CardData 接口 | 远程 CardData 服务 | 否 | 模拟未来 AI 组服务 |

Card Fixture / Card JSON / Card HTTP 三种 Provider 走同一条渲染链路，区别仅在数据来源不同。

---

## 3. 推荐正式链路

```
用户输入文本
  → AI 组服务（意图识别 + 数据获取 + CardData 填充）
  → CardData JSON（结构化业务数据）
  → CardContractValidator（校验卡片协议）
  → CardTemplateRenderer（按 cardType 委托具体模板）
    → card/template/*Template（本地确定性生成 A2UI）
  → A2uiJsonValidator（校验 A2UI 输出）
  → SurfaceManager → AGenUI 原生渲染
```

核心设计原则：

- **AI 输出业务语义和结构化数据，Android 输出最终 UI。**
- CardData 不包含 A2UI 协议片段、action、functionCall 或富文本。
- 每种 cardType 有独立的校验规则和渲染模板，确定性生成 A2UI。
- 校验失败触发 fallback 卡片，不允许出现空白 UI。

---

## 4. 为什么不推荐 LLM 直接生成 A2UI

### 4.1 归一化黑洞

真实 LLM 输出几乎不可能精确匹配 A2UI 协议结构。常见问题包括：

- `type` vs `component` 字段名差异
- `content` vs `text` 字段名差异
- 内联嵌套 children（A2UI 要求扁平组件数组 + child 引用）
- 缺少 `version` / `surfaceId` 等必要字段
- 裸数组 / Markdown fenced JSON / 连续 JSON object 等非标准输出

`A2uiMessageNormalizer` 的职责是宽容归一化这些变体，但随着模型输出多样化，Normalizer 变得越来越复杂且永远无法覆盖所有变体。778d042..696445a 修复经验表明，**归一化和校验的边界容易模糊，导致本应报 ERROR 的情况被宽容处理，最终 SDK 静默失败。**

### 4.2 SDK 静默失败

C++ `ProtocolStreamExtractor::startStreamingComponents()` 缺少 `version` 字段时返回 false 无回调；`SurfaceCoordinator::updateComponents()` 在 surface 不存在时直接丢弃消息。SDK 层面的错误可观测性需要修改 C++ core，当前只能在 Java 层做好归一化兜底。

这意味着 LLM 直接生成 A2UI 时，格式稍有偏差就可能出现"JSON 返回了但不渲染"的问题，且没有任何可见错误提示。

### 4.3 模板确定性

Card 链路中，模板输出始终通过 `A2uiJsonValidator` 校验，输出格式完全可控。`SportsScoreListTemplate.render()` 和 `WeatherSummaryTemplate.render()` 的输出是确定性的——相同的 CardData 输入总是产生相同的 A2UI 输出。

LLM 直接生成 A2UI 时，相同意图可能产生不同的组件结构、不同的 ID 命名、不同的嵌套方式，无法保证一致性。

### 4.4 UI 更新成本

LLM 直接生成 A2UI 时，修改 UI 布局需要调整模型提示词，周期长、效果不可预测。

Card 链路中，修改 UI 布局只需修改 Android 端模板代码，发版即生效，无需调整模型提示词。

---

## 5. Card HTTP 请求/响应协议

### 5.1 请求格式

Android App 通过 HTTP POST 调用 CardData 接口：

```json
{
  "userInput": "北京天气",
  "version": "0.1",
  "supportedCardTypes": ["sports_score_list", "weather_summary"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `userInput` | string | 用户输入文本 |
| `version` | string | 协议版本 |
| `supportedCardTypes` | string[] | 客户端支持的 cardType 列表，服务端应仅返回此列表内的类型 |

### 5.2 成功响应

```json
{
  "cardData": {
    "requestId": "weather-123",
    "cardType": "weather_summary",
    "title": "今日天气",
    "location": "北京",
    "updatedAt": "14:30 更新",
    "current": {
      "condition": "晴转多云",
      "temperature": 26,
      "high": 30,
      "low": 18,
      "airQuality": "良",
      "humidity": "65%",
      "wind": "东南风 3级"
    },
    "tips": ["紫外线较强，注意防晒", "早晚温差较大，注意添衣"]
  },
  "debug": {
    "provider": "mock-card-server",
    "matchedIntent": "weather_query",
    "cardType": "weather_summary"
  }
}
```

`debug` 字段设计意图：

- `provider`：标识数据来源，便于排查是哪个服务返回的数据
- `matchedIntent`：服务端识别的意图，与客户端可能不同，用于调试意图识别准确性
- `cardType`：实际返回的卡片类型，便于确认卡片类型匹配

### 5.3 Unknown Intent 返回 400

当服务端无法识别用户意图时，返回 HTTP 400：

```json
{
  "error": "Unknown intent for input '...'. Try: '北京天气' (weather) or 'NBA 比分' (sports).",
  "debug": {
    "provider": "mock-card-server",
    "matchedIntent": "unknown",
    "supportedIntents": ["weather_query", "sports_query"]
  }
}
```

**设计原则：unknown intent 返回 400 而非默认卡片。** 原因：

1. 默认一个卡片类型会让客户端无法区分"AI 确实返回了这个类型"和"AI 不知道该返回什么"，掩盖意图识别问题。
2. 客户端可以根据 400 状态码做降级处理（显示"暂不支持"提示），而非展示一张可能不相关的卡片。
3. `debug.matchedIntent = "unknown"` 让排查路径清晰。

### 5.4 CardType 不支持时返回 400

当服务端匹配的 cardType 不在客户端 `supportedCardTypes` 列表中时，返回 HTTP 400：

```json
{
  "error": "Card type 'xxx' not in client supported types: [...]",
  "debug": {
    "provider": "mock-card-server",
    "matchedIntent": "xxx",
    "cardType": "xxx"
  }
}
```

### 5.5 Android 端解析逻辑

`CardHttpProvider.parseCardData()` 处理三种响应格式：

1. **标准格式**：`{cardData: {...}, debug: {...}}` → 提取 cardData
2. **直接 CardData**：响应本身就是 CardData JSON（含 `cardType` / `requestId` 但无 `cardData` 包装）→ 直接使用
3. **无法解析**：抛出 `CardHttpException`，携带原始响应文本

---

## 6. 真实业务卡片的职责边界

### 6.1 sports_score_list

| 方面 | 职责 |
|------|------|
| **AI 组** | 意图识别（"NBA 比分"）、调用体育数据 API、填充结构化数据 |
| **Android 组** | 校验 CardData 协议、确定性生成 A2UI、渲染原生 UI |
| **数据来源** | 体育数据 API（比分、状态、时间），禁止模型编造 |
| **交互能力** | 当前无（模板控制，CardData 不含 action/functionCall） |

关键设计决策：

- `items: []` 是合法数据，渲染空状态（"今日暂无赛况"），不触发 fallback
- `homeTeam` / `awayTeam` 必须为 object 且含 `name` 字段（不是扁平字符串）
- `status` 支持 `final` / `live` / `scheduled`，未知值 warning 但不阻断
- `final` / `live` 缺少 score 产生 warning 但仍渲染（显示 `"--"`）
- `scheduled` 缺少 score 不产生 warning（正常状态，显示 `"-"`）

协议文档：`docs/card-contracts/sports_score_list_v0.1.md`

### 6.2 weather_summary

| 方面 | 职责 |
|------|------|
| **AI 组** | 意图识别（"今天天气"）、调用天气数据 API、填充结构化数据 |
| **Android 组** | 校验 CardData 协议、确定性生成 A2UI、渲染原生 UI |

**空状态语义**：`items: []`（空赛况列表）是合法状态，无 warning；`current: {}`（无天气数据）也是合法的但会产生 warning（缺少 condition/temperature/location），因为天气数据本质需要内容才有用。

**score vs temperature 的不同设计**：

- `sports_score_list` 的 `score` 在 `final`/`live` 时缺失会产生 warning（已结束的比赛没有比分是异常）
- `weather_summary` 的 `temperature` 缺失只产生 warning 而不区分场景（温度缺失总是问题，但不是硬性阻断）

### 6.2 两种卡片的共同约束

- CardData 不含 A2UI 协议片段、action、functionCall 或富文本
- 温度、比分等业务数据必须来自真实 API，禁止模型编造
- 校验失败触发 fallback 卡片，不允许空白 UI
- 模板输出不经过 `A2uiMessageNormalizer`

---

## 7. 常见失败场景

### 7.1 CardData 校验失败

**现象**：Debug 面板显示 `FAIL` 徽章，错误信息以卡片类型为前缀（如 `sports_score_list: missing 'items'`）。

**典型原因**：

- 缺少必填字段（`requestId`、`cardType`、`title`、`items`/`current`）
- `items[i]` 不是 JSONObject
- `homeTeam`/`awayTeam` 不是嵌套对象（如写成 `homeTeam: "湖人"` 而非 `homeTeam: {name: "湖人"}`）
- `tips` 不是 JSONArray

**结果**：自动降级为 error_fallback 卡片，不会出现空白 UI。

**排查**：查看 Debug 面板的"错误信息"区域，错误信息格式为 `{cardType}: {具体问题}`。

### 7.2 A2UI 校验失败

**现象**：Debug 面板显示 `FAIL` 徽章，错误信息来自 `A2uiJsonValidator`。

**典型原因**：

- 组件 `component` 字段值不在 SDK 内置的 22 种组件内
- 组件 id 重复
- children/child 引用了不存在的组件 id
- surfaceId 不一致（createSurface 和 updateComponents 的 surfaceId 不同）

**结果**：A2UI JSON 不发送到 SDK，Debug 面板显示错误详情。

**排查**：Card 链路的模板输出已保证通过 `A2uiJsonValidator`，此场景通常只在 LLM 直接生成 A2UI 时出现。

### 7.3 HTTP 失败

**现象**：Toast 提示错误，Debug 面板显示"原始输出"区域有内容但渲染状态为 error。

**典型原因**：

- 服务端不可达（URL 错误、网络不通、服务未启动）
- 服务端返回 HTTP 400（unknown intent 或不支持的 cardType）
- 服务端返回非 JSON 响应
- 网络超时

**排查**：

- 检查 Debug 面板的"原始输出"（`lastRawLlmOutput`），Card HTTP 模式下会显示服务端原始响应
- HTTP 400 时响应体含 `error` 字段和 `debug.matchedIntent`
- 使用 `tools/card-data-server/test_card_server.py` 独立测试服务端

### 7.4 Provider 切换残留

**现象**：切换 Provider 后，前一个 Provider 的输入文本、生成的 UI 和 Debug 状态仍然保留。

**原因**：`switchProvider()` 仅隐藏条件控件和取消活跃流，未清空输入框文本、未销毁前一个 Surface、未重置 Debug 面板状态。修复方案：`switchProvider()` 切换时调用 `destroyPreviousSurface()` 销毁旧 Surface、`etInput.setText("")` 清空输入、重置所有 Debug 状态字段（lastUserInput、lastGeneratedMessages、lastRawLlmOutput、lastError、renderStatus、tvValidationBadge），确保切换后界面完全干净。

**排查**：切换 Provider 后检查界面是否仍有前一个 Provider 的输入文本、渲染区域是否有残留 UI、Debug 面板是否显示旧数据。

### 7.5 PASS 但 UI 空白

**现象**：Debug 面板显示 `PASS` 徽章，SDK 渲染状态为 `rendered`，但实际界面空白。

**典型原因**：

- 组件树结构问题（如根组件被其他组件引用为 child，导致无根组件）
- 组件文本为空字符串
- 组件尺寸为 0（如 Column 无 padding、无子组件时高度为 0）
- C++ SDK 静默丢弃消息（如 surfaceId 对应的 Surface 不存在）

**排查**：

- 查看 Debug 面板的"原始 A2UI JSON"，检查组件树结构
- 确认 `createSurface.surfaceId` 与 `updateComponents.surfaceId` 一致
- 确认存在且仅有一个未被引用的根组件
- 通过 `adb logcat` 查看是否有 C++ 层面的静默失败日志

---

## 8. Fallback 和 Debug 面板排查方式

### 8.1 Fallback 机制

当 CardData 校验失败（`CardContractValidator` 报 ERROR）时，`CardTemplateRenderer` 自动生成 error_fallback 卡片：

```
CardRenderResult(valid=false, messages=[fallback A2UI], errors=[...])
```

Fallback 卡片显示错误标题和错误信息列表，确保不会出现空白 UI。

**触发 fallback 的条件**（ERROR，非 WARNING）：

- 缺少必填字段（`requestId`、`cardType`、`title`）
- `cardType` 不在已知枚举中
- 类型特定的校验错误（如 `items` 不是 JSONArray、`current` 不是 JSONObject）

**不触发 fallback 的条件**（WARNING，正常渲染）：

- 缺少可选字段
- 未知 status 值
- final/live 缺少 score
- tips 数量超过建议上限

### 8.2 Debug 面板

App 界面中部的 Debug 面板提供以下排查信息：

| 区域 | 内容 | 用途 |
|------|------|------|
| 当前 Provider | 类型 + 条件信息（如 Card HTTP URL） | 确认当前模式 |
| 用户输入 | 最近一次输入文本 | 确认输入 |
| 原始 A2UI JSON | createSurface / updateComponents / updateDataModel | 检查 A2UI 结构 |
| SDK 渲染状态 | idle / streaming / rendered / error | 确认渲染是否完成 |
| 验证状态徽章 | IDLE / WAIT / PASS / FAIL / ERROR | 快速判断校验结果 |
| 错误信息 | 验证失败详情或渲染异常堆栈 | 定位具体错误 |
| SSE 调试信息 | SSE 状态、chunk 计数、累积输出 | 仅 LLM + Stream 模式 |

**排查流程**：

1. 检查验证状态徽章：`PASS` → 正常渲染，`FAIL` → 查看错误信息
2. `FAIL` 时查看错误信息区域，按错误前缀定位问题（`sports_score_list:` / `weather_summary:` / 通用字段名）
3. 检查原始 A2UI JSON，确认组件树结构
4. Card HTTP 模式下检查"原始输出"区域的服务端响应

---

## 9. 新增 cardType 的标准开发流程

### 9.1 协议先行

1. 在 `docs/card-contracts/` 新增协议文档，按 10 段结构编写（背景、协议信息、字段定义、枚举定义、校验规则、渲染规则、JSON 示例、Android 集成、AI 组对接要求、版本演进）
2. 明确 ERROR（阻断渲染）和 WARNING（不阻断）的边界
3. AI 组和 Android 组联合评审协议

### 9.2 Android 端实现

1. **`CardType.java`** — 新增枚举值
2. **`CardContractValidator.java`** — 新增 `validateXxx()` 方法 + switch 分支 + 更新 supported-type 错误字符串
3. **`card/template/XxxTemplate.java`** — 新增渲染模板，实现 `public static String[] render(JSONObject data)` + 私有格式化辅助方法
4. **`CardTemplateRenderer.java`** — 新增 switch 分支委托到新模板

### 9.3 测试

1. **`XxxTemplateTest.java`** — 模板单元测试（render 输出、格式化方法、边界情况）
2. **`CardContractValidatorTest.java`** — 新增校验测试（valid / invalid / warning 场景）
3. **`CardTemplateRendererTest.java`** — 新增集成测试（完整渲染 + A2uiJsonValidator 通过）

### 9.4 Fixture 数据

在 `app/src/main/assets/card_fixtures/` 新增：

- `{cardType}_basic.json` — 完整数据，有效无 warning
- `{cardType}_partial.json` — 缺少可选字段，有效无 warning
- `{cardType}_warning.json` — 触发 warning 的场景，有效有 warning
- 根据需要新增更多 fixture（如空状态、最小数据等）

### 9.5 Mock 服务器

在 `tools/card-data-server/server.py` 中：

1. 新增 `MOCK_XXX` 数据常量
2. 在关键词匹配逻辑中新增意图分支
3. 在 `test_card_server.py` 中新增测试用例

### 9.6 文档

1. 更新 `docs/card-contracts/README.md` 协议列表
2. 更新 `demo/android-a2ui-text-demo/README.zh-CN.md`（cardType 数量、表格、fixture 列表、项目结构）
3. 更新 `tools/card-data-server/README.md` 支持的卡片类型

### 9.7 验证

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
python .\demo\android-a2ui-text-demo\tools\card-data-server\test_card_server.py
```

---

## 10. 当前限制和下一阶段建议

### 10.1 当前限制

- **Card Fixture 当前支持 5 种卡片类型**：`text_summary`、`text_list`、`image_text_list`、`sports_score_list`、`weather_summary`，其中 `text_summary`/`text_list`/`image_text_list` 尚无独立协议文档
- **sports_score_list / weather_summary 无真实数据源**，仅使用本地 fixture 和 Mock HTTP 服务器
- **Card HTTP 的 Mock 服务器仅基于关键词匹配**，不做自然语言理解
- **每次点击"生成 UI"会销毁旧 Surface 并创建新 Surface**，不支持增量更新
- **SSE 流式模式当前是"buffer-all → normalize → batch render"**，不是 token 级实时渲染
- **未实现自定义组件注册**（Markdown、Lottie、Chart 等需手动注册）
- **Function Call 仅有 `toast`**，Button 等组件的可交互能力有限
- **日志区域仅在 App 内展示**，不持久化，退出 Activity 后丢失

### 10.2 下一阶段建议

**短期（链路完善）**：

- 扩展 `text_summary` / `text_list` / `image_text_list` 的独立协议文档和严格校验
- 新增更多业务场景 cardType（设置卡片、表单卡片、地图卡片等）
- 接入真实 AI 组服务替换 Mock HTTP 服务器

**中期（交互增强）**：

- 注册更多 Function Call（导航、拨号、设置跳转等），需做能力分级
- 注册自定义组件（Markdown、Lottie、Chart）扩展渲染能力
- 支持 CardData 增量更新（推送而非全量替换）

**长期（性能与体验）**：

- 实现协议感知的 LLM SSE 增量渲染（token 级实时渲染）
- 在车机 ROM 场景下实现 `RomAiUiGenerationProvider`，做 Function Call 权限分级
- Surface 增量更新（不销毁重建）
- 日志持久化和远程日志上报
