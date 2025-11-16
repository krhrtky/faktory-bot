package com.example.faktory.dsl

import com.example.faktory.core.AssociationAttribute
import org.jooq.Record
import kotlin.reflect.KClass

inline fun <reified T : Record> association(
    factoryName: String? = null,
    traits: List<String> = emptyList(),
    overrides: Map<String, Any?> = emptyMap(),
): AssociationAttribute<T> = AssociationAttribute(
    targetClass = T::class,
    factoryName = factoryName,
    traits = traits,
    overrides = overrides,
)
