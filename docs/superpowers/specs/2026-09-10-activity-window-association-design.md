# DroidScope Activity ↔ Window 关联设计

## 1．目标

在现有本地 Web Workbench 中增加独立的 `Activities` Inspector，并与 `Windows` Inspector 建立可验证的双向关联。用户可以从选中 Window 跳转到关联 Activity，也可以从 Activity 跳转到关联 Windows。

首版只处理当前 Activity 状态与 Window 的关系，不实现 Task、Intent、生命周期历史、进程树、Surface 或 Input 关联。

## 2．采集与快照

用户点击刷新后，Local Core 为同一设备、同一刷新周期执行以下固定命令：

```text
adb -s <serial> shell dumpsys window
adb -s <serial> shell dumpsys activity activities
```

两份输出组成一个 Activity-Window 关联快照。浏览器不能传入任意 shell 参数，也不直接执行 ADB。

关联快照应包含采集时间、目标设备 serial、Window Snapshot、Activity Snapshot 与关联结果。页面刷新时只请求一次关联快照，Windows 与 Activities 页面共享这份快照，避免独立刷新导致数据时刻不一致。

## 3．领域模型

`ActivityInfo` 包含：

- `userId`。
- `packageName`。
- `componentName`。
- `pid`，未知时为 `-1`。
- `state`。
- `rawBlock`。
- `relatedWindowIds`。

`WindowInfo` 增加 `relatedActivityId`，没有可靠关联时为 `null`。

关联使用规范化后的三元组：`userId + packageName + componentName`。三项都存在且完全相等时才建立关系；标题相似、package 相同或 PID 相同都不足以建立关联。缺少任一项或无法匹配时，页面显示「未关联」。

一个 Activity 可关联零至多个 Window；一个 Window 最多关联一个 Activity。

## 4．Local Core

新增以下职责明确的组件：

- `ActivityDumpParser`：从 `dumpsys activity activities` 提取独立 Activity block；无法识别的 block 保留在 Raw，不阻断其他记录。
- `ActivitySnapshot`：保存解析出的 Activity 列表。
- `ActivityWindowResolver`：仅依据三元组建立双向关联，不执行 ADB，不解析 Raw。
- `ActivityWindowHandler`：执行统一采集、解析、关联和 JSON 响应。

新增受 session token 保护的固定接口：

```text
GET /api/v1/activity-window-snapshot?serial=<serial>
```

现有 `GET /api/v1/windows` 保持可用，不改变 Android 文件传输接口。

当任一命令启动失败、超时或返回非零时，接口返回明确的采集错误。前端保留上一份成功快照并展示错误，不清空现有列表。

## 5．Web UI

左侧一级导航增加 `Windows` 与 `Activities` 两个独立页面。两者共享设备选择、刷新按钮、加载状态和同一份关联快照。

Windows 页面保持现有列表、搜索、Visible／Hidden 筛选、Overview 与 Raw。Window Overview 新增 `Related Activity`：有关联时显示可点击 component；无关联时显示「未关联」。

Activities 页面提供：

- package、component、PID 或状态搜索。
- Activity 列表与当前状态。
- Overview：userId、package、component、PID、状态。
- `Related Windows` 可点击列表。
- Raw block。

点击关联项后切换到对应 Inspector，并选中目标记录。没有关联时不出现不可用跳转。

## 6．错误与边界

- Activity 或 Window 的单条解析失败不能使整份快照失败。
- 没有 Activity、没有 Window 或没有关联均是正常空态。
- 多用户／工作资料场景必须使用 `userId` 隔离，禁止跨用户关联。
- 大 Raw 输出仅在详情中显示，不在列表重复传递或渲染。
- 设备断开、ADB unauthorized 与超时沿用现有错误展示模式。

## 7．验收标准

- 一次刷新只发起一份关联快照请求。
- 可从关联 Window 跳转到唯一 Activity。
- 可从 Activity 跳转到其全部关联 Windows。
- `userId`、package、component 任一不一致时不建立关联。
- 无关联记录显示「未关联」，不会出现跳转目标。
- 两个 Inspector 都可查看对应 Raw block。
- 正常、空结果、局部解析失败和采集失败均有自动化覆盖。
