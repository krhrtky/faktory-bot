package com.example.faktory.dsl

import com.example.faktory.core.AttributeDefinition
import com.example.faktory.core.DefaultCallbackRegistry
import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.DynamicAttribute
import com.example.faktory.core.EvaluationContext
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.SequenceAttribute
import com.example.faktory.core.StaticAttribute
import com.example.faktory.core.TraitDefinition
import com.example.faktory.core.TransientDefinition
import org.jooq.Record
import kotlin.reflect.KClass

class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>,
    private val factoryName: String? = null,
) {
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    private val traits = mutableMapOf<String, TraitDefinition<T>>()
    private val callbacks = DefaultCallbackRegistry<T>()
    private val transients = TransientDefinition()

    fun attribute(
        name: String,
        value: Any?,
    ) {
        attributes[name] = StaticAttribute(value)
    }

    fun attribute(
        name: String,
        generator: (EvaluationContext) -> Any?,
    ) {
        attributes[name] = DynamicAttribute(generator)
    }

    fun sequence(
        name: String,
        generator: (Int) -> Any?,
    ) {
        attributes[name] = SequenceAttribute(name, generator)
    }

    fun trait(
        name: String,
        block: TraitDslBuilder<T>.() -> Unit,
    ) {
        val builder = TraitDslBuilder<T>(name)
        builder.block()
        traits[name] = builder.build()
    }

    fun build(): FactoryDefinition<T> {
        return DefaultFactoryDefinition(
            recordClass = recordClass,
            name = factoryName,
            attributes = attributes,
            traits = traits,
            callbacks = callbacks,
            transients = transients,
        )
    }
}

class TraitDslBuilder<T : Record>(
    private val traitName: String,
) {
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    private val callbacks = DefaultCallbackRegistry<T>()

    fun attribute(
        name: String,
        value: Any?,
    ) {
        attributes[name] = StaticAttribute(value)
    }

    fun build(): TraitDefinition<T> {
        return TraitDefinition(
            name = traitName,
            attributes = attributes,
            callbacks = callbacks,
        )
    }
}

inline fun <reified T : Record> factory(
    name: String? = null,
    block: FactoryDslBuilder<T>.() -> Unit,
): FactoryDefinition<T> {
    val builder = FactoryDslBuilder(T::class, name)
    builder.block()
    return builder.build()
}
