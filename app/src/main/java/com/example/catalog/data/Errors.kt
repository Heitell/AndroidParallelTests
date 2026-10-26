package com.example.catalog.data

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

/** Текст ошибки, который видит пользователь. */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> if (code() == 404) "Not found" else "Server error (${code()})"
    is InterruptedIOException -> "Request timed out"
    is IOException -> "No connection"
    is SerializationException -> "Unexpected response"
    else -> "Something went wrong"
}
