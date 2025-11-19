package io.github.krhrtky.faktory.examples

import io.github.krhrtky.faktory.dsl.factory
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import io.github.krhrtky.faktory.test.JooqTestBase
import io.github.krhrtky.faktory.test.jooq.tables.Posts
import io.github.krhrtky.faktory.test.jooq.tables.Posts.Companion.POSTS
import io.github.krhrtky.faktory.test.jooq.tables.Users
import io.github.krhrtky.faktory.test.jooq.tables.Users.Companion.USERS
import io.github.krhrtky.faktory.test.jooq.tables.records.PostsRecord
import io.github.krhrtky.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AdvancedExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Custom date sequence generates progressive dates`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25
        }

        val user = dsl.factory<UsersRecord>().create()

        factory<PostsRecord> {
            POSTS.TITLE set "Post"
            POSTS.CONTENT set "Content"
            POSTS.USER_ID set sequence { _ -> user.id!! }

            POSTS.CREATED_AT set
                sequence { n ->
                    LocalDateTime.now().plusDays(n.toLong())
                }
        }

        val posts =
            (1..3).map {
                dsl.factory<PostsRecord>().create()
            }

        assertThat(posts[0].createdAt).isNotNull()
        assertThat(posts[1].createdAt).isAfter(posts[0].createdAt)
        assertThat(posts[2].createdAt).isAfter(posts[1].createdAt)
    }

    @Test
    fun `2 - Dynamic attribute with context`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }

            USERS.AGE set
                sequence { n ->
                    20 + (n % 50)
                }
        }

        val users = dsl.factory<UsersRecord>().buildList(5)

        assertThat(users.all { it.age in 20..70 }).isTrue()
    }

    @Test
    fun `3 - Performance - createList is faster than multiple creates`() {
        factory<UsersRecord> {
            attribute("name", "User")
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25
        }

        val count = 50

        val start1 = System.currentTimeMillis()
        repeat(count) {
            dsl.factory<UsersRecord>().create()
        }
        val time1 = System.currentTimeMillis() - start1

        GlobalSequenceManager.reset()
        dsl.deleteFrom(Users.USERS).execute()

        val start2 = System.currentTimeMillis()
        dsl.factory<UsersRecord>().createList(count)
        val time2 = System.currentTimeMillis() - start2

        println("Individual creates: ${time1}ms")
        println("createList: ${time2}ms")
        println("Speedup: ${time1.toDouble() / time2.toDouble()}x")

        assertThat(time2).isLessThan(time1)
    }

    @Test
    fun `4 - Disable callbacks for bulk operations`() {
        var callbackCount = 0

        factory<UsersRecord> {
            attribute("name", "User")
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25

            transient {
                set("skipCallbacks", false)
            }

            afterCreate { _, transients ->
                val skip = transients.getOrNull("skipCallbacks") as? Boolean ?: false
                if (!skip) {
                    callbackCount++
                }
            }
        }

        dsl.factory<UsersRecord>().createList(
            100,
            mapOf(
                "skipCallbacks" to true,
            ),
        )

        assertThat(callbackCount).isEqualTo(0)
    }

    @Test
    fun `5 - One-to-many association with afterCreate`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

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

        val user = dsl.factory<UsersRecord>().create()

        val posts =
            dsl.selectFrom(Posts.POSTS)
                .where(Posts.POSTS.USER_ID.eq(user.id))
                .fetch()

        assertThat(posts).hasSize(5)
    }

    @Test
    fun `6 - Factory composition for complex scenarios`() {
        factory<UsersRecord> {
            attribute("name", "User")
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25
        }

        fun createBlogPost(
            authorName: String = "Author",
            postsCount: Int = 1,
        ): UsersRecord {
            val author =
                dsl.factory<UsersRecord>().create(
                    mapOf(
                        "name" to authorName,
                    ),
                )

            factory<PostsRecord> {
                attribute("title", "Post")
                attribute("content", "Content")
                sequenceAttr("user_id") { _ -> author.id!! }
            }

            repeat(postsCount) {
                dsl.factory<PostsRecord>().create(
                    mapOf(
                        "title" to "Post ${it + 1}",
                    ),
                )
            }

            return author
        }

        val author =
            createBlogPost(
                authorName = "John Doe",
                postsCount = 3,
            )

        val posts =
            dsl.selectFrom(Posts.POSTS)
                .where(Posts.POSTS.USER_ID.eq(author.id))
                .fetch()

        assertThat(posts).hasSize(3)
        assertThat(author.name).isEqualTo("John Doe")
    }

    @Test
    fun `7 - Multi-factory test setup`() {
        factory<UsersRecord> {
            attribute("name", "User")
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25
        }

        val authors = dsl.factory<UsersRecord>().createList(3)

        val allPosts =
            authors.flatMap { author ->
                factory<PostsRecord> {
                    attribute("title", "Post")
                    attribute("content", "Content")
                    sequenceAttr("user_id") { _ -> author.id!! }
                }

                dsl.factory<PostsRecord>().createList(5)
            }

        assertThat(allPosts).hasSize(15)

        val userCount = dsl.selectCount().from(Users.USERS).fetchOne(0, Int::class.java)
        val postCount = dsl.selectCount().from(Posts.POSTS).fetchOne(0, Int::class.java)

        assertThat(userCount).isEqualTo(3)
        assertThat(postCount).isEqualTo(15)
    }

    @Test
    fun `8 - Lazy association loading`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            transient {
                set("lazyLoadPosts", true)
            }

            afterCreate { user, transients ->
                val lazy = transients.getOrNull("lazyLoadPosts") as? Boolean ?: false
                if (!lazy) {
                    factory<PostsRecord> {
                        attribute("title", "Post")
                        attribute("content", "Content")
                        sequenceAttr("user_id") { _ -> user.id!! }
                    }
                    dsl.factory<PostsRecord>().createList(10)
                }
            }
        }

        val user = dsl.factory<UsersRecord>().create()

        val posts =
            dsl.selectFrom(Posts.POSTS)
                .where(Posts.POSTS.USER_ID.eq(user.id))
                .fetch()

        assertThat(posts).hasSize(0)

        factory<PostsRecord> {
            POSTS.TITLE set "Post"
            POSTS.CONTENT set "Content"
            POSTS.USER_ID set sequence { _ -> user.id!! }
        }

        dsl.factory<PostsRecord>().createList(5)

        val postsAfter =
            dsl.selectFrom(Posts.POSTS)
                .where(Posts.POSTS.USER_ID.eq(user.id))
                .fetch()

        assertThat(postsAfter).hasSize(5)
    }

    @Test
    fun `9 - Conditional attribute generation`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }

            USERS.AGE set
                sequence { n ->
                    if (n % 2 == 0) 30 else 25
                }
        }

        val users = dsl.factory<UsersRecord>().buildList(10)

        val ages = users.map { it.age }.toSet()
        assertThat(ages).containsExactlyInAnyOrder(25, 30)
    }

    @Test
    fun `10 - Complex test data with multiple factories`() {
        factory<UsersRecord> {
            USERS.NAME set sequence { n -> "User $n" }
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25
        }

        val users = dsl.factory<UsersRecord>().createList(3)

        users.forEach { user ->
            factory<PostsRecord> {
                POSTS.TITLE set sequence { n -> "Post $n" }
                POSTS.CONTENT set "Content"
                POSTS.USER_ID set sequence { _ -> user.id!! }
            }

            val postCount = (1..3).random()
            dsl.factory<PostsRecord>().createList(postCount)
        }

        val totalPosts = dsl.selectCount().from(Posts.POSTS).fetchOne(0, Int::class.java)

        assertThat(totalPosts).isGreaterThan(0)
        assertThat(users).hasSize(3)
    }
}
