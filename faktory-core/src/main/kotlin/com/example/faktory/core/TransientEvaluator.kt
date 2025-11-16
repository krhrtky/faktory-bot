package com.example.faktory.core

import org.jooq.Record

class TransientEvaluator<T : Record> {
    fun evaluate(
        definition: FactoryDefinition<T>,
        overrides: Map<String, Any?>,
    ): TransientContext {
        val baseValues = definition.transients.properties
        val mergedValues = baseValues + overrides.filterKeys { it in baseValues }

        return TransientContext(mergedValues)
    }

    fun evaluateFrom(
        transients: TransientDefinition,
        overrides: Map<String, Any?>,
    ): TransientContext {
        val baseValues = transients.properties
        val mergedValues = baseValues + overrides.filterKeys { it in baseValues }

        return TransientContext(mergedValues)
    }
}
