package com.example.faktory.builder

import com.example.faktory.core.AttributeDefinition
import com.example.faktory.core.CallbackPhase
import com.example.faktory.core.EvaluationContext
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.TransientEvaluator
import com.example.faktory.jooq.JooqTableResolver
import com.example.faktory.jooq.RequiredAttributeValidator
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.DefaultSequenceManager
import com.example.faktory.sequence.SequenceManager
import org.jooq.DSLContext
import org.jooq.Record

class DefaultFactoryBuilder<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>,
    private val sequenceManager: SequenceManager = DefaultSequenceManager(),
) : FactoryBuilder<T> {
    override fun build(overrides: Map<String, Any?>): T {
        val resolved = GlobalFactoryRegistry.resolve(definition)
        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        val transientKeys = resolved.mergedTransients.properties.keys
        val attributeOverrides = overrides.filterKeys { it !in transientKeys }

        val attributes = evaluateAttributes(definition.attributes, attributeOverrides)

        // Validate required attributes
        RequiredAttributeValidator.validateRequiredAttributes(table, attributes.keys)

        attributes.forEach { (name, value) ->
            record.set(name, value)
        }

        val transients = TransientEvaluator<T>().evaluate(definition, overrides)
        resolved.mergedCallbacks.execute(CallbackPhase.AFTER_BUILD, record, transients)

        return record
    }

    override fun build(
        vararg traits: String,
        overrides: Map<String, Any?>,
    ): T {
        val applicator = com.example.faktory.trait.TraitApplicator<T>()
        val withTraits = applicator.apply(definition, traits.toList())

        val builder = DefaultFactoryBuilder(dsl, withTraits, sequenceManager)
        return builder.build(overrides)
    }

    override fun create(overrides: Map<String, Any?>): T {
        val resolved = GlobalFactoryRegistry.resolve(definition)
        val transients = TransientEvaluator<T>().evaluateFrom(resolved.mergedTransients, overrides)

        val transientKeys = resolved.mergedTransients.properties.keys
        val attributeOverrides = overrides.filterKeys { it !in transientKeys }

        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        val attributes = evaluateAttributes(definition.attributes, attributeOverrides)

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

    override fun create(
        vararg traits: String,
        overrides: Map<String, Any?>,
    ): T {
        val applicator = com.example.faktory.trait.TraitApplicator<T>()
        val withTraits = applicator.apply(definition, traits.toList())

        val builder = DefaultFactoryBuilder(dsl, withTraits, sequenceManager)
        return builder.create(overrides)
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
    ): Map<String, Any?> {
        val evaluated = mutableMapOf<String, Any?>()

        definitions.forEach { (name, attrDef) ->
            val context =
                EvaluationContext(
                    sequenceManager = sequenceManager,
                    attributeName = name,
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
