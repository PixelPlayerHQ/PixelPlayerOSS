package com.lostf1sh.pixelplayeross.data.update

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

class AppReleaseSourceTest {

    private val packageName = "com.lostf1sh.pixelplayeross"

    @Test
    fun `fdroid release uses the suggested version rather than the newest listed`() {
        val json = JSONObject(
            """
            {"packageName":"$packageName","suggestedVersionCode":3,
             "packages":[{"versionName":"0.4.0","versionCode":4},{"versionName":"0.3.0","versionCode":3}]}
            """
        )

        val release = AppReleaseSource.parseFdroidPackage(json, packageName)!!

        assertThat(release.versionName).isEqualTo("0.3.0")
        assertThat(release.versionCode).isEqualTo(3L)
        assertThat(release.apkUrl).isEqualTo("https://f-droid.org/repo/${packageName}_3.apk")
    }

    @Test
    fun `newest alpha is picked by version even when the API lists it later`() {
        val releases = JSONArray(
            listOf(
                release("v0.3.0-alpha.9"),
                release("v0.3.0-alpha.16"),
                release("v0.3.0-alpha.15"),
            ).joinToString(prefix = "[", postfix = "]")
        )

        val latest = AppReleaseSource.parseLatestAlpha(releases, listOf("arm64-v8a"))!!

        assertThat(latest.versionName).isEqualTo("0.3.0-alpha.16")
        assertThat(latest.apkUrl).endsWith("/PixelPlayerOSS-0.3.0-alpha.16-arm64-v8a.apk")
    }

    @Test
    fun `alpha asset follows the device ABI preference order`() {
        val releases = JSONArray("[${release("v0.3.0-alpha.16")}]")

        val latest = AppReleaseSource.parseLatestAlpha(releases, listOf("armeabi-v7a", "arm64-v8a"))!!

        assertThat(latest.apkUrl).endsWith("-armeabi-v7a.apk")
    }

    @Test
    fun `drafts, stable tags and releases without a matching APK are not offered`() {
        val releases = JSONArray(
            listOf(
                release("v0.3.0-alpha.20", draft = true),
                release("v0.4.0", prerelease = false),
                release("v0.3.0-alpha.19", abis = listOf("x86_64")),
                release("v0.3.0-alpha.18"),
            ).joinToString(prefix = "[", postfix = "]")
        )

        assertThat(AppReleaseSource.parseLatestAlpha(releases, listOf("arm64-v8a"))!!.versionName)
            .isEqualTo("0.3.0-alpha.18")
        assertThat(AppReleaseSource.parseLatestAlpha(releases, listOf("x86"))).isNull()
    }

    @Test
    fun `alpha versions order by base version before build number`() {
        val olderBaseHigherBuild = alphaRelease("0.3.0-alpha.99")
        val newerBase = alphaRelease("0.4.0-alpha.1")

        assertThat(newerBase.isNewerThan("0.3.0-alpha.99", installedVersionCode = 300099)).isTrue()
        assertThat(olderBaseHigherBuild.isNewerThan("0.4.0-alpha.1", installedVersionCode = 400001)).isFalse()
        assertThat(alphaRelease("0.3.0-alpha.16").isNewerThan("0.3.0-alpha.16", 300016)).isFalse()
    }

    @Test
    fun `PR builds on the alpha key are offered any published alpha`() {
        assertThat(alphaRelease("0.3.0-alpha.16").isNewerThan("0.3.0-pr.137.ba94188", 3)).isTrue()
    }

    @Test
    fun `installed channel follows the version name published by each pipeline`() {
        assertThat(UpdateChannel.ofVersionName("0.3.0")).isEqualTo(UpdateChannel.STABLE)
        assertThat(UpdateChannel.ofVersionName("0.3.0-alpha.16")).isEqualTo(UpdateChannel.ALPHA)
        assertThat(UpdateChannel.ofVersionName("0.3.0-pr.137.ba94188")).isEqualTo(UpdateChannel.ALPHA)
    }

    private fun alphaRelease(versionName: String) = AppRelease(
        channel = UpdateChannel.ALPHA,
        versionName = versionName,
        versionCode = null,
        apkUrl = "https://example.invalid/app.apk",
        apkSizeBytes = null,
        pageUrl = "https://example.invalid",
    )

    private fun release(
        tag: String,
        prerelease: Boolean = true,
        draft: Boolean = false,
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    ): String {
        val name = tag.removePrefix("v")
        val assets = abis.joinToString(prefix = "[", postfix = "]") { abi ->
            """{"name":"PixelPlayerOSS-$name-$abi.apk","size":37082259,
               "browser_download_url":"https://github.com/PixelPlayerHQ/PixelPlayerOSS/releases/download/$tag/PixelPlayerOSS-$name-$abi.apk"}"""
        }
        return """{"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,
                   "html_url":"https://github.com/PixelPlayerHQ/PixelPlayerOSS/releases/tag/$tag","assets":$assets}"""
    }
}
