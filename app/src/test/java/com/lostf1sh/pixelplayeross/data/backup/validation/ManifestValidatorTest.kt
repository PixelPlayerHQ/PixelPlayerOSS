package com.lostf1sh.pixelplayeross.data.backup.validation

import com.lostf1sh.pixelplayeross.data.backup.model.BackupManifest
import com.lostf1sh.pixelplayeross.data.backup.model.BackupModuleInfo
import com.lostf1sh.pixelplayeross.data.backup.model.BackupValidationResult
import com.lostf1sh.pixelplayeross.data.backup.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class ManifestValidatorTest {

    private val validator = ManifestValidator()

    @Test
    fun `valid manifest passes validation`() {
        val manifest = BackupManifest(
            schemaVersion = 3,
            appVersion = "1.0.0",
            appVersionCode = 100,
            createdAt = System.currentTimeMillis(),
            modules = mapOf(
                "playlists" to BackupModuleInfo(checksum = "sha256:abc", entryCount = 5, sizeBytes = 1024)
            )
        )
        val result = validator.validate(manifest)
        assertTrue(result.isValid())
    }

    @Test
    fun `schema version 0 fails with error`() {
        val manifest = BackupManifest(schemaVersion = 0, createdAt = System.currentTimeMillis())
        val result = validator.validate(manifest)
        assertTrue(result is BackupValidationResult.Invalid)
        val errors = (result as BackupValidationResult.Invalid).fatalErrors
        assertTrue(errors.any { it.code == "SCHEMA_TOO_OLD" })
    }

    @Test
    fun `future schema version emits warning`() {
        val manifest = BackupManifest(schemaVersion = 99, createdAt = System.currentTimeMillis())
        val result = validator.validate(manifest)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "SCHEMA_TOO_NEW" })
    }

    @Test
    fun `timestamp far in the future emits warning`() {
        val manifest = BackupManifest(
            schemaVersion = 3,
            createdAt = System.currentTimeMillis() + 86_400_000 * 2 // 2 days ahead
        )
        val result = validator.validate(manifest)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "TIMESTAMP_FUTURE" })
    }

    @Test
    fun `old timestamp emits warning`() {
        val manifest = BackupManifest(
            schemaVersion = 3,
            createdAt = 1_000_000_000_000 // ~2001
        )
        val result = validator.validate(manifest)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "TIMESTAMP_OLD" })
    }

    @Test
    fun `unknown module key emits warning`() {
        val manifest = BackupManifest(
            schemaVersion = 3,
            createdAt = System.currentTimeMillis(),
            modules = mapOf(
                "unknown_module_xyz" to BackupModuleInfo(checksum = "", entryCount = 0, sizeBytes = 0)
            )
        )
        val result = validator.validate(manifest)
        assertTrue(result is BackupValidationResult.Invalid)
        val warnings = (result as BackupValidationResult.Invalid).warnings
        assertTrue(warnings.any { it.code == "UNKNOWN_MODULE" })
    }

    @Test
    fun `verifyChecksum returns true for matching payload`() {
        val payload = """[{"songId": 123}]"""
        val hash = sha256(payload.toByteArray(Charsets.UTF_8))
        val manifest = BackupManifest(
            modules = mapOf(
                "favorites" to BackupModuleInfo(checksum = "sha256:$hash", entryCount = 1, sizeBytes = payload.length.toLong())
            )
        )
        assertTrue(validator.verifyChecksum("favorites", payload, manifest))
    }

    @Test
    fun `verifyChecksum returns false for mismatched payload`() {
        val manifest = BackupManifest(
            modules = mapOf(
                "favorites" to BackupModuleInfo(checksum = "sha256:0000000000000000000000000000000000000000000000000000000000000000", entryCount = 1, sizeBytes = 10)
            )
        )
        assertFalse(validator.verifyChecksum("favorites", """[{"songId": 999}]""", manifest))
    }

    @Test
    fun `verifyChecksum fails when no checksum in manifest`() {
        // BackupWriter and LegacyPayloadAdapter always emit sha256 checksums, so a missing
        // module entry can only come from a corrupted or tampered archive — it must fail
        // verification rather than silently bypass it.
        val manifest = BackupManifest(modules = emptyMap())
        assertFalse(validator.verifyChecksum("favorites", "any payload", manifest))
    }

    @Test
    fun `verifyChecksum fails for unknown checksum algorithm`() {
        val manifest = BackupManifest(
            modules = mapOf(
                "favorites" to BackupModuleInfo(checksum = "md5:abc123", entryCount = 1, sizeBytes = 10)
            )
        )
        assertFalse(validator.verifyChecksum("favorites", "any payload", manifest))
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
