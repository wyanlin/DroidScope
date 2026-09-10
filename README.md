# DroidScope

DroidScope 是面向 Android 开发者的本地设备工作台。当前提供 Android 到 Windows 的 USB 文件传输、Window Inspector、Activity ↔ Window 关联和 Surface ↔ Window 严格关联，后续扩展 Input ↔ Window、APK 管理、Logcat、Shell、Perfetto 和 Framework 诊断。

## 当前能力

- Android 系统分享发送单个或多个文件。
- 通过 ADB Reverse 使用 USB 通道传输。
- Windows 托盘程序自动接收并保存文件。
- USB 重新连接后自动恢复传输通道。
- 采集并解析 SurfaceFlinger binary proto，展示 Layer、metadata、父子关系、buffer 和几何信息。
- 通过 `EXACT_METADATA` 规则关联 Window 与 Surface；无法验证的候选保持 `UNLINKED` 或 `AMBIGUOUS`，不进行名称猜测。
- Windows、Activities、Surfaces 统一刷新，并支持 Window ↔ Surface 双向跳转。
- USB 断开时保留最后成功的 InspectorSnapshot；设备恢复后可手动刷新设备连接和快照。

## 使用

启动 Windows Local Core 后，程序会打开本地 Web Workbench。设备连接状态由 ADB 监控，选择 Ready 设备后点击 `Refresh Snapshot` 获取 Windows、Activities 和 Surfaces 数据。

设备断开时页面保留最后一次成功数据。重新连接 USB 后，先点击 `Refresh Devices`，确认设备恢复为 Ready，再点击 `Refresh Snapshot`。

Surface ↔ Window 严格关联当前已在 Android 14 真机上验证。关联必须同时满足合法 Layer ID 后缀、名称、OWNER_UID、WINDOW_TYPE 和 OWNER_PID；其他 Android 版本或 metadata 不完整的 Layer 不自动建立关联。

## 构建

- Android：参见 [Android 构建说明](docs/ANDROID_BUILD.md)。
- Windows：参见 [Windows 构建说明](docs/WINDOWS_BUILD.md)。
- 当前发布前 Windows 包和验证记录以 `ROADMAP.md` 为准。

项目处于个人使用开发阶段，尚未提供正式稳定版本。
