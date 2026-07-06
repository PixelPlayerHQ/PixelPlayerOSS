package com.lostf1sh.pixelplayeross.data.network

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class ConnectionErrorsTest {

    @Test
    fun `ssl handshake failure gets an actionable CA install message`() {
        val raw = SSLHandshakeException("Trust anchor for certification path not found.")
        val humanized = ConnectionErrors.humanize(raw)
        assertTrue(humanized.message!!.contains("CA certificate"), humanized.message)
        assertSame(raw, humanized.cause)
    }

    @Test
    fun `trust failure buried in the cause chain is still detected`() {
        // OkHttp typically wraps CertPathValidatorException inside SSLHandshakeException,
        // and repositories may wrap that again.
        val raw = IOException(
            SSLHandshakeException("handshake failed").apply {
                initCause(CertPathValidatorException("Trust anchor not found"))
            }
        )
        val humanized = ConnectionErrors.humanize(raw)
        assertTrue(humanized.message!!.contains("CA certificate"), humanized.message)
    }

    @Test
    fun `bare certificate exception is detected`() {
        val humanized = ConnectionErrors.humanize(CertificateException("bad cert"))
        assertTrue(humanized.message!!.contains("CA certificate"), humanized.message)
    }

    @Test
    fun `hostname mismatch gets a SAN hint instead of the CA message`() {
        val raw = SSLPeerUnverifiedException("Hostname 192.168.1.10 not verified")
        val humanized = ConnectionErrors.humanize(raw)
        assertTrue(humanized.message!!.contains("Subject Alternative Names"), humanized.message)
    }

    @Test
    fun `unrelated errors pass through unchanged`() {
        val raw = IOException("Failed to connect to /192.168.1.10:8096")
        assertSame(raw, ConnectionErrors.humanize(raw))
    }
}
