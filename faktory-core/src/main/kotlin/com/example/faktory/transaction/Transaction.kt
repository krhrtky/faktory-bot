package com.example.faktory.transaction

import org.jooq.DSLContext
import java.sql.Savepoint
import java.util.UUID

data class Transaction(
    val dsl: DSLContext,
    val id: String = UUID.randomUUID().toString(),
) {
    private val savepoints = mutableMapOf<String, Savepoint>()

    fun savepoint(name: String) {
        dsl.connection { conn ->
            savepoints[name] = conn.setSavepoint(name)
        }
    }

    fun rollbackToSavepoint(name: String) {
        val savepoint =
            savepoints[name]
                ?: throw IllegalArgumentException("Savepoint not found: $name")

        dsl.connection { conn ->
            conn.rollback(savepoint)
        }
    }

    fun releaseSavepoint(name: String) {
        val savepoint =
            savepoints.remove(name)
                ?: throw IllegalArgumentException("Savepoint not found: $name")

        dsl.connection { conn ->
            conn.releaseSavepoint(savepoint)
        }
    }
}
