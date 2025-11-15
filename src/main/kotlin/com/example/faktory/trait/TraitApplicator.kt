package com.example.faktory.trait

import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.TraitNotFoundException
import org.jooq.Record

class TraitApplicator<T : Record> {
    fun apply(
        definition: FactoryDefinition<T>,
        traitNames: List<String>,
    ): FactoryDefinition<T> =
        traitNames.fold(definition) { acc, traitName ->
            val trait =
                definition.traits[traitName]
                    ?: throw TraitNotFoundException(traitName)
            trait.applyTo(acc)
        }
}
