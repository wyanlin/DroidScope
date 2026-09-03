# DroidScope

DroidScope 是面向 Android 开发者的设备工作台。当前 V1 提供 Android 到 Windows 的 USB 文件传输能力，后续扩展 APK 管理、Logcat、Shell、Perfetto 和 Framework 诊断。

## 当前能力

- Android 系统分享发送单个或多个文件。
- 通过 ADB Reverse 使用 USB 通道传输。
- Windows 托盘程序自动接收并保存文件。
- USB 重新连接后自动恢复传输通道。

## 构建

- Android：参见 [Android 构建说明](docs/ANDROID_BUILD.md)。
- Windows：参见 [Windows 构建说明](docs/WINDOWS_BUILD.md)。
- 产品设计与路线：参见 [设计文档](docs/DESIGN.md) 和 [ROADMAP.md](ROADMAP.md)。

项目处于个人使用开发阶段，尚未提供正式稳定版本。
