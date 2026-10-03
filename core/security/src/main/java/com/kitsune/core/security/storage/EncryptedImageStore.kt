package com.kitsune.core.security.storage

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * At-rest storage for user-supplied images (persona avatars, chat backgrounds — FEATURES.md
 * section 2's "chiffrement des fichiers images"). Each image is its own [EncryptedFile], keyed by
 * a random id so callers only ever persist an opaque id (e.g. on [com.kitsune.core.data.local.entities.PersonaEntity]),
 * never a raw filesystem path.
 */
@Singleton
class EncryptedImageStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val imagesDir: File
        get() = File(context.filesDir, "encrypted_images").apply { mkdirs() }

    /** Encrypts [bytes] to a new file and returns its id. */
    fun save(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        encryptedFile(id).openFileOutput().use { it.write(bytes) }
        return id
    }

    /** Returns the decrypted bytes for [id], or null if it doesn't exist. */
    fun load(id: String): ByteArray? {
        val file = File(imagesDir, fileName(id))
        if (!file.exists()) return null
        return encryptedFile(id).openFileInput().use { it.readBytes() }
    }

    fun delete(id: String) {
        File(imagesDir, fileName(id)).delete()
    }

    /** Wipes every stored image (avatars, galleries, backgrounds) — used when deleting all local
     * data, since none of these ids will resolve to anything once the database they're referenced
     * from is gone either. */
    fun deleteAll() {
        imagesDir.listFiles()?.forEach { it.delete() }
    }

    /** Every currently stored image id — used to bundle the full gallery into an account-transfer
     * archive (see core:transfer). */
    fun listAllIds(): List<String> =
        imagesDir.listFiles()?.mapNotNull { f -> f.name.takeIf { it.endsWith(".enc") }?.removeSuffix(".enc") }.orEmpty()

    /** Writes [bytes] under the EXACT given [id], unlike [save] (which mints a random one) — used
     * only when importing an account-transfer archive, where ids must match what the imported
     * database rows already reference (avatarImageId, imageAttachmentPath, etc). Every id involved
     * in a transfer is re-encrypted here with THIS device's own Keystore-backed master key — the
     * old device's raw `.enc` files are never copied as-is, since Jetpack Security's master key is
     * hardware-bound and cannot be reproduced on a different device. */
    fun saveWithId(id: String, bytes: ByteArray) {
        File(imagesDir, fileName(id)).delete()
        encryptedFile(id).openFileOutput().use { it.write(bytes) }
    }

    private fun encryptedFile(id: String): EncryptedFile = EncryptedFile.Builder(
        context,
        File(imagesDir, fileName(id)),
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    private fun fileName(id: String) = "$id.enc"
}
