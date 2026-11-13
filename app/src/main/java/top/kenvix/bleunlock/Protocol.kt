package top.kenvix.bleunlock

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Stable application wire protocol shared with the Windows C++ implementation. */
object Protocol {
    val SERVICE: UUID = UUID.fromString("5b0d6834-e3e9-48c0-977a-877a5d1f2b45")
    val PUBLIC_KEY: UUID = UUID.fromString("5b0d6835-e3e9-48c0-977a-877a5d1f2b45")
    val CHALLENGE: UUID = UUID.fromString("5b0d6836-e3e9-48c0-977a-877a5d1f2b45")
    val RESPONSE: UUID = UUID.fromString("5b0d6837-e3e9-48c0-977a-877a5d1f2b45")
    const val VERSION: Byte = 1
    const val NONCE_LENGTH = 32
    const val CHUNK_LENGTH = 16
    const val FIRST_CHUNK: Byte = 0x11
    const val SECOND_CHUNK: Byte = 0x12
    const val PUBLIC_KEY_LENGTH = 65
    const val EXPIRY_MILLIS = 30_000L

    fun decodeChallenge(data: ByteArray): ByteArray? {
        if (data.size != NONCE_LENGTH + 1 || data[0] != VERSION) return null
        return data.copyOfRange(1, data.size)
    }

    fun challengeFragments(nonce: ByteArray): List<ByteArray> {
        require(nonce.size == NONCE_LENGTH)
        return listOf(byteArrayOf(FIRST_CHUNK) + nonce.copyOfRange(0, CHUNK_LENGTH),
            byteArrayOf(SECOND_CHUNK) + nonce.copyOfRange(CHUNK_LENGTH, NONCE_LENGTH))
    }

    fun message(nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE_LENGTH)
        return "KENVIX-BLE-UNLOCK/v1:".toByteArray(StandardCharsets.US_ASCII) + nonce
    }

    fun response(status: Int, signature: ByteArray = byteArrayOf()): ByteArray {
        require(status in 0..2)
        require((status == 1 && signature.size in 8..72) || (status != 1 && signature.isEmpty()))
        return byteArrayOf(VERSION, status.toByte(), signature.size.toByte()) + signature
    }

    fun fingerprint(key: ByteArray): String {
        require(key.size == PUBLIC_KEY_LENGTH && key[0] == 4.toByte())
        return MessageDigest.getInstance("SHA-256").digest(key)
            .take(8).joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }
}
