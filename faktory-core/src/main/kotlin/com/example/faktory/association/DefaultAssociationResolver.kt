package com.example.faktory.association

import com.example.faktory.builder.DefaultFactoryBuilder
import com.example.faktory.core.AssociationAttribute
import com.example.faktory.core.AssociationResolver
import com.example.faktory.core.EvaluationContext
import com.example.faktory.registry.FactoryRegistry
import org.jooq.DSLContext
import org.jooq.Record

class DefaultAssociationResolver(
    private val dsl: DSLContext,
    private val factoryRegistry: FactoryRegistry,
    private val circularDependencyDetector: CircularDependencyDetector = CircularDependencyDetector(),
) : AssociationResolver {
    override fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext,
    ): T =
        circularDependencyDetector.withCheck(association.targetClass) {
            var definition = factoryRegistry.find(association.targetClass, association.factoryName)

            if (association.traits.isNotEmpty()) {
                val applicator = com.example.faktory.trait.TraitApplicator<T>()
                definition = applicator.apply(definition, association.traits)
            }

            val builder = DefaultFactoryBuilder(dsl, definition, context.sequenceManager)

            if (context.isCreate) {
                builder.create(association.overrides)
            } else {
                builder.build(association.overrides)
            }
        }
}
