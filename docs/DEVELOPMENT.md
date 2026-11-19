# Android 开发、构建与发布

## 构建环境

使用 JDK 17、Gradle 9.6.0、Android SDK 37 和 Android Gradle Plugin 9.5.0。项目使用 Kotlin、Jetpack Compose、Material 3 和 Android Keystore；应用最低支持 Android 12（API 31）。

在项目根目录使用已安装的 Gradle 执行：

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/check_docs.py
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。仅有 JDK 和 Kotlin 编译器时可执行可移植测试：

```bash
kotlinc app/src/main/java/top/kenvix/bleunlock/Protocol.kt tests/InteropVectorSmoke.kt -include-runtime -d /tmp/interop.jar
java -jar /tmp/interop.jar
```

完整验证还应运行 Gradle 单元测试与 Android Lint；这两类检查不能替代真实蓝牙设备测试。

## 开发步骤

1. 从最新 `main` 创建分支。先阅读 [项目台账](PROJECT_LEDGER.md) 和 [架构](ARCHITECTURE.md)。
2. 修改广播时，确认始终优先传输专用服务 UUID；名称过长或扫描响应失败时保留可用的服务广播。
3. 修改 `UnlockService` 时，确保已检查蓝牙权限，处理服务停止、GATT 断开、广播错误、请求超时和通知清理。
4. 修改自动／手动确认时，分别验证 Android Keystore 签名、公钥指纹、通知确认、拒绝和取消行为。
5. 修改 `Protocol.kt` 时同步 Windows `core/protocol.*`，保持两端测试数据 `interop_vectors.properties` 相同。
6. 修改应用权限、目标 SDK 或前台服务类型时，按目标 Android 版本进行测试。
7. 提交 PR，等待单元测试、Lint、APK 编译通过，并按 [设备测试记录](DEVICE_TEST_RECORD.md) 补充必要的实测结果。

## 版本与签名

正式发布新版本时同步更新根目录 `VERSION` 与 `app/build.gradle.kts` 的 `versionName`，并增加整数 `versionCode`。已有正式发行 APK 的更新必须使用相同签名证书。不要因为修改界面名称就变更 `applicationId` 或已有 Keystore 身份别名。

正式签名信息存放在 GitHub Actions Secrets（只设置变量名，不提交值）：

| 名称 | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 经过 Base64 编码的正式签名密钥库 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

提供全部签名信息后，`.github/workflows/ci-release.yml` 才会构建正式签名 APK 并发布 `v<版本号>` Release。未配置正式签名信息时，工作流程生成调试 APK，并在 `main` 构建成功后发布 `v<版本号>-preview.1` 预览版。预览版签名不能用于直接升级由正式证书签名的同包名应用；在测试设备切换签名时应先保护好需要保留的数据，再按 Android 的安装限制处理。

GitHub 工作流程在已有相同版本标签时保留现有 Release，不覆盖已经发布的 APK。**只修改文档时不需要提高版本号。** 修改签名、应用标识和身份密钥时，应先安排独立的升级兼容性验证。

## 跨端配合

Windows 仓库的 CI 会获取 Android `main` 并校验签名数据。因此，影响两个仓库的协议修改应先在 Android 完成相应代码及测试，再在 Windows PR 中运行跨端检查。连接协议使用 `KENVIX-BLE-UNLOCK/v1:` 等既有常量，属于线上二进制兼容规则，不能作为界面名称随意修改。

Windows Credential Provider、TPM 凭据保护、RustDesk SSE 服务均在 Windows 仓库实现。修改 Android 自动认证逻辑时，需要核对 [Windows 架构](https://github.com/HSOS6Miyabi/BluetoothUnlock-Windows/blob/main/docs/ARCHITECTURE.md)、[协议](PROTOCOL.md)、[测试说明](TESTING.md) 以及 [安全说明](SECURITY.md)。

## 真实设备发布前检查

至少使用一台支持 BLE 外围设备广播的手机和一台 Windows 电脑，检查首次绑定公钥、自动和手动认证、后台运行、屏幕锁定、蓝牙关闭与恢复、应用重启、权限撤销及 Windows 原始登录方式。所有真实结果应填入 [设备测试记录](DEVICE_TEST_RECORD.md)，并在 PR 中注明执行情况。
