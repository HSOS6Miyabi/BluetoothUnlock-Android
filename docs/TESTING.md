# 测试说明

## Android 单元测试和构建

使用 Gradle 9.6.0 和 Android SDK 37：

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`ProtocolTest.kt` 检查挑战帧长度、UUID、签名原文和公钥指纹。`InteropVectorTest.kt` 使用相同的签名、P-256 公钥和随机挑战验证 Windows 认证协议，验证改变随机挑战后签名不再有效。

仅安装 JDK 和 Kotlin 的开发机也可直接执行：

```bash
kotlinc app/src/main/java/top/kenvix/bleunlock/Protocol.kt tests/InteropVectorSmoke.kt -include-runtime -d /tmp/interop.jar
java -jar /tmp/interop.jar
```

## 与 Windows 实际设备联调

在 Android 手机上开启蓝牙前台服务，Windows 端读取公钥并核对指纹；分别试验自动认证和手机手动确认。检查通知权限、服务重新启动、蓝牙关闭与开启、拒绝解锁、30 秒请求超时和多次连续请求。测试电脑应保留 Windows 密码、PIN 或 Windows Hello 登录方式。

自动化测试不需要蓝牙硬件，真实无线连接和锁屏操作需要使用 Windows 电脑与支持 BLE 外围设备广播的 Android 手机测试。
