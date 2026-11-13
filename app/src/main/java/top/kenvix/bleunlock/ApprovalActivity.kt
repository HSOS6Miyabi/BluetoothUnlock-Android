package top.kenvix.bleunlock

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Require a fresh phone unlock confirmation for every manual Windows unlock request. */
class ApprovalActivity : ComponentActivity() {
    private var completed = false
    private val cancellation = CancellationSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(UnlockService.EXTRA_ID, -1)
        val address = intent.getStringExtra(UnlockService.EXTRA_ADDRESS).orEmpty()
        if (id < 0 || address.isEmpty()) { finish(); return }
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Scaffold { insets ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(insets).padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("KENVIX / SECURITY", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(18.dp))
                        Text("确认解锁请求", style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Card(shape = MaterialTheme.shapes.extraLarge) {
                            Column(Modifier.fillMaxWidth().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Windows 电脑正在请求解锁",
                                    style = MaterialTheme.typography.titleLarge)
                                Text("请完成手机系统身份验证，以批准本次解锁。认证完成后，电脑将检查设备信号强度并提交 Windows 登录凭据。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("此请求最多有效 30 秒。",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        OutlinedButton(onClick = { complete(false, id, address) },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("拒绝本次请求")
                        }
                    }
                }
            }
        }
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("确认 Windows 解锁")
                .setSubtitle("使用手机生物识别或锁屏密码确认本次请求。")
                .setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(cancellation, mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        complete(true, id, address)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        complete(false, id, address)
                    }
                })
        } catch (_: Exception) {
            complete(false, id, address)
        }
    }

    private fun complete(approved: Boolean, id: Int, address: String) {
        if (completed) return
        completed = true
        startService(Intent(this, UnlockService::class.java)
            .setAction(if (approved) UnlockService.ACTION_APPROVE else UnlockService.ACTION_REJECT)
            .putExtra(UnlockService.EXTRA_ID, id)
            .putExtra(UnlockService.EXTRA_ADDRESS, address))
        finish()
    }

    override fun onDestroy() {
        cancellation.cancel()
        super.onDestroy()
    }
}
