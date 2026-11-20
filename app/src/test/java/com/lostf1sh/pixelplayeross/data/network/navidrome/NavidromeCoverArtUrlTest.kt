package com.lostf1sh.pixelplayeross.data.network.navidrome

import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromeCredentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class NavidromeCoverArtUrlTest {
    @Test
    fun `original artwork omits size and resized artwork sends the requested size`() {
        val service = NavidromeApiService(OkHttpClient()).apply {
            setCredentials(NavidromeCredentials("https://navidrome.example", "sample", "sample"))
        }

        val original = service.getCoverArtUrl("al-12", 0).toHttpUrl()
        assertThat(original.queryParameter("size")).isNull()
        assertThat(original.queryParameter("id")).isEqualTo("al-12")
        assertThat(service.getCoverArtUrl("al-12", 800).toHttpUrl().queryParameter("size")).isEqualTo("800")
        assertThat(service.getCoverArtUrl("al-12").toHttpUrl().queryParameter("size")).isEqualTo("500")
    }
}
