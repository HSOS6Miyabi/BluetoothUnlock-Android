package top.kenvix.bleunlock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Implements the peripheral role, signing challenges without sending the private key over BLE. */
class UnlockService : Service() {
    companion object {
        const val ACTION_START = "top.kenvix.bleunlock.START"
        const val ACTION_STOP = "top.kenvix.bleunlock.STOP"
        const val ACTION_MODE_CHANGED = "top.kenvix.bleunlock.MODE_CHANGED"
        const val ACTION_APPROVE = "top.kenvix.bleunlock.APPROVE"
        const val ACTION_REJECT = "top.kenvix.bleunlock.REJECT"
        const val EXTRA_ID = "request_id"
        const val EXTRA_ADDRESS = "request_address"
        private const val CHANNEL_ACTIVE = "ble_active"
        private const val CHANNEL_CONFIRM = "ble_confirm"
        private const val NOTIFICATION_ACTIVE = 2031
        val running = MutableStateFlow(false)
        val status = MutableStateFlow("服务尚未启动")
        val requests = MutableStateFlow(0)
    }

    private data class FirstFragment(val bytes: ByteArray, val startMillis: Long)

    private data class Challenge(
        val id: Int,
        val nonce: ByteArray,
        val startMillis: Long,
        var frame: ByteArray = Protocol.response(0)
    )

    private val lock = Any()
    private val nextRequest = AtomicInteger(100_000)
    private val challenges = mutableMapOf<String, Challenge>()
    private val firstFragments = mutableMapOf<String, FirstFragment>()
    private val recentAttempts = mutableMapOf<String, Long>()
    private val expirationHandler = Handler(Looper.getMainLooper())
    private lateinit var identity: KeystoreIdentity
    private lateinit var adapter: BluetoothAdapter
    private var gatt: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var active = false
    private var advertising = false
    private var advertisingIncludesName = false
    private var nameOmitted = false
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED || !active) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    stopPeripheral()
                    status.value = "手机蓝牙已关闭，等待重新打开"
                }
                BluetoothAdapter.STATE_ON -> {
                    expirationHandler.postDelayed({
                        if (active && hasBluetoothPermissions() && gatt == null) {
                            try { launchPeripheral() } catch (_: Exception) {
                                status.value = "重新启动蓝牙服务失败，请检查系统设置"
                            }
                        }
                    }, 800L)
                }
            }
        }
    }

    private fun hasBluetoothPermissions() =
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        identity = KeystoreIdentity()
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_ACTIVE, "蓝牙解锁后台服务", NotificationManager.IMPORTANCE_LOW))
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_CONFIRM, "Windows 解锁请求", NotificationManager.IMPORTANCE_HIGH))
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Preferences.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPROVE, ACTION_REJECT -> {
                if (!active) { stopSelf(startId); return START_NOT_STICKY }
                decide(intent.getStringExtra(EXTRA_ADDRESS).orEmpty(),
                    intent.getIntExtra(EXTRA_ID, -1), intent.action == ACTION_APPROVE)
                return START_STICKY
            }
            ACTION_MODE_CHANGED -> {
                if (!active) { stopSelf(startId); return START_NOT_STICKY }
                synchronized(lock) {
                    challenges.values.forEach { cancelChallengeNotification(it.id) }
                    challenges.clear()
                    firstFragments.clear()
                    requests.value = 0
                }
                refreshForegroundNotification()
                status.value = if (Preferences.manual(this)) "手动确认已启用" else "自动认证已启用"
                return START_STICKY
            }
        }
        if (active) return START_STICKY
        if (!hasBluetoothPermissions() || !adapter.isEnabled) {
            status.value = "请先授权蓝牙权限并打开手机蓝牙"
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ACTIVE, ongoingNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ACTIVE, ongoingNotification())
            }
            active = true
            Preferences.setEnabled(this, true)
            running.value = true
            launchPeripheral()
        } catch (e: Exception) {
            status.value = "蓝牙服务启动失败：${e.localizedMessage ?: "系统限制"}"
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startIntent(action: String): PendingIntent {
        val intent = Intent(this, UnlockService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ongoingNotification(): Notification {
        val open = PendingIntent.getActivity(this, 11,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val mode = if (Preferences.manual(this)) "手动确认" else "自动认证"
        return Notification.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("蓝牙解锁正在运行")
            .setContentText("$mode · 正在等待附近 Windows 电脑")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "停止", startIntent(ACTION_STOP)).build())
            .build()
    }

    private fun refreshForegroundNotification() {
        if (active) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ACTIVE, ongoingNotification())
    }

    private fun manualNotification(address: String, challenge: Challenge) {
        val open = PendingIntent.getActivity(this, challenge.id,
            Intent(this, ApprovalActivity::class.java)
                .putExtra(EXTRA_ID, challenge.id)
                .putExtra(EXTRA_ADDRESS, address),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        val reject = PendingIntent.getService(this, challenge.id + 100_000,
            Intent(this, UnlockService::class.java).setAction(ACTION_REJECT)
                .putExtra(EXTRA_ID, challenge.id).putExtra(EXTRA_ADDRESS, address),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_CONFIRM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("确认解锁 Windows")
            .setContentText("附近电脑正在请求解锁，请进行生物识别或输入手机密码。")
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "确认解锁", open).build())
            .addAction(Notification.Action.Builder(null, "拒绝", reject).build())
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(challenge.id, notification)
    }

    private fun cancelChallengeNotification(id: Int) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }

    private fun expireChallenge(address: String, id: Int) {
        synchronized(lock) {
            val challenge = challenges[address] ?: return
            if (challenge.id != id) return
            challenges.remove(address)
            if (challenge.frame[1] == 0.toByte()) {
                requests.value = (requests.value - 1).coerceAtLeast(0)
            }
        }
        cancelChallengeNotification(id)
    }

    private fun decide(address: String, id: Int, approved: Boolean) {
        synchronized(lock) {
            val entry = challenges[address]
            if (entry == null || entry.id != id ||
                SystemClock.elapsedRealtime() - entry.startMillis > Protocol.EXPIRY_MILLIS) return
            if (entry.frame[1] != 0.toByte()) return
            entry.frame = if (approved) sign(entry.nonce) else Protocol.response(2)
            requests.value = (requests.value - 1).coerceAtLeast(0)
        }
        cancelChallengeNotification(id)
        status.value = if (approved) "已批准一次 Windows 解锁" else "已拒绝本次解锁"
    }

    private fun sign(nonce: ByteArray): ByteArray = try {
        Protocol.response(1, identity.sign(nonce))
    } catch (_: Exception) {
        Protocol.response(2)
    }

    @SuppressLint("MissingPermission")
    private fun launchPeripheral() {
        if (!hasBluetoothPermissions()) {
            status.value = "蓝牙权限已被撤销，请重新授权"
            stopSelf()
            return
        }
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val server = manager.openGattServer(this, gattCallbacks)
        if (server == null) {
            status.value = "手机无法打开蓝牙 GATT 服务"
            stopSelf()
            return
        }
        gatt = server
        val service = BluetoothGattService(Protocol.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(
            Protocol.PUBLIC_KEY, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ))
        service.addCharacteristic(BluetoothGattCharacteristic(
            Protocol.CHALLENGE, BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE))
        service.addCharacteristic(BluetoothGattCharacteristic(
            Protocol.RESPONSE, BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ))
        if (!server.addService(service)) {
            status.value = "无法注册蓝牙认证服务"
            stopSelf()
        }
    }

    private fun advertise() {
        val le = adapter.bluetoothLeAdvertiser
        if (le == null) {
            status.value = "当前手机不支持蓝牙低功耗外围设备广播"
            stopSelf()
            return
        }
        advertiser = le
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(Protocol.SERVICE))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        // Advertising the service UUID always takes precedence over sending the
        // name. A long phone name must never disable the authentication service.
        val deviceName = if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        ) try {
            adapter.name
        } catch (_: SecurityException) {
            null
        } else null
        val includeName = AdvertiseNamePolicy.canIncludeName(deviceName)
        advertisingIncludesName = includeName
        nameOmitted = !includeName
        if (includeName) {
            try {
                val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
                le.startAdvertising(settings, data, scanResponse, advertiseCallbacks)
            } catch (_: IllegalArgumentException) {
                advertisingIncludesName = false
                nameOmitted = true
                le.startAdvertising(settings, data, advertiseCallbacks)
            }
        } else {
            le.startAdvertising(settings, data, advertiseCallbacks)
        }
    }

    private val advertiseCallbacks: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            val mode = if (Preferences.manual(this@UnlockService))
                "需要手机确认解锁" else "自动响应认证"
            status.value = if (nameOmitted)
                "正在广播 · $mode · 设备名称未发送，Windows 会显示解锁服务设备"
            else "正在广播 · $mode"
        }
        override fun onStartFailure(errorCode: Int) {
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE &&
                advertisingIncludesName) {
                advertisingIncludesName = false
                nameOmitted = true
                try {
                    val primary = AdvertiseData.Builder()
                        .addServiceUuid(ParcelUuid(Protocol.SERVICE)).build()
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                        .setConnectable(true).build()
                    advertiser?.startAdvertising(settings, primary, this)
                    return
                } catch (_: Exception) { }
            }
            status.value = "蓝牙广播失败（错误代码 $errorCode），请检查手机的蓝牙外围设备广播能力"
            stopSelf()
        }
    }

    private val gattCallbacks = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(statusCode: Int, service: BluetoothGattService?) {
            if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                if (active && adapter.isEnabled) {
                    try { advertise() } catch (_: SecurityException) { stopSelf() }
                }
            } else {
                status.value = "蓝牙 GATT 服务注册失败（$statusCode）"
                stopSelf()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) synchronized(lock) {
                firstFragments.remove(device.address)
                val old = challenges.remove(device.address)
                if (old != null && old.frame[1] == 0.toByte()) {
                    requests.value = (requests.value - 1).coerceAtLeast(0)
                }
                if (old != null) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(old.id)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = when (characteristic.uuid) {
                Protocol.PUBLIC_KEY -> try { identity.publicKeyPoint() } catch (_: Exception) { byteArrayOf() }
                Protocol.RESPONSE -> synchronized(lock) {
                    val pending = challenges[device.address]
                    if (pending == null) Protocol.response(2)
                    else if (SystemClock.elapsedRealtime() - pending.startMillis > Protocol.EXPIRY_MILLIS) {
                        challenges.remove(device.address)
                        cancelChallengeNotification(pending.id)
                        if (pending.frame[1] == 0.toByte())
                            requests.value = (requests.value - 1).coerceAtLeast(0)
                        Protocol.response(2)
                    } else pending.frame.copyOf()
                }
                else -> null
            }
            val error = if (data == null) BluetoothGatt.GATT_READ_NOT_PERMITTED
                else if (offset !in 0..data.size) BluetoothGatt.GATT_INVALID_OFFSET
                else BluetoothGatt.GATT_SUCCESS
            try {
                gatt?.sendResponse(device, requestId, error, offset,
                    if (error == BluetoothGatt.GATT_SUCCESS) data!!.copyOfRange(offset, data.size)
                    else byteArrayOf())
            } catch (_: SecurityException) { }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            val allowed = characteristic.uuid == Protocol.CHALLENGE &&
                !preparedWrite && offset == 0
            var accepted = false
            var pending: Challenge? = null
            if (allowed) synchronized(lock) {
                val now = SystemClock.elapsedRealtime()
                var nonce = Protocol.decodeChallenge(value)
                if (value.size == Protocol.CHUNK_LENGTH + 1 && value[0] == Protocol.FIRST_CHUNK) {
                    val old = firstFragments[device.address]
                    if ((old == null || now - old.startMillis >= 1_500L) &&
                        (old != null || firstFragments.size < 4)) {
                        val fragment = FirstFragment(value.copyOfRange(1, value.size), now)
                        firstFragments[device.address] = fragment
                        expirationHandler.postDelayed({
                            synchronized(lock) {
                                if (firstFragments[device.address] === fragment)
                                    firstFragments.remove(device.address)
                            }
                        }, 5_000L)
                        accepted = true
                    }
                } else if (value.size == Protocol.CHUNK_LENGTH + 1 &&
                    value[0] == Protocol.SECOND_CHUNK) {
                    val first = firstFragments.remove(device.address)
                    if (first != null && now - first.startMillis <= 5_000L) {
                        nonce = first.bytes + value.copyOfRange(1, value.size)
                    }
                }
                if (nonce != null) {
                    val last = recentAttempts[device.address]
                    // Bound concurrent requests and avoid repeated notifications from a single peer.
                    if ((last == null || now - last >= 2_500L) &&
                        (challenges.containsKey(device.address) || challenges.size < 4)) {
                        recentAttempts[device.address] = now
                        if (recentAttempts.size > 64) {
                            recentAttempts.entries.removeAll { now - it.value > 60_000L }
                            if (recentAttempts.size > 64) {
                                val oldest = recentAttempts.minBy { it.value }.key
                                recentAttempts.remove(oldest)
                            }
                        }
                        val previous = challenges.remove(device.address)
                        if (previous != null) {
                            cancelChallengeNotification(previous.id)
                            if (previous.frame[1] == 0.toByte())
                                requests.value = (requests.value - 1).coerceAtLeast(0)
                        }
                        pending = Challenge(nextRequest.incrementAndGet(), nonce, now)
                        challenges[device.address] = pending!!
                        accepted = true
                    }
                }
            }
            if (responseNeeded) try {
                gatt?.sendResponse(device, requestId,
                    if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                    offset, byteArrayOf())
            } catch (_: SecurityException) { }
            if (pending != null) {
                val challenge = pending!!
                expirationHandler.postDelayed({
                    expireChallenge(device.address, challenge.id)
                }, Protocol.EXPIRY_MILLIS)
                if (Preferences.manual(this@UnlockService)) {
                    synchronized(lock) { requests.value += 1 }
                    manualNotification(device.address, challenge)
                    status.value = "等待手机确认 Windows 解锁"
                } else {
                    val signed = sign(challenge.nonce)
                    synchronized(lock) {
                        if (challenges[device.address] === challenge) challenge.frame = signed
                    }
                    status.value = if (signed[1] == 1.toByte())
                        "已完成一次自动身份认证" else "私钥认证失败"
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopPeripheral() {
        try {
            advertiser?.stopAdvertising(advertiseCallbacks)
            gatt?.clearServices()
            gatt?.close()
        } catch (_: Exception) { }
        advertiser = null
        gatt = null
        advertising = false
        advertisingIncludesName = false
        nameOmitted = false
        synchronized(lock) {
            challenges.values.forEach { cancelChallengeNotification(it.id) }
            challenges.clear()
            firstFragments.clear()
            requests.value = 0
        }
    }

    override fun onDestroy() {
        active = false
        expirationHandler.removeCallbacksAndMessages(null)
        running.value = false
        requests.value = 0
        synchronized(lock) {
            challenges.values.forEach {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(it.id)
            }
            challenges.clear()
            firstFragments.clear()
            recentAttempts.clear()
        }
        stopPeripheral()
        try { unregisterReceiver(bluetoothStateReceiver) } catch (_: IllegalArgumentException) { }
        status.value = "服务已停止"
        super.onDestroy()
    }
}
