package top.kenvix.bleunlock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class KeystoreIdentity {
    companion object {
        private const val ALIAS = "kenvix_unlock_identity_v1"
        private val creationLock = Any()
    }

    private fun keyPair(): KeyPair = synchronized(creationLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val current = store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (current != null) return KeyPair(current.certificate.publicKey, current.privateKey)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val params = KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(params)
        return generator.generateKeyPair()
    }

    private fun coordinate(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhileIndexedLeadingSignByte()
        require(bytes.size <= 32)
        return ByteArray(32 - bytes.size) + bytes
    }

    private fun ByteArray.dropWhileIndexedLeadingSignByte(): ByteArray =
        if (size == 33 && this[0] == 0.toByte()) copyOfRange(1, size) else this

    fun publicKeyPoint(): ByteArray {
        val ec = keyPair().public as ECPublicKey
        return byteArrayOf(4) + coordinate(ec.w.affineX) + coordinate(ec.w.affineY)
    }

    fun sign(nonce: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair().private)
        signature.update(Protocol.message(nonce))
        return signature.sign()
    }

    fun fingerprint(): String = Protocol.fingerprint(publicKeyPoint())
}
