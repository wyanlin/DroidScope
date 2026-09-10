# Window 状态颜色与可见性筛选设计

## 目标

让 Window Inspector 中的 Focused、Visible 和 Hidden 状态可一眼区分，并允许按可见性筛选窗口。

## 状态视觉

| 状态 | 标签 | 图标 | 颜色 |
| --- | --- | --- | --- |
| Focused | `FOCUSED` | `◆` | `--signal-focus` 青色 |
| Visible | `VISIBLE` | `●` | `--signal-success` 绿色 |
| Hidden | `HIDDEN` | `○` | `--text-secondary` 中性灰 |

状态颜色只作用于状态标签；Window 标题和包名继续使用现有主／次文本色。Focused 优先于 Visible。

## 筛选

搜索框下提供单选可见性筛选：`All`、`Visible`、`Hidden`。默认 `All`。筛选条件和文本搜索采用 AND 关系；Focused 窗口在数据模型中仍按其 `visible` 字段参与 Visible／Hidden 筛选。

筛选不触发 ADB 请求，不改变当前设备和 Snapshot。结果数量显示为筛选后的数量。

## 测试契约

- Focused、Visible、Hidden 行分别带有对应状态 class 和标签。
- 选择 `Visible` 后仅保留 `visible=true` 的窗口。
- 选择 `Hidden` 后仅保留 `visible=false` 的窗口。
- 文本搜索与可见性筛选同时生效。
