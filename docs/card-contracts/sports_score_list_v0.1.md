# sports_score_list CardData Protocol

| Field | Value |
|-------|-------|
| **cardType** | `sports_score_list` |
| **schemaVersion** | `0.1` |
| **Status** | Draft — AI 组 & Android 组联合评审 |
| **Last Updated** | 2026-05-26 |
| **Owner** | Android 组 (CardContractValidator + CardTemplateRenderer + SportsScoreListTemplate) |

---

## 1. 背景

`sports_score_list` 是 AGenUI Demo 中第一个真实业务场景 CardData 类型。它验证"AI 输出结构化业务数据 → Android 本地确定性生成 A2UI → 原生渲染"完整管线。

典型触发场景：用户说"今日 NBA 赛况"，AI 组识别意图、调用体育数据 API 获取真实赛况、按本协议填充结构化数据，Android 组用固定模板渲染。AI 不参与 UI 生成，比分等业务数据由 API 获取而非模型编造。

参考：`docs/android-ai-dynamic-card-plan.md` 第五步示例。

---

## 2. 协议信息

| 项目 | 值 |
|------|-----|
| cardType | `sports_score_list` |
| schemaVersion | `0.1`（当前未嵌入 JSON，仅作文档版本标识） |
| 下游渲染 | `CardTemplateRenderer` → `SportsScoreListTemplate.render()` → A2UI v0.9 三段消息 |
| 校验器 | `CardContractValidator.validateSportsScoreList()` |
| 归一化 | CardData 不经过 `A2uiMessageNormalizer`，模板输出已是合法 A2UI |
| A2UI 校验 | 模板输出必须通过 `A2uiJsonValidator` |

---

## 3. 字段定义

### 3.1 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `requestId` | string | **必填** | 请求唯一标识，非空。用于 surfaceId 生成 (`card_{requestId}`) 和日志追踪 |
| `cardType` | string | **必填** | 必须为 `"sports_score_list"` |
| `title` | string | **必填** | 卡片标题，非空。如 "NBA 今日赛况" |
| `subtitle` | string | 可选 | 副标题。如 "2026-05-26" |
| `updatedAt` | string | 可选 | 更新时间。如 "14:30 更新" |
| `league` | string | 可选 | 联赛标识。如 `"NBA"` / `"CBA"`。当前 Android 模板暂不使用，但建议 AI 组填充，方便后续多联赛扩展 |
| `items` | array | **必填** | 赛况列表，类型为 JSONArray。**允许为空数组**（渲染空状态，非 fallback） |

### 3.2 items[] 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `homeTeam` | object | **必填** | 主队信息，必须包含 `name` |
| `awayTeam` | object | **必填** | 客队信息，必须包含 `name` |
| `status` | string | 可选 | 比赛状态：`"final"` / `"live"` / `"scheduled"`。未知值不阻断渲染 |
| `gameId` | string | 可选 | 比赛唯一标识。当前未使用，预留 |
| `startTime` | string | 可选 | 开赛时间。如 `"19:00"` |
| `summary` | string | 可选 | 比赛摘要。如 "湖人终结连败" |

### 3.3 homeTeam / awayTeam 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | **必填** | 队伍名称，非空。如 "湖人" |
| `score` | number \| string | 可选 | 分数。推荐 number 类型（如 `108`）；null、空字符串、缺失均视为无分数 |

**score 字段特殊规则：**

| score 值 | status=final/live | status=scheduled |
|----------|-------------------|------------------|
| `108` (number) | 显示 `"108"` | 显示 `"108"` |
| `"108"` (string) | 显示 `"108"` | 显示 `"108"` |
| `null` | 显示 `"--"` + warning | 显示 `"-"` |
| `""` (空字符串) | 显示 `"--"` + warning | 显示 `"-"` |
| 缺失 | 显示 `"--"` + warning | 显示 `"-"` |

---

## 4. status 枚举

| 值 | 含义 | 渲染显示 | 有分数? | 无分数? |
|----|------|----------|---------|---------|
| `final` | 已结束 | `"FINAL"` | 显示分数 | warning + `"--"` |
| `live` | 进行中 | `"LIVE ●"` | 显示分数 | warning + `"--"` |
| `scheduled` | 未开始 | `"{startTime} 开赛"` / `"待开赛"` | 显示分数 | `"-"`（正常） |
| 其他值 | 未知 | 原样显示 | 显示分数 | warning，视同 scheduled |
| 缺失 | 未知 | `"未知"` | 显示分数 | 无 warning，视同 scheduled |

**status 行为边界：**

- `scheduled` 无分数是正常状态（比赛未开始），不产生 warning。
- `final` / `live` 无分数（null、空字符串、缺失）产生 warning 但不阻断渲染。
- 未知非空 status 值产生 warning 但不阻断渲染，UI 显示原始值。
- 缺失 status 不产生 warning（代码仅对非空且不在枚举内的 status warning），UI 显示 `"未知"`。

---

## 5. 校验规则

### 5.1 错误（阻断渲染，触发 fallback）

| 规则 | 错误信息格式 |
|------|-------------|
| `items` 缺失（不是 JSONArray） | `sports_score_list: missing 'items'` |
| items[i] 不是 JSONObject | `sports_score_list: items[{i}] is not a JSON object` |
| items[i] 缺少 `homeTeam` | `sports_score_list: items[{i}] missing 'homeTeam'` |
| items[i].homeTeam.name 为空 | `sports_score_list: items[{i}] homeTeam.name is empty` |
| items[i] 缺少 `awayTeam` | `sports_score_list: items[{i}] missing 'awayTeam'` |
| items[i].awayTeam.name 为空 | `sports_score_list: items[{i}] awayTeam.name is empty` |

### 5.2 警告（不阻断渲染，附加在 CardRenderResult.warnings）

| 规则 | 警告信息格式 |
|------|-------------|
| items[i].status 不在已知值中 | `sports_score_list: items[{i}] unknown status '{status}'` |
| status=final/live 时 homeTeam 无有效分数 | `sports_score_list: items[{i}] final game missing homeTeam.score` |
| status=final/live 时 awayTeam 无有效分数 | `sports_score_list: items[{i}] final game missing awayTeam.score` |
| items 数量超过 20 | `sports_score_list: items count {n} exceeds recommended max 20` |

**有效分数判定逻辑**（`hasEffectiveScore`）：

```
score 字段缺失 → 无效
score 为 null (JSONObject.NULL) → 无效
score 为空字符串 "" → 无效
score 为 Number → 有效
score 为非空 String → 有效
```

### 5.3 空状态

`items: []` 是合法数据，**不产生任何错误或警告**，渲染空状态卡片（"今日暂无赛况"），不触发 fallback。

---

## 6. 渲染规则

### 6.1 有赛况时的 A2UI 组件树

```
Column(root) [padding=20px, gap=16px, bg=#F5F5F5, align=stretch]
  → Text(title-text) [variant=h2, bold, text-align=left, text={title}]
  → Text(subtitle-text) [variant=body, color=#00000099, text={subtitle}·{updatedAt}]  ← 仅当 subtitle 或 updatedAt 非空
  → Card(game_card_0) [padding=16px, border-radius=12px, bg=#FFFFFF]
    → Column(game_col_0) [gap=8px, align=stretch]
      → Row(status_row_0) [gap=8px]
        → Text(status_text_0) [variant=body, bold, text={statusDisplay}]
        → Text(summary_text_0) [variant=body, color=#00000099, text={summary}]  ← 仅当 summary 非空
      → Row(score_row_0) [gap=0px, align=stretch]
        → Column(away_col_0) [gap=2px, align=stretch]
          → Text(away_name_0) [variant=body, bold, text-align=left, text={awayTeam.name}]
          → Text(away_score_0) [variant=h2, text-align=left, text={scoreDisplay}]
        → Column(home_col_0) [gap=2px, align=stretch]
          → Text(home_name_0) [variant=body, bold, text-align=left, text={homeTeam.name}]
          → Text(home_score_0) [variant=h2, text-align=left, text={scoreDisplay}]
  → Card(game_card_1) ...
```

**组件 ID 命名规则：** `game_card_{i}`, `game_col_{i}`, `status_row_{i}`, `status_text_{i}`, `summary_text_{i}`, `score_row_{i}`, `away_col_{i}`, `away_name_{i}`, `away_score_{i}`, `home_col_{i}`, `home_name_{i}`, `home_score_{i}`

**每场赛事组件数：**
- 有 summary：12 个（card + col + statusRow + statusText + summaryText + scoreRow + awayCol + awayName + awayScore + homeCol + homeName + homeScore）
- 无 summary：11 个（无 summaryText）

**总组件数公式：** `3 + Σ(game_i 组件数)`，其中 3 = root + title + [subtitle]

### 6.2 空赛况时的 A2UI 组件树

```
Column(root) [padding=20px, gap=16px, bg=#F5F5F5, align=stretch]
  → Text(title-text) [variant=h2, bold, text-align=left, text={title}]
  → Card(empty-card) [padding=24px, border-radius=16px, bg=#FFFFFF]
    → Column(empty-content) [align=stretch, gap=8px]
      → Text(empty-msg) [variant=body, text-align=center, color=#00000099, text="今日暂无赛况"]
```

固定 5 个组件（root + title + empty-card + empty-content + empty-msg）。

### 6.3 分数显示逻辑

```
formatScore(team, status):
  if team 为 null / score 缺失 / score 为 null:
    return (final/live) ? "--" : "-"
  if score 是 Number:
    return score.intValue().toString()
  if score.toString() 为空字符串:
    return (final/live) ? "--" : "-"
  return score.toString()
```

### 6.4 状态显示逻辑

```
formatStatus(status, startTime):
  "final"     → "FINAL"
  "live"      → "LIVE ●"
  "scheduled" → startTime 非空 ? "{startTime} 开赛" : "待开赛"
  其他非空值   → 原样显示
  空值        → "未知"
```

### 6.5 副标题行

仅当 `subtitle` 或 `updatedAt` 至少一个非空时渲染。格式：`{subtitle} · {updatedAt}`，空字段省略对应部分和分隔符。

### 6.6 A2UI 输出保证

- 模板输出总是 3 条消息：`[createSurface, updateComponents, "{}"]`
- surfaceId 格式：`card_{requestId}`
- 输出始终通过 `A2uiJsonValidator` 校验
- 不经过 `A2uiMessageNormalizer`

---

## 7. JSON 示例

### 7.1 完整赛况（3 场：final + live + scheduled）

```json
{
  "requestId": "nba_basic",
  "cardType": "sports_score_list",
  "title": "NBA 今日赛况",
  "subtitle": "2026-05-26",
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

### 7.2 空赛况

```json
{
  "requestId": "nba_empty",
  "cardType": "sports_score_list",
  "title": "NBA 今日赛况",
  "items": []
}
```

渲染结果：显示 "今日暂无赛况" 空状态。**合法，不触发 fallback。**

### 7.3 部分字段（缺少可选字段）

```json
{
  "requestId": "nba_partial",
  "cardType": "sports_score_list",
  "title": "NBA 今日赛况",
  "items": [
    {
      "status": "final",
      "startTime": "09:00",
      "homeTeam": { "name": "湖人", "score": 108 },
      "awayTeam": { "name": "凯尔特人", "score": 102 }
    },
    {
      "status": "scheduled",
      "homeTeam": { "name": "雄鹿" },
      "awayTeam": { "name": "76人" }
    }
  ]
}
```

无 subtitle、无 updatedAt、无 summary。scheduled 场次无 startTime → 显示 "待开赛"。**有效，无 warning。**

### 7.4 触发 warning 的场景

```json
{
  "requestId": "nba_warning",
  "cardType": "sports_score_list",
  "title": "NBA 今日赛况",
  "items": [
    {
      "gameId": "g001",
      "status": "postponed",
      "homeTeam": { "name": "湖人" },
      "awayTeam": { "name": "凯尔特人" }
    },
    {
      "gameId": "g002",
      "status": "final",
      "startTime": "09:00",
      "homeTeam": { "name": "勇士" },
      "awayTeam": { "name": "掘金" }
    }
  ]
}
```

Warning 列表：
- `items[0] unknown status 'postponed'`
- `items[1] final game missing homeTeam.score`
- `items[1] final game missing awayTeam.score`

**有效，仍正常渲染，不触发 fallback。**

### 7.5 score 为 null / 空字符串

```json
{
  "requestId": "nba_null_score",
  "cardType": "sports_score_list",
  "title": "NBA",
  "items": [
    {
      "status": "final",
      "homeTeam": { "name": "湖人", "score": null },
      "awayTeam": { "name": "凯尔特人", "score": "" }
    }
  ]
}
```

Warning：`items[0] final game missing homeTeam.score`、`items[0] final game missing awayTeam.score`。UI 显示 "--"。**有效。**

---

## 8. Android 集成

### 8.1 代码位置

| 文件 | 路径 | 职责 |
|------|------|------|
| `CardType.java` | `app/src/main/java/.../card/CardType.java` | 枚举 `SPORTS_SCORE_LIST("sports_score_list")` |
| `CardContractValidator.java` | `app/src/main/java/.../card/CardContractValidator.java` | `validateSportsScoreList()` + `hasEffectiveScore()` |
| `CardTemplateRenderer.java` | `app/src/main/java/.../card/CardTemplateRenderer.java` | 统一入口，SPORTS_SCORE_LIST 委托 `SportsScoreListTemplate` |
| `SportsScoreListTemplate.java` | `app/src/main/java/.../card/template/SportsScoreListTemplate.java` | `render()` + `formatScore()` + `formatStatus()` |
| `CardRenderResult.java` | `app/src/main/java/.../card/CardRenderResult.java` | valid + messages + errors + warnings |

### 8.2 数据流

```
CardData JSON
  → CardContractValidator.validate()
    → 错误: 渲染 fallback 卡片 (CardRenderResult.valid=false)
    → 有效: 继续
  → CardTemplateRenderer.render() → SportsScoreListTemplate.render()
    → A2UI v0.9 三段消息 [createSurface, updateComponents, "{}"]
    → CardRenderResult(valid=true, messages, warnings)
  → A2uiJsonValidator.validate() (当前 demo 运行时和单测均校验；正式产品可根据性能策略决定是否保留运行时校验)
  → SurfaceManager → AGenUI 渲染
```

### 8.3 Fixture 数据源

| 文件 | 说明 | 校验结果 |
|------|------|----------|
| `sports_score_list_basic.json` | 3 场赛事 | valid |
| `sports_score_list_empty.json` | 空赛况 | valid，空状态 |
| `sports_score_list_partial.json` | 缺少可选字段 | valid，无 warning |
| `sports_score_list_warning.json` | unknown status + final 缺 score | valid，有 warning |

路径：`app/src/main/assets/card_fixtures/`

### 8.4 测试覆盖

**CardContractValidatorTest（8 个测试）：**

| 测试 | 验证点 |
|------|--------|
| `validate_validSportsScoreList_returnsValid` | 完整数据 → valid |
| `validate_sportsScoreListEmptyItems_returnsValid` | 空列表 → valid |
| `validate_sportsScoreListMissingHomeTeamName_returnsInvalid` | 缺 homeTeam.name → error |
| `validate_sportsScoreListMissingAwayTeamName_returnsInvalid` | 缺 awayTeam.name → error |
| `validate_sportsScoreListUnknownStatus_returnsWarningButValid` | 未知 status → warning |
| `validate_sportsScoreListFinalMissingScore_returnsWarningButValid` | final 缺 score → warning |
| `validate_sportsScoreListOverMaxItems_returnsWarningButValid` | >20 items → warning |
| `validate_sportsScoreListNullScore_returnsWarningButValid` | score=null / score="" → warning |

**CardTemplateRendererTest（5 个测试）：**

| 测试 | 验证点 |
|------|--------|
| `render_sportsScoreList_returnsValidA2ui` | 渲染结果通过 A2uiJsonValidator，组件数/ID 正确 |
| `render_sportsScoreListEmptyItems_returnsEmptyState` | 空列表渲染空状态 |
| `render_sportsScoreListWithWarnings_propagatesWarnings` | warning 传递到 CardRenderResult |
| `render_sportsScoreListPartialFields_rendersGracefully` | 缺可选字段正常渲染 |
| `render_sportsScoreListNullAndEmptyScore_showsDash` | null/空 score → "--" |

**SportsScoreListTemplateTest（12 个测试）：**

| 测试 | 验证点 |
|------|--------|
| `render_basic_returnsThreeMessages` | 3 消息输出，surfaceId 格式，通过 A2uiJsonValidator |
| `render_emptyItems_rendersEmptyState` | 空列表渲染 5 组件，"今日暂无赛况" |
| `formatScore_number_returnsIntString` | 108 → "108" |
| `formatScore_null_final_returnsDashDash` | null + final → "--" |
| `formatScore_null_scheduled_returnsSingleDash` | null + scheduled → "-" |
| `formatScore_emptyString_final_returnsDashDash` | "" + final → "--" |
| `formatStatus_final` | "FINAL" |
| `formatStatus_live` | "LIVE ●" |
| `formatStatus_scheduled_withStartTime` | "19:00 开赛" |
| `formatStatus_scheduled_noStartTime` | "待开赛" |
| `formatStatus_unknown` | null/"" → "未知" |
| `formatStatus_unknownValue_passesThrough` | "postponed" → "postponed" |

---

## 9. AI 组对接要求

### 9.1 必须遵守

1. **cardType 必须为 `"sports_score_list"`**，不能使用别名或变体。
2. **requestId 必须非空**，用于追踪和排障。建议格式：`{业务前缀}_{唯一ID}`（如 `nba_20260526_001`）。
3. **title 必须非空**，建议格式：`"{联赛} 今日赛况"` 或 `"{联赛} 赛况"`。
4. **items 是 JSONArray，允许为空**。无赛况时传 `[]`，不要省略 `items` 字段。
5. **每个 item 的 homeTeam.name 和 awayTeam.name 必须非空**。缺失会被 Android 校验拒绝。
6. **score 推荐使用 number 类型**（`108`），不要用字符串 `"108"`。null 和空字符串被视为无效分数。
7. **比分数据必须来自真实 API**，不能由模型编造。
8. **status 使用已知值**：`"final"` / `"live"` / `"scheduled"`。其他值不会阻断渲染但会产生 warning。

### 9.2 建议遵循

1. 为 final / live 状态的场次提供 score，否则 UI 显示 "--" 并产生 warning。
2. 为 scheduled 状态的场次提供 startTime，否则 UI 显示 "待开赛"。
3. 提供 subtitle 和 updatedAt 字段，增强卡片信息密度。
4. 单次请求 items 不超过 20 条，超过产生 warning。
5. gameId 字段预留，当前未使用但建议填充以支持未来功能。

### 9.3 禁止事项

1. **禁止由模型编造比分**。score 必须来自业务数据 API。
2. **禁止在 CardData 中嵌套 A2UI 协议片段**。CardData 是业务语义，不是 UI 描述。
3. **禁止在 CardData 中包含 action / functionCall**。交互能力由 Android 侧模板控制。
4. **禁止传递 HTML / Markdown 等富文本**。所有文本字段为纯文本。

### 9.4 AI 输出检查清单

```
□ cardType = "sports_score_list"
□ requestId 非空
□ title 非空
□ items 为 JSONArray（允许 []）
□ 每个 item:
  □ homeTeam.name 非空
  □ awayTeam.name 非空
  □ status 为 final / live / scheduled
  □ final/live 状态有 score (number)
  □ scheduled 状态有 startTime
□ score 来自真实 API，非模型编造
□ 无 A2UI 协议片段混入
□ 无 action / functionCall
□ 无 HTML / Markdown
```

---

## 10. 版本演进

### v0.1（当前）

- 支持 final / live / scheduled 三种状态
- 支持 homeTeam / awayTeam 的 name + score
- 空赛况渲染空状态
- 单卡片标题 + 副标题 + 赛事列表
- 比赛摘要 (summary) 和开赛时间 (startTime) 为可选字段
- gameId 预留未使用
- league 字段可选，当前模板未使用，建议 AI 组填充

### 未来可能演进方向

| 方向 | 说明 | 兼容性影响 |
|------|------|-----------|
| 新增 status 值 | 如 `postponed` / `cancelled` / `halftime` | 向后兼容（当前未知 status 只 warning） |
| score 类型扩展 | 如 `quarter_scores: [28, 25, 30, 25]` | 需新增字段，不破坏现有 |
| 交互能力 | 点击比赛卡片跳转详情 | 需 CardData 新增 action 字段 + Android 模板更新 |
| 多联赛模板差异化 | 当前 league 字段已定义但模板未使用，后续可根据 league 值切换不同联赛渲染模板 | 向后兼容 |
| 实时更新 | CardData 推送增量 | 需设计增量协议 |
| schemaVersion 嵌入 JSON | 在 CardData 中加入 `"schemaVersion": "0.1"` | 向后兼容（当前校验器忽略该字段） |
| gameTime 字段 | 替代 startTime，支持完整日期时间 | 可能替代 startTime，需版本管理 |

**版本管理原则：**

- 新增可选字段：小版本升级（0.1 → 0.2），向后兼容。
- 修改必填字段或删除字段：大版本升级（0.1 → 1.0），需双方重新对齐。
- schemaVersion 一旦嵌入 JSON，CardContractValidator 应根据版本号选择对应校验逻辑。
