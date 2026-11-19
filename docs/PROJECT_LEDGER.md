# Android 项目台账与维护事项

以下状态截至 **2026-11-19**。版本与部署状态以本仓库 `main`、GitHub Actions、Releases 和 `VERSION` 文件为准；与 Windows 端的协作事项同时参考 [Windows 项目台账](https://github.com/HSOS6Miyabi/BluetoothUnlock-Windows/blob/main/docs/PROJECT_LEDGER.md)。

## 当前版本和交付物

| 项目 | 当前状态 |
| --- | --- |
| Android 应用 | 版本 `1.0.3`，公开预览版本为 `v1.0.3-preview.1` |
| Android 构建方式 | Gradle 9.6.0、JDK 17、Android SDK 37、Android Gradle Plugin 9.5.0 |
| 公开预览 APK | 使用调试签名，适合测试；与正式发行证书不同 |
| 正式发行 APK | GitHub Actions 已有签名流程；需要提供仓库 Secrets 中的正式签名信息 |
| Windows 端对应版本 | 已核对 `v1.2.2`；平台与版本独立发布 |
| 自动化测试 | `ProtocolTest`、`InteropVectorTest`、`AdvertiseNamePolicyTest`、Android Lint、APK 编译 |

## 模块状态与验证层级

| 功能 | 当前能力 | 自动化检查 | 真实设备 |
| --- | --- | --- | --- |
| Material 3 界面 | 自动／手动确认、服务开关、公钥指纹 | APK 编译和 Lint | 需检查不同机型屏幕及主题 |
| 设备身份 | Android Keystore EC P-256、公钥输出与签名 | 协议与跨端 JVM 测试 | 需检查密钥迁移和重新安装 |
| BLE 外围设备 | GATT 服务及服务 UUID 广播 | APK 编译 | 需检查具体手机的硬件能力 |
| BLE 设备名称 | 扫描响应发送名称，名称过长时保留服务 UUID | `AdvertiseNamePolicyTest` | 需验证扫描响应及 Windows 列表显示 |
| 自动认证 | 后台使用密钥响应有效随机挑战 | 兼容签名测试 | 需验证手机在后台时的实际通信 |
| 手动确认 | 通知和系统身份验证后签名 | APK 编译 | 需验证拒绝、取消、超时与生物识别 |
| 前台服务及重启 | 常驻通知、蓝牙关闭／开启处理、可选开机启动 | Android Lint | 需验证厂商电源管理和系统限制 |
| 发布签名 | CI 可以在提供密钥后生成正式签名 APK | CI 任务配置 | 需确认发行证书的保管与更新一致性 |

上表中自动化检查和实机验证分别记录，避免把编译成功写成硬件兼容性已经得到验证。

## 持续维护事项

| 编号 | 重要程度 | 事项与完成条件 |
| --- | --- | --- |
| A-01 | 高 | 在不同 Android 12+ 设备上实测 BLE 外围设备广播与 GATT 连接，记录是否支持扫描响应、名称回退及后台通信 |
| A-02 | 高 | 完成 Windows 锁屏真实联调，确认公钥指纹、两段挑战、自动认证和手动确认，以及取消／超时后保持锁定 |
| A-03 | 高 | 维护正式发行签名密钥的安全保管与更新流程，确保后续正式 APK 使用同一证书 |
| A-04 | 中 | 验证开机自启、蓝牙关闭／开启、系统电源管理、权限撤销和服务停止后的状态 |
| A-05 | 中 | 验证 Material 3 界面在系统深色／浅色、较大字体及不同屏幕尺寸下的显示 |
| A-06 | 中 | 修改协议时维护 Windows 和 Android 的签名测试数据，防止两端版本出现不兼容 |

每项实测结果均应记录环境、操作过程、结论及关联 GitHub Issue，具体字段见 [设备测试记录](DEVICE_TEST_RECORD.md)。

## 维护约定

| 范围 | 当前约定 |
| --- | --- |
| 应用最低系统 | `minSdk 31`，即 Android 12 |
| 应用标识 | `top.kenvix.bleunlock`，兼容已安装 APK 的包标识 |
| 密钥别名 | `kenvix_unlock_identity_v1`，修改会影响已配对设备 |
| 认证协议 | GATT v1，消息前缀 `KENVIX-BLE-UNLOCK/v1:`，与 Windows 保持一致 |
| 前台服务 | `connectedDevice`，显示常驻通知 |
| 签名 | 预览版使用调试签名；正式发行依靠仓库 Secrets |
| 版本 | `VERSION`、`app/build.gradle.kts` 中的 `versionName` 同步，更新 APK 时增加 `versionCode` |

## 更新规则

功能改变时在 PR 中注明修改模块、测试方法、用户需要配置的权限、协议兼容影响和是否需要重新绑定 Windows。影响持续运行或安全要求的修改应更新 [安全说明](SECURITY.md)、[架构说明](ARCHITECTURE.md) 和本台账。不要将真实用户的设备地址、私钥、账户密码或签名信息提交到仓库。
