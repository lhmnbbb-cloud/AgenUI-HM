# CLAUDE.md

## 项目定位

AGenUI 是一个 A2UI 原生渲染 SDK，用于把 LLM 生成的 A2UI v0.9 JSON 协议流渲染成 iOS / Android / HarmonyOS 原生 UI。

当前重点研究和二次开发方向是 Android / 定制 ROM 系统应用接入：从对话式大模型能力升级为可展示、可交互的动态 UI 能力。

## 优先使用的 Skills

如果当前 Claude Code 环境中存在这些 skills，按场景优先使用：

- `a2ui-generation`: 生成或校验 A2UI `createSurface` / `updateComponents` / `updateDataModel` 协议消息。
- `android-project-scan`: 开始 Android 相关任务前扫描项目结构、依赖和入口。
- `android-build-debug`: Android SDK 或 Playground 修改后做构建/调试验证。
- `android-change-risk-review`: 修改前后评估 Android 变更风险。
- `android-system-app-review`: 涉及系统应用、ROM 能力、权限、Function Call 时必须使用。
- `adb-logcat-anr`: 调试设备运行、崩溃、ANR、logcat 问题。
- `android-task-retrospective`: 较大 Android 任务结束前复盘验证项和遗留风险。

## 优先阅读路径

开始任何修改前，先阅读：

1. `README.zh-CN.md`
2. `docs/QuickStart.zh-CN.md`
3. `docs/API.zh-CN.md`
4. `platforms/android/src/main/java/com/amap/agenui/AGenUI.java`
5. `platforms/android/src/main/java/com/amap/agenui/render/surface/SurfaceManager.java`
6. `platforms/android/src/main/java/com/amap/agenui/render/surface/NativeEventBridge.java`
7. `platforms/android/src/main/java/com/amap/agenui/render/component/ComponentRegistry.java`
8. `playground/android/app/src/main/java/com/amap/agenuiplayground/A2UIPlaygroundActivity.java`
9. `core/src/stream/agenui_streaming_content_parser.cpp`
10. `core/src/surface/agenui_surface_coordinator.cpp`

## 架构心智模型

数据主链路：

LLM / Agent 输出 A2UI JSON
-> `SurfaceManager.beginTextStream / receiveTextChunk / endTextStream`
-> C++ `StreamingContentParser`
-> `ProtocolStreamExtractor`
-> `SurfaceCoordinator`
-> `Surface`
-> `DataModel + ComponentManager + VirtualDOM`
-> 平台桥接层
-> Android 原生 View 组件

Android 侧核心对象：

- `AGenUI`: 全局入口，加载 native so，初始化 engine，注册组件、函数、图片加载器。
- `SurfaceManager`: 一路独立流式会话，接收 A2UI JSON，管理多个 Surface。
- `Surface`: 一个独立 UI 渲染单元，持有根 `FrameLayout` 和组件树。
- `NativeEventBridge`: C++ 事件到 Android 组件树的桥。
- `ComponentRegistry`: 内置/自定义组件工厂注册中心。
- `A2UIComponent`: Android 组件基类，处理 View 生命周期、样式、Action、状态同步。

## 当前开发重点

优先做 Android 方向。除非任务明确要求，不要同时改 iOS、HarmonyOS 和 C++ core。

推荐优先级：

1. Android Playground 中验证协议和渲染效果。
2. Android SDK 层扩展组件、事件、Function Call。
3. A2UI JSON 示例和 catalog 约束。
4. 最后才考虑修改 C++ core 的协议解析、DataModel、VirtualDOM。

## 修改原则

- 不做无关重构。
- 不统一格式化整个仓库。
- 不修改生成产物、构建产物或 IDE 私有文件。
- 修改跨平台 core 时，必须说明 Android / iOS / HarmonyOS 影响面。
- Android 系统应用接入场景下，Function Call 必须考虑权限、白名单、参数校验和敏感能力隔离。
- LLM 输出不能直接驱动任意系统能力，只能通过受控组件和受控函数调用。

## Android 构建方式

SDK AAR：

```bash
./scripts/android/build.sh
```

Debug AAR：

```bash
./scripts/android/build.sh --debug
```

发布到本地 Maven：

```bash
./scripts/android/build.sh --publish-local
```

Android Playground：

用 Android Studio 打开：

```text
playground/android/
```

Playground 依赖模式由：

```text
playground/android/gradle.properties
```

中的：

```properties
agenui.sdk.source=true
```

控制。

## 测试现状

当前 Android / iOS 基本没有有效单元测试目录。HarmonyOS 只有模板 Hypium 测试。

因此每次修改后至少做：

- Android SDK 构建验证。
- Android Playground 手动验证关键 JSON story。
- 对协议解析、流式分片、Surface 生命周期、Function Call 增加必要测试或最小复现说明。

## A2UI 协议注意事项

核心协议消息包括：

- `createSurface`
- `updateComponents`
- `updateDataModel`
- `appendDataModel`
- `deleteSurface`

参考：

- `agenui_catalog.json`
- `playground/resource/stories/A2UI Show/*/updateComponents.json`
- `skills/a2ui-generation/`

模型输出 UI 时应优先受 `agenui_catalog.json` 约束，不要让模型直接生成 Android 代码。

## 自定义组件开发路线

Android 自定义组件优先参考：

- `playground/android/app/src/main/java/com/amap/agenuiplayground/component/factory/MarkdownComponentFactory.java`
- `playground/android/app/src/main/java/com/amap/agenuiplayground/component/impl/MarkdownComponent.java`
- `playground/android/app/src/main/java/com/amap/agenuiplayground/component/factory/LottieComponentFactory.java`
- `playground/android/app/src/main/java/com/amap/agenuiplayground/component/impl/LottieComponent.java`

组件注册位置参考：

- `playground/android/app/src/main/java/com/amap/agenuiplayground/A2UIPlaygroundActivity.java`

## Function Call 开发路线

Android Function Call 参考：

- `playground/android/app/src/main/java/com/amap/agenuiplayground/function/ToastFunction.java`
- `platforms/android/src/main/java/com/amap/agenui/function/IFunction.java`
- `platforms/android/src/main/java/com/amap/agenui/function/FunctionResult.java`

系统应用二次开发时，Function Call 必须做能力分级：

- 只读查询能力
- 可逆设置能力
- 高风险系统能力

高风险能力默认不要开放给模型直调。

## 不要轻易改的区域

除非任务明确要求，不要轻易改：

- `core/src/third_party/`
- `core/src/jni/`
- `platforms/ios/`
- `platforms/harmony/`
- 构建脚本中的发布逻辑
- `agenui_catalog.json` 的大范围结构

## Android A2UI LLM Rendering Lessons

来源：778d042..696445a 修复，解决"LLM JSON 返回了但不渲染"问题。

### LLM 输出必须经过归一化

真实 LLM 输出几乎不可能精确匹配 A2UI 协议结构（`type` vs `component`、`content` vs `text`、内联嵌套 children、缺少 version/surfaceId）。

778d042..696445a 修复学到的规则：

- **LLM 输出进入 SDK 前必须经过 `A2uiMessageNormalizer`**（`demo/android-a2ui-text-demo/app/src/main/java/com/amap/agenuidemo/A2uiMessageNormalizer.java`）。所有 Provider 类型（Mock/Fixture/LLM）都要经过归一化，不经归一化不进 SDK。
- **`A2uiJsonValidator` 只负责校验，不负责猜测真实模型格式**。归一化和校验职责分离：Normalizer 做宽容归一化，Validator 做严格校验。Validator 的 ERROR 条件必须与 C++ SDK 实际硬性要求一致（如 `version` 是 SDK 硬要求，必须报 ERROR 不能报 WARNING）。
- **TextDemoActivity 中 LLM 两条链路**：
  - 非 SSE：`provider.generate()` → `A2uiMessageNormalizer.normalizeMessages()` → validate → `surfaceManager.sendToSdk()`
  - SSE：`SSEClient` 缓冲全部 chunk → `onComplete` 时 `A2uiMessageNormalizer.normalizeRawText(fullText)` → validate → `sendToSdk()`
- **当前 SSE 是"真实 SSE 传输 + 完整归一化后渲染"，不是 token 级实时渲染**。SSE 不调用 `beginTextStream/receiveTextChunk/endTextStream`，而是 buffer-all → normalize → batch render。
- **真正 token 级渲染必须做协议感知流解析**，不能直接把模型 delta 喂给 `receiveTextChunk`。原因：C++ `ProtocolStreamExtractor::startStreamingComponents()` 要求完整协议 JSON；LLM chunk 是任意文本碎片，无法被协议解析器单独处理；归一化需要完整上下文（如 `ensureSingleRoot()` 需看到所有组件）。
- **SDK 静默失败是最危险的调试盲区**。`startStreamingComponents()` 缺 `version` 返回 false 无回调；`SurfaceCoordinator::updateComponents()` surface 不存在直接丢弃。修改 SDK 错误可观测性需要改 C++ core，当前只能在 Java 层做好归一化兜底。
- **每次改动后至少运行**：
  ```bash
  .\gradlew.bat testDebugUnitTest assembleDebug
  python .\tools\llm-proxy\test_sse_endpoint.py
  ```

## AGenUI Android Demo 关键约定

适用于 `demo/android-a2ui-text-demo/`。完整里程碑文档见 `docs/demo-milestone-card-http-v0.1.md`。

### 推荐链路

**正式推荐链路是 AI 组输出 CardData，Android 本地模板确定性生成 A2UI：**

```
用户输入 → AI 组服务 → CardData JSON → CardContractValidator → CardTemplateRenderer → A2uiJsonValidator → SurfaceManager → 渲染
```

LLM 直接生成 A2UI 仅用于探索验证，不作为正式稳定链路。原因见 `docs/demo-milestone-card-http-v0.1.md` 第 4 节。

### CardData 校验和渲染规则

- **Card HTTP 接口返回 CardData，不返回 A2UI。** 服务端 unknown intent 返回 HTTP 400 + `debug.matchedIntent=unknown`，不默认返回某个卡片类型。
- **CardData 必须先过 `CardContractValidator`。** 校验失败触发 fallback 卡片，不允许空白 UI。ERROR 阻断渲染，WARNING 不阻断。
- **模板输出的 A2UI 必须过 `A2uiJsonValidator`。** 确保模板输出始终是合法 A2UI 协议。
- **Card Fixture / Card JSON / Card HTTP 输出不用走 `A2uiMessageNormalizer`。** 模板输出已是合法 A2UI，归一化仅用于 LLM 直接生成 A2UI 的场景。

### CardData 协议字段易错点

- **`sports_score_list` 的 `homeTeam`/`awayTeam` 必须是对象**（`{"name": "湖人", "score": 108}`），不能是字符串（`"湖人"`）。写成字符串会导致 `CardContractValidator` 报 ERROR 并触发 fallback。
- **`weather_summary` 的 `location` 是顶层字段**，不在 `current` 内。写在 `current` 里不会报错但会导致 location-time 行缺失。
- **`sports_score_list` 的 `status` 使用英文枚举值**（`final`/`live`/`scheduled`），不是中文（`已结束`/`进行中`）。中文值会被视为 unknown status，产生 warning 但仍渲染。
- **`sports_score_list` 的 `items: []` 是合法空状态**，不触发 fallback；**`weather_summary` 的 `current: {}` 也是合法的**，但会产生 warning。

### Provider 切换 UI 规则

`switchProvider()` 中必须**先隐藏所有条件控件，再按 case 显示**。不能只在某个 case 里隐藏其他 case 的控件——遗漏一个 case 就会出现控件残留。

条件控件列表：`spinnerFixture`、`spinnerCardFixture`、`etLlmUrl`、`etProxyToken`、`etCardHttpUrl`。

### 常见坑

| 问题 | 现象 | 原因 | 排查 |
|------|------|------|------|
| PASS 但 UI 空白 | 徽章 PASS、状态 rendered，界面空白 | 组件树无根组件 / 组件高度为 0 / C++ SDK 静默丢弃 | 检查 A2UI JSON 组件树结构，确认根组件未被引用、surfaceId 一致，`adb logcat` 看 C++ 日志 |
| 协议字段层级错 | 本应渲染的卡片走了 fallback | `homeTeam: "湖人"` 而非 `homeTeam: {name: "湖人"}`；`location` 写在 `current` 内而非顶层 | 查看 Debug 面板错误信息前缀（`sports_score_list:` / `weather_summary:`） |
| Server 返回结构和协议不一致 | Card HTTP 拿到的数据渲染异常 | Mock server 的 MOCK_XXX 数据常量与 CardContractValidator 要求的字段结构不同步 | 用 `test_card_server.py` 单独测 server，对比协议文档 `docs/card-contracts/` |
| Provider 切换控件残留 | 切换后仍显示前一个 Provider 的输入框 | `switchProvider()` 未在所有 case 前统一隐藏条件控件 | 切换后检查界面是否有多余控件 |
| LLM 输出归一化后仍不渲染 | A2UI JSON 看起来对但 SDK 不渲染 | 归一化掩盖了结构问题，或 C++ SDK 因 version/surfaceId 静默失败 | 优先走 Card 链路绕过归一化；LLM 链路需 `adb logcat` 排查 C++ 层 |

## 推荐工作方式

每次任务先回答：

1. 目标是什么？
2. 影响 Android SDK、Playground、core 哪几层？
3. 是否需要改 A2UI 协议或 catalog？
4. 是否涉及 Function Call / 系统权限？
5. 如何验证？

然后再编码。
