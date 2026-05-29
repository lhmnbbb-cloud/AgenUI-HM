# A2UI Text Demo

最小 Android Demo App，验证"用户输入文本 → 生成 A2UI JSON → AGenUI 原生渲染 UI"闭环。

## 功能

- 输入中文文本，点击"生成 UI"按钮
- 支持六种 Provider 模式生成 A2UI 协议 JSON：
  - **Mock** — 根据关键词匹配生成内置 UI
  - **Fixture** — 从 `assets/fixtures/` 加载本地 A2UI JSON 样例
  - **Card Fixture** — 从 `assets/card_fixtures/` 加载结构化卡片数据，本地确定性生成 A2UI
  - **Card JSON** — 用户在输入框粘贴 CardData JSON，直接走 Card 渲染链路
  - **LLM** — 通过 HTTP POST 调用可配置的生成接口，接入真实大模型
  - **Card HTTP** — 通过 HTTP POST 调用可配置的 CardData 接口，模拟 AI 组返回结构化卡片数据
- LLM 输出先经过 `A2uiMessageNormalizer` 归一化，再进入校验和 SDK 渲染
- Card Fixture 输出不经过 Normalizer，由 `CardTemplateRenderer` 本地确定性生成 A2UI
- Card JSON 输出不经过 Normalizer，用户粘贴的 CardData JSON 直接由 `CardTemplateRenderer` 渲染
- 所有 UI 通过 AGenUI SDK 原生渲染，不依赖 WebView
- 默认使用 MockUiGenerationProvider，不依赖网络大模型

### Mock 模式

关键词映射：
- **天气** → 天气卡片
- **设置** → 设置项列表
- **表单** → 表单组件（TextField + CheckBox + Button）
- **列表** → 功能列表
- 其他输入 → 通用信息卡片

### Fixture 模式

从 `assets/fixtures/` 目录加载预置的 A2UI JSON 样例文件。App 内通过下拉框选择不同 fixture：

| Fixture 文件 | 说明 |
|---|---|
| `weather.json` | 天气卡片 |
| `settings.json` | 设置项列表 |
| `form_with_action.json` | 用户注册表单（含 Button action 触发 Toast） |
| `list.json` | 功能列表 |
| `info.json` | 通用信息卡片 |

Fixture 文件格式为 JSON 数组：

```json
[
  { "version": "v0.9", "createSurface": { "surfaceId": "...", "catalogId": "..." } },
  { "version": "v0.9", "updateComponents": { "surfaceId": "...", "components": [...] } },
  {}
]
```

- `[0]` = createSurface 消息
- `[1]` = updateComponents 消息
- `[2]` = updateDataModel 消息（可为空 `{}`）

### Card Fixture 模式

这是推荐的真实项目架构验证路径：**AI 输出结构化卡片数据 → Android 本地确定性生成 A2UI → 渲染**。

与普通 Fixture 的区别：
- **Fixture** 加载的是已写好的 A2UI 协议 JSON（最终 UI）
- **Card Fixture** 加载的是结构化业务数据（卡片语义），由 `CardTemplateRenderer` 在本地转换为 A2UI

数据流程：

```
CardData JSON (结构化卡片数据)
  → CardContractValidator (校验卡片协议)
  → CardTemplateRenderer (本地确定性生成 A2UI)
  → A2uiJsonValidator (校验 A2UI 输出)
  → SurfaceManager → AGenUI 渲染
```

CardData 不经过 `A2uiMessageNormalizer`，模板输出已是合法 A2UI。

支持五种 cardType：

| cardType | 必填字段 | 说明 |
|---|---|---|
| `text_summary` | requestId, cardType, title, content | 文本摘要卡片 |
| `text_list` | requestId, cardType, title, items[] | 文字列表卡片（每个 item 含 text） |
| `image_text_list` | requestId, cardType, title, items[] | 图文列表卡片（每个 item 含 imageUrl, title, subtitle） |
| `sports_score_list` | requestId, cardType, title, items[] | NBA 赛况卡片（每个 item 含 status, homeTeam{name}, awayTeam{name}） |
| `weather_summary` | requestId, cardType, title, current{} | 天气摘要卡片（current 含 condition, temperature 等） |

`sports_score_list` 字段说明：

| 字段 | 级别 | 说明 |
|---|---|---|
| `items` | 必填 | JSONArray，可为空（渲染空状态，非 fallback） |
| `items[].status` | 可选 | `final` / `live` / `scheduled`，未知值产生 warning |
| `items[].homeTeam.name` | 必填 | 主队名称 |
| `items[].awayTeam.name` | 必填 | 客队名称 |
| `items[].homeTeam.score` | 可选 | 主队分数（final/live 缺失产生 warning） |
| `items[].awayTeam.score` | 可选 | 客队分数 |
| `items[].summary` | 可选 | 比赛摘要 |
| `items[].startTime` | 可选 | 开赛时间（如 "19:00"） |

`weather_summary` 字段说明：

| 字段 | 级别 | 说明 |
|---|---|---|
| `current` | 必填 | JSONObject，当前天气信息（允许 `{}`，产生 warning） |
| `current.condition` | 建议填充 | 天气状况（如 "晴转多云"），缺失产生 warning |
| `current.temperature` | 建议填充 | 温度（推荐 number 如 `26`），缺失产生 warning |
| `current.high` | 可选 | 最高温度 |
| `current.low` | 可选 | 最低温度 |
| `current.airQuality` | 可选 | 空气质量（如 "良"） |
| `current.humidity` | 可选 | 湿度（如 "65%"） |
| `current.wind` | 可选 | 风力信息（如 "东南风 3级"） |
| `location` | 可选 | 位置名称（如 "上海"） |
| `updatedAt` | 可选 | 更新时间（如 "14:30 更新"） |
| `tips` | 可选 | JSONArray 天气提示，建议不超过 5 条 |

Card Fixture 样例：

| 文件 | cardType | 说明 |
|---|---|---|
| `text_summary_basic.json` | text_summary | 天气摘要 |
| `text_list_basic.json` | text_list | 功能列表 |
| `image_text_list_basic.json` | image_text_list | 热门推荐（含图片 URL） |
| `text_list_empty.json` | text_list | 空列表（触发 fallback） |
| `invalid_missing_card_type.json` | 无 | 缺少 cardType（触发 fallback） |
| `sports_score_list_basic.json` | sports_score_list | NBA 赛况：1场 final + 1场 live + 1场 scheduled |
| `sports_score_list_empty.json` | sports_score_list | 空赛况（渲染空状态，非 fallback） |
| `sports_score_list_partial.json` | sports_score_list | 缺少可选字段（summary、startTime） |
| `sports_score_list_warning.json` | sports_score_list | unknown status + final 缺 score（warning 但仍有效） |
| `weather_summary_basic.json` | weather_summary | 完整天气数据（condition + temperature + high/low + 详情 + tips） |
| `weather_summary_partial.json` | weather_summary | 仅 condition + temperature（无可选字段） |
| `weather_summary_warning.json` | weather_summary | 缺 location/temperature，6 tips（3 warnings） |
| `weather_summary_minimal.json` | weather_summary | current:{} 仅（3 warnings，仍有效） |

CardData 示例：

```json
{
  "requestId": "summary_001",
  "cardType": "text_summary",
  "title": "今日天气",
  "content": "上海今日晴转多云，气温 26°C / 18°C。"
}
```

校验失败时自动降级为 error_fallback 卡片（不会出现空白 UI）。

`sports_score_list` 是一个真实业务场景示例，验证"结构化业务数据 → 本地模板 → A2UI"完整管线。AI 输出 NBA 赛况结构化数据，Android 端确定性生成 A2UI，无需 LLM 参与 UI 生成。空赛况列表渲染"今日暂无赛况"空状态（合法，非 fallback），校验 warning（如未知 status、final 缺 score）不阻断渲染。

### Card JSON 模式

切换到 **Card JSON** Provider 后：

1. 输入框提示变为"粘贴 CardData JSON"
2. 用户粘贴任意 cardType 的 CardData JSON，点击"生成 UI"
3. App 直接将 JSON 送入 `CardContractValidator` → `CardTemplateRenderer` 渲染链路，不经过 LLM、不经过 `A2uiMessageNormalizer`

数据流程与 Card Fixture 相同：

```
用户粘贴 CardData JSON
  → CardContractValidator (校验卡片协议)
  → CardTemplateRenderer (本地确定性生成 A2UI)
  → A2uiJsonValidator (校验 A2UI 输出)
  → SurfaceManager → AGenUI 渲染
```

此模式用于模拟 AI 组直接传入 CardData JSON 的场景，验证端到端管线时不依赖本地 fixture 文件。

### Card HTTP 模式

切换到 **Card HTTP** Provider 后：

1. 出现 URL 输入框，填写 CardData 接口地址（默认 `http://10.0.2.2:8766/generate-card`）
2. 输入文本，点击"生成 UI"
3. App 通过 HTTP POST 调用该接口，请求体：

```json
{
  "userInput": "北京天气",
  "version": "0.1",
  "supportedCardTypes": ["sports_score_list", "weather_summary"]
}
```

4. 接口返回 CardData JSON：

```json
{
  "cardData": {
    "requestId": "weather-123",
    "cardType": "weather_summary",
    "title": "今日天气",
    ...
  }
}
```

5. App 直接将返回的 cardData 送入 `CardContractValidator` → `CardTemplateRenderer` 渲染链路，不经过 LLM、不经过 `A2uiMessageNormalizer`

数据流程：

```
用户输入文本
  → CardHttpProvider (HTTP POST 请求 CardData)
  → CardContractValidator (校验卡片协议)
  → CardTemplateRenderer (本地确定性生成 A2UI)
  → A2uiJsonValidator (校验 A2UI 输出)
  → SurfaceManager → AGenUI 渲染
```

此模式用于模拟未来 AI 组服务返回结构化 CardData JSON 的场景。相比直接返回 A2UI JSON，这种架构：
- 降低了 LLM 输出的复杂度（无需生成正确的 A2UI 组件结构）
- 保证了 UI 渲染的一致性（通过本地模板）
- 允许 UI 更新无需修改模型提示词
- 便于做卡片协议校验和降级处理

#### 本地 Mock CardData 服务器

项目提供了一个 Mock CardData 服务器：`tools/card-data-server/`

```bash
cd tools/card-data-server
python server.py                  # 默认监听 127.0.0.1:8766

# 真机调试时绑定所有接口：
$env:CARD_SERVER_HOST="0.0.0.0"
python server.py

# 可选：修改端口
$env:CARD_SERVER_PORT="8766"
```

服务器根据关键词匹配返回对应卡片类型：
- 输入含"天气"、"weather" → 返回 `weather_summary`
- 输入含"体育"、"NBA"、"比分"、"score" → 返回 `sports_score_list`
- 其他输入 → 返回 400 error，`debug.matchedIntent=unknown`

测试服务器：

```bash
python test_card_server.py
```

详见 `tools/card-data-server/README.md`。

weather_summary 示例（可直接复制到输入框）：

```json
{
  "requestId": "weather_basic",
  "cardType": "weather_summary",
  "title": "今日天气",
  "location": "上海",
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
}
```

sports_score_list 示例（可直接复制到输入框）：

```json
{
  "requestId": "nba_20260526_001",
  "cardType": "sports_score_list",
  "title": "NBA 今日赛况",
  "subtitle": "2026-05-26",
  "updatedAt": "14:30 更新",
  "league": "NBA",
  "items": [
    {
      "gameId": "g001",
      "status": "final",
      "startTime": "09:00",
      "summary": "湖人终结连败",
      "homeTeam": { "name": "湖人", "score": 108 },
      "awayTeam": { "name": "凯尔特人", "score": 102 }
    },
    {
      "gameId": "g002",
      "status": "live",
      "startTime": "10:30",
      "summary": "第三节进行中",
      "homeTeam": { "name": "勇士", "score": 85 },
      "awayTeam": { "name": "掘金", "score": 78 }
    },
    {
      "gameId": "g003",
      "status": "scheduled",
      "startTime": "19:00",
      "homeTeam": { "name": "雄鹿" },
      "awayTeam": { "name": "76人" }
    }
  ]
}
```

### 流式模式 (Streaming)

打开 "Stream" 开关后，A2UI JSON 会被分片发送到 `receiveTextChunk`。

- **Mock / Fixture + Stream ON**：使用 `StreamingSimulator` 模拟流式输出（默认 `chunkSize` = 20 字符/片，`delayMs` = 50ms/片）
- **LLM + Stream ON**：使用真实 SSE 传输接收模型增量，流结束后归一化并渲染，详见"SSE 流式模式"章节

### LLM 模式

切换到 **LLM** Provider 后：

1. 出现 URL 输入框，填写生成接口地址（默认 `http://10.0.2.2:8787/generate-ui`）
2. 输入文本，点击"生成 UI"
3. App 通过 HTTP POST 调用该接口，请求体：

```json
{
  "userInput": "显示天气卡片",
  "version": "v0.9",
  "availableComponents": ["Text", "Image", "Button", ...]
}
```

4. 接口返回 A2UI JSON：

```json
{
  "messages": [
    {"version": "v0.9", "createSurface": {...}},
    {"version": "v0.9", "updateComponents": {...}},
    {}
  ]
}
```

5. App 先用 `A2uiMessageNormalizer` 兼容常见 LLM 输出形态，再用 `A2uiJsonValidator` 校验；验证失败时 Debug 面板显示原始输出和错误详情，不会发送到 SDK 渲染

LLM 请求在后台线程执行，不阻塞 UI。"生成 UI"按钮在请求期间（含流式传输过程）变为"生成中..."并禁用，直到渲染完成或出错才恢复。切换 Provider 或退出 Activity 时会取消进行中的流式传输。

### SSE 流式模式

切换到 **LLM** Provider 并打开 "Stream" 开关后，使用真实 SSE 流式输出：

1. App 自动将 URL 中的 `/generate-ui` 替换为 `/generate-ui-stream`（如 `http://10.0.2.2:8787/generate-ui-stream`）
2. 输入文本，点击"生成 UI"
3. App 通过 HTTP POST 调用 SSE 端点，请求体与 LLM 模式相同
4. 代理服务器打开流式 LLM 连接，将文本增量作为 SSE 事件转发：

```
data: {"version":"v0.9","createSurface":...
data: {"surfaceId":"...
data: [DONE]
```

5. App 收到 chunk 后先缓存在 Debug 面板展示，不直接把半截模型 token 送进 SDK
6. 流结束后，对累积的完整文本做 `A2uiMessageNormalizer.normalizeRawText()` 和 `A2uiJsonValidator` 校验，通过后再按三段 A2UI 消息发送给 SDK 渲染

Debug 面板在 LLM + Stream 模式下额外显示：
- **SSE 状态** — connecting / streaming (N chunks) / complete / error
- **最后收到的 chunk** — 截断预览
- **累积输出** — 当前已接收的全部文本

取消支持：再次点击生成按钮、切换 Provider 或退出 Activity 时，关闭 SSE 连接，不弹出错误 Toast。

## 调试区域

App 界面中部有 Debug 面板，展示：

- **当前 Provider** — 类型及所选 fixture 名称
- **用户输入** — 最近一次输入文本
- **原始 A2UI JSON** — 格式化的 createSurface / updateComponents / updateDataModel
- **SDK 渲染状态** — idle / streaming / streaming (SSE) / rendered / error
- **SSE 调试信息** — SSE 状态、chunk 计数、最后 chunk 预览、累积输出（仅 LLM + Stream 模式）
- **验证状态徽章** — IDLE / PASS / FAIL / ERROR
- **错误信息** — 验证失败详情或渲染异常堆栈

## A2UI JSON 验证器

发送给 SurfaceManager 前，LLM 输出会先归一化，A2UI JSON 会自动校验：

0. **LLM 输出归一化** — 兼容 `{"messages":[...]}`、裸数组、Markdown fenced JSON、连续 JSON object、`type`/`component` 字段差异、嵌套 children、缺失 version、多 root 等常见模型输出

1. **JSON 合法性** — 每条消息必须是合法 JSON
2. **协议结构** — createSurface 必须包含 `createSurface.surfaceId` + `catalogId`；updateComponents 必须包含 `updateComponents.surfaceId` + `components`
3. **组件类型** — `component` 字段值必须在 SDK 内置的 22 种组件内，未知类型视为错误
4. **缺失字段提示** — 缺少 id / component / surfaceId 等关键字段时给出清晰错误
5. **surfaceId 一致性** — createSurface.surfaceId 必须等于 updateComponents.surfaceId
6. **组件 id 唯一性** — components 数组内 id 不能重复
7. **children/child 引用检查** — 容器组件的 children/child 引用的 id 必须存在于 components 数组中
8. **根组件检查** — 必须存在且仅有一个未被其他组件引用为 child 的根组件，推荐 id 为 "root"
9. **Action 校验** — 组件 `action` 仅允许 `functionCall`，不允许 `event`；`functionCall.call` 必须存在于白名单（当前仅 `toast`）
10. **Function Call 白名单** — `action.functionCall.call` 只允许 `toast`（当前已注册的唯一函数）

验证失败时不会发送到 SDK，Debug 面板显示错误详情。

## 本地 LLM 代理服务器

`tools/llm-proxy/` 提供一个 Python 代理，读取环境变量中的 API Key，调用真实 LLM 生成 A2UI JSON。代理使用 `ThreadingHTTPServer` 支持并发请求。

```bash
cd tools/llm-proxy

# Windows PowerShell
$env:LLM_API_KEY="your-api-key"
$env:LLM_BASE_URL="https://your-llm-endpoint.example.com"
$env:LLM_MODEL="your-model-name"
$env:LLM_API_FORMAT="anthropic"   # 或 "openai"
python server.py                  # 默认监听 127.0.0.1:8787

# 真机调试时绑定所有接口：
$env:LLM_PROXY_HOST="0.0.0.0"
python server.py

# 可选：设置代理访问令牌
$env:LLM_PROXY_TOKEN="your-secret-token"

# 可选：设置 LLM 请求超时（默认 100 秒）
$env:LLM_REQUEST_TIMEOUT="100"
```

默认绑定 `127.0.0.1`（仅本机访问），真机调试需设置 `LLM_PROXY_HOST=0.0.0.0`。设置 `LLM_PROXY_TOKEN` 后，Android App 端需在 Proxy token 输入框填入相同值。`LLM_API_FORMAT` 只允许 `anthropic` 或 `openai`，其他值启动时立即报错退出。

Android 模拟器通过 `http://10.0.2.2:8787/generate-ui` 访问，真机使用 `http://<PC-IP>:8787/generate-ui`。支持 OpenAI 和 Anthropic 两种 API 格式，通过 `LLM_API_FORMAT` 环境变量配置。

详见 `tools/llm-proxy/README.md`。

## ToastFunction

App 注册了 `toast` Function Call。Button 组件可通过 `action.functionCall` 触发 Toast：

```json
{
  "id": "submit-btn",
  "component": "Button",
  "text": "提交",
  "variant": "primary",
  "action": {
    "functionCall": {
      "call": "toast",
      "args": { "value": "提交成功！" }
    }
  }
}
```

## 用 Android Studio 打开

1. 打开 Android Studio
2. File → Open → 选择 `demo/android-a2ui-text-demo/` 目录
3. 等待 Gradle sync 完成

## 构建与运行

确保 `gradle.properties` 中：

```properties
agenui.sdk.source=true
```

然后在 Android Studio 中直接 Run，或命令行：

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

安装到设备：

```bash
gradlew.bat installDebug
```

## 公司共通控件 SDK 适配（POC）

Demo 支持在 A2UI 渲染层可选接入公司 Melo/Gua 共通控件 SDK。A2UI 协议和 CardData 协议不受影响——AI 仍然输出标准 A2UI JSON（Text/Card/Row/Column/Button），适配仅发生在 Android 渲染层。

### 设计原则

- **AAR 不提交 Git**：`sdk/*.aar` 和 `sdk/*.jar` 已加入 `.gitignore`
- **A2UI 协议无感知**：CardData/A2UI JSON 中不出现 Melo/Gua 控件名，始终使用标准 A2UI 组件名
- **反射加载，无编译期依赖**：不直接 `import com.meloui.car.ui.*`，通过 `Class.forName()` 创建控件
- **自动 fallback**：AAR 不存在或反射失败时，自动回退到标准 Android View，构建和运行不受影响

### AAR 放置

将公司共通控件 SDK AAR 放到：

```
demo/android-a2ui-text-demo/sdk/gua-car-ui-lib-release.aar
```

Gradle 会在构建时自动检测：AAR 存在则编译引用，不存在则跳过。

### minSdk 28

公司 AAR 要求 `minSdkVersion=28`，因此 Demo app 的 `minSdk` 从 21 提升到 **28**。如果不需要共通控件，可以手动改回 21。

### 已适配的 A2UI 标准组件

| A2UI 组件 | Melo/Gua 控件 | Fallback |
|-----------|--------------|----------|
| Text | MeloTextView (AppCompatTextView) | TextView |
| Card | MeloCardView / MeloFrameLayout | FrameLayout |
| Button | MeloFrameLayout 容器 | FrameLayout |

> Row 和 Column 在当前 POC 阶段不覆盖。用 LinearLayout 替代 FlexContainerLayout 会丢失 stretch、gap、flex-wrap 等 Flexbox 语义，导致 card template 布局异常。保留原生 FlexContainerLayout 保证布局一致性。

### Button 第一版限制

Button 使用 Melo 容器（FrameLayout）而非 MeloButton。原因：
- A2UI Button 是"容器 + child component"模型，child 单独渲染后加入 Button 容器
- MeloButton / AppCompatButton 是 `Button` 子类，不是 `ViewGroup`，无法容纳 child View
- 使用 MeloFrameLayout 保留 child 渲染和 Function Call 点击能力

### Text → Mlui TextAppearance 适配

为了让 Card 模板（如 `WeatherSummaryTemplate`）不再硬编码字号 / 颜色 / 字重，引入 5 个公司语义 Text variant：

| variant | 映射到 | 使用场景 |
|---------|--------|---------|
| `mluiTitleLarge` | `MluiTextAppearance.Title.Large` | 主温度等需要醒目展示的大号字 |
| `mluiTitle` | `MluiTextAppearance.Title` | 卡片标题 |
| `mluiBody` | `MluiTextAppearance.Body` | 主信息正文 |
| `mluiContent` | `MluiTextAppearance.Content` | 次级数据（高低温、风力、湿度） |
| `mluiLabel` | `MluiTextAppearance.Label` | 辅助说明（位置 · 时间、tips、空状态） |

实现链路：

1. `assets/common_controls_theme.json` 在 default theme 下声明 5 个 variant，每个 variant 仅设置私有 styles key `melo-text-appearance`（值为 Android style 资源名，如 `MluiTextAppearance.Title.Large`）
2. `CommonControlsThemeLoader.loadIfPresent()` 在 `AGenUI.initialize()` 之后、`SurfaceManager` 创建之前调用 `aGenUI.registerDefaultTheme(...)` 把 JSON 注册进 SDK
3. C++ spec 引擎用 `nlohmann::merge_patch` 合并新 variant（不会影响 SDK 原有的 h1/h2/body/caption）
4. 渲染时 `MeloTextComponent.onUpdateProperties()` 从 styles 中识别 `melo-text-appearance`，先剥离再传给 `StyleHelper`：
   - 反射调用 `Resources.getIdentifier(name, "style", pkg)`，同时尝试原名和点替换为下划线（`MluiTextAppearance_Title_Large`）
   - 解析到 style id 后调用 `setTextAppearance(styleId)`
   - 然后再走 `StyleHelper.applyTextStyles()` 处理 `text-align` 等剩余样式，保证模板里显式写的属性能覆盖 TextAppearance
5. AAR / style 资源缺失时仅 `Log.w` 一行，不抛异常；标准 `TextView` 会使用系统默认渲染，整个 Demo 仍能跑通

> 写新 variant 时不要往里塞 `font-size` / `color` / `line-height` —— 会覆盖公司 TextAppearance 中的对应属性。要覆盖时显式写在组件 `styles` 上。

### 验证是否启用

查看 App 日志：

```
CommonControlsViewFactory: Common controls SDK detected (MeloTextView found)
CommonControlsRegistry: Common controls available: true
CommonControlsRegistry: Registered common control component: Text → MeloTextView
CommonControlsRegistry: Registered common control component: Card → MeloCardView
...
```

如果 AAR 不存在：

```
CommonControlsViewFactory: Common controls SDK not found, using standard Android Views
CommonControlsRegistry: Common controls available: false
```

## 项目结构

```
demo/android-a2ui-text-demo/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── common_controls_theme.json   # mlui* Text variant 主题（注入到 SDK default theme）
│       │   ├── fixtures/
│       │   │   ├── weather.json
│       │   │   ├── settings.json
│       │   │   ├── form_with_action.json
│       │   │   ├── list.json
│       │   │   └── info.json
│       │   └── card_fixtures/
│       │       ├── text_summary_basic.json
│       │       ├── text_list_basic.json
│       │       ├── image_text_list_basic.json
│       │       ├── text_list_empty.json
│       │       ├── invalid_missing_card_type.json
│       │       ├── sports_score_list_basic.json
│       │       ├── sports_score_list_empty.json
│       │       ├── sports_score_list_partial.json
│       │       ├── sports_score_list_warning.json
│       │       ├── weather_summary_basic.json
│       │       ├── weather_summary_partial.json
│       │       ├── weather_summary_warning.json
│       │       └── weather_summary_minimal.json
│       ├── java/com/amap/agenuidemo/
│       │   ├── TextDemoActivity.java           # 主 Activity
│       │   ├── UiGenerationProvider.java        # UI 生成接口
│       │   ├── MockUiGenerationProvider.java    # Mock 实现
│       │   ├── FixtureUiGenerationProvider.java # Fixture 实现
│       │   ├── LLMUiGenerationProvider.java     # LLM 实现（HTTP POST）
│       │   ├── CardHttpProvider.java            # Card HTTP 实现（HTTP POST 获取 CardData）
│       │   ├── ProviderType.java               # Provider 类型枚举
│       │   ├── A2uiMessageNormalizer.java       # LLM 输出归一化
│       │   ├── A2uiJsonValidator.java           # A2UI JSON 校验器
│       │   ├── ToastFunction.java               # Toast 函数调用
│       │   ├── StreamingSimulator.java          # 流式模拟器
│       │   ├── SSEClient.java                   # SSE 客户端（真实流式）
│       │   └── card/
│       │       ├── CardType.java                # 卡片类型枚举
│       │       ├── CardContractValidator.java   # 卡片协议校验
│       │       ├── CardTemplateRenderer.java    # 卡片→A2UI 模板渲染入口
│       │       ├── CardRenderResult.java        # 渲染结果
│       │       ├── CardDataProvider.java        # 卡片 Fixture 数据源
│       │       └── template/
│       │           ├── SportsScoreListTemplate.java  # sports_score_list 模板
│       │           └── WeatherSummaryTemplate.java   # weather_summary 模板
│       │       └── commonui/
│       │           ├── CommonControlsViewFactory.java  # Melo/Gua 控件反射工厂
│       │           ├── CommonControlsRegistry.java     # 注册入口
│       │           ├── CommonControlsThemeLoader.java  # 注册 mlui* Text variant 主题
│       │           ├── MeloTextComponent.java          # Text → MeloTextView（含 setTextAppearance）
│       │           ├── MeloTextComponentFactory.java
│       │           ├── MeloCardComponent.java          # Card → MeloCardView
│       │           ├── MeloCardComponentFactory.java
│       │           ├── MeloButtonComponent.java        # Button → MeloFrameLayout 容器
│       │           └── MeloButtonComponentFactory.java
│       └── res/
│           ├── layout/activity_text_demo.xml
│           └── values/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── tools/
│   ├── llm-proxy/
│   │   ├── server.py        # 本地 LLM 代理服务器
│   │   └── README.md
│   └── card-data-server/
│       ├── server.py        # Mock CardData 服务器
│       ├── test_card_server.py
│       └── README.md
├── sdk/                     # 公司共通控件 SDK（不提交 Git）
│   └── gua-car-ui-lib-release.aar
└── README.zh-CN.md
```

## LLM Provider 已实现

`LLMUiGenerationProvider` 已在项目中实现，通过 HTTP POST 调用可配置的生成接口。使用方式：

1. 切换 Provider 到 **LLM**
2. 在 URL 输入框填写生成接口地址
3. 如代理设置了 `LLM_PROXY_TOKEN`，在 Proxy token 输入框填入令牌
4. 运行本地代理服务器（见"本地 LLM 代理服务器"章节）
5. 输入文本，点击"生成 UI"

自定义 LLM 接口时，参考 `tools/llm-proxy/server.py` 中的请求/响应格式。

## ROM Provider 扩展

1. 在 `ProviderType` 中新增 `ROM` 枚举值
2. 实现 `UiGenerationProvider` 接口：

```java
public class RomAiUiGenerationProvider implements UiGenerationProvider {
    @Override
    public String[] generate(String userInput) {
        // 调用车机 ROM 内置 AI 能力，获取 A2UI 协议 JSON
        // 注意 Function Call 必须做权限分级和参数校验
    }
}
```

3. 在 `TextDemoActivity.switchProvider()` 中添加 ROM 分支

## 当前限制

- MockUiGenerationProvider 仅基于关键词匹配，不做自然语言理解
- FixtureUiGenerationProvider 不支持动态数据绑定
- Card Fixture 当前支持 text_summary、text_list、image_text_list、sports_score_list、weather_summary 五种卡片类型
- sports_score_list 当前无真实数据源，仅使用本地 fixture 和 Mock HTTP 服务器
- weather_summary 当前无真实数据源，仅使用本地 fixture 和 Mock HTTP 服务器
- 每次点击"生成 UI"会销毁旧 Surface 并创建新 Surface
- 未实现自定义组件注册（Markdown、Lottie、Chart 等需要手动注册，Demo 默认只使用 SDK 22 种内置组件）
- 日志区域仅在 App 内展示，不持久化
- StreamingSimulator 的 chunkSize 和 delayMs 未在 UI 中暴露调节
- LLM + Stream ON 当前是"SSE 传输 + 完整归一化后渲染"，不是把模型 token 实时直连到 `receiveTextChunk`
- 公司共通控件适配当前覆盖 3 个 A2UI 组件（Text/Card/Button），Row/Column 保留原生 FlexContainerLayout，其余 17 个标准组件未适配
- Button 使用 Melo 容器而非 MeloButton（Button 非 ViewGroup 无法容纳 child）
- minSdk 从 21 提升到 28（因公司 AAR 要求）
- Mlui TextAppearance 适配仅落地了 5 个 Text 语义 variant（mluiTitleLarge/mluiTitle/mluiBody/mluiContent/mluiLabel），其他 A2UI variant（h1/h2/body/caption 等）仍走 SDK 原有逻辑；公司 AAR / 对应 style 资源缺失时 `setTextAppearance` 跳过，文字按系统默认渲染

## 下一步建议

- 在 normalizer 稳定后，实现协议感知的 LLM SSE 增量渲染，再直连 `receiveTextChunk`
- 扩展 Card Fixture 支持更多 cardType（设置卡片、表单卡片、图表卡片等）
- 注册更多 Function Call，让 Button 等组件可交互
- 注册自定义组件（Markdown、Lottie、Chart 等）扩展渲染能力
- 在车机 ROM 场景下，实现 RomAiUiGenerationProvider，做 Function Call 权限分级
- 在 UI 中暴露 StreamingSimulator 的 chunkSize / delayMs 调节
- 给 LLM 请求增加超时、重试和缓存机制
- 扩展公司共通控件适配更多 A2UI 组件（Image、CheckBox、Slider、TextField 等）
- 研究 MeloRow/MeloColumn 方案：用 FlexContainerLayout + Melo 外层/内层 wrapper，而非 LinearLayout 替代
- 研究 MeloButton 作为 Button 容器方案（如 wrapping 在 ViewGroup 中）
