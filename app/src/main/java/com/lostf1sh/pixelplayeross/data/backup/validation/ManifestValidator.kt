package com.lostf1sh.pixelplayeross.data.backup.validation

import com.lostf1sh.pixelplayeross.data.backup.model.BackupManifest
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.backup.model.BackupValidationResult
import com.lostf1sh.pixelplayeross.data.backup.model.Severity
import com.lostf1sh.pixelplayeross.data.backup.model.ValidationError
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManifestValidator @Inject constructor() {

    fun validate(manifest: BackupManifest): BackupValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Schema version check
        if (manifest.schemaVersion < BackupManifest.MIN_SUPPORTED_VERSION) {
            errors.add(ValidationError("SCHEMA_TOO_OLD", "Backup schema version ${manifest.schemaVersion} is not supported."))
        }
        if (manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION) {
            errors.add(ValidationError(
                "SCHEMA_TOO_NEW",
                "Backup was created with a newer app version (schema v${manifest.schemaVersion}). Some data may not be restored.",
                severity = Severity.WARNING
            ))
        }

        // Timestamp check
        val now = System.currentTimeMillis()
        if (manifest.createdAt > now + 86_400_000) { // 1 day tolerance
            errors.add(ValidationError("TIMESTAMP_FUTURE", "Backup has a timestamp in the future.", severity = Severity.WARNING))
        }
        if (manifest.createdAt < 1_700_000_000_000) { // Before ~Nov 2023
            errors.add(ValidationError("TIMESTAMP_OLD", "Backup has an unusually old timestamp.", severity = Severity.WARNING))
        }

        // Module keys check
        val knownKeys = BackupSection.entries.map { it.key }.toSet()
        manifest.modules.keys.forEach { key ->
            if (key !in knownKeys) {
                errors.add(ValidationError(
                    "UNKNOWN_MODULE",
                    "Unknown module '$key' in backup. It will be skipped.",
                    module = key,
                    severity = Severity.WARNING
                ))
            }
        }

        return if (errors.any { it.severity == Severity.ERROR }) {
            BackupValidationResult.Invalid(errors)
        } else if (errors.isNotEmpty()) {
            BackupValidationResult.Invalid(errors)
        } else {
            BackupValidationResult.Valid
        }
    }

    /**
     * Verifies the checksum of a module payload against the manifest.
     *
     * Strict by design: both [com.lostf1sh.pixelplayeross.data.backup.format.BackupWriter]
     * and [com.lostf1sh.pixelplayeross.data.backup.format.LegacyPayloadAdapter] always emit
     * `sha256:` checksums, so a missing module entry, a blank checksum, or an unknown
     * algorithm prefix can only come from a corrupted or tampered archive — treat it as a
     * verification failure rather than silently skipping the check.
     */
    fun verifyChecksum(moduleKey: String, payload: String, manifest: BackupManifest): Boolean {
        val expectedChecksum = manifest.modules[moduleKey]?.checksum ?: return false
        if (!expectedChecksum.startsWith("sha256:")) return false

        val expectedHash = expectedChecksum.removePrefix("sha256:")
        val actualHash = sha256(payload.toByteArray(Charsets.UTF_8))
        return expectedHash.equals(actualHash, ignoreCase = true)
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
