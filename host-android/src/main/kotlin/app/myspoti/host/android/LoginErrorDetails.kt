package app.myspoti.host.android

object LoginErrorDetails {
    @Volatile private var stage = "not_reported"
    @Volatile private var code = 0
    fun record(stage: String, code: Int = 0) {
        this.stage = when (stage) {
            "network", "http", "ssl", "timeout", "blank", "player_bridge_timeout" -> stage
            else -> "unknown"
        }
        this.code = code
    }
    fun summary(): String = "Login stage: $stage\nError code: $code"
}
