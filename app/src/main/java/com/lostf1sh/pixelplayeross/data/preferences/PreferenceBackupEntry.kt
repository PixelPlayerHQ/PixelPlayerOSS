package com.lostf1sh.pixelplayeross.data.preferences

data class PreferenceBackupEntry(
    val key: String,
    val type: String,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val booleanValue: Boolean? = null,
    val floatValue: Float? = null,
    val doubleValue: Double? = null,
    val stringSetValue: Set<String>? = null
)

