package io.github.krhrtky.faktory.association

import io.github.krhrtky.faktory.builder.DefaultFactoryBuilder
import io.github.krhrtky.faktory.core.AssociationAttribute
import io.github.krhrtky.faktory.core.AssociationResolver
import io.github.krhrtky.faktory.core.EvaluationContext
import io.github.krhrtky.faktory.registry.FactoryRegistry
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
                val applicator = io.github.krhrtky.faktory.trait.TraitApplicator<T>()
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
