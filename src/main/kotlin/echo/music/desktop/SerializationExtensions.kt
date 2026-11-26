package com.music.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

inline fun <reified T> Json.encodeToString(value: T): String =
    encodeToString(serializersModule.serializer<T>(), value)
