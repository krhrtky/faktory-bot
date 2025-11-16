package com.example.faktory.core

data class TransientContext(
    private val values: Map<String, Any?> = emptyMap(),
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T =
        values[key] as? T
            ?: throw TransientNotFoundException(key)

    fun getOrNull(key: String): Any? = values[key]

    fun with(
        key: String,
        value: Any?,
    ) = TransientContext(values + (key to value))

    fun merge(other: TransientContext) = TransientContext(values + other.values)
}

class TransientNotFoundException(key: String) : FactoryException("Transient key '$key' not found")
