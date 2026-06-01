# Demo 里程碑文档：公司共通控件 SDK 适配 v0.1

| 字段 | 值 |
|------|-----|
| **里程碑** | 公司共通控件 SDK 适配 v0.1 |
| **日期** | 2026-05-28 |
| **范围** | `demo/android-a2ui-text-demo/` |
| **关联提交** | `7f461c7 demo: adapt common controls sdk` |
| **状态** | 已完成，待评审 |

---

## 1. 目标

在公司车机系统应用场景下，AGenUI 渲染的 UI 控件需要使用公司品牌控件（Melo/Gua），而不是标准 Android View（TextView、CardView、FrameLayout 等）。适配目标是：

1. **A2UI 协议和 CardData 协议不变**：AI 仍然输出标准组件名（Text/Card/Button/Row/Column），适配仅在 Android 渲染层发生
2. **公司 AAR 不提交 Git**：保护知识产权
3. **无编译期直接依赖**：通过反射加载，AAR 不存在时自动 fallback
4. **保证已有卡片布局一致**：适配不能破坏 weather_summary / sports_score_list 等模板的渲染效果

---

## 2. 最终设计

### 2.1 架构

```
A2UI JSON (标准组件名)
  → ComponentRegistry (内置 22 种组件)
  → CommonControlsRegistry.registerIfAvailable() 覆盖部分组件
  → MeloXxxComponent (反射创建 Melo 控件 View)
  → 如果 AAR 不存在 / 反射失败 → 自动 fallback 到标准 View
```

关键设计决策：

| 决策 | 选择 | 原因 |
|------|------|------|
| 依赖方式 | `rootProject.file()` + `if (exists())` | AAR 路径必须相对于项目根解析，`file()` 在 app 模块里会解析成 `app/sdk/` 而不是 `demo/sdk/` |
| 控件创建 | `Class.forName()` 反射 | 无编译期 import，AAR 不存在时构建不受影响 |
| 适配范围 | 仅覆盖 Text/Card/Button | Row/Column 替代会破坏 Flexbox 语义（见第 4 节） |
| 注册时机 | `AGenUI.initialize()` 之后、`SurfaceManager` 创建之前 | `aGenUI.registerComponent()` 通过覆盖 `ComponentRegistry` ConcurrentHashMap 的同名 entry 实现 |

### 2.2 关键文件

| 文件 | 职责 |
|------|------|
| `commonui/CommonControlsViewFactory.java` | 反射工厂：`createTextView()`、`createCardView()`、`createFrameLayout()`。首次调用 `isAvailable()` 缓存 SDK 可用性 |
| `commonui/CommonControlsRegistry.java` | 注册入口：`registerIfAvailable(aGenUI, context)` → 覆盖 Text/Card/Button 三种组件工厂 |
| `commonui/MeloTextComponent.java` | Text 适配：MeloTextView，继承 `A2UIComponent` |
| `commonui/MeloCardComponent.java` | Card 适配：MeloFrameLayout / FrameLayout + GradientDrawable，继承 `A2UILayoutComponent`，含 `applyCardAppearanceIfPresent()` + `applyCardPadding()`（普通 setPadding）。**§5 描述的 `applyCardContentPadding()` / CardView 路径已废弃**，详见 §12 |
| `commonui/MeloButtonComponent.java` | Button 适配：MeloFrameLayout 容器 + StateListDrawable + 私有 `melo-button-*` keys，继承 `A2UILayoutComponent`。详见 §13 |
| `commonui/Melo*ComponentFactory.java` | 各组件的 `IComponentFactory` 实现 |
| `TextDemoActivity.java` | 调用 `CommonControlsRegistry.registerIfAvailable()` 的入口 |
| `app/build.gradle` | `minSdk 28`、条件 AAR 依赖、新增 cardview/preference/recyclerview |
| `.gitignore` | `sdk/*.aar` + `sdk/*.jar` 不提交 |

### 2.3 AAR 保护

- AAR 放在 `demo/android-a2ui-text-demo/sdk/gua-car-ui-lib-release.aar`
- `.gitignore` 排除 `sdk/*.aar` 和 `sdk/*.jar`
- `build.gradle` 通过 `rootProject.file()` + `if (exists())` 条件引用，AAR 不存在时构建仍通过
- 不要在文档中写公司 AAR 的包名、内部类结构、API key、内部 URL

---

## 3. 哪些组件适配，哪些不适配，以及原因

### 已适配（3 种）

| A2UI 组件 | 公司控件 | Fallback | 说明 |
|-----------|---------|----------|------|
| Text | MeloTextView (继承 AppCompatTextView) | TextView | 单一 View 组件，无 child 容器语义问题 |
| Card | MeloFrameLayout + GradientDrawable | FrameLayout + GradientDrawable | ViewGroup 容器，单 child；**当前实现**，曾经的 CardView 方案见 §5（历史） |
| Button | MeloFrameLayout 容器 + StateListDrawable | FrameLayout + hex fallback | A2UI Button 是"容器 + child component"模型；MeloButton/AppCompatButton 是 Button 子类，不是 ViewGroup，无法容纳 child View。视觉走 `mluiButton*` variant（详见 §13） |

### 未适配（2 种，明确不覆盖）

| A2UI 组件 | 原生组件 | 不适配原因 |
|-----------|---------|-----------|
| Row | FlexContainerLayout | LinearLayout 替代会丢失 stretch、gap、flex-wrap、Flexbox 子项权重语义。sports_score_list 和 weather_summary 模板大量使用 Row + `align: stretch` + gap，LinearLayout 无法等价实现 |
| Column | FlexContainerLayout | 同 Row。Column + `gap` + `align: stretch` 是卡片布局的基础，LinearLayout 不支持 |

### 未适配（17 种内置组件，暂无需求）

Image、CheckBox、Slider、TextField、Icon、Divider、ProgressBar、Switch、Container、ScrollView、ListView、GridView、Modal、Header、Footer、TabBar、Select。当前卡片模板不使用这些组件，暂无适配需求。

---

## 4. Button 使用容器而非 MeloButton 的原因

A2UI Button 的组件模型是 **容器 + child component**：

```
Button(id="btn", child="btn-text")
  → MeloFrameLayout 容器
    → child: MeloTextComponent(id="btn-text") 单独渲染后加入容器
```

- MeloButton 继承 AppCompatButton → 继承 Button → 继承 TextView
- Button 是 View，不是 ViewGroup，无法 `addView()` 加入 child
- A2UI 的 Function Call action 通过 `setOnClickListener` 绑定在 Button 容器上
- MeloFrameLayout 是 ViewGroup，可以承载 child 并绑定 click listener

如果未来需要使用 MeloButton 的品牌样式（圆角、渐变背景等），可以考虑：
1. 在 MeloFrameLayout 外层包裹一层样式装饰（如 drawable）
2. 或者把 MeloButton 作为不可交互的样式层叠在 FrameLayout 内

---

## 5. Card padding 问题（历史问题，已废弃 — 仅作背景）

> **当前实现不再走这条路径。** Card backing view 已改为 MeloFrameLayout / FrameLayout + `GradientDrawable` 背景 + 普通 `setPadding`，详见 §12。本节保留是为了解释为什么不再使用 CardView —— 如果你正在调 Card 渲染，不要照本节代码改。

### 5.1 问题现象

启用 MeloCardView 后，weather_summary / sports_score_list 卡片虽然能渲染，但 Card 内部 padding 可能丢失，表现为**内容贴边**。

### 5.2 根因分析

```
A2UI styles: { padding: "20px" }
  → StyleHelper.applySpacing() → view.setPadding(left, top, right, bottom)  ← 普通 View 的 padding API
  → 但 CardView 的内容内边距语义是 setContentPadding()，不是 setPadding()
```

具体链路：

1. `A2UIComponent.applyCommonStyles()` 调用 `StyleHelper.applySpacing(view, styles)`
2. `StyleHelper.applySpacing()` 对所有 View 统一调用 `view.setPadding(left, top, right, bottom)`
3. `CardView.setPadding()` 设置的是 CardView 整体（含 CardBackground）的外边距，不是内容区域的内边距
4. `CardView.setContentPadding()` 才是真正控制内容区域和 Card 边框之间间距的 API
5. 因此 `setPadding(20px)` 被调用后，CardView 内容区域仍然贴边，padding 被吃到 CardView 外层而非内容区

### 5.3 原生 CardComponent 的解决方案

原生 `CardComponent.java` (line 41-46) 通过匿名子类 override `setPadding()`：

```java
cardView = new CardView(context) {
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        setContentPadding(left, top, right, bottom);
    }
};
```

这样 `StyleHelper.applySpacing()` 调 `setPadding()` 时，实际执行的是 `setContentPadding()`。

### 5.4 MeloCardComponent 不能假设 MeloCardView override 了 setPadding

- MeloCardView 继承 CardView，但源码不可见（AAR 内部）
- 反查 AAR 后确认：MeloCardView 没有公开覆盖 `setPadding()`
- 不能对反射创建的控件做匿名子类 override（因为反射创建的是具体类实例，不是匿名类）

### 5.5 最终修复方案

`MeloCardComponent` 在 `createView()` 和 `onUpdateProperties()` 后调用 `applyCardContentPadding()`：

```java
@Override
public View createView(Context context, ViewGroup parent) {
    View view = super.createView(context, parent);  // 触发 StyleHelper.applySpacing → setPadding()
    applyCardContentPadding();                       // 重新解析 styles → setContentPadding()
    return view;
}

@Override
public void onUpdateProperties(Map<String, Object> properties) {
    super.onUpdateProperties(properties);            // 触发 StyleHelper.applySpacing → setPadding()
    applyCardContentPadding();                       // 重新解析 styles → setContentPadding()
}
```

`applyCardContentPadding()` 的逻辑：

1. 检查 `cardView instanceof CardView`（如果不是 CardView，说明 fallback 到了 FrameLayout，无需特殊处理）
2. 从 `this.properties` 中提取 `styles` Map
3. 检查是否包含 padding 相关 key（`padding`、`padding-left`、`padding-right`、`padding-top`、`padding-bottom`、`padding-inline-start/end`、`padding-block-start/end`）
4. 逐一解析 padding 值，使用 `StyleHelper.parseDimension()` 和复制的 CSS shorthand 解析器
5. 调用 `card.setContentPadding(left, top, right, bottom)` 写入内容区 padding

这个方案的特点：

- **不阻止 `StyleHelper.applySpacing()` 执行 `setPadding()`**：super 调用仍然会执行 setPadding，设置 CardView 外层 padding
- **在 super 之后追加 `setContentPadding()`**：用同样的 styles 值设置内容区 padding
- **不删除 styles 中的 padding key**：因为 margin 等 spacing 仍然由 StyleHelper 处理
- **对非 CardView fallback 无影响**：FrameLayout fallback 不执行 applyCardContentPadding

### 5.6 经验总结

**适配公司控件不能只看"能创建 View、能编译、能显示"，还必须确认 A2UI 组件语义一致。**

必须检查的语义维度：

| 语义维度 | Card 实例 | 说明 |
|---------|----------|------|
| child 容器 | CardView 是 ViewGroup，可 addView | 确认公司控件是 ViewGroup 且能容纳 child |
| padding | `setPadding()` ≠ `setContentPadding()` | 公司控件继承 CardView 时，padding API 语义是否一致 |
| background | CardView 有 CardBackground drawable | 公司控件的背景 API 是否兼容 `StyleHelper.applyBackground()` |
| border-radius | CardView 通过 `setRadius()` | 公司控件是否有等价 API |
| click / action | `setOnClickListener()` | 公司控件是否保留了 click listener 能力 |
| elevation | CardView 有 `setCardElevation()` | 公司控件是否兼容 elevation API |

---

## 6. 多轮调试中发现的其他问题

### 6.1 AAR 路径写错（P1）

**问题**：`app/build.gradle` 用 `file('sdk/gua-car-ui-lib-release.aar')`，这个路径在 app 模块里解析成 `app/sdk/` 而不是项目根的 `sdk/`。

**现象**：构建通过，但 `CommonControlsViewFactory.isAvailable()` 返回 false，整个 Melo 适配实际没跑起来。

**修复**：改为 `rootProject.file('sdk/gua-car-ui-lib-release.aar')`。

**经验**：Gradle 的 `file()` 在模块 build.gradle 里相对于模块目录解析，不是项目根目录。对于放在项目根目录下但不在 app 模块内的文件，必须用 `rootProject.file()` 或 `file('../sdk/...')`。

### 6.2 Row/Column 替换成 LinearLayout 后布局异常（P2）

**问题**：第一版尝试覆盖 Row → MeloLinearLayout、Column → MeloLinearLayout。

**现象**：一旦 AAR 路径修正，MeloLinearLayout 替代 FlexContainerLayout，stretch、gap、flex-wrap 等语义全部丢失，卡片布局严重异常。

**修复**：删除 MeloRowComponent / MeloColumnComponent 及其 Factory，不再注册 Row/Column override。

**经验**：FlexboxLayout 和 LinearLayout 是完全不同的布局引擎。A2UI 的 Row/Column 使用 FlexContainerLayout（FlexboxLayout + FrameLayout 包装），支持 `justify-content`、`align-items`、`flex-wrap`、`flex-grow` 等语义。LinearLayout 只支持 `orientation` + `weight`，无法等价替代。

### 6.3 Button 文档残留 MeloLinearLayout 表述（P3）

**问题**：MeloButtonComponent.java class Javadoc 和 README.zh-CN.md 某行仍写"MeloLinearLayout"，但实际代码已改为 MeloFrameLayout。

**修复**：更新文字表述为 MeloFrameLayout，删除未使用的 `LinearLayout` import。

**经验**：修改设计方案后，注释和文档要同步更新，否则容易误导后续维护。

### 6.4 AAR 引入的额外依赖（P1 补充）

**问题**：AAR 合并资源时报错 `attr/preferenceScreenStyle not found`。

**原因**：公司 AAR 依赖了 `preference` 和 `recyclerview`，但 demo app 的 build.gradle 原来没有声明这些依赖。

**修复**：在 `libs.versions.toml` 和 `build.gradle` 中新增 `cardview:1.0.0`、`preference:1.2.1`、`recyclerview:1.3.2`。

**经验**：引入第三方 AAR 时，需要检查其 manifest 合入是否会带来隐式依赖。Android 资源合并机制要求宿主 app 声明 AAR 使用的所有资源依赖。

### 6.5 minSdk 从 21 提升到 28

**原因**：公司 AAR 的 `minSdkVersion=28`。

**影响**：Demo app 从支持 Android 5.0 缩减到 Android 9.0+。如果不需要公司控件，可以手动改回 21。

**注意**：这个变更与 AAR 是否存在无关——即使 AAR 不存在，minSdk 也是 28，因为 build.gradle 里的 `minSdk` 是无条件写死的 28。未来可以考虑用 flavor 或 buildConfig 动态切换。

---

## 7. 以后扩展更多公司控件前的检查清单

在适配下一个公司控件（如 Image → MeloImageView、CheckBox → MeloCheckBox、Slider → MeloSeekBar）之前，必须逐一确认以下维度：

| # | 检查项 | 说明 | Card 实例参考 |
|---|--------|------|-------------|
| 1 | 是否是 ViewGroup | A2UI 容器组件（Card、Button）需要承载 child。非 ViewGroup 控件只能用于非容器组件（Text、Image、Icon） | Button 不能用 MeloButton（非 ViewGroup） |
| 2 | Flexbox 语义是否一致 | Row/Column 依赖 FlexContainerLayout（FlexboxLayout）。LinearLayout 不支持 stretch/gap/flex-wrap | Row/Column 保留原生 FlexContainerLayout |
| 3 | padding API 是否语义一致 | 标准 ViewGroup 用 `setPadding()`；带"内容区/外层"语义分裂的容器（如 CardView 的 `setContentPadding()`）必须额外特判 —— 这是把 A2UI Card backing view 改回 FrameLayout 的主要原因 | 当前 Card → MeloFrameLayout，普通 `setPadding`，详见 §12.1 |
| 4 | background API 是否一致 | 检查公司控件的背景绘制是否兼容 `StyleHelper.applyBackground()`（GradientDrawable / ColorDrawable）；自带内置背景的控件（CardView 的 cardBackgroundColor）会和我们的 GradientDrawable 抢视觉层 | Card 现在用 FrameLayout + GradientDrawable，无内置背景冲突 |
| 5 | border-radius API 是否一致 | 优先用 `GradientDrawable.setCornerRadius()` —— 这是通用方案。控件自带 corner radius API（CardView 的 `setRadius()`）需要清零以免与 GradientDrawable 双层 | Card 用 GradientDrawable 圆角，参见 §12.2 |
| 6 | elevation / shadow API | 默认压成 `filter: "none"`，避免与公司控件内置 elevation 重影 | Card variant fallback `filter: "none"` |
| 7 | action / click / function call | 确认 `setOnClickListener()` 是否仍能正常触发 A2UI Function Call | Button 容器的 click listener |
| 8 | AAR 不存在时 fallback 是否可用 | 反射失败自动 fallback 到标准 View，功能不能降级 | CommonControlsViewFactory fallback 链 |
| 9 | minSdk / manifest / 权限 / 资源冲突 | 公司 AAR 可能引入 minSdk 限制、manifest provider 合并冲突、权限要求、资源命名冲突 | minSdk 28、preference/recyclerview 依赖 |
| 10 | 是否需要真机验证 | assembleDebug 通过不代表渲染正确。padding、background、radius、Flexbox 等必须在真机上视觉确认 | Card padding 贴边问题在 assembleDebug 通过后才暴露 |

---

## 8. 当前限制

1. 公司共通控件适配当前覆盖 3 个 A2UI 组件（Text/Card/Button），Row/Column 保留原生 FlexContainerLayout，其余 17 个标准组件未适配
2. Button 使用 Melo 容器而非 MeloButton（Button 非 ViewGroup 无法容纳 child）
3. minSdk 从 21 提升到 28（因公司 AAR 要求），即使 AAR 不存在 minSdk 也是 28
4. ~~MeloCardView 的 `setPadding()` 语义问题已通过 `applyCardContentPadding()` 修复~~ **（已废弃）** —— Card backing view 已彻底改为 MeloFrameLayout / FrameLayout + `GradientDrawable`，padding 回归普通 `setPadding`，不再需要 `setContentPadding` 特判。详见 §12.1
5. Row/Column 的公司控件适配需要研究"FlexContainerLayout + Melo 外层/内层 wrapper"方案，不能直接用 LinearLayout 替代

---

## 9. 下一阶段建议

1. **真机验证 Card 视觉**：在带公司 AAR 的真机上渲染 weather_summary 和 sports_score_list 卡片，确认 FrameLayout + GradientDrawable 方案（§12）下圆角、底色、padding、日夜切换都正确，AAR 缺失时仍呈白底圆角 fallback
2. **研究 MeloRow/MeloColumn 方案**：不替换 FlexContainerLayout，而是用 Melo 控件做外层/内层 wrapper（例如：FlexContainerLayout 内部子项的 View 替换为 Melo 控件，但 FlexboxLayout 布局引擎不变）
3. **研究 MeloButton 容器方案**：尝试把 MeloButton 的品牌样式（圆角、渐变背景 drawable）提取出来，应用到 MeloFrameLayout 容器上，而不是直接用 MeloButton
4. **评估 minSdk 动态切换**：当前 minSdk=28 是无条件写死的。可以研究用 flavor 或 buildConfig 让不带公司 AAR 的构建保持 minSdk=21
5. **扩展更多组件适配**：按第 7 节检查清单逐一评估 Image → MeloImageView、CheckBox → MeloCheckBox、Slider → MeloSeekBar 等

---

## 10. 关联文档

| 文档 | 路径 |
|------|------|
| Card HTTP 里程碑 | `docs/demo-milestone-card-http-v0.1.md` |
| Card 协议文档 | `docs/card-contracts/` |
| Demo README | `demo/android-a2ui-text-demo/README.zh-CN.md` |

---

## 11. Text → Mlui TextAppearance 适配（小闭环）

为了让模板不再硬编码 `font-size` / `color` / `font-weight`，引入了一组面向公司控件的 Text 语义 variant。整个改动只动 Text 一条链路，不涉及 Row/Column/Card/Button，也不修改 A2UI core spec。

### 11.1 链路

```
WeatherSummaryTemplate (variant: mluiTitle / mluiTitleLarge / mluiBody / mluiContent / mluiLabel)
  → SDK spec 引擎读取 default theme，把 variant 展开为 styles.{melo-text-appearance: "MluiTextAppearance.Xxx"}
  → MeloTextComponent.onUpdateProperties() 读取 styles
  → applyTextAppearanceIfPresent() 把 melo-text-appearance 从 styles 里剥离，
    再调用 applyTextAppearance() → Resources.getIdentifier() → setTextAppearance()
  → StyleHelper.applyTextStyles() 处理剩余样式（text-align、color 覆写等）
```

调用顺序刻意安排为 **先 setTextAppearance，再 applyTextStyles**，这样模板里如果显式带了 `color` / `text-align`，会覆盖 TextAppearance 中的同名属性。

### 11.2 新增 / 修改的文件

| 文件 | 角色 |
|------|------|
| `assets/common_controls_theme.json` | 声明 5 个 mlui* Text variant，每个变体只设置 `melo-text-appearance` 私有 key |
| `commonui/CommonControlsThemeLoader.java` | 在 `AGenUI.initialize()` 之后、`SurfaceManager` 创建之前调用 `aGenUI.registerDefaultTheme(...)` 加载该 JSON |
| `commonui/MeloTextComponent.java` | 识别 `melo-text-appearance`，通过 `Resources.getIdentifier()` 反射解析 style id，调用 `setTextAppearance` |
| `card/template/WeatherSummaryTemplate.java` | 主要 Text 全部改用 mlui* variant，移除 `font-size` / `color` / `font-weight` 硬编码 |
| `TextDemoActivity.java` | 增加一行 `CommonControlsThemeLoader.loadIfPresent(...)` |

### 11.3 为什么 MluiTextAppearance 不进 A2UI core spec

- core spec 是跨平台的；MluiTextAppearance 是 Android 公司控件包的私有 style 资源，iOS / HarmonyOS 没有对应物
- core spec 不应该依赖任何平台特定的资源命名约定（dot/underscore、R.style）
- 用 demo 侧 theme + 私有 styles key（`melo-text-appearance`）的方式，模型 / 模板写出来仍是合法 A2UI；其他平台拿到这份 JSON 不会因为未知 variant 而崩溃（spec 引擎对未注册 variant 是宽容的，未知 styles key 也会被 StyleHelper 忽略）

### 11.4 setTextAppearance 的 fallback 策略

`MeloTextComponent.resolveStyleId()` 同时尝试两种命名：
- 原始名（如 `MluiTextAppearance.Title.Large`）
- 把点替换成下划线（`MluiTextAppearance_Title_Large`），匹配 Android style 资源在 R 类中的命名

走到 `Resources.getIdentifier()` 返回 0 时，仅 `Log.w` 一行，不抛异常，不影响后续 `StyleHelper.applyTextStyles()`。所以三种情形都不会崩溃：
1. AAR 存在、style 资源存在 → `setTextAppearance` 生效
2. AAR 不存在、style 资源缺失 → 跳过 `setTextAppearance`，文字仍按系统默认渲染
3. style 名拼错 → 同上，仅日志告警

### 11.5 写新 Text variant 时的注意事项

- variant 名以 `mlui` 开头，避免和 A2UI 标准 variant（h1/h2/body/caption 等）冲突
- 每个 variant 只放 `melo-text-appearance`；不要把 `font-size` / `color` / `line-height` 也写进去，否则会覆盖公司 TextAppearance 中的对应属性
- 模板里如果确实要覆盖某个属性，请显式写在组件的 `styles` 里（会覆盖 TextAppearance）
- 在 `WeatherSummaryTemplateTest` 里对照新增 `render_basic_textsUseMluiVariants` 和 `render_basic_textStylesOmitFontSizeColorWeight` 两条测试，保证未来不会有人在模板里偷偷加回硬编码
| 项目级 CLAUDE.md | `CLAUDE.md`（"公司共通控件 SDK 适配"章节） |

---

## 12. Card → Mlui Card appearance 适配（小闭环）

第二条"模板不再硬编码视觉属性"的小闭环。复用 §11 的 demo theme + 私有 styles key 模式，把硬编码的 `background-color` / `border-radius` 替换为公司语义 variant。**严格禁止使用 `gua_*` 开头的资源**（drawable / color / attr / style），背景资源只允许使用 `mlui_app_bg_color_*` ColorStateList。

### 12.1 Card backing view 切回 FrameLayout

历史上 A2UI Card 的 Android 承载 View 是公司 CardView（MeloCardView）。这条路径累积了一堆坑：

- `CardView.setPadding()` 设的是外层 padding，不是内容区，必须特判 `setContentPadding()`
- 自带 `cardBackgroundColor / radius / cardElevation` 会和我们安装的 `GradientDrawable` 抢视觉层
- `useCompatPadding / preventCornerOverlap` 引入 inset，与 Flexbox 父容器打架
- fallback 行为复杂：要分 CardView / MeloCardView / FrameLayout 三段判断

**当前版本把 Card backing view 改为 MeloFrameLayout / FrameLayout：**

```
CommonControlsViewFactory.createCardView(ctx)
  → 反射 com.meloui.car.ui.widget.MeloFrameLayout
  → 反射失败 → new android.widget.FrameLayout(ctx)
  → 不再尝试任何 CardView 子类
```

`MeloCardComponent` 同步删除所有 CardView 专属 API：`setContentPadding / setCardBackgroundColor / setRadius / setCardElevation / setUseCompatPadding / setPreventCornerOverlap / instanceof CardView`。padding 回归普通 `cardView.setPadding(...)`。

### 12.2 链路

```
WeatherSummaryTemplate (variant: mluiCardPrimary)
  → SDK spec 引擎读取 default theme，把 variant 展开为 styles:
      { melo-card-background: "mlui_app_bg_color_primary",
        melo-card-radius: "16px",
        background-color: "#FFFFFF",      // 可见 fallback（AAR 缺失时仍是白底）
        border-radius: "16px",            // 可见 fallback（mluiCardRect 为 0px）
        filter: "none" }
  → MeloCardComponent.onUpdateProperties() 调 super → applyCardAppearanceIfPresent()
  → Resources.getIdentifier(name, "color", pkg)
  → ContextCompat.getColorStateList(...) 优先（保留日夜 state）
  → ColorStateList 失败 → ContextCompat.getColor(...)
  → 都失败 → ContextCompat.getDrawable(...) 仅认 ColorDrawable / GradientDrawable
  → new GradientDrawable().setShape(RECTANGLE).setColor(...).setCornerRadius(radiusPx)
  → cardView.setBackground(gradient)
  → applyCardPadding() 普通 setPadding（不再 setContentPadding）
```

### 12.3 新增 4 个 Card variant

| variant | 背景资源 | 圆角 | 说明 |
|---------|---------|------|------|
| `mluiCardPrimary` | `mlui_app_bg_color_primary` | 16px | 主卡片 |
| `mluiCardSecondary` | `mlui_app_bg_color_secondary` | 16px | 次级 / 嵌套 |
| `mluiCardTertiary` | `mlui_app_bg_color_tertiary` | 16px | 浮层 |
| `mluiCardRect` | `mlui_app_bg_color_primary` | 0px | 直角主卡片 |

### 12.4 为什么 Card variant 的 fallback 装饰必须可见

A2UI core 的 Card spec 会自动补 `background-color` / `border-radius` / `filter` 默认值。即使模板里删掉这些字段，C++ spec 引擎仍会在 styles 里补回去，触发 `StyleHelper.applyBorder()` 用这些值构造 `GradientDrawable` 并 `view.setBackground()`。

之前为了避免 StyleHelper 的背景覆盖公司 drawable，把 variant 里写成 `#00000000 + 0px + none`。后果是 AAR 缺失时（开发机、CI、外部贡献者），`melo-card-background` 解析失败 → 没有公司 drawable → fallback 背景透明 → 卡片完全消失。

现在的方案是 fallback 装饰**保持可见**：

```json
{
  "background-color": "#FFFFFF",
  "border-radius": "16px",     // mluiCardRect 是 0px
  "filter": "none"             // 仍要压住默认阴影
}
```

公司控件可用时，`MeloCardComponent.applyCardAppearanceIfPresent()` 在 super 之后 `setBackground(gradient)`，公司 drawable 一定赢；公司控件不可用时，StyleHelper 的白底 16px GradientDrawable 留在 View 上，至少是可见卡片。

### 12.5 为什么背景必须用 ColorStateList 路径

公司 `mlui_app_bg_color_*` 是 `res/color/*.xml` ColorStateList，state 是公司自定义 `mlui_state_night` 属性。资源解析顺序**必须**是：

1. `ContextCompat.getColorStateList(ctx, colorId)` —— 拿到完整 selector，`GradientDrawable.setColor(ColorStateList)` 之后日夜切换由 GradientDrawable 自己处理
2. ColorStateList 失败再 `ContextCompat.getColor(ctx, colorId)` —— 仅对纯 `<color>` 资源有效，拿到的是 default（day）值，会丢日夜
3. color resource 也未命中才退 `getDrawable()`

**不能**反过来"先 getColor 再补 ColorStateList"。getColor 对 state list 资源不会失败，会直接返回 default 值，selector 就被悄悄拍平。

### 12.6 为什么不使用 LiquidGlass

AAR 里有 7 个 `Mlui.LiquidGlass.*` style + 11 个微调 attr + `com.meloui.car.ui.glass.LiquidGlassEffectHelper`（5 个 AGSL `RuntimeShader`）。当前不集成的原因：

- LiquidGlass 依赖 Android 13+（`sPlatformSupportLiquidGlass` 静态门控的 RuntimeShader API）
- 效果通过 `ViewHelper.updateAttributeSet(View, AttributeSet)` 在 View 构造期从 XML AttributeSet 读取，**没有运行时 Java API**
- demo `minSdk=28`（Android 9），且车机 GPU 性能不可控
- 实现成本远高于 GradientDrawable 路径，收益（玻璃质感）非 demo 验证目标

### 12.7 新增 / 修改的文件

| 文件 | 角色 |
|------|------|
| `assets/common_controls_theme.json` | default theme 下新增 `Card.variant.enum` 的 4 个 variant；fallback 装饰为 `#FFFFFF + 16px/0px + filter:none` |
| `commonui/CommonControlsViewFactory.java` | `createCardView()` 改为只反射 `MeloFrameLayout`，fallback 到 `FrameLayout`；不再创建 CardView 子类 |
| `commonui/MeloCardComponent.java` | 重写：删除全部 CardView 专属 API；`applyCardAppearanceIfPresent()` 走 ColorStateList → int color → drawable 三层；`applyCardPadding()` 普通 setPadding |
| `card/template/WeatherSummaryTemplate.java` | `weather-card` 用 `variant: "mluiCardPrimary"`，移除 `background-color` / `border-radius`，保留 `padding/width/height` |
| `test/commonui/CommonControlsThemeTest.java` | theme 守卫：无 `gua`、保留 5 个 mlui Text variants、声明 4 个 Card variants、fallback 装饰可见（`#FFFFFF + 16px/0px + filter:none`） |
| `test/commonui/CommonControlsViewFactoryTest.java` | 源码级守卫：factory 不再引用 CardView 类 / `androidx.cardview` 包；`MeloCardComponent` 不再使用 `setContentPadding / setCard* / setUseCompatPadding / setPreventCornerOverlap / instanceof CardView` |
| `test/card/template/WeatherSummaryTemplateTest.java` | weather-card 用 `mluiCardPrimary`、styles 不含 `background-color/border-radius`、仍含 `padding` 且过 A2uiJsonValidator |

### 12.8 当前限制

- 只改 `WeatherSummaryTemplate` 作为对照样本；`SportsScoreListTemplate` 故意保留旧写法
- AAR / `mlui_app_bg_color_*` 资源缺失时跳过 setBackground，由 theme JSON 里的 `#FFFFFF + 16px` 白底圆角作为可见 fallback
- 真机未验证。必须验证项：圆角是否到位、`ColorStateList` 日夜切换是否生效、padding 是否到位、AAR 缺失时 Card 是否仍呈白底圆角
- 没有为 Card 提供 `mluiCard` 默认无修饰 variant，需要时再加

---

## 13. Button → Mlui Button appearance 适配（小闭环）

承接 §4「Button 容器而非 MeloButton」的结论：A2UI Button 的 backing view **依然** 是 `MeloFrameLayout / FrameLayout`，本节只在容器之上叠加品牌视觉，不改组件模型，不破坏 `Function Call` click 链路。

### 13.1 目标

- A2UI 协议 / CardData 不变；JSON 仍使用 `"component": "Button"` + `child: "<text-id>"` + `action.functionCall`
- 容器视觉通过 4 个语义 variant 注入：`mluiButtonPrimary` / `mluiButtonSecondary` / `mluiButtonDanger` / `mluiButtonGhost`
- 资源来源限定在 AAR `mlui_*` 命名空间；缺资源时 hex fallback 让按钮仍可见、可点
- 点击 / disabled 行为与 SDK 现有语义一致，不引入 Material ripple、不依赖任何 `androidx.cardview` 之类的额外依赖

### 13.2 AAR 资源调研结论

`gua-car-ui-lib-release.aar` 中可用的 button color 资源（两组同义命名）：

| 用途 | selector（推荐，四态） | legacy flat 色（仅 day/night） |
|------|----------------------|-------------------------------|
| filled 按钮背景 | `mlui_button_filled_bg_color` | `mlui_btn_filled_bg_color_day` / `_night` |
| filled 按钮 disabled 背景 | （selector 已内置 disabled 态） | `mlui_btn_filled_bg_disabled_color_day` / `_night` |
| grey 按钮背景 | `mlui_button_grey_bg_color` | `mlui_btn_grey_bg_color_day` / `_night` |
| grey 按钮 disabled 背景 | （selector 已内置 disabled 态） | `mlui_btn_grey_bg_disabled_color_day` / `_night` |
| warning 按钮背景 | `mlui_button_warning_bg_color` | `mlui_btn_warning_bg_color_day` / `_night` |
| warning 按钮 disabled 背景 | （selector 已内置 disabled 态） | `mlui_btn_warning_bg_disabled_color_day` / `_night` |
| plain 按钮背景 | `mlui_button_plain_bg_color` | `mlui_btn_plain_bg_color_day` / `_night` |
| 其它 | `mlui_bg_color_accent` 等 | — |

**两组资源区别**：`mlui_button_*_bg_color`（带 `_button_`）在 `res/color/` 下是 selector 文件，内置 enabled/disabled × day/night **四态**；`mlui_btn_*_bg_color`（带 `_btn_`）只有 `_day` / `_night` 拍平 `<color>`，**没有 base ColorStateList**。**Theme 必须用带 `_button_` 的 selector** —— `ContextCompat.getColorStateList()` 直接拿到四态 CSL，不需要单独再传 `melo-button-disabled-background`。

**pressed 状态**：AAR 的 `res/drawable/mlui_button_foreground.xml` 是 selector，`state_pressed` → `mlui_btn_pressed_foreground_color` 半透明覆盖，自带 12dp 圆角（`mlui_corner_radius_r3`）。当前实现通过 `buttonContainer.setForeground(R.drawable.mlui_button_foreground)` 安装这层 overlay，FrameLayout 自身 pressed 状态驱动 overlay 显示，**与背景 StateListDrawable 解耦**。不再使用 `setAlpha(204)` 派生 pressed 背景。AAR 中也只暴露了 `mlui_btn_filled_text_pressed_color_day/night` 等前景文字色，base ColorStateList 没有 pressed 背景态 —— foreground overlay 是 AAR 自己的 pressed UX 路径，对齐这条更准。

**文字色**：AAR 暴露的 `mlui_btn_*_text_color`（带 `_btn_`）是完整 CSL，覆盖 enabled × pressed × disabled × day/night 八态。Theme 用 `melo-button-text-color` 私有 key 声明（如 `mlui_btn_filled_text_color` / `mlui_btn_grey_text_color` / `mlui_btn_plain_text_color`），运行时 `MeloButtonComponent` 装 `OnHierarchyChangeListener` 监听子 view add，通过 `View.post()` 推到队尾（绕过子 Text `setTextAppearance(mluiLabel)` 自带的 `android:textColor=@color/mlui_text_color_secondary` 覆盖），递归对每个子 `TextView` 调 `setTextColor(csl)` + `setDuplicateParentStateEnabled(true)` + 关掉自身 clickable/focusable（防止子 view 拦截 pressed 状态传播）。CSL 跟随容器 pressed/disabled/day-night 自动切换。

**legacy 路径 fallback（仅在用旧名时触发）**：如果资源名只有 `_day`/`_night` 拍平色，`buildBackgroundDrawable()` 在 base 名解析失败后调用 `buildDayNightDrawable()`：
1. 分别解析 `<name>_day` / `<name>_night` flat 色 + AAR 自定义 `mlui_state_night` attr id
2. 三者齐全时**运行时合成** ColorStateList：`{mlui_state_night} → night色`、`{-mlui_state_night} → day色`、默认 → day 色
3. 装进 GradientDrawable.setColor(csl) → 日夜切换由 AAR 控件自身驱动 `mlui_state_night` state 时仍然生效
4. 三者任一缺失才退到 hex fallback

当前 theme 已迁移到带 `_button_` 的 selector，此路径仅作 fallback 保留。

### 13.3 Variant 表

| variant | melo-button-background | melo-button-disabled-background | melo-button-radius | background-color (hex fallback) |
|---------|------------------------|--------------------------------|--------------------|----------------------------------|
| `mluiButtonPrimary` | `mlui_button_filled_bg_color` | —（selector 内置 disabled 态） | `24px` | `#007AFF` |
| `mluiButtonSecondary` | `mlui_button_grey_bg_color` | —（selector 内置 disabled 态） | `24px` | `#E8E8E8` |
| `mluiButtonDanger` | `mlui_button_warning_bg_color` | —（selector 内置 disabled 态） | `24px` | `#E54545` |
| `mluiButtonGhost` | `#00000000` | `#00000000` | `24px` | `#00000000` |

> **AAR 双命名空间**：solid 三类首选 `res/color/mlui_button_*_bg_color.xml`（4 态 ColorStateList：enabled/disabled × day/night）；legacy `mlui_btn_*_bg_color` 只有 `_day`/`_night` flat 色，没有 base CSL，须靠 `MeloButtonComponent.buildDayNightDrawable()` 现场合成。新代码全部用 `_button_` 命名空间，`_btn_` 仅留作 fallback 兼容路径。

每个 variant 都额外声明 `melo-button-text-variant: "mluiLabel"`（**仅文档约束**，运行时不读 — 让模板作者知道 child Text 该用哪个 Mlui Text variant）。

### 13.4 实现链路

1. `assets/common_controls_theme.json` 在 default theme 下新增 `Button.variant.enum`，4 个 variant 各自带 `melo-button-*` keys + 可见 hex fallback
2. C++ spec 引擎 `nlohmann::merge_patch` 把 variant 合并进 Button spec
3. `MeloButtonComponent.createView(Context, ViewGroup)` override：`super.createView(...)` 之后立即调用 `applyProperties()` + `applyButtonAppearanceIfPresent()`（对齐 MeloCardComponent），防止"直接 `Surface.addComponent` 无后续 update"链路漏样式
4. `MeloButtonComponent.onUpdateProperties()`：
   - 先 `super.onUpdateProperties()`（让 `StyleHelper.applyBackground/applyBorder` 跑完，否则 hex fallback 不上 GradientDrawable）
   - `applyProperties()` 处理 child / checks / disable / clickable / focusable
   - `applyButtonAppearanceIfPresent()` 读 `melo-button-*` keys、组装 `StateListDrawable`、`setBackground()`
5. **资源解析顺序（每个 key）**：
   - 值以 `#` 开头 → `Color.parseColor()`
   - 否则按 color resource 名解析：`ContextCompat.getColorStateList()` → `getColor()` → `getDrawable()`
   - **ColorStateList 必须在 getColor 之前**，否则带 `_button_` 的 selector 会被拍平成单色，丢失四态切换
   - **base 名不存在时**（仅 legacy `mlui_btn_*_bg_color` 这一类）→ 调 `buildDayNightDrawable()` 用 `_day` + `_night` flat 色 + `mlui_state_night` attr 现场合成 CSL；任一缺失才退到 hex fallback
6. **StateListDrawable 组装顺序**（状态判定先到先得）：
   - `disabled`（`-android.R.attr.state_enabled`）
   - normal（空 state 数组兜底）
   - 当 `melo-button-background` 已经是带四态的 `mlui_button_*_bg_color` selector 时，StateListDrawable 实际只有 normal 一条 entry，disabled 切换由内部 CSL 自动完成
7. **pressed 由 foreground overlay 独立驱动，不进 StateListDrawable**：
   - `applyButtonAppearanceIfPresent()` 末尾调 `installForegroundOverlay(ctx)` → `buttonContainer.setForeground(R.drawable.mlui_button_foreground)`
   - AAR 的 `mlui_button_foreground` 是 selector，`state_pressed` → `mlui_btn_pressed_foreground_color` 半透明覆盖 + 12dp 圆角
   - 资源缺失（AAR 不在 / unbranded build）→ 仅 `Log.d` 跳过，pressed 无 overlay 但功能不受影响
   - **已弃用** `setAlpha(204)` 派生方案和 `melo-button-pressed-background` 私有 key

### 13.5 点击 / Function Call 不回归

- 容器仍由 `CommonControlsViewFactory.createFrameLayout(context)` 创建（反射 MeloFrameLayout，fallback FrameLayout）
- `shouldAutoAddChildView() == true` 保证 child Text view 被自动加入容器
- `enabled = !disable && checksResult` —— `disable: true` 或 `checks.result == false` 任一为真都 `setClickable(false) + setEnabled(false)`，Function Call 不触发。两个输入合并后**一次性 set**（早期实现先按 checks 再按 disable 分两步 set，`disable:false` 会清掉 `checks.result:false`，已修）
- 启用 disabled 后的视觉：优先由 selector 自身的 `state_enabled="false"` 条目驱动；如果用了 legacy 拍平色，则由 `melo-button-disabled-background` 安装的 StateListDrawable 条目接手

### 13.6 不修改 SDK / core

- 不动 `platforms/android/src/main/java/com/amap/agenui/render/style/StyleHelper.java`
- 不动 `core/src/surface/component_property_spec/`
- 不引入 `RippleDrawable`（如需 Material ripple，未来在 `MeloButtonComponent` 自己包一层即可）

### 13.7 测试

| 文件 | 覆盖点 |
|------|--------|
| `test/commonui/CommonControlsThemeTest.java`（新增 5 个用例） | 4 个 mluiButton variant 存在；每个都有 `melo-button-background` + `melo-button-radius`；solid 三类 fallback `background-color` 非透明；Button 节点不含 `gua` 字符串；solid 三类必须用 `mlui_button_*_bg_color`（res/color selector，四态）而非 legacy `mlui_btn_*` |
| `test/commonui/MeloButtonComponentTest.java`（新增） | backing view 仍是 FrameLayout 容器；不引用任何 Button 子类 backing；`shouldAutoAddChildView() == true`；私有 keys 全识别；ColorStateList 解析在 getColor 之前；`super.onUpdateProperties` 在 appearance 安装之前；`createView(Context, ViewGroup)` override 也调 appearance；`enabled = !isDisabled && checksResult` 合并逻辑 + 默认值 + setClickable/setEnabled 只调一次；fixture 中 submit-btn 用 `mluiButtonPrimary` + child Text 用 `mluiLabel` + 仍保留 `toast` functionCall |

### 13.8 当前限制

- 只改 `form_with_action.json` 的 submit-btn 作为对照样例，其余 fixture 仍走旧的 `variant: "primary"` 写法
- danger 用的是 AAR 的 `mlui_button_warning_bg_color`（4 态 selector，名含 `warning` 而非 `danger`），正式项目应让设计提供专属 `mlui_btn_danger_bg_color`
- ghost 默认透明，无边框 key，视觉完全依赖 child Text 的 `color`
- pressed 视觉由 AAR `mlui_button_foreground` selector 通过 `buttonContainer.setForeground(...)` 提供（半透明覆盖 + 12dp 圆角），AAR 缺失时 pressed 无 overlay 但功能不受影响；不引入 Material ripple
- `melo-button-text-variant` 仅文档约束，运行时不强制 child Text 用 `mluiLabel`
- AAR 缺失时 hex fallback 接手；ghost 仍透明
- 真机未验证。必须验证项：normal/pressed 视觉切换、`checks.result=false` 时 disabled 状态视觉 + 点击不触发 Function Call、日夜模式下 selector 背景色切换、AAR 缺失时 hex fallback 可见、`toast` Function Call 触发是否正常

## 14. Toast → MeloToast 适配（小闭环）

### 14.1 背景

真机验证环境是定制 ROM，`android.widget.Toast` 在系统层被静默过滤，用户看不到任何提示。Demo 内全部 toast 调用必须改走公司 `com.meloui.car.ui.widget.Toast`。

### 14.2 API 29 兼容性演进

**第一版**：反射 3 参 `MeloToast.makeText(Context, CharSequence, int).show()`。真机抛 `NoSuchMethodError: No virtual method getDisplayId()I`——3 参 `makeText` 内部走 3 参 private 构造器，该构造器调 `Context.getDisplayId()`（API 30+），API 29 设备直接崩。

**第二版（临时）**：绕开 wrapper，反射 `MeloWindowManager.getInstance().getService().showToastOnDisplay(pkg, msg, duration, 0)` 直连 IPC。可行但绕了远路，需要解析两个 FQN + 4 个 Method。

**第三版（当前）**：SDK 新增 `public static Toast makeText(Context, CharSequence, int, int)` 4 参重载，内部走 4 参 private 构造器（直接用传入的 displayId，不调 `Context.getDisplayId()`）。Helper 反射这一个 API + `show()`，硬编码 `displayId = 0`。

MeloToast 公开 API（javap 输出）：

```
public static Toast makeText(Context, CharSequence, int);           // ← 3 参，API 30+ 才安全
public static Toast makeText(Context, CharSequence, int, int);      // ← 4 参，API 29 安全
public static Toast makeText(Context, int, int);
public void show();
public void show(int);
```

### 14.3 链路

```
任意 demo 代码
  → commonui.MeloToastHelper.show(context, text, duration)
    → ensureResolved() —— 反射 Class.forName("com.meloui.car.ui.widget.Toast")
        + getMethod("makeText", Context, CharSequence, int, int)
        + getMethod("show")
      → 成功 → MeloToast.makeText(ctx, text, dur, 0).show()  ← 定制 ROM 真正可见
      → 失败 → android.widget.Toast.makeText(...).show()      ← unbranded build / 非定制 ROM
```

`duration` 直接转发 `android.widget.Toast.LENGTH_SHORT/LENGTH_LONG`（两边都是 0/1，编码一致）。

### 14.4 新增 / 修改的文件

- `commonui/MeloToastHelper.java`（新增）—— 反射 + fallback 单类，唯一允许调用 `android.widget.Toast.makeText(...)` 的位置
- `commonui/MeloToastHelperTest.java`（新增）—— 4 个 source-level 守护：helper 不能 `import com.meloui.*`、必须引用 `com.meloui.car.ui.widget.Toast` FQN + `Class.forName`、必须解析 4 参 `makeText` 签名、必须保留 `android.widget.Toast` fallback、`TextDemoActivity` / `ToastFunction` 不能直接出现 `Toast.makeText(`
- `TextDemoActivity.java` —— 25 处 `Toast.makeText(...).show()` 全部替换为 `MeloToastHelper.show(...)`
- `ToastFunction.java` —— A2UI Function Call `toast` 走 helper

### 14.5 为什么用单文件 helper 而不是包级 utility

- 2 个 `Method` 句柄 + `sAvailable` 集中在一处，初始化代价摊到首次调用（双重检查 + `volatile`）
- AAR 缺失 → `Class.forName` 抛 `ClassNotFoundException`，捕获后写 `sAvailable = false`，后续直接走 fallback，无重复反射
- 任何调用失败也 `sAvailable = false` 短路——一次失败之后不再反复抛
- 调用方语法与原生 Toast 一致（`MeloToastHelper.show(ctx, text, dur)`），迁移代价 = 一次正则替换

### 14.6 错误可观测性陷阱

第一版抓到的日志是 `MeloToast invocation failed, falling back: null`——message 是 null，看不出根因。原因：反射 `invoke()` 失败抛的是 `InvocationTargetException`，它本身的 `getMessage()` 永远是 null，真实异常在 `getCause()` 里。`MeloToastHelper.show()` 专门 catch `InvocationTargetException` 并打印 `cause.getClass().getName() + ": " + cause.getMessage()` + stack，否则后续任何失败都会被吞成 "null"。

### 14.7 为什么是 source-level guard test，不是行为测试

Robolectric 在本模块不可用，没法真正驱动 Toast。`MeloToastHelperTest` 退而求其次直接读源码字符串，禁词 `Toast.makeText(` 在测试源里通过 `"Toast." + "makeText("` 运行时拼接，保证测试自身不被禁词扫到。够用、零依赖、跟里程碑 §13.7 Button 守护一致。

### 14.8 当前限制

- **AAR 缺失时仍可工作**：fallback 到 `android.widget.Toast`；但在定制 ROM 上 fallback 仍然不可见，必须装好 AAR
- **必须用 4 参 `makeText` 重载**：3 参 `makeText(Context, CharSequence, int)` 内部走 3 参构造器，会调 `Context.getDisplayId()`（API 30+），API 29 设备直接崩。Source guard test 已守护这一签名
- **displayId 硬编码为 0**：多屏 / 副屏场景未覆盖；MeloToast 也暴露了 `show(int)` 方法，未来可按需切换
- **真机未验证**：必须验证项：Function Call `toast`、Card 渲染成功 / 失败、复制 JSON 成功 / 失败、Provider 输入校验提示。日志关键字 `MeloToastHelper`：成功应看到 `MeloToast available — using makeText(ctx, text, dur, displayId=0)`
- MeloToast 的扩展 API（`cancel` / `setText` 等）未在 helper 中暴露——按需扩展
