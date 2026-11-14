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

## 蓝牙名称与后台广播测试

Android 对系统设备名称进行 UTF-8 字节数检查，正常长度的名称放在扫描响应中；名称过长或无法获取时仍会广播应用的 128 位服务 UUID。可使用 JVM 单元测试检查名称长度筛选。在具有蓝牙硬件的手机上，分别使用普通名称、超长名称，确认服务状态显示广播已经启动，Windows 对超长名称显示解锁服务设备占位名称。

## 与 Windows 实际设备联调

在 Android 手机上开启蓝牙前台服务，确认 Windows 可以看到手机的真实设备名称、MAC 地址和 RSSI，且过滤未广播名称的设备。随后由 Windows 端读取公钥并核对指纹；分别试验自动认证和手机手动确认。检查通知权限、服务重新启动、蓝牙关闭与开启、拒绝解锁、30 秒请求超时和多次连续请求。测试电脑应保留 Windows 密码、PIN 或 Windows Hello 登录方式。

自动化测试不需要蓝牙硬件，真实无线连接和锁屏操作需要使用 Windows 电脑与支持 BLE 外围设备广播的 Android 手机测试。
