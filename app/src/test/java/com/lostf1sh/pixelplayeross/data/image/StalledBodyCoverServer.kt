package com.lostf1sh.pixelplayeross.data.image

import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A loopback HTTP server that answers a cover request with its headers and the first
 * [sentBytes] of [image], then holds back the rest of the body until the client hangs up.
 *
 * Unlike [GatedCoverServer], the body arrives over a real socket, so a client that stops
 * downloading has to abort a read that is blocked waiting for more of the body.
 */
internal class StalledBodyCoverServer(private val image: ByteArray, private val sentBytes: Int) : Closeable {

    /** Counted down once the client has read the first bytes of the body. */
    val bodyBytesRead = CountDownLatch(1)

    /** Counted down when the client closes the connection before the body is complete. */
    val clientHungUp = CountDownLatch(1)

    /** Counted down once the client has no call running, so every callback has returned. */
    val callsFinished = CountDownLatch(1)

    private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    @Volatile
    private var connection: Socket? = null

    val request: Request = Request.Builder()
        .url(
            HttpUrl.Builder()
                .scheme("http")
                .host(serverSocket.inetAddress.hostAddress!!)
                .port(serverSocket.localPort)
                .addPathSegments("rest/getCoverArt.view")
                .addQueryParameter("id", "al-1")
                .build()
        )
        .build()

    val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .dispatcher(Dispatcher().apply { idleCallback = Runnable { callsFinished.countDown() } })
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            val signallingSource = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { if (it > 0) bodyBytesRead.countDown() }
            }
            response.newBuilder()
                .body(signallingSource.buffer().asResponseBody(body.contentType(), body.contentLength()))
                .build()
        }
        .build()

    init {
        thread(isDaemon = true, name = "StalledBodyCoverServer") {
            runCatching { serverSocket.accept().use(::serve) }
        }
    }

    private fun serve(socket: Socket) {
        connection = socket
        socket.soTimeout = TimeUnit.SECONDS.toMillis(10).toInt()
        val input = socket.getInputStream()
        skipRequestHead(input)

        val output = socket.getOutputStream()
        val head = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${image.size}\r\n\r\n"
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(image, 0, sentBytes)
        output.flush()

        // The request has no body, so the only thing left for the client to do on this
        // connection is close it: EOF or a reset means it hung up, a timeout means it never did.
        val hungUp = try {
            input.read() == -1
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: IOException) {
            true
        }
        if (hungUp) clientHungUp.countDown()
    }

    private fun skipRequestHead(input: InputStream) {
        val terminator = "\r\n\r\n"
        var matched = 0
        while (matched < terminator.length) {
            val next = input.read()
            if (next == -1) throw EOFException("Request ended before its headers")
            matched = when (next) {
                terminator[matched].code -> matched + 1
                '\r'.code -> 1
                else -> 0
            }
        }
    }

    override fun close() {
        serverSocket.close()
        connection?.close()
        client.dispatcher.executorService.shutdown()
    }
}
