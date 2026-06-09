package com.lostf1sh.pixelplayeross.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import com.lostf1sh.pixelplayeross.data.backup.format.BackupReader
import com.lostf1sh.pixelplayeross.data.backup.format.BackupWriter
import com.lostf1sh.pixelplayeross.data.backup.history.BackupHistoryRepository
import com.lostf1sh.pixelplayeross.data.backup.model.BackupHistoryEntry
import com.lostf1sh.pixelplayeross.data.backup.model.BackupManifest
import com.lostf1sh.pixelplayeross.data.backup.model.BackupOperationType
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.backup.model.BackupTransferProgressUpdate
import com.lostf1sh.pixelplayeross.data.backup.model.BackupValidationResult
import com.lostf1sh.pixelplayeross.data.backup.model.DeviceInfo
import com.lostf1sh.pixelplayeross.data.backup.model.RestorePlan
import com.lostf1sh.pixelplayeross.data.backup.model.RestoreResult
import com.lostf1sh.pixelplayeross.data.backup.module.BackupModuleHandler
import com.lostf1sh.pixelplayeross.data.backup.restore.RestoreExecutor
import com.lostf1sh.pixelplayeross.data.backup.restore.RestorePlanner
import com.lostf1sh.pixelplayeross.data.backup.validation.ValidationPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupWriter: BackupWriter,
    private val backupReader: BackupReader,
    private val restorePlanner: RestorePlanner,
    private val restoreExecutor: RestoreExecutor,
    private val validationPipeline: ValidationPipeline,
    private val backupHistoryRepository: BackupHistoryRepository,
    private val handlers: Map<BackupSection, @JvmSuppressWildcards BackupModuleHandler>
) {
    /**
     * Exports selected modules to a .pxpl file at the given URI.
     */
    suspend fun export(
        uri: Uri,
        sections: Set<BackupSection>,
        onProgress: (BackupTransferProgressUpdate) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val selectedSections = sections.toList()
            val totalSteps = selectedSections.size + 3
            var step = 0

            reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                "Preparing backup", "Building your selected backup sections.")

            // Collect module payloads
            val modulePayloads = mutableMapOf<String, String>()
            selectedSections.forEach { section ->
                reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                    "Collecting ${section.label}", section.description, section)
                val handler = handlers[section]
                    ?: throw IllegalStateException("No handler for module ${section.key}")
                modulePayloads[section.key] = handler.export()
            }

            // Build manifest
            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (_: Exception) { null }

            val manifest = BackupManifest(
                schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
                appVersion = packageInfo?.versionName ?: "unknown",
                appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.longVersionCode?.toInt() ?: 0
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo?.versionCode ?: 0
                },
                createdAt = System.currentTimeMillis(),
                deviceInfo = DeviceInfo(
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.SDK_INT
                )
            )

            reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                "Packaging backup", "Creating .pxpl archive.")

            backupWriter.write(uri, manifest, modulePayloads).getOrThrow()

            reportProgress(onProgress, BackupOperationType.EXPORT, ++step, totalSteps,
                "Backup complete", "Your PixelPlayerOSS backup was created successfully.")
        }
    }

    /**
     * Inspects a backup file and returns a RestorePlan (without actually restoring).
     */
    suspend fun inspectBackup(uri: Uri): Result<RestorePlan> = withContext(Dispatchers.IO) {
        runCatching {
            // Validate file first
            val fileValidation = validationPipeline.validateFile(uri)
            val warnings = mutableListOf<String>()
            if (fileValidation is BackupValidationResult.Invalid && fileValidation.fatalErrors.isNotEmpty()) {
                throw IllegalArgumentException(fileValidation.fatalErrors.first().message)
            }
            if (fileValidation is BackupValidationResult.Invalid) {
                warnings.addAll(fileValidation.warnings.map { it.message })
            }

            // Build restore plan
            val plan = restorePlanner.buildRestorePlan(uri).getOrThrow()

            // Validate manifest
            val manifestValidation = validationPipeline.validateManifest(plan.manifest)
            warnings.addAll(plan.warnings)
            if (manifestValidation is BackupValidationResult.Invalid) {
                if (manifestValidation.fatalErrors.isNotEmpty()) {
                    throw IllegalArgumentException(manifestValidation.fatalErrors.first().message)
                }
                warnings.addAll(manifestValidation.warnings.map { it.message })
            }

            plan.availableModules.toList().sortedBy { it.key }.forEach { section ->
                val moduleInfo = plan.manifest.modules[section.key]
                if (moduleInfo != null && moduleInfo.sizeBytes > BackupReader.MAX_MODULE_PAYLOAD_BYTES) {
                    warnings.add(
                        "${section.label}: payload is ${moduleInfo.sizeBytes / (1024 * 1024)}MB, " +
                            "so preview validation was skipped to avoid running out of memory."
                    )
                    return@forEach
                }

                val payload = backupReader.readModulePayload(uri, section.key).getOrThrow()

                val moduleValidation = validationPipeline.validateModulePayload(
                    section = section,
                    payload = payload,
                    manifest = plan.manifest
                )
                if (moduleValidation is BackupValidationResult.Invalid) {
                    if (moduleValidation.fatalErrors.isNotEmpty()) {
                        throw IllegalArgumentException(
                            "${section.label}: ${moduleValidation.fatalErrors.first().message}"
                        )
                    }
                    warnings.addAll(
                        moduleValidation.warnings.map { warning ->
                            "${section.label}: ${warning.message}"
                        }
                    )
                }
            }

            plan.copy(warnings = warnings)
        }
    }

    /**
     * Executes a restore according to the given plan.
     */
    suspend fun restore(
        uri: Uri,
        plan: RestorePlan,
        onProgress: (BackupTransferProgressUpdate) -> Unit
    ): RestoreResult = withContext(Dispatchers.IO) {
        // Re-run the file-level safety checks (zip-bomb, path traversal, size limits) here
        // rather than trusting that the caller went through inspectBackup() first — restore()
        // is public API and must not be a validation bypass.
        val fileValidation = validationPipeline.validateFile(uri)
        if (fileValidation is BackupValidationResult.Invalid && fileValidation.fatalErrors.isNotEmpty()) {
            return@withContext RestoreResult.TotalFailure(
                "Backup file failed validation: ${fileValidation.fatalErrors.first().message}"
            )
        }

        val result = restoreExecutor.execute(uri, plan, onProgress)

        // Add to backup history on successful inspection/restore
        if (result is RestoreResult.Success) {
            try {
                val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                backupHistoryRepository.addEntry(
                    BackupHistoryEntry(
                        uri = uri.toString(),
                        displayName = docFile?.name ?: "backup.pxpl",
                        createdAt = plan.manifest.createdAt,
                        schemaVersion = plan.manifest.schemaVersion,
                        modules = plan.manifest.modules.keys,
                        sizeBytes = docFile?.length() ?: 0,
                        appVersion = plan.manifest.appVersion
                    )
                )
            } catch (_: Exception) {
                // Non-critical; don't fail restore because of history persistence
            }
        }

        result
    }

    fun getBackupHistory(): Flow<List<BackupHistoryEntry>> {
        return backupHistoryRepository.historyFlow
    }

    suspend fun removeBackupHistoryEntry(uri: String) {
        backupHistoryRepository.removeEntry(uri)
    }

    private fun reportProgress(
        onProgress: (BackupTransferProgressUpdate) -> Unit,
        operation: BackupOperationType,
        step: Int,
        totalSteps: Int,
        title: String,
        detail: String,
        section: BackupSection? = null
    ) {
        onProgress(
            BackupTransferProgressUpdate(
                operation = operation,
                step = step,
                totalSteps = totalSteps,
                title = title,
                detail = detail,
                section = section
            )
        )
    }
}
