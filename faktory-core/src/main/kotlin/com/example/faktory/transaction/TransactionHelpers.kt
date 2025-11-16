package com.example.faktory.transaction

fun <T> withFactoryTransaction(block: () -> T): T {
    return TransactionManager.getInstance().withRollback(block)
}
