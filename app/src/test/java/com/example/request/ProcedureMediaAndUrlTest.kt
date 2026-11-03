package com.example.request

import com.example.request.data.sync.CatalogCryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcedureMediaAndUrlTest {

    @Test
    fun testEncryptedWebsiteUrlAndRemoteImageDecryption() {
        val originalUrl = "https://example.com/apply"
        val encryptedUrl = CatalogCryptoUtils.encrypt(originalUrl)

        val originalImageUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg"
        val encryptedImageUrl = CatalogCryptoUtils.encrypt(originalImageUrl)

        assertTrue(encryptedUrl.startsWith("enc:v1:"))
        assertTrue(encryptedImageUrl.startsWith("enc:v1:"))

        val decryptedUrl = CatalogCryptoUtils.decrypt(encryptedUrl).trim()
        val decryptedImageUrl = CatalogCryptoUtils.decrypt(encryptedImageUrl).trim()

        assertEquals(originalUrl, decryptedUrl)
        assertEquals(originalImageUrl, decryptedImageUrl)
    }

    @Test
    fun testUrlNormalizationLogic() {
        fun normalizeUrl(rawUrl: String): String {
            val decrypted = CatalogCryptoUtils.decrypt(rawUrl).trim()
            return if (!decrypted.startsWith("http://", ignoreCase = true) &&
                !decrypted.startsWith("https://", ignoreCase = true)
            ) {
                "https://$decrypted"
            } else {
                decrypted
            }
        }

        // Test plain URL without scheme
        assertEquals("https://www.icct.edu.ph", normalizeUrl("www.icct.edu.ph"))

        // Test encrypted URL without scheme
        val encryptedNoScheme = CatalogCryptoUtils.encrypt("docs.google.com/forms/sample")
        assertEquals("https://docs.google.com/forms/sample", normalizeUrl(encryptedNoScheme))

        // Test URL with scheme already
        assertEquals("https://portal.icct.edu.ph", normalizeUrl("https://portal.icct.edu.ph"))
        assertEquals("http://legacy.icct.edu.ph", normalizeUrl("http://legacy.icct.edu.ph"))
    }
}
