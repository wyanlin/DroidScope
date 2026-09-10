# Activity ↔ Window 关联实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在同一刷新快照内采集 Activity 与 Window，建立严格双向关联，并提供独立的 Activities Inspector。

**Architecture:** Local Core 并行采集固定的 `dumpsys window` 与 `dumpsys activity activities` 输出；Activity Parser 和 Window Parser 各自产生 Raw 可追溯的领域对象，Resolver 仅以 `userId + packageName + componentName` 建立关系。Web UI 请求一次关联快照，在 Windows 与 Activities 两个页面共享该快照并支持双向跳转。

**Tech Stack:** Java 17、JDK HttpServer、ADB、React、TypeScript、Vite、Vitest、Testing Library、Node.js。

---

### Task 1：补齐 Window 关联键并建立 Activity 领域模型

**Files:**

- Modify: `windows/src/main/java/com/droidscope/window/WindowInfo.java`
- Modify: `windows/src/main/java/com/droidscope/window/WindowDumpParser.java`
- Modify: `windows/src/test/java/com/droidscope/window/WindowDumpParserTest.java`
- Create: `windows/src/main/java/com/droidscope/activity/ActivityInfo.java`
- Create: `windows/src/main/java/com/droidscope/activity/ActivitySnapshot.java`
- Create: `windows/src/main/java/com/droidscope/activity/ActivityDumpParser.java`
- Create: `windows/src/test/java/com/droidscope/activity/ActivityDumpParserTest.java`

- [ ] **Step 1: Write failing parser tests using the observed Android format**

```java
String dump = "topResumedActivity=ActivityRecord{a2bcf31 u0 com.android.launcher3/.uioverrides.QuickstepLauncher t1285}\n"
        + "  * Hist  #0: ActivityRecord{a2bcf31 u0 com.android.launcher3/.uioverrides.QuickstepLauncher t1285}\n"
        + "    app=ProcessRecord{207d6bb 2795:com.android.launcher3/u0a171}\n"
        + "    mActivityComponent=com.android.launcher3/.uioverrides.QuickstepLauncher\n";
ActivityInfo activity = new ActivityDumpParser().parse(dump).activities().get(0);
assertEquals(0, activity.userId());
assertEquals("com.android.launcher3", activity.packageName());
assertEquals("com.android.launcher3/.uioverrides.QuickstepLauncher", activity.componentName());
assertEquals(2795, activity.pid());
assertEquals(ActivityInfo.State.RESUMED, activity.state());
```

补充未知 PID、无 `mActivityComponent` 与不完整 block 的用例；测试期望不抛出异常，并保留解析成功记录的 Raw。

Run: `javac -encoding UTF-8 -d build-out/test-classes windows/src/main/java/com/droidscope/activity/*.java windows/src/test/java/com/droidscope/activity/ActivityDumpParserTest.java && java -cp build-out/test-classes com.droidscope.activity.ActivityDumpParserTest`

Expected: FAIL，因为类型和 Parser 尚不存在。

- [ ] **Step 2: Add immutable models and the minimal parser**

`ActivityInfo` 固定定义 `State { RESUMED, PAUSED, UNKNOWN }`，以及 `id()`：

```java
public String id() {
    return "u" + userId + ":" + componentName;
}
```

Parser 以 `* Hist` 行分割 Activity block；从 `ActivityRecord` 提取 userId 与 component，从 `mActivityComponent` 覆盖 component，从 `ProcessRecord` 提取 PID。`topResumedActivity` 中的相同 `u<userId> <component>` 标识为 `RESUMED`；与 `mLastPausedActivity` 匹配的记录标识为 `PAUSED`；其余为 `UNKNOWN`。

Window 模型新增 `userId`、`componentName` 与可空 `relatedActivityId`。Window Parser 从 `Window{… u<userId> <component>}` 头部提取前两项；旧 JSON 输出保持既有字段，并追加这三个字段。

- [ ] **Step 3: Run parser tests and the existing Window parser test**

Run: `javac -encoding UTF-8 -d build-out/test-classes windows/src/main/java/com/droidscope/window/*.java windows/src/test/java/com/droidscope/window/WindowDumpParserTest.java windows/src/main/java/com/droidscope/activity/*.java windows/src/test/java/com/droidscope/activity/ActivityDumpParserTest.java && java -cp build-out/test-classes com.droidscope.window.WindowDumpParserTest && java -cp build-out/test-classes com.droidscope.activity.ActivityDumpParserTest`

Expected: both commands exit `0`.

- [ ] **Step 4: Commit**

```powershell
git add windows/src/main/java/com/droidscope/window windows/src/main/java/com/droidscope/activity windows/src/test/java/com/droidscope/window/WindowDumpParserTest.java windows/src/test/java/com/droidscope/activity/ActivityDumpParserTest.java
git commit -m "feat: 解析 Activity 快照与 Window 关联键"
```

### Task 2：实现严格关联、统一快照与 JSON

**Files:**

- Create: `windows/src/main/java/com/droidscope/activity/ActivityWindowResolver.java`
- Create: `windows/src/main/java/com/droidscope/activity/ActivityWindowSnapshot.java`
- Create: `windows/src/main/java/com/droidscope/activity/ActivityWindowJson.java`
- Create: `windows/src/test/java/com/droidscope/activity/ActivityWindowResolverTest.java`

- [ ] **Step 1: Write the failing resolver test**

```java
ActivityInfo activity = activity(0, "com.demo", "com.demo/.MainActivity");
WindowInfo matched = window(0, "com.demo", "com.demo/.MainActivity");
WindowInfo wrongUser = window(10, "com.demo", "com.demo/.MainActivity");
ActivityWindowSnapshot snapshot = new ActivityWindowResolver().resolve(List.of(activity), List.of(matched, wrongUser));
assertEquals(activity.id(), snapshot.windows().get(0).relatedActivityId());
assertEquals(null, snapshot.windows().get(1).relatedActivityId());
assertEquals(List.of(matched.id()), snapshot.activities().get(0).relatedWindowIds());
```

Run: `javac -encoding UTF-8 -d build-out/test-classes windows/src/main/java/com/droidscope/window/*.java windows/src/main/java/com/droidscope/activity/*.java windows/src/test/java/com/droidscope/activity/ActivityWindowResolverTest.java && java -cp build-out/test-classes com.droidscope.activity.ActivityWindowResolverTest`

Expected: FAIL，因为 Resolver 与统一 Snapshot 尚不存在。

- [ ] **Step 2: Implement exact-key resolution and JSON encoding**

Resolver 使用 `Map<String, ActivityInfo>` 以 `ActivityInfo.id()` 建索引。`WindowInfo` 保持不可变，并提供 `withRelatedActivityId(String)` 返回带关系的新实例。只有 Window 的 `userId >= 0`、package、component 非空且 map 中存在精确 key 时写入 `relatedActivityId`；同一 Activity 以 Window 的稳定 `id()` 收集关联窗口。任何字段缺失都返回未关联。

`ActivityWindowJson.encode` 返回：

```json
{
  "capturedAtEpochMs": 0,
  "windows": [{"id":"window:0","relatedActivityId":"u0:com.demo/.MainActivity"}],
  "activities": [{"id":"u0:com.demo/.MainActivity","relatedWindowIds":["window:0"]}]
}
```

字符串沿用 `WindowJson` 的转义规则；不把 Raw 拼入关联字段。

- [ ] **Step 3: Verify exact matching and JSON contract**

Run: `java -cp build-out/test-classes com.droidscope.activity.ActivityWindowResolverTest`

Expected: exit `0`，且测试覆盖 userId、package、component 分别不一致时不关联。

- [ ] **Step 4: Commit**

```powershell
git add windows/src/main/java/com/droidscope/activity windows/src/main/java/com/droidscope/window/WindowInfo.java windows/src/test/java/com/droidscope/activity/ActivityWindowResolverTest.java
git commit -m "feat: 建立 Activity 与 Window 严格关联快照"
```

### Task 3：接入 ADB 与受保护的关联快照接口

**Files:**

- Modify: `windows/src/main/java/com/droidscope/adb/AdbManager.java`
- Modify: `windows/src/main/java/com/droidscope/server/ReceiverServer.java`
- Create: `windows/src/main/java/com/droidscope/activity/ActivityWindowHandler.java`
- Create: `windows/src/test/java/com/droidscope/activity/ActivityWindowHandlerTest.java`
- Modify: `windows/src/test/java/com/droidscope/window/WindowEndpointDeviceSmokeTest.java`

- [ ] **Step 1: Write failing handler tests**

使用注入的 `Function<String, String>` 代替真实 ADB，断言：缺失 `serial` 返回 `400`；非 GET 返回 `405`；两个 dump 均成功时返回 `200` 和 `activities`／`windows`；任一 dump 抛异常时返回 `502` 与 `ACTIVITY_WINDOW_CAPTURE_FAILED`。

Run:

```powershell
$mainSources = Get-ChildItem -Recurse -Filter '*.java' windows/src/main/java | ForEach-Object FullName
javac -encoding UTF-8 -d build-out/test-classes $mainSources windows/src/test/java/com/droidscope/activity/ActivityWindowHandlerTest.java
java -cp build-out/test-classes com.droidscope.activity.ActivityWindowHandlerTest
```

Expected: FAIL，因为 Handler 和新接口尚不存在。

- [ ] **Step 2: Add fixed ADB collection and server wiring**

在 `AdbManager` 增加：

```java
public String dumpActivities(String serial) throws IOException, InterruptedException {
    CommandResult result = run("-s", serial, "shell", "dumpsys", "activity", "activities");
    if (result.exitCode() != 0) throw new IOException("dumpsys activity activities failed: " + result.output());
    return result.output();
}
```

`ReceiverServer` 注册 `/api/v1/activity-window-snapshot`，复用现有 `SessionGuard`。Handler 并行启动两个固定 provider，全部成功后 parse、resolve、encode；任一失败时不返回局部快照。

- [ ] **Step 3: Add manual connected-device smoke verification**

将现有 smoke test 改为请求：

```text
GET /api/v1/activity-window-snapshot?serial=<serial>
```

断言 HTTP `200`，并包含 `"activities":`、`"windows":` 与至少一个 Raw 字段。

Run: `java -cp build-out/test-classes com.droidscope.window.WindowEndpointDeviceSmokeTest bc22c05f`

Expected: exit `0`；无设备时该步骤记录为待验证，不伪造通过。

- [ ] **Step 4: Commit**

```powershell
git add windows/src/main/java/com/droidscope/adb/AdbManager.java windows/src/main/java/com/droidscope/server/ReceiverServer.java windows/src/main/java/com/droidscope/activity windows/src/test/java/com/droidscope/activity/ActivityWindowHandlerTest.java windows/src/test/java/com/droidscope/window/WindowEndpointDeviceSmokeTest.java
git commit -m "feat: 提供受保护的 Activity Window 关联快照接口"
```

### Task 4：让 Web Client 消费统一快照

**Files:**

- Modify: `web/src/local-client/DroidScopeClient.ts`
- Modify: `web/src/local-client/HttpDroidScopeClient.ts`
- Modify: `web/src/local-client/HttpDroidScopeClient.test.ts`

- [ ] **Step 1: Write the failing HTTP client contract test**

```ts
const snapshot = await client.getActivityWindowSnapshot('ABC123')
expect(fetch).toHaveBeenCalledWith(
  '/api/v1/activity-window-snapshot?serial=ABC123',
  { headers: { 'X-DroidScope-Session': 'token' } },
)
expect(snapshot.activities[0].relatedWindowIds).toEqual(['window:0'])
```

Run: `npm.cmd test -- --run web/src/local-client/HttpDroidScopeClient.test.ts`

Expected: FAIL，因为 Client 方法与 Activity 类型尚不存在。

- [ ] **Step 2: Add shared TypeScript snapshot types and one request method**

在 `DroidScopeClient.ts` 新增 `ActivityInfo` 与 `ActivityWindowSnapshot`，Window 类型新增 `id`、`userId`、`componentName`、`relatedActivityId`。接口替换为：

```ts
getActivityWindowSnapshot(serial: string): Promise<ActivityWindowSnapshot>
```

`HttpDroidScopeClient` 只请求上述固定 URL，沿用 session header 与非 2xx 错误文本。

- [ ] **Step 3: Run the client tests**

Run: `npm.cmd test -- --run web/src/local-client/HttpDroidScopeClient.test.ts`

Expected: PASS。

- [ ] **Step 4: Commit**

```powershell
git add web/src/local-client/DroidScopeClient.ts web/src/local-client/HttpDroidScopeClient.ts web/src/local-client/HttpDroidScopeClient.test.ts
git commit -m "feat: 让 Web Client 请求关联快照"
```

### Task 5：实现两个独立 Inspector 页面与双向跳转

**Files:**

- Modify: `web/src/app/App.tsx`
- Modify: `web/src/app/App.test.tsx`
- Modify: `web/src/styles/app.css`

- [ ] **Step 1: Write failing UI tests**

测试最少覆盖：

```ts
it('opens the related Activity from a selected Window', async () => {
  render(<App client={clientWithLinkedSnapshot()} />)
  fireEvent.click(await screen.findByRole('button', { name: /MainActivity/ }))
  fireEvent.click(screen.getByRole('button', { name: /Related Activity.*MainActivity/ }))
  expect(screen.getByRole('heading', { name: 'Activities' })).toBeInTheDocument()
  expect(screen.getByText('com.demo/.MainActivity')).toBeInTheDocument()
})
```

另写反向跳转、未关联显示、一次刷新只调用 `getActivityWindowSnapshot` 一次、Activities 搜索和旧快照在刷新失败后仍显示的测试。

Run: `npm.cmd test -- --run web/src/app/App.test.tsx`

Expected: FAIL，因为当前 UI 只有 Windows 页面和 `getWindows`。

- [ ] **Step 2: Implement shared snapshot state and inspector navigation**

将当前 `windows` state 替换为一个 `snapshot` state。导航 state 为：

```ts
type Inspector = 'windows' | 'activities'
```

刷新时仅调用一次 `client.getActivityWindowSnapshot(selectedSerial)`；请求失败时不清空 `snapshot`，只更新错误状态。Window 与 Activity 的选中项分别存储为 stable `id`，点击关系项设置目标 inspector 与目标 id。

Windows Overview 增加 `Related Activity`；Activities 页面实现搜索、列表、Overview、`Related Windows` 和 Raw。关联为空时显示 `Unlinked`，不渲染跳转按钮。

- [ ] **Step 3: Run focused UI tests and production build**

Run: `npm.cmd test -- --run web/src/app/App.test.tsx`

Expected: PASS。

Run: `npm.cmd run build`

Expected: TypeScript 与 Vite 均成功。

- [ ] **Step 4: Commit**

```powershell
git add web/src/app/App.tsx web/src/app/App.test.tsx web/src/styles/app.css
git commit -m "feat: 增加 Activity Inspector 与双向跳转"
```

### Task 6：端到端验证、打包与路线图

**Files:**

- Modify: `ROADMAP.md`

- [ ] **Step 1: Run all automated validation**

Run: `node scripts/build-local-core.mjs`

Expected: Web UI 测试、生产构建、Java 生产编译和 Local Core JAR 创建均成功。

Run: `node scripts/verify-offline.mjs`

Expected: 输出 `Offline resource scan passed`。

- [ ] **Step 2: Build the Windows app-image**

Run: `powershell -ExecutionPolicy Bypass -File windows/package.ps1`

Expected: `build-out/DroidScope-win32/DroidScope/DroidScope.exe` 已生成。

- [ ] **Step 3: Perform device acceptance**

在已连接设备上验证：刷新后 Windows／Activities 均有数据；点击双向关联正确跳转；不匹配 userId、package 或 component 的记录显示未关联；断开设备后保留最后成功快照并显示采集错误。

- [ ] **Step 4: Update roadmap and commit**

将本地 `ROADMAP.md` 的「进行中」改为已完成，并将下一步调整为 Surface ↔ Window 关联；记录实际构建产物路径与真机验证结果。该文件按项目约定被 `.gitignore` 忽略，不纳入提交。

```powershell
git add windows web docs
git commit -m "feat: 完成 Activity Window 关联验收"
```
