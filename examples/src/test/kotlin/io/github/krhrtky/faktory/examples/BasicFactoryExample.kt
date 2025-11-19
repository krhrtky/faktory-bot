package io.github.krhrtky.faktory.examples

import io.github.krhrtky.faktory.dsl.factory
import io.github.krhrtky.faktory.examples.jooq.tables.Users
import io.github.krhrtky.faktory.examples.jooq.tables.Users.Companion.USERS
import io.github.krhrtky.faktory.examples.jooq.tables.records.UsersRecord
import io.github.krhrtky.faktory.registry.GlobalFactoryRegistry
import io.github.krhrtky.faktory.sequence.GlobalSequenceManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasicFactoryExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Define factory with default attributes`() {
        factory<UsersRecord> {
            USERS.NAME set "John Doe"
            USERS.EMAIL set "john@example.com"
            USERS.AGE set 30
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.age).isEqualTo(30)
    }

    @Test
    fun `2 - build() - Create in-memory record`() {
        factory<UsersRecord> {
            USERS.NAME set "John Doe"
            USERS.EMAIL set "john@example.com"
            USERS.AGE set 30
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.id).isNull()
    }

    @Test
    fun `3 - build(overrides) - Create with custom attributes`() {
        factory<UsersRecord> {
            USERS.NAME set "John Doe"
            USERS.EMAIL set "john@example.com"
            USERS.AGE set 30
        }

        val user =
            dsl.factory<UsersRecord>().build(
                mapOf(
                    "name" to "Jane Smith",
                    "age" to 25,
                ),
            )

        assertThat(user.name).isEqualTo("Jane Smith")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.age).isEqualTo(25)
    }

    @Test
    fun `4 - create() - Create and persist to database`() {
        factory<UsersRecord> {
            USERS.NAME set "John Doe"
            USERS.EMAIL set "john@example.com"
            USERS.AGE set 30
        }

        val user = dsl.factory<UsersRecord>().create()

        assertThat(user.id).isNotNull()
        assertThat(user.name).isEqualTo("John Doe")

        val found =
            dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.eq(user.id))
                .fetchOne()

        assertThat(found).isNotNull()
        assertThat(found!!.name).isEqualTo("John Doe")
    }

    @Test
    fun `5 - createList(n) - Create and persist multiple records`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25
        }

        val users = dsl.factory<UsersRecord>().createList(10)

        assertThat(users).hasSize(10)
        assertThat(users.all { it.id != null }).isTrue()

        val count =
            dsl.selectCount()
                .from(Users.USERS)
                .fetchOne(0, Int::class.java)

        assertThat(count).isEqualTo(10)
    }
}
