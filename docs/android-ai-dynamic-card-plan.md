# AI 动态卡片展示方案说明

## 1. 项目目标

当前定制 ROM 已接入 AI 大模型能力，但主要以对话文本形式呈现。下一阶段希望让 AI 能根据用户意图生成可视化卡片，在 Android 系统应用中以原生 UI 方式展示结果。

目标不是让模型随意生成 UI，而是建立一套稳定链路：

```text
用户输入/语音
 -> AI 组理解意图并获取业务数据
 -> 输出结构化卡片数据
 -> Android 组本地转换为 A2UI/原生 UI
 -> 稳定渲染卡片
```

## 2. 当前 Demo 结论

当前 demo 已验证可行性：

- AGenUI Android SDK 可以接收 A2UI JSON 并渲染原生 UI。
- LLM 可以生成 A2UI 协议数据。
- Android 侧已实现 normalize、validate、debug、fixture、LLM 接入、SSE 基础验证。
- 但也发现真实模型直接生成 UI 存在不稳定问题：不同模型输出差异大，同一输入可能生成不同结构，甚至出现 validator PASS 但实际空白。

因此真实项目不建议让 LLM 直接决定最终 UI 细节。

## 3. 推荐总体架构

推荐采用“AI 负责理解和数据，Android 负责展示和渲染”的分工。

```text
AI 组负责：
语音转文字、意图识别、工具调用、业务数据获取、卡片类型选择、结构化数据填充

Android 组负责：
卡片协议定义、数据校验、模板渲染、A2UI JSON 生成、原生 UI 展示、兜底、安全控制、埋点
```

核心原则：

```text
AI 输出业务语义和结构化数据
Android 输出最终 UI
```

## 4. 为什么不让 LLM 直接生成 A2UI

Demo 证明 LLM 可以直接生成 A2UI，但真实项目风险较高：

- UI 结构不稳定，不同模型差异大。
- 可能缺少 `root`、`version`、`surfaceId` 等 SDK 必需字段。
- 容易生成 Android 侧不支持的组件或样式。
- 业务数据可能被模型编造。
- Function Call 和系统能力调用存在安全风险。
- 难以保证 UI 风格一致、可测试、可回滚。

所以生产方案中，A2UI 更适合作为 Android 展示侧内部协议，而不是直接暴露给模型自由生成。

## 5. 推荐链路

以用户说“今日 NBA 赛况”为例：

### 第一步：用户输入

```text
今日 NBA 赛况
```

### 第二步：AI 组识别意图

```json
{
  "intent": "sports_score_query",
  "league": "NBA",
  "date": "today"
}
```

### 第三步：AI 组获取真实数据

AI 组通过体育数据 API 或公司业务服务获取真实赛况，而不是让模型编比分。

```json
{
  "league": "NBA",
  "date": "2026-05-25",
  "games": [
    {
      "homeTeam": "Celtics",
      "awayTeam": "Knicks",
      "homeScore": 108,
      "awayScore": 102,
      "status": "final"
    }
  ]
}
```

### 第四步：AI 组选择卡片类型并填充协议

```json
{
  "cardType": "sports_score_list",
  "title": "今日 NBA 赛况",
  "items": [
    {
      "homeTeam": "Celtics",
      "awayTeam": "Knicks",
      "homeScore": 108,
      "awayScore": 102,
      "status": "final"
    }
  ]
}
```

### 第五步：Android 侧校验并本地生成 A2UI

Android 根据 `cardType = sports_score_list` 选择本地模板：

```text
SportsScoreListTemplate -> A2UI 三段消息 -> AGenUI 渲染
```

## 6. 卡片协议设计

双方需要共同定义一批标准卡片类型，例如：

```text
text_summary          纯文本摘要卡
text_list             纯文字列表卡
image_text_list       图文列表卡
single_image          单图卡
sports_score_list     体育赛况卡
weather_card          天气卡
settings_list         设置项卡
action_confirm        操作确认卡
error_fallback        错误兜底卡
```

每种卡片需要定义：

- `cardType`
- 必填字段
- 可选字段
- 最大 item 数
- 字段类型
- 空数据表现
- 点击行为
- 权限等级
- Android 渲染模板

## 7. Android 侧工作内容

Android 组需要建设以下模块：

```text
CardContractValidator
校验 AI 组传来的结构化卡片数据

CardTemplateRenderer
根据 cardType 选择固定模板

A2uiMessageBuilder
本地生成 createSurface / updateComponents / updateDataModel

RenderFallbackManager
失败时展示兜底卡片，避免空白

FunctionCallGuard
控制按钮、跳转、系统能力调用权限

RenderTelemetry
记录渲染成功率、失败原因、耗时、cardType、requestId
```

Android 侧不再依赖模型生成最终 UI，而是用代码保证输出稳定。

## 8. AI 组工作内容

AI 组主要负责：

- ASR 语音转文字。
- 用户意图识别。
- 多轮上下文理解。
- 工具/API 调用。
- 真实业务数据获取。
- 选择合适 `cardType`。
- 按卡片协议填充结构化数据。
- 对缺数据、无权限、失败场景给出明确状态。

AI 组输出的不是完整 UI，而是标准卡片数据。

## 9. 协作接口示例

AI 组给 Android：

```json
{
  "requestId": "req_123",
  "cardType": "sports_score_list",
  "title": "今日 NBA 赛况",
  "dataVersion": "1.0",
  "items": [
    {
      "homeTeam": "Celtics",
      "awayTeam": "Knicks",
      "homeScore": 108,
      "awayScore": 102,
      "status": "final"
    }
  ]
}
```

Android 返回渲染结果或上报：

```json
{
  "requestId": "req_123",
  "cardType": "sports_score_list",
  "renderStatus": "success",
  "durationMs": 42
}
```

## 10. 验收标准

建议立项阶段定义这些指标：

- 合法卡片协议渲染成功率 >= 99%。
- 不允许出现空白卡片。
- 渲染失败必须有 fallback。
- 不支持的 `cardType` 必须降级。
- Function Call 必须白名单校验。
- 单卡组件数量、JSON 大小、渲染耗时有上限。
- 所有失败可通过 `requestId` 串联排查。

## 11. 项目阶段建议

第一阶段：协议对齐

Android 组和 AI 组共同定义 5 到 8 种核心卡片协议。

第二阶段：模板实现

Android 实现本地模板、校验、A2UI 生成和兜底。

第三阶段：AI 接入

AI 组输出真实结构化卡片数据，Android 联调。

第四阶段：稳定性验证

建立测试集，覆盖天气、体育、设置、车况、列表、图片、错误状态等场景。

第五阶段：上线准备

补齐埋点、安全策略、权限分级、灰度开关和回滚策略。

## 12. 结论

当前 demo 已证明 Android 侧具备承接 AI 动态 UI 的可行性。

真实项目建议从“模型直接生成 UI”升级为：

```text
AI 生成结构化卡片数据
Android 本地确定性生成 A2UI 并渲染
```

这样可以兼顾 AI 的理解能力和 Android 系统应用对稳定性、安全性、性能、可控性的要求。下一步重点应放在卡片协议、模板体系、失败兜底、安全边界和双方协作接口上。
