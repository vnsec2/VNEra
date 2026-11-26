package app.myspoti.host.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginTransportTest {
    @Test fun onlyCodeRequestsOnTheExactAccountsHostAreRelayed() {
        assertTrue(LoginTransport.allowed("POST", "https://accounts.spotify.com/login/code/send?flow=x"))
        assertTrue(LoginTransport.allowed("POST", "https://accounts.spotify.com/login/code/verify"))
        assertFalse(LoginTransport.allowed("GET", "https://accounts.spotify.com/login/code/send"))
        assertFalse(LoginTransport.allowed("POST", "http://accounts.spotify.com/login/code/send"))
        assertFalse(LoginTransport.allowed("POST", "https://accounts.spotify.com.evil.test/login/code/send"))
        assertFalse(LoginTransport.allowed("POST", "https://accounts.spotify.com/login/password"))
    }
}
