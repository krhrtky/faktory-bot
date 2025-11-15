package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Posts
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.PostsRecord
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CallbackExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - afterBuild callback executes after record is built`() {
        var callbackExecuted = false

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterBuild { _, _ ->
                callbackExecuted = true
            }
        }

        dsl.factory<UsersRecord>().build()

        assertThat(callbackExecuted).isTrue()
    }

    @Test
    fun `2 - afterBuild executes for both build and create`() {
        var buildCount = 0

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterBuild { _, _ ->
                buildCount++
            }
        }

        dsl.factory<UsersRecord>().build()
        dsl.factory<UsersRecord>().create()

        assertThat(buildCount).isEqualTo(2)
    }

    @Test
    fun `3 - beforeCreate executes only for create, not build`() {
        var beforeCreateCalled = false

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            beforeCreate { _, _ ->
                beforeCreateCalled = true
            }
        }

        dsl.factory<UsersRecord>().build()
        assertThat(beforeCreateCalled).isFalse()

        dsl.factory<UsersRecord>().create()
        assertThat(beforeCreateCalled).isTrue()
    }

    @Test
    fun `4 - beforeCreate can validate before persistence`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            beforeCreate { user, _ ->
                require(user.email?.contains("@") == true) {
                    "Invalid email: ${user.email}"
                }
            }
        }

        val validUser = dsl.factory<UsersRecord>().create()
        assertThat(validUser.id).isNotNull()

        try {
            dsl.factory<UsersRecord>().create(mapOf(
                "email" to "invalid-email"
            ))
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Invalid email")
        }
    }

    @Test
    fun `5 - afterCreate executes after database insert`() {
        var afterCreateCalled = false
        var userId: Long? = null

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterCreate { user, _ ->
                afterCreateCalled = true
                userId = user.id
            }
        }

        dsl.factory<UsersRecord>().create()

        assertThat(afterCreateCalled).isTrue()
        assertThat(userId).isNotNull()
    }

    @Test
    fun `6 - afterCreate can create associations`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterCreate { user, _ ->
                factory<PostsRecord> {
                    title = "Post Title"
                    content = "Post Content"
                    sequenceAttr("userId") { _ -> user.id!! }
                }

                dsl.factory<PostsRecord>().createList(5)
            }
        }

        val user = dsl.factory<UsersRecord>().create()

        val posts = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user.id))
            .fetch()

        assertThat(posts).hasSize(5)
    }

    @Test
    fun `7 - Callback execution order - afterBuild, beforeCreate, afterCreate`() {
        val executionOrder = mutableListOf<String>()

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterBuild { _, _ ->
                executionOrder.add("afterBuild")
            }

            beforeCreate { _, _ ->
                executionOrder.add("beforeCreate")
            }

            afterCreate { _, _ ->
                executionOrder.add("afterCreate")
            }
        }

        dsl.factory<UsersRecord>().create()

        assertThat(executionOrder).containsExactly(
            "afterBuild",
            "beforeCreate",
            "afterCreate"
        )
    }

    @Test
    fun `8 - Multiple callbacks of same type execute in order`() {
        val executionOrder = mutableListOf<String>()

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterCreate { _, _ ->
                executionOrder.add("callback1")
            }

            afterCreate { _, _ ->
                executionOrder.add("callback2")
            }

            afterCreate { _, _ ->
                executionOrder.add("callback3")
            }
        }

        dsl.factory<UsersRecord>().create()

        assertThat(executionOrder).containsExactly(
            "callback1",
            "callback2",
            "callback3"
        )
    }

    @Test
    fun `9 - Callbacks execute for each record in createList`() {
        var callbackCount = 0

        factory<UsersRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25

            afterCreate { _, _ ->
                callbackCount++
            }
        }

        dsl.factory<UsersRecord>().createList(5)

        assertThat(callbackCount).isEqualTo(5)
    }

    @Test
    fun `10 - Trait callbacks execute after base callbacks`() {
        val executionOrder = mutableListOf<String>()

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterCreate { _, _ ->
                executionOrder.add("base")
            }

            trait("withCallback") {
                afterCreate { _, _ ->
                    executionOrder.add("trait")
                }
            }
        }

        dsl.factory<UsersRecord>().create("withCallback")

        assertThat(executionOrder).containsExactly("base", "trait")
    }
}
