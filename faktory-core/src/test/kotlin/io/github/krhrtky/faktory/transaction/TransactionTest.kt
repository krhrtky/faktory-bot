package io.github.krhrtky.faktory.transaction

import io.github.krhrtky.faktory.test.JooqTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TransactionTest : JooqTestBase() {
    @Test
    fun `transaction has unique id`() {
        val tx1 = Transaction(dsl)
        val tx2 = Transaction(dsl)

        assertThat(tx1.id).isNotEqualTo(tx2.id)
    }

    @Test
    fun `savepoint creates savepoint`() {
        dsl.transaction { config ->
            val txDsl = org.jooq.impl.DSL.using(config)
            val tx = Transaction(txDsl)

            tx.savepoint("sp1")

            txDsl.connection { conn ->
                assertThat(conn.autoCommit).isFalse()
            }
        }
    }

    @Test
    fun `rollbackToSavepoint rolls back to savepoint`() {
        dsl.transaction { config ->
            val txDsl = org.jooq.impl.DSL.using(config)
            val tx = Transaction(txDsl)

            txDsl.execute("INSERT INTO users (name, email, age) VALUES ('User1', 'user1@example.com', 25)")
            val countBefore = txDsl.fetchCount(io.github.krhrtky.faktory.test.jooq.tables.Users.USERS)

            tx.savepoint("sp1")

            txDsl.execute("INSERT INTO users (name, email, age) VALUES ('User2', 'user2@example.com', 30)")
            val countAfter = txDsl.fetchCount(io.github.krhrtky.faktory.test.jooq.tables.Users.USERS)

            tx.rollbackToSavepoint("sp1")

            val countAfterRollback = txDsl.fetchCount(io.github.krhrtky.faktory.test.jooq.tables.Users.USERS)

            assertThat(countBefore).isEqualTo(1)
            assertThat(countAfter).isEqualTo(2)
            assertThat(countAfterRollback).isEqualTo(1)
        }
    }

    @Test
    fun `rollbackToSavepoint throws on unknown savepoint`() {
        dsl.transaction { config ->
            val txDsl = org.jooq.impl.DSL.using(config)
            val tx = Transaction(txDsl)

            assertThatThrownBy {
                tx.rollbackToSavepoint("unknown")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Savepoint not found: unknown")
        }
    }

    @Test
    fun `releaseSavepoint releases savepoint`() {
        dsl.transaction { config ->
            val txDsl = org.jooq.impl.DSL.using(config)
            val tx = Transaction(txDsl)

            tx.savepoint("sp1")
            tx.releaseSavepoint("sp1")

            assertThatThrownBy {
                tx.rollbackToSavepoint("sp1")
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `releaseSavepoint throws on unknown savepoint`() {
        dsl.transaction { config ->
            val txDsl = org.jooq.impl.DSL.using(config)
            val tx = Transaction(txDsl)

            assertThatThrownBy {
                tx.releaseSavepoint("unknown")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Savepoint not found: unknown")
        }
    }
}
