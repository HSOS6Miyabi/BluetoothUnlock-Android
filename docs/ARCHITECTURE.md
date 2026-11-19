# Android 架构与模块职责

## 主要流程

```text
MainActivity.kt（Compose / Material 3）
    ├─ Preferences.kt：自动／手动确认、开机启动、是否启用
    ├─ KeystoreIdentity.kt：Android Keystore 密钥、公钥指纹、ECDSA 签名
    └─ UnlockService.kt（connectedDevice 前台服务）
         ├─ BLE 广播：主广播放入服务 UUID，必要时扫描响应放入手机名称
         ├─ BLE GATT 服务：公钥读取、挑战写入、结果读取
         ├─ 自动模式：签名响应
         ├─ 手动模式：通知 → ApprovalActivity.kt → 系统身份验证 → 签名
         └─ 蓝牙重启、请求超时、服务停止后的资源清理
BootReceiver.kt：按用户设置与权限情况尝试开机启动服务
```

Windows 负责扫描、RSSI 判断、生成一次性随机挑战、验证签名并通过 Credential Provider 提交已配置 Windows 账户的密码。Android 仅使用私钥完成签名，不接收、保存 Windows 登录密码。

## 代码文件

| 文件 | 负责内容 | 维护时检查 |
| --- | --- | --- |
| `app/src/main/java/top/kenvix/bleunlock/MainActivity.kt` | Material 3 界面、模式切换、公钥指纹、服务开关 | 浅色／深色、不同屏幕宽度、权限反馈 |
| `UnlockService.kt` | 蓝牙 GATT 服务、前台通知、广播、挑战请求、超时清理 | 广播权限、前台服务状态、GATT 连接资源及多请求处理 |
| `AdvertiseNamePolicy.kt` | 检查系统蓝牙名称能否放入传统扫描响应 | 名称超过 UTF-8 29 字节时优先保留服务 UUID |
| `KeystoreIdentity.kt` | 创建／读取 EC 密钥、签名、公钥指纹 | 密钥别名兼容与私钥不导出 |
| `ApprovalActivity.kt` | 手动认证及请求确认 | 拒绝、取消、超时、系统生物识别状态 |
| `Preferences.kt` | 用户模式、开机自启及服务启用设置 | 旧配置兼容和默认值 |
| `BootReceiver.kt` | 收到 `BOOT_COMPLETED` 后按条件启动服务 | Android 后台启动限制 |
| `Protocol.kt` | GATT UUID、分段挑战、响应、签名原文和指纹算法 | 必须与 Windows `core/protocol.*` 一致 |
| `app/src/main/AndroidManifest.xml` | 权限、服务、Activity、系统启动接收器 | Android SDK 和目标系统限制 |
| `app/src/test/` | 协议、签名兼容、广播名称策略单元测试 | 改动后执行 Gradle 单元测试及 Lint |

## 身份和安全范围

- 私钥在 Android Keystore 中生成并保存，算法为 P-256、`SHA256withECDSA`。Windows 只获得 SEC1 格式的 65 字节公钥。
- `KeystoreIdentity.kt` 当前使用 `setUserAuthenticationRequired(false)`，因此自动认证可以在后台使用私钥；手动模式通过 `ApprovalActivity` 另行请求系统身份验证。修改此机制时需同时测试两种模式。
- GATT 认证的完整字节格式见 [PROTOCOL](PROTOCOL.md)。签名消息前缀 `KENVIX-BLE-UNLOCK/v1:` 是既有协议常量；修改该值会使旧版 Windows 验证失败。
- 应用包标识 `top.kenvix.bleunlock`、Keystore 别名 `kenvix_unlock_identity_v1` 属于兼容性标识。用户界面使用“Bluetooth Unlock”，更名界面时无需改变包标识或密钥别名。
- RSSI 只能辅助判断接近程度，不能防御所有蓝牙中继攻击。需要更高操作确认要求时，可启用手动确认模式。

## BLE 广播和 GATT 会话

Android 在主广播中提供 `Protocol.SERVICE`，普通长度的系统蓝牙名称通过扫描响应发送。名称缺失、过长或扫描响应失败时，程序会尝试只发送服务 UUID，避免名称影响发现服务。部分设备或厂商系统不支持 BLE 外围设备广播，应用将提示当前状态。

Windows 收到广播后会合并同一蓝牙地址下的名称、RSSI 与服务 UUID。Windows 的历史设备行保留约 3 分钟；真正发起解锁要求最近 4 秒的新广播。Android 在一次 GATT 连接中接收两个 17 字节挑战片段，按当前随机数签名并返回 DER 格式签名；挑战和请求都有超时及清理逻辑。详情见 [测试](TESTING.md)。

## 后台服务与权限

Android 应用声明 `BLUETOOTH_CONNECT`、`BLUETOOTH_ADVERTISE`、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE_CONNECTED_DEVICE`、`RECEIVE_BOOT_COMPLETED` 与 `USE_BIOMETRIC` 等权限。用户授权后，应用使用 `connectedDevice` 类型的前台服务与常驻通知。

Android 会根据权限、电池策略、系统版本和蓝牙硬件决定服务能否持续运行。开机自动启动是用户可选功能，系统也可能暂时限制前台服务启动，因此需要按 [设备测试记录](DEVICE_TEST_RECORD.md) 在目标机型上核实。

## 与 Windows 代码的关系

RustDesk SSE、系统登录组件、TPM 加密和 Windows 服务管理都由 Windows 仓库负责。Android 维护人员需要对照 [Windows 架构说明](https://github.com/HSOS6Miyabi/BluetoothUnlock-Windows/blob/main/docs/ARCHITECTURE.md) 和 [Windows 测试说明](https://github.com/HSOS6Miyabi/BluetoothUnlock-Windows/blob/main/docs/TESTING.md)，同步维护 BLE 协议和签名测试数据。
