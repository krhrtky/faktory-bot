package io.github.krhrtky.faktory.examples

import io.github.krhrtky.faktory.dsl.factory
import io.github.krhrtky.faktory.examples.jooq.tables.references.POSTS
import io.github.krhrtky.faktory.examples.jooq.tables.references.USERS
import io.github.krhrtky.faktory.examples.jooq.tables.records.PostsRecord
import io.github.krhrtky.faktory.examples.jooq.tables.records.UsersRecord
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostsExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `create posts with type-safe traits and automatic user association`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 25
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set "Default Post"
            POSTS.CONTENT set "Default content"
            POSTS.PUBLISHED set false

            trait(PostTrait.Published) {
                POSTS.PUBLISHED set true
                POSTS.TITLE set "Published Post"
            }

            trait(PostTrait.Draft) {
                POSTS.PUBLISHED set false
                POSTS.TITLE set "Draft Post"
            }

            trait(PostTrait.Featured) {
                POSTS.PUBLISHED set true
                POSTS.TITLE set "Featured Post"
                POSTS.CONTENT set "This is a featured article"
            }
        }

        val draft = dsl.factory<PostsRecord>().create(PostTrait.Draft)
        val published = dsl.factory<PostsRecord>().create(PostTrait.Published)
        val featured = dsl.factory<PostsRecord>().create(PostTrait.Featured)

        assertThat(draft.userId).isNotNull()
        assertThat(draft.published).isFalse()
        assertThat(draft.title).isEqualTo("Draft Post")

        assertThat(published.userId).isNotNull()
        assertThat(published.published).isTrue()
        assertThat(published.title).isEqualTo("Published Post")

        assertThat(featured.userId).isNotNull()
        assertThat(featured.published).isTrue()
        assertThat(featured.title).isEqualTo("Featured Post")
        assertThat(featured.content).isEqualTo("This is a featured article")
    }

    @Test
    fun `combine multiple post traits with automatic user association`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user${n}@example.com" }
            USERS.AGE set 25
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set "Post"
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set false

            trait(PostTrait.Published) {
                POSTS.PUBLISHED set true
            }

            trait(PostTrait.Long) {
                POSTS.CONTENT set "This is a very long article with lots of detailed content spanning multiple paragraphs."
            }

            trait(PostTrait.Short) {
                POSTS.CONTENT set "Brief."
            }
        }

        val publishedLong = dsl.factory<PostsRecord>().create(PostTrait.Published, PostTrait.Long)
        val publishedShort = dsl.factory<PostsRecord>().create(PostTrait.Published, PostTrait.Short)

        assertThat(publishedLong.userId).isNotNull()
        assertThat(publishedLong.published).isTrue()
        assertThat(publishedLong.content).contains("very long article")

        assertThat(publishedShort.userId).isNotNull()
        assertThat(publishedShort.published).isTrue()
        assertThat(publishedShort.content).isEqualTo("Brief.")
    }

    @Test
    fun `posts can be created with sequence for unique titles and automatic user`() {
        factory<UsersRecord> {
            USERS.NAME set "Auto User"
            USERS.EMAIL set sequence { n -> "auto${n}@example.com" }
            USERS.AGE set 30
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set sequence { n -> "Post #$n" }
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set true
        }

        val post1 = dsl.factory<PostsRecord>().create()
        val post2 = dsl.factory<PostsRecord>().create()

        assertThat(post1.userId).isNotNull()
        assertThat(post1.title).isEqualTo("Post #1")
        assertThat(post2.userId).isNotNull()
        assertThat(post2.title).isEqualTo("Post #2")

        val users =
            dsl
                .selectFrom(USERS)
                .fetch()

        assertThat(users).hasSizeGreaterThanOrEqualTo(2)
    }

    @Test
    fun `type-safe traits prevent wrong trait combinations`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set "Post"
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set false

            trait(PostTrait.Published) {
                POSTS.PUBLISHED set true
            }

            trait(PostTrait.Draft) {
                POSTS.PUBLISHED set false
            }
        }

        val published = dsl.factory<PostsRecord>().create(PostTrait.Published)

        assertThat(published.userId).isNotNull()
        assertThat(published.published).isTrue()
    }

    @Test
    fun `create multiple posts with different traits and automatic users`() {
        factory<UsersRecord> {
            USERS.NAME set "Author"
            USERS.EMAIL set sequence { n -> "author${n}@example.com" }
            USERS.AGE set 35
        }

        factory<PostsRecord> {
            POSTS.USER_ID set association<UsersRecord>()
            POSTS.TITLE set sequence { n -> "Post $n" }
            POSTS.CONTENT set "Content"
            POSTS.PUBLISHED set false

            trait(PostTrait.Published) {
                POSTS.PUBLISHED set true
            }

            trait(PostTrait.Draft) {
                POSTS.PUBLISHED set false
            }
        }

        val posts =
            listOf(
                dsl.factory<PostsRecord>().create(PostTrait.Published),
                dsl.factory<PostsRecord>().create(PostTrait.Draft),
                dsl.factory<PostsRecord>().create(PostTrait.Published),
            )

        assertThat(posts).hasSize(3)
        assertThat(posts.all { it.userId != null }).isTrue()
        assertThat(posts.filter { it.published == true }).hasSize(2)
        assertThat(posts.filter { it.published == false }).hasSize(1)
    }
}
