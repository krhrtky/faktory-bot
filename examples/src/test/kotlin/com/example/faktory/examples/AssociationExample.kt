package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.examples.jooq.tables.Posts
import com.example.faktory.examples.jooq.tables.Posts.Companion.POSTS
import com.example.faktory.examples.jooq.tables.Users.Companion.USERS
import com.example.faktory.examples.jooq.tables.records.PostsRecord
import com.example.faktory.examples.jooq.tables.records.UsersRecord
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AssociationExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Create post with associated user`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25
        }

        val user = dsl.factory<UsersRecord>().create()

        factory<PostsRecord> {
            POSTS.TITLE set "My Post"
            POSTS.CONTENT set "Content here"
            POSTS.USER_ID set user.id!!
            POSTS.PUBLISHED set false
        }

        val post = dsl.factory<PostsRecord>().create()

        assertThat(post.id).isNotNull()
        assertThat(post.userId).isEqualTo(user.id)
        assertThat(post.title).isEqualTo("My Post")

        val foundPost =
            dsl.selectFrom(Posts.POSTS)
                .where(Posts.POSTS.ID.eq(post.id))
                .fetchOne()

        assertThat(foundPost).isNotNull()
        assertThat(foundPost!!.userId).isEqualTo(user.id)
    }

    @Test
    fun `2 - Create multiple posts for same user`() {
        factory<UsersRecord> {
            USERS.NAME set "Blogger"
            USERS.EMAIL set "blogger@example.com"
            USERS.AGE set 30
        }

        val user = dsl.factory<UsersRecord>().create()

        factory<PostsRecord> {
            POSTS.TITLE set sequence { n -> "Post #$n" }
            POSTS.CONTENT set "Content"
            POSTS.USER_ID set user.id!!
            POSTS.PUBLISHED set true
        }

        val posts = dsl.factory<PostsRecord>().createList(5)

        assertThat(posts).hasSize(5)
        assertThat(posts.all { it.userId == user.id }).isTrue()
        assertThat(posts[0].title).isEqualTo("Post #1")
        assertThat(posts[4].title).isEqualTo("Post #5")

        val count =
            dsl.selectCount()
                .from(Posts.POSTS)
                .where(Posts.POSTS.USER_ID.eq(user.id))
                .fetchOne(0, Int::class.java)

        assertThat(count).isEqualTo(5)
    }

    @Test
    fun `3 - Create user with posts in single setup`() {
        factory<UsersRecord> {
            USERS.NAME set "Author"
            USERS.EMAIL set "author@example.com"
            USERS.AGE set 35
        }

        val user = dsl.factory<UsersRecord>().create()

        factory<PostsRecord> {
            POSTS.TITLE set sequence { n -> "Article $n" }
            POSTS.CONTENT set "Article content"
            POSTS.USER_ID set user.id!!
            POSTS.PUBLISHED set false
        }

        val posts = (1..3).map { dsl.factory<PostsRecord>().create() }

        assertThat(posts).hasSize(3)
        assertThat(posts.all { it.userId == user.id }).isTrue()
    }
}
