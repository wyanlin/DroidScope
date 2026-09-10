# Overview Property Grid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Windows 与 Activities 页面的 Overview 改为紧凑、分组、对齐的属性表，并保留关联跳转与 Raw 内容。

**Architecture:** 仅重构 `App.tsx` 的详情 JSX 为可复用的属性组／属性行展示结构，并在 `app.css` 用 CSS Grid 替代当前 `dt/dd` 浮动布局。现有 `ActivityWindowSnapshot`、页面状态和关联跳转函数保持不变。

**Tech Stack:** React、TypeScript、Vite、Vitest、Testing Library、CSS 自定义属性。

---

## 文件结构

- 修改：`web/src/app/App.tsx` — 定义 `PropertyGroup`、`PropertyRow` 等纯展示组件；将 Window／Activity Overview 映射为 Identity、Runtime、State、Relationship、Raw 分组。
- 修改：`web/src/app/App.test.tsx` — 先覆盖两类 Overview 的分组、长 Component 值及关联跳转的回归行为。
- 修改：`web/src/styles/app.css` — 为属性组、属性行、状态值和关联按钮增加紧凑深色样式；移除旧 `dt/dd` 浮动规则。

### Task 1：为属性分组展示建立失败测试

**Files:**

- Modify：`web/src/app/App.test.tsx`

- [ ] **Step 1：写出 Window Overview 的失败测试**

在现有含关联 Window 的测试中，断言选中窗口后具有以下语义内容：

```tsx
expect(screen.getByText('Identity')).toBeInTheDocument()
expect(screen.getByText('Runtime')).toBeInTheDocument()
expect(screen.getByText('Window State')).toBeInTheDocument()
expect(screen.getByText('Relationship')).toBeInTheDocument()
expect(screen.getByText('Component')).toBeInTheDocument()
expect(screen.getByText('com.demo/.MainActivity')).toHaveClass('property-value')
expect(screen.getByRole('button', { name: 'Activity: com.demo/.MainActivity' })).toHaveClass('relationship-link')
```

- [ ] **Step 2：写出 Activity Overview 的失败测试**

在现有双向跳转测试中，进入 Activities 页面后断言：

```tsx
expect(screen.getByText('Identity')).toBeInTheDocument()
expect(screen.getByText('Runtime')).toBeInTheDocument()
expect(screen.getByText('Relationship')).toBeInTheDocument()
expect(screen.getByText('RESUMED')).toHaveClass('value-active')
expect(screen.getByRole('button', { name: 'Window: com.demo/.MainActivity' })).toHaveClass('relationship-link')
```

- [ ] **Step 3：运行测试确认失败**

Run：`npm.cmd test -- --run src/app/App.test.tsx`

Expected：失败，提示缺少 `Identity`、`Runtime` 或相应 CSS 类。

- [ ] **Step 4：提交测试基线**

```powershell
git add web/src/app/App.test.tsx
git commit -m "test: 覆盖 Overview 分组属性展示"
```

### Task 2：实现分组属性表与语义颜色

**Files:**

- Modify：`web/src/app/App.tsx`
- Modify：`web/src/styles/app.css`

- [ ] **Step 1：在 `App.tsx` 增加纯展示组件**

在 `activityLabel` 后定义：

```tsx
function PropertyGroup({ title, children }: { title: string; children: ReactNode }) {
  return <section className="property-group"><h3>{title}</h3>{children}</section>
}

function PropertyRow({ label, children, valueClassName = '' }: { label: string; children: ReactNode; valueClassName?: string }) {
  return <div className="property-row"><span className="property-label">{label}</span><span className={`property-value ${valueClassName}`.trim()}>{children}</span></div>
}
```

- [ ] **Step 2：将 Window detail 替换为属性组**

使用 `PropertyGroup` 与 `PropertyRow` 按以下映射输出：

```tsx
<PropertyGroup title="Identity">
  <PropertyRow label="Package">{selectedWindow.packageName ?? '—'}</PropertyRow>
  <PropertyRow label="Component">{selectedWindow.componentName ?? '—'}</PropertyRow>
  <PropertyRow label="User">{selectedWindow.userId}</PropertyRow>
</PropertyGroup>
<PropertyGroup title="Runtime">
  <PropertyRow label="PID">{selectedWindow.pid < 0 ? '—' : selectedWindow.pid}</PropertyRow>
  <PropertyRow label="UID">{selectedWindow.uid < 0 ? '—' : selectedWindow.uid}</PropertyRow>
  <PropertyRow label="Display">{selectedWindow.displayId}</PropertyRow>
</PropertyGroup>
```

将 Focused 的值类设为 `value-focus`；Visible 和 Has Surface 的 `true` 设为 `value-active`，`false` 设为 `value-muted`。关联 Activity 放入 `Relationship` 组，保留 `showActivity` 调用；Raw 放入 `Raw` 组。

- [ ] **Step 3：将 Activity detail 替换为属性组**

Identity 组输出 Package、Component、User；Runtime 组输出 PID 和 State。State 为 `RESUMED` 时传入 `value-active`，其他状态传入 `value-muted`。关联 Window 按现有 `relatedWindowIds` 循环放入 `Relationship` 组，保留 `showWindow` 调用；Raw 放入 `Raw` 组。

- [ ] **Step 4：实现紧凑属性表样式**

在 `web/src/styles/app.css` 添加：

```css
.property-group { border: 1px solid var(--border-default); border-radius: 6px; margin-bottom: .75rem; overflow: hidden; }
.property-group h3 { margin: 0; padding: .45rem .7rem; background: var(--surface-sidebar); color: var(--text-secondary); font-size: .68rem; letter-spacing: .08em; text-transform: uppercase; }
.property-row { display: grid; grid-template-columns: 7rem minmax(0, 1fr); border-top: 1px solid var(--border-default); }
.property-label { padding: .45rem .7rem; color: var(--text-secondary); }
.property-value { padding: .45rem .7rem; font-family: ui-monospace, Consolas, monospace; overflow-wrap: anywhere; }
.value-active { color: var(--signal-success); }
.value-focus, .relationship-link { color: var(--signal-focus); }
.value-muted { color: var(--text-secondary); }
```

将 `.window-detail pre` 的上边距设为零，并删除全局 `dt`、`dd` 浮动规则。

- [ ] **Step 5：运行属性测试确认通过**

Run：`npm.cmd test -- --run src/app/App.test.tsx`

Expected：`App.test.tsx` 中的全部测试通过，其中 Overview 分组与跳转断言通过。

- [ ] **Step 6：提交实现**

```powershell
git add web/src/app/App.tsx web/src/styles/app.css web/src/app/App.test.tsx
git commit -m "feat: 采用紧凑分组属性展示 Overview"
```

### Task 3：完整回归与生产构建

**Files:**

- Verify：`web/src/app/App.test.tsx`
- Verify：`web/src/local-client/HttpDroidScopeClient.test.ts`

- [ ] **Step 1：运行完整 Web 测试**

Run：`npm.cmd test -- --run`

Expected：所有 Vitest 测试通过。

- [ ] **Step 2：运行生产构建**

Run：`npm.cmd run build`

Expected：`tsc -b && vite build` 退出码为 0。

- [ ] **Step 3：复核工作树**

Run：`git status --short`

Expected：无未提交的源代码改动。

- [ ] **Step 4：提交验证状态（仅当验证引入文件改动时）**

若没有文件改动，不创建空提交。
