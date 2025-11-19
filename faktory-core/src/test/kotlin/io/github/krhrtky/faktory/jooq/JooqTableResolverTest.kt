package io.github.krhrtky.faktory.jooq

import io.github.krhrtky.faktory.test.jooq.tables.Posts
import io.github.krhrtky.faktory.test.jooq.tables.Users
import io.github.krhrtky.faktory.test.jooq.tables.records.PostsRecord
import io.github.krhrtky.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JooqTableResolverTest {
    @Test
    fun `resolveTable returns correct table for UsersRecord`() {
        val table = JooqTableResolver.resolveTable(UsersRecord::class)

        assertThat(table).isEqualTo(Users.USERS)
    }

    @Test
    fun `resolveTable returns correct table for PostsRecord`() {
        val table = JooqTableResolver.resolveTable(PostsRecord::class)

        assertThat(table).isEqualTo(Posts.POSTS)
    }
}
