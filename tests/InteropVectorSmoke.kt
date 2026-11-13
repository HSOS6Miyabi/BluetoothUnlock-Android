package top.kenvix.bleunlock

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.ECParameterSpec
import java.util.Properties

fun main() {
    val fixture = Properties().apply {
        Files.newInputStream(Path.of("app/src/test/resources/interop_vectors.properties")).use { load(it) }
    }
    fun bytes(name: String) = requireNotNull(fixture.getProperty(name)).chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()
    val nonce = bytes("nonce")
    val publicKey = bytes("public_key")
    val signature = bytes("signature_der")
    val params = AlgorithmParameters.getInstance("EC")
    params.init(ECGenParameterSpec("secp256r1"))
    val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(
        ECPoint(BigInteger(1,publicKey.copyOfRange(1,33)),BigInteger(1,publicKey.copyOfRange(33,65))),
        params.getParameterSpec(ECParameterSpec::class.java)))
    fun verify(challenge: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(Protocol.message(challenge))
        return verifier.verify(signature)
    }
    check(verify(nonce))
    check(!verify(nonce.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }))
    check(Protocol.fingerprint(publicKey)==fixture.getProperty("fingerprint"))
    check(MessageDigest.getInstance("SHA-256").digest(Protocol.message(nonce)).contentEquals(bytes("message_sha256")))
    check(Protocol.response(1,signature).copyOfRange(3,signature.size+3).contentEquals(signature))
    val fragments=Protocol.challengeFragments(nonce)
    check((fragments[0].drop(1)+fragments[1].drop(1)).toByteArray().contentEquals(nonce))
    println("Android / Windows signed GATT interoperability vectors passed")
}
