# Bluetooth Unlock for Android

为 Windows 原生锁屏提供蓝牙身份认证的 Android 客户端。使用 Kotlin、Jetpack Compose 和 Material 3 实现界面，并通过 Android Keystore 保存 ECDSA P-256 私钥。

## 主要功能

- 自动认证：在允许的后台运行条件下自动响应 Windows 的一次性认证挑战。
- 手动确认：在手机上使用生物识别或系统凭据确认。
- Android `connectedDevice` 前台服务、常驻通知、可选开机启动。
- BLE GATT 挑战响应协议，与 Windows 客户端对应。
- 通过蓝牙扫描响应广播手机的系统蓝牙名称，Windows 使用“设备名称、MAC 地址、信号强度”表格显示附近设备。
- JVM 自动化测试使用 Windows 与 Android 共享的 ECDSA 签名数据，检查随机挑战、签名和公钥指纹。

## 设备名称

Windows 只显示能够提供蓝牙名称的设备。请在 Android 系统蓝牙设置中设置简短、清晰的手机名称，然后启动应用的蓝牙服务。Android 在主广播中发送服务 UUID，名称长度合适时通过扫描响应发送设备名称。名称超过传统 BLE 扫描响应的容量时，应用会继续广播解锁服务 UUID，Windows 将显示“解锁服务设备（名称未广播）”。应用保持后台运行是发现手机的必要条件。

## 环境

Android 12 及更高版本，支持 BLE 外围设备广播。使用 JDK 17、Android SDK 37、Android Gradle Plugin 9.5.0 和 Gradle 9.6.0。

在 Android Studio 打开仓库根目录，或使用安装好的 Gradle 执行：

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions 会自动编译、执行 JVM 单元测试和 Android Lint，并上传可安装的调试 APK。对正式版 APK 进行签名后，工作流程才会发布正式 Release。

## 正式发行签名

在仓库 Settings → Secrets and variables → Actions 中配置：

| Secret | 说明 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 将用于发行的 JKS/PKCS12 签名文件做 Base64 编码后的内容 |
| `ANDROID_KEYSTORE_PASSWORD` | 签名文件密码 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

请妥善保存签名文件及密码，后续更新版本需要使用同一签名密钥。签名信息不应提交到 GitHub 仓库。

## 两端协议测试

Windows 使用 CNG 验证签名，Android 使用 `SHA256withECDSA` 验证同一组签名数据，包含错误挑战拒绝测试。

```bash
kotlinc app/src/main/java/top/kenvix/bleunlock/Protocol.kt tests/InteropVectorSmoke.kt -include-runtime -d /tmp/interop.jar
java -jar /tmp/interop.jar
```

完整 Android 单元测试使用 Gradle 执行 `:app:testDebugUnitTest`，其中包含 `InteropVectorTest.kt`。两端在真实蓝牙设备上的认证测试步骤见 Windows 仓库的测试说明。

## Windows 客户端

Windows 的 Credential Provider、设置程序与安装脚本位于 [BluetoothUnlock-Windows](https://github.com/HSOS6Miyabi/BluetoothUnlock-Windows)。

## 协议与安全

[通信协议](docs/PROTOCOL.md) · [安全说明](docs/SECURITY.md) · [测试说明](docs/TESTING.md)。
