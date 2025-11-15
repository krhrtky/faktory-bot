package com.example.faktory.core

class TransientContext(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) {
    operator fun get(key: String): Any? = values[key]

    operator fun set(
        key: String,
        value: Any?,
    ) {
        values[key] = value
    }
}
