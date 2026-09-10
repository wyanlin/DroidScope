# Window 状态颜色与可见性筛选 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用不同颜色标识 Focused、Visible、Hidden 状态，并允许按可见和隐藏状态筛选 Window。

**Architecture:** 在 `App` 内维护一个单选 `visibilityFilter` UI 状态，与既有文本搜索组合计算列表。Window 行根据既有 `focused`、`visible` 字段生成状态 class，CSS 使用已有设计 token 定义不同视觉。

**Tech Stack:** React 19、TypeScript、Vitest、Testing Library、CSS custom properties。

---

### Task 1: 状态筛选与状态 class

**Files:**
- Modify: `web/src/app/App.tsx`
- Modify: `web/src/app/App.test.tsx`

- [ ] **Step 1: 写出失败的筛选与状态 class 测试**

```tsx
const snapshot = {
  focusedTarget: 'focused',
  windows: [
    { order: 0, title: 'focused', packageName: null, displayId: 0, pid: -1, uid: -1, focused: true, visible: true, hasSurface: true, rawBlock: '' },
    { order: 1, title: 'visible', packageName: null, displayId: 0, pid: -1, uid: -1, focused: false, visible: true, hasSurface: true, rawBlock: '' },
    { order: 2, title: 'hidden', packageName: null, displayId: 0, pid: -1, uid: -1, focused: false, visible: false, hasSurface: false, rawBlock: '' },
  ],
}
expect(await screen.findByRole('button', { name: /focused/ })).toHaveClass('state-focused')
fireEvent.click(screen.getByRole('button', { name: 'Visible' }))
expect(screen.getByRole('button', { name: /visible/ })).toBeInTheDocument()
expect(screen.queryByRole('button', { name: /hidden/ })).not.toBeInTheDocument()
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- --run` in `web`

Expected: FAIL，找不到 `Visible` 筛选按钮和 `state-focused` class。

- [ ] **Step 3: 实现最小筛选和状态 class**

```tsx
type VisibilityFilter = 'all' | 'visible' | 'hidden'
const [visibilityFilter, setVisibilityFilter] = useState<VisibilityFilter>('all')
const matchesVisibility = visibilityFilter === 'all'
  || (visibilityFilter === 'visible' && window.visible)
  || (visibilityFilter === 'hidden' && !window.visible)
const stateClass = window.focused ? 'state-focused' : window.visible ? 'state-visible' : 'state-hidden'
```

在搜索框下添加 `All`、`Visible`、`Hidden` 三个 button，点击时更新 `visibilityFilter`。将 `stateClass` 添加至 Window 行的状态标签。

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test -- --run` in `web`

Expected: PASS，Visible 和 Hidden 筛选只显示匹配行，Focused、Visible、Hidden 行分别有对应 class。

### Task 2: 状态色与实机回归

**Files:**
- Modify: `web/src/styles/app.css`

- [ ] **Step 1: 为三种状态写最小样式**

```css
.state-focused { color: var(--signal-focus); }
.state-visible { color: var(--signal-success); }
.state-hidden { color: var(--text-secondary); }
```

按钮筛选使用现有边框色；激活项使用 `--signal-focus`，不新增色值。

- [ ] **Step 2: 构建 Web 与 Local Core**

Run: `node scripts/build-local-core.mjs`

Expected: Vitest 全部通过，输出 `Local Core JAR created`。

- [ ] **Step 3: 生成 Windows app-image 并实机验证**

Run: `$env:DROID_SCOPE_PACKAGE_OUTPUT='DroidScope-win32-state-filter'; node scripts/package-local-core.mjs`

Expected: 输出 `Desktop application created for win32`。在连接设备上确认 Focused 为青色、Visible 为绿色、Hidden 为灰色；Visible／Hidden 筛选正确改变列表。

- [ ] **Step 4: 提交**

```powershell
git add web/src/app/App.tsx web/src/app/App.test.tsx web/src/styles/app.css
git commit -m "feat: 区分窗口状态颜色并支持可见性筛选"
```
