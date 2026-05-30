package com.lostf1sh.pixelplayeross.data.backup.model

data class BackupManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "",
    val appVersionCode: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val modules: Map<String, BackupModuleInfo> = emptyMap()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val MIN_SUPPORTED_VERSION = 1
        const val MANIFEST_FILENAME = "manifest.json"
    }
}

data class DeviceInfo(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: Int = 0
)

data class BackupModuleInfo(
    val checksum: String = "",
    val entryCount: Int = 0,
    val sizeBytes: Long = 0
)
