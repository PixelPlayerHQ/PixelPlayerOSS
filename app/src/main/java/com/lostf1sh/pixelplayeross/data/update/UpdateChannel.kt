package com.lostf1sh.pixelplayeross.data.update

/**
 * Where builds of a channel are published. Stable builds are built and signed by F-Droid;
 * alpha builds are signed with the project CI key and published as GitHub prereleases, so an
 * install from one channel can never be updated in place by the other.
 */
enum class UpdateChannel(val storageKey: String) {
    STABLE("stable"),
    ALPHA("alpha");

    companion object {
        fun fromStorageKey(key: String?): UpdateChannel? = entries.firstOrNull { it.storageKey == key }

        /**
         * The channel the running build was distributed through. CI-built alphas (and PR builds,
         * which share the CI key) carry a pre-release suffix; F-Droid builds the plain tag.
         */
        fun ofVersionName(versionName: String): UpdateChannel =
            if ('-' in versionName) ALPHA else STABLE
    }
}

/** `0.3.0-alpha.16` as published by the alpha-release workflow. */
data class AlphaVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int,
) : Comparable<AlphaVersion> {

    override fun compareTo(other: AlphaVersion): Int = compareValuesBy(
        this, other, AlphaVersion::major, AlphaVersion::minor, AlphaVersion::patch, AlphaVersion::build
    )

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)-alpha\.(\d+)$""")

        fun parse(value: String): AlphaVersion? {
            val groups = PATTERN.matchEntire(value.trim())?.groupValues ?: return null
            return AlphaVersion(
                major = groups[1].toIntOrNull() ?: return null,
                minor = groups[2].toIntOrNull() ?: return null,
                patch = groups[3].toIntOrNull() ?: return null,
                build = groups[4].toIntOrNull() ?: return null,
            )
        }
    }
}

/** A published build that can be offered to the user. */
data class AppRelease(
    val channel: UpdateChannel,
    val versionName: String,
    /** Known for F-Droid builds; alpha releases only expose their version name. */
    val versionCode: Long?,
    val apkUrl: String,
    val apkSizeBytes: Long?,
    val pageUrl: String,
)

/** Whether [release] is newer than the running build, assuming both are on the same channel. */
internal fun AppRelease.isNewerThan(installedVersionName: String, installedVersionCode: Long): Boolean =
    when (channel) {
        UpdateChannel.STABLE -> (versionCode ?: 0L) > installedVersionCode
        UpdateChannel.ALPHA -> {
            val candidate = AlphaVersion.parse(versionName) ?: return false
            // PR and other CI builds share the alpha signing key but have no alpha number;
            // any published alpha is the way back onto the channel.
            val installed = AlphaVersion.parse(installedVersionName) ?: return true
            candidate > installed
        }
    }
