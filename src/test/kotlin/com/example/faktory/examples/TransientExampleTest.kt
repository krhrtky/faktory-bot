package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Posts
import com.example.faktory.test.jooq.tables.records.PostsRecord
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransientExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Transients control callback behavior`() {
        var postsCountFromCallback: Int? = null

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 5)
            }

            afterCreate { _, transients ->
                postsCountFromCallback = transients.getOrNull("postsCount") as? Int
            }
        }

        val user = dsl.factory<UsersRecord>().create()

        assertThat(postsCountFromCallback).isEqualTo(5)
        assertThat(user.name).isEqualTo("User")
    }

    @Test
    fun `2 - Transients can be overridden`() {
        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 5)
            }

            afterCreate { user, transients ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> user.id!! }
                }

                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostsRecord>().create()
                }
            }
        }

        val user1 = dsl.factory<UsersRecord>().create()
        val posts1 = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user1.id))
            .fetch()
        assertThat(posts1).hasSize(5)

        GlobalFactoryRegistry.clear()
        GlobalSequenceManager.reset()

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 5)
            }

            afterCreate { user, transients ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> user.id!! }
                }

                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostsRecord>().create()
                }
            }
        }

        val user2 = dsl.factory<UsersRecord>().create(mapOf(
            "postsCount" to 10
        ))
        val posts2 = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user2.id))
            .fetch()
        assertThat(posts2).hasSize(10)
    }

    @Test
    fun `3 - Conditional behavior with transients`() {
        var emailSent = false

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("sendWelcomeEmail", false)
            }

            afterCreate { _, transients ->
                val sendEmail = transients.getOrNull("sendWelcomeEmail") as? Boolean ?: false
                if (sendEmail) {
                    emailSent = true
                }
            }
        }

        dsl.factory<UsersRecord>().create()
        assertThat(emailSent).isFalse()

        dsl.factory<UsersRecord>().create(mapOf(
            "sendWelcomeEmail" to true
        ))
        assertThat(emailSent).isTrue()
    }

    @Test
    fun `4 - Traits can override transients`() {
        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 0)
            }

            trait("withPosts") {
                transient {
                    set("postsCount", 5)
                }
            }

            trait("withManyPosts") {
                transient {
                    set("postsCount", 50)
                }
            }

            afterCreate { user, transients ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> user.id!! }
                }

                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostsRecord>().create()
                }
            }
        }

        val user1 = dsl.factory<UsersRecord>().create()
        val posts1 = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user1.id))
            .fetch()
        assertThat(posts1).hasSize(0)

        GlobalFactoryRegistry.clear()
        GlobalSequenceManager.reset()

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 0)
            }

            trait("withPosts") {
                transient {
                    set("postsCount", 5)
                }
            }

            trait("withManyPosts") {
                transient {
                    set("postsCount", 50)
                }
            }

            afterCreate { user, transients ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> user.id!! }
                }

                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostsRecord>().create()
                }
            }
        }

        val user2 = dsl.factory<UsersRecord>().create("withPosts")
        val posts2 = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user2.id))
            .fetch()
        assertThat(posts2).hasSize(5)

        GlobalFactoryRegistry.clear()
        GlobalSequenceManager.reset()

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 0)
            }

            trait("withPosts") {
                transient {
                    set("postsCount", 5)
                }
            }

            trait("withManyPosts") {
                transient {
                    set("postsCount", 50)
                }
            }

            afterCreate { user, transients ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> user.id!! }
                }

                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostsRecord>().create()
                }
            }
        }

        val user3 = dsl.factory<UsersRecord>().create("withManyPosts")
        val posts3 = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user3.id))
            .fetch()
        assertThat(posts3).hasSize(50)
    }

    @Test
    fun `5 - Multiple transient values`() {
        var intValue: Int? = null
        var stringValue: String? = null
        var boolValue: Boolean? = null

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("intValue", 42)
                set("stringValue", "test")
                set("boolValue", true)
            }

            afterCreate { _, transients ->
                intValue = transients.getOrNull("intValue") as? Int
                stringValue = transients.getOrNull("stringValue") as? String
                boolValue = transients.getOrNull("boolValue") as? Boolean
            }
        }

        dsl.factory<UsersRecord>().create()

        assertThat(intValue).isEqualTo(42)
        assertThat(stringValue).isEqualTo("test")
        assertThat(boolValue).isTrue()
    }

    @Test
    fun `6 - Transients provide defaults in callbacks`() {
        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("postsCount", 3)
            }

            afterCreate { user, transients ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> user.id!! }
                }

                val count = transients.getOrNull("postsCount") as? Int ?: 0
                repeat(count) {
                    dsl.factory<PostsRecord>().create()
                }
            }
        }

        val user = dsl.factory<UsersRecord>().create()
        val posts = dsl.selectFrom(Posts.POSTS)
            .where(Posts.POSTS.USER_ID.eq(user.id))
            .fetch()

        assertThat(posts).hasSize(3)
    }

    @Test
    fun `7 - Transients are not persisted to database`() {
        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("metadata", "test-data")
            }
        }

        val user = dsl.factory<UsersRecord>().create()

        assertThat(user.id).isNotNull()
        assertThat(user.name).isEqualTo("User")
    }

    @Test
    fun `8 - Transients work with all callbacks`() {
        val executionLog = mutableListOf<String>()

        factory<UsersRecord> {
            attribute("name", "User")
            attribute("email", "user@example.com")
            attribute("age", 25)

            transient {
                set("metadata", "test")
            }

            afterBuild { _, transients ->
                val metadata = transients.getOrNull("metadata") as? String
                executionLog.add("afterBuild: $metadata")
            }

            beforeCreate { _, transients ->
                val metadata = transients.getOrNull("metadata") as? String
                executionLog.add("beforeCreate: $metadata")
            }

            afterCreate { _, transients ->
                val metadata = transients.getOrNull("metadata") as? String
                executionLog.add("afterCreate: $metadata")
            }
        }

        dsl.factory<UsersRecord>().create()

        assertThat(executionLog).containsExactly(
            "afterBuild: test",
            "beforeCreate: test",
            "afterCreate: test"
        )
    }
}
