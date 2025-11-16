package com.example.faktory.transaction

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JooqTransactionManagerTest : JooqTestBase() {

    private lateinit var transactionManager: JooqTransactionManager

    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
        transactionManager = JooqTransactionManager(dsl)
        TransactionManager.setInstance(transactionManager)

        factory<UsersRecord> {
            attribute("name", "Test User")
            sequenceAttr("email") { n -> "user${n}@example.com" }
            attribute("age", 25)
        }
    }

    @Test
    fun `withRollback automatically rolls back transaction`() {
        transactionManager.withRollback {
            val user = dsl.factory<UsersRecord>().create()
            assertThat(user.id).isNotNull()

            val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
            assertThat(count).isEqualTo(1)
        }

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `withTransaction commits on success`() {
        transactionManager.withTransaction {
            val user = dsl.factory<UsersRecord>().create()
            assertThat(user.id).isNotNull()
        }

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `withTransaction rolls back on exception`() {
        assertThatThrownBy {
            transactionManager.withTransaction {
                dsl.factory<UsersRecord>().create()
                throw RuntimeException("Test exception")
            }
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Test exception")

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `nested transactions use savepoints`() {
        transactionManager.withTransaction {
            val user1 = dsl.factory<UsersRecord>().create()
            assertThat(user1.id).isNotNull()

            assertThatThrownBy {
                transactionManager.withTransaction {
                    val user2 = dsl.factory<UsersRecord>().create()
                    assertThat(user2.id).isNotNull()
                    throw RuntimeException("Nested transaction error")
                }
            }.isInstanceOf(RuntimeException::class.java)

            val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
            assertThat(count).isEqualTo(1)
        }
    }

    @Test
    fun `nested withRollback does not affect outer transaction`() {
        var innerUserId: Long? = null
        var outerUserId: Long? = null

        transactionManager.withTransaction {
            val outerUser = dsl.factory<UsersRecord>().create()
            outerUserId = outerUser.id

            transactionManager.withRollback {
                val innerUser = dsl.factory<UsersRecord>().create()
                innerUserId = innerUser.id

                val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
                assertThat(count).isEqualTo(2)
            }

            val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
            assertThat(count).isEqualTo(1)
        }

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `setIsolationLevel changes transaction isolation level`() {
        transactionManager.setIsolationLevel(IsolationLevel.SERIALIZABLE)

        transactionManager.withTransaction {
            dsl.connection { conn ->
                assertThat(conn.transactionIsolation)
                    .isEqualTo(java.sql.Connection.TRANSACTION_SERIALIZABLE)
            }
        }
    }

    @Test
    fun `begin commit rollback work correctly`() {
        transactionManager.begin()
        val user = dsl.factory<UsersRecord>().create()
        transactionManager.commit()

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `begin rollback discards changes`() {
        transactionManager.begin()
        dsl.factory<UsersRecord>().create()
        transactionManager.rollback()

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }
}
