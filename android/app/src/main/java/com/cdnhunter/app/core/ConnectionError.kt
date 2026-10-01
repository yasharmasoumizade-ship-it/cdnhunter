package com.cdnhunter.app.core

/** Stable machine-readable codes. The UI maps these to text; logs carry them verbatim. */
enum class ErrorCode {
    INVALID_JSON,
    INVALID_CONFIG,
    INVALID_SERVER,
    INVALID_PORT,
    INVALID_CREDENTIALS,
    UNSUPPORTED_PROTOCOL,
    PERMISSION_DENIED,
    CORE_START_FAILED,
    CORE_CRASHED,
    TUNNEL_FAILED,
    NETWORK_UNAVAILABLE,
    CONNECTION_TIMEOUT,
    RECONNECT_FAILED,
    UNKNOWN,
}

/**
 * The one error type that leaves the connection layer.
 *
 * Raw core output never reaches the UI: [technical] is for the debug log (and is
 * already redacted by the constructor), [userMessage] is what a person is shown.
 */
sealed class ConnectionError(
    val code: ErrorCode,
    technical: String,
    val cause: Throwable? = null,
) {
    /** Redacted, safe to log and to put in the copyable debug dump. */
    val technical: String = Redactor.redact(technical)

    /** Whether trying again can plausibly help. A bad config never gets better by retrying. */
    abstract val retryable: Boolean

    class ConfigError(code: ErrorCode, technical: String, val field: String? = null) :
        ConnectionError(code, technical) {
        override val retryable = false
    }

    class PermissionError(technical: String) : ConnectionError(ErrorCode.PERMISSION_DENIED, technical) {
        override val retryable = false
    }

    class CoreError(code: ErrorCode, technical: String, cause: Throwable? = null) :
        ConnectionError(code, technical, cause) {
        // A config the core rejected outright will be rejected again; a crash may not recur.
        override val retryable = code == ErrorCode.CORE_CRASHED
    }

    class TunnelError(technical: String, cause: Throwable? = null) :
        ConnectionError(ErrorCode.TUNNEL_FAILED, technical, cause) {
        override val retryable = true
    }

    class NetworkError(technical: String, cause: Throwable? = null) :
        ConnectionError(ErrorCode.NETWORK_UNAVAILABLE, technical, cause) {
        override val retryable = true
    }

    class TimeoutError(technical: String, cause: Throwable? = null) :
        ConnectionError(ErrorCode.CONNECTION_TIMEOUT, technical, cause) {
        override val retryable = true
    }

    class ReconnectFailedError(val attempts: Int, technical: String) :
        ConnectionError(ErrorCode.RECONNECT_FAILED, technical) {
        override val retryable = false
    }

    class UnknownError(technical: String, cause: Throwable? = null) :
        ConnectionError(ErrorCode.UNKNOWN, technical, cause) {
        override val retryable = true
    }

    /** Text for a person. [lang] is the app's language setting: "fa" or anything else for English. */
    fun userMessage(lang: String): String {
        val fa = lang == "fa"
        return when (code) {
            ErrorCode.INVALID_JSON ->
                if (fa) "فایل JSON نامعتبر است." else "The JSON config is not valid."
            ErrorCode.INVALID_CONFIG ->
                if (fa) "کانفیگ ناقص یا نامعتبر است." else "The config is incomplete or invalid."
            ErrorCode.INVALID_SERVER ->
                if (fa) "آدرس سرور نامعتبر است." else "The server address is invalid."
            ErrorCode.INVALID_PORT ->
                if (fa) "پورت سرور نامعتبر است." else "The server port is invalid."
            ErrorCode.INVALID_CREDENTIALS ->
                if (fa) "اطلاعات ورود (UUID/رمز) کانفیگ نامعتبر است." else "The config's credentials (UUID/password) are invalid."
            ErrorCode.UNSUPPORTED_PROTOCOL ->
                if (fa) "این پروتکل یا ترنسپورت پشتیبانی نمی‌شود." else "This protocol or transport is not supported."
            ErrorCode.PERMISSION_DENIED ->
                if (fa) "مجوز VPN داده نشده است." else "VPN permission was not granted."
            ErrorCode.CORE_START_FAILED ->
                if (fa) "هسته‌ی VPN اجرا نشد." else "The VPN core failed to start."
            ErrorCode.CORE_CRASHED ->
                if (fa) "هسته‌ی VPN متوقف شد." else "The VPN core stopped unexpectedly."
            ErrorCode.TUNNEL_FAILED ->
                if (fa) "ساخت تونل VPN ناموفق بود." else "Could not create the VPN tunnel."
            ErrorCode.NETWORK_UNAVAILABLE ->
                if (fa) "اینترنت در دسترس نیست." else "No network connection is available."
            ErrorCode.CONNECTION_TIMEOUT ->
                if (fa) "اتصال به سرور زمان‌بر شد؛ سرور پاسخ نداد." else "The server did not respond in time."
            ErrorCode.RECONNECT_FAILED ->
                if (fa) "اتصال مجدد ناموفق بود." else "Reconnecting failed."
            ErrorCode.UNKNOWN ->
                if (fa) "خطای ناشناخته در اتصال." else "An unknown connection error occurred."
        }
    }

    override fun toString(): String = "ConnectionError($code: $technical)"
}
