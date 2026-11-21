package com.music.spotui.data.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class BadgeClientTest {
    private fun newKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun signedJson(
        userId: String,
        perks: Boolean,
        badges: List<BadgeClient.UserBadge>,
        issuedAt: Long,
        expiresAt: Long,
        keyPair: java.security.KeyPair,
        tamperAfterSign: ((JSONObject) -> Unit)? = null,
    ): String {
        val payload = BadgeClient.canonicalPayload(userId, perks, issuedAt, expiresAt, badges)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        val sigB64 = Base64.getEncoder().encodeToString(signer.sign())
        val pubB64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val arr = JSONArray()
        for (b in badges) {
            arr.put(JSONObject().put("id", b.id).put("label", b.label).put("color", b.colorHex))
        }
        val obj = JSONObject()
            .put("userId", userId)
            .put("perks", perks)
            .put("badges", arr)
            .put("issuedAt", issuedAt)
            .put("expiresAt", expiresAt)
            .put("publicKey", pubB64)
            .put("signature", sigB64)
        tamperAfterSign?.invoke(obj)
        return obj.toString()
    }

    @Test
    fun verifiesValidSignedAssertionWithCyrillicLabels() {
        val kp = newKeyPair()
        val badges = listOf(
            BadgeClient.UserBadge("vip", "VIP", "#FFD700"),
            BadgeClient.UserBadge("dev", "Разработчик", "#509BF5"),
        )
        val json = signedJson("spotify_user_1", true, badges, 1_000L, 10_000L, kp)
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val parsed = BadgeClient.parseAndVerify(json, expectedUserId = "spotify_user_1", pinnedKeyB64 = pubB64, nowMs = 5_000L)
        assertNotNull(parsed)
        assertTrue(parsed!!.perks)
        assertEquals(2, parsed.badges.size)
        assertEquals("Разработчик", parsed.badges[1].label)
    }

    @Test
    fun rejectsTamperedLabelOrPerks() {
        val kp = newKeyPair()
        val badges = listOf(BadgeClient.UserBadge("supporter", "Supporter", "#1ED760"))
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val tamperedPerks = signedJson("u1", false, badges, 1_000L, 10_000L, kp) {
            it.put("perks", true)
        }
        assertNull(BadgeClient.parseAndVerify(tamperedPerks, "u1", pinnedKeyB64 = pubB64, nowMs = 5_000L))
        val tamperedLabel = signedJson("u1", true, badges, 1_000L, 10_000L, kp) {
            it.getJSONArray("badges").getJSONObject(0).put("label", "Admin")
        }
        assertNull(BadgeClient.parseAndVerify(tamperedLabel, "u1", pinnedKeyB64 = pubB64, nowMs = 5_000L))
    }

    @Test
    fun rejectsExpiredOrWrongUserOrWrongKey() {
        val kp1 = newKeyPair()
        val kp2 = newKeyPair()
        val badges = listOf(BadgeClient.UserBadge("vip", "VIP", "#1ED760"))
        val pub1 = Base64.getEncoder().encodeToString(kp1.public.encoded)
        val pub2 = Base64.getEncoder().encodeToString(kp2.public.encoded)
        val json = signedJson("u1", true, badges, 1_000L, 10_000L, kp1)
        assertNull(BadgeClient.parseAndVerify(json, "u1", pinnedKeyB64 = pub1, nowMs = 10_000L))
        assertNull(BadgeClient.parseAndVerify(json, "u2", pinnedKeyB64 = pub1, nowMs = 5_000L))
        assertNull(BadgeClient.parseAndVerify(json, "u1", pinnedKeyB64 = pub2, nowMs = 5_000L))
    }

    @Test
    fun rejectsEmptyPinnedKeyEvenWhenPayloadCarriesMatchingPublicKey() {
        val kp = newKeyPair()
        val badges = listOf(BadgeClient.UserBadge("vip", "VIP", "#1ED760"))
        val json = signedJson("u1", true, badges, 1_000L, 10_000L, kp)
        assertNull(BadgeClient.parseAndVerify(json, "u1", pinnedKeyB64 = "", nowMs = 5_000L))
        assertNull(BadgeClient.parseAndVerify(json, "u1", pinnedKeyB64 = "   ", nowMs = 5_000L))
    }
}
