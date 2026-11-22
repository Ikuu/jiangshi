package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.ServerStatus.Reason
import eu.kanade.tachiyomi.data.track.suwayomi.GraphQLException
import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerStatusTest {
    private fun reason(e: Throwable) = ServerStatus.of(e).reason

    @Test
    fun `network failures say what went wrong`() {
        assertEquals(Reason.UNKNOWN_HOST, reason(UnknownHostException("kavita.local")))
        assertEquals(Reason.UNREACHABLE, reason(ConnectException("Connection refused")))
        assertEquals(Reason.TIMED_OUT, reason(SocketTimeoutException()))
        assertEquals(Reason.SECURE_CONNECTION, reason(SSLHandshakeException("bad certificate")))
    }

    @Test
    fun `HTTP errors are told apart`() {
        assertEquals(Reason.NOT_SIGNED_IN, reason(HttpException(401)))
        assertEquals(Reason.NOT_SIGNED_IN, reason(HttpException(403)))
        assertEquals(Reason.NOT_FOUND, reason(HttpException(404)))
        assertEquals(Reason.SERVER_ERROR, reason(HttpException(502)))
        assertEquals(502, ServerStatus.of(HttpException(502)).httpCode)
    }

    @Test
    fun `wrapped exceptions are looked through`() {
        assertEquals(Reason.UNREACHABLE, reason(IOException(IllegalStateException(ConnectException()))))
    }

    @Test
    fun `GraphQL sign-in errors count as not signed in`() {
        assertEquals(Reason.NOT_SIGNED_IN, reason(GraphQLException(listOf("Unauthorized"))))
        val other = ServerStatus.of(GraphQLException(listOf("Something else")))
        assertEquals(Reason.OTHER, other.reason)
        assertEquals("Something else", other.detail)
    }
}
