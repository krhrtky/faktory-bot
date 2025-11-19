package io.github.krhrtky.faktory.examples

import io.github.krhrtky.faktory.dsl.factory
import io.github.krhrtky.faktory.examples.jooq.tables.Posts
import io.github.krhrtky.faktory.examples.jooq.tables.Posts.Companion.POSTS
import io.github.krhrtky.faktory.examples.jooq.tables.Users
import io.github.krhrtky.faktory.examples.jooq.tables.Users.Companion.USERS
import io.github.krhrtky.faktory.examples.jooq.tables.records.PostsRecord
import io.github.krhrtky.faktory.examples.jooq.tables.records.UsersRecord
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import io.github.krhrtky.faktory.transaction.JooqTransactionManager
import io.github.krhrtky.faktory.transaction.TransactionManager
import io.github.krhrtky.faktory.transaction.withFactoryTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransactionExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
        TransactionManager.setInstance(JooqTransactionManager(dsl))
    }

    @Test
    fun `1 - Transaction automatically rolls back`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25
        }

        withFactoryTransaction {
            val user = dsl.factory<UsersRecord>().create()
            assertThat(user.id).isNotNull()
        }

        val count =
            dsl.selectCount()
                .from(Users.USERS)
                .fetchOne(0, Int::class.java)

        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `2 - Manual transaction commit with TransactionManager`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25
        }

        val userId =
            TransactionManager.getInstance().withTransaction {
                val user = dsl.factory<UsersRecord>().create()
                user.id!!
            }

        val found =
            dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.eq(userId))
                .fetchOne()

        assertThat(found).isNotNull()
    }

    @Test
    fun `3 - Create related records in transaction`() {
        factory<UsersRecord> {
            USERS.NAME set "Blogger"
            USERS.EMAIL set "blogger@example.com"
            USERS.AGE set 30
        }

        factory<PostsRecord> {
            POSTS.TITLE set sequence { n -> "Post #$n" }
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set true
        }

        val (userId, postCount) =
            TransactionManager.getInstance().withTransaction {
                val user = dsl.factory<UsersRecord>().create()

                val posts =
                    (1..3).map {
                        dsl.factory<PostsRecord>().create(
                            mapOf("user_id" to user.id!!),
                        )
                    }

                Pair(user.id!!, posts.size)
            }

        val userExists =
            dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.eq(userId))
                .fetchOne()

        assertThat(userExists).isNotNull()

        val postsCount =
            dsl.selectCount()
                .from(Posts.POSTS)
                .where(Posts.POSTS.USER_ID.eq(userId))
                .fetchOne(0, Int::class.java)

        assertThat(postsCount).isEqualTo(3)
    }
}
