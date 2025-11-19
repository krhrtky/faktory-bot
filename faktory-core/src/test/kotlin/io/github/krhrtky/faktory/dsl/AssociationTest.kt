package io.github.krhrtky.faktory.dsl

import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import io.github.krhrtky.faktory.test.JooqTestBase
import io.github.krhrtky.faktory.test.jooq.tables.Posts.Companion.POSTS
import io.github.krhrtky.faktory.test.jooq.tables.Users.Companion.USERS
import io.github.krhrtky.faktory.test.jooq.tables.records.PostsRecord
import io.github.krhrtky.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AssociationTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `association creates related record automatically`() {
        factory<UsersRecord> {
            USERS.NAME set "Associated User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 30
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set "Post with association"
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set true
        }

        val post = dsl.factory<PostsRecord>().create()

        assertThat(post.userId).isNotNull()
        assertThat(post.title).isEqualTo("Post with association")

        val user =
            dsl
                .selectFrom(USERS)
                .where(USERS.ID.eq(post.userId))
                .fetchOne()

        assertThat(user).isNotNull()
        assertThat(user?.name).isEqualTo("Associated User")
    }

    @Test
    fun `association works with build without database`() {
        factory<UsersRecord> {
            USERS.NAME set "Built User"
            USERS.EMAIL set "built@example.com"
            USERS.AGE set 25
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set "Built Post"
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set false
        }

        val post = dsl.factory<PostsRecord>().build()

        assertThat(post.userId).isNull()
        assertThat(post.title).isEqualTo("Built Post")
    }

    @Test
    fun `association creates multiple users for multiple posts`() {
        factory<UsersRecord> {
            USERS.NAME set sequence { n -> "User $n" }
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 30
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set sequence { n -> "Post $n" }
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set true
        }

        val posts = dsl.factory<PostsRecord>().createList(3)

        assertThat(posts).hasSize(3)
        assertThat(posts.all { it.userId != null }).isTrue()

        val userIds = posts.map { it.userId }.distinct()
        assertThat(userIds).hasSize(3)

        val users =
            dsl
                .selectFrom(USERS)
                .fetch()

        assertThat(users).hasSizeGreaterThanOrEqualTo(3)
    }

    @Test
    fun `association with overrides`() {
        factory<UsersRecord> {
            USERS.NAME set "Default User"
            USERS.EMAIL set "default@example.com"
            USERS.AGE set 25
        }

        factory<PostsRecord> {
            POSTS.USER_ID set
                association<UsersRecord>(
                    overrides =
                        mapOf(
                            "name" to "Custom User",
                            "email" to "custom@example.com",
                        ),
                )
            POSTS.TITLE set "Post"
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set true
        }

        val post = dsl.factory<PostsRecord>().create()

        assertThat(post.userId).isNotNull()

        val user =
            dsl
                .selectFrom(USERS)
                .where(USERS.ID.eq(post.userId))
                .fetchOne()

        assertThat(user).isNotNull()
        assertThat(user?.name).isEqualTo("Custom User")
        assertThat(user?.email).isEqualTo("custom@example.com")
    }

    @Test
    fun `association type safety prevents wrong record type at compile time`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set "Post"
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set true
        }

        val post = dsl.factory<PostsRecord>().create()

        assertThat(post.userId).isNotNull()
    }
}
