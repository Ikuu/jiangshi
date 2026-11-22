package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.suwayomi.GraphQLException
import eu.kanade.tachiyomi.network.HttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** What checking a Kavita or Suwayomi server found, for Settings → Tracking. */
sealed interface ServerStatus {
    /** The server answered and took the sign-in, with its version and the account where it says them. */
    data class Connected(val version: String?, val username: String?) : ServerStatus

    data class Problem(val reason: Reason, val httpCode: Int? = null, val detail: String? = null) : ServerStatus

    enum class Reason {
        /** The extension doesn't have the server's address or key yet. */
        NOT_SET_UP,
        UNKNOWN_HOST,
        UNREACHABLE,
        TIMED_OUT,
        SECURE_CONNECTION,
        NOT_SIGNED_IN,

        /** Something answered, but not the server's API. */
        NOT_FOUND,
        SERVER_ERROR,
        OTHER,
    }

    companion object {
        /** Works out why a check failed, looking through wrapped exceptions. */
        fun of(e: Throwable): Problem {
            var cause: Throwable? = e
            while (cause != null) {
                reasonOf(cause)?.let { return it }
                cause = cause.cause?.takeIf { it !== cause }
            }
            return Problem(Reason.OTHER, detail = e.message)
        }

        private fun reasonOf(e: Throwable): Problem? = when (e) {
            is HttpException -> ofHttp(e.code)
            is UnknownHostException -> Problem(Reason.UNKNOWN_HOST)
            is SocketTimeoutException -> Problem(Reason.TIMED_OUT)
            is ConnectException, is NoRouteToHostException -> Problem(Reason.UNREACHABLE)
            is SSLException -> Problem(Reason.SECURE_CONNECTION)
            is GraphQLException -> if (e.messages.any { it.contains("unauthorized", ignoreCase = true) }) {
                Problem(Reason.NOT_SIGNED_IN)
            } else {
                Problem(Reason.OTHER, detail = e.messages.firstOrNull())
            }
            else -> null
        }

        fun ofHttp(code: Int): Problem = when (code) {
            401, 403 -> Problem(Reason.NOT_SIGNED_IN, code)
            404 -> Problem(Reason.NOT_FOUND, code)
            in 500..599 -> Problem(Reason.SERVER_ERROR, code)
            else -> Problem(Reason.OTHER, code, "HTTP $code")
        }
    }
}
