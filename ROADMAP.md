# DroidScope Roadmap

## 当前阶段

当前 `master` 已合并 `codex/local-web-foundation` 和 `codex/surface-window`。本地 Web Workbench、USB／ADB 设备检测、`dumpsys window` 采集解析、Window Inspector V0.1、Activity ↔ Window 及 Surface ↔ Window V0.1 均已实现并完成用户验收。

后续产品方向已确认：先完成 DroidScope，再逐步接入常用开发工具；PC 端采用跨平台、本地优先的 Web Workbench，由 Java Local Core 仅在 `127.0.0.1` 提供本地页面和受限 interface，启动后默认打开系统浏览器，断网状态仍可使用。Local-first Web Foundation 和 Window Inspector V0.1 已完成，后续按 Window、Activity、Surface、Input 等关联能力逐步扩展。

代码保存状态：`codex/local-web-foundation` 已通过合并提交 `952a10c` 合入 `master`。Surface ↔ Window 功能提交为 `9cd28dd`，已通过合并提交合入 `master`。

## 已完成

- Task 1：建立 `android/`、`windows/`、`docs/`、`scripts/` 基础结构；Windows Java 源码独立编译通过，Android 工程待 Task 4 引入实现后单独编译验证。
- Task 2：实现 `GET /api/v1/ping` 与 `POST /api/v1/files`；Receiver 仅监听 `127.0.0.1:9527`；curl 上传已验证文件写入 `PhoneReceive/test.txt`。
- 2026-09-04 Android 稳定性收尾：测试接收端改用标准 ServerSocket，恢复 Gradle 单测编译；协调器在 Ping／Upload 对象登记后重新检查取消状态，防止创建期间取消或销毁页面后仍启动操作。四个新增回归用例修复前失败、修复后通过，完整 44 项单测通过，Debug APK 编译通过。
- 2026-09-05 设计与规划：确认 Window Inspector 的本地浏览器三栏工作台设计，并新增 Local-first Web Foundation 可执行计划。
- 2026-09-10：Local-first Web Foundation 和 Window Inspector V0.1 已合入 `master`，包含本地 Web UI、Local Core、USB／ADB 设备状态、SSE、Window Parser、Window Snapshot 和可见性筛选。
- 2026-09-10：用户已验收最新 Windows Web UI 版本，确认 USB 检测和 Window Inspector 功能正常。
- 2026-09-10：Activity ↔ Window 关联及 Overview 紧凑属性表已合入 `master`，Web UI 单测 19 项通过，Vite 生产构建通过；Window 与 Activity 页面均支持关联跳转、长 Component 换行和可调整左右面板比例。
- 2026-09-10：`codex/surface-window` 完成并合入 `master`，包含 SurfaceFlinger binary proto 采集解析、`EXACT_METADATA` 严格关联、Surface Inspector、Window ↔ Surface 双向跳转和断线快照保留；功能提交 `9cd28dd`。

## 进行中

- 路线图和构建说明需要继续维护实际分支、验收和产物状态。

## 待办

- 第一优先级：设计并实现 Input ↔ Window 关联。
- 补齐 Android 11、13、14、15 的真实 `dumpsys window` fixture 验证；当前 Parser 已有 Android 13／14 相关实现，其他版本覆盖情况待实机确认。
- Surface ↔ Window 已合入 `master`，下一步推进 Input ↔ Window、Activity／Process Inspector、Binder、Perfetto 与 Diagnose。Perfetto 使用固定版本的本地静态包，运行时不依赖 `ui.perfetto.dev`。
- DroidScope 核心稳定后再评估 WangBox 工具接入；优先复用高契合的本地工具，同时保留独立离线使用方式，不在当前阶段迁移。
- Android 文件发送页仍可作为独立体验优化项：文件列表、当前文件／总体进度、成功／失败展示、取消／重试按钮统一样式。
- V1.2 范围候选依据 DESIGN.md 第二阶段：Windows 开机启动、接收目录设置、接收通知、Android 多文件总体进度、历史记录与 Debug 页面；尚未作为已完成事项或正式实施计划。
- 后续每次代码修改完成后，编译对应 Android APK／Windows 桌面包，并交付绝对路径及手工验证场景；构建受限时明确记录，不交付旧产物冒充新版本。

## 最近验证

- 2026-09-10：Web UI 单测 12 项通过，Vite 生产构建通过；Windows Local Core JAR 和 Web UI app-image 打包通过；JAR 已包含 Web 静态资源和 Window Inspector 类。
- 2026-09-10：用户完成最新 Windows Web UI 版本验收，USB 设备检测、`dumpsys window` 和 Window Inspector 功能正常。
- 最新 Windows 包：`build-out/DroidScope-win32/DroidScope/DroidScope.exe`。
- `codex/surface-window`：Web tests 24 项通过，Production build 和 offline verification 通过；Windows 包为 `build-out/DroidScope-win32-final4/DroidScope/DroidScope.exe`。
- Android 14 真机 `bc22c05f`：Inspector snapshot 返回 200，`EXACT_METADATA=2`、`UNLINKED=125`、`AMBIGUOUS=0`；断开后 ADB 设备为空，刷新返回 502，页面保留最后成功快照。
- 最新 Android Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。
- 以下记录保留为历史验证，不覆盖 2026-09-10 的最新状态。
- 用户在早期交付后反馈安装版本「功能 OK」。
- 早期 Windows Receiver 包曾生成于 `build-out/DroidScope-repackage-20260904/DroidScope/DroidScope.exe`。
- 早期合并后的 master 曾验证 `gradlew.bat assembleDebug` 成功，APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`。
- 2026-09-04：先复现 `com.sun.net.httpserver` 测试编译失败，换为 ServerSocket 后原有单测通过。新增四项取消／销毁期间创建 Ping／Upload 的回归测试，修复前 4 项失败；增加登记后的状态复查后，`gradlew.bat testDebugUnitTest assembleDebug --console=plain` 成功，44 项测试、0 失败、0 跳过。
- 早期 APK：`android/app/build/outputs/apk/debug/app-debug.apk`，生成时间 2026-09-04 10:18:49。Gradle 弃用提示及 ShareActivity 已弃用 API 提示当时仍存在，不影响当次构建成功。
- 2026-09-04 用户真机验收：V1.1 本轮列出的单／多文件分享、传输中取消、断线重试及页面退出／旋转相关用例均已测试通过。Android V1.1 进入验收完成状态；本轮稳定性修复仍待按用户决定提交到 master。
- 2026-09-04 Windows ADB 共存修复：发现多实例及多套 ADB daemon 竞争可能导致 5037 启动失败。Windows 启动入口新增用户目录锁，第二实例在启动 Receiver／ADB 监控前退出；继续通过系统 PATH 调用 `adb`，不固定 SDK 路径。SingleInstanceLockTest 与 DeviceStateTrackerTest 通过，重新生成 Windows 包待用户验证。
- 以下为早期 V1 验证历史，不代表当前 V1.1 分支已重新通过全部验证。

- `windows/build.ps1`：通过。
- Task 3：`AdbManager` 已实现，`adb devices` 无设备时返回空列表；Receiver 启动时自动尝试建立 reverse。
- Task 4：Android `ShareActivity` 已实现，支持 `ACTION_SEND`／`ACTION_SEND_MULTIPLE` 和 `content://` URI，打印 URI、DISPLAY_NAME、SIZE、MIME；当前环境未安装 Gradle，Android APK 编译待具备 Gradle 后验证。
- Task 4 验证：Android 资源与 Manifest 已通过本机 `aapt2` 语法校验；完整 APK 编译仍待 Gradle。
- Task 5：新增 `PingClient`，通过 `127.0.0.1:9527` 检测 Receiver；Android Java 源码、资源和 Manifest 校验通过。
- Task 6：新增 `UploadManager`／`UploadTask`／`UploadResult`，使用 `ContentResolver.openInputStream` 和 64KB 缓冲区流式上传，支持进度回调；Android Java 源码编译通过。
- Task 7：`ShareActivity` 已接入后台传输与进度 UI，显示连接状态、上传百分比和成功／失败结果；Android Java 源码编译通过。
- Task 8：`ShareActivity` 已按 URI 顺序串行发送多文件，逐个显示进度，失败时停止后续任务；Android Java 源码编译通过。
- Task 9：补齐 URI 元数据异常、权限异常、网络／HTTP 失败和文件大小不可用的错误处理，并显示失败状态；Android Java 源码编译通过。
- Task 10：Receiver MVP 验收通过：ping 200、单文件 201、重名不覆盖、路径穿越过滤、大小不匹配 400、无 `.part` 残留、仅监听 127.0.0.1；Android Java／资源／Manifest 校验通过。真实 Android 设备、USB reverse 和 APK 安装验收待现场设备与 Gradle。
- 真机补充验证：USB 连接 Android 设备，Windows 自动建立 ADB reverse，手机分享 MP4 后文件已成功写入 `PhoneReceive`。
- 图标：生成的 USB 文件传输图标已加入 Android Manifest，作为普通和圆形应用图标。
- `GET http://127.0.0.1:9527/api/v1/ping`：HTTP 200。
- curl 上传 `task2.txt`（5 bytes）：HTTP 201，保存内容 `hello`；重名生成 `task2 (1).txt`；大小不匹配返回 HTTP 400 且不残留 `.part`。
- Windows Receiver 已接入 `AdbMonitor`，USB 断开重连后自动恢复 `adb reverse`；补齐单设备 reverse 调用并通过 Windows 编译和 `DeviceStateTrackerTest`。
- 桌面启动增加端口占用提示，避免无控制台模式将 `BindException` 泛化为 `Failed to launch JVM`。
- 托盘菜单改为英文，规避精简运行时缺少中文字体导致的方框字符；新桌面包已重新生成。
