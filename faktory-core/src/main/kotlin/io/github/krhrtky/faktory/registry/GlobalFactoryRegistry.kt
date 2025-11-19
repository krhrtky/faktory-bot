package io.github.krhrtky.faktory.registry

import io.github.krhrtky.faktory.core.FactoryDefinition
import org.jooq.Record
import kotlin.reflect.KClass

object GlobalFactoryRegistry : FactoryRegistry {
    private val registry = DefaultFactoryRegistry()

    override fun <T : Record> register(definition: FactoryDefinition<T>) {
        registry.register(definition)
    }

    override fun <T : Record> find(recordClass: KClass<T>): FactoryDefinition<T> {
        return registry.find(recordClass)
    }

    override fun <T : Record> find(
        recordClass: KClass<T>,
        name: String?,
    ): FactoryDefinition<T> {
        return registry.find(recordClass, name)
    }

    override fun <T : Record> resolve(definition: FactoryDefinition<T>): ResolvedFactory<T> {
        return registry.resolve(definition)
    }

    override fun all(): List<FactoryDefinition<*>> {
        return registry.all()
    }

    override fun clear() {
        registry.clear()
    }
}
