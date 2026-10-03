package com.kitsune.core.security.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 5869 HKDF-SHA256, used to combine two independent secrets into a single output key. */
object Hkdf {

    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_LENGTH_BYTES = 32

    fun deriveKey(inputKeyMaterial: ByteArray, info: ByteArray, salt: ByteArray = ByteArray(HASH_LENGTH_BYTES), outputLengthBytes: Int = 32): ByteArray {
        val pseudoRandomKey = extract(salt, inputKeyMaterial)
        return expand(pseudoRandomKey, info, outputLengthBytes)
    }

    private fun extract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(salt, ALGORITHM))
        return mac.doFinal(inputKeyMaterial)
    }

    private fun expand(pseudoRandomKey: ByteArray, info: ByteArray, outputLengthBytes: Int): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(pseudoRandomKey, ALGORITHM))

        val output = ByteArray(outputLengthBytes)
        var previousBlock = ByteArray(0)
        var bytesWritten = 0
        var counter = 1
        while (bytesWritten < outputLengthBytes) {
            mac.reset()
            mac.update(previousBlock)
            mac.update(info)
            mac.update(counter.toByte())
            previousBlock = mac.doFinal()
            val bytesToCopy = minOf(previousBlock.size, outputLengthBytes - bytesWritten)
            previousBlock.copyInto(output, bytesWritten, 0, bytesToCopy)
            bytesWritten += bytesToCopy
            counter++
        }
        return output
    }
}
