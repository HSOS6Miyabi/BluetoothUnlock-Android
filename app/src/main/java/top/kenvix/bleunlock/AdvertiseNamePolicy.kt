package top.kenvix.bleunlock

/** Legacy BLE scan responses have 31 bytes; the local-name field uses two header bytes. */
internal object AdvertiseNamePolicy {
    private const val MAX_NAME_BYTES = 29

    fun canIncludeName(name: String?): Boolean {
        if (name.isNullOrBlank() || name.any { it.isISOControl() }) return false
        return name.toByteArray(Charsets.UTF_8).size in 1..MAX_NAME_BYTES
    }
}