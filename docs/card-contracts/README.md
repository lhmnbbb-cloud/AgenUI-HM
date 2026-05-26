# CardData 协议索引

本目录包含 AGenUI Demo 中所有 CardData 卡片类型的正式协议文档。

CardData 协议定义 AI 组输出和 Android 组校验/渲染之间的结构化数据契约。每份协议文档由 AI 组和 Android 组共同评审。

## 原则

1. **AI 输出业务语义和结构化数据，Android 输出最终 UI。**
2. CardData 不包含 A2UI 协议片段、action、functionCall 或富文本。
3. 每种 cardType 有独立的校验规则和渲染模板，确定性生成 A2UI。
4. 校验失败触发 fallback 卡片，不允许出现空白 UI。
5. 协议版本采用语义化版本（schemaVersion），新增可选字段小版本升级，修改必填字段大版本升级。

## 协议列表

| cardType | schemaVersion | 文档 | 说明 |
|----------|---------------|------|------|
| `text_summary` | — | 待补充 | 文本摘要卡片 |
| `text_list` | — | 待补充 | 文字列表卡片 |
| `image_text_list` | — | 待补充 | 图文列表卡片 |
| `sports_score_list` | `0.1` | [sports_score_list_v0.1.md](sports_score_list_v0.1.md) | NBA 赛况卡片 |

## 数据流

```
AI 组输出 CardData JSON
  → CardContractValidator (校验卡片协议)
  → CardTemplateRenderer (本地确定性生成 A2UI)
  → A2uiJsonValidator (校验 A2UI 输出)
  → SurfaceManager → AGenUI 渲染
```

CardData 不经过 `A2uiMessageNormalizer`，模板输出已是合法 A2UI。

## 新增 cardType 流程

1. 在 `CardType.java` 新增枚举值。
2. 在 `CardContractValidator.java` 新增校验逻辑。
3. 在 `CardTemplateRenderer.java` 新增渲染模板。
4. 创建 fixture JSON 样例（`app/src/main/assets/card_fixtures/`）。
5. 编写单元测试。
6. 在本目录新增协议文档，双方评审。
7. 更新 `README.zh-CN.md`。
