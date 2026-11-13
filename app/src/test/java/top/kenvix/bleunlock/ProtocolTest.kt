package top.kenvix.bleunlock

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class ProtocolTest {
    private val nonce = ByteArray(32) { it.toByte() }

    @Test fun challengeAndMessageAgreeWithWindows() {
        assertArrayEquals(nonce, Protocol.decodeChallenge(byteArrayOf(1) + nonce))
        assertNull(Protocol.decodeChallenge(byteArrayOf(2) + nonce))
        assertNull(Protocol.decodeChallenge(byteArrayOf(1) + nonce.copyOfRange(1, 32)))
        assertEquals(2, Protocol.challengeFragments(nonce).size)
        val chunks = Protocol.challengeFragments(nonce)
        assertEquals(17, chunks[0].size)
        assertEquals(17, chunks[1].size)
        assertArrayEquals(nonce, (chunks[0].drop(1) + chunks[1].drop(1)).toByteArray())
        assertEquals(53, Protocol.message(nonce).size)
        assertEquals("KENVIX-BLE-UNLOCK/v1:", Protocol.message(nonce).copyOfRange(0, 21).toString(Charsets.US_ASCII))
    }

    @Test fun responseFramesHaveExpectedStatusAndLength() {
        assertArrayEquals(byteArrayOf(1, 0, 0), Protocol.response(0))
        assertArrayEquals(byteArrayOf(1, 2, 0), Protocol.response(2))
        assertEquals(73, Protocol.response(1, ByteArray(70)).size)
        assertThrows(IllegalArgumentException::class.java) { Protocol.response(1) }
    }

    @Test fun ecdsaSignatureOnDomainSeparatedChallengeIsVerifiable() {
        val keys = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keys.private)
        signature.update(Protocol.message(nonce))
        val signed = signature.sign()
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keys.public)
        verifier.update(Protocol.message(nonce))
        assertTrue(verifier.verify(signed))
        verifier.initVerify(keys.public)
        verifier.update(Protocol.message(ByteArray(32)))
        assertFalse(verifier.verify(signed))
    }

    @Test fun keyFingerprintUsesSha256FirstEightBytes() {
        val key=ByteArray(65).apply { this[0]=4; this[64]=1 }
        val expected=MessageDigest.getInstance("SHA-256").digest(key).take(8)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
        assertEquals(expected,Protocol.fingerprint(key))
    }
}
