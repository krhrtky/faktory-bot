package com.example.faktory.registry

import com.example.faktory.core.AttributeDefinition
import com.example.faktory.core.CallbackRegistry
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.TransientDefinition
import org.jooq.Record
import kotlin.reflect.KClass

interface FactoryRegistry {
    fun <T : Record> register(definition: FactoryDefinition<T>)

    fun <T : Record> find(recordClass: KClass<T>): FactoryDefinition<T>

    fun <T : Record> find(
        recordClass: KClass<T>,
        name: String?,
    ): FactoryDefinition<T>

    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T>

    fun clear()
}

data class ResolvedFactory<T : Record>(
    val definition: FactoryDefinition<T>,
    val mergedAttributes: Map<String, AttributeDefinition<*>>,
    val mergedCallbacks: CallbackRegistry<T>,
    val mergedTransients: TransientDefinition,
)
