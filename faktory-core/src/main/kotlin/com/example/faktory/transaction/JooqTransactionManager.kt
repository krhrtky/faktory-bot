package com.example.faktory.transaction

import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.sql.Connection

class JooqTransactionManager(
    private val dsl: DSLContext,
) : TransactionManager {
    private val transactionStack = ThreadLocal.withInitial { mutableListOf<Transaction>() }

    override fun <T> withRollback(block: () -> T): T {
        val stack = transactionStack.get()
        val isNested = stack.isNotEmpty()

        return if (isNested) {
            withNestedRollback(block)
        } else {
            withTopLevelRollback(block)
        }
    }

    private fun <T> withTopLevelRollback(block: () -> T): T {
        return try {
            dsl.transactionResult { _ ->
                block()
                throw RollbackException()
            }
        } catch (e: RollbackException) {
            @Suppress("UNCHECKED_CAST")
            null as T
        }
    }

    private fun <T> withNestedRollback(block: () -> T): T {
        val currentTx = transactionStack.get().last()
        val savepointName = "sp_rollback_${java.util.UUID.randomUUID()}"

        currentTx.savepoint(savepointName)

        try {
            block()
        } finally {
            try {
                currentTx.rollbackToSavepoint(savepointName)
                currentTx.releaseSavepoint(savepointName)
            } catch (e: Exception) {
            }
        }

        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    override fun <T> withTransaction(block: () -> T): T {
        val stack = transactionStack.get()
        val isNested = stack.isNotEmpty()

        return if (isNested) {
            withNestedTransaction(block)
        } else {
            withTopLevelTransaction(block)
        }
    }

    private val manualTransactionContext = ThreadLocal.withInitial<ManualTransactionContext?> { null }

    override fun begin() {
        val ctx = dsl.configuration()
        val connection =
            ctx.connectionProvider().acquire()
                ?: throw IllegalStateException("Failed to acquire connection")
        connection.autoCommit = false
        manualTransactionContext.set(ManualTransactionContext(connection, DSL.using(connection, ctx.dialect())))
    }

    override fun commit() {
        val ctx =
            manualTransactionContext.get()
                ?: throw IllegalStateException("No active transaction")
        try {
            ctx.connection.commit()
        } finally {
            ctx.connection.autoCommit = true
            dsl.configuration().connectionProvider().release(ctx.connection)
            manualTransactionContext.remove()
        }
    }

    override fun rollback() {
        val ctx =
            manualTransactionContext.get()
                ?: throw IllegalStateException("No active transaction")
        try {
            ctx.connection.rollback()
        } finally {
            ctx.connection.autoCommit = true
            dsl.configuration().connectionProvider().release(ctx.connection)
            manualTransactionContext.remove()
        }
    }

    private data class ManualTransactionContext(
        val connection: java.sql.Connection,
        val dsl: DSLContext,
    )

    override fun setIsolationLevel(level: IsolationLevel) {
        val sqlLevel =
            when (level) {
                IsolationLevel.READ_UNCOMMITTED -> Connection.TRANSACTION_READ_UNCOMMITTED
                IsolationLevel.READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED
                IsolationLevel.REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ
                IsolationLevel.SERIALIZABLE -> Connection.TRANSACTION_SERIALIZABLE
            }

        dsl.connection { conn ->
            conn.transactionIsolation = sqlLevel
        }
    }

    private fun <T> withTopLevelTransaction(block: () -> T): T {
        return dsl.transactionResult { config ->
            val tx = Transaction(DSL.using(config))
            transactionStack.get().add(tx)

            try {
                block()
            } finally {
                transactionStack.get().removeLast()
            }
        }
    }

    private fun <T> withNestedTransaction(block: () -> T): T {
        val currentTx = transactionStack.get().last()
        val savepointName = "sp_${java.util.UUID.randomUUID()}"

        currentTx.savepoint(savepointName)

        return try {
            block()
        } catch (e: Exception) {
            currentTx.rollbackToSavepoint(savepointName)
            throw e
        } finally {
            try {
                currentTx.releaseSavepoint(savepointName)
            } catch (e: Exception) {
            }
        }
    }

    private class RollbackException : RuntimeException()
}
