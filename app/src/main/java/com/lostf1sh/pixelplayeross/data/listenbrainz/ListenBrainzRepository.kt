package com.lostf1sh.pixelplayeross.data.listenbrainz

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzDao
import com.lostf1sh.pixelplayeross.data.database.ListenBrainzPendingListenEntity
import com.lostf1sh.pixelplayeross.data.worker.ScrobbleFlushWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the ListenBrainz account (user token in EncryptedSharedPreferences, mirroring the
 * Navidrome credential pattern) and all API submission paths.
 *
 * Scrobbling is opt-in: with no stored token, every path here is a no-op.
 */
@Singleton
class ListenBrainzRepository @Inject constructor(
    private val api: ListenBrainzApiService,
    private val listenBrainzDao: ListenBrainzDao,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ListenBrainzRepo"
        private const val PREFS_NAME = "listenbrainz_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AUTH_INVALID = "auth_invalid"

        private const val SUBMISSION_CLIENT = "PixelPlayerOSS"
        private const val MAX_QUEUE_SIZE = 3000
    }

    private val prefs: SharedPreferences = createCredentialPrefs()

    /** Serializes queue mutation against disconnect so consent revocation is a clean boundary. */
    private val queueMutex = Mutex()

    @Volatile
    private var cachedToken: String? = prefs.getString(KEY_TOKEN, null)

    private val _accountState = MutableStateFlow(
        ListenBrainzAccountState(
            isConnected = cachedToken != null,
            userName = prefs.getString(KEY_USER_NAME, null),
            needsReauth = prefs.getBoolean(KEY_AUTH_INVALID, false)
        )
    )
    val accountState: StateFlow<ListenBrainzAccountState> = _accountState.asStateFlow()

    val pendingListenCount = listenBrainzDao.countFlow()

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun createCredentialPrefs(): SharedPreferences = try {
        createEncryptedPrefs()
    } catch (e: Exception) {
        Timber.e(e, "$TAG: EncryptedSharedPreferences unreadable, deleting and recreating")
        context.deleteSharedPreferences(PREFS_NAME)
        try {
            createEncryptedPrefs()
        } catch (e2: Exception) {
            Timber.e(e2, "$TAG: Encrypted prefs unavailable, falling back to plain")
            context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    fun hasToken(): Boolean = cachedToken != null

    /**
     * True when a token is stored and not known to be invalid. Gate outbound requests on this;
     * [hasToken] alone only decides whether listens should still be collected into the queue.
     */
    fun isAuthorized(): Boolean = cachedToken != null && !_accountState.value.needsReauth

    /**
     * Inserts under the same lock [disconnect] takes, so a listen admitted before a disconnect
     * can never land in the queue after consent was revoked. Returns false when disconnected.
     */
    suspend fun enqueueListen(listen: ListenBrainzPendingListenEntity): Boolean {
        return queueMutex.withLock {
            if (cachedToken == null) return@withLock false
            listenBrainzDao.insert(listen)
            val overflow = listenBrainzDao.count() - MAX_QUEUE_SIZE
            if (overflow > 0) {
                listenBrainzDao.deleteOldest(overflow)
                Timber.w("ListenBrainz queue capped at %d, dropped %d oldest listens", MAX_QUEUE_SIZE, overflow)
            }
            true
        }
    }

    /**
     * Validates [token] against the API and stores it on success.
     * Returns the ListenBrainz user name on success.
     */
    suspend fun connect(token: String): Result<String> {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Empty token"))
        return try {
            val response = api.validateToken(authHeader(trimmed))
            val body = response.body()
            if (response.isSuccessful && body?.valid == true) {
                val userName = body.userName.orEmpty()
                prefs.edit {
                    putString(KEY_TOKEN, trimmed)
                    putString(KEY_USER_NAME, userName)
                    putBoolean(KEY_AUTH_INVALID, false)
                }
                cachedToken = trimmed
                _accountState.value = ListenBrainzAccountState(
                    isConnected = true,
                    userName = userName,
                    needsReauth = false
                )
                scheduleFlush()
                Result.success(userName)
            } else {
                Result.failure(IllegalStateException("Token rejected by ListenBrainz"))
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Token validation failed")
            Result.failure(e)
        }
    }

    /** Clears the account and drops any queued listens — disconnecting revokes consent. */
    suspend fun disconnect() {
        queueMutex.withLock {
            prefs.edit {
                remove(KEY_TOKEN)
                remove(KEY_USER_NAME)
                remove(KEY_AUTH_INVALID)
            }
            cachedToken = null
            _accountState.value = ListenBrainzAccountState()
            listenBrainzDao.clear()
        }
    }

    suspend fun submitListens(listens: List<ListenBrainzPendingListenEntity>): ListenBrainzSubmitResult {
        val token = cachedToken ?: return ListenBrainzSubmitResult.AuthFailed
        val submission = ListenBrainzSubmission(
            listenType = ListenBrainzSubmission.TYPE_IMPORT,
            payload = listens.map { it.toListen() }
        )
        return submit(token, submission)
    }

    suspend fun submitPlayingNow(
        trackName: String,
        artistName: String,
        releaseName: String?,
        durationMs: Long?,
        recordingMbid: String?
    ): ListenBrainzSubmitResult {
        val token = cachedToken ?: return ListenBrainzSubmitResult.AuthFailed
        val submission = ListenBrainzSubmission(
            listenType = ListenBrainzSubmission.TYPE_PLAYING_NOW,
            payload = listOf(
                ListenBrainzListen(
                    trackMetadata = trackMetadata(trackName, artistName, releaseName, durationMs, recordingMbid)
                )
            )
        )
        return submit(token, submission)
    }

    private suspend fun submit(token: String, submission: ListenBrainzSubmission): ListenBrainzSubmitResult {
        return try {
            val response = api.submitListens(authHeader(token), submission)
            when {
                response.isSuccessful -> ListenBrainzSubmitResult.Success
                response.code() == 401 -> {
                    markAuthInvalid()
                    ListenBrainzSubmitResult.AuthFailed
                }
                response.code() == 400 -> ListenBrainzSubmitResult.InvalidPayload
                response.code() == 429 -> ListenBrainzSubmitResult.TransientError(
                    retryAfterSeconds = response.retryAfterSeconds()
                )
                else -> ListenBrainzSubmitResult.TransientError()
            }
        } catch (e: Exception) {
            Timber.d(e, "$TAG: Submission failed, will retry")
            ListenBrainzSubmitResult.TransientError()
        }
    }

    private fun Response<*>.retryAfterSeconds(): Long? {
        return headers()["Retry-After"]?.toLongOrNull()
            ?: headers()["X-RateLimit-Reset-In"]?.toLongOrNull()
    }

    private fun markAuthInvalid() {
        prefs.edit { putBoolean(KEY_AUTH_INVALID, true) }
        _accountState.value = _accountState.value.copy(needsReauth = true)
    }

    fun scheduleFlush() {
        workManager.enqueueUniqueWork(
            ScrobbleFlushWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            ScrobbleFlushWorker.request()
        )
    }

    /** Chains the next flush attempt no earlier than a server-directed retry window. */
    fun scheduleFlushAfter(delaySeconds: Long) {
        workManager.enqueueUniqueWork(
            ScrobbleFlushWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            ScrobbleFlushWorker.request(initialDelaySeconds = delaySeconds)
        )
    }

    private fun authHeader(token: String) = "Token $token"

    private fun ListenBrainzPendingListenEntity.toListen(): ListenBrainzListen {
        return ListenBrainzListen(
            listenedAt = listenedAtMs / 1000,
            trackMetadata = trackMetadata(trackName, artistName, releaseName, durationMs, recordingMbid)
        )
    }

    private fun trackMetadata(
        trackName: String,
        artistName: String,
        releaseName: String?,
        durationMs: Long?,
        recordingMbid: String?
    ): ListenBrainzTrackMetadata {
        return ListenBrainzTrackMetadata(
            artistName = artistName,
            trackName = trackName,
            releaseName = releaseName,
            additionalInfo = ListenBrainzAdditionalInfo(
                mediaPlayer = SUBMISSION_CLIENT,
                submissionClient = SUBMISSION_CLIENT,
                durationMs = durationMs,
                recordingMbid = recordingMbid
            )
        )
    }
}
