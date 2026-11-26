package app.myspoti.host.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoginErrorDetailsTest {
    @Test fun preservesStructuredErrorCode() {
        LoginErrorDetails.record("http", 403)
        assertEquals("Login stage: http\nError code: 403", LoginErrorDetails.summary())
    }
    @Test fun discardsUntrustedText() {
        LoginErrorDetails.record("https://accounts.spotify.com/?token=secret")
        assertFalse(LoginErrorDetails.summary().contains("secret"))
        assertFalse(LoginErrorDetails.summary().contains("spotify.com"))
    }
}
