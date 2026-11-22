package com.eduvark.app.data.remote

class ApiException(
    val code: String,
    override val message: String,
    val statusCode: Int,
) : Exception(message)
