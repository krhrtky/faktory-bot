package com.example.faktory.core

import org.jooq.Record
import kotlin.reflect.KClass

interface FactoryDefinition<T : Record> {
    val recordClass: KClass<T>
    val name: String?
    val parent: FactoryDefinition<T>?
    val attributes: Map<String, AttributeDefinition<*>>
    val traits: Map<String, TraitDefinition<T>>
    val callbacks: CallbackRegistry<T>
    val transients: TransientDefinition

    fun withParent(parent: FactoryDefinition<T>): FactoryDefinition<T>

    fun withAttribute(
        name: String,
        definition: AttributeDefinition<*>,
    ): FactoryDefinition<T>

    fun withTrait(
        name: String,
        trait: TraitDefinition<T>,
    ): FactoryDefinition<T>
}

data class DefaultFactoryDefinition<T : Record>(
    override val recordClass: KClass<T>,
    override val name: String? = null,
    override val parent: FactoryDefinition<T>? = null,
    override val attributes: Map<String, AttributeDefinition<*>> = emptyMap(),
    override val traits: Map<String, TraitDefinition<T>> = emptyMap(),
    override val callbacks: CallbackRegistry<T> = DefaultCallbackRegistry(),
    override val transients: TransientDefinition = TransientDefinition(),
) : FactoryDefinition<T> {
    override fun withParent(parent: FactoryDefinition<T>) = copy(parent = parent)

    override fun withAttribute(
        name: String,
        definition: AttributeDefinition<*>,
    ) = copy(attributes = attributes + (name to definition))

    override fun withTrait(
        name: String,
        trait: TraitDefinition<T>,
    ) = copy(traits = traits + (name to trait))
}
