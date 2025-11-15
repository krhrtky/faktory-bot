package com.example.faktory.builder

import com.example.faktory.core.AttributeDefinition
import com.example.faktory.core.EvaluationContext
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.jooq.JooqTableResolver
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
        val table = JooqTableResolver.resolveTable(definition.recordClass)
        val record = dsl.newRecord(table)

        val attributes = evaluateAttributes(definition.attributes, overrides)

        attributes.forEach { (name, value) ->
            record.set(name, value)
        }

        return record
    }

    override fun build(
        vararg traits: String,
        overrides: Map<String, Any?>,
    ): T {
        TODO("Traits not implemented yet")
    }

    override fun create(overrides: Map<String, Any?>): T {
        val record = build(overrides)
        val table = JooqTableResolver.resolveTable(definition.recordClass)

        val inserted =
            dsl.insertInto(table)
                .set(record)
                .returning()
                .fetchOne()

        return inserted ?: throw IllegalStateException("Failed to insert record")
    }

    override fun create(
        vararg traits: String,
        overrides: Map<String, Any?>,
    ): T {
        TODO("Create not implemented yet")
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
