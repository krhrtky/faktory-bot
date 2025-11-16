package com.example.faktory.debug

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users.Companion.USERS
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TraitAttributeOverrideTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `trait can override name attribute`() {
        factory<UsersRecord> {
            USERS.NAME set "Regular User"
            USERS.EMAIL set "user@example.com"

            trait("admin") {
                USERS.NAME set "Admin User"
            }
        }

        val regularUser = dsl.factory<UsersRecord>().build()
        val adminUser = dsl.factory<UsersRecord>().build("admin")

        println("Regular user name: ${regularUser.name}")
        println("Admin user name: ${adminUser.name}")

        assertThat(regularUser.name).isEqualTo("Regular User")
        assertThat(adminUser.name).isEqualTo("Admin User")
    }

    @Test
    fun `trait can override email attribute`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "default@example.com"

            trait("verified") {
                USERS.EMAIL set "verified@example.com"
            }
        }

        val defaultUser = dsl.factory<UsersRecord>().build()
        val verifiedUser = dsl.factory<UsersRecord>().build("verified")

        println("Default user email: ${defaultUser.email}")
        println("Verified user email: ${verifiedUser.email}")

        assertThat(defaultUser.email).isEqualTo("default@example.com")
        assertThat(verifiedUser.email).isEqualTo("verified@example.com")
    }

    @Test
    fun `trait can override age attribute`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait("senior") {
                USERS.AGE set 65
            }
        }

        val youngUser = dsl.factory<UsersRecord>().build()
        val seniorUser = dsl.factory<UsersRecord>().build("senior")

        println("Young user age: ${youngUser.age}")
        println("Senior user age: ${seniorUser.age}")

        assertThat(youngUser.age).isEqualTo(25)
        assertThat(seniorUser.age).isEqualTo(65)
    }

    @Test
    fun `trait can override multiple attributes`() {
        factory<UsersRecord> {
            USERS.NAME set "Regular User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait("admin") {
                USERS.NAME set "Admin User"
                USERS.EMAIL set "admin@example.com"
                USERS.AGE set 35
            }
        }

        val regularUser = dsl.factory<UsersRecord>().build()
        val adminUser = dsl.factory<UsersRecord>().build("admin")

        println("Regular user: name=${regularUser.name}, email=${regularUser.email}, age=${regularUser.age}")
        println("Admin user: name=${adminUser.name}, email=${adminUser.email}, age=${adminUser.age}")

        assertThat(regularUser.name).isEqualTo("Regular User")
        assertThat(regularUser.email).isEqualTo("user@example.com")
        assertThat(regularUser.age).isEqualTo(25)

        assertThat(adminUser.name).isEqualTo("Admin User")
        assertThat(adminUser.email).isEqualTo("admin@example.com")
        assertThat(adminUser.age).isEqualTo(35)
    }
}
