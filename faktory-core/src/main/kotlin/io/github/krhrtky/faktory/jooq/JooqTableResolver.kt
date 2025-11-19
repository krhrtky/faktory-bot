package io.github.krhrtky.faktory.jooq

import org.jooq.Record
import org.jooq.Table
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties

object JooqTableResolver {
    fun <T : Record> resolveTable(recordClass: KClass<T>): Table<T> {
        val recordClassName =
            recordClass.simpleName
                ?: throw IllegalArgumentException("Cannot resolve table for anonymous class")

        if (!recordClassName.endsWith("Record")) {
            throw IllegalArgumentException("Class $recordClassName is not a jOOQ Record class")
        }

        val tableName = recordClassName.removeSuffix("Record")
        val recordPackage = recordClass.qualifiedName?.substringBeforeLast(".") ?: ""

        if (!recordPackage.endsWith(".records")) {
            throw IllegalArgumentException("Class $recordClassName is not in a .records package")
        }

        val tablePackage = recordPackage.removeSuffix(".records")
        val tableClassName = "$tablePackage.$tableName"

        val tableClass =
            try {
                Class.forName(tableClassName).kotlin
            } catch (e: ClassNotFoundException) {
                throw IllegalArgumentException("Table class $tableClassName not found for record $recordClassName", e)
            }

        val companion =
            tableClass.companionObject
                ?: throw IllegalArgumentException("Table class $tableClassName has no companion object")

        val companionInstance =
            companion.objectInstance
                ?: throw IllegalArgumentException("Cannot get companion object instance for $tableClassName")

        val tableInstance =
            companion.memberProperties
                .firstOrNull { it.name == tableName.uppercase() }
                ?.getter
                ?.call(companionInstance)
                ?: throw IllegalArgumentException(
                    "Table instance ${tableName.uppercase()} not found in $tableClassName companion object",
                )

        @Suppress("UNCHECKED_CAST")
        return tableInstance as Table<T>
    }
}
