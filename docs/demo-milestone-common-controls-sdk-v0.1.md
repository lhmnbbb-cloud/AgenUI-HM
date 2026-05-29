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
| `commonui/MeloCardComponent.java` | Card 适配：MeloCardView，继承 `A2UILayoutComponent`，含 `applyCardContentPadding()` |
| `commonui/MeloButtonComponent.java` | Button 适配：MeloFrameLayout 容器，继承 `A2UILayoutComponent` |
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
| Card | MeloCardView / MeloFrameLayout | FrameLayout | ViewGroup 容器，单 child，需特殊处理 padding（见第 5 节） |
| Button | MeloFrameLayout 容器 | FrameLayout | A2UI Button 是"容器 + child component"模型；MeloButton/AppCompatButton 是 Button 子类，不是 ViewGroup，无法容纳 child View |

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

## 5. Card padding 问题（最重要的坑）

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
| 3 | padding API 是否语义一致 | CardView 的 padding = `setContentPadding()`，普通 View 的 padding = `setPadding()` | MeloCardComponent.applyCardContentPadding() |
| 4 | background API 是否一致 | 检查公司控件的背景绘制是否兼容 StyleHelper.applyBackground()（GradientDrawable / ColorDrawable） | CardView 有 CardBackground |
| 5 | border-radius API 是否一致 | CardView 用 `setRadius()`，普通 View 用 GradientDrawable corner radius | CardView.setRadius() |
| 6 | elevation / shadow API | CardView 有 `setCardElevation()`，普通 View 用 `StateListAnimator` 或自定义 drawable | CardView elevation |
| 7 | action / click / function call | 确认 `setOnClickListener()` 是否仍能正常触发 A2UI Function Call | Button 容器的 click listener |
| 8 | AAR 不存在时 fallback 是否可用 | 反射失败自动 fallback 到标准 View，功能不能降级 | CommonControlsViewFactory fallback 链 |
| 9 | minSdk / manifest / 权限 / 资源冲突 | 公司 AAR 可能引入 minSdk 限制、manifest provider 合并冲突、权限要求、资源命名冲突 | minSdk 28、preference/recyclerview 依赖 |
| 10 | 是否需要真机验证 | assembleDebug 通过不代表渲染正确。padding、background、radius、Flexbox 等必须在真机上视觉确认 | Card padding 贴边问题在 assembleDebug 通过后才暴露 |

---

## 8. 当前限制

1. 公司共通控件适配当前覆盖 3 个 A2UI 组件（Text/Card/Button），Row/Column 保留原生 FlexContainerLayout，其余 17 个标准组件未适配
2. Button 使用 Melo 容器而非 MeloButton（Button 非 ViewGroup 无法容纳 child）
3. minSdk 从 21 提升到 28（因公司 AAR 要求），即使 AAR 不存在 minSdk 也是 28
4. MeloCardView 的 `setPadding()` 语义问题已通过 `applyCardContentPadding()` 修复。`StyleHelper.applySpacing()` 仍会调用 `setPadding()`，但对 CardView 的内容内边距不可依赖；最终以 `applyCardContentPadding()` 调用 `setContentPadding()` 来保证内容区 padding 正确
5. Row/Column 的公司控件适配需要研究"FlexContainerLayout + Melo 外层/内层 wrapper"方案，不能直接用 LinearLayout 替代

---

## 9. 下一阶段建议

1. **真机验证 Card padding**：在带公司 AAR 的真机上渲染 weather_summary 和 sports_score_list 卡片，确认 `applyCardContentPadding()` 修复有效，内容不贴边
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