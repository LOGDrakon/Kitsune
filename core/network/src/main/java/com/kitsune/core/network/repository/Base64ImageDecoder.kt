package com.kitsune.core.network.repository

import java.util.Base64

/** Decodes a `data:image/...;base64,...` URI (or a bare base64 payload) into raw bytes. Returns null for anything else (e.g. a remote http(s) URL — not supported yet, see IDEAS.md). */
fun decodeBase64ImageDataUri(uri: String): ByteArray? {
    val marker = ";base64,"
    val markerIndex = uri.indexOf(marker)
    if (markerIndex == -1 && uri.startsWith("http", ignoreCase = true)) return null
    val payload = if (markerIndex != -1) uri.substring(markerIndex + marker.length) else uri
    return runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
}

/**
 * Encodes raw image bytes as a `data:image/...;base64,...` URI — the inverse of
 * [decodeBase64ImageDataUri], used to attach a reference image (e.g. a persona's existing avatar)
 * as an input image for image-to-image visual consistency (FEATURES.md section 5).
 */
fun encodeBase64ImageDataUri(bytes: ByteArray, mimeType: String = "image/png"): String =
    "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
