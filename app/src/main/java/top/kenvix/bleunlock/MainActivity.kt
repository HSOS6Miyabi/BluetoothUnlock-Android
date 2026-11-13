package top.kenvix.bleunlock

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme(primary = Color(0xFF97BFFF), surface = Color(0xFF111928))
                else -> lightColorScheme(primary = Color(0xFF265DAD))
            }
            MaterialTheme(colorScheme = colors) {
                UnlockScreen()
            }
        }
    }
}

@Composable
private fun BluetoothMark() {
    val accent = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.size(112.dp).background(
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge)) {
        Canvas(Modifier.size(74.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val path = Path().apply {
                moveTo(cx, 4f)
                lineTo(cx + 18f, cy - 12f)
                lineTo(cx - 13f, cy + 14f)
                moveTo(cx, size.height - 4f)
                lineTo(cx + 18f, cy + 12f)
                lineTo(cx - 13f, cy - 14f)
                moveTo(cx, 4f)
                lineTo(cx, size.height - 4f)
            }
            drawPath(path, accent, style = Stroke(width = 5f))
        }
    }
}

@Composable
private fun TitleText(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun UnlockScreen() {
    val context = LocalContext.current
    val running by UnlockService.running.collectAsState()
    val status by UnlockService.status.collectAsState()
    val requests by UnlockService.requests.collectAsState()
    var manual by rememberSaveable { mutableStateOf(Preferences.manual(context)) }
    var boot by rememberSaveable { mutableStateOf(Preferences.autostart(context)) }
    var permissionMessage by remember { mutableStateOf("") }
    val fingerprint = remember {
        runCatching { KeystoreIdentity().fingerprint() }
            .getOrElse { "无法初始化安全密钥" }
    }
    fun start() {
        try {
            context.startForegroundService(Intent(context, UnlockService::class.java)
                .setAction(UnlockService.ACTION_START))
        } catch (e: Exception) {
            permissionMessage = e.localizedMessage ?: "系统不允许启动蓝牙服务"
        }
    }
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val bluetoothGranted = listOf(Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE).all { permission ->
            grants[permission] == true ||
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (bluetoothGranted && (!manual || notificationGranted)) start()
        else permissionMessage = if (manual && !notificationGranted)
            "手动确认需要通知权限，请在系统设置中允许通知。"
        else "请允许蓝牙连接和蓝牙广播权限。"
    }
    fun requestStart() {
        permissionMessage = ""
        val needed = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE)
        if (manual && Build.VERSION.SDK_INT >= 33)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) start()
        else requestPermissions.launch(missing.toTypedArray())
    }
    fun stop() {
        context.startService(Intent(context, UnlockService::class.java)
            .setAction(UnlockService.ACTION_STOP))
    }
    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Text("KENVIX / SECURITY", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            Text("蓝牙解锁", style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold)
            Text("让 Windows 在你靠近时，识别可信设备。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge)

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BluetoothMark()
                    Text(if (running) "设备已就绪" else "等待启动",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(status, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (requests > 0) Text("等待确认的请求：$requests",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                    if (running) OutlinedButton(onClick = ::stop, modifier = Modifier.fillMaxWidth()) {
                        Text("停止蓝牙认证")
                    } else Button(onClick = ::requestStart, modifier = Modifier.fillMaxWidth()) {
                        Text("启用蓝牙认证")
                    }
                    if (permissionMessage.isNotEmpty()) Text(permissionMessage,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TitleText("认证方式")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !manual,
                        onClick = {
                            manual = false
                            Preferences.setManual(context, false)
                            if (running) context.startService(Intent(context, UnlockService::class.java)
                                .setAction(UnlockService.ACTION_MODE_CHANGED))
                        }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text("自动解锁")
                    }
                    SegmentedButton(selected = manual,
                        onClick = {
                            manual = true
                            Preferences.setManual(context, true)
                            if (running) context.startService(Intent(context, UnlockService::class.java)
                                .setAction(UnlockService.ACTION_MODE_CHANGED))
                            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permissionMessage = "手动确认需要通知权限。"
                                requestPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text("手动确认")
                    }
                }
                Text(if (manual) "Windows 提出解锁请求时，手机通知会要求使用指纹、面容或锁屏密码确认。"
                    else "收到可信 Windows 电脑的挑战后，手机在后台自动完成数字签名。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }

            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TitleText("设备身份")
                    Text("在 Windows 中读取手机公钥后，核对两端显示的指纹。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Text(fingerprint, modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kenvix fingerprint", fingerprint))
                    }, modifier = Modifier.fillMaxWidth()) { Text("复制指纹") }
                }
            }

            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            TitleText("开机自动启动")
                            Spacer(Modifier.height(4.dp))
                            Text("手机重新启动后继续运行蓝牙认证服务。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = boot, onCheckedChange = {
                            boot = it
                            Preferences.setAutostart(context, it)
                        })
                    }
                    HorizontalDivider()
                    Text("前台服务将显示常驻通知。部分手机的省电设置可能会限制后台蓝牙广播。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Android Keystore · ECDSA P-256 · BLE GATT v1",
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}
