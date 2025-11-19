package io.github.krhrtky.faktory.builder

import io.github.krhrtky.faktory.association.CircularDependencyDetector
import io.github.krhrtky.faktory.association.DefaultAssociationResolver
import io.github.krhrtky.faktory.core.AssociationResolver
import io.github.krhrtky.faktory.core.AttributeDefinition
import io.github.krhrtky.faktory.core.CallbackPhase
import io.github.krhrtky.faktory.core.DefaultFactoryDefinition
import io.github.krhrtky.faktory.core.EvaluationContext
import io.github.krhrtky.faktory.core.FactoryDefinition
import io.github.krhrtky.faktory.core.TransientEvaluator
import io.github.krhrtky.faktory.jooq.JooqTableResolver
import io.github.krhrtky.faktory.jooq.RequiredAttributeValidator
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.DefaultSequenceManager
import io.github.krhrtky.faktory.sequence.SequenceManager
import org.jooq.DSLContext
import org.jooq.Record

class DefaultFactoryBuilder<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>,
    private val sequenceManager: SequenceManager = DefaultSequenceManager(),
    private val associationResolver: AssociationResolver? = null,
) : FactoryBuilder<T> {
    override fun build(overrides: Map<String, Any?>): T {
        if (definition.defaultTraits.isNotEmpty()) {
            val applicator = io.github.krhrtky.faktory.trait.TraitApplicator<T>()
            val withTraits = applicator.apply(definition, definition.defaultTraits)
            val withoutDefaultTraits =
                if (withTraits is DefaultFactoryDefinition) {
                    withTraits.copy(defaultTraits = emptyList())
                } else {
                    withTraits
                }
            val builder = DefaultFactoryBuilder(dsl, withoutDefaultTraits, sequenceManager, associationResolver)
            return builder.build(overrides)
        }

        val resolved = GlobalFactoryRegistry.resolve(definition)
        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        val transientKeys = resolved.mergedTransients.properties.keys
        val attributeOverrides = overrides.filterKeys { it !in transientKeys }

        val attributes = evaluateAttributes(definition.attributes, attributeOverrides, isCreate = false)

        // Validate required attributes
        RequiredAttributeValidator.validateRequiredAttributes(table, attributes.keys)

        attributes.forEach { (name, value) ->
            record.set(name, value)
        }

        val transients = TransientEvaluator<T>().evaluate(definition, overrides)
        resolved.mergedCallbacks.execute(CallbackPhase.AFTER_BUILD, record, transients)

        return record
    }

    override fun build(vararg traits: io.github.krhrtky.faktory.core.Trait<T>): T {
        val traitNames = traits.map { it.name }
        val applicator = io.github.krhrtky.faktory.trait.TraitApplicator<T>()
        val withTraits = applicator.apply(definition, traitNames)

        val withoutDefaultTraits =
            if (withTraits is DefaultFactoryDefinition) {
                withTraits.copy(defaultTraits = emptyList())
            } else {
                withTraits
            }

        val builder = DefaultFactoryBuilder(dsl, withoutDefaultTraits, sequenceManager, associationResolver)
        return builder.build(emptyMap())
    }

    override fun create(overrides: Map<String, Any?>): T {
        if (definition.defaultTraits.isNotEmpty()) {
            val applicator = io.github.krhrtky.faktory.trait.TraitApplicator<T>()
            val withTraits = applicator.apply(definition, definition.defaultTraits)
            val withoutDefaultTraits =
                if (withTraits is DefaultFactoryDefinition) {
                    withTraits.copy(defaultTraits = emptyList())
                } else {
                    withTraits
                }
            val builder = DefaultFactoryBuilder(dsl, withoutDefaultTraits, sequenceManager, associationResolver)
            return builder.create(overrides)
        }

        val resolved = GlobalFactoryRegistry.resolve(definition)
        val transients = TransientEvaluator<T>().evaluateFrom(resolved.mergedTransients, overrides)

        val transientKeys = resolved.mergedTransients.properties.keys
        val attributeOverrides = overrides.filterKeys { it !in transientKeys }

        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        val attributes = evaluateAttributes(definition.attributes, attributeOverrides, isCreate = true)

        // Validate required attributes
        RequiredAttributeValidator.validateRequiredAttributes(table, attributes.keys)

        attributes.forEach { (name, value) ->
            record.set(name, value)
        }

        resolved.mergedCallbacks.execute(CallbackPhase.AFTER_BUILD, record, transients)
        resolved.mergedCallbacks.execute(CallbackPhase.BEFORE_CREATE, record, transients)

        val inserted =
            dsl.insertInto(table)
                .set(record)
                .returning()
                .fetchOne()
                ?: throw IllegalStateException("Failed to insert record")

        resolved.mergedCallbacks.execute(CallbackPhase.AFTER_CREATE, inserted, transients)

        return inserted
    }

    override fun create(vararg traits: io.github.krhrtky.faktory.core.Trait<T>): T {
        val traitNames = traits.map { it.name }
        val applicator = io.github.krhrtky.faktory.trait.TraitApplicator<T>()
        val withTraits = applicator.apply(definition, traitNames)

        val withoutDefaultTraits =
            if (withTraits is DefaultFactoryDefinition) {
                withTraits.copy(defaultTraits = emptyList())
            } else {
                withTraits
            }

        val builder = DefaultFactoryBuilder(dsl, withoutDefaultTraits, sequenceManager, associationResolver)
        return builder.create(emptyMap())
    }

    override fun buildList(
        count: Int,
        overrides: Map<String, Any?>,
    ): List<T> {
        return (1..count).map { build(overrides) }
    }

    override fun createList(
        count: Int,
        overrides: Map<String, Any?>,
    ): List<T> {
        return (1..count).map { create(overrides) }
    }

    override fun attributes(overrides: Map<String, Any?>): Map<String, Any?> {
        return evaluateAttributes(definition.attributes, overrides)
    }

    private fun evaluateAttributes(
        definitions: Map<String, AttributeDefinition<*>>,
        overrides: Map<String, Any?>,
        isCreate: Boolean = false,
    ): Map<String, Any?> {
        val evaluated = mutableMapOf<String, Any?>()

        val resolver =
            associationResolver ?: DefaultAssociationResolver(
                dsl = dsl,
                factoryRegistry = GlobalFactoryRegistry,
                circularDependencyDetector = CircularDependencyDetector(),
            )

        definitions.forEach { (name, attrDef) ->
            val context =
                EvaluationContext(
                    sequenceManager = sequenceManager,
                    attributeName = name,
                    associationResolver = resolver,
                    isCreate = isCreate,
                )
            val value = attrDef.evaluate(context)
            evaluated[name] = value
        }

        overrides.forEach { (name, value) ->
            evaluated[name] = value
        }

        return evaluated
    }

    private fun Record.set(
        fieldName: String,
        value: Any?,
    ) {
        val field = this.field(fieldName) ?: throw IllegalArgumentException("Field $fieldName not found")
        @Suppress("UNCHECKED_CAST")
        this.set(field as org.jooq.Field<Any?>, value)
    }
}
