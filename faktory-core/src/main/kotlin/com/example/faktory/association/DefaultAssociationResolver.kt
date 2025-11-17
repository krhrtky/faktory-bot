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
            val definition = factoryRegistry.find(association.targetClass, association.factoryName)

            val builder = DefaultFactoryBuilder(dsl, definition, context.sequenceManager)

            if (context.isCreate) {
                if (association.traits.isNotEmpty()) {
                    builder.create(*association.traits.toTypedArray())
                } else {
                    builder.create(association.overrides)
                }
            } else {
                if (association.traits.isNotEmpty()) {
                    builder.build(*association.traits.toTypedArray())
                } else {
                    builder.build(association.overrides)
                }
            }
        }
}
