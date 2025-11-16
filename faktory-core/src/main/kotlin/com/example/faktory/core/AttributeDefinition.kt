package com.example.faktory.core

import org.jooq.Record
import kotlin.reflect.KClass

sealed interface AttributeDefinition<T> {
    fun evaluate(context: EvaluationContext): T
}

data class StaticAttribute<T>(
    val value: T,
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext) = value
}

data class DynamicAttribute<T>(
    val generator: (EvaluationContext) -> T,
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext) = generator(context)
}

data class SequenceAttribute<T>(
    val name: String?,
    val generator: (Int) -> T,
) : AttributeDefinition<T> {
    override fun evaluate(context: EvaluationContext): T {
        val sequenceName = name ?: context.attributeName
        return context.sequenceManager.next(sequenceName, generator)
    }
}

data class AssociationAttribute<T : Record>(
    val targetClass: KClass<T>,
    val factoryName: String? = null,
    val traits: List<String> = emptyList(),
    val overrides: Map<String, Any?> = emptyMap(),
) : AttributeDefinition<Any?> {
    override fun evaluate(context: EvaluationContext): Any? {
        val record = context.associationResolver?.resolve(this, context)
            ?: throw IllegalStateException("AssociationResolver not configured in EvaluationContext")

        val idProperty = record::class.members.find { it.name == "id" }
            ?: throw IllegalStateException("Record ${record::class.simpleName} does not have an 'id' property")

        return idProperty.call(record)
    }
}
