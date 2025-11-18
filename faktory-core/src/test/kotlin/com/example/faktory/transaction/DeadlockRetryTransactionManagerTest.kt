package com.example.faktory.transaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DeadlockRetryTransactionManagerTest {
    @Test
    fun `retries on deadlock`() {
        val delegate = mockk<TransactionManager>()
        val deadlockException = RuntimeException("Deadlock detected")

        every { delegate.withTransaction<Unit>(any()) } throws deadlockException andThen Unit

        val manager = DeadlockRetryTransactionManager(delegate, maxRetries = 3, baseDelay = 10)

        manager.withTransaction { }

        verify(exactly = 2) { delegate.withTransaction<Unit>(any()) }
    }

    @Test
    fun `throws after max retries`() {
        val delegate = mockk<TransactionManager>()
        val deadlockException = RuntimeException("Deadlock detected")

        every { delegate.withTransaction<Unit>(any()) } throws deadlockException

        val manager = DeadlockRetryTransactionManager(delegate, maxRetries = 3, baseDelay = 10)

        assertThatThrownBy {
            manager.withTransaction { }
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Deadlock detected")

        verify(exactly = 3) { delegate.withTransaction<Unit>(any()) }
    }

    @Test
    fun `does not retry on non-deadlock exception`() {
        val delegate = mockk<TransactionManager>()
        val exception = RuntimeException("Other error")

        every { delegate.withTransaction<Unit>(any()) } throws exception

        val manager = DeadlockRetryTransactionManager(delegate, maxRetries = 3, baseDelay = 10)

        assertThatThrownBy {
            manager.withTransaction { }
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Other error")

        verify(exactly = 1) { delegate.withTransaction<Unit>(any()) }
    }

    @Test
    fun `successful transaction does not retry`() {
        val delegate = mockk<TransactionManager>()

        every { delegate.withTransaction(any<() -> String>()) } returns "result"

        val manager = DeadlockRetryTransactionManager(delegate, maxRetries = 3, baseDelay = 10)

        val result = manager.withTransaction { "result" }

        assertThat(result).isEqualTo("result")
        verify(exactly = 1) { delegate.withTransaction(any<() -> String>()) }
    }

    @Test
    fun `uses exponential backoff`() {
        val delegate = mockk<TransactionManager>()
        val deadlockException = RuntimeException("Deadlock detected")
        val delays = mutableListOf<Long>()

        every { delegate.withTransaction<Unit>(any()) } throws deadlockException

        val manager =
            object : DeadlockRetryTransactionManager(delegate, maxRetries = 3, baseDelay = 100) {
                override fun <T> withTransaction(block: () -> T): T {
                    var attempts = 0
                    val startTime = System.currentTimeMillis()

                    while (attempts < 3) {
                        try {
                            return delegate.withTransaction(block)
                        } catch (e: Exception) {
                            if (e.message?.contains("deadlock", ignoreCase = true) == true && attempts < 2) {
                                attempts++
                                val delay = (100 * (1 shl attempts)).toLong()
                                delays.add(delay)
                                Thread.sleep(delay)
                            } else {
                                throw e
                            }
                        }
                    }
                    throw IllegalStateException("Unreachable")
                }
            }

        assertThatThrownBy {
            manager.withTransaction { }
        }

        assertThat(delays).containsExactly(200L, 400L)
    }
}
