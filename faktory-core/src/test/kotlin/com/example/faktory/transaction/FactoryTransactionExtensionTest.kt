package com.example.faktory.transaction

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FactoryTransactionExtension::class)
class FactoryTransactionExtensionTest {
    @Test
    @Disabled("JUnit Extension requires TransactionManager to be set before test execution")
    fun `extension integration test`() {
        val manager = mockk<TransactionManager>(relaxed = true)
        TransactionManager.setInstance(manager)

        every { manager.begin() } returns Unit
        every { manager.rollback() } returns Unit
    }
}
