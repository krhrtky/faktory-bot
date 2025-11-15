package com.example.faktory.core

data class TransientDefinition(
    val properties: Map<String, Any?> = emptyMap(),
) {
    inline fun <reified T> get(key: String): T? = properties[key] as? T

    fun with(
        key: String,
        value: Any?,
    ) = copy(properties = properties + (key to value))

    fun merge(other: TransientDefinition) = TransientDefinition(properties = properties + other.properties)
}
