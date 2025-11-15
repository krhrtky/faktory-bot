package com.example.faktory.core

import org.jooq.Record

interface AssociationResolver {
    fun <T : Record> resolve(
        association: AssociationAttribute<T>,
        context: EvaluationContext,
    ): T
}
