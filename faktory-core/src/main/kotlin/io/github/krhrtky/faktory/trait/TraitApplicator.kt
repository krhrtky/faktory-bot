package io.github.krhrtky.faktory.trait

import io.github.krhrtky.faktory.core.FactoryDefinition
import io.github.krhrtky.faktory.core.TraitNotFoundException
import io.github.krhrtky.faktory.registry.GlobalTraitRegistry
import org.jooq.Record

class TraitApplicator<T : Record> {
    fun apply(
        definition: FactoryDefinition<T>,
        traitNames: List<String>,
    ): FactoryDefinition<T> {
        require(definition is io.github.krhrtky.faktory.core.DefaultFactoryDefinition) {
            "Only DefaultFactoryDefinition is supported"
        }
        return traitNames.fold(definition as FactoryDefinition<T>) { acc, traitName ->
            val trait =
                (acc as io.github.krhrtky.faktory.core.DefaultFactoryDefinition).traits[traitName]
                    ?: GlobalTraitRegistry.find<T>(traitName)
                    ?: throw TraitNotFoundException(traitName)
            trait.applyTo(acc)
        }
    }
}
