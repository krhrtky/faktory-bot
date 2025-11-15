package com.example.faktory.registry

import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.FactoryNotFoundException
import org.jooq.Record
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class DefaultFactoryRegistry : FactoryRegistry {
    private val factories = ConcurrentHashMap<RegistryKey, FactoryDefinition<*>>()

    override fun <T : Record> register(definition: FactoryDefinition<T>) {
        val key = RegistryKey(definition.recordClass, definition.name)
        factories[key] = definition
    }

    override fun <T : Record> find(recordClass: KClass<T>): FactoryDefinition<T> {
        return find(recordClass, null)
    }

    override fun <T : Record> find(
        recordClass: KClass<T>,
        name: String?,
    ): FactoryDefinition<T> {
        val key = RegistryKey(recordClass, name)
        @Suppress("UNCHECKED_CAST")
        return factories[key] as? FactoryDefinition<T>
            ?: throw FactoryNotFoundException(recordClass, name)
    }

    override fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T> {
        return FactoryResolver().resolve(definition)
    }

    override fun clear() {
        factories.clear()
    }

    private data class RegistryKey(
        val recordClass: KClass<*>,
        val name: String?,
    )
}
