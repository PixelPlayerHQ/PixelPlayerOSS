package com.lostf1sh.pixelplayeross.data.network

import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Maps low-level connection failures to messages a user can act on.
 *
 * Self-hosted Navidrome/Jellyfin servers commonly run behind self-signed
 * certificates. The app trusts user-installed CAs (see
 * res/xml/network_security_config.xml), but until the user imports their CA
 * the TLS handshake fails with a raw Java exception message ("Trust anchor
 * for certification path not found") that gives no hint about the fix.
 */
object ConnectionErrors {

    private const val UNTRUSTED_CERT_MESSAGE =
        "The server's certificate is not trusted by this device. " +
            "If your server uses a self-signed certificate, install its CA certificate " +
            "in Android Settings (Security > Encryption & credentials > Install a certificate " +
            "> CA certificate). PixelPlayer trusts user-installed CA certificates."

    private const val HOSTNAME_MISMATCH_MESSAGE =
        "The server's certificate does not match its hostname. " +
            "Make sure the certificate includes the host or IP address you are connecting to " +
            "in its Subject Alternative Names."

    /**
     * Returns a throwable whose message explains a certificate trust failure in
     * actionable terms, or the original throwable unchanged for everything else.
     * The original exception is preserved as the cause for logging.
     */
    fun humanize(error: Throwable): Throwable {
        if (isHostnameMismatch(error)) return Exception(HOSTNAME_MISMATCH_MESSAGE, error)
        if (isCertificateTrustFailure(error)) return Exception(UNTRUSTED_CERT_MESSAGE, error)
        return error
    }

    private fun isHostnameMismatch(error: Throwable): Boolean =
        causeChain(error).any { it is SSLPeerUnverifiedException }

    private fun isCertificateTrustFailure(error: Throwable): Boolean =
        causeChain(error).any {
            it is SSLHandshakeException ||
                it is CertificateException ||
                it is CertPathValidatorException
        }

    private fun causeChain(error: Throwable): Sequence<Throwable> =
        generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
}
