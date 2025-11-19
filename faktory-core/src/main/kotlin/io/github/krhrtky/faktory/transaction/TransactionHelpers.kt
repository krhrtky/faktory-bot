package io.github.krhrtky.faktory.transaction

fun <T> withFactoryTransaction(block: () -> T): T {
    return TransactionManager.getInstance().withRollback(block)
}
