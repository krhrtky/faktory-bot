package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TraitExampleTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Single trait overrides base attributes`() {
        factory<UsersRecord> {
            name = "Regular User"
            email = "user@example.com"
            age = 25

            trait("admin") {
                attribute("name", "Admin User")
                attribute("age", 35)
            }
        }

        val regularUser = dsl.factory<UsersRecord>().build()
        val adminUser = dsl.factory<UsersRecord>().build("admin")

        assertThat(regularUser.name).isEqualTo("Regular User")
        assertThat(regularUser.age).isEqualTo(25)

        assertThat(adminUser.name).isEqualTo("Admin User")
        assertThat(adminUser.email).isEqualTo("user@example.com")
        assertThat(adminUser.age).isEqualTo(35)
    }

    @Test
    fun `2 - Multiple traits can be composed`() {
        factory<UsersRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25

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
    fun `3 - Trait order matters - later traits override earlier ones`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            trait("young") {
                attribute("age", 18)
            }

            trait("old") {
                attribute("age", 70)
            }
        }

        val user1 = dsl.factory<UsersRecord>().build("young", "old")
        assertThat(user1.age).isEqualTo(70)

        val user2 = dsl.factory<UsersRecord>().build("old", "young")
        assertThat(user2.age).isEqualTo(18)
    }

    @Test
    fun `4 - Traits with callbacks`() {
        var callbackExecuted = false

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            trait("withCallback") {
                afterCreate { _, _ ->
                    callbackExecuted = true
                }
            }
        }

        dsl.factory<UsersRecord>().create("withCallback")

        assertThat(callbackExecuted).isTrue()
    }

    @Test
    fun `5 - Overrides take precedence over traits`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            trait("senior") {
                attribute("age", 60)
            }
        }

        val user = dsl.factory<UsersRecord>().build("senior", overrides = mapOf(
            "age" to 45
        ))

        assertThat(user.age).isEqualTo(45)
    }

    @Test
    fun `6 - Flag-based traits for feature toggles`() {
        factory<UsersRecord> {
            name = "User"
            email = sequence { n -> "user${n}@example.com" }
            age = 25

            trait("verified") {
                attribute("name", "Verified User")
            }

            trait("premium") {
                attribute("name", "Premium User")
            }
        }

        val regular = dsl.factory<UsersRecord>().build()
        val verified = dsl.factory<UsersRecord>().build("verified")
        val premium = dsl.factory<UsersRecord>().build("premium")

        assertThat(regular.name).isEqualTo("User")
        assertThat(verified.name).isEqualTo("Verified User")
        assertThat(premium.name).isEqualTo("Premium User")
    }

    @Test
    fun `7 - Traits work with create() and persist to database`() {
        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            trait("admin") {
                attribute("name", "Admin User")
            }
        }

        val admin = dsl.factory<UsersRecord>().create("admin")

        assertThat(admin.id).isNotNull()
        assertThat(admin.name).isEqualTo("Admin User")
    }

    @Test
    fun `8 - Multiple traits with callbacks execute in order`() {
        val executionOrder = mutableListOf<String>()

        factory<UsersRecord> {
            name = "User"
            email = "user@example.com"
            age = 25

            afterCreate { _, _ ->
                executionOrder.add("base")
            }

            trait("t1") {
                afterCreate { _, _ ->
                    executionOrder.add("t1")
                }
            }

            trait("t2") {
                afterCreate { _, _ ->
                    executionOrder.add("t2")
                }
            }
        }

        dsl.factory<UsersRecord>().create("t1", "t2")

        assertThat(executionOrder).containsExactly("base", "t1", "t2")
    }
}
