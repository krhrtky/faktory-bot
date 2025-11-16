package com.example.faktory.registry

import com.example.faktory.core.DefaultCallbackRegistry
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.TransientDefinition
import org.jooq.Record

class FactoryResolver {
    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T> {
        val chain = buildInheritanceChain(definition)
        return mergeChain(chain)
    }

    private fun <T : Record> buildInheritanceChain(definition: FactoryDefinition<T>): List<FactoryDefinition<T>> {
        val chain = mutableListOf<FactoryDefinition<T>>()
        var current: FactoryDefinition<T>? = definition

        while (current != null) {
            chain.add(0, current)
            current = current.parent
        }

        return chain
    }

    private fun <T : Record> mergeChain(chain: List<FactoryDefinition<T>>): ResolvedFactory<T> {
        val mergedAttributes = mutableMapOf<String, com.example.faktory.core.AttributeDefinition<*>>()
        var mergedCallbacks: com.example.faktory.core.CallbackRegistry<T> = DefaultCallbackRegistry<T>()
        var mergedTransients = TransientDefinition()

        chain.forEach { def ->
            mergedAttributes.putAll(def.attributes)
            mergedCallbacks = mergedCallbacks.merge(def.callbacks)
            mergedTransients = mergedTransients.merge(def.transients)
        }

        return ResolvedFactory(
            definition = chain.last(),
            mergedAttributes = mergedAttributes,
            mergedCallbacks = mergedCallbacks,
            mergedTransients = mergedTransients,
        )
    }
}
