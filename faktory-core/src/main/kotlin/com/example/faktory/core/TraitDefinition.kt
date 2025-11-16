package com.example.faktory.core

import org.jooq.Record

internal data class TraitDefinition<T : Record>(
    val name: String,
    val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    val transients: TransientDefinition = TransientDefinition(),
    val includedTraits: List<String> = emptyList(),
) {
    fun applyTo(
        definition: FactoryDefinition<T>,
        visited: Set<String> = emptySet(),
    ): FactoryDefinition<T> {
        if (name in visited) {
            throw CircularTraitReferenceException(name, visited)
        }

        var result = definition
        val newVisited = visited + name

        require(definition is DefaultFactoryDefinition) {
            "Only DefaultFactoryDefinition is supported"
        }

        includedTraits.forEach { includedName ->
            val includedTrait =
                definition.traits[includedName]
                    ?: com.example.faktory.registry.GlobalTraitRegistry.find<T>(includedName)
                    ?: throw TraitNotFoundException(includedName)
            result = includedTrait.applyTo(result, newVisited)
        }

        val finalResult = result
        return if (finalResult is DefaultFactoryDefinition) {
            finalResult.copy(
                attributes = finalResult.attributes + attributes,
                callbacks = finalResult.callbacks.merge(callbacks),
                transients = finalResult.transients.merge(transients),
            )
        } else {
            throw IllegalArgumentException("Only DefaultFactoryDefinition is supported")
        }
    }
}
