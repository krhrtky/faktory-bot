package io.github.krhrtky.faktory.jooq

import io.github.krhrtky.faktory.core.MissingRequiredAttributesException
import org.jooq.Record
import org.jooq.Table
import org.jooq.TableField

object RequiredAttributeValidator {
    fun <T : Record> validateRequiredAttributes(
        table: Table<T>,
        providedAttributes: Set<String>,
    ) {
        val requiredFields = getRequiredFields(table)
        val missingFields = requiredFields - providedAttributes

        if (missingFields.isNotEmpty()) {
            throw MissingRequiredAttributesException(
                table.name,
                missingFields.toList(),
            )
        }
    }

    private fun <T : Record> getRequiredFields(table: Table<T>): Set<String> {
        return table.fields()
            .asSequence()
            .filterIsInstance<TableField<T, *>>()
            .filter { field ->
                val dataType = field.dataType
                !dataType.nullable() && field.name != "id"
            }
            .map { it.name }
            .toSet()
    }
}
