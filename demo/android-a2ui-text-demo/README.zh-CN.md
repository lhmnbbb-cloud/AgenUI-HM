# A2UI Text Demo

最小 Android Demo App，验证"用户输入文本 → 生成 A2UI JSON → AGenUI 原生渲染 UI"闭环。

## 功能

- 输入中文文本，点击"生成 UI"按钮
- 支持三种 Provider 模式生成 A2UI 协议 JSON：
  - **Mock** — 根据关键词匹配生成内置 UI
  - **Fixture** — 从 `assets/fixtures/` 加载本地 A2UI JSON 样例
  - **LLM** — 通过 HTTP POST 调用可配置的生成接口，接入真实大模型
- LLM 输出先经过 `A2uiMessageNormalizer` 归一化，再进入校验和 SDK 渲染
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

## 项目结构

```
demo/android-a2ui-text-demo/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── fixtures/
│       │       ├── weather.json
│       │       ├── settings.json
│       │       ├── form_with_action.json
│       │       ├── list.json
│       │       └── info.json
│       ├── java/com/amap/agenuidemo/
│       │   ├── TextDemoActivity.java           # 主 Activity
│       │   ├── UiGenerationProvider.java        # UI 生成接口
│       │   ├── MockUiGenerationProvider.java    # Mock 实现
│       │   ├── FixtureUiGenerationProvider.java # Fixture 实现
│       │   ├── LLMUiGenerationProvider.java     # LLM 实现（HTTP POST）
│       │   ├── ProviderType.java               # Provider 类型枚举
│       │   ├── A2uiMessageNormalizer.java       # LLM 输出归一化
│       │   ├── A2uiJsonValidator.java           # A2UI JSON 校验器
│       │   ├── ToastFunction.java               # Toast 函数调用
│       │   ├── StreamingSimulator.java          # 流式模拟器
│       │   └── SSEClient.java                   # SSE 客户端（真实流式）
│       └── res/
│           ├── layout/activity_text_demo.xml
│           └── values/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── tools/
│   └── llm-proxy/
│       ├── server.py        # 本地 LLM 代理服务器
│       └── README.md
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
- 每次点击"生成 UI"会销毁旧 Surface 并创建新 Surface
- 未实现自定义组件注册（Markdown、Lottie、Chart 等需要手动注册，Demo 默认只使用 SDK 22 种内置组件）
- 日志区域仅在 App 内展示，不持久化
- StreamingSimulator 的 chunkSize 和 delayMs 未在 UI 中暴露调节
- LLM + Stream ON 当前是"SSE 传输 + 完整归一化后渲染"，不是把模型 token 实时直连到 `receiveTextChunk`

## 下一步建议

- 在 normalizer 稳定后，实现协议感知的 LLM SSE 增量渲染，再直连 `receiveTextChunk`
- 注册更多 Function Call，让 Button 等组件可交互
- 注册自定义组件（Markdown、Lottie、Chart 等）扩展渲染能力
- 在车机 ROM 场景下，实现 RomAiUiGenerationProvider，做 Function Call 权限分级
- 在 UI 中暴露 StreamingSimulator 的 chunkSize / delayMs 调节
- 给 LLM 请求增加超时、重试和缓存机制
