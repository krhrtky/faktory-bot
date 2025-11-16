package com.example.faktory.trait

import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.TraitNotFoundException
import com.example.faktory.registry.GlobalTraitRegistry
import org.jooq.Record

class TraitApplicator<T : Record> {
    fun apply(
        definition: FactoryDefinition<T>,
        traitNames: List<String>,
    ): FactoryDefinition<T> {
        require(definition is com.example.faktory.core.DefaultFactoryDefinition) {
            "Only DefaultFactoryDefinition is supported"
        }
        return traitNames.fold(definition as FactoryDefinition<T>) { acc, traitName ->
            val trait =
                (acc as com.example.faktory.core.DefaultFactoryDefinition).traits[traitName]
                    ?: GlobalTraitRegistry.find<T>(traitName)
                    ?: throw TraitNotFoundException(traitName)
            trait.applyTo(acc)
        }
    }
}
