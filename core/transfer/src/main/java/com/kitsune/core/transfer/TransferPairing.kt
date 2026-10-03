package com.kitsune.core.transfer

private const val QR_PREFIX = "kitsune-transfer:v2:"

data class ParsedTransferQr(val transferId: String, val pullToken: String, val transferKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is ParsedTransferQr && transferId == other.transferId && pullToken == other.pullToken && transferKey.contentEquals(other.transferKey)

    override fun hashCode(): Int = 31 * (31 * transferId.hashCode() + pullToken.hashCode()) + transferKey.contentHashCode()
}

/**
 * Everything encoded in the QR code the old phone displays: the pairing identifiers, plus the
 * high-entropy half of the transfer encryption key ([com.kitsune.core.security.transfer.TransferCrypto]
 * — see that class's doc for why a PIN alone isn't enough). The transfer PIN itself is still never
 * included here — it travels only through the user's own memory, typed separately on each device.
 * The identifiers don't need to stay secret on their own (nothing here decrypts anything without
 * the PIN too), but keeping them out of logs/screenshots is still good hygiene: they're single-use,
 * short-lived session identifiers, and the transfer key genuinely must stay confidential.
 *
 * No Android framework dependency deliberately (hex rather than `android.util.Base64`) — this file
 * is pure string/byte parsing, kept trivially unit-testable on the plain JVM.
 */
object TransferPairing {
    fun buildQrPayload(transferId: String, pullToken: String, transferKey: ByteArray): String =
        "$QR_PREFIX$transferId:$pullToken:${transferKey.toHex()}"

    fun parseQrPayload(raw: String): ParsedTransferQr? {
        if (!raw.startsWith(QR_PREFIX)) return null
        val parts = raw.removePrefix(QR_PREFIX).split(":")
        if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return null
        val transferKey = parts[2].hexToBytesOrNull() ?: return null
        if (transferKey.isEmpty()) return null
        return ParsedTransferQr(transferId = parts[0], pullToken = parts[1], transferKey = transferKey)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i -> ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
