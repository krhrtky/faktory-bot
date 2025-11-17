package com.example.faktory.lint

import com.example.faktory.builder.DefaultFactoryBuilder
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.FactoryLintException
import com.example.faktory.core.FactoryNotFoundException
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record
import kotlin.reflect.KClass

object FactoryLinter {
    fun lint(
        dsl: DSLContext,
        traits: Boolean = false,
    ) {
        val registry = GlobalFactoryRegistry
        registry.all().forEach { factory ->
            @Suppress("UNCHECKED_CAST")
            lintFactory(dsl, factory as FactoryDefinition<Record>, traits)
        }
    }

    fun <T : Record> lint(
        dsl: DSLContext,
        recordClass: KClass<T>,
        traits: Boolean = false,
    ) {
        val factory =
            try {
                GlobalFactoryRegistry.find(recordClass)
            } catch (e: FactoryNotFoundException) {
                throw e
            }
        lintFactory(dsl, factory, traits)
    }

    private fun <T : Record> lintFactory(
        dsl: DSLContext,
        factory: FactoryDefinition<T>,
        lintTraits: Boolean,
    ) {
        try {
            val builder = DefaultFactoryBuilder(dsl, factory, GlobalSequenceManager.getInstance())
            builder.build()
        } catch (e: Exception) {
            throw FactoryLintException(
                factory.recordClass,
                factory.name,
                null,
                e,
            )
        }

        if (lintTraits && factory is com.example.faktory.core.DefaultFactoryDefinition) {
            factory.traits.forEach { (traitName, _) ->
                try {
                    val applicator = com.example.faktory.trait.TraitApplicator<T>()
                    val withTrait = applicator.apply(factory, listOf(traitName))
                    val builder = DefaultFactoryBuilder(dsl, withTrait, GlobalSequenceManager.getInstance())
                    builder.build()
                } catch (e: Exception) {
                    throw FactoryLintException(
                        factory.recordClass,
                        factory.name,
                        traitName,
                        e,
                    )
                }
            }
        }
    }
}
