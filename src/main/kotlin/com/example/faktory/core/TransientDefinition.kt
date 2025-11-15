package com.example.faktory.core

class TransientDefinition(
    private val attributes: MutableMap<String, AttributeDefinition<*>> = mutableMapOf(),
) {
    fun set(
        name: String,
        definition: AttributeDefinition<*>,
    ) {
        attributes[name] = definition
    }

    fun get(name: String): AttributeDefinition<*>? = attributes[name]

    fun merge(other: TransientDefinition): TransientDefinition {
        val merged = TransientDefinition()
        attributes.forEach { (name, attr) -> merged.set(name, attr) }
        if (other is TransientDefinition) {
            other.attributes.forEach { (name, attr) -> merged.set(name, attr) }
        }
        return merged
    }
}
