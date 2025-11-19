package io.github.krhrtky.faktory.dsl

import io.github.krhrtky.faktory.builder.DefaultFactoryBuilder
import io.github.krhrtky.faktory.builder.FactoryBuilder
import io.github.krhrtky.faktory.core.AssociationAttribute
import io.github.krhrtky.faktory.core.AttributeDefinition
import io.github.krhrtky.faktory.core.CallbackPhase
import io.github.krhrtky.faktory.core.DefaultCallbackRegistry
import io.github.krhrtky.faktory.core.DefaultFactoryDefinition
import io.github.krhrtky.faktory.core.DynamicAttribute
import io.github.krhrtky.faktory.core.EvaluationContext
import io.github.krhrtky.faktory.core.FactoryDefinition
import io.github.krhrtky.faktory.core.SequenceAttribute
import io.github.krhrtky.faktory.core.StaticAttribute
import io.github.krhrtky.faktory.core.TraitDefinition
import io.github.krhrtky.faktory.core.TransientContext
import io.github.krhrtky.faktory.core.TransientDefinition
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import kotlin.reflect.KClass

class FactoryDslBuilder<T : Record>(
    private val recordClass: KClass<T>,
    private val factoryName: String? = null,
    private val defaultTraits: List<String> = emptyList(),
) {
    internal val attributes = mutableMapOf<String, AttributeDefinition<*>>()
    internal val traits = mutableMapOf<String, TraitDefinition<T>>()
    internal val callbacks = DefaultCallbackRegistry<T>()
    internal var transients = TransientDefinition()
    private val includedTraits = mutableListOf<String>()

    infix fun <R : Record, V> TableField<R, V>.set(value: V) {
        attributes[this.name] = StaticAttribute(value)
    }

    infix fun <R : Record, V> TableField<R, V>.set(value: SequenceAttribute<V>) {
        attributes[this.name] = value
    }

    infix fun <R : Record, V> TableField<R, V>.set(value: DynamicAttribute<V>) {
        attributes[this.name] = value
    }

    infix fun <R : Record, V> TableField<R, V>.set(value: AssociationAttribute<*>) {
        attributes[this.name] = value
    }

    fun <V> sequence(generator: (Int) -> V): SequenceAttribute<V> {
        return SequenceAttribute(null, generator)
    }

    inline fun <reified A : Record> association(
        factoryName: String? = null,
        traits: List<String> = emptyList(),
        overrides: Map<String, Any?> = emptyMap(),
    ): AssociationAttribute<A> {
        return AssociationAttribute(
            targetClass = A::class,
            factoryName = factoryName,
            traits = traits,
            overrides = overrides,
        )
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

    @Deprecated(
        message = "Use type-safe TableField syntax instead: USERS.NAME set \"value\"",
        replaceWith = ReplaceWith("/* Use TableField.set() instead */"),
        level = DeprecationLevel.WARNING,
    )
    fun attribute(
        name: String,
        value: Any?,
    ) {
        attributes[name] = StaticAttribute(value)
    }

    @Deprecated(
        message = "Use type-safe TableField syntax instead: USERS.NAME set { \"value\" }",
        replaceWith = ReplaceWith("/* Use TableField.set() with lambda instead */"),
        level = DeprecationLevel.WARNING,
    )
    fun attribute(
        name: String,
        generator: (EvaluationContext) -> Any?,
    ) {
        attributes[name] = DynamicAttribute(generator)
    }

    @Deprecated(
        message =
            "Use type-safe TableField syntax instead: " +
                "USERS.EMAIL set sequence { n -> \"user\${n}@example.com\" }",
        replaceWith = ReplaceWith("/* Use TableField.set(sequence { ... }) instead */"),
        level = DeprecationLevel.WARNING,
    )
    fun sequenceAttr(
        name: String,
        generator: (Int) -> Any?,
    ) {
        attributes[name] = SequenceAttribute(name, generator)
    }

    fun includeTrait(traitName: String) {
        includedTraits.add(traitName)
    }

    fun trait(
        trait: io.github.krhrtky.faktory.core.Trait<T>,
        block: FactoryDslBuilder<T>.() -> Unit,
    ) {
        val builder = FactoryDslBuilder(recordClass)
        builder.block()
        traits[trait.name] =
            TraitDefinition(
                name = trait.name,
                attributes = builder.attributes,
                callbacks = builder.callbacks,
                transients = builder.transients,
                includedTraits = builder.includedTraits.toList(),
            )
    }

    fun build(): FactoryDefinition<T> {
        return DefaultFactoryDefinition(
            recordClass = recordClass,
            name = factoryName,
            defaultTraits = defaultTraits,
            attributes = attributes,
            traits = traits,
            callbacks = callbacks,
            transients = transients,
        )
    }

    internal fun buildGlobalTrait(name: String): TraitDefinition<T> {
        return TraitDefinition(
            name = name,
            attributes = attributes,
            callbacks = callbacks,
            transients = transients,
            includedTraits = includedTraits.toList(),
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
    defaultTraits: List<String> = emptyList(),
    block: FactoryDslBuilder<T>.() -> Unit,
): FactoryDefinition<T> {
    val builder = FactoryDslBuilder(T::class, name, defaultTraits)
    builder.block()
    val definition = builder.build()
    GlobalFactoryRegistry.register(definition)
    return definition
}

inline fun <reified T : Record> DSLContext.factory(): FactoryBuilder<T> {
    val definition = GlobalFactoryRegistry.find(T::class)
    return DefaultFactoryBuilder(this, definition, GlobalSequenceManager.getInstance())
}

fun <T : Record> globalTrait(
    recordClass: KClass<T>,
    name: String,
    block: FactoryDslBuilder<T>.() -> Unit,
) {
    val builder = FactoryDslBuilder(recordClass)
    builder.block()
    val trait = builder.buildGlobalTrait(name)
    io.github.krhrtky.faktory.registry.GlobalTraitRegistry.register(name, trait)
}
