package com.example.faktory.core

import kotlin.reflect.KClass

sealed class FactoryException(message: String) : RuntimeException(message)

class FactoryNotFoundException(
    recordClass: KClass<*>,
    name: String?,
) : FactoryException(
        "Factory not found for ${recordClass.simpleName}" +
            (name?.let { " with name '$it'" } ?: ""),
    )

class CircularAssociationException(
    chain: List<KClass<*>>,
) : FactoryException(
        "Circular association detected: ${chain.joinToString(" -> ") { it.simpleName ?: "Unknown" }}",
    )

class InvalidAttributeException(
    attributeName: String,
    reason: String,
) : FactoryException(
        "Invalid attribute '$attributeName': $reason",
    )

class TraitNotFoundException(
    traitName: String,
) : FactoryException(
        "Trait '$traitName' not found",
    )
