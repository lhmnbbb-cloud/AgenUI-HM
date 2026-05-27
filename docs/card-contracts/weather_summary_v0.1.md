# weather_summary CardData Protocol

| Field | Value |
|-------|-------|
| **cardType** | `weather_summary` |
| **schemaVersion** | `0.1` |
| **Status** | Draft — AI 组 & Android 组联合评审 |
| **Last Updated** | 2026-05-27 |
| **Owner** | Android 组 (CardContractValidator + CardTemplateRenderer + WeatherSummaryTemplate) |

---

## 1. 背景

`weather_summary` 是 AGenUI Demo 中第二个真实业务场景 CardData 类型（第一个为 `sports_score_list`）。它验证"AI 输出结构化业务数据 → Android 本地确定性生成 A2UI → 原生渲染"管线在天气场景的适用性。

典型触发场景：用户说"今天天气怎么样"，AI 组识别意图、调用天气数据 API 获取真实天气、按本协议填充结构化数据，Android 组用固定模板渲染。AI 不参与 UI 生成，温度等业务数据由 API 获取而非模型编造。

参考：`docs/android-ai-dynamic-card-plan.md` 第五步示例。

---

## 2. 协议信息

| 项目 | 值 |
|------|-----|
| cardType | `weather_summary` |
| schemaVersion | `0.1`（当前未嵌入 JSON，仅作文档版本标识） |
| 下游渲染 | `CardTemplateRenderer` → `WeatherSummaryTemplate.render()` → A2UI v0.9 三段消息 |
| 校验器 | `CardContractValidator.validateWeatherSummary()` |
| 归一化 | CardData 不经过 `A2uiMessageNormalizer`，模板输出已是合法 A2UI |
| A2UI 校验 | 模板输出必须通过 `A2uiJsonValidator` |

---

## 3. 字段定义

### 3.1 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `requestId` | string | **必填** | 请求唯一标识，非空。用于 surfaceId 生成 (`card_{requestId}`) 和日志追踪 |
| `cardType` | string | **必填** | 必须为 `"weather_summary"` |
| `title` | string | **必填** | 卡片标题，非空。如 "今日天气" |
| `location` | string | 可选 | 位置名称。如 "上海" |
| `updatedAt` | string | 可选 | 更新时间。如 "14:30 更新" |
| `current` | object | **必填** | 当前天气信息，类型为 JSONObject。允许为空对象 `{}`（产生 warning 但不阻断渲染） |
| `tips` | array | 可选 | 天气提示列表，类型为 JSONArray。建议不超过 5 条 |

### 3.2 current 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `condition` | string | 建议填充 | 天气状况。如 "晴转多云"。缺失产生 warning |
| `temperature` | number \| string | 建议填充 | 当前温度。推荐 number 类型（如 `26`）；支持 string（如 `"26"` / `"26°"`）。缺失产生 warning |
| `high` | number \| string | 可选 | 最高温度。如 `30` |
| `low` | number \| string | 可选 | 最低温度。如 `18` |
| `airQuality` | string | 可选 | 空气质量。如 "良" |
| `humidity` | string | 可选 | 湿度。如 "65%" |
| `wind` | string | 可选 | 风力信息。如 "东南风 3级" |

### 3.3 temperature 格式规则

| temperature 值 | 渲染显示 |
|----------------|----------|
| `26` (number) | `"26°"` |
| `"26"` (string) | `"26°"` |
| `"26°"` (已有度号) | `"26°"`（不重复添加） |
| `null` / 缺失 | 不显示温度文本 |

---

## 4. 位置时间行

`location` 和 `updatedAt` 组合为一行文字，格式规则：

| location | updatedAt | 渲染显示 |
|----------|-----------|----------|
| 非空 | 非空 | `"{location} · {updatedAt}"` |
| 非空 | 空 | `"{location}"` |
| 空 | 非空 | `"{updatedAt}"` |
| 空 | 空 | 不渲染该行 |

---

## 5. 校验规则

---

## 5. 校验规则

### 5.1 错误（阻断渲染，触发 fallback）

| 规则 | 错误信息格式 |
|------|-------------|
| `current` 缺失或不是 JSONObject | `weather_summary: missing 'current'` |
| `tips` 存在但不是 JSONArray | `weather_summary: 'tips' is not a JSON array` |

### 5.2 警告（不阻断渲染，附加在 CardRenderResult.warnings）

| 规则 | 警告信息格式 |
|------|-------------|
| 顶层缺少 `location` | `weather_summary: missing 'location'` |
| current 缺少 `temperature` | `weather_summary: missing 'temperature'` |
| current 缺少 `condition` | `weather_summary: missing 'condition'` |
| tips 数量超过 5 | `weather_summary: tips count {n} exceeds recommended max 5` |

**注意：** `current: {}` 是合法数据，产生 3 个 warning（缺少 location/temperature/condition）但不触发 fallback。这与 `sports_score_list` 的 `items: []`（无 warning）不同——天气数据本质需要内容才有用，而空赛程是合法状态。

---

## 6. 渲染规则

### 6.1 完整数据时的 A2UI 组件树

```
Column(root) [padding=20px, gap=16px, bg=#F5F5F5, align=stretch]
  → Text(title-text) [variant=h2, bold, text-align=left, text={title}]
  → Text(location-time-text) [variant=body, color=#00000099, text-align=left, text={location}·{updatedAt}]  ← 仅当 location 或 updatedAt 非空
  → Card(weather-card) [padding=24px, border-radius=16px, bg=#FFFFFF]
    → Column(weather-content) [gap=12px, align=stretch]
      → Row(primary-row) [gap=8px]
        → Text(condition-text) [variant=body, text-align=left, text={condition}]
        → Text(temperature-text) [variant=h2, bold, text-align=left, text={temperature}°]
      → Row(highlow-row) [gap=16px]  ← 仅当 high 或 low 非空
        → Text(high-text) [variant=body, color=#00000099, text="最高 {high}°"]
        → Text(low-text) [variant=body, color=#00000099, text="最低 {low}°"]
      → Row(detail-row) [gap=16px]  ← 仅当 airQuality/humidity/wind 至少一个非空
        → Text(airquality-text) [variant=body, color=#00000099, text="空气质量: {airQuality}"]  ← 仅当 airQuality 非空
        → Text(humidity-text) [variant=body, color=#00000099, text="湿度: {humidity}"]  ← 仅当 humidity 非空
        → Text(wind-text) [variant=body, color=#00000099, text="风力: {wind}"]  ← 仅当 wind 非空
      → Column(tips-col) [gap=4px, align=stretch]  ← 仅当 tips 非空数组
        → Text(tips-item_0) [variant=body, color=#00000099, text-align=left, text="• {tip}"]
        → Text(tips-item_1) ...
```

**组件 ID 命名规则：** 固定 ID（非数组循环），所有字段名与组件 ID 一一对应。

**组件数量：**
- 完整数据（2 tips）：18 个（root + title + location-time + card + content + primary-row + condition + temperature + highlow-row + high + low + detail-row + airquality + humidity + wind + tips-col + tip0 + tip1）
- 最小数据（current:{} only）：6 个（root + title + card + content + primary-row + condition）

### 6.2 条件渲染逻辑

| 组件 | 条件 |
|------|------|
| `location-time-text` | `formatLocationTime(location, updatedAt)` 返回非空 |
| `highlow-row` | `high` 或 `low` 至少一个非空 |
| `high-text` | `high` 非空 |
| `low-text` | `low` 非空 |
| `detail-row` | `airQuality` 或 `humidity` 或 `wind` 至少一个非空 |
| `airquality-text` | `airQuality` 非空 |
| `humidity-text` | `humidity` 非空 |
| `wind-text` | `wind` 非空 |
| `tips-col` | `tips` 为非空 JSONArray |

**特殊情况：当 condition 和 temperature 同时为空时**
- primary-row 仅包含 condition-text
- condition-text 显示 "暂无天气数据"（灰色）
- temperature-text 不渲染

### 6.3 格式化逻辑

**formatTemperature(current)：**
```
current 为 null / temperature 缺失 / temperature 为 null → ""
temperature 为 Number → "{intValue}°"
temperature 为 String:
  空字符串 → ""
  已有 "°" 后缀 → 原样返回
  其他 → 追加 "°"
```

**formatLocationTime(location, updatedAt)：**
```
均非空 → "{location} · {updatedAt}"
仅 location → location
仅 updatedAt → updatedAt
均空 → ""
```

### 6.4 A2UI 输出保证

- 模板输出总是 3 条消息：`[createSurface, updateComponents, "{}"]`
- surfaceId 格式：`card_{requestId}`
- 输出始终通过 `A2uiJsonValidator` 校验
- 不经过 `A2uiMessageNormalizer`

---

## 7. JSON 示例

### 7.1 完整数据

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

渲染 18 个组件。**有效，无 warning。**

### 7.2 部分字段

```json
{
  "requestId": "weather_partial",
  "cardType": "weather_summary",
  "title": "天气",
  "current": {
    "condition": "晴",
    "temperature": 28
  }
}
```

渲染 7 个组件（无 location-time、highlow-row、detail-row、tips-col）。**有效，无 warning（location 在顶层而非 current 内，condition/temperature 存在）。**

### 7.3 触发 warning 的场景

```json
{
  "requestId": "weather_warning",
  "cardType": "weather_summary",
  "title": "天气",
  "current": {
    "condition": "多云",
    "high": 25,
    "low": 15
  },
  "tips": ["带伞", "注意防晒", "多喝水", "减少外出", "关好窗户", "注意路况"]
}
```

Warning 列表：
- `weather_summary: missing 'location'`（顶层 location 缺失）
- `weather_summary: missing 'temperature'`
- `weather_summary: tips count 6 exceeds recommended max 5`

**有效，仍正常渲染，不触发 fallback。**

### 7.4 最小数据

```json
{
  "requestId": "weather_minimal",
  "cardType": "weather_summary",
  "title": "天气",
  "current": {}
}
```

渲染 6 个组件（root + title + card + content + primary-row + condition），condition-text 显示 "暂无天气数据"，不渲染 temperature-text。**有效，3 个 warning（缺少 location/temperature/condition）。**

---

## 8. Android 集成

### 8.1 代码位置

| 文件 | 路径 | 职责 |
|------|------|------|
| `CardType.java` | `app/src/main/java/.../card/CardType.java` | 枚举 `WEATHER_SUMMARY("weather_summary")` |
| `CardContractValidator.java` | `app/src/main/java/.../card/CardContractValidator.java` | `validateWeatherSummary()` |
| `CardTemplateRenderer.java` | `app/src/main/java/.../card/CardTemplateRenderer.java` | 统一入口，WEATHER_SUMMARY 委托 `WeatherSummaryTemplate` |
| `WeatherSummaryTemplate.java` | `app/src/main/java/.../card/template/WeatherSummaryTemplate.java` | `render()` + `formatTemperature()` + `formatLocationTime()` |
| `CardRenderResult.java` | `app/src/main/java/.../card/CardRenderResult.java` | valid + messages + errors + warnings |

### 8.2 数据流

```
CardData JSON
  → CardContractValidator.validate()
    → 错误: 渲染 fallback 卡片 (CardRenderResult.valid=false)
    → 有效: 继续
  → CardTemplateRenderer.render() → WeatherSummaryTemplate.render()
    → A2UI v0.9 三段消息 [createSurface, updateComponents, "{}"]
    → CardRenderResult(valid=true, messages, warnings)
  → A2uiJsonValidator.validate() (当前 demo 运行时和单测均校验；正式产品可根据性能策略决定是否保留运行时校验)
  → SurfaceManager → AGenUI 渲染
```

### 8.3 Fixture 数据源

| 文件 | 说明 | 校验结果 |
|------|------|----------|
| `weather_summary_basic.json` | 完整数据，2 tips | valid，无 warning |
| `weather_summary_partial.json` | 仅 condition + temperature | valid，无 warning |
| `weather_summary_warning.json` | 缺 location/temperature，6 tips | valid，3 warnings |
| `weather_summary_minimal.json` | current:{} 仅 | valid，3 warnings |

路径：`app/src/main/assets/card_fixtures/`

### 8.4 测试覆盖

**CardContractValidatorTest（9 个测试）：**

| 测试 | 验证点 |
|------|--------|
| `validate_validWeatherSummary_returnsValid` | 完整数据 → valid |
| `validate_weatherSummaryMissingCurrent_returnsInvalid` | 缺 current → error |
| `validate_weatherSummaryMissingLocation_returnsWarning` | 缺 location → warning |
| `validate_weatherSummaryMissingTemperature_returnsWarning` | 缺 temperature → warning |
| `validate_weatherSummaryMissingCondition_returnsWarning` | 缺 condition → warning |
| `validate_weatherSummaryTipsNotArray_returnsInvalid` | tips 非数组 → error |
| `validate_weatherSummaryTipsOverMax_returnsWarning` | >5 tips → warning |
| `validate_weatherSummaryMinimalCurrent_returnsValidWithWarnings` | current:{} → valid + 3 warnings |
| `validate_weatherSummaryLocationInCurrentNotTopLevel_stillWarns` | location 在 current 中而非顶层 → warning |

**CardTemplateRendererTest（5 个测试）：**

| 测试 | 验证点 |
|------|--------|
| `render_weatherSummary_returnsValidA2ui` | 完整数据通过 A2uiJsonValidator，18 组件 |
| `render_weatherSummaryPartial_rendersGracefully` | 部分数据正常渲染，7 组件 |
| `render_weatherSummaryWithWarnings_propagatesWarnings` | warning 传递到 CardRenderResult |
| `render_weatherSummaryMinimalData_showsPlaceholder` | current:{} 渲染占位文本，6 组件 |
| `render_weatherSummaryHighLowStringWithDegree_noDuplicate` | high/low 带 ° 不重复显示 |

**WeatherSummaryTemplateTest（17 个测试）：**

| 测试 | 验证点 |
|------|--------|
| `render_basic_returnsThreeMessages` | 3 消息输出，surfaceId 格式，通过 A2uiJsonValidator |
| `render_basic_containsAllSections` | 所有区段内容正确 |
| `render_partialData_rendersGracefully` | 部分数据 7 组件，无可选区段 |
| `render_noLocationNoTime_skipsLocationText` | 无 location-time-text 组件 |
| `formatTemperature_number_returnsWithDegree` | 26 → "26°" |
| `formatTemperature_string_returnsWithDegree` | "26" → "26°" |
| `formatTemperature_stringAlreadyWithDegree_returnsAsIs` | "26°" → "26°" |
| `formatTemperature_null_returnsEmpty` | null/缺失 → "" |
| `formatTemperature_emptyString_returnsEmpty` | 空字符串 → "" |
| `formatDegreeField_number_returnsWithDegree` | 30 → "30°" |
| `formatDegreeField_string_returnsWithDegree` | "30" → "30°" |
| `formatDegreeField_stringAlreadyWithDegree_returnsAsIs` | "30°" → "30°" |
| `formatDegreeField_missing_returnsEmpty` | 缺失 → "" |
| `render_minimalCurrent_showsPlaceholder` | current:{} 显示占位文本 |
| `formatLocationTime_both_returnsCombined` | "上海" + "14:30" → "上海 · 14:30" |
| `formatLocationTime_onlyLocation_returnsLocation` | "上海" + "" → "上海" |
| `formatLocationTime_onlyTime_returnsTime` | "" + "14:30" → "14:30" |
| `formatLocationTime_bothEmpty_returnsEmpty` | "" + "" → "" |

---

## 9. AI 组对接要求

### 9.1 必须遵守

1. **cardType 必须为 `"weather_summary"`**，不能使用别名或变体。
2. **requestId 必须非空**，用于追踪和排障。建议格式：`{业务前缀}_{唯一ID}`。
3. **title 必须非空**，建议格式：`"今日天气"` 或 `"{城市} 天气"`。
4. **current 必须为 JSONObject**，不能省略。即使无数据也应传 `{}`（会产生 warning 但不阻断渲染）。
5. **temperature 推荐使用 number 类型**（`26`），不要用字符串 `"26"`。null 和缺失被视为无效。
6. **温度数据必须来自真实 API**，不能由模型编造。
7. **tips 必须为 JSONArray**（如果提供）。建议不超过 5 条。

### 9.2 建议遵循

1. 提供 condition、temperature 字段（在 current 内）和 location 字段（顶层），避免 warning。
2. 提供 high/low 增强信息密度。
3. 提供 airQuality/humidity/wind 增强天气细节。
4. 顶层 location 和 updatedAt 字段帮助用户定位天气信息。
5. tips 条数不超过 5 条，超过产生 warning。

### 9.3 禁止事项

1. **禁止由模型编造温度等数据**。所有数值必须来自业务数据 API。
2. **禁止在 CardData 中嵌套 A2UI 协议片段**。CardData 是业务语义，不是 UI 描述。
3. **禁止在 CardData 中包含 action / functionCall**。交互能力由 Android 侧模板控制。
4. **禁止传递 HTML / Markdown 等富文本**。所有文本字段为纯文本。

### 9.4 AI 输出检查清单

```
□ cardType = "weather_summary"
□ requestId 非空
□ title 非空
□ current 为 JSONObject（不允许缺失）
□ current 包含 condition
□ current 包含 temperature (推荐 number)
□ current 包含 high / low（建议）
□ current 包含 airQuality / humidity / wind（建议）
□ 顶层 location 非空（建议）
□ tips 为 JSONArray，不超过 5 条
□ 温度等数据来自真实 API，非模型编造
□ 无 A2UI 协议片段混入
□ 无 action / functionCall
□ 无 HTML / Markdown
```

---

## 10. 版本演进

### v0.1（当前）

- 支持 condition + temperature 核心展示
- 支持 high/low 高低温显示
- 支持 airQuality/humidity/wind 细节行
- 支持 tips 天气提示列表
- 支持 location + updatedAt 位置时间行
- 条件渲染：缺少可选字段时省略对应组件

### 未来可能演进方向

| 方向 | 说明 | 兼容性影响 |
|------|------|-----------|
| 逐小时预报 | 新增 `hourly` 数组字段 | 需新增字段，不破坏现有 |
| 多日预报 | 新增 `daily` 数组字段 | 需新增字段，不破坏现有 |
| 天气图标 | current 新增 `icon` 字段 | 向后兼容 |
| 交互能力 | 点击卡片跳转天气详情 | 需 CardData 新增 action 字段 + Android 模板更新 |
| 预警信息 | 新增 `alerts` 数组字段 | 向后兼容 |
| 实时更新 | CardData 推送增量 | 需设计增量协议 |
| schemaVersion 嵌入 JSON | 在 CardData 中加入 `"schemaVersion": "0.1"` | 向后兼容（当前校验器忽略该字段） |

**版本管理原则：**

- 新增可选字段：小版本升级（0.1 → 0.2），向后兼容。
- 修改必填字段或删除字段：大版本升级（0.1 → 1.0），需双方重新对齐。
- schemaVersion 一旦嵌入 JSON，CardContractValidator 应根据版本号选择对应校验逻辑。