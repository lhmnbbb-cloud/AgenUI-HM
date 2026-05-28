# 人工验收清单：Card HTTP v0.1

| 字段 | 值 |
|------|-----|
| **版本** | Card HTTP v0.1 |
| **范围** | `demo/android-a2ui-text-demo/` |
| **日期** | 2026-05-28 |
| **前置条件** | APK 已安装到模拟器或真机 |

---

## 前置准备

1. Android Studio 打开 `demo/android-a2ui-text-demo/`，构建并安装到设备
2. 确认 `gradle.properties` 中 `agenui.sdk.source=true`
3. 启动 Mock CardData 服务器（仅 Card HTTP 测试需要）：

```powershell
cd demo/android-a2ui-text-demo/tools/card-data-server
python server.py
```

真机需绑定所有接口：

```powershell
$env:CARD_SERVER_HOST="0.0.0.0"
python server.py
```

---

## 1. Card Fixture

### 1.1 sports_score_list basic

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card Fixture → 下拉选择 `sports_score_list_basic` → 点击"生成 UI" |
| **预期** | 渲染 3 场赛事卡片（1 场 FINAL + 1 场 LIVE + 1 场 scheduled），有比分显示，徽章 PASS |
| **失败查看** | Debug 面板 → 验证状态徽章（FAIL/ERROR）、错误信息区域 |

### 1.2 sports_score_list empty

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card Fixture → 下拉选择 `sports_score_list_empty` → 点击"生成 UI" |
| **预期** | 渲染空状态卡片（"今日暂无赛况"），徽章 PASS，**不是 fallback** |
| **失败查看** | Debug 面板 → 如果出现 fallback 错误卡片，说明 empty 状态被错误地当成了校验失败 |

### 1.3 sports_score_list warning

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card Fixture → 下拉选择 `sports_score_list_warning` → 点击"生成 UI" |
| **预期** | 正常渲染，徽章 PASS（warning 不阻断渲染）。第 1 场显示 unknown status 原始值，第 2 场 final 无 score 显示 "--" |
| **失败查看** | Debug 面板 → 如果徽章 FAIL，说明 warning 被错误地当成了 error |

### 1.4 weather_summary basic

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card Fixture → 下拉选择 `weather_summary_basic` → 点击"生成 UI" |
| **预期** | 渲染天气卡片：标题"今日天气"、位置时间行"上海 · 14:30 更新"、天气状况+温度、高低温、空气质量/湿度/风力、2 条 tips。徽章 PASS |
| **失败查看** | Debug 面板 → 验证状态徽章、错误信息；检查 A2UI JSON 中组件树是否完整 |

### 1.5 weather_summary minimal

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card Fixture → 下拉选择 `weather_summary_minimal` → 点击"生成 UI" |
| **预期** | 渲染简化天气卡片（仅标题 + 占位文本"暂无天气数据"），无位置时间行、无高低温、无详情行、无 tips。徽章 PASS（有 warning 但不阻断） |
| **失败查看** | Debug 面板 → 如果徽章 FAIL 或出现 fallback，说明 current:{} 被错误拒绝 |

---

## 2. Card JSON

### 2.1 粘贴 sports_score_list 示例

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card JSON → 清空输入框 → 粘贴以下 JSON → 点击"生成 UI" |
| **粘贴内容** | 见下方 |
| **预期** | 渲染 3 场赛事卡片，与 Card Fixture sports_score_list_basic 视觉一致，徽章 PASS |

```json
{
  "requestId": "nba_20260528_001",
  "cardType": "sports_score_list",
  "title": "NBA 今日赛况",
  "subtitle": "2026-05-28",
  "updatedAt": "14:30 更新",
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

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card JSON → 粘贴上述 JSON → 点击"生成 UI" |
| **预期** | 渲染 3 场赛事卡片，与 1.1 结果一致，徽章 PASS |
| **失败查看** | Debug 面板 → 错误信息；如果 JSON 解析失败，检查粘贴内容是否被截断或包含额外空白 |

### 2.2 粘贴 weather_summary 示例

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

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card JSON → 粘贴上述 JSON → 点击"生成 UI" |
| **预期** | 渲染天气卡片，与 1.4 结果一致，徽章 PASS |
| **失败查看** | 同 2.1 |

---

## 3. Card HTTP

### 3.1 前置：确认 Mock 服务器运行

| 项目 | 内容 |
|------|------|
| **操作** | 终端执行 `python test_card_server.py` |
| **预期** | 5/5 tests passed |
| **失败查看** | 检查 server.py 是否已启动、端口 8766 是否被占用 |

### 3.2 输入"北京天气"

| 项目 | 内容 |
|------|------|
| **操作** | Provider 切换到 Card HTTP → URL 填写 `http://10.0.2.2:8766/generate-card`（模拟器）或 `http://{PC-IP}:8766/generate-card`（真机）→ 输入"北京天气" → 点击"生成 UI" |
| **预期** | 渲染 weather_summary 卡片（北京，晴转多云，26°），徽章 PASS。Debug 面板原始输出显示服务端响应含 `debug.matchedIntent=weather_query` |
| **失败查看** | Debug 面板 → 原始输出（是否收到响应）、验证状态徽章、错误信息。如无响应，检查 URL 和网络 |

### 3.3 输入"NBA 比分"

| 项目 | 内容 |
|------|------|
| **操作** | 保持 Card HTTP Provider → 清空输入 → 输入"NBA 比分" → 点击"生成 UI" |
| **预期** | 渲染 sports_score_list 卡片（3 场赛事），徽章 PASS。Debug 面板原始输出显示 `debug.matchedIntent=sports_query` |
| **失败查看** | 同 3.2 |

### 3.4 输入未知文本

| 项目 | 内容 |
|------|------|
| **操作** | 保持 Card HTTP Provider → 清空输入 → 输入"今天吃什么" → 点击"生成 UI" |
| **预期** | App 显示错误提示（Toast），**界面不空白**。Debug 面板原始输出显示 HTTP 400 响应，含 `debug.matchedIntent=unknown`。当前渲染区域保留上次成功渲染结果或显示 fallback |
| **失败查看** | Debug 面板 → 原始输出（确认 400 响应内容）；如果界面空白，说明 HTTP 错误处理有缺陷 |

---

## 4. Provider 切换

### 4.1 Card HTTP → Mock

| 项目 | 内容 |
|------|------|
| **操作** | 当前在 Card HTTP（URL 输入框可见）→ Provider 切换到 Mock → 观察界面 |
| **预期** | URL 输入框消失，输入框恢复单行模式。点击"生成 UI"使用 Mock 逻辑正常工作 |
| **失败查看** | 如果 URL 输入框仍可见，说明 `switchProvider()` 未隐藏 `etCardHttpUrl` |

### 4.2 Card HTTP → Fixture

| 项目 | 内容 |
|------|------|
| **操作** | 切回 Card HTTP → 再切换到 Fixture → 观察界面 |
| **预期** | URL 输入框消失，出现 Fixture 下拉框 |
| **失败查看** | 同 4.1 |

### 4.3 Card HTTP → Card JSON

| 项目 | 内容 |
|------|------|
| **操作** | 切回 Card HTTP → 再切换到 Card JSON → 观察界面 |
| **预期** | URL 输入框消失，输入框变为多行模式，提示文字变为"粘贴 CardData JSON" |
| **失败查看** | 同 4.1 |

### 4.4 Card HTTP → LLM

| 项目 | 内容 |
|------|------|
| **操作** | 切回 Card HTTP → 再切换到 LLM → 观察界面 |
| **预期** | Card HTTP 的 URL 输入框消失，出现 LLM URL 输入框和 Proxy token 输入框。两个 URL 输入框不应同时可见 |
| **失败查看** | 如果同时出现两个 URL 输入框或 Card HTTP URL 残留，说明 `switchProvider()` 隐藏逻辑有遗漏 |

### 4.5 逐一切回验证

| 项目 | 内容 |
|------|------|
| **操作** | 依次从 Card HTTP 切换到 Card Fixture → Card JSON → LLM → Mock，每次切换后观察界面 |
| **预期** | 每次切换后，仅当前 Provider 需要的控件可见：Card Fixture 显示下拉框；Card JSON 显示多行输入框；LLM 显示 URL + Proxy token 输入框；Mock 无额外控件。**URL 输入框不残留** |
| **失败查看** | 逐个截图对比，确认没有控件残留 |

---

## 5. 网络异常

### 5.1 Server 未启动

| 项目 | 内容 |
|------|------|
| **操作** | 停止 Mock CardData 服务器（Ctrl+C）→ Provider 切换到 Card HTTP → URL 填写 `http://10.0.2.2:8766/generate-card` → 输入"北京天气" → 点击"生成 UI" |
| **预期** | App 显示错误 Toast（连接失败相关提示），**界面不空白、不 crash**。按钮恢复可点击状态 |
| **失败查看** | 如果 App crash，检查 `handleCardHttpGenerate()` 中异常处理是否完整；如果按钮卡在"请求中..."，检查 `finally` 块是否调用了 `setBusy(false)` |

### 5.2 错误 URL

| 项目 | 内容 |
|------|------|
| **操作** | 保持 server 未启动 → URL 填写 `http://10.0.2.2:9999/not-exist` → 输入"北京天气" → 点击"生成 UI" |
| **预期** | App 显示错误 Toast，界面不空白不 crash，按钮恢复可点击 |
| **失败查看** | 同 5.1 |

---

## 6. LLM 模式回归

> 以下测试需要本地 LLM 代理服务器运行（`tools/llm-proxy/server.py`），如不可用则标记为 N/A。

### 6.1 非 SSE 模式

| 项目 | 内容 |
|------|------|
| **操作** | 启动 `tools/llm-proxy/server.py` → Provider 切换到 LLM → URL 填写 `http://10.0.2.2:8787/generate-ui` → 关闭 Stream 开关 → 输入"显示天气卡片" → 点击"生成 UI" |
| **预期** | 按钮变为"生成中..."，请求完成后渲染 UI 或显示校验失败详情（LLM 输出不稳定，PASS 或 FAIL 均可接受）。徽章状态与渲染结果一致 |
| **失败查看** | Debug 面板 → 原始 A2UI JSON（检查 LLM 输出内容）、验证状态徽章、错误信息。如果按钮卡住不恢复，检查请求超时处理 |

### 6.2 SSE 模式

| 项目 | 内容 |
|------|------|
| **操作** | Provider 保持 LLM → 打开 Stream 开关 → 输入"显示天气卡片" → 点击"生成 UI" |
| **预期** | 按钮变为"生成中..."，Debug 面板显示 SSE 状态（connecting → streaming → complete），流结束后归一化并渲染。取消支持：切换 Provider 或退出 Activity 可中断流（当前 setBusy 会禁用生成按钮，再次点击无法取消；如需支持再次点击取消，需另做代码改造） |
| **失败查看** | Debug 面板 → SSE 状态、chunk 计数、累积输出。如果 SSE 状态停留在 connecting，检查 URL 是否为 `/generate-ui-stream` 端点 |

---

## 验收标准

| 类别 | 通过条件 |
|------|----------|
| Card Fixture | 5 项全部渲染正确，徽章 PASS |
| Card JSON | 2 项粘贴后渲染正确，与对应 Fixture 结果一致 |
| Card HTTP | 3 项全部符合预期（weather / sports / unknown 400） |
| Provider 切换 | 4 项均无控件残留 |
| 网络异常 | 2 项均不 crash 不空白 |
| LLM 回归 | 2 项基本可用（PASS 或 FAIL 有明确反馈均可，不 crash 不卡死） |

**阻断级问题**（任一出现则验收不通过）：

- App crash
- 界面空白且无错误提示
- 按钮卡死在"请求中.../生成中..."
- Provider 切换后控件残留导致误操作
