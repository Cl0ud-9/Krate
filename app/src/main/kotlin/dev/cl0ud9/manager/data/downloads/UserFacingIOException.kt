package dev.cl0ud9.manager.data.downloads

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// an IOException whose message is already written for the user - passed through as-is
class UserFacingIOException(
    message: String,
) : IOException(message)

// what a user should read for a failed network request, instead of Java's own exception text
// ("Unable to resolve host "api.github.com": No address associated with hostname")
fun friendlyNetworkError(exception: IOException): String =
    when (exception) {
        is UserFacingIOException -> exception.message.orEmpty()
        is UnknownHostException, is ConnectException, is NoRouteToHostException ->
            "No internet connection. Check your connection and try again."
        is SocketTimeoutException -> "The connection timed out. Check your connection and try again."
        is SSLException -> "A secure connection couldn't be made. Check your network and try again."
        else -> "The download was interrupted. Check your connection and try again."
    }

// an unsuccessful HTTP response, worded for the user
fun friendlyHttpError(code: Int): String =
    when (code) {
        HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
            "GitHub is limiting requests right now. Wait a few minutes and try again."
        HTTP_NOT_FOUND -> "This file is no longer available. Refresh and try again."
        in HTTP_SERVER_ERRORS -> "The server is having trouble (error $code). Try again later."
        else -> "The server returned an error ($code). Try again later."
    }

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERRORS = 500..599
