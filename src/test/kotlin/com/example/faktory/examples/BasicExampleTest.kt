package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasicExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Define factory with default attributes`() {
        factory<UsersRecord> {
            this["name"] = "John Doe"
            this["email"] = "john@example.com"
            this["age"] = 30
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.age).isEqualTo(30)
    }

    @Test
    fun `2 - build() - Create in-memory record`() {
        factory<UsersRecord> {
            this["name"] = "John Doe"
            this["email"] = "john@example.com"
            this["age"] = 30
        }

        val user = dsl.factory<UsersRecord>().build()

        assertThat(user.name).isEqualTo("John Doe")
        assertThat(user.id).isNull()
    }

    @Test
    fun `3 - build(overrides) - Create with custom attributes`() {
        factory<UsersRecord> {
            this["name"] = "John Doe"
            this["email"] = "john@example.com"
            this["age"] = 30
        }

        val user = dsl.factory<UsersRecord>().build(mapOf(
            "name" to "Jane Smith",
            "age" to 25
        ))

        assertThat(user.name).isEqualTo("Jane Smith")
        assertThat(user.email).isEqualTo("john@example.com")
        assertThat(user.age).isEqualTo(25)
    }

    @Test
    fun `4 - create() - Create and persist to database`() {
        factory<UsersRecord> {
            this["name"] = "John Doe"
            this["email"] = "john@example.com"
            this["age"] = 30
        }

        val user = dsl.factory<UsersRecord>().create()

        assertThat(user.id).isNotNull()
        assertThat(user.name).isEqualTo("John Doe")

        val found = dsl.selectFrom(Users.USERS)
            .where(Users.USERS.ID.eq(user.id))
            .fetchOne()

        assertThat(found).isNotNull()
        assertThat(found!!.name).isEqualTo("John Doe")
    }

    @Test
    fun `5 - create(overrides) - Create with custom attributes and persist`() {
        factory<UsersRecord> {
            this["name"] = "John Doe"
            this["email"] = "john@example.com"
            this["age"] = 30
        }

        val user = dsl.factory<UsersRecord>().create(mapOf(
            "email" to "custom@example.com"
        ))

        assertThat(user.email).isEqualTo("custom@example.com")
        assertThat(user.id).isNotNull()

        val found = dsl.selectFrom(Users.USERS)
            .where(Users.USERS.ID.eq(user.id))
            .fetchOne()

        assertThat(found!!.email).isEqualTo("custom@example.com")
    }

    @Test
    fun `6 - buildList(n) - Create multiple in-memory records`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val users = dsl.factory<UsersRecord>().buildList(10)

        assertThat(users).hasSize(10)
        assertThat(users.all { it.id == null }).isTrue()
        assertThat(users[0].email).isEqualTo("user1@example.com")
        assertThat(users[9].email).isEqualTo("user10@example.com")
    }

    @Test
    fun `7 - createList(n) - Create and persist multiple records`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val users = dsl.factory<UsersRecord>().createList(10)

        assertThat(users).hasSize(10)
        assertThat(users.all { it.id != null }).isTrue()

        val count = dsl.selectCount()
            .from(Users.USERS)
            .fetchOne(0, Int::class.java)

        assertThat(count).isEqualTo(10)
    }

    @Test
    fun `8 - attributes() - Get evaluated attributes as Map`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = "user@example.com"
            this["age"] = 25
        }

        val attrs = dsl.factory<UsersRecord>().attributes()

        assertThat(attrs).containsEntry("name", "User")
        assertThat(attrs).containsEntry("email", "user@example.com")
        assertThat(attrs).containsEntry("age", 25)
    }

    @Test
    fun `9 - attributes(overrides) - Get evaluated attributes with overrides`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = "user@example.com"
            this["age"] = 25
        }

        val attrs = dsl.factory<UsersRecord>().attributes(mapOf(
            "name" to "Custom User"
        ))

        assertThat(attrs["name"]).isEqualTo("Custom User")
        assertThat(attrs["email"]).isEqualTo("user@example.com")
    }
}
