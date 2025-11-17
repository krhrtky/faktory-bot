package com.example.faktory.examples

import com.example.faktory.dsl.factory
import com.example.faktory.examples.jooq.tables.Users.Companion.USERS
import com.example.faktory.examples.jooq.tables.records.UsersRecord
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TraitExample : ExamplesTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `1 - Single trait overrides base attributes`() {
        factory<UsersRecord> {
            USERS.NAME set "Regular User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait("admin") {
                USERS.NAME set "Admin User"
                USERS.AGE set 35
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
            USERS.NAME set "User"
            USERS.EMAIL set sequence { n -> "user$n@example.com" }
            USERS.AGE set 25

            trait("verified") {
                USERS.EMAIL set "verified@example.com"
            }

            trait("senior") {
                USERS.AGE set 60
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
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait("young") {
                USERS.AGE set 18
            }

            trait("old") {
                USERS.AGE set 70
            }
        }

        val user1 = dsl.factory<UsersRecord>().build("young", "old")
        assertThat(user1.age).isEqualTo(70)

        val user2 = dsl.factory<UsersRecord>().build("old", "young")
        assertThat(user2.age).isEqualTo(18)
    }
}
