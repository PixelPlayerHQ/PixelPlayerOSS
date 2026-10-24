package com.lostf1sh.pixelplayeross.data.image

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A slow cover server: the first request stays in flight until [releaseFirstResponse] is called
 * or its call is cancelled. Later requests are answered right away.
 */
internal class GatedCoverServer(private val image: ByteArray, private val contentType: String = "image/png") {
    val firstRequestStarted = CountDownLatch(1)
    val firstCallCancelled = CountDownLatch(1)
    val requestCount = AtomicInteger()
    private val release = CountDownLatch(1)

    fun releaseFirstResponse() = release.countDown()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (requestCount.incrementAndGet() == 1) {
                firstRequestStarted.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!chain.call().isCanceled() && release.count > 0 && System.nanoTime() < deadline) {
                    Thread.sleep(10)
                }
                if (chain.call().isCanceled()) {
                    firstCallCancelled.countDown()
                    throw IOException("Canceled")
                }
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", contentType)
                .body(image.toResponseBody(contentType.toMediaType()))
                .build()
        }
        .build()
}
