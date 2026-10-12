package io.opentrack.core.ntes

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * NTES/CRIS mobile envelope. Key, IV and shared secret are the OFFICIAL app's own public
 * constants - the same for every client, extracted from the official APK; they are not
 * credentials and none is issued to the user (KICKOFF 3.1). Format (publicly documented):
 *
 *   request : MD5(payload + secret) # HEX(BASE64(AES-128-CBC/PKCS5(payload)))
 *   response: "jsonIn" = "<md5>#" + HEX(BASE64(AES-128-CBC(json)))
 *
 * Base64/hex are implemented here so the core stays free of android.* and of API-26-only
 * java.util.Base64 (9.4).
 */
object NtesCrypto {

    private val KEY = SecretKeySpec("8EA4DB2CC1EB3DC5".toByteArray(Charsets.US_ASCII), "AES")
    private val IV = IvParameterSpec("7DC5EB3BB4DB6EA8".toByteArray(Charsets.US_ASCII))
    private const val SECRET = "645fbc1e56e23365f2f3c204ae0899f6"

    const val BASE_URL = "https://enquiry.indianrail.gov.in/crisns/AppServAnd"
    const val USER_AGENT = "Dalvik/2.1.0 (Linux; Android 11)"

    fun buildRequest(payload: String): String =
        md5Hex(payload + SECRET) + "#" + hexUpper(base64(aesCbc(true, payload.toByteArray(Charsets.UTF_8))))

    /** Decrypts a response's jsonIn; null when the layers do not decode (never a guessed parse). */
    fun decodeResponse(jsonIn: String): String? {
        val enc = if (jsonIn.contains("#")) jsonIn.substringAfter("#") else jsonIn
        if (enc.isBlank() || enc.length % 2 != 0) return null
        if (!enc.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val cipherBytes = try {
            base64Decode(String(unhex(enc), Charsets.US_ASCII))
        } catch (e: Exception) {
            return null
        }
        val plain = try {
            aesCbc(false, cipherBytes)
        } catch (e: Exception) {
            return null
        }
        return String(plain, Charsets.UTF_8)
    }

    private fun aesCbc(forEncrypt: Boolean, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(if (forEncrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, KEY, IV)
        return cipher.doFinal(data)
    }

    private fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.uppercase()

    private fun hexUpper(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }.uppercase()

    private fun unhex(text: String): ByteArray {
        require(text.length % 2 == 0) { "odd-length hex" }
        return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    // ---- minimal base64 (no android.*, no API-26 java.util.Base64) ----

    private val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun base64(bytes: ByteArray): ByteArray {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            out.append(if (b1 >= 0) ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)] else '=')
            out.append(if (b2 >= 0) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString().toByteArray(Charsets.US_ASCII)
    }

    fun base64Decode(text: String): ByteArray {
        val clean = text.filter { it != '=' }
        require(clean.all { ALPHABET.indexOf(it) >= 0 }) { "invalid base64" }
        val bits = StringBuilder()
        clean.forEach { c ->
            val v = ALPHABET.indexOf(c)
            bits.append(v.toString(2).padStart(6, '0'))
        }
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i + 8 <= bits.length) {
            bytes.add(bits.substring(i, i + 8).toInt(2).toByte())
            i += 8
        }
        return bytes.toByteArray()
    }
}
