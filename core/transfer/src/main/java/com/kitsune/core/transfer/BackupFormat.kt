package com.kitsune.core.transfer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A Kitsune backup file: `magic(8) | TransferCrypto envelope`, where the envelope encrypts a
 * [TransferArchive] (database + images + settings) with a key derived from the user's passphrase
 * (Argon2id, then HKDF — see `TransferCrypto`).
 *
 * The file replaces the hosted version's QR-code transfer, which relayed the same envelope through
 * the server. A file needs no server, works across any distance and doubles as a backup — but it
 * also sits wherever the user puts it (a cloud drive, an email) for as long as they keep it. That is
 * why it is protected by a **passphrase** with a real minimum length, not a 6-digit PIN: the QR
 * flow could afford a short PIN because the envelope only existed for minutes and half of its key
 * travelled in the QR code itself; a file has neither protection.
 */
object BackupFormat {
    /** `KITSBAK1` — identifies a backup file and its format version. */
    val MAGIC: ByteArray = "KITSBAK1".toByteArray(Charsets.US_ASCII)

    /** Below this, an offline brute force of a stolen backup file becomes realistic despite Argon2id. */
    const val MIN_PASSPHRASE_LENGTH = 10

    /** The envelope is decryptable from the passphrase alone; `TransferCrypto` folds this fixed,
     * public value in where the QR flow folded its random key. Keeping the derivation shared means
     * one audited code path for both. */
    val FIXED_KEY_COMPONENT: ByteArray = "kitsune-file-backup-v1".toByteArray(Charsets.US_ASCII)

    const val MIME_TYPE = "application/octet-stream"

    fun suggestedFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(nowMillis))
        return "kitsune-$date.kitsune"
    }

    fun hasMagic(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
}

class NotABackupFileException : Exception("Ce fichier n’est pas une sauvegarde Kitsune.")
