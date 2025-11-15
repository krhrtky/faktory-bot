package com.example.faktory.core

import org.jooq.Record

data class TraitDefinition<T : Record>(
    val name: String,
    val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    val transients: TransientDefinition = TransientDefinition(),
) {
    fun applyTo(definition: FactoryDefinition<T>): FactoryDefinition<T> {
        return if (definition is DefaultFactoryDefinition) {
            definition.copy(
                attributes = definition.attributes + attributes,
                callbacks = definition.callbacks.merge(callbacks),
                transients = definition.transients.merge(transients),
            )
        } else {
            throw IllegalArgumentException("Only DefaultFactoryDefinition is supported")
        }
    }
}
