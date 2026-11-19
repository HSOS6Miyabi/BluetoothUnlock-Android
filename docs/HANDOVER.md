# Android 项目交接指南

本仓库提供 Windows 原生锁屏蓝牙解锁功能所需的 Android 应用。第一次接手项目时，应先阅读本文件、[架构说明](ARCHITECTURE.md)、[项目台账](PROJECT_LEDGER.md) 和 [开发发版流程](DEVELOPMENT.md)。Windows 端代码位于 [BluetoothUnlock-Windows](https://github.com/HSOS6Miyabi/BluetoothUnlock-Windows)。

## 项目现状（2026-11-19）

| 项目 | 当前情况 |
| --- | --- |
| 开发分支 | `main`，GitHub PR 执行构建与单元测试 |
| 最新已核对的 Android 版本 | `1.0.3`，公开发行 `v1.0.3-preview.1` 为调试签名 APK |
| 相关 Windows 发行版本 | 已核对 `v1.2.2`，Windows 端在独立仓库发布 |
| 平台 | Android 12 起（`minSdk 31`），Kotlin、Jetpack Compose、Material 3 |
| 构建环境 | JDK 17、Gradle 9.6.0、Android Gradle Plugin 9.5.0、Android SDK 37 |
| 本机认证 | Android Keystore 中的 ECDSA P-256 私钥；Windows 保存已核对的公钥 |
| 蓝牙通信 | Android 作为 BLE 外围设备运行 GATT 服务；Windows 主动扫描并发起挑战 |
| 测试范围 | GitHub Actions 检查单元测试、Lint、APK 编译；无线连接及后台行为另行实测 |

**交接状态：** CI 已具备可重复的测试和构建流程。不同手机的 BLE 外围设备能力、长期后台运行、手动通知、系统生物识别以及 Windows 真实锁屏仍需要按 [设备测试记录](DEVICE_TEST_RECORD.md) 记录实测结果。

## 接手后先完成

1. 按 [DEVELOPMENT](DEVELOPMENT.md) 运行 Gradle 构建及单元测试，找到生成的调试 APK。
2. 阅读 [ARCHITECTURE](ARCHITECTURE.md)，确认界面、前台服务、设备身份密钥和 GATT 回调分别由哪个文件负责。
3. 阅读 [PROTOCOL](PROTOCOL.md)，核对 Windows 的服务 UUID、挑战数据、签名原文及公钥指纹。影响认证协议的修改应提交两端兼容性测试。
4. 在具有 BLE 外围设备广播功能的 Android 手机上安装应用，授予蓝牙及通知权限，按 [DEVICE_TEST_RECORD](DEVICE_TEST_RECORD.md) 检查广播、自动认证和手动确认。
5. 核查 GitHub Actions 的签名配置。公开预览版使用调试签名；正式发行签名文件与密码必须由管理员安全保管，不应进入仓库。

## 日常维护

- **Windows 发现不了手机：** 查看应用是否显示正在广播、是否缺少蓝牙广播权限、手机是否支持 BLE 外围设备模式。名称过长时应用会保留服务 UUID 广播，Windows 可显示“解锁服务设备（名称未广播）”。
- **前台服务中断：** 检查权限、蓝牙开关、系统电源管理及开机恢复限制；不要保证所有厂商设备都能永久驻留。
- **手动确认无通知：** 检查通知授权、通知渠道、系统身份验证设置、请求有效期和应用是否仍处于活动状态。
- **重新安装后无法验证：** 检查公钥指纹是否改变。Android Keystore 身份密钥变化后，需要在 Windows 中重新读取公钥并核对指纹。

## 安全与交接资料

维护日志与 GitHub Issue 记录版本、设备型号、系统版本、复现步骤、测试结果和修复状态。不要提交 Android Keystore 私钥、JKS 文件、签名密码、Windows 账户密码、实际 SSE API Key 或未脱敏的设备日志。

改变 BLE UUID、`Protocol.message()` 的签名原文、应用包标识或 Keystore 别名时，必须提出明确的迁移方案，并同步更新 Windows 客户端和两端测试数据。现有兼容标识与用户界面品牌名称分别管理。维护规则见 [项目台账](PROJECT_LEDGER.md)。
