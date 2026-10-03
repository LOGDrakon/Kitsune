package com.kitsune.core.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

private val MAGIC = byteArrayOf('K'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte())
private const val FORMAT_VERSION = 1
private const val HEADER_SIZE = 4 + 4 + 8 + 4 // magic + version + crc32 + manifestLength

class ArchiveIntegrityException(message: String) : Exception(message)

@Serializable
private data class ImageManifestEntry(val id: String, val size: Int)

@Serializable
private data class ArchiveManifest(
    val dbSize: Int,
    val images: List<ImageManifestEntry>,
    /** User settings/preferences (see `SecureStorage.exportTransferablePrefs`) — added after the
     * original format, defaulted so archives packed before this existed still unpack fine. */
    val prefs: Map<String, String> = emptyMap()
)

/**
 * The plaintext container bundled into a transfer envelope before PIN encryption
 * ([com.kitsune.core.security.transfer.TransferCrypto]): the exported database file plus every
 * encrypted-image-store file (already decrypted locally — see [com.kitsune.core.security.storage.EncryptedImageStore]),
 * concatenated after a small JSON manifest. Layout:
 * `magic(4) | version(4) | crc32(8) | manifestLength(4) | manifest | db | image1 | image2 | ...`
 *
 * The explicit CRC32 (checked in [unpack], separate from AES-GCM's own authentication tag) is a
 * deliberate extra integrity gate — it also catches bugs in this packer/unpacker itself, not just
 * malicious tampering, and gives the UI a distinct "checksum verified" confirmation to show the
 * user (FEATURES.md section 2: "un CRC pour confirmer que les données n'ont pas été altérées").
 */
object TransferArchive {

    data class UnpackedArchive(
        val dbBytes: ByteArray,
        val images: List<Pair<String, ByteArray>>,
        val prefs: Map<String, String> = emptyMap()
    )

    fun pack(dbBytes: ByteArray, images: List<Pair<String, ByteArray>>, prefs: Map<String, String> = emptyMap()): ByteArray {
        val manifest = ArchiveManifest(dbBytes.size, images.map { (id, bytes) -> ImageManifestEntry(id, bytes.size) }, prefs)
        val manifestBytes = Json.encodeToString(ArchiveManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)

        val payload = ByteArrayOutputStream(manifestBytes.size + dbBytes.size + images.sumOf { it.second.size }).apply {
            write(manifestBytes)
            write(dbBytes)
            images.forEach { (_, bytes) -> write(bytes) }
        }.toByteArray()

        val crc = CRC32().apply { update(payload) }.value

        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .putInt(FORMAT_VERSION)
            .putLong(crc)
            .putInt(manifestBytes.size)
            .array()

        return header + payload
    }

    fun unpack(archive: ByteArray): UnpackedArchive {
        if (archive.size < HEADER_SIZE) throw ArchiveIntegrityException("Archive too short")
        val buffer = ByteBuffer.wrap(archive)

        val magic = ByteArray(4).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) throw ArchiveIntegrityException("Not a Kitsune transfer archive")

        val version = buffer.int
        if (version != FORMAT_VERSION) throw ArchiveIntegrityException("Unsupported archive version $version")

        val expectedCrc = buffer.long
        val manifestLength = buffer.int

        val payload = archive.copyOfRange(buffer.position(), archive.size)
        val actualCrc = CRC32().apply { update(payload) }.value
        if (actualCrc != expectedCrc) {
            throw ArchiveIntegrityException("Checksum mismatch — the transferred data may be corrupted")
        }

        val manifest = Json.decodeFromString(
            ArchiveManifest.serializer(),
            String(payload.copyOfRange(0, manifestLength), Charsets.UTF_8)
        )

        var offset = manifestLength
        val dbBytes = payload.copyOfRange(offset, offset + manifest.dbSize)
        offset += manifest.dbSize

        val images = manifest.images.map { entry ->
            val bytes = payload.copyOfRange(offset, offset + entry.size)
            offset += entry.size
            entry.id to bytes
        }

        return UnpackedArchive(dbBytes, images, manifest.prefs)
    }
}
