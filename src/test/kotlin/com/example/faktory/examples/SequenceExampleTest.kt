package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SequenceExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Simple sequence generates unique email addresses`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val user1 = dsl.factory<UsersRecord>().build()
        val user2 = dsl.factory<UsersRecord>().build()
        val user3 = dsl.factory<UsersRecord>().build()

        assertThat(user1.email).isEqualTo("user1@example.com")
        assertThat(user2.email).isEqualTo("user2@example.com")
        assertThat(user3.email).isEqualTo("user3@example.com")
    }

    @Test
    fun `2 - Multiple sequences maintain independent counters`() {
        factory<UsersRecord> {
            sequenceAttr("name") { n -> "User $n" }
            this["email"] = sequence { n -> "user${n}@example.com" }
            sequenceAttr("age") { n -> 20 + n }
        }

        val user1 = dsl.factory<UsersRecord>().build()
        val user2 = dsl.factory<UsersRecord>().build()

        assertThat(user1.name).isEqualTo("User 1")
        assertThat(user1.email).isEqualTo("user1@example.com")
        assertThat(user1.age).isEqualTo(21)

        assertThat(user2.name).isEqualTo("User 2")
        assertThat(user2.email).isEqualTo("user2@example.com")
        assertThat(user2.age).isEqualTo(22)
    }

    @Test
    fun `3 - Formatted sequences with padding`() {
        factory<UsersRecord> {
            name = "User"
            email = sequence { n -> "user${String.format("%04d", n)}@example.com" }
            age = 25
        }

        val users = dsl.factory<UsersRecord>().buildList(3)

        assertThat(users[0].email).isEqualTo("user0001@example.com")
        assertThat(users[1].email).isEqualTo("user0002@example.com")
        assertThat(users[2].email).isEqualTo("user0003@example.com")
    }

    @Test
    fun `4 - Sequences work with buildList and createList`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val users = dsl.factory<UsersRecord>().buildList(5)

        val emails = users.map { it.email }
        assertThat(emails.toSet()).hasSize(5)
        assertThat(emails[0]).isEqualTo("user1@example.com")
        assertThat(emails[4]).isEqualTo("user5@example.com")
    }

    @Test
    fun `5 - Sequence reset ensures deterministic values`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val user1 = dsl.factory<UsersRecord>().build()
        assertThat(user1.email).isEqualTo("user1@example.com")

        GlobalSequenceManager.reset()

        val user2 = dsl.factory<UsersRecord>().build()
        assertThat(user2.email).isEqualTo("user1@example.com")
    }

    @Test
    fun `6 - Sequence with attribute override`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val user = dsl.factory<UsersRecord>().build(mapOf(
            "email" to "custom@example.com"
        ))

        assertThat(user.email).isEqualTo("custom@example.com")
    }

    @Test
    fun `7 - Sequences ensure uniqueness across many records`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val users = dsl.factory<UsersRecord>().buildList(100)
        val emails = users.map { it.email }.toSet()

        assertThat(emails).hasSize(100)
    }

    @Test
    fun `8 - Using sequenceAttr for dynamic attributes`() {
        factory<UsersRecord> {
            name = "User"
            sequenceAttr("email") { n -> "user${n}@example.com" }
            age = 25
        }

        val user1 = dsl.factory<UsersRecord>().build()
        val user2 = dsl.factory<UsersRecord>().build()

        assertThat(user1.email).matches("user\\d+@example.com")
        assertThat(user2.email).matches("user\\d+@example.com")
        assertThat(user1.email).isNotEqualTo(user2.email)
    }
}
