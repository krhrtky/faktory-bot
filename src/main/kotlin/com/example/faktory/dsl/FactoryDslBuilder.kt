package com.example.faktory.dsl

import com.example.faktory.builder.DefaultFactoryBuilder
import com.example.faktory.builder.FactoryBuilder
import com.example.faktory.core.AttributeDefinition
import com.example.faktory.core.CallbackPhase
import com.example.faktory.core.DefaultCallbackRegistry
import com.example.faktory.core.DefaultFactoryDefinition
import com.example.faktory.core.DynamicAttribute
import com.example.faktory.core.EvaluationContext
import com.example.faktory.core.FactoryDefinition
import com.example.faktory.core.SequenceAttribute
import com.example.faktory.core.StaticAttribute
import com.example.faktory.core.TraitDefinition
import com.example.faktory.core.TransientContext
import com.example.faktory.core.TransientDefinition
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>,
    private val factoryName: String? = null,
) {
    private val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    private val traits = mutableMapOf<String, TraitDefinition<T>>()
    private val callbacks = DefaultCallbackRegistry<T>()
    private var transients = TransientDefinition()

    operator fun set(
        name: String,
        value: Any?,
    ) {
        when (value) {
            is SequenceAttribute<*> -> attributes[name] = value
            is DynamicAttribute<*> -> attributes[name] = value
            else -> attributes[name] = StaticAttribute(value)
        }
    }

    fun <T> sequence(generator: (Int) -> T): SequenceAttribute<T> {
        return SequenceAttribute(null, generator)
    }

    fun transient(block: TransientDslBuilder.() -> Unit) {
        val builder = TransientDslBuilder()
        builder.block()
        transients = builder.build()
    }

    fun afterBuild(callback: (T, TransientContext) -> Unit) {
        callbacks.register(CallbackPhase.AFTER_BUILD, callback)
    }

    fun beforeCreate(callback: (T, TransientContext) -> Unit) {
        callbacks.register(CallbackPhase.BEFORE_CREATE, callback)
    }

    fun afterCreate(callback: (T, TransientContext) -> Unit) {
        callbacks.register(CallbackPhase.AFTER_CREATE, callback)
    }

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

    fun sequenceAttr(
        name: String,
        generator: (Int) -> Any?,
    ) {
        attributes[name] = SequenceAttribute(name, generator)
    }

    fun trait(
        name: String,
        block: FactoryDslBuilder<T>.() -> Unit,
    ) {
        val builder = FactoryDslBuilder(recordClass)
        builder.block()
        traits[name] =
            TraitDefinition(
                name = name,
                attributes = builder.attributes,
                callbacks = builder.callbacks,
                transients = builder.transients,
            )
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

class TransientDslBuilder {
    private val properties = mutableMapOf<String, Any?>()

    fun set(
        key: String,
        value: Any?,
    ) {
        properties[key] = value
    }

    fun build(): TransientDefinition {
        return TransientDefinition(properties)
    }
}


inline fun <reified T : Record> factory(
    name: String? = null,
    block: FactoryDslBuilder<T>.() -> Unit,
): FactoryDefinition<T> {
    val builder = FactoryDslBuilder(T::class, name)
    builder.block()
    val definition = builder.build()
    GlobalFactoryRegistry.register(definition)
    return definition
}

inline fun <reified T : Record> DSLContext.factory(): FactoryBuilder<T> {
    val definition = GlobalFactoryRegistry.find(T::class)
    return DefaultFactoryBuilder(this, definition, GlobalSequenceManager.getInstance())
}
