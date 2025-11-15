package com.example.faktory.core

import com.example.faktory.sequence.SequenceManager

data class EvaluationContext(
    val sequenceManager: SequenceManager,
    val associationResolver: AssociationResolver,
    val transients: TransientContext,
    val attributeName: String,
    val isCreate: Boolean = false,
    val depth: Int = 0,
) {
    fun withDepth(newDepth: Int) = copy(depth = newDepth)

    fun withAttributeName(name: String) = copy(attributeName = name)
}
