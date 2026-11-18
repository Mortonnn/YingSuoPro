# 影梭 Pro · Material 3 Expressive（Mobile）前端规范

本文件是本次前端重构的统一约束。所有页面共用同一套 token，不允许各自发挥。

## 1. 视觉方向

- **像 Pixel 原生应用**：低饱和 tonal surface，大圆角（24–28dp），扁平、干净、留白充足。
- **Material 3 Expressive**：大标题 + 高对比层级；胶囊按钮；圆形/圆角图标按钮；柔和的分组卡片；有意义的颜色，不用装饰性渐变。
- **去 AI 味**：不要紫色渐变、不要发光、不要玻璃拟态、不要 emoji 充当图标、不要每行一张“卡片汤”、不要无意义的阴影。
- 图标只用 Google **Material Symbols Rounded**（已下载到 `res/drawable/ic_*.xml`，24dp，viewport 960，`fillColor=#FF000000` 由 `app:tint` / `android:tint` 着色）。禁止手搓新图标路径；缺少图标时先找对应的 Material Symbols 名称。

## 2. 颜色（只用语义色，禁止在布局写死 hex）

浅色 / 深色已同时定义在 `values/colors.xml` 与 `values-night/colors.xml`：

| 角色 | 用途 |
|---|---|
| `@color/primary` / `on_primary` | 主按钮、强调图标 |
| `@color/primary_container` / `on_primary_container` | 选中态、欢迎页 hero |
| `@color/secondary_container` / `on_secondary_container` | 图标底圆、chip、状态 chip |
| `@color/surface` | 页面底色 |
| `@color/surface_container_lowest` | 浮起的卡片/顶栏/输入区 |
| `@color/surface_container_low` | Bottom Sheet、普通卡片 |
| `@color/surface_container` / `_high` / `_highest` | 分组、图标按钮底、轨道 |
| `@color/on_surface` / `on_surface_variant` | 主文字 / 次文字 |
| `@color/outline` / `outline_variant` | 描边 / 分割线 |
| `@color/error` | 危险操作 |

兼容别名仍可用：`colorPrimary`、`colorAccent`、`muted`、`app_background`、`white`。

## 3. 字体与尺寸

- 字体：`sans-serif` / `sans-serif-medium`（Pixel 系统字体栈），数字统一 `fontFeatureSettings="tnum"`。
- 层级：Display 40sp（仅欢迎页）、Headline 28sp、Title 22sp（顶栏）、Section 17sp、Body 15sp、Label 13sp、Caption 12sp。
- 使用 `@style/TextAppearance.App.*`，不要逐处写 sp。
- 间距：页面左右 20dp；卡片内 16–20dp；组件间距 8/12/16dp。
- 触控区不小于 48dp。

## 4. 形状与组件

- 顶栏：`MaterialCardView` + `Widget.App.Card.Floating` 风格，圆角 28dp，高 68dp，左右/顶部 12dp 悬浮在地图上；内部 `MaterialToolbar` 用 `Widget.App.Toolbar`，标题 22sp，副标题 13sp。
- 卡片：24dp 圆角、`surface_container_lowest` 或 `surface_container_low`、elevation 0（浮起元素才 2dp）。
- 主按钮：`Widget.App.Button`（56dp 高、胶囊、primary）。
- 次按钮：`Widget.App.Button.Tonal` 或 `Widget.App.Button.Outlined`。
- 图标按钮：`Widget.App.IconButton` / `Widget.App.IconButton.Tonal`（48dp 圆形）。
- Chip：32dp 高、100dp 圆角、12sp。
- Bottom Sheet：顶部 28dp 圆角，拖拽把手 32×4dp、`outline_variant`。
- 列表行：最小 64dp，leading 图标放在 40dp tonal 圆底内，标题 15–16sp，副标题 12–13sp。
- 分割线：1dp `outline_variant`，左右留 16dp。

## 5. 动效

- 只用系统 ripple、Material 默认 state layer；不写花哨动画。
- 页面切换保持默认；BottomSheet 使用 `BottomSheetBehavior`。

## 6. 文件与写入边界（重要）

- 公共 token（colors / dimens / styles / strings_ui / drawable）由 Lead 维护；其他 Agent 不要改。
- 每个页面只在各自负责的 layout / java / strings 文件内写入；新文案写到独立文件：
  - 路线页：`values/strings_ui_route.xml`
  - 设置/历史/欢迎/对话框：`values/strings_ui_pages.xml`
- 不要删除已有 id；Java 依赖的控件 id 必须保留。
- 布局中引用的 drawable 名必须已存在（见 `res/drawable`）。
- 完成后自查：`grep -n "#[0-9A-Fa-f]\{6\}"` 不应在布局中出现（地图/透明除外）；所有图标均为 `ic_*` 的 Material Symbols。

## 7. 页面基线

### 首页
- 地图全屏，顶部悬浮 28dp 圆角顶栏（菜单 + 标题/副标题 + 搜索）。
- 地图右上：4 个 48dp 圆形浮起按钮（输入坐标 / 我的位置 / 放大 / 缩小），间距 8dp。
- 地图左上（顶栏下方）：地图类型分段按钮（普通图 / 卫星图），使用 `MaterialButtonToggleGroup`。
- 底部左侧：Extended FAB「开始模拟」；底部右侧：低对比免责声明 chip。
- 搜索下拉：24dp 圆角卡片，列表行带 leading 图标。

### 路线模拟页
- 地图全屏 + 悬浮顶栏（返回 + 路线模拟 + 拖动地图，对准点）。
- 顶栏下方左侧：28dp 圆角搜索条；右侧：3 个圆形地图按钮。
- 地图中心：绿色准星（`ic_route_location`，48dp 白色圆底 + 2dp 阴影）。
- 底部 Bottom Sheet：统计卡（3 列 metric）+ 5 个带文字的操作 + 状态/暂停/开始按钮 + 进度条；展开后显示历史路线列表。

### 设置页
- 顶栏（返回 + 设置 + 副标题），下方 PreferenceFragment。
- 分组标题 13sp primary；行：40dp tonal 圆形图标 + 标题/摘要 + trailing（Switch / chevron）。
- 行与行之间 1dp 分割；整组视觉上是一张 24dp 圆角卡片。

### 历史页
- 顶栏（返回 + 历史记录 + 删除 action）。
- 空状态：96dp `primary_container` 圆底 + `history` 图标 + 标题/说明。
- 列表行：leading `location_on` 圆底 + 地点名 + 时间 + 坐标（monospace 12sp）。

### 欢迎页
- 无顶栏；可滚动。
- 顶部品牌区：hero 卡片（32dp 圆角、`primary_container`）+ 抽象路线插画；标题 40sp；副标题 15sp。
- 三行功能说明（40dp tonal 图标 + 标题/说明）。
- 底部协议 checkbox + 56dp 主按钮 + 版本号。

## 8. 验收

- `gradlew.bat :app:assembleDebug --offline` 通过。
- 无自定义图标路径；无硬编码颜色；深色模式可读。
- 截图：首页、抽屉、路线页（折叠/展开）、设置、历史空态、欢迎页、速度弹窗、摇杆。
