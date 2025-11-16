package com.example.faktory.integration

import com.example.faktory.core.*
import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Posts
import com.example.faktory.test.jooq.tables.Posts.Companion.POSTS
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.Users.Companion.USERS
import com.example.faktory.test.jooq.tables.records.PostsRecord
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Phase3IntegrationTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `factory with sequence generates unique values`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 25
        }

        val user1 = dsl.factory<UsersRecord>().build()
        val user2 = dsl.factory<UsersRecord>().build()
        val user3 = dsl.factory<UsersRecord>().build()

        assertThat(user1.email).matches("user\\d+@example.com")
        assertThat(user2.email).matches("user\\d+@example.com")
        assertThat(user3.email).matches("user\\d+@example.com")
        assertThat(setOf(user1.email, user2.email, user3.email)).hasSize(3)
    }

    @Test
    fun `factory with association builds related records`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 30
        }

        val user = dsl.factory<UsersRecord>().create()

        factory<PostsRecord> {
            POSTS.USER_ID set user.id
            POSTS.TITLE set "Post Title"
            POSTS.CONTENT set "Post Content"
        }

        val post = dsl.factory<PostsRecord>().build()

        assertThat(post.title).isEqualTo("Post Title")
        assertThat(post.content).isEqualTo("Post Content")
        assertThat(post.userId).isEqualTo(user.id)
    }

    @Test
    fun `transients control callback behavior`() {
        var postsCountFromCallback: Int? = null

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

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

        val count = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `callbacks receive transient context`() {
        var beforeCreateCalled = false
        var afterCreateCalled = false
        var afterBuildCalled = false
        var metadataFromBuild: String? = null
        var metadataFromBeforeCreate: String? = null
        var metadataFromAfterCreate: String? = null

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            transient {
                set("metadata", "test-data")
            }

            afterBuild { _, transients ->
                afterBuildCalled = true
                metadataFromBuild = transients.getOrNull("metadata") as? String
            }

            beforeCreate { _, transients ->
                beforeCreateCalled = true
                metadataFromBeforeCreate = transients.getOrNull("metadata") as? String
            }

            afterCreate { _, transients ->
                afterCreateCalled = true
                metadataFromAfterCreate = transients.getOrNull("metadata") as? String
            }
        }

        val user = dsl.factory<UsersRecord>().create()

        assertThat(beforeCreateCalled).isTrue()
        assertThat(afterCreateCalled).isTrue()
        assertThat(afterBuildCalled).isTrue()
        assertThat(metadataFromBuild).isEqualTo("test-data")
        assertThat(metadataFromBeforeCreate).isEqualTo("test-data")
        assertThat(metadataFromAfterCreate).isEqualTo("test-data")
        assertThat(user.name).isEqualTo("User")
    }

    @Test
    fun `sequence is reset between tests`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 25
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.email).isEqualTo("user1@example.com")
    }
}
