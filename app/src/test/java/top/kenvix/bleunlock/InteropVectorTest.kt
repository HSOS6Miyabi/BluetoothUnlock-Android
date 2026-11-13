package top.kenvix.bleunlock

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Properties

class InteropVectorTest {
    private val fixture = Properties().apply {
        requireNotNull(InteropVectorTest::class.java.classLoader?.getResourceAsStream("interop_vectors.properties")).use { load(it) }
    }

    private fun bytes(name: String): ByteArray {
        val hex = requireNotNull(fixture.getProperty(name))
        require(hex.length % 2 == 0)
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Test fun windowsAndAndroidChallengeAndSignatureAgree() {
        val nonce = bytes("nonce")
        val pub = bytes("public_key")
        val der = bytes("signature_der")
        assertEquals(32, nonce.size)
        assertEquals(65, pub.size)
        assertEquals(4, pub[0].toInt())
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        val point = ECPoint(BigInteger(1, pub.copyOfRange(1,33)), BigInteger(1, pub.copyOfRange(33,65)))
        val key = KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(point, params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(Protocol.message(nonce))
        assertTrue(verifier.verify(der))
        val tampered = nonce.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        verifier.initVerify(key)
        verifier.update(Protocol.message(tampered))
        assertFalse(verifier.verify(der))
        assertEquals(fixture.getProperty("fingerprint"), Protocol.fingerprint(pub))
        val checksum = MessageDigest.getInstance("SHA-256").digest(Protocol.message(nonce))
        assertArrayEquals(bytes("message_sha256"), checksum)
        val frame = Protocol.response(1, der)
        assertEquals(1, frame[0].toInt())
        assertEquals(1, frame[1].toInt())
        assertEquals(der.size, frame[2].toInt() and 0xff)
        assertArrayEquals(der, frame.copyOfRange(3, frame.size))
        val fragments = Protocol.challengeFragments(nonce)
        assertArrayEquals(nonce, fragments.flatMap { it.drop(1) }.toByteArray())
    }
}