package com.lostf1sh.pixelplayeross.data.backup.validation

import android.net.Uri
import com.lostf1sh.pixelplayeross.data.backup.model.BackupManifest
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.backup.model.BackupValidationResult
import com.lostf1sh.pixelplayeross.data.backup.model.Severity
import com.lostf1sh.pixelplayeross.data.backup.model.ValidationError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationPipeline @Inject constructor(
    private val fileValidator: BackupFileValidator,
    private val manifestValidator: ManifestValidator,
    private val moduleSchemaValidator: ModuleSchemaValidator
) {
    /**
     * Full validation: file → manifest → checksums → per-module schemas.
     */
    fun validateFile(uri: Uri): BackupValidationResult {
        return fileValidator.validate(uri)
    }

    fun validateManifest(manifest: BackupManifest): BackupValidationResult {
        return manifestValidator.validate(manifest)
    }

    fun validateModulePayload(
        section: BackupSection,
        payload: String,
        manifest: BackupManifest? = null
    ): BackupValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Checksum verification (if manifest available)
        if (manifest != null && !manifestValidator.verifyChecksum(section.key, payload, manifest)) {
            errors.add(ValidationError(
                "CHECKSUM_MISMATCH",
                "Checksum mismatch for module '${section.label}'. The backup may be corrupted.",
                module = section.key
            ))
            return BackupValidationResult.Invalid(errors)
        }

        // Schema validation
        val schemaResult = moduleSchemaValidator.validate(section, payload)
        if (schemaResult is BackupValidationResult.Invalid) {
            errors.addAll(schemaResult.errors)
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
     * Collect all warnings from validation results (non-fatal).
     */
    fun collectWarnings(vararg results: BackupValidationResult): List<ValidationError> {
        return results.flatMap { result ->
            when (result) {
                is BackupValidationResult.Invalid -> result.warnings
                is BackupValidationResult.Valid -> emptyList()
            }
        }
    }
}
