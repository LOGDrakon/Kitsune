package com.kitsune.core.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferPairingTest {

    @Test
    fun `round trip preserves transferId, pullToken and transferKey exactly`() {
        val transferKey = ByteArray(32) { it.toByte() }

        val payload = TransferPairing.buildQrPayload("transfer-123", "pull-token-abc", transferKey)
        val parsed = TransferPairing.parseQrPayload(payload)

        requireNotNull(parsed)
        assertEquals("transfer-123", parsed.transferId)
        assertEquals("pull-token-abc", parsed.pullToken)
        assertArrayEquals(transferKey, parsed.transferKey)
    }

    @Test
    fun `rejects a QR code that does not come from Kitsune`() {
        assertNull(TransferPairing.parseQrPayload("https://evil.example/not-a-transfer-code"))
    }

    @Test
    fun `rejects an empty payload`() {
        assertNull(TransferPairing.parseQrPayload(""))
    }

    @Test
    fun `rejects a pre-transfer-key (v1 shaped) payload instead of silently accepting it without a key`() {
        // Old shape: "kitsune-transfer:v1:<id>:<token>" — must fail closed now that the transfer
        // key is a required security component, not be silently treated as "no transfer key".
        assertNull(TransferPairing.parseQrPayload("kitsune-transfer:v1:transfer-123:pull-token-abc"))
    }

    @Test
    fun `rejects a v2 payload with malformed hex in the transfer key segment`() {
        assertNull(TransferPairing.parseQrPayload("kitsune-transfer:v2:transfer-123:pull-token-abc:not-valid-hex"))
    }

    @Test
    fun `rejects a v2 payload with an odd-length hex transfer key segment`() {
        assertNull(TransferPairing.parseQrPayload("kitsune-transfer:v2:transfer-123:pull-token-abc:abc"))
    }

    @Test
    fun `rejects a payload missing the pull token`() {
        assertNull(TransferPairing.parseQrPayload("kitsune-transfer:v2::abcd"))
    }
}
