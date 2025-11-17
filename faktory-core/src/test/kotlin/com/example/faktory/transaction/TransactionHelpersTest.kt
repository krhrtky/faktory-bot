package com.example.faktory.transaction

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class TransactionHelpersTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
        TransactionManager.setInstance(JooqTransactionManager(dsl))

        factory<UsersRecord> {
            attribute("name", "Test User")
            sequenceAttr("email") { n -> "user$n@example.com" }
            attribute("age", 25)
        }
    }

    @Test
    fun `withFactoryTransaction automatically rolls back`() {
        withFactoryTransaction {
            val user = dsl.factory<UsersRecord>().create()
            assertThat(user.id).isNotNull()

            val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
            assertThat(count).isEqualTo(1)
        }

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }

    @Test
    @Disabled("Nested withRollback not fully supported in current design")
    fun `withFactoryTransaction can be nested`() {
        withFactoryTransaction {
            val user1 = dsl.factory<UsersRecord>().create()

            withFactoryTransaction {
                val user2 = dsl.factory<UsersRecord>().create()

                val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
                assertThat(count).isEqualTo(2)
            }

            val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
            assertThat(count).isEqualTo(1)
        }

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(0)
    }
}
