package com.example.faktory.transaction

interface TransactionManager {
    fun <T> withRollback(block: () -> T): T

    fun <T> withTransaction(block: () -> T): T

    fun begin()

    fun commit()

    fun rollback()

    fun setIsolationLevel(level: IsolationLevel)

    companion object {
        private var instance: TransactionManager? = null

        fun getInstance(): TransactionManager {
            return instance ?: throw IllegalStateException("TransactionManager not initialized")
        }

        fun setInstance(manager: TransactionManager) {
            instance = manager
        }

        fun clear() {
            instance = null
        }
    }
}
