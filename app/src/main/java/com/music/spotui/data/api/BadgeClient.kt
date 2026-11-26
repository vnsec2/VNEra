package com.music.spotui.data.api

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.myspoti.engine.BuildConfig
import app.myspoti.host.android.MyspotiRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object BadgeClient {
    private const val PREFS = "badges_prefs"
    private const val KEY_LAST_USER = "last_user_id"
    private const val KEY_RAW_PREFIX = "raw_"
    private const val PROFILE_CACHE_TTL_MS = 60_000L
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class UserBadge(val id: String, val label: String, val colorHex: String)

    data class BadgeState(
        val userId: String,
        val perks: Boolean,
        val badges: List<UserBadge>,
        val issuedAt: Long,
        val expiresAt: Long,
    )

    sealed interface RedeemResult {
        data class Ok(val state: BadgeState) : RedeemResult
        data object InvalidCode : RedeemResult
        data object Exhausted : RedeemResult
        data object Expired : RedeemResult
        data object NotSignedIn : RedeemResult
        data class Failed(val message: String) : RedeemResult
    }

    private data class CachedEntry(val state: BadgeState?, val fetchedAt: Long)

    var myState by mutableStateOf<BadgeState?>(null)
        private set

    private val profileCache = ConcurrentHashMap<String, CachedEntry>()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.orEmpty()
        val hw = Build.HARDWARE.orEmpty()
        val model = Build.MODEL.orEmpty()
        return fp.contains("generic", ignoreCase = true) ||
            fp.contains("emulator", ignoreCase = true) ||
            hw.contains("ranchu", ignoreCase = true) ||
            hw.contains("goldfish", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for", ignoreCase = true)
    }

    fun baseUrl(@Suppress("UNUSED_PARAMETER") context: Context?): String {
        val trimmed = BuildConfig.BADGE_SERVER_URL.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (isEmulator() && trimmed.startsWith("http://127.0.0.1")) {
            "http://10.0.2.2" + trimmed.removePrefix("http://127.0.0.1")
        } else {
            trimmed
        }
    }

    fun canonicalPayload(
        userId: String,
        perks: Boolean,
        issuedAt: Long,
        expiresAt: Long,
        badges: List<UserBadge>,
    ): String {
        val lines = ArrayList<String>(5 + badges.size)
        lines.add("v1")
        lines.add(userId)
        lines.add(if (perks) "1" else "0")
        lines.add(issuedAt.toString())
        lines.add(expiresAt.toString())
        for (b in badges) {
            lines.add("${b.id}\t${b.label}\t${b.colorHex}")
        }
        return lines.joinToString("\n")
    }

    fun verifySignature(payload: String, signatureB64: String, publicKeyB64: String): Boolean {
        if (signatureB64.isBlank() || publicKeyB64.isBlank()) return false
        return runCatching {
            val decoder = Base64.getDecoder()
            val keyBytes = decoder.decode(publicKeyB64.trim())
            val sigBytes = decoder.decode(signatureB64.trim())
            val pubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(payload.toByteArray(Charsets.UTF_8))
            verifier.verify(sigBytes)
        }.getOrDefault(false)
    }

    fun parseAndVerify(
        jsonText: String,
        expectedUserId: String,
        pinnedKeyB64: String = BuildConfig.BADGE_PUBLIC_KEY,
        nowMs: Long = System.currentTimeMillis(),
    ): BadgeState? {
        if (expectedUserId.isBlank() || jsonText.isBlank()) return null
        return runCatching {
            val obj = JSONObject(jsonText)
            val userId = obj.optString("userId", "")
            if (userId != expectedUserId) return null
            val perks = obj.optBoolean("perks", false)
            val issuedAt = obj.optLong("issuedAt", 0L)
            val expiresAt = obj.optLong("expiresAt", 0L)
            if (issuedAt <= 0L || expiresAt <= nowMs) return null
            val key = pinnedKeyB64.trim()
            if (key.isBlank()) return null
            val signature = obj.optString("signature", "")
            val arr = obj.optJSONArray("badges")
            val badges = ArrayList<UserBadge>(arr?.length() ?: 0)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").trim()
                    val label = item.optString("label", "").trim()
                    val color = item.optString("color", "").trim()
                    if (id.isNotEmpty() && label.isNotEmpty()) {
                        badges.add(UserBadge(id = id, label = label, colorHex = color.ifEmpty { "#1ED760" }))
                    }
                }
            }
            val payload = canonicalPayload(userId, perks, issuedAt, expiresAt, badges)
            if (!verifySignature(payload, signature, key)) return null
            BadgeState(userId = userId, perks = perks, badges = badges, issuedAt = issuedAt, expiresAt = expiresAt)
        }.getOrNull()
    }

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastUser = p.getString(KEY_LAST_USER, null)?.takeIf { it.isNotBlank() }
        val loaded = if (lastUser != null) {
            p.getString(KEY_RAW_PREFIX + lastUser, null)?.let { parseAndVerify(it, lastUser) }
        } else {
            null
        }
        myState = loaded
        SupportReminder.setServerPerks(context, loaded?.perks == true)
    }

    fun clearSession(context: Context? = null) {
        myState = null
        profileCache.clear()
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(KEY_LAST_USER)?.apply()
        SupportReminder.setServerPerks(context, false)
    }

    fun onAccountLoaded(context: Context, userId: String) {
        if (userId.isBlank()) return
        val appCtx = context.applicationContext
        val p = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString(KEY_LAST_USER, userId).apply()
        val cached = p.getString(KEY_RAW_PREFIX + userId, null)?.let { parseAndVerify(it, userId) }
        myState = cached
        SupportReminder.setServerPerks(appCtx, cached?.perks == true)
        MyspotiRuntime.scope.launch {
            refreshMe(appCtx, userId)
        }
    }

    private fun storeVerified(context: Context, userId: String, rawJson: String, state: BadgeState) {
        val appCtx = context.applicationContext
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_USER, userId)
            .putString(KEY_RAW_PREFIX + userId, rawJson)
            .apply()
        profileCache[userId] = CachedEntry(state, System.currentTimeMillis())
        if (ProfileCache.id == null || ProfileCache.id == userId) {
            myState = state
            SupportReminder.setServerPerks(appCtx, state.perks)
        }
    }

    suspend fun refreshMe(context: Context, userId: String? = ProfileCache.id): BadgeState? = withContext(Dispatchers.IO) {
        val uid = userId?.takeIf { it.isNotBlank() }
            ?: myState?.userId?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_USER, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val url = baseUrl(context)
        if (url.isBlank()) return@withContext null
        val req = Request.Builder()
            .url("$url/api/v1/badges/${Uri.encode(uid)}")
            .get()
            .build()
        val raw = runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                res.body?.string()
            }
        }.getOrNull() ?: return@withContext null
        val verified = parseAndVerify(raw, uid) ?: return@withContext null
        storeVerified(context, uid, raw, verified)
        verified
    }

    suspend fun fetchUser(context: Context, userId: String): BadgeState? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        val now = System.currentTimeMillis()
        val hit = profileCache[userId]
        if (hit != null && now - hit.fetchedAt < PROFILE_CACHE_TTL_MS) {
            return@withContext hit.state
        }
        val url = baseUrl(context)
        if (url.isBlank()) return@withContext null
        val req = Request.Builder()
            .url("$url/api/v1/badges/${Uri.encode(userId)}")
            .get()
            .build()
        val raw = runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                res.body?.string()
            }
        }.getOrNull() ?: return@withContext hit?.state
        val verified = parseAndVerify(raw, userId)
        profileCache[userId] = CachedEntry(verified, now)
        verified
    }

    suspend fun redeem(context: Context, code: String): RedeemResult = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim()
        if (trimmedCode.isBlank()) return@withContext RedeemResult.InvalidCode
        val uid = ProfileCache.id
            ?: ProfileCache.awaitId(context, 3_000L)
            ?: myState?.userId?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_USER, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext RedeemResult.NotSignedIn
        val url = baseUrl(context)
        if (url.isBlank()) return@withContext RedeemResult.Failed("server_unconfigured")
        val bodyJson = JSONObject()
            .put("userId", uid)
            .put("code", trimmedCode)
            .toString()
        val req = Request.Builder()
            .url("$url/api/v1/badges/redeem")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        val outcome = runCatching {
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                res.code to text
            }
        }
        val (status, text) = outcome.getOrElse { err ->
            return@withContext RedeemResult.Failed(err.message ?: "network_error")
        }
        when (status) {
            200 -> {
                val verified = parseAndVerify(text, uid)
                    ?: return@withContext RedeemResult.Failed("invalid_signature")
                storeVerified(context, uid, text, verified)
                RedeemResult.Ok(verified)
            }
            404 -> RedeemResult.InvalidCode
            409 -> RedeemResult.Exhausted
            410 -> RedeemResult.Expired
            else -> {
                val errCode = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                RedeemResult.Failed(errCode.ifBlank { "http_$status" })
            }
        }
    }
}
