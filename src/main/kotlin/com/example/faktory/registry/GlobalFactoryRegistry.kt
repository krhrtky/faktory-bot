package com.example.faktory.registry

import com.example.faktory.core.FactoryDefinition
import org.jooq.Record
import kotlin.reflect.KClass

object GlobalFactoryRegistry {
    private val registry = DefaultFactoryRegistry()

    fun <T : Record> register(definition: FactoryDefinition<T>) {
        registry.register(definition)
    }

    fun <T : Record> find(
        recordClass: KClass<T>,
        name: String? = null,
    ): FactoryDefinition<T> {
        return registry.find(recordClass, name)
    }

    fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T> {
        return registry.resolve(definition)
    }

    fun clear() {
        registry.clear()
    }
}
