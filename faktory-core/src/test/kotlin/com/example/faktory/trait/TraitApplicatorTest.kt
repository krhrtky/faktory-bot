package com.example.faktory.trait

import com.example.faktory.dsl.factory
import com.example.faktory.registry.GlobalFactoryRegistry
import com.example.faktory.sequence.GlobalSequenceManager
import com.example.faktory.test.JooqTestBase
import com.example.faktory.test.jooq.tables.Users.Companion.USERS
import com.example.faktory.test.jooq.tables.records.UsersRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TraitApplicatorTest : JooqTestBase() {
    @BeforeEach
    fun setup() {
        GlobalSequenceManager.reset()
        GlobalFactoryRegistry.clear()
    }

    @Test
    fun `apply single trait overrides attributes`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait("admin") {
                USERS.NAME set "Admin"
                USERS.AGE set 35
            }
        }

        val regularUser = dsl.factory<UsersRecord>().build()
        val adminUser = dsl.factory<UsersRecord>().build("admin")

        assertThat(regularUser.name).isEqualTo("User")
        assertThat(regularUser.age).isEqualTo(25)

        assertThat(adminUser.name).isEqualTo("Admin")
        assertThat(adminUser.age).isEqualTo(35)
    }

    @Test
    fun `apply multiple traits in order`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"
            USERS.AGE set 25

            trait("verified") {
                USERS.EMAIL set "verified@example.com"
            }

            trait("senior") {
                USERS.AGE set 60
            }
        }

        val user = dsl.factory<UsersRecord>().build("verified", "senior")

        assertThat(user.email).isEqualTo("verified@example.com")
        assertThat(user.age).isEqualTo(60)
    }

    @Test
    fun `throws exception for non-existent trait`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
        }

        assertThatThrownBy {
            dsl.factory<UsersRecord>().build("nonexistent")
        }.hasMessageContaining("nonexistent")
    }

    @Test
    fun `apply trait with callbacks`() {
        var callbackExecuted = false

        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"

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
    fun `apply trait with transients`() {
        factory<UsersRecord> {
            USERS.NAME set "User"
            USERS.EMAIL set "user@example.com"

            trait("withTransient") {
                transient {
                    set("key", "value")
                }
            }
        }

        val definition = GlobalFactoryRegistry.find(UsersRecord::class)
        val traitDef = (definition as com.example.faktory.core.DefaultFactoryDefinition).traits["withTransient"]!!

        assertThat(traitDef.transients.properties["key"]).isEqualTo("value")
    }

    @Test
    fun `later traits override earlier traits`() {
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
