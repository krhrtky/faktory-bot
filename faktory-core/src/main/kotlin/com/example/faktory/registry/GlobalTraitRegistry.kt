package com.example.faktory.registry

import com.example.faktory.core.TraitDefinition
import org.jooq.Record
import java.util.concurrent.ConcurrentHashMap

internal object GlobalTraitRegistry {
    private val traits = ConcurrentHashMap<String, TraitDefinition<*>>()

    fun <T : Record> register(
        name: String,
        trait: TraitDefinition<T>,
    ) {
        traits[name] = trait
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Record> find(name: String): TraitDefinition<T>? {
        return traits[name] as? TraitDefinition<T>
    }

    fun clear() {
        traits.clear()
    }
}
