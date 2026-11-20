package com.lostf1sh.pixelplayeross.data.network.navidrome

import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromeAuthMethod
import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromeCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navidrome/Subsonic API client.
 *
 * Implements the Subsonic API protocol with token-based authentication, falling back
 * to password authentication for servers that reject tokens (see [NavidromeAuthMethod]).
 * Compatible with Navidrome, Gonic, Airsonic, Nextcloud Music, and other
 * Subsonic-compatible servers.
 *
 * API Reference: http://www.subsonic.org/pages/api.jsp
 */
@Singleton
class NavidromeApiService @Inject constructor(
    baseOkHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "NavidromeApi"
        private const val API_VERSION = "1.16.1"
        private const val DEFAULT_CLIENT_ID = "PixelPlayerOSS"
        private const val DEFAULT_FORMAT = "json"
    }

    @Volatile
    private var credentials: NavidromeCredentials? = null

    @Volatile
    private var authMethod: NavidromeAuthMethod = NavidromeAuthMethod.TOKEN

    /**
     * Notified when the client switches auth method after a server rejection, so the
     * choice can be persisted and reused on the next launch instead of re-discovered.
     */
    @Volatile
    var onAuthMethodChanged: ((NavidromeAuthMethod) -> Unit)? = null

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Set the server credentials for API calls.
     *
     * @param authMethod The auth method to start with. Pass the previously discovered
     *   method for a known server; [NavidromeAuthMethod.TOKEN] servers that reject
     *   tokens are detected on the first request.
     */
    fun setCredentials(
        credentials: NavidromeCredentials,
        authMethod: NavidromeAuthMethod = NavidromeAuthMethod.TOKEN
    ) {
        this.credentials = credentials
        this.authMethod = authMethod
        Timber.d("$TAG: Credentials set for server: ${credentials.normalizedServerUrl}, user: ${credentials.username}, auth: $authMethod")
    }

    /**
     * Clear the stored credentials.
     */
    fun clearCredentials() {
        this.credentials = null
        this.authMethod = NavidromeAuthMethod.TOKEN
        Timber.d("$TAG: Credentials cleared")
    }

    /**
     * The auth method currently in use, after any server-driven fallback.
     */
    val activeAuthMethod: NavidromeAuthMethod
        get() = authMethod

    /**
     * Check if credentials are configured.
     */
    fun hasCredentials(): Boolean = credentials?.isValid == true

    /**
     * Get the current server URL.
     */
    fun getServerUrl(): String? = credentials?.normalizedServerUrl

    /**
     * Build a URL with authentication parameters for a Subsonic API endpoint.
     */
    private fun buildApiUrl(
        endpoint: String,
        extraParams: Map<String, String> = emptyMap(),
        method: NavidromeAuthMethod = authMethod
    ): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")

        val baseUrl = "${cred.normalizedServerUrl}/rest/$endpoint.view"

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("u", cred.username)

        subsonicAuthParams(cred.password, method).forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        urlBuilder
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", cred.clientId.ifBlank { DEFAULT_CLIENT_ID })
            .addQueryParameter("f", DEFAULT_FORMAT)

        extraParams.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        return urlBuilder.build().toString()
    }

    /**
     * Make a GET request to a Subsonic API endpoint.
     *
     * @param endpoint The API endpoint name (without .view suffix)
     * @param params Additional query parameters
     * @return The raw JSON response as a string
     */
    private suspend fun request(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        method: NavidromeAuthMethod = authMethod
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildApiUrl(endpoint, params, method)
                Timber.d("$TAG: >>> GET $endpoint")

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "PixelPlayerOSS/${API_VERSION}")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body.string()

                    if (!response.isSuccessful) {
                        Timber.w("$TAG: <<< HTTP $code for $endpoint")
                        return@withContext Result.failure(Exception("HTTP $code: ${response.message}"))
                    }

                    Timber.d("$TAG: <<< HTTP $code for $endpoint, body length: ${body.length}")
                    Result.success(body)
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: !!! FAILED GET $endpoint")
                Result.failure(e)
            }
        }
    }

    /**
     * Parse and validate a Subsonic API JSON response.
     * Subsonic responses are wrapped in a "subsonic-response" object.
     *
     * @return The "subsonic-response" JSON object, or an error
     */
    private fun parseResponse(raw: String): Result<JSONObject> {
        return try {
            val root = JSONObject(raw)
            val subsonicResponse = root.optJSONObject("subsonic-response")
                ?: return Result.failure(Exception("Invalid response: missing subsonic-response"))

            val status = subsonicResponse.optString("status", "failed")
            if (status != "ok") {
                val error = subsonicResponse.optJSONObject("error")
                val code = error?.optInt("code", -1) ?: -1
                val message = error?.optString("message", "Unknown error") ?: "Unknown error"
                return Result.failure(SubsonicApiException(code, message))
            }

            Result.success(subsonicResponse)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to parse response")
            Result.failure(e)
        }
    }

    /**
     * Make a request and parse the response.
     *
     * Servers that store only password hashes (Nextcloud/ownCloud Music) reject the
     * token/salt parameters instead of checking them, so a single rejection switches
     * this client to password auth for the rest of the session and retries once.
     */
    private suspend fun requestAndParse(endpoint: String, params: Map<String, String> = emptyMap()): Result<JSONObject> {
        val method = authMethod
        val result = requestParsed(endpoint, params, method)

        val error = result.exceptionOrNull()
        if (error is SubsonicApiException && shouldFallBackToPasswordAuth(error, method)) {
            Timber.w("$TAG: Server rejected token auth (${error.message}), retrying with password auth")
            authMethod = NavidromeAuthMethod.PASSWORD
            onAuthMethodChanged?.invoke(NavidromeAuthMethod.PASSWORD)
            return requestParsed(endpoint, params, NavidromeAuthMethod.PASSWORD)
        }

        return result
    }

    private suspend fun requestParsed(
        endpoint: String,
        params: Map<String, String>,
        method: NavidromeAuthMethod
    ): Result<JSONObject> {
        return request(endpoint, params, method).fold(
            onSuccess = { parseResponse(it) },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Ping the server to test connectivity and authentication.
     */
    suspend fun ping(): Result<Boolean> {
        return requestAndParse("ping").map { true }
    }

    /**
     * Get server license information.
     */
    suspend fun getLicense(): Result<JSONObject> {
        return requestAndParse("getLicense")
    }

    /**
     * Get all music folders (libraries) configured on the server.
     */
    suspend fun getMusicFolders(): Result<List<JSONObject>> {
        return requestAndParse("getMusicFolders").map { response ->
            val folders = response.optJSONObject("musicFolders")?.optJSONArray("musicFolder")
            (0 until (folders?.length() ?: 0)).mapNotNull { folders?.optJSONObject(it) }
        }
    }

    /**
     * Get all artists.
     * Uses getArtists (ID3 tags) for better metadata.
     */
    suspend fun getArtists(musicFolderId: String? = null): Result<List<JSONObject>> {
        val params = musicFolderId?.let { mapOf("musicFolderId" to it) } ?: emptyMap()
        return requestAndParse("getArtists", params).map { response ->
            val artists = response.optJSONObject("artists")?.optJSONArray("index")
            val result = mutableListOf<JSONObject>()
            (0 until (artists?.length() ?: 0)).forEach { i ->
                val index = artists?.optJSONObject(i)
                val artistArray = index?.optJSONArray("artist")
                (0 until (artistArray?.length() ?: 0)).forEach { j ->
                    artistArray?.optJSONObject(j)?.let { result.add(it) }
                }
            }
            result
        }
    }

    /**
     * Get albums by an artist.
     */
    suspend fun getArtist(id: String): Result<JSONObject> {
        return requestAndParse("getArtist", mapOf("id" to id))
    }

    /**
     * Get albums list.
     * Uses getAlbumList2 (ID3 tags) for better metadata.
     *
     * @param type One of: random, newest, highest, frequent, recent, alphabeticalByName,
     *             alphabeticalByArtist, starred, byGenre, byYear
     * @param size Number of albums to return (default 10, max 500)
     * @param offset Offset for pagination
     * @param musicFolderId Filter by music folder
     */
    suspend fun getAlbumList(
        type: String = "newest",
        size: Int = 50,
        offset: Int = 0,
        musicFolderId: String? = null
    ): Result<List<JSONObject>> {
        val params = mutableMapOf(
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString()
        )
        musicFolderId?.let { params["musicFolderId"] = it }

        return requestAndParse("getAlbumList2", params).map { response ->
            val albums = response.optJSONObject("albumList2")?.optJSONArray("album")
            (0 until (albums?.length() ?: 0)).mapNotNull { albums?.optJSONObject(it) }
        }
    }

    /**
     * Get songs in an album.
     */
    suspend fun getAlbum(id: String): Result<List<JSONObject>> {
        return requestAndParse("getAlbum", mapOf("id" to id)).map { response ->
            val songs = response.optJSONObject("album")?.optJSONArray("song")
            (0 until (songs?.length() ?: 0)).mapNotNull { songs?.optJSONObject(it) }
        }
    }

    /**
     * Get song details.
     */
    suspend fun getSong(id: String): Result<JSONObject> {
        return requestAndParse("getSong", mapOf("id" to id))
    }

    /**
     * Get all playlists.
     */
    suspend fun getPlaylists(): Result<List<JSONObject>> {
        return requestAndParse("getPlaylists").map { response ->
            val playlistsContainer = response.optJSONObject("playlists")
            val playlists = playlistsContainer?.optJSONArray("playlist")
            
            if (playlists == null) {
                val topLevelPlaylists = response.optJSONArray("playlist")
                if (topLevelPlaylists != null) {
                    return@map (0 until topLevelPlaylists.length()).mapNotNull { topLevelPlaylists.optJSONObject(it) }
                }
                return@map emptyList<JSONObject>()
            }
            
            (0 until (playlists.length())).mapNotNull { playlists.optJSONObject(it) }
        }
    }

    /**
     * Get playlist details with songs.
     */
    suspend fun getPlaylist(id: String): Result<Pair<JSONObject, List<JSONObject>>> {
        return requestAndParse("getPlaylist", mapOf("id" to id)).map { response ->
            val playlist = response.optJSONObject("playlist") ?: JSONObject()
            val songs = playlist.optJSONArray("entry")
            
            if (songs == null) {
                val altSongs = playlist.optJSONArray("song")
                if (altSongs != null) {
                    val songList = (0 until altSongs.length()).mapNotNull { altSongs.optJSONObject(it) }
                    return@map Pair(playlist, songList)
                }
                return@map Pair(playlist, emptyList<JSONObject>())
            }
            
            val songList = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }
            Pair(playlist, songList)
        }
    }

    /**
     * Search for songs, albums, and artists.
     * Uses search3 (ID3 tags) for better metadata.
     *
     * @param query Search query
     * @param artistCount Max artists to return
     * @param albumCount Max albums to return
     * @param songCount Max songs to return
     */
    suspend fun search3(
        query: String,
        artistCount: Int = 10,
        albumCount: Int = 20,
        songCount: Int = 30
    ): Result<JSONObject> {
        val params = mapOf(
            "query" to query,
            "artistCount" to artistCount.toString(),
            "albumCount" to albumCount.toString(),
            "songCount" to songCount.toString()
        )
        return requestAndParse("search3", params)
    }

    /**
     * Search for songs only.
     */
    suspend fun searchSongs(query: String, count: Int = 30): Result<List<JSONObject>> {
        return search3(query, artistCount = 0, albumCount = 0, songCount = count).map { response ->
            val searchResult = response.optJSONObject("searchResult3")
            val songs = searchResult?.optJSONArray("song")
            (0 until (songs?.length() ?: 0)).mapNotNull { songs?.optJSONObject(it) }
        }
    }

    /**
     * Search for albums only.
     */
    suspend fun searchAlbums(query: String, count: Int = 20): Result<List<JSONObject>> {
        return search3(query, artistCount = 0, albumCount = count, songCount = 0).map { response ->
            val searchResult = response.optJSONObject("searchResult3")
            val albums = searchResult?.optJSONArray("album")
            (0 until (albums?.length() ?: 0)).mapNotNull { albums?.optJSONObject(it) }
        }
    }

    /**
     * Search for artists only.
     */
    suspend fun searchArtists(query: String, count: Int = 10): Result<List<JSONObject>> {
        return search3(query, artistCount = count, albumCount = 0, songCount = 0).map { response ->
            val searchResult = response.optJSONObject("searchResult3")
            val artists = searchResult?.optJSONArray("artist")
            (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it) }
        }
    }

    /**
     * Build a streaming URL for a song.
     * This returns a URL that can be passed directly to ExoPlayer.
     *
     * @param songId The song ID
     * @param maxBitRate Maximum bitrate in kbps (0 = no limit)
     * @param format Target format (e.g., "mp3", "raw")
     */
    fun getStreamUrl(songId: String, maxBitRate: Int = 0, format: String? = null): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")

        val urlBuilder = "${cred.normalizedServerUrl}/rest/stream.view".toHttpUrl().newBuilder()
            .addQueryParameter("u", cred.username)

        subsonicAuthParams(cred.password, authMethod).forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        urlBuilder
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", cred.clientId.ifBlank { DEFAULT_CLIENT_ID })
            .addQueryParameter("id", songId)

        if (maxBitRate > 0) {
            urlBuilder.addQueryParameter("maxBitRate", maxBitRate.toString())
        }
        if (format != null) {
            urlBuilder.addQueryParameter("format", format)
        }

        return urlBuilder.build().toString()
    }

    /**
     * Build a cover art URL.
     *
     * @param coverArtId The cover art ID (usually same as song/album ID)
     * @param size Desired size in pixels, or 0 to leave the artwork at its original size
     */
    fun getCoverArtUrl(coverArtId: String, size: Int = 500): String {
        val cred = credentials ?: throw IllegalStateException("No credentials configured")

        val urlBuilder = "${cred.normalizedServerUrl}/rest/getCoverArt.view"
            .toHttpUrl().newBuilder()
            .addQueryParameter("u", cred.username)

        subsonicAuthParams(cred.password, authMethod).forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        return urlBuilder
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", cred.clientId.ifBlank { DEFAULT_CLIENT_ID })
            .addQueryParameter("id", coverArtId)
            .apply {
                if (size > 0) addQueryParameter("size", size.toString())
            }
            .build()
            .toString()
    }

    /**
     * Get lyrics for a song.
     * Note: Not all servers support this endpoint.
     */
    suspend fun getLyrics(artist: String? = null, title: String? = null): Result<String> {
        val params = mutableMapOf<String, String>()
        artist?.let { params["artist"] = it }
        title?.let { params["title"] = it }

        return requestAndParse("getLyrics", params).map { response ->
            response.optJSONObject("lyrics")?.optString("value", "") ?: ""
        }
    }

    /**
     * Get lyrics by song ID (OpenSubsonic extension, supported by Navidrome).
     */
    suspend fun getLyricsBySongId(songId: String): Result<String> {
        return requestAndParse("getLyricsBySongId", mapOf("id" to songId)).map { response ->
            val lyrics = response.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
                ?.optJSONObject(0)?.optJSONArray("line")

            if (lyrics != null && lyrics.length() > 0) {
                (0 until lyrics.length()).mapNotNull { lyrics.optJSONObject(it)?.optString("value") }
                    .joinToString("\n")
            } else {
                ""
            }
        }
    }

    /**
     * Reports playback timeline state for a song.
     * OpenSubsonic extension: playbackReport
     */
    suspend fun reportPlayback(
        mediaId: String,
        mediaType: String = "song",
        positionMs: Long,
        state: String,
        playbackRate: Float = 1.0f,
        ignoreScrobble: Boolean = false
    ): Result<Unit> {
        val params = mutableMapOf<String, String>()
        params["mediaId"] = mediaId
        params["mediaType"] = mediaType
        params["positionMs"] = positionMs.toString()
        params["state"] = state
        params["playbackRate"] = playbackRate.toString()
        params["ignoreScrobble"] = ignoreScrobble.toString()
        return requestAndParse("reportPlayback", params).map { Unit }
    }

    /**
     * Standard Subsonic scrobble API.
     * Used as fallback for now playing and marking as played.
     */
    suspend fun scrobble(id: String, submission: Boolean = true): Result<Unit> {
        val params = mutableMapOf<String, String>()
        params["id"] = id
        params["submission"] = submission.toString()
        
        Timber.d("$TAG: Calling scrobble API: id=$id, submission=$submission")
        return requestAndParse("scrobble", params).fold(
            onSuccess = {
                Timber.d("$TAG: scrobble API success")
                Result.success(Unit)
            },
            onFailure = {
                Timber.e(it, "$TAG: scrobble API failed")
                Result.failure(it)
            }
        )
    }

    /**
     * Star a song, album, or artist.
     */
    suspend fun star(id: String? = null, albumId: String? = null, artistId: String? = null): Result<Boolean> {
        val params = mutableMapOf<String, String>()
        id?.let { params["id"] = it }
        albumId?.let { params["albumId"] = it }
        artistId?.let { params["artistId"] = it }

        return requestAndParse("star", params).map { true }
    }

    /**
     * Unstar a song, album, or artist.
     */
    suspend fun unstar(id: String? = null, albumId: String? = null, artistId: String? = null): Result<Boolean> {
        val params = mutableMapOf<String, String>()
        id?.let { params["id"] = it }
        albumId?.let { params["albumId"] = it }
        artistId?.let { params["artistId"] = it }

        return requestAndParse("unstar", params).map { true }
    }
}

/**
 * A Subsonic API call that reached the server but came back with `status="failed"`.
 *
 * Error codes are defined by the Subsonic API (10 = missing parameter, 40 = wrong
 * credentials, 41 = token auth unsupported, 70 = not found, ...).
 */
class SubsonicApiException(
    val code: Int,
    val serverMessage: String
) : Exception("API Error $code: $serverMessage")

/** Subsonic error 41: the server cannot verify salted tokens and wants `p` instead. */
private const val SUBSONIC_ERROR_TOKEN_AUTH_UNSUPPORTED = 41

/**
 * Whether a failed call should be retried with password auth.
 *
 * Only error 41 qualifies: wrong credentials (40) would fail the same way again, and
 * a server that already answered a token request has nothing to gain from the switch.
 */
internal fun shouldFallBackToPasswordAuth(
    error: SubsonicApiException,
    currentMethod: NavidromeAuthMethod
): Boolean = currentMethod == NavidromeAuthMethod.TOKEN &&
    error.code == SUBSONIC_ERROR_TOKEN_AUTH_UNSUPPORTED

/**
 * Build the credential query parameters for [method].
 *
 * TOKEN sends `t=md5(password + salt)` with the salt; PASSWORD sends the hex-encoded
 * `p=enc:...` form, which every Subsonic server accepts and is the only form servers
 * like Nextcloud Music can verify.
 */
internal fun subsonicAuthParams(
    password: String,
    method: NavidromeAuthMethod,
    salt: String = randomSubsonicSalt()
): Map<String, String> = when (method) {
    NavidromeAuthMethod.TOKEN -> mapOf(
        "t" to md5Hex(password + salt),
        "s" to salt
    )
    NavidromeAuthMethod.PASSWORD -> mapOf(
        "p" to "enc:" + password.toByteArray(Charsets.UTF_8).toHexString()
    )
}

private fun randomSubsonicSalt(): String = UUID.randomUUID().toString().take(6)

private fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).toHexString()

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
