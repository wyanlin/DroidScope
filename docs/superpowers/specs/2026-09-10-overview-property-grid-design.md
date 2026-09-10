# Overview 属性表设计

## 目标

将 Windows 和 Activities 页面右侧的 Overview 改为接近 WinScope 的紧凑属性视图，解决 Package、Component 等长值与字段名不对齐、难以扫描的问题。现有 Activity 与 Window 关联和跳转能力保持不变。

## 布局

Overview 由多个属性组组成。每个组有紧凑的小号标题条，属性行使用固定字段列和自适应值列：

- `Identity`：Package、Component、User。
- `Runtime`：PID、UID（仅 Window）、Display（仅 Window）、Activity State（仅 Activity）。
- `Window State`：Focused、Visible、Has Surface（仅 Window）。
- `Relationship`：关联的 Activity 或 Window 跳转项。
- `Raw`：原始 dumpsys 片段，保留为独立的底部区域。

字段列宽固定为约 `7rem`，左对齐。值列填充其余可用空间，使用等宽字体、`overflow-wrap: anywhere`，允许长 Package 和 Component 自动换行。属性行不使用浮动布局。

## 颜色和交互

沿用现有深色主题令牌：

- 字段名：低对比蓝灰（`--text-secondary`）。
- 普通值：主文字色（`--text-primary`）。
- 正向或活跃状态，例如 `true`、`RESUMED`、`FOCUSED`：成功色（`--signal-success`）或焦点色（`--signal-focus`）。
- 中性或非活跃状态，例如 `false`、`HIDDEN`：辅助文字色。
- 关联跳转：焦点色，并保持当前点击后切换页面、选中关联项的行为。

属性组标题、行分隔和选择态应使用既有 surface 与 border 令牌，避免引入独立色板。当前数据没有 WinScope 中枚举常量的语义，因此不新增紫色常量色。

## 范围与非目标

仅调整 Web UI 的展示层与样式。不会修改 Local Core API、ADB 命令、dumpsys 解析器、关联规则或 Raw 内容。不会伪造树状层级，也不会增加截图中不存在的筛选开关。

## 验证

- App 测试覆盖 Window 与 Activity Overview 的分组和关联跳转。
- 样式验证字段和值使用独立的语义类，长 Component 在值列换行。
- 现有 Windows／Activities 页面、刷新、筛选和双向跳转回归通过。
