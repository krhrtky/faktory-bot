package com.example.faktory.builder

import com.example.faktory.core.AttributeDefinition
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.jooq.JooqTableResolver
import org.jooq.DSLContext
import org.jooq.Record

class DefaultFactoryBuilder<T : Record>(
    private val dsl: DSLContext,
    private val definition: FactoryDefinition<T>,
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
        TODO("Create not implemented yet")
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
        TODO("BuildList not implemented yet")
    }

    override fun createList(
        count: Int,
        overrides: Map<String, Any?>,
    ): List<T> {
        TODO("CreateList not implemented yet")
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
            val value =
                when (attrDef) {
                    is com.example.faktory.core.StaticAttribute<*> -> attrDef.value
                    is com.example.faktory.core.DynamicAttribute<*> -> TODO("Dynamic not implemented")
                    is com.example.faktory.core.SequenceAttribute<*> -> TODO("Sequence not implemented")
                    is com.example.faktory.core.AssociationAttribute<*> -> TODO("Association not implemented")
                }
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
