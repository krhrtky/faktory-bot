package com.example.faktory.integration

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Phase4IntegrationTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `trait overrides base attributes`() {
        factory<UsersRecord> {
            this["name"] = "Regular User"
            this["email"] = "user@example.com"
            this["age"] = 25

            trait("admin") {
                attribute("name", "Admin User")
                attribute("age", 35)
            }
        }

        val admin = dsl.factory<UsersRecord>().build("admin")

        assertThat(admin.name).isEqualTo("Admin User")
        assertThat(admin.email).isEqualTo("user@example.com")
        assertThat(admin.age).isEqualTo(35)
    }

    @Test
    fun `multiple traits can be applied`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = "user@example.com"
            this["age"] = 25

            trait("verified") {
                attribute("email", "verified@example.com")
            }

            trait("senior") {
                attribute("age", 60)
            }
        }

        val user = dsl.factory<UsersRecord>().build("verified", "senior")

        assertThat(user.name).isEqualTo("User")
        assertThat(user.email).isEqualTo("verified@example.com")
        assertThat(user.age).isEqualTo(60)
    }

    @Test
    fun `trait with callbacks`() {
        var callbackCalled = false

        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = "user@example.com"
            this["age"] = 25

            trait("withCallback") {
                afterCreate { _, _ ->
                    callbackCalled = true
                }
            }
        }

        dsl.factory<UsersRecord>().create("withCallback")

        assertThat(callbackCalled).isTrue()
    }

    @Test
    fun `attributes method returns evaluated attributes`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val attrs1 = dsl.factory<UsersRecord>().attributes()
        val attrs2 = dsl.factory<UsersRecord>().attributes()

        assertThat(attrs1["name"]).isEqualTo("User")
        assertThat(attrs1["email"]).asString().matches("user\\d+@example.com")
        assertThat(attrs1["age"]).isEqualTo(25)

        assertThat(attrs2["email"]).asString().matches("user\\d+@example.com")
        assertThat(attrs1["email"]).isNotEqualTo(attrs2["email"])
    }

    @Test
    fun `attributes method with overrides`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = "user@example.com"
            this["age"] = 25
        }

        val attrs = dsl.factory<UsersRecord>().attributes(mapOf("name" to "Custom User"))

        assertThat(attrs["name"]).isEqualTo("Custom User")
        assertThat(attrs["email"]).isEqualTo("user@example.com")
        assertThat(attrs["age"]).isEqualTo(25)
    }

    @Test
    fun `buildList creates multiple records`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val users = dsl.factory<UsersRecord>().buildList(5)

        assertThat(users).hasSize(5)
        val emails = users.map { it.email }
        assertThat(emails.toSet()).hasSize(5)
    }

    @Test
    fun `createList persists multiple records`() {
        factory<UsersRecord> {
            this["name"] = "User"
            this["email"] = sequence { n -> "user${n}@example.com" }
            this["age"] = 25
        }

        val users = dsl.factory<UsersRecord>().createList(3)

        assertThat(users).hasSize(3)
        assertThat(users.all { it.id != null }).isTrue()

        val count = dsl.selectCount().from(com.example.faktory.test.jooq.tables.Users.USERS).fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(3)
    }
}
