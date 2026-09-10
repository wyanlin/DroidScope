# DroidScope Local-first Web Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有 Android → PC 文件传输行为的前提下，让 DroidScope Local Core 能在 Windows、macOS 和 Linux 上提供完全本地的 Web UI、受保护的设备状态 interface，并自动打开系统浏览器。

**Architecture:** 保留现有 Java Local Core 与 `127.0.0.1:9527` Receiver，新增 React + TypeScript + Vite 静态 UI。Java 进程同时提供静态资源和受 session token 保护的本地 interface；现有 Android `/api/v1/ping` 与 `/api/v1/files` 保持兼容。构建阶段把 Web 产物放进 JAR，运行阶段不访问公网。

**Tech Stack:** Java 17、JDK `HttpServer`、React、TypeScript、Vite、Vitest、Testing Library、Node.js 构建脚本、JDK `jpackage`。

---

## 1．范围与执行门禁

本计划只实现 Local-first Web Foundation：

- 本地 React 页面。
- Local Core 静态资源服务。
- session token 与 Origin 校验。
- 设备列表 interface。
- 设备状态 SSE。
- 自动打开系统浏览器。
- 跨平台构建入口和断网验证。

本计划不实现 Window Parser、Window Snapshot interface、Window Inspector 业务页面、Tauri、Electron、Perfetto 或 WangBox 集成。Foundation 验收后，根据真实 `dumpsys window` fixture 单独编写 Window Inspector 实施计划。

新增前端依赖前需按项目规范取得用户确认。所有依赖只在构建期从包仓库获取，运行时不得联网。

## 2．文件结构

### 新建文件

```text
web/package.json
web/package-lock.json
web/tsconfig.json
web/tsconfig.app.json
web/vite.config.ts
web/index.html
web/src/main.tsx
web/src/app/App.tsx
web/src/app/App.test.tsx
web/src/local-client/DroidScopeClient.ts
web/src/local-client/HttpDroidScopeClient.ts
web/src/local-client/session.ts
web/src/local-client/session.test.ts
web/src/styles/tokens.css
web/src/styles/app.css

windows/src/main/java/com/droidscope/local/BrowserLauncher.java
windows/src/main/java/com/droidscope/local/SessionToken.java
windows/src/main/java/com/droidscope/local/SessionGuard.java
windows/src/main/java/com/droidscope/local/StaticAssetHandler.java
windows/src/main/java/com/droidscope/local/DeviceJson.java
windows/src/main/java/com/droidscope/local/DeviceHandler.java
windows/src/main/java/com/droidscope/local/DeviceEventBroker.java
windows/src/main/java/com/droidscope/local/EventTicketStore.java
windows/src/main/java/com/droidscope/local/EventTicketHandler.java
windows/src/main/java/com/droidscope/local/DeviceEventsHandler.java

windows/src/test/java/com/droidscope/local/SessionTokenTest.java
windows/src/test/java/com/droidscope/local/SessionGuardTest.java
windows/src/test/java/com/droidscope/local/StaticAssetHandlerTest.java
windows/src/test/java/com/droidscope/local/DeviceJsonTest.java
windows/src/test/java/com/droidscope/local/DeviceEventBrokerTest.java
windows/src/test/java/com/droidscope/local/EventTicketStoreTest.java

scripts/build-local-core.mjs
scripts/package-local-core.mjs
scripts/verify-offline.mjs
docs/local-first-runtime.md
```

### 修改文件

```text
windows/src/main/java/com/droidscope/Main.java
windows/src/main/java/com/droidscope/server/ReceiverServer.java
windows/src/main/java/com/droidscope/adb/AdbMonitor.java
windows/build.ps1
windows/package.ps1
.gitignore
README.md
docs/WINDOWS_BUILD.md
docs/SCRIPTS.md
docs/private/ROADMAP.md
```

`windows/` 暂时保留历史名称。本计划不得移动或删除该目录；跨平台构建验证完成后，再单独讨论目录改名。

执行前必须先检查工作区。当前 `Main.java`、`SingleInstanceLock.java` 和 `SingleInstanceLockTest.java` 存在尚未纳入本计划的改动；Task 7 修改 `Main.java` 时必须在现有内容上做最小合并，不得覆盖、回滚或顺带重构这些改动。若其状态已变化，以执行时的 `git status` 和 diff 为准。

## 3．核心 interface

浏览器只允许使用以下最小 interface：

```typescript
export type DeviceState = "device" | "unauthorized" | "offline" | "unknown";

export interface DeviceSummary {
  serial: string;
  state: DeviceState;
  ready: boolean;
}

export interface CoreHealth {
  status: "ok";
  platform: string;
  version: string;
}

export interface DroidScopeClient {
  getHealth(): Promise<CoreHealth>;
  listDevices(): Promise<DeviceSummary[]>;
  subscribeDevices(onDevices: (devices: DeviceSummary[]) => void): () => void;
}
```

此 interface 不接受 shell 字符串或 ADB 参数。后续 Window Inspector 通过新的定向方法扩展，不增加通用命令执行入口。

Task 5 先实现 `getHealth` 和 `listDevices`，Task 6 再增加 `subscribeDevices`；每个 Task 结束时接口与实现必须完整匹配并能通过类型检查。

## 4．Task 1：建立 Web UI 可测试基线

**Files:**

- Create: `web/package.json`
- Create: `web/vite.config.ts`
- Create: `web/index.html`
- Create: `web/src/main.tsx`
- Create: `web/src/app/App.tsx`
- Create: `web/src/app/App.test.tsx`
- Create: `web/src/styles/tokens.css`
- Create: `web/src/styles/app.css`

- [ ] **Step 1：取得新增前端依赖确认**

说明依赖用途：React 负责 UI，Vite 负责静态构建，Vitest 与 Testing Library 负责浏览器界面回归。未确认前不执行安装命令。

- [ ] **Step 2：创建 Vite React TypeScript 工程**

Run：

```powershell
npm create vite@latest web -- --template react-ts
Set-Location web
npm install
npm install --save-dev vitest jsdom @testing-library/react @testing-library/jest-dom
```

Expected：生成 `web/package-lock.json`，`npm run build` 可以执行。

- [ ] **Step 3：先写失败的 App 测试**

```tsx
import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("App", () => {
  it("shows the local core connection state", () => {
    render(<App />);
    expect(screen.getByText("DroidScope")).toBeInTheDocument();
    expect(screen.getByText("Connecting to Local Core…")).toBeInTheDocument();
  });
});
```

- [ ] **Step 4：运行测试并确认失败**

Run：

```powershell
npm --prefix web test -- --run
```

Expected：FAIL，原因是 `App` 尚未导出或页面文本不存在。

- [ ] **Step 5：实现最小 App 和 Graphite Signal Token**

```tsx
export function App() {
  return (
    <main className="app-shell">
      <h1>DroidScope</h1>
      <p>Connecting to Local Core…</p>
    </main>
  );
}
```

```css
:root {
  color-scheme: dark;
  --surface-base: #101722;
  --surface-raised: #182231;
  --surface-sidebar: #131c28;
  --border-default: #28364a;
  --text-primary: #e5eaf2;
  --text-secondary: #78879c;
  --signal-focus: #67e8f9;
  --signal-success: #86efac;
  --signal-warning: #fcd34d;
  --signal-error: #fb7185;
}
```

- [ ] **Step 6：运行测试与构建**

Run：

```powershell
npm --prefix web test -- --run
npm --prefix web run build
```

Expected：测试 PASS，构建生成 `web/dist/index.html` 和本地 assets；HTML 的 `src`／`href`、CSS `url()` 以及应用代码主动加载的资源不得指向 `http://`、`https://` 或 `//cdn`。依赖库内部不触发请求的文档字符串和 XML 命名空间不视为运行时引用。

- [ ] **Step 7：提交**

```powershell
git add web
git commit -m "feat: 建立本地 Web UI 构建与测试基线"
```

## 5．Task 2：实现浏览器 session token

**Files:**

- Create: `windows/src/main/java/com/droidscope/local/SessionToken.java`
- Create: `windows/src/test/java/com/droidscope/local/SessionTokenTest.java`
- Create: `web/src/local-client/session.ts`
- Create: `web/src/local-client/session.test.ts`

- [ ] **Step 1：写 Java token 失败测试**

```java
package com.droidscope.local;

public final class SessionTokenTest {
    public static void main(String[] args) {
        String first = SessionToken.create();
        String second = SessionToken.create();
        if (first.length() < 40) throw new AssertionError("token must contain at least 256 bits");
        if (!first.matches("[A-Za-z0-9_-]+")) throw new AssertionError("token must be URL safe");
        if (first.equals(second)) throw new AssertionError("tokens must be unique");
    }
}
```

- [ ] **Step 2：确认 Java 测试失败**

Run：编译并运行 `SessionTokenTest`。

Expected：FAIL，`SessionToken` 不存在。

- [ ] **Step 3：实现 Java token**

```java
package com.droidscope.local;

import java.security.SecureRandom;
import java.util.Base64;

public final class SessionToken {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionToken() {}

    public static String create() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

- [ ] **Step 4：写并实现浏览器 session 读取测试**

```ts
export function readSessionToken(location: Location, storage: Storage): string {
  const params = new URLSearchParams(location.hash.replace(/^#/, ""));
  const fromHash = params.get("token");
  if (fromHash) {
    storage.setItem("droidscope.session", fromHash);
    history.replaceState(null, "", `${location.pathname}${location.search}`);
    return fromHash;
  }
  return storage.getItem("droidscope.session") ?? "";
}
```

测试必须覆盖从 fragment 读取、写入 `sessionStorage`、清除 fragment，以及刷新后从 `sessionStorage` 恢复。

- [ ] **Step 5：运行 Java 与 Web 测试**

Expected：所有 token 测试 PASS。

- [ ] **Step 6：提交**

```powershell
git add windows/src/main/java/com/droidscope/local/SessionToken.java windows/src/test/java/com/droidscope/local/SessionTokenTest.java web/src/local-client
git commit -m "feat: 增加本地页面会话令牌"
```

## 6．Task 3：提供 JAR 内嵌静态 Web 资源

**Files:**

- Create: `windows/src/main/java/com/droidscope/local/StaticAssetHandler.java`
- Create: `windows/src/test/java/com/droidscope/local/StaticAssetHandlerTest.java`
- Modify: `web/vite.config.ts`

- [ ] **Step 1：写路径映射和 MIME 测试**

测试以下映射：

```text
/ui/                 -> web/index.html, text/html
/ui/assets/app.js    -> web/assets/app.js, text/javascript
/ui/assets/app.css   -> web/assets/app.css, text/css
/ui/../secret        -> HTTP 400
/ui/missing.js       -> HTTP 404
```

- [ ] **Step 2：确认测试失败**

Expected：FAIL，`StaticAssetHandler` 不存在。

- [ ] **Step 3：实现受限资源路径解析**

```java
private static String resourceName(String requestPath) {
    String relative = requestPath.substring("/ui/".length());
    if (relative.isEmpty()) relative = "index.html";
    if (relative.contains("..") || relative.startsWith("/")) {
        throw new IllegalArgumentException("invalid asset path");
    }
    return "web/" + relative;
}
```

Handler 从 `StaticAssetHandler.class.getClassLoader().getResourceAsStream(resourceName)` 读取资源，设置固定 MIME、`X-Content-Type-Options: nosniff` 和 `Cache-Control`。HTML 使用 `no-store`，带 hash 的 assets 使用长期缓存。

- [ ] **Step 4：配置 Vite base**

```ts
export default defineConfig({
  base: "/ui/",
  plugins: [react()],
  test: { environment: "jsdom" },
});
```

- [ ] **Step 5：运行测试**

Expected：合法资源返回正确内容和 MIME，路径穿越被拒绝。

- [ ] **Step 6：提交**

```powershell
git add web/vite.config.ts windows/src/main/java/com/droidscope/local/StaticAssetHandler.java windows/src/test/java/com/droidscope/local/StaticAssetHandlerTest.java
git commit -m "feat: 从本地 Core 提供内嵌 Web 资源"
```

## 7．Task 4：保护本地 interface

**Files:**

- Create: `windows/src/main/java/com/droidscope/local/SessionGuard.java`
- Create: `windows/src/test/java/com/droidscope/local/SessionGuardTest.java`

- [ ] **Step 1：写失败测试**

覆盖：正确 token＋正确 Origin 通过；缺 token、错误 token、错误 Origin、`Origin: null` 均返回 `403`；没有 Origin 的非浏览器测试请求只有显式允许的 health endpoint 可以通过。

- [ ] **Step 2：实现固定时间 token 比较**

```java
private static boolean tokenMatches(String expected, String actual) {
    if (actual == null) return false;
    return java.security.MessageDigest.isEqual(
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
}
```

允许的 Origin 固定为 `http://127.0.0.1:9527`。保护后的响应加入：

```text
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'
X-Frame-Options: DENY
Referrer-Policy: no-referrer
```

- [ ] **Step 3：运行测试**

Expected：受信请求通过，其余请求返回 `403`，现有 Android 上传 endpoint 不经过此 Guard。

- [ ] **Step 4：提交**

```powershell
git add windows/src/main/java/com/droidscope/local/SessionGuard.java windows/src/test/java/com/droidscope/local/SessionGuardTest.java
git commit -m "feat: 限制本地设备接口访问来源"
```

## 8．Task 5：暴露设备列表 interface

**Files:**

- Create: `windows/src/main/java/com/droidscope/local/DeviceJson.java`
- Create: `windows/src/main/java/com/droidscope/local/DeviceHandler.java`
- Create: `windows/src/test/java/com/droidscope/local/DeviceJsonTest.java`
- Create: `web/src/local-client/DroidScopeClient.ts`
- Create: `web/src/local-client/HttpDroidScopeClient.ts`
- Modify: `web/src/app/App.tsx`
- Modify: `web/src/app/App.test.tsx`

- [ ] **Step 1：写设备 JSON 失败测试**

```java
String json = DeviceJson.encode(List.of(
        new AdbDevice("ABC123", AdbDevice.State.DEVICE),
        new AdbDevice("OFFLINE1", AdbDevice.State.OFFLINE)));
assertEquals("{\"devices\":[{\"serial\":\"ABC123\",\"state\":\"device\",\"ready\":true},{\"serial\":\"OFFLINE1\",\"state\":\"offline\",\"ready\":false}]}", json);
```

另加序列号包含引号和反斜杠的转义测试。

- [ ] **Step 2：实现 DeviceJson**

仅输出 `serial`、`state`、`ready`。字符串转义必须覆盖引号、反斜杠和 U+0000—U+001F 控制字符；不得直接拼接未转义设备序列号。

- [ ] **Step 3：实现 GET `/api/v1/devices`**

Handler 只接受 GET，调用现有 `AdbManager.listDevices()`，成功返回 `200 application/json`，ADB 不存在或超时时返回稳定错误：

```json
{"error":{"code":"ADB_UNAVAILABLE","message":"ADB is unavailable"}}
```

- [ ] **Step 4：实现 HttpDroidScopeClient**

Task 5 中的 `DroidScopeClient` 暂时只声明 `getHealth` 和 `listDevices`：

```ts
export interface DroidScopeClient {
  getHealth(): Promise<CoreHealth>;
  listDevices(): Promise<DeviceSummary[]>;
}
```

```ts
export class HttpDroidScopeClient implements DroidScopeClient {
  constructor(private readonly token: string) {}

  async getHealth(): Promise<CoreHealth> {
    const response = await fetch("/api/v1/health");
    if (!response.ok) throw new Error(`HEALTH_FAILED:${response.status}`);
    return response.json();
  }

  async listDevices(): Promise<DeviceSummary[]> {
    const response = await fetch("/api/v1/devices", {
      headers: { "X-DroidScope-Session": this.token },
    });
    if (!response.ok) throw new Error(`DEVICE_LIST_FAILED:${response.status}`);
    return (await response.json()).devices;
  }
}
```

- [ ] **Step 5：先写 App 设备状态测试，再接入 client**

测试 ready、unauthorized、offline、空设备和 ADB unavailable 五种状态。App 通过注入的 `DroidScopeClient` 获取数据，不在组件内部创建 client。

- [ ] **Step 6：运行 Java 与 Web 测试**

Expected：JSON、Handler、client 和 App 状态测试全部 PASS。

- [ ] **Step 7：提交**

```powershell
git add windows/src/main/java/com/droidscope/local windows/src/test/java/com/droidscope/local web/src
git commit -m "feat: 提供受保护的本地设备状态接口"
```

## 9．Task 6：通过 SSE 推送设备变化

**Files:**

- Create: `windows/src/main/java/com/droidscope/local/DeviceEventBroker.java`
- Create: `windows/src/main/java/com/droidscope/local/EventTicketStore.java`
- Create: `windows/src/main/java/com/droidscope/local/EventTicketHandler.java`
- Create: `windows/src/main/java/com/droidscope/local/DeviceEventsHandler.java`
- Create: `windows/src/test/java/com/droidscope/local/DeviceEventBrokerTest.java`
- Create: `windows/src/test/java/com/droidscope/local/EventTicketStoreTest.java`
- Modify: `windows/src/main/java/com/droidscope/adb/AdbMonitor.java`
- Modify: `web/src/local-client/DroidScopeClient.ts`
- Modify: `web/src/local-client/HttpDroidScopeClient.ts`

- [ ] **Step 1：写 Broker 测试**

测试订阅者收到一次设备变化、取消订阅后不再收到、两个订阅者独立收到相同事件、慢订阅者不会阻塞发布线程。

- [ ] **Step 2：实现有界 Broker**

每个订阅者使用容量为 `1` 的 `ArrayBlockingQueue<String>`。发布新状态时先移除旧值再写入新值，保证只保留最新设备状态。

- [ ] **Step 3：实现 SSE Handler**

响应头：

```text
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

事件格式：

```text
event: devices
data: {"devices":[]}

```

每 `15 s` 无事件时写入 `: keepalive`。客户端断开后必须取消 Broker 订阅并关闭输出流。

- [ ] **Step 4：让 AdbMonitor 发布状态**

向 `AdbMonitor` 构造函数注入 `Consumer<List<AdbDevice>>`。只有设备集合或状态变化时发布；现有 ADB Reverse 恢复逻辑保持不变。

- [ ] **Step 5：前端接入 EventSource 兼容方案**

原生 `EventSource` 不能设置自定义 Header，因此 endpoint 使用一次性 URL ticket，而不是把主 session token 长期放进查询参数。流程为：受保护的 POST `/api/v1/event-tickets` 返回一次性 ticket；`EventSource` 连接 `/api/v1/events?ticket=...`；ticket 使用一次后立即失效，最长有效期 `30 s`。

先写 `EventTicketStoreTest`，覆盖签发、成功消费、二次消费失败和 `30 s` 到期失败；时间通过 `Clock` 注入，不在测试中等待。`EventTicketHandler` 只接受通过 SessionGuard 的 POST，请求体为空，成功返回：

```json
{"ticket":"<URL_SAFE_TICKET>","expiresInSeconds":30}
```

随后为 `DroidScopeClient` 增加 `subscribeDevices`，前端订阅必须先申请 ticket，再创建 `EventSource`；取消订阅时关闭 EventSource。申请 ticket 或 SSE 连接失败时保留最后一次设备列表并显示连接告警。

- [ ] **Step 6：运行 Broker、Handler 和前端订阅测试**

Expected：设备变化只更新 UI，不触发页面轮询；ticket 重放返回 `403`。

- [ ] **Step 7：提交**

```powershell
git add windows/src/main/java/com/droidscope/local windows/src/main/java/com/droidscope/adb/AdbMonitor.java windows/src/test/java/com/droidscope/local web/src/local-client
git commit -m "feat: 通过本地 SSE 推送设备变化"
```

## 10．Task 7：接入 Local Core 并自动打开浏览器

**Files:**

- Create: `windows/src/main/java/com/droidscope/local/BrowserLauncher.java`
- Modify: `windows/src/main/java/com/droidscope/server/ReceiverServer.java`
- Modify: `windows/src/main/java/com/droidscope/Main.java`
- Modify: `windows/src/main/java/com/droidscope/tray/TrayManager.java`

- [ ] **Step 1：写 BrowserLauncher 测试**

通过注入 `Consumer<URI>` 测试生成 URL：

```text
http://127.0.0.1:9527/ui/#token=<URL_SAFE_TOKEN>
```

测试不实际打开浏览器。

- [ ] **Step 2：实现 BrowserLauncher**

生产 Adapter 使用 `Desktop.getDesktop().browse(uri)`。`Desktop` 不支持 BROWSE 时打印完整本地 URL，不让 Local Core 启动失败。

- [ ] **Step 3：注册 Local Core contexts**

在 `ReceiverServer` 中新增：

```text
/ui/
/api/v1/health
/api/v1/devices
/api/v1/event-tickets
/api/v1/events
```

保留现有：

```text
/api/v1/ping
/api/v1/files
```

- [ ] **Step 4：修改 Main 启动顺序**

修改前先读取并保留当前 `Main.java` 中的单实例锁改动；若现有 diff 与以下启动顺序冲突，停止 Task 7 并请用户确认合并方式，不得覆盖现有工作区内容。

```text
获取单实例锁
→ 创建 session token
→ 启动 ReceiverServer
→ 初始化 ADB Reverse
→ 启动 AdbMonitor
→ 打开 /ui/#token=...
→ 启动 Tray
→ 等待退出
```

任何 Web UI 初始化失败都不能破坏已有文件 Receiver；错误必须写日志并保留托盘退出能力。

- [ ] **Step 5：修改托盘打开行为**

新增 `Open DroidScope` 菜单项，复用 BrowserLauncher 打开同一 session URL。`Open Receive Folder`、`Reconnect` 和 `Exit` 行为保持不变。

- [ ] **Step 6：运行全部 Java 测试和 Receiver 回归**

Expected：原 `/ping`、文件上传、文件重名、`.part` 清理、单实例、ADB Reverse 测试仍通过；浏览器页面可打开并显示设备状态。

- [ ] **Step 7：提交**

```powershell
git add windows/src/main windows/src/test
git commit -m "feat: 启动本地 Web 工作台并保留文件接收"
```

## 11．Task 8：建立跨平台构建与离线检查

**Files:**

- Create: `scripts/build-local-core.mjs`
- Create: `scripts/package-local-core.mjs`
- Create: `scripts/verify-offline.mjs`
- Modify: `windows/build.ps1`
- Modify: `windows/package.ps1`
- Modify: `.gitignore`

- [ ] **Step 1：实现跨平台构建脚本**

`scripts/build-local-core.mjs` 必须按顺序：

```text
npm --prefix web ci
npm --prefix web test -- --run
npm --prefix web run build
javac -encoding UTF-8 -d build-out/classes <java sources>
copy web/dist -> build-out/stage/web
copy compiled classes -> build-out/stage
jar --create --file build-out/droidscope.jar -C build-out/stage .
```

使用 `spawnSync` 的参数数组调用进程，禁止拼接 shell 字符串。Windows 使用 `npm.cmd`，其他平台使用 `npm`。

- [ ] **Step 2：让 PowerShell 构建入口调用统一脚本**

```powershell
$ErrorActionPreference = 'Stop'
node (Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts\build-local-core.mjs')
if ($LASTEXITCODE -ne 0) { throw "build failed with exit code $LASTEXITCODE" }
```

- [ ] **Step 3：实现当前平台 app-image 打包**

`scripts/package-local-core.mjs` 调用当前 JDK 的 `jpackage`，输入 `build-out/droidscope.jar`，主类为 `com.droidscope.Main`。脚本根据 `process.platform` 报告实际产物路径，不宣称生成其他操作系统产物。

- [ ] **Step 4：实现离线静态资源扫描**

`scripts/verify-offline.mjs` 扫描 `web/dist` 的 HTML 的 `src`／`href`、CSS `url()` 与应用代码主动加载的资源 URL，发现以下外部资源引用即返回非零退出码：

```text
http://
https://
//cdn.
fonts.googleapis.com
fonts.gstatic.com
```

依赖库内部不触发请求的文档字符串和 XML 命名空间不参与判定；V0.1 不设置外部资源允许列表。

- [ ] **Step 5：更新 `.gitignore`**

加入：

```text
web/node_modules/
web/dist/
.superpowers/
build-out/
```

保留现有忽略规则，不覆盖用户配置。

- [ ] **Step 6：执行 Windows 构建、打包和断网检查**

Run：

```powershell
node scripts/build-local-core.mjs
node scripts/verify-offline.mjs
node scripts/package-local-core.mjs
```

Expected：测试通过，生成 `build-out/droidscope.jar` 和当前 Windows app-image；断开互联网后仍能启动、打开 UI、列出本地 ADB 设备。

- [ ] **Step 7：记录 macOS 与 Linux 门禁**

只有在对应真实系统执行相同三个 Node 命令成功，并完成浏览器启动、ADB 列表和退出测试后，才能在 Roadmap 标记该平台已验证。缺少环境时记录「架构支持，尚未实机验证」。

- [ ] **Step 8：提交**

```powershell
git add scripts windows/build.ps1 windows/package.ps1 .gitignore
git commit -m "build: 增加本地工作台跨平台构建与离线校验"
```

## 12．Task 9：补齐文档和最终验收

**Files:**

- Create: `docs/local-first-runtime.md`
- Modify: `README.md`
- Modify: `docs/WINDOWS_BUILD.md`
- Modify: `docs/SCRIPTS.md`
- Modify: `docs/private/ROADMAP.md`

- [ ] **Step 1：编写运行架构文档**

必须包含：Local Core／浏览器数据流、endpoint 清单、session token 生命周期、Origin 限制、离线资源策略、SSE ticket、平台 Adapter、日志位置和已知限制。

- [ ] **Step 2：更新 README**

明确：DroidScope 是跨平台、本地优先 System Workbench；当前功能、启动方式、离线承诺、各平台验证状态，以及 Android → PC 文件传输仍受支持。

- [ ] **Step 3：执行最终自动验证**

Run：

```powershell
node scripts/build-local-core.mjs
node scripts/verify-offline.mjs
```

Expected：Java 与 Web 测试全部 PASS，离线扫描 PASS，JAR 可启动。

- [ ] **Step 4：执行最终手工验证**

逐项记录：

```text
Local Core 启动
系统浏览器自动打开
刷新页面后 session 仍可用
设备列表与 unauthorized / offline 状态
拔插设备后的 SSE 更新
原 Android 单文件与多文件发送
托盘再次打开 DroidScope
断网启动和刷新
外部 Origin 调用 devices endpoint 返回 403
```

- [ ] **Step 5：更新 Roadmap**

只有实际通过的项目写入「已完成」和「最近验证」。macOS、Linux 未执行时必须写「待验证」，不能用 Windows 结果代替。

- [ ] **Step 6：提交**

```powershell
git add README.md docs
git commit -m "docs: 记录本地工作台运行方式与验证状态"
```

## 13．Foundation Definition of Done

- [ ] 现有 Android → PC 文件传输行为和回归测试未退化。
- [ ] Local Core 仅监听 `127.0.0.1`。
- [ ] 启动后自动打开系统浏览器。
- [ ] Web UI 完全由本地静态资源组成。
- [ ] 断网状态下 UI、health、设备列表和 SSE 可用。
- [ ] 受保护 interface 校验 session token 和 Origin。
- [ ] SSE 使用一次性 ticket，不在 URL 中长期暴露主 session token。
- [ ] UI 没有任意 shell 或 ADB 命令入口。
- [ ] Windows 完成构建、app-image 和手工验收。
- [ ] macOS、Linux 分别记录真实验证结果或明确标记待验证。
- [ ] README、运行架构和 Roadmap 与实际状态一致。
- [ ] 已获得真实 `dumpsys window` fixture 后，才能开始编写 Window Inspector 实施计划。
