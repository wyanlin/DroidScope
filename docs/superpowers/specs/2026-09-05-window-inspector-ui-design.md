# DroidScope Window Inspector V0.1 UI 设计

## 1．设计结论

Window Inspector 采用面向 Windows 主程序的 IDE 三栏式工作台：左侧为模块导航，中间为按 Display 分组的 Window Tree Table，右侧为选中 Window 的常驻详情。

默认视觉主题使用 `Graphite Signal`：蓝黑石墨底色承载长时间调试，青色只标识焦点、选中对象和对象关联，绿色表示正常可用状态，黄色表示可恢复的解析告警，红色表示连接或命令失败。

V0.1 只显示已实现且可用的 `Overview` 与 `Raw` 页签。Activity、Surface、Input 等关联能力未实现时，不展示不可用入口。

## 2．背景与设计依据

DroidScope 面向 Android Framework、ROM、BSP 和系统稳定性开发者。Window Inspector 的任务不是美化 `dumpsys window`，而是让用户快速完成以下判断：

1. 当前有哪些 Window。
2. 哪个 Window 获得焦点、可见并拥有 Surface。
3. Window 属于哪个进程、UID 和 Display。
4. 结构化字段是否可信，原始证据在哪里。
5. 设备断开或部分解析失败后，已有证据是否仍可继续查看。

视觉设计参考了以下方向：

- [anbeime/skill](https://github.com/anbeime/skill) 中收录的 `frontend-design` 设计方法，用于强调有明确产品身份的视觉选择，而不是套用通用后台模板。
- [shadcn/ui](https://github.com/shadcn-ui/ui) 的低层级、可组合组件思路，用于约束搜索框、筛选器、页签和状态反馈的一致性。
- [Ant Design Pro](https://github.com/ant-design/ant-design-pro) 的企业级工作台信息架构，用于参考稳定的侧边导航与高密度数据页面组织方式。
- Android Studio、Chrome DevTools、Perfetto 和 Wireshark 的工程工具交互习惯，用于降低目标用户的学习成本。

这些参考只用于提取信息层级和交互原则，不直接复制其页面或品牌视觉。

## 3．范围

### 3.1 本次范围

- Windows 主程序中的 Window Inspector 页面。
- 全局设备上下文、连接状态和 Snapshot 刷新入口。
- Window 搜索、筛选、分组列表和选中状态。
- Window 结构化详情与原始 `dumpsys` block 查看。
- 加载、空数据、设备断开、命令失败和部分解析失败状态。
- 支撑后续 Inspector 复用的基础颜色、间距和交互规范。

### 3.2 非本次范围

- Windows 托盘图标重设计。
- Android 分享 App 页面重设计。
- Activity、Surface、Input、Process 和 Binder Inspector 的实际页面。
- Window 与其他系统对象的关联交互。
- Snapshot Diff、Perfetto 和 Diagnose 页面。
- Windows UI 技术框架选型与工程迁移。

## 4．产品边界

DroidScope 的界面承载关系如下：

```text
Android App
└── 系统分享、文件发送

Windows Tray
└── 后台状态、打开主窗口、重连、退出

Windows Main Window
├── Device Overview
├── Window Inspector
├── Logcat
└── Shell
```

Window Inspector 只存在于 Windows 主窗口。托盘继续保持轻量，不承载 Inspector 数据。

## 5．信息架构

```text
Window Inspector
├── Top Bar
│   ├── Product Identity
│   ├── Selected Device
│   ├── Connection State
│   ├── Snapshot Timestamp
│   └── Refresh Snapshot
├── Module Navigation
│   ├── Device
│   ├── System
│   └── Tools
├── Window Tree Table
│   ├── Search
│   ├── Filters
│   ├── Display Groups
│   └── Window Rows
├── Window Details
│   ├── Overview
│   └── Raw
└── Status Bar
    ├── ADB State
    ├── Device Serial
    ├── Capture Duration
    └── Parse Warnings
```

## 6．主界面布局

### 6.1 推荐尺寸

- 推荐窗口尺寸：`1440 × 900 px`。
- 最小窗口尺寸：`1100 × 700 px`。
- 左侧导航默认宽度：`184 px`，可折叠到 `56 px`。
- 右侧详情默认宽度：`330 px`，可在 `280—480 px` 范围拖动。
- 中间 Tree Table 占用剩余空间，最小宽度为 `450 px`。
- 顶栏高度：`52 px`。
- 底部状态栏高度：`27 px`。

小于最小宽度时不继续压缩字段。优先折叠左侧导航，再将右侧详情切换为覆盖式抽屉。

### 6.2 三栏职责

#### 左侧导航

只负责一级模块切换，不混入筛选条件。V0.1 必须显示 `Windows`，其余入口仅在对应功能已经实现时出现。尚未实现的模块不显示，避免用户误认为可以使用。

#### 中间 Tree Table

Tree Table 按 `displayId` 分组。Window 行保持表格对齐，同时提供树状层级感。默认列为：

| 列 | 内容 | 行为 |
|---|---|---|
| Window | `title`，次要位置显示 `packageName` | 单击选中，双击聚焦详情 |
| PID | `pid` | 支持筛选与复制 |
| UID | `uid` | 支持筛选与复制 |
| Display | `displayId` | 与分组信息保持一致 |
| State | `FOCUSED`、`VISIBLE`、`HIDDEN` | 使用文字、图标与颜色共同表达 |

默认排序保持 `dumpsys window` 中可表达的 Z Order。若当前 Parser 无法可靠得到顺序，必须明确标为采集顺序，不得暗示为真实 Z Order。

#### 右侧详情

详情常驻，切换 Window 时不离开列表上下文。未选中 Window 时展示引导状态，不默认选中第一项，避免用户误认为该 Window 是 focused。

## 7．顶部设备上下文

顶部必须持续显示：

- 设备名称。
- Android 版本与 API Level。
- 连接状态。
- 当前 Snapshot 的采集时间和相对时间。
- `Refresh Snapshot` 主操作。

多设备场景下，点击设备区域打开设备选择器。切换设备会清空当前选中 Window，但保留搜索与筛选条件。旧 Snapshot 不与新设备数据混合。

刷新过程中保留旧 Snapshot，整体降低一个层级的对比度，并在顶部显示采集中状态。采集成功后一次性替换数据，避免不同 Collector 的结果分批闪烁。

## 8．搜索与筛选

### 8.1 搜索

单一搜索框匹配：

- `title`
- `packageName`
- `pid`
- `uid`
- `windowToken`
- `appToken`

搜索不区分英文大小写。输入在 `150 ms` 后生效，避免大量 Window 时频繁刷新列表。

快捷键：

- `Ctrl+F`：聚焦搜索框。
- `Esc`：首次清空搜索内容，再次退出搜索焦点。

### 8.2 筛选

V0.1 提供：

- Package。
- Visible。
- Focused only。
- Display。

多个筛选条件使用 AND 关系。启用筛选后显示结果数量，并提供一次性清除全部筛选的操作。

## 9．选中与状态表达

选中 Window 使用深青色背景和左侧 `2—3 px` 青色边线。Focused Window 同时显示菱形焦点图标与 `FOCUSED` 标签。

颜色不能作为唯一信息载体：

- Focused：菱形图标＋`FOCUSED`＋青色。
- Visible：圆点图标＋`VISIBLE`＋绿色。
- Hidden：空心圆点＋`HIDDEN`＋中性灰。
- Parse warning：三角图标＋告警数量＋黄色。
- Failure：叉号图标＋错误类型＋红色。

当选中项因筛选条件变化而消失时，右侧详情继续保留，并显示「当前对象已被筛选隐藏」。用户可选择清除筛选或关闭详情。

## 10．Overview 详情

详情字段按排障语义分组，顺序固定：

### 10.1 State

- `focused`
- `visible`
- `hasSurface`
- `type`
- `layer`

### 10.2 Geometry

- `frame`
- `parentFrame`
- `displayFrame`

坐标统一标注单位为 `px`，保持原始矩形值，不擅自换算为 dp。

### 10.3 Owner

- `packageName`
- `pid`
- `uid`
- `displayId`

### 10.4 Tokens

- `windowToken`
- `appToken`

### 10.5 Parser

- 解析告警数量。
- 未识别字段数量。
- Raw block 大小。

字段不存在时显示 `—`。字段解析失败时显示 `Unparsed` 并提供跳转到对应 Raw 行的操作，不能以 `false`、`0` 或空字符串代替未知值。

## 11．Raw 查看

Raw 页签默认只显示选中 Window 的原始 block，不显示整份 `dumpsys window`，以降低检索成本。

Raw 工具栏提供：

- 在当前 block 内搜索。
- 复制完整 block。
- 导出完整 block。
- 跳转到下一个搜索结果。

Raw 内容必须只读，使用等宽字体并保留原始换行和缩进。Parser 无法识别的字段以黄色行背景提示，但不得修改文本。结构化字段的来源行可以使用低饱和青色高亮。

V0.1 不提供 Raw 编辑、折叠规则自定义和正则替换。

## 12．异常与边界状态

### 12.1 首次加载

显示页面骨架和「正在采集 Window Snapshot」。超过 `3 s` 后补充显示当前阶段，例如正在等待 ADB 或解析 Window。

### 12.2 无 Window

区分两种情况：

- 采集成功但结果为空：显示 `No windows found`，保留刷新入口。
- 搜索或筛选结果为空：显示 `No matching windows`，提供清除筛选入口。

### 12.3 设备断开

保留上次 Snapshot 为只读，并在顶部和内容区明确显示 Snapshot 时间。提供重连入口，但不自动用其他设备替换当前设备。

### 12.4 命令失败

显示统一错误类型，例如 `ADB_NOT_FOUND`、`DEVICE_OFFLINE`、`COMMAND_TIMEOUT` 或 `PERMISSION_DENIED`。页面提供重试与打开日志入口。异常堆栈只进入开发日志，不直接展示给用户。

### 12.5 部分解析失败

成功解析的 Window 正常显示。解析失败的对象仍作为行存在，标题优先从 raw block 头提取；详情只显示已知字段和 Raw。页面显示总告警数量，并允许定位到具体对象。

### 12.6 Snapshot 刷新失败

保留旧 Snapshot，不用失败结果覆盖。顶部标注「刷新失败，当前显示的是某时刻的 Snapshot」。

## 13．键盘与可访问性

- `Ctrl+F`：搜索。
- `↑`、`↓`：移动 Window 选中项。
- `←`、`→`：折叠或展开 Display 分组。
- `Enter`：聚焦详情区域。
- `Ctrl+C`：复制当前聚焦字段或选中 Window 摘要。
- `Ctrl+Shift+C`：复制完整 Raw block。
- `F5`：刷新 Snapshot。

主要文字与背景对比度不低于 WCAG AA。选中、焦点、警告和失败状态必须同时使用图标或文字，不只依赖颜色。所有可点击组件保留清晰的键盘焦点环。

## 14．视觉规范

### 14.1 Graphite Signal 色板

| Token | 色值 | 用途 |
|---|---|---|
| `surface.base` | `#101722` | 主内容背景 |
| `surface.raised` | `#182231` | 顶栏与抬升区域 |
| `surface.sidebar` | `#131C28` | 左侧导航 |
| `surface.detail` | `#0D1520` | 详情面板 |
| `border.default` | `#28364A` | 分隔线 |
| `text.primary` | `#E5EAF2` | 主文字 |
| `text.secondary` | `#78879C` | 次要信息 |
| `signal.focus` | `#67E8F9` | 焦点、选中、关联 |
| `signal.success` | `#86EFAC` | 正常、可见、已连接 |
| `signal.warning` | `#FCD34D` | 解析告警 |
| `signal.error` | `#FB7185` | 失败、断开 |

青色信号色不能用于普通装饰，以保证真正的焦点和关联在高密度页面中仍然醒目。

### 14.2 字体

- 导航、按钮、标签：优先使用 `Inter`，不可用时回退到 `Segoe UI`。
- Window 名称、PID、UID、Token、Raw：使用 `JetBrains Mono`，不可用时回退到 `Consolas`。
- 正文字号：`12 px`。
- 表格数据：`11 px`。
- 辅助标签：`9—10 px`。
- Raw：`11 px`，行高 `1.55—1.6`。

字体是否随应用打包由实现计划决定；V0.1 不因字体加载失败阻塞启动。

### 14.3 间距与圆角

- 基础间距单位：`4 px`。
- 页面内主要间距：`8 px`、`12 px`、`16 px`。
- 表格行高：`34 px`。
- 输入框与按钮高度：`30 px`。
- 控件圆角：`5—6 px`。
- 面板圆角：`8 px`。

避免大卡片和过量留白。信息密度优先，但每一组信息必须能被快速扫描。

## 15．组件边界

UI 实现至少拆分为以下职责单一的组件：

```text
DeviceContextBar
ModuleNavigation
WindowToolbar
WindowTreeTable
WindowRow
WindowDetailPanel
WindowOverview
RawBlockViewer
SnapshotStatusBar
InspectorEmptyState
InspectorErrorState
ParseWarningBanner
```

组件只消费 ViewModel 或 Domain Model，不直接执行 ADB，也不解析 `dumpsys`。`Refresh Snapshot` 只发送意图，由状态层调用 Snapshot／Collector 服务。

## 16．界面数据流

```text
User Action
    ↓
Window Inspector State
    ↓
Window Capture Coordinator
    ↓
WindowCollector → WindowDumpParser
    ↓
WindowInfo[] + ParseWarning[] + Raw Blocks
    ↓
Window Inspector ViewModel
    ↓
Tree Table + Detail + Status
```

`Window Capture Coordinator` 只负责本页的一次完整采集，不要求提前实现 V2.0 的统一 `SnapshotService`。后续引入统一 Snapshot 时替换协调层，UI 仍消费相同的 Window 状态。

筛选、搜索、排序和选中属于界面状态，不应触发新的 ADB 命令。只有刷新 Snapshot、切换设备和显式重试会触发采集。

## 17．V0.1 与后续扩展

### 17.1 V0.1 显示

- Window Tree Table。
- Focused、Visible 和 Has Surface 状态。
- 搜索与基础筛选。
- Overview 详情。
- Raw block。
- Snapshot 状态与解析告警。

### 17.2 后续按能力出现

- Milestone 2：Activity 关联。
- Milestone 3：Surface 关联。
- Milestone 4：Input 关联。
- Milestone 5：统一 `Related` 页签和跨 Inspector 导航。

后续能力通过新增详情页签或行内关系标识扩展，不改变三栏主布局。

## 18．验收标准

- 用户连接设备后，可以在一个屏幕内看到设备、Snapshot 时间、Window 列表和选中详情。
- Focused Window 可在 `2 s` 内通过视觉扫描识别。
- 用户可以按 package、title、PID 或 UID 搜索 Window。
- 用户可以只查看 Visible 或 Focused Window。
- 用户可以查看并复制选中 Window 的 Raw block。
- 单个 Window 解析失败不会导致列表或页面整体失败。
- 设备断开或刷新失败后，旧 Snapshot 保留且明确标注时间。
- 页面不展示尚未实现的关联操作。
- UI 层不直接执行 ADB 或解析 `dumpsys`。
- 在 `1100 × 700 px` 窗口下仍可完成搜索、选择 Window 和查看完整详情。
- 键盘可以完成搜索、列表移动、详情聚焦和刷新。

## 19．验证建议

实现阶段至少验证：

1. `1440 × 900 px` 和 `1100 × 700 px` 两种窗口尺寸。
2. 单 Display、多 Display、无 Window 和超过 `100` 个 Window 的列表。
3. 正常采集、超时、设备断开、权限不足和部分解析失败。
4. 超长 Window title、缺失 package、未知 PID／UID 和大于 `1 MB` 的 Raw block。
5. 鼠标与纯键盘操作路径。
6. 主要文字、状态标签和键盘焦点的颜色对比度。

## 20．已确认决策

- 设计对象是 Windows 主程序中的 Window Inspector，不是托盘或 Android App。
- 使用 IDE 三栏式布局。
- 默认主题使用 `Graphite Signal`。
- 主体列表使用按 Display 分组的 Tree Table。
- 详情面板常驻右侧。
- V0.1 只开放 `Overview` 与 `Raw`。
- 断开设备和刷新失败时保留旧 Snapshot。
- 未知字段和解析失败必须保留 Raw 证据。
