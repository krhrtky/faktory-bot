package io.github.krhrtky.faktory.transaction

open class DeadlockRetryTransactionManager(
    private val delegate: TransactionManager,
    private val maxRetries: Int = 3,
    private val baseDelay: Long = 100,
) : TransactionManager by delegate {
    open override fun <T> withTransaction(block: () -> T): T {
        var attempts = 0

        while (attempts < maxRetries) {
            try {
                return delegate.withTransaction(block)
            } catch (e: Exception) {
                if (!isDeadlock(e) || attempts >= maxRetries - 1) {
                    throw e
                }

                attempts++
                val delay = baseDelay * (1 shl attempts)
                Thread.sleep(delay)
            }
        }

        throw IllegalStateException("Unreachable")
    }

    private fun isDeadlock(e: Exception): Boolean {
        return e.message?.contains("deadlock", ignoreCase = true) == true
    }
}
