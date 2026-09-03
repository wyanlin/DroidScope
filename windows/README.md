# Windows Receiver

开发验证：`powershell -ExecutionPolicy Bypass -File build.ps1`。

桌面版：使用 JDK 17+，设置 `JAVA_HOME` 后运行 `powershell -ExecutionPolicy Bypass -File package.ps1`。生成的 `build-out\DroidScope\DroidScope\DroidScope.exe` 可直接双击启动，程序会隐藏控制台并常驻系统托盘。
