package top.kenvix.bleunlock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED ||
            !Preferences.autostart(context) || !Preferences.enabled(context)) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return
        try {
            context.startForegroundService(
                Intent(context, UnlockService::class.java).setAction(UnlockService.ACTION_START))
        } catch (_: RuntimeException) {
            // The OS may temporarily restrict launching foreground services after boot.
        }
    }
}
