package com.example.faktory.core

import kotlin.reflect.KClass

sealed class FactoryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

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

class MissingRequiredAttributesException(
    tableName: String,
    missingFields: List<String>,
) : FactoryException(
        "Missing required attributes for table '$tableName': ${missingFields.joinToString(", ")}",
    )

class CircularTraitReferenceException(
    traitName: String,
    visited: Set<String>,
) : FactoryException(
        "Circular trait reference detected: $traitName in ${visited.joinToString(" -> ")}",
    )

class FactoryLintException(
    val recordClass: KClass<*>,
    val factoryName: String?,
    val traitName: String?,
    cause: Exception,
) : FactoryException(
        "Factory lint failed for ${recordClass.simpleName}" +
            (factoryName?.let { " ($it)" } ?: "") +
            (traitName?.let { " with trait '$it'" } ?: "") +
            ": ${cause.message}",
        cause,
    )
