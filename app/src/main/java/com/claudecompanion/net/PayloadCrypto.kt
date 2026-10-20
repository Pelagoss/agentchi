package com.claudecompanion.net

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement de bout en bout des événements (format v1), compatible avec
 * `openssl` côté hook : AES-256-CBC puis HMAC-SHA256 (encrypt-then-MAC).
 *
 *   message = "v1.<iv hex>.<chiffré base64>.<hmac hex>"
 *   hmac    = HMAC-SHA256(clé MAC, "v1.<iv hex>.<chiffré base64>")
 *
 * Le serveur ntfy ne voit jamais le contenu, et un message qui n'a pas été
 * produit avec la clé de la montre est rejeté.
 */
object PayloadCrypto {
    private const val VERSION = "v1"

    fun encKey(secret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("claude-companion/enc:$secret".toByteArray())

    fun macKey(secret: String): ByteArray = "claude-companion/mac:$secret".toByteArray()

    /** Retourne le texte clair, ou null si le message est invalide ou n'est pas signé avec [secret]. */
    fun decrypt(payload: String, secret: String): String? {
        if (secret.isBlank()) return null
        val parts = payload.trim().split('.')
        if (parts.size != 4 || parts[0] != VERSION) return null
        val (_, ivHex, cipherB64, macHex) = parts
        val given = hexToBytes(macHex) ?: return null
        if (!MessageDigest.isEqual(hmac(macKey(secret), "$VERSION.$ivHex.$cipherB64"), given)) return null
        val iv = hexToBytes(ivHex)?.takeIf { it.size == 16 } ?: return null
        val cipherText = runCatching { Base64.getDecoder().decode(cipherB64) }.getOrNull() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey(secret), "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    fun encrypt(plain: String, secret: String, iv: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey(secret), "AES"), IvParameterSpec(iv))
        val head = "$VERSION.${iv.toHex()}.${Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray()))}"
        return "$head.${hmac(macKey(secret), head).toHex()}"
    }

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data.toByteArray())
        }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return runCatching { ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}
