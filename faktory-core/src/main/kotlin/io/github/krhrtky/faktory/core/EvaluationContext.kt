package io.github.krhrtky.faktory.core

import io.github.krhrtky.faktory.sequence.SequenceManager

data class EvaluationContext(
    val sequenceManager: SequenceManager,
    val attributeName: String,
    val associationResolver: AssociationResolver? = null,
    val transients: TransientContext? = null,
    val isCreate: Boolean = false,
    val depth: Int = 0,
) {
    fun withDepth(newDepth: Int) = copy(depth = newDepth)

    fun withAttributeName(name: String) = copy(attributeName = name)
}
